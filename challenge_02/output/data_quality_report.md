# Claims Data Quality Report

- Input: `data/claims_dirty.csv`
- Clean output: `output/claims_clean.csv`
- Generated at: 2026-10-19T11:59:35

## 1. Row counts

| Metric | Rows |
|---|---|
| Rows before cleaning (excluding header) | 500 |
| Exact duplicate rows removed | 12 |
| Near duplicates removed (identical after normalization) | 8 |
| **Total duplicates removed** | 20 |
| Malformed rows (wrong column count) | 0 |
| **Rows after cleaning** | 480 |
| &nbsp;&nbsp;valid (`is_valid=true`) | 427 |
| &nbsp;&nbsp;flagged invalid (`is_valid=false`) | 53 |

## 2. Issues found (rows after de-duplication)

*Fixed* = repaired automatically, row stays valid. *Flagged* = cannot be trusted, row marked `is_valid=false`.

| Issue | Action | Rows | Description |
|---|---|---|---|
| `MISSING_CLAIM_ID` | Flagged | 8 | claim_id is empty |
| `DUPLICATE_CLAIM_ID` | Flagged | 22 | claim_id shared by rows with different data |
| `MISSING_POLICY_ID` | Flagged | 7 | policy_id is empty |
| `MISSING_MEMBER_NAME` | Fixed | 0 | member_name is empty |
| `NAME_FORMAT` | Fixed | 15 | member_name casing/whitespace normalized to Title Case |
| `MISSING_CLAIM_TYPE` | Flagged | 0 | claim_type is empty |
| `CLAIM_TYPE_FORMAT` | Fixed | 4 | claim_type casing/separators normalized |
| `CLAIM_TYPE_TYPO` | Fixed | 2 | claim_type typo or abbreviation corrected (e.g. Outpateint, OP) |
| `INVALID_CLAIM_TYPE` | Flagged | 0 | claim_type not recognized |
| `MISSING_DIAGNOSIS` | Fixed | 6 | diagnosis empty / N/A / n/a → NULL |
| `DIAGNOSIS_FORMAT` | Fixed | 0 | diagnosis casing/whitespace normalized |
| `MISSING_AMOUNT` | Flagged | 0 | submitted_amount is empty |
| `AMOUNT_FORMAT` | Fixed | 6 | submitted_amount had thousands separators / spaces (e.g. "15,000") |
| `INVALID_AMOUNT` | Flagged | 0 | submitted_amount is not a number |
| `NEGATIVE_AMOUNT` | Flagged | 13 | submitted_amount < 0 |
| `ZERO_AMOUNT` | Flagged | 8 | submitted_amount = 0 |
| `MISSING_CURRENCY` | Flagged | 0 | currency is empty |
| `CURRENCY_FORMAT` | Fixed | 3 | currency casing normalized (thb → THB) |
| `CURRENCY_ALIAS` | Fixed | 4 | currency name mapped to ISO 4217 code (Baht → THB) |
| `INVALID_CURRENCY` | Flagged | 0 | currency not recognized |
| `MISSING_DATE` | Flagged | 0 | submitted_date is empty |
| `DATE_FORMAT` | Fixed | 13 | submitted_date converted to ISO 8601 (YYYY-MM-DD) |
| `INVALID_DATE` | Flagged | 0 | submitted_date unparseable or impossible (e.g. 31/02/2024) |
| `MISSING_STATUS` | Flagged | 0 | status is empty |
| `STATUS_FORMAT` | Fixed | 4 | status casing/separators normalized (In Review → IN_REVIEW) |
| `STATUS_TYPO` | Fixed | 5 | status typo corrected (APROVED → APPROVED) |
| `INVALID_STATUS` | Flagged | 0 | status not one of APPROVED, REJECTED, PENDING, IN_REVIEW |

**Rows by number of issues** (edge case: one row can carry several issues)

| Issues per row | Rows | Share |
|---|---|---|
| 0 | 394 | 82.1% |
| 1 | 54 | 11.3% |
| 2 | 30 | 6.3% |
| 3+ | 2 | 0.4% |

## 3. Claims by type

| Claim type | Claims | Share |
|---|---|---|
| OUTPATIENT | 251 | 52.3% |
| INPATIENT | 118 | 24.6% |
| DENTAL | 72 | 15.0% |
| MATERNITY | 39 | 8.1% |
| **Total** | 480 | 100.0% |

## 4. Claims by status

| Status | Claims | Share |
|---|---|---|
| APPROVED | 201 | 41.9% |
| PENDING | 103 | 21.5% |
| IN_REVIEW | 101 | 21.0% |
| REJECTED | 75 | 15.6% |
| **Total** | 480 | 100.0% |

## 5. Average submitted amount by type

Only rows with a positive amount and a known currency. Currencies are **not** mixed (no FX conversion), so each type is reported per currency.

| Claim type | Currency | Claims | Average amount | Total amount |
|---|---|---|---|---|
| DENTAL | THB | 49 | 10,474.08 | 513,230.00 |
| DENTAL | VND | 19 | 8,295,736.84 | 157,619,000.00 |
| INPATIENT | THB | 82 | 157,836.34 | 12,942,580.00 |
| INPATIENT | VND | 31 | 109,879,677.42 | 3,406,270,000.00 |
| MATERNITY | THB | 28 | 92,348.21 | 2,585,750.00 |
| MATERNITY | VND | 10 | 67,738,300.00 | 677,383,000.00 |
| OUTPATIENT | THB | 184 | 7,300.38 | 1,343,270.00 |
| OUTPATIENT | VND | 56 | 4,825,625.00 | 270,235,000.00 |

## 6. Top 5 most common diagnoses

| Rank | Diagnosis | Claims |
|---|---|---|
| 1 | Flu | 69 |
| 2 | Common cold | 60 |
| 3 | Gastritis | 34 |
| 4 | Migraine | 34 |
| 5 | Fracture | 31 |

6 rows have no diagnosis (`NULL`) and are excluded from the ranking.

## 7. Cleaning rules applied

- Exact duplicate rows are removed (first occurrence kept); rows that become identical after normalization are removed too.
- `claim_id` / `policy_id`: trimmed + uppercased; a `claim_id` reused by rows with different data is flagged `DUPLICATE_CLAIM_ID`.
- `member_name`: whitespace collapsed, Title Case. `diagnosis`: Sentence case.
- `claim_type` / `status`: matched to a fixed vocabulary — case/separator differences, aliases (`OP`, `IP`) and typos (edit distance ≤ 2) are corrected; anything else becomes `NULL` and is flagged.
- `submitted_amount`: thousands separators removed, 2 decimals; negative / zero / non-numeric amounts are flagged (kept for audit, excluded from averages).
- `currency`: ISO 4217 uppercase (`thb`, `Baht` → `THB`; `vnd`, `dong` → `VND`).
- `submitted_date`: `YYYY-MM-DD`, `DD/MM/YYYY` (day first) and `Month D, YYYY` → ISO 8601; impossible dates are flagged.
- Empty, `N/A`, `n/a`, `NULL`, `-` → `NULL` (single null marker).
