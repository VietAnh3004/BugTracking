"""Thống kê và đối chiếu artifact phân tích của một dự án case study.

    python summarize.py vulnshop              # xem thống kê
    python summarize.py vulnshop --check      # đối chiếu với expected-analysis.json, lệch thì exit 1

Chế độ --check là thứ CI chạy. Nó cần thiết vì `mvn verify` một mình không đủ: pom của vulnshop
đặt testFailureIgnore=true (5 test hỏng là dữ liệu đầu vào của đồ án, không phải lỗi), nên
vulnshop hỏng thế nào build cũng xanh. Chỉ so sánh số liệu mới phát hiện được.

Đây cũng là bản nháp đầu tiên của logic mà StaticAnalysisAdapter và TestEvidenceCollector sẽ
hiện thực ở tuần 8-9.
"""

from __future__ import annotations

import argparse
import json
import re
import sys
import xml.etree.ElementTree as ET
from collections import Counter
from pathlib import Path
from typing import Any

STACK_FRAME = re.compile(r"at ([\w.$]+)\.(\w+)\(([\w.]+):(\d+)\)")

REPO_ROOT = Path(__file__).resolve().parent.parent


class MissingArtifact(Exception):
    """Thiếu file artifact cần thiết — chạy lại run-analysis.sh."""


# --------------------------------------------------------------------------- thu thập


def collect_spotbugs(path: Path) -> dict[str, Any]:
    if not path.exists():
        raise MissingArtifact(f"Khong tim thay {path.name}")

    root = ET.parse(path).getroot()
    bugs = root.findall("BugInstance")

    return {
        "findings": len(bugs),
        # 0 plugin nghia la find-sec-bugs khong nap duoc -> mat toan bo canh bao bao mat
        # (28 tut xuong 14) ma phan tich van "thanh cong". Da gap that mot lan, xem SEEDED.md.
        "plugins": len(root.findall(".//Plugin")),
        "by_type": Counter(b.get("type") for b in bugs),
    }


def collect_semgrep(path: Path) -> dict[str, Any]:
    if not path.exists():
        raise MissingArtifact(f"Khong tim thay {path.name}")

    data = json.loads(path.read_text(encoding="utf-8"))
    results = [r for run in data["runs"] for r in run["results"]]

    return {
        "findings": len(results),
        "by_rule": Counter(r["ruleId"].split(".")[-1] for r in results),
    }


def collect_surefire(directory: Path) -> dict[str, Any]:
    reports = sorted(directory.glob("TEST-*.xml")) if directory.exists() else []
    if not reports:
        raise MissingArtifact("Khong tim thay bao cao Surefire")

    total = failed = 0
    frames: list[str] = []

    for report in reports:
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

    return {"total": total, "failed": failed, "production_frames": frames}


def collect_jacoco(path: Path) -> dict[str, Any]:
    if not path.exists():
        raise MissingArtifact(f"Khong tim thay {path.name}")

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

    return {
        "covered_lines": covered,
        "total_lines": total,
        # Vung khong duoc phu la CO CHU DICH: canh bao o do khong co bang chung kiem thu,
        # tao ra truong hop INSUFFICIENT_EVIDENCE cho he thong danh gia.
        "uncovered_files": sorted(uncovered_files),
    }


def collect_all(samples: Path) -> dict[str, Any]:
    return {
        "spotbugs": collect_spotbugs(samples / "spotbugs.xml"),
        "semgrep": collect_semgrep(samples / "semgrep.sarif"),
        "tests": collect_surefire(samples / "surefire-reports"),
        "coverage": collect_jacoco(samples / "jacoco.xml"),
    }


# --------------------------------------------------------------------------- hiển thị


