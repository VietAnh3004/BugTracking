#!/usr/bin/env bash
# Chạy toàn bộ pipeline phân tích trên một dự án case study và thu artifact về docs/samples/.
#
#   ./run-analysis.sh vulnshop            # chạy và in thống kê
#   ./run-analysis.sh vulnshop --check    # thêm bước đối chiếu expected-analysis.json
#
# Bốn nguồn bằng chứng sinh ra ở đây chính là đầu vào của Unified Evidence Model:
#   STATIC_WARNING          <- spotbugs.xml, semgrep.sarif
#   TEST_RESULT/FAILED_TEST <- surefire-reports/TEST-*.xml
#   STACK_TRACE             <- cũng trong Surefire XML
#   COVERAGE_INFORMATION    <- jacoco.xml
set -euo pipefail

PROJECT="${1:-vulnshop}"
CHECK="${2:-}"

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
PROJECT_DIR="$SCRIPT_DIR/$PROJECT"
SAMPLES_DIR="$REPO_ROOT/docs/samples/$PROJECT"

if [ ! -d "$PROJECT_DIR" ]; then
    echo "Khong tim thay du an: $PROJECT_DIR" >&2
    exit 1
fi

# Git Bash tren Windows tu chuyen doi duong dan khi goi docker, phai tat di va dung dang
# //d/study/... thay vi D:/study/... Tren Linux (CI) thi dung nguyen duong dan.
#
# QUAN TRONG: MSYS_NO_PATHCONV chi duoc dat inline cho rieng lenh docker. Export no ra ca
# script se lam hong Maven (mvn la shell script, no can path conversion de tu tim thu vien)
# voi loi ClassNotFoundException: ...plexus.classworlds.launcher.Launcher.
case "$(uname -s)" in
    MINGW* | MSYS* | CYGWIN*)
        DOCKER_MOUNT="//${SCRIPT_DIR//:/}"
        DOCKER_ENV="MSYS_NO_PATHCONV=1"
        ;;
    *)
        DOCKER_MOUNT="$SCRIPT_DIR"
        DOCKER_ENV=""
        ;;
esac

# `python3` la App Execution Alias hong tren may Windows nay; `python` moi la ban that.
# Tren Linux thi nguoc lai. Thu lan luot va kiem tra chay duoc that.
PYTHON=""
for candidate in python python3; do
    if command -v "$candidate" >/dev/null 2>&1 && "$candidate" -c "" >/dev/null 2>&1; then
        PYTHON="$candidate"
        break
    fi
done
if [ -z "$PYTHON" ]; then
    echo "Khong tim thay Python chay duoc." >&2
    exit 1
fi

# JDK 21 la bat buoc: SpotBugs khong doc duoc class file cua JDK moi hon va tra ve 0 canh bao
# ma van bao "thanh cong". Xem docs/adr/0003-chuan-hoa-jdk-21.md.
JAVA_VERSION="$(java -version 2>&1 | head -1 | sed -E 's/.*"([0-9]+).*/\1/')"
if [ "$JAVA_VERSION" != "21" ]; then
    echo "CANH BAO: dang chay JDK $JAVA_VERSION, du an yeu cau JDK 21." >&2
    echo "  export JAVA_HOME=\"C:\\Program Files\\Java\\jdk-21.0.12\"" >&2
    exit 1
fi

echo "==> [1/4] Chay test (Surefire + JaCoCo)"
cd "$PROJECT_DIR"
# `clean` la bat buoc, khong phai tuy chon: class file cu con lai trong target/ tu lan chay
# truoc lam JaCoCo dem sai so dong duoc phu (da gap that - 82/171 tut thanh 83/173 chi vi
# chay lai ma khong don). Script nay sinh BANG CHUNG cho do an, nen phai tai lap duoc tu
# trang thai sach moi lan, khong duoc phu thuoc build truoc do con sot lai gi.
mvn -B -q clean test

echo "==> [2/4] Chay SpotBugs (kem find-sec-bugs)"
mvn -B -q spotbugs:spotbugs

echo "==> [3/4] Chay Semgrep voi rule da vendor (offline)"
env $DOCKER_ENV docker run --rm --network=none \
    -v "${DOCKER_MOUNT}:/work" \
    semgrep/semgrep \
    semgrep scan --config="/work/semgrep-rules/p-java.yaml" \
        --sarif --output="/work/$PROJECT/target/semgrep.sarif" \
        --metrics=off "/work/$PROJECT/src" >/dev/null

echo "==> [4/4] Thu artifact ve docs/samples/$PROJECT"
mkdir -p "$SAMPLES_DIR/surefire-reports"
cp "$PROJECT_DIR/target/spotbugsXml.xml"          "$SAMPLES_DIR/spotbugs.xml"
cp "$PROJECT_DIR/target/semgrep.sarif"            "$SAMPLES_DIR/semgrep.sarif"
cp "$PROJECT_DIR/target/site/jacoco/jacoco.xml"   "$SAMPLES_DIR/jacoco.xml"
cp "$PROJECT_DIR"/target/surefire-reports/TEST-*.xml "$SAMPLES_DIR/surefire-reports/"

echo
cd "$REPO_ROOT"
if [ "$CHECK" = "--check" ]; then
    exec "$PYTHON" "$SCRIPT_DIR/summarize.py" "$PROJECT" --check
else
    "$PYTHON" "$SCRIPT_DIR/summarize.py" "$PROJECT"
fi
