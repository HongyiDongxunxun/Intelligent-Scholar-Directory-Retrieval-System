#!/usr/bin/env python3
"""Validate the reviewer package using only its public contents."""

from __future__ import annotations

import argparse
import csv
import hashlib
import json
import re
import sys
from collections import Counter, defaultdict
from decimal import Decimal
from pathlib import Path


METHODS = ("Rel-only", "Inf-only", "Rel+Inf", "Rel+Str", "Full")
EXPECTED = {
    ("T001", "paper"): 500, ("T001", "author"): 787,
    ("T001", "unit"): 212, ("T001", "keyword"): 1175,
    ("T002", "paper"): 427, ("T002", "author"): 774,
    ("T002", "unit"): 183, ("T002", "keyword"): 934,
}
FORBIDDEN_COLUMNS = {
    "title", "abstract", "name", "display_name", "journal", "keyword", "unit_name",
    "author_name", "sno", "source_id", "article_id", "paper_id", "original_id",
    "host", "url", "session_id", "user_id", "request_text", "response_text", "prompt",
}
SENSITIVE_PATTERNS = (
    re.compile("jdbc" + ":|mysql" + "://", re.IGNORECASE),
    re.compile(r"[A-Za-z]:\\\\"),
    re.compile(r"\b(?:password|passwd|api[_-]?key|authorization)\s*[:=]", re.IGNORECASE),
)


def read_csv(path: Path) -> list[dict[str, str]]:
    with path.open("r", encoding="utf-8-sig", newline="") as handle:
        return list(csv.DictReader(handle))


def score(row: dict[str, str], method: str) -> Decimal:
    rel, strength, influence = Decimal(row["rel"]), Decimal(row["str"]), Decimal(row["inf"])
    if method == "Rel-only":
        return rel
    if method == "Inf-only":
        return influence
    if method == "Rel+Inf":
        return Decimal("0.8") * rel + Decimal("0.2") * influence
    if method == "Rel+Str":
        return Decimal("0.75") * rel + Decimal("0.25") * strength
    return Decimal(row["full_score"])


def validate_checksums(root: Path) -> int:
    lines = (root / "checksums.sha256").read_text(encoding="ascii").splitlines()
    checked = 0
    for line in lines:
        expected_hash, relative = line.split("  ", 1)
        path = root / Path(relative)
        actual_hash = hashlib.sha256(path.read_bytes()).hexdigest()
        assert actual_hash == expected_hash, f"Checksum mismatch: {relative}"
        checked += 1
    actual_files = {path.relative_to(root).as_posix() for path in root.rglob("*") if path.is_file() and path.name != "checksums.sha256"}
    listed_files = {line.split("  ", 1)[1] for line in lines}
    assert actual_files == listed_files, "Checksum manifest does not cover exactly all files"
    return checked


