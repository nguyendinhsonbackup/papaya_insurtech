package benefits.engine;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Outcome for one expense. Always: {@code submitted = covered + memberPays} and
 * {@code memberPays = deductible + copay + notCovered}.
 *
 * @param notCoveredAmount     part not paid because of a limit or a denial (excludes deductible and copay)
 * @param remainingAnnualLimit policy annual limit left after this expense
 * @param remainingBenefitLimit annual limit left for the expense's benefit (null if the benefit is unknown)
 * @param remainingVisitLimit  paid visits left for the expense's benefit/sub-benefit (null = no visit cap)
 * @param processingOrder      1-based position in chronological processing
 */
public record ExpenseResult(String expenseId, LocalDate date, String benefitType, String subBenefit,
                            BigDecimal submittedAmount, BigDecimal coveredAmount, BigDecimal copayAmount,
                            BigDecimal deductibleAmount, BigDecimal notCoveredAmount, BigDecimal memberPays,
                            Decision decision, String reason, List<ReasonCode> reasonCodes,
                            BigDecimal remainingAnnualLimit, BigDecimal remainingBenefitLimit,
                            Integer remainingVisitLimit, int processingOrder) {
}
