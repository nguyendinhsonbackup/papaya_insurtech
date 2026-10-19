package benefits.engine;

import java.math.BigDecimal;
import java.util.List;

/**
 * Balance of one benefit after all expenses were processed.
 *
 * @param availableNow what can still be paid: min(benefit remaining, policy annual remaining)
 */
public record BenefitBalance(String benefitType, BigDecimal annualLimit, BigDecimal used, BigDecimal remaining,
                             BigDecimal availableNow, BigDecimal deductible, BigDecimal deductibleMet,
                             Integer maxVisits, int visitsUsed, List<SubBenefitBalance> subBenefits) {

    /** Balance of one sub-benefit; {@code remaining} is null when it has no own annual limit. */
    public record SubBenefitBalance(String name, BigDecimal perVisitLimit, BigDecimal annualLimit, BigDecimal used,
                                    BigDecimal remaining, Integer maxVisits, int visitsUsed) {
    }
}
