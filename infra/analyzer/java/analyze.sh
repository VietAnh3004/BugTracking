#!/usr/bin/env bash
#
# Hai pha của một lần phân tích Java, mỗi pha là MỘT lời gọi container riêng với mức đặc quyền
# khác nhau (ADR 0008 mục 4bis):
#
#   analyze.sh resolve <sourceRoot>   — CÓ mạng. Chỉ phân giải phụ thuộc, không chạy vòng đời
#                                       build của người dùng.
#   analyze.sh analyze <sourceRoot>   — KHÔNG mạng. Chạy test + SpotBugs. Đây là pha thực thi mã
#                                       của người lạ, nên nó là pha bị cắt mạng.
#
# Mọi artifact ghi vào /work/.analysis/ để SandboxRunner copy ra bằng một lời gọi.
#
# NGUYÊN TẮC XUYÊN SUỐT: thà hỏng ồn ào còn hơn cho ra một kết quả trông đầy đủ mà thiếu. Mọi
# `exit 1` ở dưới đều là một kiểu hỏng-im-lặng đã được đặt tên.
set -euo pipefail

MODE="${1:-}"
SOURCE_ROOT="${2:-/work/repo}"

OUT_DIR=/work/.analysis
LOCAL_REPO=/work/.m2
AUX_CLASSPATH_FILE="$OUT_DIR/auxclasspath.txt"

SPOTBUGS_HOME=/opt/spotbugs
CANARY_CLASSES=/opt/canary/classes

# `maven.repo.local.tail` (Maven 3.9+) xâu chuỗi seed CHỈ ĐỌC vào sau repo ghi được của lần chạy
# này. Không dùng seed làm repo chính: Maven sẽ ghi vào đó, mà nó là read-only trong image.
MVN_REPO_ARGS=(-Dmaven.repo.local="$LOCAL_REPO" -Dmaven.repo.local.tail=/opt/m2-seed)
MVN_COMMON=(-B -ntp "${MVN_REPO_ARGS[@]}")

usage() {
    echo "Dung: analyze.sh {resolve|analyze} <sourceRoot>" >&2
    exit 2
}

fail() {
    echo "PHAN TICH HONG: $*" >&2
    exit 1
}

require_source_root() {
    [ -d "$SOURCE_ROOT" ] || fail "khong thay thu muc ma nguon '$SOURCE_ROOT'"
    [ -f "$SOURCE_ROOT/pom.xml" ] || fail "khong thay '$SOURCE_ROOT/pom.xml' — chi ho tro du an Maven mot module"
}

