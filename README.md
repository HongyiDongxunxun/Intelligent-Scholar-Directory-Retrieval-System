# Interactive Intelligent Scholar Directory Retrieval System

This repository is the research artifact for the paper *From Conceptual Vision to Prototype: Designing an Interactive Intelligent Scholar Directory Retrieval System for Chinese Humanities and Social Sciences*.

It provides a confidentiality-preserving reconstruction of the system, a runnable synthetic fixture, de-identified experiment-derived data, reproducibility scripts, API examples, and archived experiment evidence. It does **not** contain the original production source code, raw bibliographic database, real scholar profiles, credentials, or anonymization crosswalks.

## Artifact scope

The reconstructed prototype supports:

- structured publication retrieval and scholar discovery;
- cross-entity topic assembly for publications, scholars, institutions, and keywords;
- auditable `Rel`, `Str`, and `Inf` scoring components;
- evidence navigation from ranked entities to supporting publications;
- deterministic natural-language parse-and-confirm workflows;
- repeatable functional, ranking, cache, and lightweight performance experiments.

## Repository structure

```text
.
|-- backend/                       Spring Boot API and integration tests
|-- frontend/                      Vue 3 interactive interface
|-- data/
|   |-- synthetic-fixture-manifest.json
|   |-- reviewer_data_public/      De-identified reviewer dataset
|   `-- reviewer_data_public.zip   Submission-ready data archive
|-- experiments/                   E1-E3 experiment runner and published run
|-- scripts/                       Build, start, and experiment helpers
|-- docs/                          API examples and screenshot verification
|-- private-materials/             Authenticated encrypted private-source bundle
|-- CITATION.cff                   Citation metadata
|-- SECURITY.md                    Exposure model and reporting policy
|-- DATA_USE_NOTICE.md             Terms for experiment-derived data
`-- LICENSE                        MIT license for reconstructed code
```

## Data layers

This repository deliberately separates two data layers.

### Synthetic system fixture

The running application generates a deterministic fixture containing 60 fictional publications, 40 fictional scholars, and 20 fictional institutions. It is used only to exercise application behavior, edge cases, ranking logic, caching, and interfaces. The backend reports `dataMode = SYNTHETIC`.

### De-identified experiment-derived data

[`data/reviewer_data_public/`](data/reviewer_data_public/) contains the minimum real experiment-derived evidence needed for reviewer verification:

| Evidence | Size | Verification purpose |
|---|---:|---|
| E1 normalized entity features | 4,992 rows | Recalculate score formulas across two anonymous topics and four entity types |
| E1 expected rankings | 24,960 rows | Verify five ranking methods |
| E1 annotation-pool structure | 168 rows | Verify Top-10 pooling without claiming completed relevance labels |
| E2 sanitized repeated outcomes | 12 records | Compare direct-confirm and parse-confirm behavior |
| E3 historical aggregate | 8 scenarios | Audit the aggregate values used in the paper |
| E3 synthetic calculation fixture | 26 measurements | Test Mean, P50, observed P95, RPS, and cache calculations |

The public data remove titles, names, institution labels, keyword text, source identifiers, request entities, hosts, credentials, prompts, sessions, and raw logs. Public entity IDs are random within this release, and no mapping back to the confidential database is included.

The directory also contains a fully synthetic relational fixture with 60 publications, 40 scholars, 20 institutions, 30 keywords, and 380 relationship rows. Historical and synthetic evidence are explicitly marked and must not be conflated.

## Requirements

- Java 17 or later
- Maven 3.8 or later
- Node.js 18 or later
- npm
- Python 3.10 or later for experiments and data validation

## Build and test

```powershell
Set-Location -LiteralPath ".\backend"
mvn.cmd test
mvn.cmd package

Set-Location -LiteralPath "..\frontend"
npm.cmd install
npm.cmd run build
```

The backend integration suite checks synthetic index consistency, publication and scholar evidence, topic-score formulas and caching, and the parse-confirm workflow.

## Run the prototype

Open two PowerShell windows at the repository root.

```powershell
powershell -ExecutionPolicy Bypass -File ".\scripts\start_backend.ps1"
```

```powershell
powershell -ExecutionPolicy Bypass -File ".\scripts\start_frontend.ps1"
```

The frontend is available at `http://127.0.0.1:5174`; the backend health endpoint is `http://127.0.0.1:8091/actuator/health`.

The backend binds only to loopback by default. A non-loopback address requires `API_ACCESS_TOKEN`, and any shared deployment must add TLS and proxy-level rate limits. See [`SECURITY.md`](SECURITY.md).

## Reproduce the reconstructed experiments

Start the backend, then run:

```powershell
powershell -ExecutionPolicy Bypass -File ".\scripts\run_experiments.ps1" -Workers 30 -Requests 60
```

Each run creates `experiments/results/<run-id>/` with a manifest, environment summary, requests, responses, measurements, aggregate results, report, and SHA-256 checksums. The tracked reference run is under [`experiments/results/published/`](experiments/results/published/).

## Validate the reviewer data

From the repository root:

```powershell
python data/reviewer_data_public/scripts/validate_package.py data/reviewer_data_public
python data/reviewer_data_public/scripts/calculate_e3.py data/reviewer_data_public/e3/calculation_fixture.jsonl
python -B -m unittest discover -s data/reviewer_data_public/tests -p "test_*.py"
```

Expected package checks include:

- 4,992 E1 feature rows;
- 24,960 ranking rows;
- 168 pooled entities;
- 12 sanitized E2 records;
- 26 synthetic E3 calculation rows;
- zero direct-identifier findings and zero mapping files.

Historical ranks retain the original deterministic tie order. Because public features are displayed to four decimal places, the package records 332 score-rounding adjustments and 53 rank-order comparisons that differ only within `0.0001`. These are documented rather than silently removed.

## Reproducibility boundary

| Claim | Supported by this repository | Outside the artifact boundary |
|---|---|---|
| Score formulas and ranking implementations | Yes | Original retrieval recall |
| Topic assembly and evidence navigation | Yes, with synthetic entities | Identity of confidential entities |
| E2 repeat outcomes | Yes, as sanitized archived records | Population-level model accuracy |
| Historical E3 aggregate interpretation | Yes | Lost historical per-request samples |
| Reconstructed-system performance | Yes, locally reproducible | Production SLA or long-term stability |

The artifact supports verification of engineering feasibility and reported calculations. It does not establish production readiness, exhaustive retrieval quality, or general model accuracy.

## Private build materials

The original private database structure, runtime configurations, and Maven descriptor are stored in `private-materials/original-build-materials.isdenc`, encrypted with AES-256-GCM and a password that is not committed. They are not required by the public reconstruction.

The tracked `backend/pom.xml` and `backend/src/main/resources/application.yml` are deliberately retained as sanitized, credential-free reproducibility templates. Encrypting or removing these public templates would make independent compilation impossible without improving confidentiality.

Use `scripts/protect_private_materials.ps1` to create or restore a private bundle. Plaintext schemas, local/production configurations, private POM files, restored files, passwords, and key files are excluded by `.gitignore`.

## Citation

Use the metadata in [`CITATION.cff`](CITATION.cff). Add the final publication venue, volume, pages, and DOI after acceptance.

## License and data use

The reconstructed source code is released under the [MIT License](LICENSE). De-identified experiment-derived data are governed separately by [`DATA_USE_NOTICE.md`](DATA_USE_NOTICE.md), including attribution, non-commercial academic use, and a prohibition on re-identification. No rights to the confidential source database, original system, or unpublished identity mappings are granted.

Questions about reproducibility can be submitted through the repository's GitHub Issues page.
