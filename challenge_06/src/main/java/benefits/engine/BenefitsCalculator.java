package benefits.engine;

import benefits.model.Benefit;
import benefits.model.Copay;
import benefits.model.Exclusion;
import benefits.model.Expense;
import benefits.model.Money;
import benefits.model.Policy;
import benefits.model.SubBenefit;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Calculates the covered amount of each expense under a policy. Reusable and stateless between calls:
 * {@link #calculate(List)} starts every run with a fresh ledger.
 *
 * <h2>Rule order (per expense)</h2>
 * <ol>
 *   <li><b>Eligibility</b> — deny if: outside the policy period → benefit / sub-benefit not in the policy →
 *       diagnosis excluded → inside the waiting period → visit cap reached → a limit is already exhausted.</li>
 *   <li><b>Deductible</b> — the unmet part of the benefit's annual deductible is taken first.</li>
 *   <li><b>Copay</b> — percentage of, or fixed amount on, what is left after the deductible.</li>
 *   <li><b>Limits</b> — the payable amount is capped by the tightest of: per-visit limit, sub-benefit annual
 *       remaining, benefit annual remaining, policy annual remaining.</li>
 * </ol>
 * Expenses are processed chronologically (date, then expense_id), so earlier ones consume limits first.
 */
public final class BenefitsCalculator {

    private final Policy policy;
    private final String currency;

    public BenefitsCalculator(Policy policy) {
        this.policy = Objects.requireNonNull(policy, "policy");
        this.currency = policy.currency();
    }

    /** Convenience one-shot call. */
    public static CalculationResult calculate(Policy policy, List<Expense> expenses) {
        return new BenefitsCalculator(policy).calculate(expenses);
    }

    public CalculationResult calculate(List<Expense> expenses) {
        BenefitLedger ledger = new BenefitLedger(policy);
        List<Expense> chronological = expenses.stream()
                .sorted(Comparator.comparing(Expense::date).thenComparing(Expense::expenseId))
                .toList();
        List<ExpenseResult> results = new ArrayList<>();
        for (Expense e : chronological) {
            results.add(assess(e, ledger, results.size() + 1));
        }
        return new CalculationResult(policy, List.copyOf(results), ledger.policyUsed(), ledger.policyRemaining(),
                ledger.balances());
    }

    // ---- one expense ----

    private ExpenseResult assess(Expense e, BenefitLedger ledger, int order) {
        if (!policy.isInForce(e.date())) {
            return deny(e, ledger, order, null, null, ReasonCode.OUTSIDE_POLICY_PERIOD, String.format(
                    "Denied: expense date %s is outside the policy period %s to %s.",
                    e.date(), policy.effectiveDate(), policy.expiryDate()));
        }
        Optional<Benefit> benefit = policy.benefit(e.benefitType());
        if (benefit.isEmpty()) {
            return deny(e, ledger, order, null, null, ReasonCode.BENEFIT_NOT_COVERED, String.format(
                    "Denied: benefit type '%s' is not covered by policy %s (covered: %s).", e.benefitType(),
                    policy.policyId(), String.join(", ", policy.benefits().stream().map(Benefit::type).toList())));
        }
        Benefit b = benefit.get();
        Optional<SubBenefit> sub = b.subBenefit(e.subBenefit());
        if (sub.isEmpty()) {
            return deny(e, ledger, order, b, null, ReasonCode.BENEFIT_NOT_COVERED, String.format(
                    "Denied: '%s' is not a covered %s sub-benefit (covered: %s).", e.subBenefit(), b.type(),
                    String.join(", ", b.subBenefitNames())));
        }
        SubBenefit s = sub.get();

        Optional<Exclusion> exclusion = policy.exclusionFor(e.diagnosis());
        if (exclusion.isPresent()) {
            return deny(e, ledger, order, b, s, ReasonCode.EXCLUSION, String.format(
                    "Denied: diagnosis '%s' is excluded under %s (%s).", e.diagnosis(), exclusion.get().code(),
                    exclusion.get().description()));
        }
        int waitingDays = b.waitingPeriodFor(s);
        LocalDate coverStarts = policy.effectiveDate().plusDays(waitingDays);
        if (e.date().isBefore(coverStarts)) {
            String scope = s.waitingPeriodDays() != null ? s.name() : b.type();
            return deny(e, ledger, order, b, s, ReasonCode.WAITING_PERIOD, String.format(
                    "Denied: %s has a %d-day waiting period; cover starts on %s but the expense is dated %s.",
                    scope, waitingDays, coverStarts, e.date()));
        }
        String visitCap = visitCapReached(b, s, ledger);
        if (visitCap != null) {
            return deny(e, ledger, order, b, s, ReasonCode.VISIT_LIMIT_REACHED, "Denied: " + visitCap + ".");
        }
        String exhausted = exhaustedLimit(b, s, ledger);
        if (exhausted != null) {
            return deny(e, ledger, order, b, s, ReasonCode.LIMIT_EXHAUSTED,
                    "Denied: " + exhausted + " is fully used (0 " + currency + " remaining).");
        }
        return pay(e, ledger, order, b, s);
    }

    private ExpenseResult pay(Expense e, BenefitLedger ledger, int order, Benefit b, SubBenefit s) {
        List<ReasonCode> codes = new ArrayList<>();
        BigDecimal amount = Money.scale(e.amount());

        BigDecimal deductible = ledger.deductibleRemaining(b).min(amount);
        if (Money.isPositive(deductible)) {
            codes.add(ReasonCode.DEDUCTIBLE);
        }
        BigDecimal eligible = amount.subtract(deductible);

        Copay copayRule = b.copayFor(s);
        BigDecimal copay = copayRule.on(eligible);
        if (Money.isPositive(copay)) {
            codes.add(copayRule.type() == Copay.Type.PERCENTAGE ? ReasonCode.COPAY_PERCENTAGE : ReasonCode.COPAY_FIXED);
        }
        BigDecimal payable = eligible.subtract(copay);

        Cap cap = tightestCap(b, s, ledger);
        BigDecimal covered = cap != null && cap.amount().compareTo(payable) < 0 ? cap.amount() : payable;
        BigDecimal notCovered = payable.subtract(covered);
        if (Money.isPositive(notCovered)) {
            codes.add(cap.code());
        }

        ledger.record(b, s, covered, deductible);
        BigDecimal memberPays = amount.subtract(covered);
        String reason = explain(b, ledger, copayRule, amount, deductible, copay, cap, notCovered, covered, memberPays);
        return new ExpenseResult(e.expenseId(), e.date(), b.type(), s.name(), amount, covered, copay, deductible,
                notCovered, memberPays, decision(amount, covered), reason, List.copyOf(codes),
                ledger.policyRemaining(), ledger.benefitRemaining(b), ledger.visitsRemaining(b, s), order);
    }

    private ExpenseResult deny(Expense e, BenefitLedger ledger, int order, Benefit b, SubBenefit s,
                               ReasonCode code, String reason) {
        BigDecimal amount = Money.scale(e.amount());
        return new ExpenseResult(e.expenseId(), e.date(), b != null ? b.type() : e.benefitType(),
                s != null ? s.name() : e.subBenefit(), amount, Money.ZERO, Money.ZERO, Money.ZERO, amount, amount,
                Decision.DENIED, reason, List.of(code), ledger.policyRemaining(),
                b != null ? ledger.benefitRemaining(b) : null,
                b != null && s != null ? ledger.visitsRemaining(b, s) : null, order);
    }

    // ---- rules ----

    private record Cap(ReasonCode code, BigDecimal amount, String description) {
    }

    /** Smallest applicable limit; on a tie the narrowest scope wins (per visit → sub → benefit → policy). */
    private Cap tightestCap(Benefit b, SubBenefit s, BenefitLedger ledger) {
        List<Cap> caps = new ArrayList<>();
        if (s.perVisitLimit() != null) {
            caps.add(new Cap(ReasonCode.PER_VISIT_LIMIT, s.perVisitLimit(),
                    "the " + s.name() + " per-visit limit of " + money(s.perVisitLimit())));
        }
        BigDecimal subLeft = ledger.subRemaining(b, s);
        if (subLeft != null) {
            caps.add(new Cap(ReasonCode.SUB_BENEFIT_ANNUAL_LIMIT, subLeft,
                    "the remaining " + s.name() + " annual limit of " + money(subLeft)));
        }
        caps.add(new Cap(ReasonCode.BENEFIT_ANNUAL_LIMIT, ledger.benefitRemaining(b),
                "the remaining " + b.type() + " annual limit of " + money(ledger.benefitRemaining(b))));
        caps.add(new Cap(ReasonCode.POLICY_ANNUAL_LIMIT, ledger.policyRemaining(),
                "the remaining policy annual limit of " + money(ledger.policyRemaining())));
        Cap tightest = null;
        for (Cap c : caps) {
            if (tightest == null || c.amount().compareTo(tightest.amount()) < 0) {
                tightest = c;
            }
        }
        return tightest;
    }

    private static String visitCapReached(Benefit b, SubBenefit s, BenefitLedger ledger) {
        if (s.maxVisits() != null && ledger.subVisits(b, s) >= s.maxVisits()) {
            return String.format("%s visit limit reached (%d of %d visits used this policy year)",
                    s.name(), ledger.subVisits(b, s), s.maxVisits());
        }
        if (b.maxVisits() != null && ledger.benefitVisits(b) >= b.maxVisits()) {
            return String.format("%s visit limit reached (%d of %d visits used this policy year)",
                    b.type(), ledger.benefitVisits(b), b.maxVisits());
        }
        return null;
    }

    private String exhaustedLimit(Benefit b, SubBenefit s, BenefitLedger ledger) {
        BigDecimal subLeft = ledger.subRemaining(b, s);
        if (subLeft != null && subLeft.signum() == 0) {
            return "the " + s.name() + " annual limit of " + money(s.annualLimit());
        }
        if (ledger.benefitRemaining(b).signum() == 0) {
            return "the " + b.type() + " annual limit of " + money(b.annualLimit());
        }
        if (ledger.policyRemaining().signum() == 0) {
            return "the policy annual limit of " + money(policy.annualLimit());
        }
        return null;
    }

    // ---- explanation ----

    private String explain(Benefit b, BenefitLedger ledger, Copay copayRule, BigDecimal amount, BigDecimal deductible,
                           BigDecimal copay, Cap cap, BigDecimal notCovered, BigDecimal covered, BigDecimal memberPays) {
        List<String> parts = new ArrayList<>();
        if (Money.isPositive(deductible)) {
            BigDecimal left = ledger.deductibleRemaining(b);
            parts.add("Deductible applied: " + money(deductible) + (left.signum() == 0
                    ? " (" + b.type() + " annual deductible of " + money(b.deductible()) + " now fully met)."
                    : " (" + money(left) + " of the " + money(b.deductible()) + " " + b.type()
                    + " annual deductible still to be met)."));
        }
        if (Money.isPositive(copay)) {
            String rule = copayRule.describe(currency) + " applied";
            parts.add(copayRule.type() == Copay.Type.FIXED && copay.compareTo(copayRule.value()) < 0
                    ? rule + " (limited to the " + money(copay) + " eligible amount)." : rule + ".");
        }
        if (Money.isPositive(notCovered)) {
            parts.add("Capped at " + cap.description() + " (" + money(notCovered) + " not covered).");
        }
        if (covered.signum() == 0) {
            parts.add("Nothing payable.");
        } else if (covered.compareTo(amount) == 0) {
            parts.add("Covered in full: " + money(covered) + ".");
        } else {
            parts.add("Covered: " + money(covered) + ".");
        }

        // phần thành viên trả: chỉ 1 thành phần → nêu nhãn; nhiều thành phần → liệt kê từng khoản
        List<String> labels = new ArrayList<>();
        List<String> shares = new ArrayList<>();
        if (Money.isPositive(deductible)) {
            labels.add("deductible");
            shares.add("deductible " + money(deductible));
        }
        if (Money.isPositive(copay)) {
            labels.add("copay");
            shares.add("copay " + money(copay));
        }
        if (Money.isPositive(notCovered)) {
            labels.add("above the limit");
            shares.add(money(notCovered) + " above the limit");
        }
        if (labels.equals(List.of("copay"))) {
            parts.add("Member copay: " + money(copay) + ".");
        } else if (labels.size() == 1) {
            parts.add("Member pays: " + money(memberPays) + " (" + labels.get(0) + ").");
        } else if (!labels.isEmpty()) {
            parts.add("Member pays: " + money(memberPays) + " (" + String.join(" + ", shares) + ").");
        }
        return String.join(" ", parts);
    }

    private static Decision decision(BigDecimal submitted, BigDecimal covered) {
        if (covered.signum() == 0) {
            return Decision.DENIED;
        }
        return covered.compareTo(submitted) == 0 ? Decision.COVERED : Decision.PARTIALLY_COVERED;
    }

    private String money(BigDecimal amount) {
        return Money.format(amount) + " " + currency;
    }
}
