package claims;

import static claims.TestRunner.assertEquals;
import static claims.TestRunner.assertTrue;
import static claims.TestRunner.log;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Pattern;

/** Generates the 500-row dataset and checks the cleaner against the issues the generator injected. */
final class EndToEndTest {

    /** Injected label → issues the cleaner may report for it (any one is enough). */
    private static final Map<String, Set<Issue>> EXPECTED = Map.ofEntries(
            Map.entry("MISSING_CLAIM_ID", Set.of(Issue.MISSING_CLAIM_ID)),
            Map.entry("DUPLICATE_CLAIM_ID", Set.of(Issue.DUPLICATE_CLAIM_ID)),
            Map.entry("MISSING_POLICY_ID", Set.of(Issue.MISSING_POLICY_ID)),
            Map.entry("NAME_CASING", Set.of(Issue.NAME_FORMAT)),
            Map.entry("CLAIM_TYPE_TYPO", Set.of(Issue.CLAIM_TYPE_FORMAT, Issue.CLAIM_TYPE_TYPO)),
            Map.entry("DIAGNOSIS_NULL", Set.of(Issue.MISSING_DIAGNOSIS)),
            Map.entry("NEGATIVE_AMOUNT", Set.of(Issue.NEGATIVE_AMOUNT)),
            Map.entry("ZERO_AMOUNT", Set.of(Issue.ZERO_AMOUNT)),
            Map.entry("AMOUNT_WITH_COMMAS", Set.of(Issue.AMOUNT_FORMAT)),
            Map.entry("CURRENCY_VARIANT", Set.of(Issue.CURRENCY_FORMAT, Issue.CURRENCY_ALIAS)),
            Map.entry("DATE_FORMAT", Set.of(Issue.DATE_FORMAT)),
            Map.entry("STATUS_VARIANT", Set.of(Issue.STATUS_FORMAT, Issue.STATUS_TYPO)));

    private static final Pattern ISO_DATE = Pattern.compile("\\d{4}-\\d{2}-\\d{2}");

    private static ClaimsGenerator.Generated generate() {
        return new ClaimsGenerator(42).generate(500);
    }

    private static ClaimsCleaner.Result cleanGenerated(ClaimsGenerator.Generated g) {
        List<List<String>> table = new ArrayList<>();
        table.add(ClaimsGenerator.HEADER);
        table.addAll(g.rows());
        return new ClaimsCleaner().clean(table);
    }

    @TestCase(id = "E2E-01", title = "Generator is deterministic and produces 500 rows",
            steps = "Generate the dataset twice with seed 42, 500 rows",
            expected = "500 rows each time; both runs identical")
    static void testGeneratorIsDeterministicAndSized() {
        ClaimsGenerator.Generated a = generate();
        ClaimsGenerator.Generated b = generate();
        log("run 1  : %d rows, first = %s", a.rows().size(), a.rows().get(0));
        log("run 2  : %d rows, first = %s", b.rows().size(), b.rows().get(0));
        log("identical: %s", a.rows().equals(b.rows()));
        assertEquals(500, a.rows().size());
        assertEquals(a.rows(), b.rows());
    }

    @TestCase(id = "E2E-02", title = "15-20% of generated rows contain at least one issue",
            steps = "Generate 500 rows (seed 42); count rows with >= 1 injected issue (incl. duplicates)",
            expected = "Dirty-row rate between 15% and 20%")
    static void testDirtyRowRateIsBetween15And20Percent() {
        ClaimsGenerator.Generated g = generate();
        double rate = g.dirtyRowCount() / 500.0;
        Map<String, Integer> perLabel = new TreeMap<>();
        g.injected().forEach(s -> s.forEach(label -> perLabel.merge(label, 1, Integer::sum)));
        log("dirty rows : %d / 500 = %.1f%%", g.dirtyRowCount(), rate * 100);
        perLabel.forEach((label, n) -> log("  %-20s : %d rows", label, n));
        assertTrue(rate >= 0.15 && rate <= 0.20, "dirty rate out of range: " + rate);
    }

    @TestCase(id = "E2E-03", title = "Duplicates removed match the duplicates injected",
            steps = "Generate 500 rows (seed 42), clean them, compare removed duplicates with the generator log",
            expected = "exact removed = exact injected; near removed = near injected; rows out = 500 - both")
    static void testDuplicateCountsMatchInjected() {
        ClaimsGenerator.Generated g = generate();
        ClaimsCleaner.Result r = cleanGenerated(g);
        long exact = g.injected().stream().filter(s -> s.contains(ClaimsGenerator.EXACT_DUPLICATE)).count();
        long near = g.injected().stream()
                .filter(s -> s.contains(ClaimsGenerator.NEAR_DUPLICATE) && !s.contains(ClaimsGenerator.EXACT_DUPLICATE))
                .count();
        log("exact duplicates : injected %d, removed %d", exact, r.exactDuplicatesRemoved());
        log("near duplicates  : injected %d, removed %d", near, r.nearDuplicatesRemoved());
        log("rows out         : expected %d, actual %d", 500 - exact - near, r.claims().size());
        assertEquals((int) exact, r.exactDuplicatesRemoved());
        assertEquals((int) near, r.nearDuplicatesRemoved());
        assertEquals(500 - (int) exact - (int) near, r.claims().size());
    }

