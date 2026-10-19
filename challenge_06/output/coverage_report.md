# Coverage Report - POL-2024-000123

- Policy holder: Somchai Prasert
- Policy period: 2024-01-01 to 2024-12-31
- Policy annual limit: 100,000 THB
- Input: `data/policy.json`, `data/expenses.json`

## 1. Decision per expense

Expenses are processed in chronological order (date, then expense_id): earlier claims consume limits first. Amounts in THB.

| # | Expense | Date | Benefit / Sub-benefit | Submitted | Covered | Member pays | Decision | Annual limit left | Visits left | Reason |
|---|---|---|---|---|---|---|---|---|---|---|
| 1 | EXP-002 | 2024-02-10 | DENTAL / Filling | 2,000 | 0 | 2,000 | **DENIED** | 100,000 | 4 | Denied: DENTAL has a 90-day waiting period; cover starts on 2024-03-31 but the expense is dated 2024-02-10. |
| 2 | EXP-001 | 2024-03-15 | OUTPATIENT / Doctor Visit | 2,500 | 2,000 | 500 | **PARTIALLY_COVERED** | 98,000 | 29 | 20% copay applied. Covered: 2000 THB. Member copay: 500 THB. |
| 3 | EXP-003 | 2024-03-22 | OUTPATIENT / Doctor Visit | 4,500 | 3,000 | 1,500 | **PARTIALLY_COVERED** | 95,000 | 28 | 20% copay applied. Capped at the Doctor Visit per-visit limit of 3000 THB (600 THB not covered). Covered: 3000 THB. Member pays: 1500 THB (copay 900 THB + 600 THB above the limit). |
| 4 | EXP-004 | 2024-04-10 | DENTAL / Cleaning | 1,200 | 1,000 | 200 | **PARTIALLY_COVERED** | 94,000 | 3 | Fixed copay of 200 THB applied. Covered: 1000 THB. Member copay: 200 THB. |
| 5 | EXP-005 | 2024-04-18 | DENTAL / Cleaning | 3,000 | 0 | 3,000 | **DENIED** | 94,000 | 3 | Denied: diagnosis 'Cosmetic teeth whitening' is excluded under EX-01 (Cosmetic or aesthetic treatment). |
| 6 | EXP-006 | 2024-05-05 | INPATIENT / Room & Board | 1,500 | 0 | 1,500 | **DENIED** | 94,000 | - | Deductible applied: 1500 THB (500 THB of the 2000 THB INPATIENT annual deductible still to be met). Nothing payable. Member pays: 1500 THB (deductible). |
| 7 | EXP-007 | 2024-05-06 | INPATIENT / Room & Board | 4,000 | 3,500 | 500 | **PARTIALLY_COVERED** | 90,500 | - | Deductible applied: 500 THB (INPATIENT annual deductible of 2000 THB now fully met). Covered: 3500 THB. Member pays: 500 THB (deductible). |
| 8 | EXP-008 | 2024-06-12 | OUTPATIENT / Physiotherapy | 800 | 640 | 160 | **PARTIALLY_COVERED** | 89,860 | 1 | 20% copay applied. Covered: 640 THB. Member copay: 160 THB. |
| 9 | EXP-009 | 2024-06-19 | OUTPATIENT / Physiotherapy | 800 | 640 | 160 | **PARTIALLY_COVERED** | 89,220 | 0 | 20% copay applied. Covered: 640 THB. Member copay: 160 THB. |
| 10 | EXP-010 | 2024-06-26 | OUTPATIENT / Physiotherapy | 800 | 0 | 800 | **DENIED** | 89,220 | 0 | Denied: Physiotherapy visit limit reached (2 of 2 visits used this policy year). |
| 11 | EXP-011 | 2024-07-15 | DENTAL / Filling | 3,500 | 2,500 | 1,000 | **PARTIALLY_COVERED** | 86,720 | 2 | Fixed copay of 200 THB applied. Capped at the Filling per-visit limit of 2500 THB (800 THB not covered). Covered: 2500 THB. Member pays: 1000 THB (copay 200 THB + 800 THB above the limit). |
| 12 | EXP-012 | 2024-08-01 | DENTAL / Filling | 1,800 | 500 | 1,300 | **PARTIALLY_COVERED** | 86,220 | 1 | Fixed copay of 200 THB applied. Capped at the remaining DENTAL annual limit of 500 THB (1100 THB not covered). Covered: 500 THB. Member pays: 1300 THB (copay 200 THB + 1100 THB above the limit). |
| 13 | EXP-013 | 2024-08-20 | DENTAL / Cleaning | 1,000 | 0 | 1,000 | **DENIED** | 86,220 | 1 | Denied: the DENTAL annual limit of 4000 THB is fully used (0 THB remaining). |
| 14 | EXP-014 | 2024-09-10 | INPATIENT / Surgery | 45,000 | 40,000 | 5,000 | **PARTIALLY_COVERED** | 46,220 | - | Capped at the Surgery per-visit limit of 40000 THB (5000 THB not covered). Covered: 40000 THB. Member pays: 5000 THB (above the limit). |
| 15 | EXP-015 | 2024-09-11 | INPATIENT / Room & Board | 5,000 | 5,000 | 0 | **COVERED** | 41,220 | - | Covered in full: 5000 THB. |
| 16 | EXP-016 | 2024-10-01 | MATERNITY / Prenatal Checkup | 1,500 | 0 | 1,500 | **DENIED** | 41,220 | 12 | Denied: MATERNITY has a 280-day waiting period; cover starts on 2024-10-07 but the expense is dated 2024-10-01. |
| 17 | EXP-017 | 2024-10-20 | MATERNITY / Prenatal Checkup | 1,500 | 1,500 | 0 | **COVERED** | 39,720 | 11 | Covered in full: 1500 THB. |
| 18 | EXP-019 | 2024-11-05 | INPATIENT / Surgery | 30,000 | 21,500 | 8,500 | **PARTIALLY_COVERED** | 18,220 | - | Capped at the remaining INPATIENT annual limit of 21500 THB (8500 THB not covered). Covered: 21500 THB. Member pays: 8500 THB (above the limit). |
| 19 | EXP-018 | 2024-12-02 | MATERNITY / Delivery | 25,000 | 18,220 | 6,780 | **PARTIALLY_COVERED** | 0 | - | Capped at the remaining policy annual limit of 18220 THB (6780 THB not covered). Covered: 18220 THB. Member pays: 6780 THB (above the limit). |
| 20 | EXP-020 | 2024-12-20 | OUTPATIENT / Doctor Visit | 1,000 | 0 | 1,000 | **DENIED** | 0 | 26 | Denied: the policy annual limit of 100000 THB is fully used (0 THB remaining). |

