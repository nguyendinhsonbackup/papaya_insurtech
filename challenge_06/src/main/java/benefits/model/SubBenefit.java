package benefits.model;

import java.math.BigDecimal;

/**
 * A line of cover inside a benefit (e.g. OUTPATIENT → "Doctor Visit").
 * Nullable fields mean "not limited at this level" or, for copay / waiting period, "inherit from the benefit".
 *
 * @param perVisitLimit     max paid for one expense (visit) of this sub-benefit
 * @param annualLimit       max paid per policy year for this sub-benefit
 * @param maxVisits         max number of paid visits per policy year
 * @param copay             overrides the benefit copay
 * @param waitingPeriodDays overrides the benefit waiting period
 */
public record SubBenefit(String name, BigDecimal perVisitLimit, BigDecimal annualLimit, Integer maxVisits,
                         Copay copay, Integer waitingPeriodDays) {
}