def validate_e1(root: Path) -> dict:
    features = read_csv(root / "e1" / "entity_features.csv")
    assert len(features) == 4992
    assert set(features[0]) == {"topic_id", "entity_type", "entity_pid", "rel", "str", "inf", "full_score"}
    observed = Counter((row["topic_id"], row["entity_type"]) for row in features)
    assert dict(observed) == EXPECTED
    pid_pattern = re.compile(r"^[PAUK]-[0-9A-F]{16}$")
    keys = set()
    for row in features:
        assert pid_pattern.fullmatch(row["entity_pid"])
        assert (row["topic_id"], row["entity_type"], row["entity_pid"]) not in keys
        keys.add((row["topic_id"], row["entity_type"], row["entity_pid"]))
        full = Decimal("0.70") * Decimal(row["rel"]) + Decimal("0.20") * Decimal(row["str"]) + Decimal("0.10") * Decimal(row["inf"])
        assert abs(full - Decimal(row["full_score"])) <= Decimal("0.00005")

    rankings = read_csv(root / "e1" / "ranking_expected.csv")
    assert len(rankings) == 24960
    feature_lookup = {(row["topic_id"], row["entity_type"], row["entity_pid"]): row for row in features}
    ranking_groups = defaultdict(list)
    ranking_keys = set()
    for row in rankings:
        key = (row["topic_id"], row["entity_type"], row["method"], int(row["rank"]))
        assert key not in ranking_keys
        ranking_keys.add(key)
        feature = feature_lookup[(row["topic_id"], row["entity_type"], row["entity_pid"])]
        assert abs(Decimal(row["method_score"]) - score(feature, row["method"])) <= Decimal("0.0000005")
        ranking_groups[(row["topic_id"], row["entity_type"], row["method"])].append(row)
    assert len(ranking_groups) == 40

    rounding_order_adjustments = 0
    for (topic_id, entity_type, method), rows in ranking_groups.items():
        rows.sort(key=lambda row: int(row["rank"]))
        expected_size = EXPECTED[(topic_id, entity_type)]
        assert [int(row["rank"]) for row in rows] == list(range(1, expected_size + 1))
        assert len({row["entity_pid"] for row in rows}) == expected_size
        previous = None
        for row in rows:
            current = Decimal(row["method_score"])
            if previous is not None and current > previous:
                assert current - previous <= Decimal("0.0001005")
                rounding_order_adjustments += 1
            previous = current

    pool = read_csv(root / "e1" / "annotation_pool_structure.csv")
    assert len(pool) == 168
    expected_pool = {}
    for rows in ranking_groups.values():
        for row in rows[:10]:
            key = (row["topic_id"], row["entity_type"], row["entity_pid"])
            rank = int(row["rank"])
            entry = expected_pool.setdefault(key, {"methods": [], "best_rank": rank})
            entry["methods"].append(f'{row["method"]}@{rank}')
            entry["best_rank"] = min(entry["best_rank"], rank)
    actual_pool = {
        (row["topic_id"], row["entity_type"], row["entity_pid"]): row
        for row in pool
    }
    assert set(actual_pool) == set(expected_pool)
    for key, expected_entry in expected_pool.items():
        row = actual_pool[key]
        assert row["pooled_methods"].split(";") == expected_entry["methods"]
        assert int(row["best_rank"]) == expected_entry["best_rank"]
        assert row["label"] == "" and row["notes"] == ""
    return {"features": len(features), "rankings": len(rankings), "pool": len(pool), "rounding_order_adjustments": rounding_order_adjustments}


def validate_e2_e3(root: Path) -> dict:
    e2 = json.loads((root / "e2" / "samples_sanitized.json").read_text(encoding="utf-8"))
    records = e2["records"]
    assert len(records) == 12
    stages = Counter(row["stage"] for row in records)
    assert stages == {"direct_confirm": 6, "parse_confirm": 6}
    assert {(row["case_id"], row["stage"], row["repeat_no"]) for row in records} == {
        (case, stage, repeat) for case in ("CASE-A", "CASE-B") for stage in ("direct_confirm", "parse_confirm") for repeat in range(1, 4)
    }
    assert all(row["http_status"] == 200 and row["business_status"] == "2000" for row in records)

    historical = json.loads((root / "e3" / "historical_aggregate.json").read_text(encoding="utf-8"))
    assert historical["snapshot_type"] == "historical_aggregate"
    assert historical["raw_per_request_samples_available"] is False
    assert "environment" not in historical

    fixture = [json.loads(line) for line in (root / "e3" / "calculation_fixture.jsonl").read_text(encoding="utf-8").splitlines() if line]
    assert len(fixture) == 26
    assert all(row["synthetic"] is True for row in fixture)
    return {"e2_records": len(records), "e3_calculation_rows": len(fixture)}


