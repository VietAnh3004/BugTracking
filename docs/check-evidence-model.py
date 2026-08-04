#!/usr/bin/env python
"""Doi chieu docs/evidence-model.md <-> artifact that <-> migration SQL.

Tuong tu case-study/summarize.py --check: khong doc tay ma chay that locator len artifact,
va doi chieu hai chieu voi migration SQL de doc va schema khong troi dat khoi nhau.

    python docs/check-evidence-model.py

Ba dieu can biet truoc khi sua file nay:

1. Bang provenance duoc gom THEO TUNG BANG, khong phai mot tap phang moi ten cot. Truoc day
   script gom tat ca ten cot vao mot `set` chung: khi chi co 2 bang duoc ghi tai lieu thi con
   chap nhan duoc, nhung tu V5 tro di co 7 bang va `file_path` khai o `evidence_record` se TU
   DONG thoa man `warning_location.file_path` lan `stack_frame.file_path`. Kiem tra hai chieu
   mat sach y nghia ma van in "OK".

2. Doc TAT CA file migration (glob `V*__*.sql`), khong hard-code mot file. Liet ke tay thi
   them migration moi ma quen sua o day se lam script bo qua ca bang moi trong im lang — dung
   loai hong ma chinh script nay sinh ra de chan.

3. Co guard chong xanh gia: `COLUMN_NAME_RE` doi thut dung 4 dau cach + >=2 dau cach sau ten
   cot. Neu mot file migration can le khac mot chut, regex doc duoc 0 cot va script in "OK"
   trong khi khong kiem gi ca. Vi vay moi bang trong `DOCUMENTED_TABLES` phai cho ra it nhat
   MIN_COLUMNS_PER_TABLE cot, khong thi bao loi.
"""

import json
import re
import sys
import xml.etree.ElementTree as ET
from pathlib import Path

# Console mac dinh cua Windows la cp1252: in mot ky tu nhu '→' lay tu tai lieu se nem
# UnicodeEncodeError va lam script chet NGAY GIUA phan in danh sach van de — nguoi doc thay
# traceback thay vi thay van de that. Ep UTF-8 ngay tu dau.
if hasattr(sys.stdout, "reconfigure"):
    sys.stdout.reconfigure(encoding="utf-8", errors="replace")

REPO_ROOT = Path(__file__).resolve().parent.parent
DOC_PATH = REPO_ROOT / "docs" / "evidence-model.md"
MIGRATION_DIR = REPO_ROOT / "core-backend" / "src" / "main" / "resources" / "db" / "migration"
SAMPLES_DIR = REPO_ROOT / "docs" / "samples" / "vulnshop"

# Bang co bang provenance trong doc. Cac bang con lai (analysis_run, evidence_link, claim,
# claim_citation, app_user, refresh_token, snapshot_file...) la DERIVED thuan tuy hoac thuoc
# tang ung dung, khong co artifact nguon nao de doi chieu tung cot.
DOCUMENTED_TABLES = {
    "evidence_record",
    "warning",
    "warning_location",
    "test_case",
    "stack_frame",
    "coverage_line",
    "coverage_method",
}

# Guard chong xanh gia (xem docstring, diem 3).
MIN_COLUMNS_PER_TABLE = 3

# ------------------------------------------------------------------------------------------
# 1. Trich bang provenance tu markdown, GOM THEO BANG
# ------------------------------------------------------------------------------------------

# "## 2. Bang provenance — `evidence_record` (chung cho moi loai)" -> evidence_record
SECTION_RE = re.compile(r"^#{2,3}\s+\d+\.\s+B[^\n]*?provenance[^\n]*?`(\w+)`")
ROW_RE = re.compile(r"^\|\s*([\w_]+)\s*\|\s*([^|]+?)\s*\|\s*([^|]+?)\s*\|\s*([^|]*?)\s*\|\s*$")
ANY_HEADING_RE = re.compile(r"^#{1,6}\s")


def parse_provenance_rows(markdown_text):
    """Tra ve list dict co them khoa 'table'.

    Chi nhan dong nam BEN TRONG mot muc "Bang provenance — `<table>`". Ngoai vung do, mot bang
    markdown 4 cot bat ky (vi du bang mapping severity neu sau nay them cot) se bi hieu nham la
    dong provenance.
    """
    rows = []
    current_table = None
    for line in markdown_text.splitlines():
        section = SECTION_RE.match(line)
        if section:
            current_table = section.group(1)
            continue
        # Bat ky tieu de nao khac ket thuc vung provenance dang mo.
        if ANY_HEADING_RE.match(line):
            current_table = None
            continue
        if current_table is None:
            continue
        m = ROW_RE.match(line.strip())
        if not m:
            continue
        column, source, locator, example = m.groups()
        if column in ("Cột", "---") or source.strip("- ") == "":
            continue
        rows.append(
            {
                "table": current_table,
                "column": column,
                "source": source.strip(),
                "locator": locator.strip().strip("`"),
                "example": example.strip().strip("`"),
            }
        )
    return rows


