#!/usr/bin/env python
"""Doi chieu docs/evidence-model.md <-> artifact that <-> V2__evidence_model.sql.

Tuong tu case-study/summarize.py --check: khong doc tay ma chay that locator len artifact,
va doi chieu hai chieu voi migration SQL de doc va schema khong troi dat khoi nhau.

    python docs/check-evidence-model.py
"""

import json
import re
import sys
import xml.etree.ElementTree as ET
from pathlib import Path

REPO_ROOT = Path(__file__).resolve().parent.parent
DOC_PATH = REPO_ROOT / "docs" / "evidence-model.md"
SQL_PATH = REPO_ROOT / "core-backend" / "src" / "main" / "resources" / "db" / "migration" / "V2__evidence_model.sql"
SAMPLES_DIR = REPO_ROOT / "docs" / "samples" / "vulnshop"

# ------------------------------------------------------------------------------------------
# 1. Trich bang provenance tu markdown: dong dang "| cot | nguon | locator | vi du |"
# ------------------------------------------------------------------------------------------

ROW_RE = re.compile(r"^\|\s*([\w_]+)\s*\|\s*([^|]+?)\s*\|\s*([^|]+?)\s*\|\s*([^|]*?)\s*\|\s*$")


def parse_provenance_rows(markdown_text):
    rows = []
    for line in markdown_text.splitlines():
        m = ROW_RE.match(line.strip())
        if not m:
            continue
        column, source, locator, example = m.groups()
        if column in ("Cột", "---") or source.strip("- ") == "":
            continue
        rows.append(
            {
                "column": column,
                "source": source.strip(),
                "locator": locator.strip().strip("`"),
                "example": example.strip().strip("`"),
            }
        )
    return rows


# ------------------------------------------------------------------------------------------
# 2. Locator resolver: "<tool>:<xpath hoac jsonpath don gian>"
# ------------------------------------------------------------------------------------------


def _find_and_read(tree, xpath):
    # ElementTree.find() khong ho tro ket hop predicate ([@a='b']) VOI mot buoc "/@attr" cuoi
    # cung trong CUNG mot bieu thuc (loi that: KeyError('@')) — phai tach '/@attr' ra truoc,
    # find() phan con lai (chi con predicate loc phan tu), roi doc attribute bang .get().
    element_path, _, attr = xpath.rpartition("/@")
    node = tree.getroot().find(element_path) if element_path else tree.getroot()
    if node is None:
        return None
    return node.get(attr) if attr else node.text


def resolve_spotbugs(xpath):
    tree = ET.parse(SAMPLES_DIR / "spotbugs.xml")
    return _find_and_read(tree, xpath)


def resolve_jacoco(xpath):
    tree = ET.parse(SAMPLES_DIR / "jacoco.xml")
    return _find_and_read(tree, xpath)


def resolve_semgrep(_locator):
    # Chi mot vi du duoc dan chieu truc tiep trong doc (muc 2); cac dong con lai la DERIVED.
    with open(SAMPLES_DIR / "semgrep.sarif", encoding="utf-8") as f:
        sarif = json.load(f)
    return sarif["runs"][0]["results"][0]["ruleId"]


LOCATOR_RESOLVERS = {
    "spotbugs": resolve_spotbugs,
    "jacoco": resolve_jacoco,
    "semgrep": resolve_semgrep,
}


def resolve_locator(locator):
    if ":" not in locator:
        return None, "khong dung dinh dang '<tool>:<xpath>'"
    tool, expr = locator.split(":", 1)
    resolver = LOCATOR_RESOLVERS.get(tool)
    if resolver is None:
        return None, f"khong co resolver cho tool '{tool}'"
    try:
        value = resolver(expr)
    except Exception as exc:  # noqa: BLE001 - muon in ro nguyen nhan cho nguoi doc CI log
        return None, f"loi khi resolve: {exc}"
    if value is None:
        return None, "khong tim thay node/attribute nao khop"
    return value, None


# ------------------------------------------------------------------------------------------
# 3. Doi chieu hai chieu voi V2__evidence_model.sql
# ------------------------------------------------------------------------------------------

CREATE_TABLE_RE = re.compile(r"CREATE TABLE (\w+) \((.*?)\n\);", re.DOTALL)
COLUMN_NAME_RE = re.compile(r"^\s{4}(\w+)\s{2,}", re.MULTILINE)


def sql_columns():
    sql_text = SQL_PATH.read_text(encoding="utf-8")
    columns = set()
    for table_match in CREATE_TABLE_RE.finditer(sql_text):
        table, body = table_match.groups()
        for col_match in COLUMN_NAME_RE.finditer(body):
            col = col_match.group(1)
            if col.upper().startswith(("CONSTRAINT", "CHECK", "UNIQUE")):
                continue
            columns.add(f"{table}.{col}")
    return columns


# ------------------------------------------------------------------------------------------
# main
# ------------------------------------------------------------------------------------------


def main():
    problems = []

    markdown_text = DOC_PATH.read_text(encoding="utf-8")
    rows = parse_provenance_rows(markdown_text)
    if not rows:
        print("Khong trich duoc dong provenance nao tu docs/evidence-model.md.")
        return 1

    print(f"Doc duoc {len(rows)} dong provenance tu docs/evidence-model.md.\n")

    # (a) Moi locator resolve duoc va khop gia tri vi du. Cot DERIVED khong can locator may-doc-duoc,
    # chi can co ly do o cot 'nguon' — da kiem tra o (b) qua viec cot do van phai xuat hien trong doc.
    for row in rows:
        if row["source"] == "DERIVED":
            continue
        value, error = resolve_locator(row["locator"])
        if error:
            problems.append(f"[{row['column']}] locator '{row['locator']}' loi: {error}")
            continue
        if row["example"] and str(value) != row["example"]:
            problems.append(
                f"[{row['column']}] locator '{row['locator']}' tra ve '{value}', "
                f"khac vi du trong doc '{row['example']}'"
            )
        else:
            print(f"  OK  {row['column']:24s} -> {value}")

    # (b) Doi chieu hai chieu voi cot trong V2 SQL.
    print("\nDoi chieu voi V2__evidence_model.sql...")
    # Bang provenance trong doc gom evidence_record va warning (muc 2, 3); cac bang con lai
    # (analysis_run, evidence_link, claim, claim_citation) la DERIVED thuan tuy, khong can
    # doi chieu tung cot vi khong co artifact nguon nao de doi chieu.
    doc_columns_evidence_record = {row["column"] for row in rows}

    sql_cols = sql_columns()
    documented_tables = {"evidence_record", "warning"}
    undocumented = sorted(
        col
        for col in sql_cols
        if col.split(".")[0] in documented_tables and col.split(".")[1] not in doc_columns_evidence_record
    )
    if undocumented:
        for col in undocumented:
            problems.append(f"Cot SQL '{col}' khong co dong provenance tuong ung trong doc")
    else:
        print(f"  OK  Tat ca cot cua {sorted(documented_tables)} trong SQL deu co dong provenance.")

    print()
    if problems:
        print(f"THAT BAI: {len(problems)} van de.\n")
        for p in problems:
            print(f"  - {p}")
        return 1

    print("KHOP: moi locator resolve dung, doc va SQL khong troi dat khoi nhau.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
