"""Spike tuần 1: ba nguồn bằng chứng có trỏ về cùng một file:line không?

Câu hỏi này quyết định toàn bộ Unified Evidence Model. Nếu stack trace của test hỏng và số
liệu độ phủ không quy chiếu được về cùng vị trí với cảnh báo phân tích tĩnh thì cơ chế liên
kết warning-code-test phải thiết kế lại.

    python check_alignment.py

Đọc target/surefire-reports/*.xml và target/site/jacoco/jacoco.xml, in ra bảng đối chiếu.
"""

from __future__ import annotations

import re
import sys
import xml.etree.ElementTree as ET
from dataclasses import dataclass, field
from pathlib import Path

TARGET = Path(__file__).parent / "target"
PACKAGE_PREFIX = "vn.hust.datn.spike"

# Một dòng stack trace kiểu: at vn.hust.datn.spike.UserRepository.findEmailDomain(UserRepository.java:29)
STACK_FRAME = re.compile(r"at ([\w.$]+)\.(\w+)\(([\w.]+):(\d+)\)")


@dataclass
class TestResult:
    """Một testcase trong báo cáo Surefire."""

    name: str
    classname: str
    status: str
    failure_type: str | None = None
    failure_message: str | None = None
    frames: list[tuple[str, str, int]] = field(default_factory=list)


def parse_surefire(reports_dir: Path) -> list[TestResult]:
    """Đọc kết quả kiểm thử và bóc các frame stack trace thuộc mã nguồn của dự án."""
    results: list[TestResult] = []

    for report in sorted(reports_dir.glob("TEST-*.xml")):
        root = ET.parse(report).getroot()
        for case in root.iter("testcase"):
            problem = case.find("failure") if case.find("failure") is not None else case.find("error")

            if problem is None:
                results.append(
                    TestResult(
                        name=case.get("name", "?"),
                        classname=case.get("classname", "?"),
                        status="PASSED",
                    )
                )
                continue

            trace = problem.text or ""
            frames = [
                (cls, method, int(line))
                for cls, method, _file, line in STACK_FRAME.findall(trace)
                # Chỉ giữ frame thuộc mã của dự án; frame của JUnit và JDK là nhiễu.
                if cls.startswith(PACKAGE_PREFIX)
            ]

            results.append(
                TestResult(
                    name=case.get("name", "?"),
                    classname=case.get("classname", "?"),
                    status="FAILED",
                    failure_type=problem.get("type"),
                    failure_message=problem.get("message"),
                    frames=frames,
                )
            )

    return results


def parse_jacoco(report: Path) -> dict[str, dict[int, str]]:
    """Trả về {đường_dẫn_file: {số_dòng: trạng_thái}} với trạng thái là COVERED/PARTIAL/MISSED."""
    root = ET.parse(report).getroot()
    coverage: dict[str, dict[int, str]] = {}

    for package in root.iter("package"):
        package_path = package.get("name", "")
        for sourcefile in package.iter("sourcefile"):
            path = f"{package_path}/{sourcefile.get('name')}"
            lines: dict[int, str] = {}
            for line in sourcefile.iter("line"):
                number = int(line.get("nr", 0))
                covered_instructions = int(line.get("ci", 0))
                missed_branches = int(line.get("mb", 0))
                covered_branches = int(line.get("cb", 0))

                if covered_instructions == 0:
                    lines[number] = "MISSED"
                elif missed_branches > 0 and covered_branches > 0:
                    lines[number] = "PARTIAL"
                else:
                    lines[number] = "COVERED"
            coverage[path] = lines

    return coverage


def main() -> int:
    surefire_dir = TARGET / "surefire-reports"
    jacoco_report = TARGET / "site" / "jacoco" / "jacoco.xml"

    if not surefire_dir.exists() or not jacoco_report.exists():
        print("Chưa có artifact. Chạy `mvn test` trước.", file=sys.stderr)
        return 1

    tests = parse_surefire(surefire_dir)
    coverage = parse_jacoco(jacoco_report)

    print("=" * 78)
    print("1. TEST_RESULT / FAILED_TEST / STACK_TRACE  (nguồn: Surefire XML)")
    print("=" * 78)
    for test in tests:
        print(f"  [{test.status:6}] {test.classname}.{test.name}")
        if test.failure_type:
            print(f"           kiểu lỗi : {test.failure_type}")
            print(f"           thông điệp: {test.failure_message}")
        for cls, method, line in test.frames:
            print(f"           frame    : {cls}.{method}  ->  dòng {line}")

    print()
    print("=" * 78)
    print("2. COVERAGE_INFORMATION  (nguồn: jacoco.xml)")
    print("=" * 78)
    for path, lines in coverage.items():
        covered = sum(1 for status in lines.values() if status == "COVERED")
        print(f"  {path}: {covered}/{len(lines)} dòng được thực thi")

    print()
    print("=" * 78)
    print("3. ĐỐI CHIẾU: dòng trong stack trace có số liệu độ phủ tương ứng không?")
    print("=" * 78)

    matched = 0
    total = 0
    for test in tests:
        for cls, method, line in test.frames:
            source_path = cls.replace(".", "/") + ".java"
            status = coverage.get(source_path, {}).get(line)

            # JaCoCo chỉ đo mã production, không đo chính các lớp test. Frame nằm trong lớp
            # test vì vậy không có số liệu độ phủ — đó là điều bình thường, không phải lệch.
            if cls.endswith("Test"):
                print(f"  BỎ QUA  {source_path}:{line} ({method}) -> frame thuộc mã test")
                continue

            total += 1
            if status:
                matched += 1
                print(f"  KHỚP    {source_path}:{line} ({method}) -> độ phủ = {status}")
            else:
                print(f"  LỆCH    {source_path}:{line} ({method}) -> không có dữ liệu độ phủ")

    print()
    if total and matched == total:
        print(f"KẾT LUẬN: {matched}/{total} frame mã production khớp với dữ liệu độ phủ.")
        print("Liên kết warning-code-test theo file:line là khả thi.")
    elif total:
        print(f"KẾT LUẬN: chỉ {matched}/{total} frame khớp — cần xem lại cách chuẩn hoá đường dẫn.")
    else:
        print("KẾT LUẬN: không có test nào hỏng, spike chưa kiểm được chân stack trace.")

    return 0


if __name__ == "__main__":
    sys.exit(main())
