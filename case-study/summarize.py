"""Thống kê nhanh artifact phân tích của một dự án case study.

    python summarize.py vulnshop

Dùng để kiểm tra sau mỗi lần chạy lại pipeline: số liệu lệch khỏi con số kỳ vọng ghi trong
SEEDED.md nghĩa là môi trường đã đổi, cần tìm nguyên nhân trước khi dùng dữ liệu.

Đây cũng là bản nháp đầu tiên của logic mà StaticAnalysisAdapter và TestEvidenceCollector sẽ
hiện thực ở tuần 8-9.
"""

from __future__ import annotations

import json
import re
import sys
import xml.etree.ElementTree as ET
from collections import Counter
from pathlib import Path

STACK_FRAME = re.compile(r"at ([\w.$]+)\.(\w+)\(([\w.]+):(\d+)\)")


def summarize_spotbugs(path: Path) -> None:
    if not path.exists():
        print("  spotbugs.xml: KHONG CO")
        return

    root = ET.parse(path).getroot()
    bugs = root.findall("BugInstance")
    plugins = root.findall(".//Plugin")

    print(f"  SpotBugs: {len(bugs)} canh bao, {len(plugins)} plugin duoc nap")
    # 0 plugin nghia la find-sec-bugs khong nap duoc -> mat toan bo canh bao bao mat
    # ma phan tich van "thanh cong". Da gap loi nay mot lan, xem SEEDED.md.
    if not plugins:
        print("    CANH BAO: khong plugin nao duoc nap, thieu rule bao mat!")

    for bug_type, count in Counter(b.get("type") for b in bugs).most_common():
        print(f"    {bug_type:<42} {count}")


def summarize_semgrep(path: Path) -> None:
    if not path.exists():
        print("  semgrep.sarif: KHONG CO")
        return

    data = json.loads(path.read_text(encoding="utf-8"))
    results = [r for run in data["runs"] for r in run["results"]]
    print(f"  Semgrep: {len(results)} canh bao")
    for rule, count in Counter(r["ruleId"].split(".")[-1] for r in results).most_common():
        print(f"    {rule:<42} {count}")


def summarize_surefire(directory: Path) -> None:
    if not directory.exists():
        print("  surefire-reports: KHONG CO")
        return

    total = failed = 0
    frames: list[str] = []

    for report in sorted(directory.glob("TEST-*.xml")):
        root = ET.parse(report).getroot()
        for case in root.iter("testcase"):
            total += 1
            problem = case.find("failure")
            if problem is None:
                problem = case.find("error")
            if problem is None:
                continue

            failed += 1
            trace = problem.text or ""
            for cls, method, _file, line in STACK_FRAME.findall(trace):
                # Chi giu frame thuoc ma production; frame cua JUnit va JDK la nhieu.
                if "casestudy" in cls and not cls.endswith("Test"):
                    frames.append(f"{cls}.{method}:{line}")
                    break

    print(f"  Test: {total} test, {failed} hong")
    for frame in frames:
        print(f"    stack trace -> {frame}")


def summarize_jacoco(path: Path) -> None:
    if not path.exists():
        print("  jacoco.xml: KHONG CO")
        return

    root = ET.parse(path).getroot()
    covered = total = 0
    uncovered_files: list[str] = []

    for package in root.iter("package"):
        for sourcefile in package.iter("sourcefile"):
            lines = list(sourcefile.iter("line"))
            if not lines:
                continue
            file_covered = sum(1 for line in lines if int(line.get("ci", 0)) > 0)
            covered += file_covered
            total += len(lines)
            if file_covered == 0:
                uncovered_files.append(sourcefile.get("name", "?"))

    percent = 100 * covered / total if total else 0
    print(f"  Do phu: {covered}/{total} dong ({percent:.1f}%)")
    # Vung khong duoc phu la CO CHU DICH: canh bao o do khong co bang chung kiem thu,
    # tao ra truong hop INSUFFICIENT_EVIDENCE cho he thong danh gia.
    print(f"    File khong duoc phu ({len(uncovered_files)}): {', '.join(sorted(uncovered_files))}")


def main() -> int:
    project = sys.argv[1] if len(sys.argv) > 1 else "vulnshop"
    samples = Path(__file__).parent.parent / "docs" / "samples" / project

    if not samples.exists():
        print(f"Khong tim thay {samples}. Chay ./run-analysis.sh {project} truoc.", file=sys.stderr)
        return 1

    print(f"Thong ke artifact cua '{project}':")
    summarize_spotbugs(samples / "spotbugs.xml")
    summarize_semgrep(samples / "semgrep.sarif")
    summarize_surefire(samples / "surefire-reports")
    summarize_jacoco(samples / "jacoco.xml")
    return 0


if __name__ == "__main__":
    sys.exit(main())
