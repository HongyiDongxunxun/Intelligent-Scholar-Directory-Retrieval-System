# Reviewer Verification Data Package

This package supports independent checking of the scoring, ranking, pooling, sanitized repeat outcomes, benchmark calculations, and system data contracts described in the paper.

## Privacy boundary

- `e1/` contains real experiment-derived normalized feature values, but no titles, names, institution labels, keyword strings, bibliographic identifiers, or source IDs.
- Public IDs are random for this release. No ID mapping or generation secret is included.
- `e2/` removes request text, entity values, prompts, model/provider payloads, hosts, sessions, credentials, and response entities.
- `e3/historical_aggregate.json` is an aggregate historical snapshot. The per-request file in `e3/calculation_fixture.jsonl` is fully synthetic and is never historical evidence.
- `synthetic_system_fixture/` is entirely fictional and supports runnable system/interface checks only.

## E1 methods

All feature values are normalized. Scores are:

```text
Rel-only = rel
Inf-only = inf
Rel+Inf  = 0.80*rel + 0.20*inf
Rel+Str  = 0.75*rel + 0.25*str
Full     = 0.70*rel + 0.20*str + 0.10*inf
```

`full_score` is recomputed from the four-decimal public feature values. This removes last-decimal differences caused by the historical pipeline calculating scores before feature display rounding; the adjustment count is recorded in `validation_report.json`.

Ranking is descending by method score. Historical ranks retain the experiment pipeline's deterministic source-ID tie order; source IDs are withheld, so score-equal entities may appear in a different order when a reviewer selects another valid tie rule. The annotation pool is the historical union of each method's Top-10 within every topic/entity-type group. `label` and `notes` are intentionally empty; no human annotation is claimed.

## E2 interpretation

The file contains six manually confirmed repetitions and six parse-to-confirm repetitions. For parse-to-confirm rows, `latency_ms` covers only the parse request because combined end-to-end latency was not preserved. `parser_config_version` is explicitly marked unspecified where the archive did not record it.

## E3 interpretation

Historical values are aggregates adopted by the paper. Earlier historical request-level samples are unavailable because a later incompatible warm-cache run overwrote that JSON. The synthetic calculation fixture exists only to test mean, percentile, success-rate, throughput, and cache calculations.

## Synthetic fixture edge cases

The fixture includes missing scholar institutions, two distinct same-name scholars, zero-citation publications, cross-topic publications, two-level institutions, keyword aliases, and one scholar with no publication relationship.

## Validation

Run:

```powershell
python scripts/validate_package.py .
python scripts/calculate_e3.py e3/calculation_fixture.jsonl
python -B -m unittest discover -s tests -p "test_*.py"
```

Review `manifest.json`, `validation_report.json`, and `checksums.sha256` before submission.

## Release and data-use status

This package is released for peer review, reproducibility verification, teaching, and non-commercial academic research under the included `LICENSE_NOTICE.md`. Re-identification and linkage to real entities are prohibited. Nothing in this package grants rights to the confidential source database, original identifiers, or unpublished mappings.
