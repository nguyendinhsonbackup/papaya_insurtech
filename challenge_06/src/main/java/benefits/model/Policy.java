package benefits.model;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/** Policy terms for one policy year. {@code annualLimit} caps the total paid across all benefits. */
public record Policy(String policyId, String policyHolder, String currency, LocalDate effectiveDate,
                     LocalDate expiryDate, BigDecimal annualLimit, List<Exclusion> exclusions,
                     List<Benefit> benefits) {

    public boolean isInForce(LocalDate date) {
        return !date.isBefore(effectiveDate) && !date.isAfter(expiryDate);
    }

    public Optional<Benefit> benefit(String type) {
        return benefits.stream().filter(b -> b.type().equalsIgnoreCase(type.strip())).findFirst();
    }

    public Optional<Exclusion> exclusionFor(String diagnosis) {
        return exclusions.stream().filter(e -> e.matches(diagnosis)).findFirst();
    }
}
