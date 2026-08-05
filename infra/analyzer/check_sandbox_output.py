#!/usr/bin/env python
"""Doi chieu artifact sandbox sinh ra voi expected-analysis.json.

Tieu chi nghiem thu cua giai doan D (ADR 0008): sandbox phai cho DUNG CUNG so lieu nhu
run-analysis.sh chay tren host. Lech mot con so la co gi do sai.

Script nay cung kiem luon BON TANG chong "chay xong nhung 0 canh bao" tren du lieu that:
  tang 1  artifact ton tai, khac rong, parse duoc
  tang 2  locator cau truc trong chinh artifact (total_classes, java_version, errors, plugin)
  tang 3  canary — ma moi trong image phai ra du finding cua CA HAI bo rule
  tang 4  (thuoc ve backend) quyet dinh trang thai — khong kiem o day

Chay: python check_sandbox_output.py <thu-muc-artifact> <expected-analysis.json>
"""

import json
import sys
import xml.etree.ElementTree as ET
from pathlib import Path

# Package cua ma canary. Finding thuoc package nay KHONG phai canh bao cua repo nguoi dung.
CANARY_PACKAGE = "evidence.canary"

# Canary phai ra du CA HAI bo rule. Thieu mot bo la mot kieu hong khac han:
#   - thieu find-sec-bugs  -> mat toan bo SQL injection / weak crypto / path traversal
#   - thieu SpotBugs core  -> plugin nap duoc nhung engine goc hong
CANARY_FINDSECBUGS = {"WEAK_MESSAGE_DIGEST_MD5", "SQL_INJECTION_JDBC"}
CANARY_CORE = {"DM_DEFAULT_ENCODING"}


class Failure(Exception):
    pass


def _require_file(path: Path) -> Path:
    """Tang 1: ton tai, khac rong. Thieu file => FAILED, KHONG phai '0 finding'."""
    if not path.exists():
        raise Failure(f"thieu artifact {path.name} — day la run FAILED, khong phai '0 canh bao'")
    if path.stat().st_size == 0:
        raise Failure(f"artifact {path.name} rong")
    return path


def _direct_children(parent, tag):
    """Element.getElementsByTagName cua Java la DE QUY; findall cua Python thi khong.

    Giu cung ngu nghia voi XmlParsers.directChildren ben Java de hai ben dem ra cung mot con so.
    """
    return [child for child in parent if child.tag == tag]


def check_spotbugs(out_dir: Path, expected: dict) -> int:
    root = ET.parse(_require_file(out_dir / "spotbugs.xml")).getroot()

    # --- Tang 2: locator cau truc ---
    summary = root.find("FindBugsSummary")
    if summary is None:
        raise Failure("spotbugs.xml khong co <FindBugsSummary>")

    total_classes = int(summary.get("total_classes") or 0)
    if total_classes <= 0:
        raise Failure("SpotBugs quet 0 class — no chay nhung khong doc duoc bytecode nao")

    java_version = summary.get("java_version") or ""
    if not java_version.startswith("21."):
        # ADR 0003 thanh assertion tren DU LIEU, khong chi mot cau trong tai lieu.
        raise Failure(
            f"SpotBugs chay tren JDK {java_version!r}, phai la 21.x — "
            "JDK moi hon cho 0 canh bao ma van bao thanh cong"
        )

    errors = root.find("Errors")
    n_errors = int(errors.get("errors") or 0) if errors is not None else 0
    if n_errors:
        raise Failure(f"SpotBugs bao {n_errors} loi khi phan tich")
    # CO Y khong kiem missingClasses: do la tin hieu TUONG DOI. 6 la san cua chinh vulnshop, va
    # no nhay len 13 khi thieu auxclasspath — nhung moi nguong tuyet doi deu la bao dong gia cho
    # repo khac. Xem docs/spike-spotbugs-cli.md.

    plugin_ids = {p.get("id") for p in root.iter("Plugin")}
    if "com.h3xstream.findsecbugs" not in plugin_ids:
        raise Failure(f"find-sec-bugs khong duoc nap (plugin da nap: {sorted(plugin_ids)})")

    # --- Tang 3: canary ---
    canary_types, user_findings = set(), 0
    for bug in root.findall("BugInstance"):
        classes = [c.get("classname") or "" for c in bug.iter("Class")]
        if any(c.startswith(CANARY_PACKAGE) for c in classes):
            canary_types.add(bug.get("type"))
        else:
            user_findings += 1

    thieu_fsb = CANARY_FINDSECBUGS - canary_types
    thieu_core = CANARY_CORE - canary_types
    if thieu_fsb:
        raise Failure(f"canary thieu finding cua find-sec-bugs: {sorted(thieu_fsb)}")
    if thieu_core:
        raise Failure(f"canary thieu finding cua SpotBugs core: {sorted(thieu_core)}")

    # --- Doi chieu ground truth ---
    mong_doi = expected["spotbugs"]["findings"]
    if user_findings != mong_doi:
        raise Failure(
            f"SpotBugs ra {user_findings} canh bao, mong doi {mong_doi}. "
            "Neu con so nay doi that thi phai hieu VI SAO truoc khi sua expected-analysis.json."
        )

    print(f"  OK  spotbugs  {user_findings} canh bao nguoi dung, "
          f"{len(canary_types)} loai canary, {total_classes} class, JDK {java_version}")
    return user_findings