    @TestCase(id = "E2E-04", title = "Every injected issue is detected, untouched rows report none",
            steps = "Generate 500 rows (seed 42), clean them, and compare each kept row's issues\n"
                    + "with the issues the generator injected into it",
            expected = "Recall: every injected label detected (100%).\n"
                    + "Precision: untouched rows have no issue (only DUPLICATE_CLAIM_ID when another row borrowed their id)")
    static void testEveryInjectedIssueDetectedAndNoFalsePositives() {
        ClaimsGenerator.Generated g = generate();
        Map<String, int[]> perLabel = new TreeMap<>(); // label → {detected, injected}
        List<String> misses = new ArrayList<>();
        int untouched = 0;
        int idVictims = 0;
        for (CleanClaim c : cleanGenerated(g).claims()) {
            Set<String> injected = g.injected().get(c.sourceRow() - 2); // dòng 1 là header
            if (injected.contains(ClaimsGenerator.EXACT_DUPLICATE) || injected.contains(ClaimsGenerator.NEAR_DUPLICATE)) {
                continue; // bản giữ lại có thể là bản sao → đã kiểm tra ở E2E-03
            }
            if (injected.isEmpty()) {
                untouched++;
                if (c.issues().equals(Set.of(Issue.DUPLICATE_CLAIM_ID))) {
                    idVictims++;
                } else if (!c.issues().isEmpty()) {
                    misses.add("row " + c.sourceRow() + ": false positive " + c.issues());
                }
                continue;
            }
            for (String label : injected) {
                int[] counts = perLabel.computeIfAbsent(label, k -> new int[2]);
                counts[1]++;
                if (EXPECTED.get(label).stream().anyMatch(c.issues()::contains)) {
                    counts[0]++;
                } else {
                    misses.add("row " + c.sourceRow() + ": " + label + " not detected, got " + c.issues());
                }
            }
        }
        log("%-20s   detected / injected", "injected label");
        perLabel.forEach((label, n) -> log("%-20s   %3d / %d", label, n[0], n[1]));
        log("untouched rows checked: %d, false positives: %d", untouched,
                misses.stream().filter(m -> m.contains("false positive")).count());
        log("  (%d untouched rows flagged DUPLICATE_CLAIM_ID because another row reused their id)", idVictims);
        misses.forEach(m -> log("MISS: %s", m));
        assertTrue(misses.isEmpty(), misses.size() + " problem(s), first: " + (misses.isEmpty() ? "" : misses.get(0)));
    }

    @TestCase(id = "E2E-05", title = "Clean output is consistently formatted",
            steps = "Generate + clean 500 rows (seed 42); check every kept row",
            expected = "claim_type / currency / status in their allowed sets, dates YYYY-MM-DD, amounts without commas,\n"
                    + "missing values always 'NULL' (no '', N/A, n/a)")
    static void testCleanOutputIsConsistentlyFormatted() {
        List<CleanClaim> claims = cleanGenerated(generate()).claims();
        List<String> violations = new ArrayList<>();
        for (CleanClaim c : claims) {
            List<String> v = c.businessValues();
            if (!(v.get(3).equals("NULL") || Normalizers.CLAIM_TYPES.values().contains(v.get(3)))) violations.add("type " + v);
            if (!(v.get(6).equals("NULL") || Normalizers.CURRENCIES.values().contains(v.get(6)))) violations.add("currency " + v);
            if (!(v.get(7).equals("NULL") || ISO_DATE.matcher(v.get(7)).matches())) violations.add("date " + v);
            if (!(v.get(8).equals("NULL") || Normalizers.STATUSES.values().contains(v.get(8)))) violations.add("status " + v);
            if (v.get(5).contains(",")) violations.add("amount " + v);
            if (v.contains("") || v.contains("N/A") || v.contains("n/a")) violations.add("null marker " + v);
        }
        log("rows checked : %d (6 rules each)", claims.size());
        log("violations   : %d", violations.size());
        log("sample output rows:");
        claims.stream().limit(3).forEach(c -> log("  %s", c.toOutputRow()));
        claims.stream().filter(c -> !c.isValid()).limit(2).forEach(c -> log("  %s", c.toOutputRow()));
        violations.stream().limit(10).forEach(v -> log("VIOLATION: %s", v));
        assertTrue(violations.isEmpty(), violations.size() + " violation(s), first: "
                + (violations.isEmpty() ? "" : violations.get(0)));
    }
}
