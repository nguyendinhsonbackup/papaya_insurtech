package benefits.io;

import benefits.engine.BenefitBalance;
import benefits.engine.BenefitBalance.SubBenefitBalance;
import benefits.engine.CalculationResult;
import benefits.engine.Decision;
import benefits.engine.ExpenseResult;
import benefits.model.Money;
import benefits.model.Policy;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Human-readable Markdown report: decision per expense + remaining balance per benefit. */
public final class CoverageReport {

    private CoverageReport() {
    }

    public static String toMarkdown(CalculationResult r, String policyFile, String expensesFile) {
        Policy p = r.policy();
        String cur = p.currency();
        StringBuilder md = new StringBuilder();
        md.append("# Coverage Report - ").append(p.policyId()).append("\n\n");
        md.append("- Policy holder: ").append(p.policyHolder()).append("\n");
        md.append("- Policy period: ").append(p.effectiveDate()).append(" to ").append(p.expiryDate()).append("\n");
        md.append("- Policy annual limit: ").append(amount(p.annualLimit())).append(" ").append(cur).append("\n");
        md.append("- Input: `").append(policyFile).append("`, `").append(expensesFile).append("`\n\n");

        md.append("## 1. Decision per expense\n\n");
        md.append("Expenses are processed in chronological order (date, then expense_id): earlier claims consume "
                + "limits first. Amounts in ").append(cur).append(".\n\n");
        List<List<String>> rows = new ArrayList<>();
        for (ExpenseResult x : r.results()) {
            rows.add(List.of(String.valueOf(x.processingOrder()), x.expenseId(), x.date().toString(),
                    x.benefitType() + " / " + x.subBenefit(), amount(x.submittedAmount()), amount(x.coveredAmount()),
                    amount(x.memberPays()), "**" + x.decision() + "**", amount(x.remainingAnnualLimit()),
                    x.remainingVisitLimit() == null ? "-" : String.valueOf(x.remainingVisitLimit()), x.reason()));
        }
        md.append(table(List.of("#", "Expense", "Date", "Benefit / Sub-benefit", "Submitted", "Covered",
                "Member pays", "Decision", "Annual limit left", "Visits left", "Reason"), rows));

        md.append("\n## 2. Totals\n\n");
        md.append(table(List.of("Decision", "Expenses"), List.of(
                List.of("COVERED", String.valueOf(r.count(Decision.COVERED))),
                List.of("PARTIALLY_COVERED", String.valueOf(r.count(Decision.PARTIALLY_COVERED))),
                List.of("DENIED", String.valueOf(r.count(Decision.DENIED))),
                List.of("**Total**", String.valueOf(r.results().size())))));
        md.append("\n");
        md.append(table(List.of("Amount", cur), List.of(
                List.of("Submitted", amount(r.total(ExpenseResult::submittedAmount))),
                List.of("Covered (paid by insurer)", amount(r.total(ExpenseResult::coveredAmount))),
                List.of("Member copay", amount(r.total(ExpenseResult::copayAmount))),
                List.of("Member deductible", amount(r.total(ExpenseResult::deductibleAmount))),
                List.of("Not covered (limits, denials)", amount(r.total(ExpenseResult::notCoveredAmount))),
                List.of("**Member pays in total**", amount(r.total(ExpenseResult::memberPays))))));

        md.append("\n## 3. Remaining balance per benefit\n\n");
        md.append("Policy annual limit: used ").append(amount(r.policyUsed())).append(" of ")
                .append(amount(p.annualLimit())).append(", **remaining ").append(amount(r.policyRemaining()))
                .append(" ").append(cur).append("**. *Available now* = min(benefit remaining, policy remaining).\n\n");
        List<List<String>> benefitRows = new ArrayList<>();
        List<List<String>> subRows = new ArrayList<>();
        for (BenefitBalance b : r.balances()) {
            benefitRows.add(List.of(b.benefitType(), amount(b.annualLimit()), amount(b.used()), amount(b.remaining()),
                    "**" + amount(b.availableNow()) + "**",
                    b.deductible().signum() == 0 ? "-" : amount(b.deductibleMet()) + " / " + amount(b.deductible()),
                    visits(b.visitsUsed(), b.maxVisits())));
            for (SubBenefitBalance s : b.subBenefits()) {
                subRows.add(List.of(b.benefitType(), s.name(), optional(s.perVisitLimit()), optional(s.annualLimit()),
                        amount(s.used()), optional(s.remaining()), visits(s.visitsUsed(), s.maxVisits())));
            }
        }
        md.append(table(List.of("Benefit", "Annual limit", "Used", "Remaining", "Available now",
                "Deductible met", "Paid visits"), benefitRows));
        md.append("\n");
        md.append(table(List.of("Benefit", "Sub-benefit", "Per-visit limit", "Own annual limit", "Used",
                "Remaining", "Paid visits"), subRows));

        md.append("\n## 4. Calculation rules (applied per expense, in this order)\n\n");
        md.append("""
                1. **Eligibility** - denied if: outside the policy period -> benefit / sub-benefit not in the policy -> \
                diagnosis matches an exclusion keyword -> date before `effective_date + waiting_period_days` -> \
                visit cap reached -> a limit (sub-benefit, benefit or policy annual) is already exhausted.
                2. **Deductible** - the unmet part of the benefit's annual deductible is paid by the member first.
                3. **Copay** - percentage of, or a fixed amount on, what is left after the deductible.
                4. **Limits** - the payable amount is capped by the tightest of: per-visit limit, sub-benefit annual \
                remaining, benefit annual remaining, policy annual remaining.
                5. **Decision** - COVERED (paid in full), PARTIALLY_COVERED (paid in part), DENIED (nothing paid). \
                A paid expense counts as one visit and consumes all its limits at once.
                """);
        return md.toString();
    }

    private static String visits(int used, Integer max) {
        return max == null ? used + " (no cap)" : used + " / " + max;
    }

    private static String optional(BigDecimal v) {
        return v == null ? "-" : amount(v);
    }

    private static String amount(BigDecimal v) {
        return String.format(Locale.US, "%,.2f", v).replace(".00", "");
    }

    private static String table(List<String> header, List<List<String>> rows) {
        StringBuilder sb = new StringBuilder();
        sb.append("| ").append(String.join(" | ", header)).append(" |\n");
        sb.append("|").append("---|".repeat(header.size())).append("\n");
        for (List<String> row : rows) {
            sb.append("| ").append(String.join(" | ", row.stream().map(c -> c.replace("|", "\\|")).toList()))
                    .append(" |\n");
        }
        return sb.toString();
    }

    /** Short console line per expense. */
    public static String consoleLine(ExpenseResult x) {
        return String.format(Locale.US, "%2d. %-8s %s %-30s %10s -> %10s  %-17s",
                x.processingOrder(), x.expenseId(), x.date(), x.benefitType() + "/" + x.subBenefit(),
                Money.format(x.submittedAmount()), Money.format(x.coveredAmount()), x.decision());
    }
}