def print_summary(metrics: dict[str, Any]) -> None:
    spotbugs = metrics["spotbugs"]
    print(f"  SpotBugs: {spotbugs['findings']} canh bao, {spotbugs['plugins']} plugin duoc nap")
    if not spotbugs["plugins"]:
        print("    CANH BAO: khong plugin nao duoc nap, thieu rule bao mat!")
    for bug_type, count in spotbugs["by_type"].most_common():
        print(f"    {bug_type:<42} {count}")

    semgrep = metrics["semgrep"]
    print(f"  Semgrep: {semgrep['findings']} canh bao")
    for rule, count in semgrep["by_rule"].most_common():
        print(f"    {rule:<42} {count}")

    tests = metrics["tests"]
    print(f"  Test: {tests['total']} test, {tests['failed']} hong")
    for frame in tests["production_frames"]:
        print(f"    stack trace -> {frame}")

    coverage = metrics["coverage"]
    percent = 100 * coverage["covered_lines"] / coverage["total_lines"]
    print(f"  Do phu: {coverage['covered_lines']}/{coverage['total_lines']} dong ({percent:.1f}%)")
    uncovered = coverage["uncovered_files"]
    print(f"    File khong duoc phu ({len(uncovered)}): {', '.join(uncovered)}")


# --------------------------------------------------------------------------- đối chiếu


def check(metrics: dict[str, Any], expected: dict[str, Any]) -> list[str]:
    """Trả về danh sách sai lệch; rỗng nghĩa là khớp hoàn toàn."""
    mismatches: list[str] = []

    for group, fields in expected.items():
        if group.startswith("_"):
            continue
        for field, want in fields.items():
            got = metrics[group][field]
            if got != want:
                mismatches.append(f"{group}.{field}: ky vong {want}, thuc te {got}")

    return mismatches


def explain(mismatches: list[str]) -> None:
    """Gợi ý nguyên nhân cho các sai lệch hay gặp, thay vì chỉ báo số lệch."""
    joined = " ".join(mismatches)

    if "spotbugs.plugins" in joined:
        print("\n  -> find-sec-bugs khong nap duoc. Kiem tra pom: plugin phai khai bao trong")
        print("     <configuration><plugins>, KHONG phai <dependencies> cua plugin.")
    if "spotbugs.findings" in joined:
        print("\n  -> So canh bao SpotBugs doi. Neu tut manh, kiem tra phien ban JDK:")
        print("     JDK moi hon 21 lam SpotBugs tra ve 0 canh bao ma van bao thanh cong.")
    if "semgrep.findings" in joined:
        print("\n  -> So canh bao Semgrep doi. Kiem tra case-study/semgrep-rules/p-java.yaml")
        print("     co bi cap nhat khong - bo rule doi thi ket qua doi theo.")
    if "tests." in joined:
        print("\n  -> So test doi. Nam test hong la CO CHU DICH (nguon FAILED_TEST va")
        print("     STACK_TRACE cho do an), khong phai loi can sua. Xem vulnshop/SEEDED.md.")


# --------------------------------------------------------------------------- main


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("project", nargs="?", default="vulnshop")
    parser.add_argument(
        "--check",
        action="store_true",
        help="Doi chieu voi expected-analysis.json, lech thi exit 1",
    )
    args = parser.parse_args()

    samples = REPO_ROOT / "docs" / "samples" / args.project
    if not samples.exists():
        print(f"Khong tim thay {samples}.", file=sys.stderr)
        print(f"Chay ./case-study/run-analysis.sh {args.project} truoc.", file=sys.stderr)
        return 1

    try:
        metrics = collect_all(samples)
    except MissingArtifact as e:
        print(f"Thieu artifact: {e}", file=sys.stderr)
        print(f"Chay ./case-study/run-analysis.sh {args.project} truoc.", file=sys.stderr)
        return 1

    print(f"Thong ke artifact cua '{args.project}':")
    print_summary(metrics)

    if not args.check:
        return 0

    expected_path = REPO_ROOT / "case-study" / args.project / "expected-analysis.json"
    if not expected_path.exists():
        print(f"\nKhong tim thay {expected_path}", file=sys.stderr)
        return 1

    expected = json.loads(expected_path.read_text(encoding="utf-8"))
    mismatches = check(metrics, expected)

    print()
    if not mismatches:
        print("KHOP: moi so lieu dung nhu expected-analysis.json")
        return 0

    print(f"LECH {len(mismatches)} cho so voi expected-analysis.json:")
    for mismatch in mismatches:
        print(f"  - {mismatch}")
    explain(mismatches)
    print("\nNeu so lieu doi la co chu dich, cap nhat expected-analysis.json va ghi ro ly do")
    print("vao commit message.")
    return 1


if __name__ == "__main__":
    sys.exit(main())
