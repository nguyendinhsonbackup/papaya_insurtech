# AI Challenge 06 — Policy Benefits Calculator

This is a reusable Java 21 module with no dependencies. It takes a **policy definition (JSON)** and a **list of medical expenses** and returns, for each expense, the covered amount, the member's share, a decision and a human-readable reason. It also returns the remaining balance per benefit.

| Deliverable | Path |
|---|---|
| Policy JSON | [`data/policy.json`](data/policy.json) |
| 20 expenses | [`data/expenses.json`](data/expenses.json) |
| Expected outputs (hand-calculated) | [`data/expected_results.json`](data/expected_results.json) |
| Actual outputs (results + summary) | [`output/coverage_results.json`](output/coverage_results.json) |
| Readable report | [`output/coverage_report.md`](output/coverage_report.md) |
| Test results (Excel + evidence screenshots) | [`test-report/benefits_test_report.xlsx`](test-report/benefits_test_report.xlsx), [`test-report/evidence/`](test-report/evidence) |

## Quick start

Requires JDK 21+ (`java` and `javac` on `PATH`).

```bash
./run.sh     # calculate data/expenses.json under data/policy.json  (Windows: .\run.ps1)
./test.sh    # 26 tests + Excel test report                         (Windows: .\test.ps1)
```

Options: `--policy <file>`, `--expenses <file>`, `--out-dir <dir>`.

## Using it as a module

```java
Policy policy = PolicyReader.read(Path.of("policy.json"));        // or PolicyReader.fromJson(Json.parse(text))
List<Expense> expenses = ExpenseReader.read(Path.of("expenses.json"));
CalculationResult result = BenefitsCalculator.calculate(policy, expenses);

result.results();          // ExpenseResult per expense, in processing order
result.balances();         // BenefitBalance per benefit after all expenses
ResultJson.toJson(result); // the output JSON structure
```

The calculator keeps no state between calls. Each `calculate()` starts from full limits.

## Policy format

```jsonc
{
  "policy_id": "POL-2024-000123", "currency": "THB",
  "effective_date": "2024-01-01", "expiry_date": "2024-12-31",
  "annual_limit": 100000,                         // overall cap across all benefits
  "exclusions": [{ "code": "EX-01", "description": "Cosmetic", "keywords": ["cosmetic", "whitening"] }],
  "benefits": [{
    "benefit_type": "OUTPATIENT",
    "annual_limit": 20000,                        // cap for this benefit
    "deductible": 0,                              // annual, member pays it first
    "copay": { "type": "PERCENTAGE", "value": 20 },   // or { "type": "FIXED", "value": 200 }
    "waiting_period_days": 30,                    // covered from effective_date + 30 days
    "max_visits": 30,                             // paid visits per year
    "sub_benefits": [
      { "name": "Doctor Visit", "per_visit_limit": 3000 },
      { "name": "Physiotherapy", "per_visit_limit": 1000, "max_visits": 2 }
      // optional per sub-benefit: annual_limit, copay, waiting_period_days (override the benefit)
    ]
  }]
}
```

## Calculation rules (per expense, in this order)

1. **Chronological processing.** Expenses are sorted by `date`, then `expense_id`, so earlier claims consume limits first. In the sample, EXP-019 (Nov) is processed before EXP-018 (Dec).
2. **Eligibility.** An expense is denied (nothing consumed) if:
   - it is outside the policy period, or
   - its benefit or sub-benefit is not in the policy, or
   - its diagnosis matches an exclusion keyword, or
   - it falls inside the waiting period, or
   - the visit cap is reached, or
   - a limit is already exhausted.
3. **Deductible.** The unmet part of the benefit's annual deductible is paid by the member first.
4. **Copay.** A percentage (rounded HALF_UP to 2 decimals) or a fixed amount, applied on what is left after the deductible.
5. **Limits.** The payable amount is capped by the tightest of four limits: per-visit, sub-benefit annual remaining, benefit annual remaining, and policy annual remaining. All of them are consumed together, which handles overlapping benefits.
6. **Decision.** `COVERED` means paid in full, `PARTIALLY_COVERED` means paid in part, and `DENIED` means nothing was paid. A visit is counted only when something is paid. `reason_codes` gives the machine-readable rule behind each decision.

Each result satisfies `submitted = covered + member_pays` and `member_pays = deductible + copay + not_covered`.

## The 20 expenses

| Scenario | Expenses |
|---|---|
| Covered in full | EXP-015, EXP-017 |
| Partially covered: copay (20% and fixed 200) | EXP-001 (the challenge example), EXP-004, EXP-008, EXP-009 |
| Partially covered: per-visit sub-limit | EXP-003, EXP-011, EXP-014 |
| Partially covered: remaining limit less than the expense | EXP-012 (DENTAL), EXP-019 (INPATIENT), EXP-018 (policy annual) |
| Deductible consumed across two expenses | EXP-006 (all to deductible), EXP-007 |
| Denied: waiting period | EXP-002 (DENTAL 90 days), EXP-016 (MATERNITY 280 days) |
| Denied: exclusion | EXP-005 (cosmetic) |
| Denied: visit limit | EXP-010 (Physiotherapy 2 of 2) |
| Denied: limit exhausted | EXP-013 (DENTAL), EXP-020 (policy annual, although OUTPATIENT still shows 13,720) |

**Result:** 136,400 THB submitted → **100,000 THB covered**. The policy annual limit is fully used. The calculator output matches the hand-calculated `expected_results.json` for 20 of 20 expenses (test E2E-01).

## Tests

26 tests, run by a small dependency-free runner:

| Group | Tests |
|---|---|
| Core rules | CALC-01..16: normal coverage, copay (percentage and fixed), deductible across expenses, per-visit cap, waiting-period boundary, exclusion, visit limit, limit exhaustion by several expenses, policy limit across benefits, sub-benefit annual limit, chronological order, unknown benefit or out of period, denied expenses not consuming limits, reusability, rounding |
| Scenario | E2E-01..04: 20/20 expected results, balances, rule coverage and invariants, output identical to the challenge example |
| Input | JSON-01..03, VAL-01..03: parser and validation (errors name the exact field) |

Each run writes `test-report/benefits_test_report.xlsx` with three sheets:
- **Summary**
- **Testcase**: steps, expected and actual result, status
- **Evidence**: one terminal-style screenshot per test

See **[GUIDELINE.md](GUIDELINE.md)** for the full guide in Vietnamese, including how to run, test and the timeline.
