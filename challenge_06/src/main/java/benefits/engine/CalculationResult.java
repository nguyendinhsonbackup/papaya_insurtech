package benefits.engine;

import benefits.model.Money;
import benefits.model.Policy;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;

/** Results in processing (chronological) order plus the balances left after the last expense. */
public record CalculationResult(Policy policy, List<ExpenseResult> results, BigDecimal policyUsed,
                                BigDecimal policyRemaining, List<BenefitBalance> balances) {

    public BigDecimal total(Function<ExpenseResult, BigDecimal> amount) {
        return results.stream().map(amount).reduce(Money.ZERO, BigDecimal::add);
    }

    public long count(Decision decision) {
        return results.stream().filter(r -> r.decision() == decision).count();
    }

    public Optional<ExpenseResult> result(String expenseId) {
        return results.stream().filter(r -> r.expenseId().equals(expenseId)).findFirst();
    }

    public Optional<BenefitBalance> balance(String benefitType) {
        return balances.stream().filter(b -> b.benefitType().equalsIgnoreCase(benefitType)).findFirst();
    }
}
