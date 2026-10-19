package claims;

import static claims.TestRunner.assertEquals;
import static claims.TestRunner.assertTrue;
import static claims.TestRunner.log;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.EnumSet;
import java.util.Set;
import java.util.function.BiFunction;

final class NormalizersTest {

    private static void header() {
        log("%-22s => %-18s %s", "INPUT", "OUTPUT", "ISSUES");
    }

    /** Runs a normalizer and checks both the value and the exact set of issues reported. */
    private static <T> void check(BiFunction<String, Set<Issue>, T> fn, String raw, T expected, Issue... expectedIssues) {
        Set<Issue> issues = EnumSet.noneOf(Issue.class);
        T actual = fn.apply(raw, issues);
        log("%-22s => %-18s %s", "'" + raw + "'", actual == null ? "NULL" : actual, issues.isEmpty() ? "-" : issues);
        assertEquals(expected, actual);
        Set<Issue> want = EnumSet.noneOf(Issue.class);
        want.addAll(Set.of(expectedIssues));
        assertTrue(want.equals(issues), "raw='" + raw + "' expected issues " + want + " but got " + issues);
    }

    @TestCase(id = "NORM-01", title = "member_name is normalized to Title Case",
            steps = "Normalize member_name: 'John Doe', 'JOHN DOE', 'john doe', '  jOHN   dOE ', ''",
            expected = "All non-empty values -> 'John Doe' (NAME_FORMAT when changed); '' -> NULL + MISSING_MEMBER_NAME")
    static void testMemberNameCasing() {
        header();
        check(Normalizers::memberName, "John Doe", "John Doe");
        check(Normalizers::memberName, "JOHN DOE", "John Doe", Issue.NAME_FORMAT);
        check(Normalizers::memberName, "john doe", "John Doe", Issue.NAME_FORMAT);
        check(Normalizers::memberName, "  jOHN   dOE ", "John Doe", Issue.NAME_FORMAT);
        check(Normalizers::memberName, "", null, Issue.MISSING_MEMBER_NAME);
    }

    @TestCase(id = "NORM-02", title = "claim_type casing, typos and abbreviations are corrected",
            steps = "Normalize claim_type: OUTPATIENT, outpatient, Out-Patient, Outpateint, OP, Inpatinet, XRAY, ' '",
            expected = "-> OUTPATIENT / INPATIENT with CLAIM_TYPE_FORMAT (casing) or CLAIM_TYPE_TYPO (typo, OP);\n"
                    + "XRAY -> NULL + INVALID_CLAIM_TYPE; blank -> NULL + MISSING_CLAIM_TYPE")
    static void testClaimTypeTyposAndAbbreviations() {
        header();
        check(Normalizers::claimType, "OUTPATIENT", "OUTPATIENT");
        check(Normalizers::claimType, "outpatient", "OUTPATIENT", Issue.CLAIM_TYPE_FORMAT);
        check(Normalizers::claimType, "Out-Patient", "OUTPATIENT", Issue.CLAIM_TYPE_FORMAT);
        check(Normalizers::claimType, "Outpateint", "OUTPATIENT", Issue.CLAIM_TYPE_TYPO);
        check(Normalizers::claimType, "OP", "OUTPATIENT", Issue.CLAIM_TYPE_TYPO);
        check(Normalizers::claimType, "Inpatinet", "INPATIENT", Issue.CLAIM_TYPE_TYPO);
        check(Normalizers::claimType, "XRAY", null, Issue.INVALID_CLAIM_TYPE);
        check(Normalizers::claimType, " ", null, Issue.MISSING_CLAIM_TYPE);
    }

    @TestCase(id = "NORM-03", title = "diagnosis 'N/A', 'n/a' and empty become the NULL marker",
            steps = "Normalize diagnosis: N/A, n/a, '', Flu, ACUTE BRONCHITIS",
            expected = "N/A / n/a / '' -> NULL + MISSING_DIAGNOSIS; Flu unchanged; ACUTE BRONCHITIS -> Acute bronchitis")
    static void testDiagnosisNullMarkers() {
        header();
        check(Normalizers::diagnosis, "N/A", null, Issue.MISSING_DIAGNOSIS);
        check(Normalizers::diagnosis, "n/a", null, Issue.MISSING_DIAGNOSIS);
        check(Normalizers::diagnosis, "", null, Issue.MISSING_DIAGNOSIS);
        check(Normalizers::diagnosis, "Flu", "Flu");
        check(Normalizers::diagnosis, "ACUTE BRONCHITIS", "Acute bronchitis", Issue.DIAGNOSIS_FORMAT);
    }

