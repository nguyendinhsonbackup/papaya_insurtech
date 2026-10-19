package benefits.engine;

import benefits.engine.BenefitBalance.SubBenefitBalance;
import benefits.model.Benefit;
import benefits.model.Money;
import benefits.model.Policy;
import benefits.model.SubBenefit;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Running usage for one policy year: amounts paid (policy / benefit / sub-benefit), deductible met and visits.
 * One ledger per calculation, so a calculator call never leaks state into the next one.
 */
final class BenefitLedger {

    private final Policy policy;
    private BigDecimal policyUsed = Money.ZERO;
    private final Map<String, BigDecimal> benefitUsed = new HashMap<>();
    private final Map<String, BigDecimal> deductibleMet = new HashMap<>();
    private final Map<String, Integer> benefitVisits = new HashMap<>();
    private final Map<String, BigDecimal> subUsed = new HashMap<>();
    private final Map<String, Integer> subVisits = new HashMap<>();

    BenefitLedger(Policy policy) {
        this.policy = policy;
    }

    BigDecimal policyRemaining() {
        return policy.annualLimit().subtract(policyUsed).max(Money.ZERO);
    }

    BigDecimal benefitRemaining(Benefit b) {
        return b.annualLimit().subtract(benefitUsed(b)).max(Money.ZERO);
    }

    /** Null when the sub-benefit has no annual limit of its own. */
    BigDecimal subRemaining(Benefit b, SubBenefit s) {
        return s.annualLimit() == null ? null : s.annualLimit().subtract(subUsed(b, s)).max(Money.ZERO);
    }

    BigDecimal deductibleRemaining(Benefit b) {
        return b.deductible().subtract(deductibleMet.getOrDefault(b.type(), Money.ZERO)).max(Money.ZERO);
    }

    int benefitVisits(Benefit b) {
        return benefitVisits.getOrDefault(b.type(), 0);
    }

    int subVisits(Benefit b, SubBenefit s) {
        return subVisits.getOrDefault(key(b, s), 0);
    }

    /** Most restrictive visits left (sub-benefit cap vs benefit cap); null when neither has a cap. */
    Integer visitsRemaining(Benefit b, SubBenefit s) {
        Integer left = null;
        if (b.maxVisits() != null) {
            left = Math.max(0, b.maxVisits() - benefitVisits(b));
        }
        if (s.maxVisits() != null) {
            int subLeft = Math.max(0, s.maxVisits() - subVisits(b, s));
            left = left == null ? subLeft : Math.min(left, subLeft);
        }
        return left;
    }

    /** Records a processed expense. A visit is counted only when something was paid. */
    void record(Benefit b, SubBenefit s, BigDecimal covered, BigDecimal deductibleApplied) {
        deductibleMet.merge(b.type(), deductibleApplied, BigDecimal::add);
        if (!Money.isPositive(covered)) {
            return;
        }
        policyUsed = policyUsed.add(covered);
        benefitUsed.merge(b.type(), covered, BigDecimal::add);
        subUsed.merge(key(b, s), covered, BigDecimal::add);
        benefitVisits.merge(b.type(), 1, Integer::sum);
        subVisits.merge(key(b, s), 1, Integer::sum);
    }

    BigDecimal policyUsed() {
        return policyUsed;
    }

    List<BenefitBalance> balances() {
        return policy.benefits().stream().map(b -> new BenefitBalance(
                b.type(), b.annualLimit(), benefitUsed(b), benefitRemaining(b),
                benefitRemaining(b).min(policyRemaining()), b.deductible(),
                deductibleMet.getOrDefault(b.type(), Money.ZERO), b.maxVisits(), benefitVisits(b),
                b.subBenefits().stream().map(s -> new SubBenefitBalance(s.name(), s.perVisitLimit(), s.annualLimit(),
                        subUsed(b, s), subRemaining(b, s), s.maxVisits(), subVisits(b, s))).toList())).toList();
    }

    private BigDecimal benefitUsed(Benefit b) {
        return benefitUsed.getOrDefault(b.type(), Money.ZERO);
    }

    private BigDecimal subUsed(Benefit b, SubBenefit s) {
        return subUsed.getOrDefault(key(b, s), Money.ZERO);
    }

    private static String key(Benefit b, SubBenefit s) {
        return b.type() + "|" + s.name();
    }
}