# ------------------------------------------------------------------------------------------
# 2. Locator resolver: "<tool>:<xpath hoac json path don gian>"
# ------------------------------------------------------------------------------------------


def _find_and_read(tree, xpath):
    # ElementTree.find() khong ho tro ket hop predicate ([@a='b']) VOI mot buoc "/@attr" cuoi
    # cung trong CUNG mot bieu thuc (loi that: KeyError('@')) — phai tach '/@attr' ra truoc,
    # find() phan con lai (chi con predicate loc phan tu), roi doc attribute bang .get().
    #
    # Hai dang locator, phan biet bang viec CO hay KHONG co '/@':
    #   - '<duong dan>/@attr' -> doc thuoc tinh ('/@attr' o dau chuoi nghia la thuoc tinh cua root)
    #   - '<duong dan>'       -> doc TEXT cua phan tu (vd toan van the <failure>)
    if "/@" in xpath:
        element_path, _, attr = xpath.rpartition("/@")
        node = tree.getroot().find(element_path) if element_path else tree.getroot()
        return None if node is None else node.get(attr)

    node = tree.getroot().find(xpath)
    return None if node is None else node.text


JSON_STEP_RE = re.compile(r"([\w-]+)|\[(\d+)\]")


def _read_json_path(data, path):
    """JSON path toi gian: 'runs[0].results[0].locations[0].physicalLocation.region.startLine'.

    Chi ho tro truy cap khoa va chi so mang — du cho SARIF, va co doc duoc bang mat thuong.
    Nem KeyError/IndexError/TypeError de resolve_locator() bao ro nguyen nhan.
    """
    node = data
    for key, index in JSON_STEP_RE.findall(path):
        node = node[key] if key else node[int(index)]
    return node


def resolve_spotbugs(xpath):
    return _find_and_read(ET.parse(SAMPLES_DIR / "spotbugs.xml"), xpath)


def resolve_jacoco(xpath):
    return _find_and_read(ET.parse(SAMPLES_DIR / "jacoco.xml"), xpath)


def resolve_semgrep(path):
    # Truoc day ham nay la STUB: no bo qua tham so va luon tra results[0].ruleId, nen MOI dong
    # provenance semgrep deu "khop" mot cach vo nghia. Gio chay that JSON path.
    with open(SAMPLES_DIR / "semgrep.sarif", encoding="utf-8") as f:
        return _read_json_path(json.load(f), path)


def resolve_surefire(locator):
    # Dinh dang: "<ten file>#<xpath>" — bat buoc chi ro file nao trong 9 file surefire-reports,
    # khong duoc doan.
    file_name, sep, xpath = locator.partition("#")
    if not sep:
        raise ValueError("thieu '#': phai la '<ten file>.xml#<xpath>'")
    return _find_and_read(ET.parse(SAMPLES_DIR / "surefire-reports" / file_name), xpath)


LOCATOR_RESOLVERS = {
    "spotbugs": resolve_spotbugs,
    "jacoco": resolve_jacoco,
    "semgrep": resolve_semgrep,
    "surefire": resolve_surefire,
}


def resolve_locator(locator):
    if ":" not in locator:
        return None, "khong dung dinh dang '<tool>:<locator>'"
    tool, expr = locator.split(":", 1)
    resolver = LOCATOR_RESOLVERS.get(tool)
    if resolver is None:
        return None, f"khong co resolver cho tool '{tool}'"
    try:
        value = resolver(expr)
    except Exception as exc:  # noqa: BLE001 - muon in ro nguyen nhan cho nguoi doc CI log
        return None, f"loi khi resolve: {type(exc).__name__}: {exc}"
    if value is None:
        return None, "khong tim thay node/attribute nao khop"
    return value, None


# ------------------------------------------------------------------------------------------
# 3. Doi chieu hai chieu voi migration SQL
# ------------------------------------------------------------------------------------------

CREATE_TABLE_RE = re.compile(r"CREATE TABLE (\w+) \((.*?)\n\);", re.DOTALL)
# Dung ' ' tuong minh chu KHONG dung '\s': '\s' nuot ca ky tu xuong dong, nen o mot dong trong
# no an sang dau dong ke tiep va sinh khop gia. Va chi doi >=1 dau cach sau ten cot chu khong
# phai >=2: 'analysis_run_id UUID  NOT NULL ...' (V2 dong 68) chi co DUNG MOT dau cach vi ten
# cot da dai bang cot can le, nen regex '\s{2,}' cu KHONG BAO GIO trich duoc no. Kiem tra mot
# chieu truoc day (chi bao khi cot SQL thieu dong provenance) khong the lo ra dieu do.
COLUMN_NAME_RE = re.compile(r"^ {4}(\w+) +", re.MULTILINE)
# "ALTER TABLE warning\n    ADD COLUMN severity_rank INT GENERATED ..." — mot ALTER co the co
# nhieu menh de ADD COLUMN, moi menh de mot dong.
ALTER_TABLE_RE = re.compile(r"ALTER TABLE (\w+)(.*?);", re.DOTALL)
ADD_COLUMN_RE = re.compile(r"ADD COLUMN (?:IF NOT EXISTS )?(\w+)")

