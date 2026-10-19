package benefits.model;

import java.math.BigDecimal;
import java.time.LocalDate;

/** One medical expense submitted by the member. */
public record Expense(String expenseId, LocalDate date, String benefitType, String subBenefit, BigDecimal amount,
                     String diagnosis, String provider) {
}
