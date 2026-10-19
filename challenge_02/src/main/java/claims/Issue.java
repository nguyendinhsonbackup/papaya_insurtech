package claims;

/**
 * Data quality issues the cleaner can detect.
 * <p>
 * {@code blocking = true}: the value cannot be trusted/repaired → the row is kept but flagged {@code is_valid=false}.
 * {@code blocking = false}: the value was repaired automatically (formatting, typo, alias...) → row stays valid.
 */
public enum Issue {
    MISSING_CLAIM_ID(true, "claim_id is empty"),
    DUPLICATE_CLAIM_ID(true, "claim_id shared by rows with different data"),
    MISSING_POLICY_ID(true, "policy_id is empty"),

    MISSING_MEMBER_NAME(false, "member_name is empty"),
    NAME_FORMAT(false, "member_name casing/whitespace normalized to Title Case"),

    MISSING_CLAIM_TYPE(true, "claim_type is empty"),
    CLAIM_TYPE_FORMAT(false, "claim_type casing/separators normalized"),
    CLAIM_TYPE_TYPO(false, "claim_type typo or abbreviation corrected (e.g. Outpateint, OP)"),
    INVALID_CLAIM_TYPE(true, "claim_type not recognized"),

    MISSING_DIAGNOSIS(false, "diagnosis empty / N/A / n/a → NULL"),
    DIAGNOSIS_FORMAT(false, "diagnosis casing/whitespace normalized"),

    MISSING_AMOUNT(true, "submitted_amount is empty"),
    AMOUNT_FORMAT(false, "submitted_amount had thousands separators / spaces (e.g. \"15,000\")"),
    INVALID_AMOUNT(true, "submitted_amount is not a number"),
    NEGATIVE_AMOUNT(true, "submitted_amount < 0"),
    ZERO_AMOUNT(true, "submitted_amount = 0"),

    MISSING_CURRENCY(true, "currency is empty"),
    CURRENCY_FORMAT(false, "currency casing normalized (thb → THB)"),
    CURRENCY_ALIAS(false, "currency name mapped to ISO 4217 code (Baht → THB)"),
    INVALID_CURRENCY(true, "currency not recognized"),

    MISSING_DATE(true, "submitted_date is empty"),
    DATE_FORMAT(false, "submitted_date converted to ISO 8601 (YYYY-MM-DD)"),
    INVALID_DATE(true, "submitted_date unparseable or impossible (e.g. 31/02/2024)"),

    MISSING_STATUS(true, "status is empty"),
    STATUS_FORMAT(false, "status casing/separators normalized (In Review → IN_REVIEW)"),
    STATUS_TYPO(false, "status typo corrected (APROVED → APPROVED)"),
    INVALID_STATUS(true, "status not one of APPROVED, REJECTED, PENDING, IN_REVIEW");

    private final boolean blocking;
    private final String description;

    Issue(boolean blocking, String description) {
        this.blocking = blocking;
        this.description = description;
    }

    public boolean isBlocking() {
        return blocking;
    }

    public String description() {
        return description;
    }
}
