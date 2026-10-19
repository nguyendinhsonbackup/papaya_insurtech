package benefits.model;

import java.math.BigDecimal;

/** Member's share of an eligible amount: a percentage, a fixed amount per visit, or nothing. */
public record Copay(Type type, BigDecimal value) {

    public enum Type { NONE, PERCENTAGE, FIXED }

    public static final Copay NONE = new Copay(Type.NONE, BigDecimal.ZERO);

    public static Copay percentage(BigDecimal percent) {
        return new Copay(Type.PERCENTAGE, percent);
    }

    public static Copay fixed(BigDecimal amount) {
        return new Copay(Type.FIXED, Money.scale(amount));
    }

    /** Copay owed on the eligible amount (a fixed copay never exceeds the amount itself). */
    public BigDecimal on(BigDecimal eligible) {
        return switch (type) {
            case NONE -> Money.ZERO;
            case PERCENTAGE -> Money.percentOf(eligible, value);
            case FIXED -> value.min(eligible);
        };
    }

    /** e.g. "20% copay" or "Fixed copay of 200 THB". */
    public String describe(String currency) {
        return switch (type) {
            case NONE -> "No copay";
            case PERCENTAGE -> Money.format(value) + "% copay";
            case FIXED -> "Fixed copay of " + Money.format(value) + " " + currency;
        };
    }
}
