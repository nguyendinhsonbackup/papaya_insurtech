package benefits.model;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** Money helpers: all amounts are {@link BigDecimal} with scale 2, rounded HALF_UP. */
public final class Money {

    public static final BigDecimal ZERO = scale(BigDecimal.ZERO);
    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    private Money() {
    }

    public static BigDecimal scale(BigDecimal value) {
        return value.setScale(2, RoundingMode.HALF_UP);
    }

    public static BigDecimal percentOf(BigDecimal amount, BigDecimal percent) {
        return amount.multiply(percent).divide(HUNDRED, 2, RoundingMode.HALF_UP);
    }

    public static boolean isPositive(BigDecimal value) {
        return value.signum() > 0;
    }

    /** Plain number without trailing zeros: 2000.00 → "2000", 333.30 → "333.3". */
    public static String format(BigDecimal value) {
        return value.stripTrailingZeros().toPlainString();
    }
}
