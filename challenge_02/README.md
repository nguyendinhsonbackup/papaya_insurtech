# AI Challenge 02 — Claims Data Cleanup & Report

Java 21, zero dependencies. The project generates a messy 500-row insurance claims CSV, cleans it, and writes a data quality report.

| Deliverable | Path |
|---|---|
| Dirty input (seed 42) | [`data/claims_dirty.csv`](data/claims_dirty.csv) |
| Clean output | [`output/claims_clean.csv`](output/claims_clean.csv) |
| Data quality report | [`output/data_quality_report.md`](output/data_quality_report.md) |
| Test report (test cases + evidence screenshots) | [`test-report/claims_test_report.xlsx`](test-report/claims_test_report.xlsx), [`test-report/evidence/`](test-report/evidence) |

## Quick start

Requires JDK 21+ (`java` and `javac` on `PATH`).

```bash
./run.sh            # generate + clean + report   (Windows: .\run.ps1)
./test.sh           # 22 tests + Excel report     (Windows: .\test.ps1)
```

After the tests run, `test-report/claims_test_report.xlsx` is written with 3 sheets:
- **Summary**: run info, pass/fail totals, per-class breakdown and a console screenshot.
- **Testcase**: one row per test case with ID, steps, expected vs actual, status and a link to its evidence.
- **Evidence**: one terminal-style screenshot per test showing the real inputs and outputs. Each image is also saved as `test-report/evidence/<ID>.png`.

The workbook is written with the JDK only (Office Open XML + Java2D).

Sub-commands: `generate`, `clean`, `all` (default). Options: `--rows 500`, `--seed 42`, `--dirty data/claims_dirty.csv`, `--out-dir output`.

## Result (seed 42)

500 rows in → 12 exact + 8 near duplicates removed → **480 rows out**: 427 valid, 53 flagged `is_valid=false`.

## How it works

1. **Generate** (`ClaimsGenerator`): 480 clean rows. 1–3 issues are injected into 15% of them, then 8 near duplicates and 12 exact duplicates are added. That gives 18.4% dirty rows.
2. **Clean** (`ClaimsCleaner`):
   - Map columns by header name.
   - Drop exact duplicates.
   - Normalize every column (`Normalizers`, `Vocabulary`) and record each `Issue`.
   - Drop rows that are identical after normalization.
   - Flag `claim_id`s shared by different rows.
3. **Report** (`ClaimsReport`):
   - Row counts.
   - Rows per issue type.
   - Issues-per-row distribution (rows with several issues at once).
   - Claims by type and by status.
   - Average amount by type and currency.
   - Top 5 diagnoses.

Issues are either **Fixed** (formatting, typos, aliases; the row stays valid) or **Flagged** (negative/zero amount, missing IDs, conflicting `claim_id`, unrecognized values). Flagged rows are kept with `is_valid=false`, and `dq_issues` lists every issue on the row. Nothing is dropped silently.

The end-to-end test checks the cleaner against the generator's injected-issue log:
- Every injected issue is detected.
- Untouched rows report no issues.

See **[GUIDELINE.md](GUIDELINE.md)** for the full guide (Vietnamese). It covers how to run and test, the execution flow, design decisions and the timeline.
