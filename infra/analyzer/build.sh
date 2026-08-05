#!/usr/bin/env bash
#
# Dựng hai image analyzer VÀ đối chiếu chúng với `expected-analysis.json`.
#
#   ./infra/analyzer/build.sh            # dựng + đối chiếu (mặc định)
#   ./infra/analyzer/build.sh --no-check # chỉ dựng
#
# Vì sao bước đối chiếu nằm ngay trong script dựng, không phải một việc riêng ai đó nhớ thì chạy:
# một image analyzer "dựng thành công" nhưng cho ra 0 cảnh báo là đúng thứ mà cả đồ án chống. Có
# đủ ground truth (28 SpotBugs + 5 Semgrep trên vulnshop) mà không đối chiếu thì phí.
#
# Script này CỐ Ý dùng `docker create` + `docker cp` + `docker start`, KHÔNG dùng bind mount — y
# hệt cách DockerSandboxRunner sẽ làm ở D6. Nhờ vậy nó vừa là bước dựng, vừa là bản kiểm chứng
# cơ chế cp-vào/cp-ra trước khi viết code Java (xem ADR 0008 mục 2 để biết vì sao bind mount là
# cái bẫy: daemon giải đường dẫn theo filesystem của HOST, không có thì lặng lẽ tạo thư mục rỗng
# và analyzer quét 0 class rồi exit 0).
set -euo pipefail

CHECK=1
[ "${1:-}" = "--no-check" ] && CHECK=0

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"

IMAGE_JAVA=evidence/analyzer-java:latest
IMAGE_SEMGREP=evidence/analyzer-semgrep:latest

# Git Bash có HAI cái bẫy đường dẫn NGƯỢC CHIỀU nhau khi gọi docker, và chỉ tắt path conversion
# thì hỏng cái còn lại:
#
#   - Đường dẫn TRONG CONTAINER (`/work`, `name:/work/repo`) bị MSYS biến thành
#     `C:/Program Files/Git/work` → phải MSYS_NO_PATHCONV=1.
#   - Đường dẫn TRÊN HOST (build context, nguồn của `docker cp`) ở dạng `/d/study/...` thì daemon
#     Windows không hiểu → phải chuyển sang `D:/study/...` bằng `cygpath -m`.
#
# Vì vậy: bật MSYS_NO_PATHCONV cho MỌI lệnh docker, và chuyển tay mọi đường dẫn host qua hostpath().
# Đã vấp: đặt MSYS_NO_PATHCONV=1 cho `docker build` với context tuyệt đối cho ra
# `unable to prepare context: path "/d/study/..." not found`.
case "$(uname -s)" in
    MINGW* | MSYS* | CYGWIN*)
        DOCKER_ENV=(env MSYS_NO_PATHCONV=1)
        hostpath() { cygpath -m "$1"; }
        ;;
    *)
        DOCKER_ENV=()
        hostpath() { printf '%s' "$1"; }
        ;;
esac
docker_() { "${DOCKER_ENV[@]}" docker "$@"; }

echo "==> [1/3] Dung $IMAGE_JAVA"
docker_ build -t "$IMAGE_JAVA" "$(hostpath "$SCRIPT_DIR/java")"

echo "==> [2/3] Dung $IMAGE_SEMGREP"
# Context la thu muc ruleset da vendor, Dockerfile truyen bang -f: ruleset song trong
# case-study/ (co lich su Git) chu khong chep sang infra/ thanh ban thu hai troi dan.
docker_ build -t "$IMAGE_SEMGREP" \
    -f "$(hostpath "$SCRIPT_DIR/semgrep/Dockerfile")" \
    "$(hostpath "$REPO_ROOT/case-study/semgrep-rules")"

if [ "$CHECK" = "0" ]; then
    echo "Da dung xong hai image (bo qua doi chieu)."
    exit 0
fi

echo "==> [3/3] Doi chieu tren case-study/vulnshop"