def validate_synthetic(root: Path) -> dict:
    base = root / "synthetic_system_fixture"
    publications = read_csv(base / "publications.csv")
    scholars = read_csv(base / "scholars.csv")
    institutions = read_csv(base / "institutions.csv")
    keywords = read_csv(base / "keywords.csv")
    journals = read_csv(base / "journals.csv")
    disciplines = read_csv(base / "disciplines.csv")
    pub_scholar = read_csv(base / "publication_scholar.csv")
    pub_keyword = read_csv(base / "publication_keyword.csv")
    citations = read_csv(base / "citations.csv")
    assert (len(publications), len(scholars), len(institutions), len(keywords), len(journals), len(disciplines)) == (60, 40, 20, 30, 4, 4)
    assert (len(pub_scholar), len(pub_keyword), len(citations)) == (120, 180, 80)

    pub_ids = {row["publication_id"] for row in publications}
    scholar_ids = {row["scholar_id"] for row in scholars}
    institution_ids = {row["institution_id"] for row in institutions}
    keyword_ids = {row["keyword_id"] for row in keywords}
    journal_ids = {row["journal_id"] for row in journals}
    discipline_ids = {row["discipline_id"] for row in disciplines}
    assert all(not row["institution_id"] or row["institution_id"] in institution_ids for row in scholars)
    assert all(not row["parent_id"] or row["parent_id"] in institution_ids for row in institutions)
    assert all(row["journal_id"] in journal_ids and row["discipline_id"] in discipline_ids for row in publications)
    assert all(row["publication_id"] in pub_ids and row["scholar_id"] in scholar_ids for row in pub_scholar)
    assert all(row["publication_id"] in pub_ids and row["keyword_id"] in keyword_ids for row in pub_keyword)
    assert all(row["citing_publication_id"] in pub_ids and row["cited_publication_id"] in pub_ids for row in citations)

    author_scholars = {row["scholar_id"] for row in pub_scholar}
    assert len(scholar_ids - author_scholars) >= 1
    assert sum(1 for row in scholars if not row["institution_id"]) >= 1
    assert any(count > 1 for count in Counter(row["display_name"] for row in scholars).values())
    assert any("|" in row["topic_codes"] for row in publications)
    assert any(int(row["citation_count"]) == 0 for row in publications)
    assert any(row["alias_form"] for row in keywords)
    return {"publications": 60, "scholars": 40, "institutions": 20, "relationship_rows": 380}


def validate_privacy(root: Path) -> dict:
    findings = []
    mapping_files = []
    for path in root.rglob("*"):
        if not path.is_file():
            continue
        relative = path.relative_to(root).as_posix()
        if "mapping" in path.name.lower() or "crosswalk" in path.name.lower():
            mapping_files.append(relative)
        if path.suffix.lower() == ".csv" and not relative.startswith("synthetic_system_fixture/"):
            rows = read_csv(path)
            if rows:
                bad_columns = set(rows[0]).intersection(FORBIDDEN_COLUMNS)
                if bad_columns:
                    findings.append({"file": relative, "columns": sorted(bad_columns)})
        try:
            content = path.read_text(encoding="utf-8")
        except UnicodeDecodeError:
            continue
        for pattern in SENSITIVE_PATTERNS:
            if pattern.search(content):
                findings.append({"file": relative, "pattern": pattern.pattern})
    assert not mapping_files, f"Mapping-like files present: {mapping_files}"
    assert not findings, f"Privacy scan findings: {findings}"
    return {"findings": 0, "mapping_files": 0}


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("root", nargs="?", default=".", type=Path)
    args = parser.parse_args()
    root = args.root.resolve()
    try:
        result = {
            "status": "pass",
            "checksums": validate_checksums(root),
            "e1": validate_e1(root),
            "e2_e3": validate_e2_e3(root),
            "synthetic": validate_synthetic(root),
            "privacy": validate_privacy(root),
        }
    except (AssertionError, KeyError, ValueError, FileNotFoundError) as error:
        print(json.dumps({"status": "fail", "error": str(error)}, ensure_ascii=False, indent=2))
        return 1
    print(json.dumps(result, ensure_ascii=False, indent=2))
    return 0


if __name__ == "__main__":
    sys.exit(main())