# ==========================================================================================
# Pha 1 — resolve. CÓ mạng.
# ==========================================================================================
do_resolve() {
    require_source_root
    # 0777 vì HAI image ghi vào cùng thư mục này với HAI uid khác nhau: analyzer-java chạy uid
    # 10001, còn image semgrep chính chủ chạy user `semgrep` của nó. Không ép hai image dùng chung
    # uid — đó là ràng buộc ngầm giữa hai Dockerfile, sớm muộn ai đó đổi một bên. Triệu chứng nếu
    # thiếu: Semgrep quét xong xuôi, in đủ "Scanning 20 files with 60 java rules", rồi chết ở bước
    # GHI với PermissionError — kết quả phân tích đúng nhưng không ai đọc được.
    mkdir -p "$OUT_DIR" && chmod 0777 "$OUT_DIR"
    cd "$SOURCE_ROOT"

    echo "==> [resolve 1/2] Tai phu thuoc"
    mvn "${MVN_COMMON[@]}" dependency:go-offline \
        || fail "khong phan giai duoc phu thuoc Maven. Repo can phu thuoc ngoai seed va may chu khong ra duoc mang?"

    # Bước này KHÔNG phải tối ưu, nó là BẮT BUỘC. docs/spike-spotbugs-cli.md đo được: thiếu
    # -auxclasspath, SpotBugs mất 3/28 cảnh báo mà VẪN exit 0, XML vẫn hợp lệ, total_classes vẫn
    # đúng — không tầng kiểm nào khác bắt được. Hỏng ở đây phải dừng hẳn.
    echo "==> [resolve 2/2] Dung auxclasspath cho SpotBugs"
    mvn "${MVN_COMMON[@]}" dependency:build-classpath \
        -Dmdep.outputFile="$AUX_CLASSPATH_FILE" -Dmdep.includeScope=test \
        || fail "khong dung duoc auxclasspath. Quet tiep se mat canh bao ma khong co gi bao."

    [ -s "$AUX_CLASSPATH_FILE" ] || fail "auxclasspath rong — xem ghi chu o tren, khong duoc quet tiep"

    # `dependency:go-offline` KHONG keo provider cua Surefire (surefire-junit-platform): Surefire
    # phan giai no LUC CHAY, sau khi do duoc khung test nao dang dung, nen no khong nam trong do
    # thi phu thuoc ma go-offline biet. Hau qua neu bo qua: pha analyze offline chet voi
    # "Cannot access central ... in offline mode" va ta mat TOAN BO test + do phu, trong khi
    # SpotBugs van chay xong ngon lanh.
    #
    # Da thu `mvn test -Dtest=<lop khong ton tai>`: KHONG an — Surefire thoat truoc khi phan giai
    # provider khi khong co test nao khop. Chay test that thi lai la thuc thi ma nguoi la KEM
    # MANG, dung thu pha `analyze` sinh ra de tranh.
    #
    # Cach dung: doc phien ban surefire tu effective-pom roi tai thang ba provider pho bien.
    echo "==> [resolve 3/3] Keo provider cua Surefire (go-offline khong biet toi no)"
    mvn "${MVN_COMMON[@]}" -q help:effective-pom -Doutput="$OUT_DIR/effective-pom.xml" \
        > "$OUT_DIR/maven-resolve.log" 2>&1 \
        || fail "khong doc duoc effective-pom (xem maven-resolve.log)"

    SUREFIRE_VERSION="$(grep -A3 '<artifactId>maven-surefire-plugin</artifactId>' "$OUT_DIR/effective-pom.xml" \
        | grep -m1 '<version>' | sed -E 's@.*<version>(.*)</version>.*@\1@' || true)"
    [ -n "$SUREFIRE_VERSION" ] \
        || fail "khong xac dinh duoc phien ban maven-surefire-plugin tu effective-pom"

    # Tai ca ba provider: JUnit 5 (junit-platform), JUnit 4 (junit4/junit47). Khong doan truoc du
    # an dung khung nao — doan sai thi pha analyze chet offline va mat toan bo test.
    for provider in surefire-junit-platform surefire-junit4 surefire-junit47; do
        mvn "${MVN_COMMON[@]}" -q dependency:get \
            -Dartifact="org.apache.maven.surefire:${provider}:${SUREFIRE_VERSION}" \
            >> "$OUT_DIR/maven-resolve.log" 2>&1 || true
    done

    ls "$LOCAL_REPO"/org/apache/maven/surefire/surefire-junit*/"$SUREFIRE_VERSION"/*.jar >/dev/null 2>&1 \
        || fail "khong tai duoc provider Surefire ${SUREFIRE_VERSION} — pha analyze offline se mat toan bo test"

    # Provider JUnit 5 lai keo tiep `junit-platform-launcher` LUC CHAY — mot mat xich nua ma
    # go-offline khong thay. Phien ban phai lay tu chinh cay phu thuoc cua du an: doan theo phien
    # ban jupiter (5.12.2 -> 1.12.2) la dua vao mot quy uoc co the doi.
    mvn "${MVN_COMMON[@]}" -q dependency:list -DincludeGroupIds=org.junit.platform \
        -DoutputFile="$OUT_DIR/junit-platform.txt" -DappendOutput=false \
        >> "$OUT_DIR/maven-resolve.log" 2>&1 || true

    JUNIT_PLATFORM_VERSION="$(grep -oE 'org\.junit\.platform:[a-z0-9-]+:jar:[0-9][^: ]*' "$OUT_DIR/junit-platform.txt" 2>/dev/null \
        | head -1 | cut -d: -f4 || true)"
    if [ -n "$JUNIT_PLATFORM_VERSION" ]; then
        mvn "${MVN_COMMON[@]}" -q dependency:get \
            -Dartifact="org.junit.platform:junit-platform-launcher:${JUNIT_PLATFORM_VERSION}" \
            >> "$OUT_DIR/maven-resolve.log" 2>&1 \
            || fail "khong tai duoc junit-platform-launcher ${JUNIT_PLATFORM_VERSION}"
    fi
    # JUNIT_PLATFORM_VERSION rong la binh thuong voi du an JUnit 4 — khong ep phai co.

    echo "resolve xong: $(wc -c < "$AUX_CLASSPATH_FILE") byte auxclasspath," \
         "surefire ${SUREFIRE_VERSION}, junit-platform ${JUNIT_PLATFORM_VERSION:-khong dung}"
}

# ==========================================================================================
# Pha 2 — analyze. KHÔNG mạng.
# ==========================================================================================
do_analyze() {
    require_source_root
    mkdir -p "$OUT_DIR"
    [ -s "$AUX_CLASSPATH_FILE" ] || fail "chua chay pha resolve (thieu $AUX_CLASSPATH_FILE)"
    cd "$SOURCE_ROOT"

    # JaCoCo gọi theo TOẠ ĐỘ ĐẦY ĐỦ, không dựa vào pom người dùng có khai plugin hay không — cùng
    # lý do dùng SpotBugs CLI thay vì spotbugs-maven-plugin: repo người dùng nói chung không khai
    # gì, và một lần chạy "thành công" mà không có độ phủ là hỏng im lặng.
    #
    # `-o` (offline) đúng ở đây vì pha resolve đã kéo đủ; nếu chưa đủ thì hỏng ồn ào, không âm
    # thầm bỏ qua. `-fn` để 5 test hỏng của vulnshop KHÔNG làm dừng pipeline — test hỏng là DỮ
    # LIỆU ĐẦU VÀO của đồ án, không phải lỗi build.
    echo "==> [analyze 1/3] Chay test + do phu"
    mvn "${MVN_COMMON[@]}" -o -fn \
        org.jacoco:jacoco-maven-plugin:0.8.12:prepare-agent \
        test \
        org.jacoco:jacoco-maven-plugin:0.8.12:report \
        > "$OUT_DIR/maven-test.log" 2>&1 || true

    [ -d target/classes ] || fail "khong co target/classes sau khi build — ma nguon khong bien dich duoc (xem maven-test.log)"

    echo "==> [analyze 2/3] SpotBugs CLI + find-sec-bugs (kem canary)"
    # Canary đưa vào CÙNG lời gọi, không phải lời gọi riêng: chạy riêng chỉ chứng minh "SpotBugs
    # chạy được ở đâu đó", không chứng minh "lần quét NÀY có rule bảo mật". Finding của canary bị
    # loại khỏi finding_count theo package evidence.canary.
    java -jar "$SPOTBUGS_HOME/lib/spotbugs.jar" -textui \
        -effort:max -low -xml:withMessages \
        -output "$OUT_DIR/spotbugs.xml" \
        -pluginList "$SPOTBUGS_HOME/plugin/findsecbugs-plugin.jar" \
        -auxclasspath "$(cat "$AUX_CLASSPATH_FILE")" \
        target/classes "$CANARY_CLASSES" \
        > "$OUT_DIR/spotbugs.log" 2>&1 \
        || fail "SpotBugs chay khong xong (xem spotbugs.log)"

    [ -s "$OUT_DIR/spotbugs.xml" ] || fail "SpotBugs khong sinh ra spotbugs.xml"

    echo "==> [analyze 3/3] Thu artifact"
    # `-fn` o tren cho phep 5 test hong cua vulnshop khong lam dung pipeline — nhung no cung nuot
    # luon MOI loi khac cua Maven. Da vap that: provider Surefire thieu trong repo offline khien
    # 0 test chay va 0 file coverage, ma script van exit 0 vi SpotBugs xong ngon lanh. Ba kiem tra
    # duoi day la cho bat kieu hong do, va chung la BAT BUOC chu khong phai cho chac chan.
    [ -d target/surefire-reports ] || fail "khong co thu muc surefire-reports — test khong chay duoc (xem maven-test.log)"

    mkdir -p "$OUT_DIR/surefire-reports"
    # `find ... -exec` chứ không phải `cp target/surefire-reports/TEST-*.xml`: glob không khớp gì
    # trong `set -e` sẽ làm cả script chết với thông điệp của shell, không phải của ta.
    find target/surefire-reports -name 'TEST-*.xml' -exec cp {} "$OUT_DIR/surefire-reports/" \;
    SO_BAO_CAO=$(find "$OUT_DIR/surefire-reports" -name 'TEST-*.xml' | wc -l)
    [ "$SO_BAO_CAO" -gt 0 ] || fail "0 bao cao TEST-*.xml — test khong chay duoc (xem maven-test.log)"

    [ -f target/site/jacoco/jacoco.xml ] \
        || fail "khong co jacoco.xml — agent JaCoCo khong attach duoc (argLine bi pom ghi de?)"
    cp target/site/jacoco/jacoco.xml "$OUT_DIR/jacoco.xml"

    echo "analyze xong:"
    echo "  spotbugs.xml      $(wc -c < "$OUT_DIR/spotbugs.xml") byte"
    echo "  jacoco.xml        $(wc -c < "$OUT_DIR/jacoco.xml") byte"
    echo "  surefire reports  $SO_BAO_CAO"
}

case "$MODE" in
    resolve) do_resolve ;;
    analyze) do_analyze ;;
    *) usage ;;
esac
