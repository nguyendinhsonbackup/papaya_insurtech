package benefits.engine;

/** Machine-readable rule that reduced or denied an expense (the human-readable text is in {@code reason}). */
public enum ReasonCode {
    // từ chối toàn bộ
    OUTSIDE_POLICY_PERIOD,
    BENEFIT_NOT_COVERED,
    EXCLUSION,
    WAITING_PERIOD,
    VISIT_LIMIT_REACHED,
    LIMIT_EXHAUSTED,
    // giảm một phần
    DEDUCTIBLE,
    COPAY_PERCENTAGE,
    COPAY_FIXED,
    PER_VISIT_LIMIT,
    SUB_BENEFIT_ANNUAL_LIMIT,
    BENEFIT_ANNUAL_LIMIT,
    POLICY_ANNUAL_LIMIT
}