def check_semgrep(out_dir: Path, expected: dict) -> int:
    sarif = json.loads(_require_file(out_dir / "semgrep.sarif").read_text(encoding="utf-8"))

    runs = sarif.get("runs") or []
    if not runs:
        raise Failure("semgrep.sarif khong co run nao")

    results, rules = 0, 0
    for run in runs:
        invocations = run.get("invocations") or []
        if invocations and not invocations[0].get("executionSuccessful", False):
            raise Failure("Semgrep bao executionSuccessful=false")
        n_rules = len(((run.get("tool") or {}).get("driver") or {}).get("rules") or [])
        if n_rules == 0:
            # SARIF van hoan toan hop le voi 0 rule — day dung la kieu hong im lang can chan.
            raise Failure("Semgrep nap 0 rule — ruleset khong doc duoc, SARIF van hop le")
        rules += n_rules
        results += len(run.get("results") or [])

    mong_doi = expected["semgrep"]["findings"]
    if results != mong_doi:
        raise Failure(f"Semgrep ra {results} canh bao, mong doi {mong_doi}")

    print(f"  OK  semgrep   {results} canh bao, {rules} rule da nap")
    return results


def check_tests(out_dir: Path, expected: dict) -> None:
    reports = sorted((out_dir / "surefire-reports").glob("TEST-*.xml"))
    if not reports:
        raise Failure("khong co bao cao Surefire nao")

    tong = hong = 0
    for report in reports:
        suite = ET.parse(report).getroot()
        for case in _direct_children(suite, "testcase"):
            tong += 1
            if _direct_children(case, "failure") or _direct_children(case, "error"):
                hong += 1

    if tong != expected["tests"]["total"]:
        raise Failure(f"{tong} test, mong doi {expected['tests']['total']}")
    if hong != expected["tests"]["failed"]:
        # Nam test hong la DU LIEU DAU VAO cua do an, khong phai loi can sua.
        raise Failure(f"{hong} test hong, mong doi {expected['tests']['failed']}")

    print(f"  OK  surefire  {tong} test ({hong} hong) trong {len(reports)} bao cao")


def check_coverage(out_dir: Path, expected: dict) -> None:
    path = out_dir / "jacoco.xml"
    if not path.exists():
        raise Failure("thieu jacoco.xml — agent khong attach duoc? (argLine bi ghi de)")

    # jacoco.xml co <!DOCTYPE ... "report.dtd"> ma report.dtd KHONG ton tai canh no. ElementTree
    # khong tu tai DTD ngoai nen doc duoc; ben Java phai dung factory noi hon (xem XmlParsers).
    root = ET.parse(path).getroot()

    tong = phu = 0
    for source_file in root.iter("sourcefile"):
        for line in _direct_children(source_file, "line"):
            tong += 1
            # "Duoc phu" theo JaCoCo la ci > 0 = COVERED + PARTIAL. Dem moi COVERED ra 77 la sai.
            if int(line.get("ci") or 0) > 0:
                phu += 1

    if tong == 0:
        raise Failure("jacoco.xml co 0 dong duoc instrument — agent khong attach duoc, "
                      "KHONG phai 'do phu 0%'")
    if tong != expected["coverage"]["total_lines"]:
        raise Failure(f"{tong} dong, mong doi {expected['coverage']['total_lines']}")
    if phu != expected["coverage"]["covered_lines"]:
        raise Failure(f"{phu} dong duoc phu, mong doi {expected['coverage']['covered_lines']}")

    print(f"  OK  jacoco    {phu}/{tong} dong duoc phu")


def main() -> int:
    if len(sys.argv) != 3:
        print(__doc__, file=sys.stderr)
        return 2

    out_dir = Path(sys.argv[1])
    expected = json.loads(Path(sys.argv[2]).read_text(encoding="utf-8"))

    print(f"Doi chieu {out_dir} voi expected-analysis.json:")
    try:
        check_spotbugs(out_dir, expected)
        check_semgrep(out_dir, expected)
        check_tests(out_dir, expected)
        check_coverage(out_dir, expected)
    except Failure as exc:
        print(f"\nLECH: {exc}", file=sys.stderr)
        return 1

    print("\nKHOP: sandbox cho dung cung so lieu nhu run-analysis.sh chay tren host.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