NON_COLUMN_KEYWORDS = ("CONSTRAINT", "CHECK", "UNIQUE", "PRIMARY", "FOREIGN", "EXCLUDE")


def sql_columns():
    """Tra ve {ten_bang: {ten_cot}} gom tu MOI file migration."""
    tables = {}
    migration_files = sorted(MIGRATION_DIR.glob("V*__*.sql"))
    if not migration_files:
        raise SystemExit(f"Khong tim thay file migration nao trong {MIGRATION_DIR}")

    for sql_file in migration_files:
        sql_text = sql_file.read_text(encoding="utf-8")
        for table, body in CREATE_TABLE_RE.findall(sql_text):
            for col in COLUMN_NAME_RE.findall(body):
                if col.upper().startswith(NON_COLUMN_KEYWORDS):
                    continue
                tables.setdefault(table, set()).add(col)
        for table, body in ALTER_TABLE_RE.findall(sql_text):
            for col in ADD_COLUMN_RE.findall(body):
                tables.setdefault(table, set()).add(col)
    return tables, migration_files


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

    tables_in_doc = sorted({row["table"] for row in rows})
    print(f"Doc duoc {len(rows)} dong provenance cho {len(tables_in_doc)} bang: {', '.join(tables_in_doc)}\n")

    # (a) Moi locator resolve duoc va khop gia tri vi du. Cot DERIVED khong can locator
    # may-doc-duoc, chi can co ly do o cot 'nguon' — da kiem o (b) qua viec cot do van phai
    # xuat hien trong doc.
    for row in rows:
        if row["source"] == "DERIVED":
            continue
        value, error = resolve_locator(row["locator"])
        label = f"{row['table']}.{row['column']}"
        if error:
            problems.append(f"[{label}] locator '{row['locator']}' loi: {error}")
            continue
        if row["example"] and str(value) != row["example"]:
            problems.append(
                f"[{label}] locator '{row['locator']}' tra ve '{value}', "
                f"khac vi du trong doc '{row['example']}'"
            )
        else:
            # Cat ngan khi in: stack_trace_raw tra ve toan van stack trace vai KB, do khong phai
            # thu nen do day log CI. Viec so sanh o tren van dung gia tri day du.
            printable = " ".join(str(value).split())
            if len(printable) > 70:
                printable = printable[:70] + "…"
            print(f"  OK  {label:44s} -> {printable}")

    # (b) Doi chieu hai chieu voi cot trong SQL, THEO TUNG BANG.
    sql_tables, migration_files = sql_columns()
    print(f"\nDoi chieu voi {len(migration_files)} file migration ({', '.join(f.name for f in migration_files)})...")

    doc_by_table = {}
    for row in rows:
        doc_by_table.setdefault(row["table"], set()).add(row["column"])

    for table in sorted(DOCUMENTED_TABLES):
        sql_cols = sql_tables.get(table, set())
        doc_cols = doc_by_table.get(table, set())

        # Guard chong xanh gia: doc duoc qua it cot nghia la regex khong an khop voi can le
        # cua file SQL, khong phai bang do that su it cot.
        if len(sql_cols) < MIN_COLUMNS_PER_TABLE:
            problems.append(
                f"Chi trich duoc {len(sql_cols)} cot SQL cho bang '{table}' "
                f"(toi thieu {MIN_COLUMNS_PER_TABLE}) — nhieu kha nang can le trong file "
                f"migration khong khop COLUMN_NAME_RE, khong phai bang that su it cot"
            )
            continue
        if not doc_cols:
            problems.append(f"Bang '{table}' khong co muc 'Bang provenance' nao trong doc")
            continue

        for col in sorted(sql_cols - doc_cols):
            problems.append(f"Cot SQL '{table}.{col}' khong co dong provenance tuong ung trong doc")
        for col in sorted(doc_cols - sql_cols):
            problems.append(f"Dong provenance '{table}.{col}' khong co cot SQL tuong ung")
        if sql_cols == doc_cols:
            print(f"  OK  {table:24s} {len(sql_cols)} cot khop hai chieu")

    # Bang co provenance trong doc nhung quen khai o DOCUMENTED_TABLES se khong bao gio duoc
    # doi chieu — im lang y het loi ma script nay sinh ra de chan.
    for table in sorted(set(doc_by_table) - DOCUMENTED_TABLES):
        problems.append(
            f"Bang '{table}' co bang provenance trong doc nhung khong nam trong "
            f"DOCUMENTED_TABLES — se khong bao gio duoc doi chieu voi SQL"
        )

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
