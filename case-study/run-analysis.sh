#!/usr/bin/env bash
# Chạy toàn bộ pipeline phân tích trên một dự án case study và thu artifact về docs/samples/.
#
#   ./run-analysis.sh vulnshop
#
# Bốn nguồn bằng chứng sinh ra ở đây chính là đầu vào của Unified Evidence Model:
#   STATIC_WARNING          <- spotbugs.xml, semgrep.sarif
#   TEST_RESULT/FAILED_TEST <- surefire-reports/TEST-*.xml
#   STACK_TRACE             <- cũng trong Surefire XML
#   COVERAGE_INFORMATION    <- jacoco.xml
set -euo pipefail

PROJECT="${1:-vulnshop}"
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
PROJECT_DIR="$SCRIPT_DIR/$PROJECT"
SAMPLES_DIR="$REPO_ROOT/docs/samples/$PROJECT"

if [ ! -d "$PROJECT_DIR" ]; then
    echo "Khong tim thay du an: $PROJECT_DIR" >&2
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
mvn -B -q test

echo "==> [2/4] Chay SpotBugs (kem find-sec-bugs)"
mvn -B -q spotbugs:spotbugs

echo "==> [3/4] Chay Semgrep voi rule da vendor (offline)"
MSYS_NO_PATHCONV=1 docker run --rm --network=none \
    -v "//${SCRIPT_DIR//:/}:/work" \
    semgrep/semgrep \
    semgrep scan --config="/work/semgrep-rules/p-java.yaml" \
        --sarif --output="/work/$PROJECT/target/semgrep.sarif" \
        --metrics=off "/work/$PROJECT/src" >/dev/null

echo "==> [4/4] Thu artifact ve $SAMPLES_DIR"
mkdir -p "$SAMPLES_DIR/surefire-reports"
cp "$PROJECT_DIR/target/spotbugsXml.xml"          "$SAMPLES_DIR/spotbugs.xml"
cp "$PROJECT_DIR/target/semgrep.sarif"            "$SAMPLES_DIR/semgrep.sarif"
cp "$PROJECT_DIR/target/site/jacoco/jacoco.xml"   "$SAMPLES_DIR/jacoco.xml"
cp "$PROJECT_DIR"/target/surefire-reports/TEST-*.xml "$SAMPLES_DIR/surefire-reports/"

echo
echo "Xong. Kiem tra nhanh:"
python "$SCRIPT_DIR/summarize.py" "$PROJECT" 2>/dev/null || \
    echo "  (chay 'python case-study/summarize.py $PROJECT' de xem thong ke)"