    @TestCase(id = "NORM-04", title = "submitted_amount: commas parsed, negative / zero / non-numeric flagged",
            steps = "Normalize amount: 15000, 15,000, 1,234,567.5, -500, -1,500, 0.00, abc, 1,5, ''",
            expected = "Thousands separators removed (AMOUNT_FORMAT), 2 decimals;\n"
                    + "-500 -> NEGATIVE_AMOUNT; 0.00 -> ZERO_AMOUNT; abc / 1,5 -> NULL + INVALID_AMOUNT; '' -> MISSING_AMOUNT")
    static void testAmounts() {
        header();
        check(Normalizers::amount, "15000", new BigDecimal("15000.00"));
        check(Normalizers::amount, "15,000", new BigDecimal("15000.00"), Issue.AMOUNT_FORMAT);
        check(Normalizers::amount, "1,234,567.5", new BigDecimal("1234567.50"), Issue.AMOUNT_FORMAT);
        check(Normalizers::amount, "-500", new BigDecimal("-500.00"), Issue.NEGATIVE_AMOUNT);
        check(Normalizers::amount, "-1,500", new BigDecimal("-1500.00"), Issue.AMOUNT_FORMAT, Issue.NEGATIVE_AMOUNT);
        check(Normalizers::amount, "0.00", new BigDecimal("0.00"), Issue.ZERO_AMOUNT);
        check(Normalizers::amount, "abc", null, Issue.INVALID_AMOUNT);
        check(Normalizers::amount, "1,5", null, Issue.INVALID_AMOUNT); // dấu phẩy thập phân → mơ hồ
        check(Normalizers::amount, "", null, Issue.MISSING_AMOUNT);
    }

    @TestCase(id = "NORM-05", title = "currency is standardized to uppercase ISO 4217",
            steps = "Normalize currency: THB, thb, Baht, vnd, dong, USD",
            expected = "thb -> THB, vnd -> VND (CURRENCY_FORMAT); Baht -> THB, dong -> VND (CURRENCY_ALIAS);\n"
                    + "USD -> NULL + INVALID_CURRENCY")
    static void testCurrencies() {
        header();
        check(Normalizers::currency, "THB", "THB");
        check(Normalizers::currency, "thb", "THB", Issue.CURRENCY_FORMAT);
        check(Normalizers::currency, "Baht", "THB", Issue.CURRENCY_ALIAS);
        check(Normalizers::currency, "vnd", "VND", Issue.CURRENCY_FORMAT);
        check(Normalizers::currency, "dong", "VND", Issue.CURRENCY_ALIAS);
        check(Normalizers::currency, "USD", null, Issue.INVALID_CURRENCY);
    }

    @TestCase(id = "NORM-06", title = "submitted_date in 3 formats is converted to ISO 8601",
            steps = "Normalize date: 2024-03-15, 15/03/2024, March 15, 2024, march 15, 2024, Mar 15, 2024,\n"
                    + "31/02/2024, 2024-13-01, n/a",
            expected = "All valid inputs -> 2024-03-15 (DATE_FORMAT when converted);\n"
                    + "31/02/2024, 2024-13-01 -> NULL + INVALID_DATE; n/a -> NULL + MISSING_DATE")
    static void testDates() {
        header();
        LocalDate expected = LocalDate.of(2024, 3, 15);
        check(Normalizers::date, "2024-03-15", expected);
        check(Normalizers::date, "15/03/2024", expected, Issue.DATE_FORMAT);
        check(Normalizers::date, "March 15, 2024", expected, Issue.DATE_FORMAT);
        check(Normalizers::date, "march 15, 2024", expected, Issue.DATE_FORMAT);
        check(Normalizers::date, "Mar 15, 2024", expected, Issue.DATE_FORMAT);
        check(Normalizers::date, "31/02/2024", null, Issue.INVALID_DATE);
        check(Normalizers::date, "2024-13-01", null, Issue.INVALID_DATE);
        check(Normalizers::date, "n/a", null, Issue.MISSING_DATE);
    }

    @TestCase(id = "NORM-07", title = "status is normalized to APPROVED / REJECTED / PENDING / IN_REVIEW",
            steps = "Normalize status: APPROVED, In Review, pending, APROVED, CANCELLED",
            expected = "In Review -> IN_REVIEW, pending -> PENDING (STATUS_FORMAT); APROVED -> APPROVED (STATUS_TYPO);\n"
                    + "CANCELLED -> NULL + INVALID_STATUS")
    static void testStatuses() {
        header();
        check(Normalizers::status, "APPROVED", "APPROVED");
        check(Normalizers::status, "In Review", "IN_REVIEW", Issue.STATUS_FORMAT);
        check(Normalizers::status, "pending", "PENDING", Issue.STATUS_FORMAT);
        check(Normalizers::status, "APROVED", "APPROVED", Issue.STATUS_TYPO);
        check(Normalizers::status, "CANCELLED", null, Issue.INVALID_STATUS);
    }
}
