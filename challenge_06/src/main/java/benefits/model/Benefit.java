package benefits.model;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

/**
 * A benefit group of the policy (OUTPATIENT, INPATIENT, DENTAL, ...).
 *
 * @param annualLimit       max paid per policy year for the whole benefit
 * @param deductible        annual deductible: the first X of eligible expenses is paid by the member (0 = none)
 * @param copay             default copay of its sub-benefits
 * @param waitingPeriodDays expenses are covered from {@code effective_date + waitingPeriodDays}
 * @param maxVisits         max number of paid visits per policy year (null = unlimited)
 */
public record Benefit(String type, BigDecimal annualLimit, BigDecimal deductible, Copay copay, int waitingPeriodDays,
                      Integer maxVisits, List<SubBenefit> subBenefits) {

    public Optional<SubBenefit> subBenefit(String name) {
        return subBenefits.stream().filter(s -> s.name().equalsIgnoreCase(name.strip())).findFirst();
    }

    public Copay copayFor(SubBenefit sub) {
        return sub.copay() != null ? sub.copay() : copay;
    }

    public int waitingPeriodFor(SubBenefit sub) {
        return sub.waitingPeriodDays() != null ? sub.waitingPeriodDays() : waitingPeriodDays;
    }

    public List<String> subBenefitNames() {
        return subBenefits.stream().map(SubBenefit::name).toList();
    }
}