WORK_TMP="$(mktemp -d)"
CONTAINERS=()
cleanup() {
    for c in "${CONTAINERS[@]:-}"; do
        [ -n "$c" ] && docker_ rm -f "$c" >/dev/null 2>&1 || true
    done
    rm -rf "$WORK_TMP"
}
trap cleanup EXIT

# Cây nguồn gửi vào sandbox: chỉ những gì có trong repo người dùng, KHÔNG có target/ (nó là sản
# phẩm biên dịch của máy phát triển; để lẫn vào sẽ khiến sandbox "thành công" nhờ class có sẵn
# thay vì nhờ chính nó biên dịch được).
mkdir -p "$WORK_TMP/repo"
( cd "$REPO_ROOT/case-study/vulnshop" \
  && find . -type f -not -path './target/*' -exec cp --parents {} "$WORK_TMP/repo/" \; )

VOLUME="evidence-verify-$$"
docker_ volume create "$VOLUME" >/dev/null

run_java_phase() {
    local phase="$1" network="$2"
    local name="evidence-verify-${phase}-$$"
    CONTAINERS+=("$name")
    docker_ create --name "$name" \
        --network="$network" \
        --cap-drop=ALL --security-opt=no-new-privileges \
        --pids-limit=512 --memory=2g --memory-swap=2g --cpus=2 \
        -v "$VOLUME:/work" \
        "$IMAGE_JAVA" /opt/analyze.sh "$phase" /work/repo >/dev/null

    # Chỉ pha đầu mới cần đẩy nguồn vào; pha sau dùng lại volume.
    if [ "$phase" = "resolve" ]; then
        # `docker cp <thu muc>` gan quyen ROOT cho moi file chep vao, ke ca khi container chay
        # non-root. Container sau do khong ghi duoc `target/` va Maven chet voi
        # "Cannot create resource output directory" — mot loi tro ve trinh bien dich, khong he
        # nhac toi quyen, nen rat de di sua nham cho.
        #
        # Cach dung: tu dung tar voi uid/gid CUA USER TRONG IMAGE roi dua qua stdin (`docker cp -`
        # giu nguyen uid/gid trong tar). DockerSandboxRunner o D6 dung y het co che nay.
        # Dich la `/work` chu khong phai `/work/repo`, va tar mang san tien to `repo/`:
        # `docker cp -` doi thu muc DICH phai ton tai san, ma volume moi chi co `/work`.
        tar --owner=10001 --group=10001 --numeric-owner \
            -cf - -C "$WORK_TMP" repo \
            | docker_ cp - "$name:/work"
    fi
    docker_ start -a "$name"
    local code
    code="$(docker_ inspect -f '{{.State.ExitCode}}' "$name")"
    [ "$code" = "0" ] || { echo "Pha $phase hong voi exit code $code" >&2; exit 1; }
}

echo "--- pha resolve (co mang) ---"
run_java_phase resolve bridge
echo "--- pha analyze (khong mang) ---"
run_java_phase analyze none

echo "--- semgrep (khong mang) ---"
SEMGREP_NAME="evidence-verify-semgrep-$$"
CONTAINERS+=("$SEMGREP_NAME")
docker_ create --name "$SEMGREP_NAME" \
    --network=none --cap-drop=ALL --security-opt=no-new-privileges \
    --pids-limit=512 --memory=2g --memory-swap=2g \
    -v "$VOLUME:/work" \
    "$IMAGE_SEMGREP" /work/repo >/dev/null
docker_ start -a "$SEMGREP_NAME" || true

echo "--- thu artifact ---"
OUT="$WORK_TMP/out"
mkdir -p "$OUT"
docker_ cp "$SEMGREP_NAME:/work/.analysis/." "$(hostpath "$OUT")"

python "$SCRIPT_DIR/check_sandbox_output.py" \
    "$(hostpath "$OUT")" \
    "$(hostpath "$REPO_ROOT/case-study/vulnshop/expected-analysis.json")"
