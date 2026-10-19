package claims;

import static claims.TestRunner.assertEquals;
import static claims.TestRunner.assertThrows;
import static claims.TestRunner.assertTrue;
import static claims.TestRunner.log;

import java.util.ArrayList;
import java.util.List;

final class ClaimsCleanerTest {

    private static final List<String> CLEAN_ROW =
            List.of("CLM-00001", "POL-100", "John Doe", "OUTPATIENT", "Flu", "1500", "THB", "2024-03-15", "APPROVED");

    /** Cleans the rows (with the standard header) and logs input/output as evidence. */
    private static ClaimsCleaner.Result clean(List<List<String>> rows) {
        List<List<String>> table = new ArrayList<>();
        table.add(ClaimsGenerator.HEADER);
        table.addAll(rows);
        rows.forEach(r -> log("input  : %s", r));
        ClaimsCleaner.Result result = new ClaimsCleaner().clean(table);
        logResult(result);
        return result;
    }

    private static void logResult(ClaimsCleaner.Result r) {
        log("summary: rows in=%d, exact dup removed=%d, near dup removed=%d, rows out=%d",
                r.inputRows(), r.exactDuplicatesRemoved(), r.nearDuplicatesRemoved(), r.claims().size());
        r.claims().forEach(c -> log("output : %s", c.businessValues()));
        r.claims().forEach(c -> log("         row %d -> is_valid=%s, issues=%s", c.sourceRow(), c.isValid(),
                c.issues().isEmpty() ? "-" : c.issues()));
    }

    @TestCase(id = "CLN-01", title = "A clean row passes through unchanged with no issue",
            steps = "Clean 1 row: CLM-00001, POL-100, John Doe, OUTPATIENT, Flu, 1500, THB, 2024-03-15, APPROVED",
            expected = "1 row out, no issues, is_valid=true")
    static void testCleanRowHasNoIssues() {
        ClaimsCleaner.Result r = clean(List.of(CLEAN_ROW));
        assertEquals(1, r.claims().size());
        assertTrue(r.claims().get(0).issues().isEmpty(), "clean row should have no issues");
        assertTrue(r.claims().get(0).isValid(), "clean row should be valid");
    }

    @TestCase(id = "CLN-02", title = "Exact duplicate rows are removed",
            steps = "Clean the same row 3 times",
            expected = "2 exact duplicates removed, 1 row kept")
    static void testExactDuplicatesRemoved() {
        ClaimsCleaner.Result r = clean(List.of(CLEAN_ROW, CLEAN_ROW, CLEAN_ROW));
        assertEquals(2, r.exactDuplicatesRemoved());
        assertEquals(1, r.claims().size());
    }

    @TestCase(id = "CLN-03", title = "Rows identical after normalization are removed as near duplicates",
            steps = "Clean 2 rows of the same claim; row 2 differs only by format:\n"
                    + "JOHN DOE, outpatient, 1,500, thb, 15/03/2024, approved",
            expected = "0 exact + 1 near duplicate removed, 1 row kept")
    static void testNearDuplicateRemovedAfterNormalization() {
        List<String> sameClaimDifferentFormat = List.of("CLM-00001", "POL-100", "JOHN DOE", "outpatient", "Flu",
                "1,500", "thb", "15/03/2024", "approved");
        ClaimsCleaner.Result r = clean(List.of(CLEAN_ROW, sameClaimDifferentFormat));
        assertEquals(0, r.exactDuplicatesRemoved());
        assertEquals(1, r.nearDuplicatesRemoved());
        assertEquals(1, r.claims().size());
    }

    @TestCase(id = "CLN-04", title = "claim_id shared by different claims flags both rows",
            steps = "Clean 2 different claims that both use claim_id CLM-00001",
            expected = "Both rows kept with DUPLICATE_CLAIM_ID and is_valid=false")
    static void testConflictingClaimIdFlagsBothRows() {
        List<String> other = List.of("CLM-00001", "POL-200", "Mary Smith", "DENTAL", "Gingivitis", "3000", "THB",
                "2024-05-01", "PENDING");
        ClaimsCleaner.Result r = clean(List.of(CLEAN_ROW, other));
        assertEquals(2, r.claims().size());
        r.claims().forEach(c -> {
            assertTrue(c.issues().contains(Issue.DUPLICATE_CLAIM_ID), "both rows must be flagged");
            assertTrue(!c.isValid(), "conflicting claim_id makes the row invalid");
        });
    }

    @TestCase(id = "CLN-05", title = "Edge case: a row with many issues reports all of them",
            steps = "Clean 1 row: '', N/A, '  jOHN doe', Outpateint, n/a, -1,500, Baht, March 15, 2024, In Review",
            expected = "10 issues detected (missing ids, name, typo, diagnosis, amount format + negative, alias, date, status);\n"
                    + "values normalized; is_valid=false")
    static void testRowWithMultipleIssuesDetectsAllOfThem() {
        List<String> messy = List.of("", "N/A", "  jOHN doe", "Outpateint", "n/a", "-1,500", "Baht",
                "March 15, 2024", "In Review");
        CleanClaim c = clean(List.of(messy)).claims().get(0);
        log("issues : %d detected", c.issues().size());
        for (Issue expected : List.of(Issue.MISSING_CLAIM_ID, Issue.MISSING_POLICY_ID, Issue.NAME_FORMAT,
                Issue.CLAIM_TYPE_TYPO, Issue.MISSING_DIAGNOSIS, Issue.AMOUNT_FORMAT, Issue.NEGATIVE_AMOUNT,
                Issue.CURRENCY_ALIAS, Issue.DATE_FORMAT, Issue.STATUS_FORMAT)) {
            assertTrue(c.issues().contains(expected), "missing " + expected + " in " + c.issues());
        }
        assertEquals(List.of("NULL", "NULL", "John Doe", "OUTPATIENT", "NULL", "-1500.00", "THB", "2024-03-15",
                "IN_REVIEW"), c.businessValues());
        assertTrue(!c.isValid(), "row must be flagged invalid");
    }

    @TestCase(id = "CLN-06", title = "Columns are matched by header name, not position",
            steps = "Clean a file whose header starts with 'status' (columns reordered)",
            expected = "status read from the right column; row has no issue")
    static void testColumnsMatchedByHeaderNameNotPosition() {
        List<List<String>> table = List.of(
                List.of("status", "claim_id", "policy_id", "member_name", "claim_type", "diagnosis",
                        "submitted_amount", "currency", "submitted_date"),
                List.of("APPROVED", "CLM-00001", "POL-100", "John Doe", "OUTPATIENT", "Flu", "1500", "THB", "2024-03-15"));
        log("header : %s", table.get(0));
        log("input  : %s", table.get(1));
        ClaimsCleaner.Result r = new ClaimsCleaner().clean(table);
        logResult(r);
        CleanClaim c = r.claims().get(0);
        assertEquals("APPROVED", c.status());
        assertTrue(c.issues().isEmpty(), "reordered columns should still be clean");
    }

    @TestCase(id = "CLN-07", title = "A file missing required columns is rejected",
            steps = "Clean a file whose header is only: claim_id, policy_id",
            expected = "IllegalArgumentException naming the first missing column (member_name)")
    static void testMissingColumnIsRejected() {
        List<List<String>> table = List.of(List.of("claim_id", "policy_id"));
        log("header : %s", table.get(0));
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> new ClaimsCleaner().clean(table));
        log("thrown : %s", e);
        assertTrue(e.getMessage().contains("member_name"), "message should name the missing column");
    }
}