## 2. Totals

| Decision | Expenses |
|---|---|
| COVERED | 2 |
| PARTIALLY_COVERED | 11 |
| DENIED | 7 |
| **Total** | 20 |

| Amount | THB |
|---|---|
| Submitted | 136,400 |
| Covered (paid by insurer) | 100,000 |
| Member copay | 2,320 |
| Member deductible | 2,000 |
| Not covered (limits, denials) | 32,080 |
| **Member pays in total** | 36,400 |

## 3. Remaining balance per benefit

Policy annual limit: used 100,000 of 100,000, **remaining 0 THB**. *Available now* = min(benefit remaining, policy remaining).

| Benefit | Annual limit | Used | Remaining | Available now | Deductible met | Paid visits |
|---|---|---|---|---|---|---|
| OUTPATIENT | 20,000 | 6,280 | 13,720 | **0** | - | 4 / 30 |
| INPATIENT | 70,000 | 70,000 | 0 | **0** | 2,000 / 2,000 | 4 (no cap) |
| DENTAL | 4,000 | 4,000 | 0 | **0** | - | 3 / 4 |
| MATERNITY | 30,000 | 19,720 | 10,280 | **0** | - | 2 (no cap) |

| Benefit | Sub-benefit | Per-visit limit | Own annual limit | Used | Remaining | Paid visits |
|---|---|---|---|---|---|---|
| OUTPATIENT | Doctor Visit | 3,000 | - | 5,000 | - | 2 (no cap) |
| OUTPATIENT | Lab Test | 2,000 | - | 0 | - | 0 (no cap) |
| OUTPATIENT | Physiotherapy | 1,000 | - | 1,280 | - | 2 / 2 |
| INPATIENT | Room & Board | 5,000 | - | 8,500 | - | 2 (no cap) |
| INPATIENT | Surgery | 40,000 | - | 61,500 | - | 2 (no cap) |
| DENTAL | Cleaning | 1,500 | - | 1,000 | - | 1 (no cap) |
| DENTAL | Filling | 2,500 | - | 3,000 | - | 2 (no cap) |
| MATERNITY | Prenatal Checkup | 2,000 | - | 1,500 | - | 1 / 12 |
| MATERNITY | Delivery | 25,000 | - | 18,220 | - | 1 (no cap) |

## 4. Calculation rules (applied per expense, in this order)

1. **Eligibility** - denied if: outside the policy period -> benefit / sub-benefit not in the policy -> diagnosis matches an exclusion keyword -> date before `effective_date + waiting_period_days` -> visit cap reached -> a limit (sub-benefit, benefit or policy annual) is already exhausted.
2. **Deductible** - the unmet part of the benefit's annual deductible is paid by the member first.
3. **Copay** - percentage of, or a fixed amount on, what is left after the deductible.
4. **Limits** - the payable amount is capped by the tightest of: per-visit limit, sub-benefit annual remaining, benefit annual remaining, policy annual remaining.
5. **Decision** - COVERED (paid in full), PARTIALLY_COVERED (paid in part), DENIED (nothing paid). A paid expense counts as one visit and consumes all its limits at once.
