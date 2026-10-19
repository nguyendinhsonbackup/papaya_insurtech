package claims;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Builds the Markdown data quality report from a cleaning {@link ClaimsCleaner.Result}. */
public final class ClaimsReport {

    private ClaimsReport() {
    }

    public static String toMarkdown(ClaimsCleaner.Result r, String inputFile, String outputFile) {
        List<CleanClaim> claims = r.claims();
        StringBuilder md = new StringBuilder();
        md.append("# Claims Data Quality Report\n\n");
        md.append("- Input: `").append(inputFile).append("`\n");
        md.append("- Clean output: `").append(outputFile).append("`\n");
        md.append("- Generated at: ").append(LocalDateTime.now().withNano(0)).append("\n\n");

        // 1. Row counts
        md.append("## 1. Row counts\n\n");
        long invalid = claims.size() - r.validRows();
        md.append(table(List.of("Metric", "Rows"), List.of(
                List.of("Rows before cleaning (excluding header)", num(r.inputRows())),
                List.of("Exact duplicate rows removed", num(r.exactDuplicatesRemoved())),
                List.of("Near duplicates removed (identical after normalization)", num(r.nearDuplicatesRemoved())),
                List.of("**Total duplicates removed**", num(r.exactDuplicatesRemoved() + r.nearDuplicatesRemoved())),
                List.of("Malformed rows (wrong column count)", num(r.malformedRows())),
                List.of("**Rows after cleaning**", num(claims.size())),
                List.of("&nbsp;&nbsp;valid (`is_valid=true`)", num(r.validRows())),
                List.of("&nbsp;&nbsp;flagged invalid (`is_valid=false`)", num(invalid)))));

        // 2. Issues
        md.append("\n## 2. Issues found (rows after de-duplication)\n\n");
        md.append("*Fixed* = repaired automatically, row stays valid. *Flagged* = cannot be trusted, row marked `is_valid=false`.\n\n");
        Map<Issue, Long> issueCounts = new EnumMap<>(Issue.class);
        claims.forEach(c -> c.issues().forEach(i -> issueCounts.merge(i, 1L, Long::sum)));
        List<List<String>> issueRows = new ArrayList<>();
        for (Issue issue : Issue.values()) {
            issueRows.add(List.of("`" + issue.name() + "`", issue.isBlocking() ? "Flagged" : "Fixed",
                    num(issueCounts.getOrDefault(issue, 0L)), issue.description()));
        }
        md.append(table(List.of("Issue", "Action", "Rows", "Description"), issueRows));

        md.append("\n**Rows by number of issues** (edge case: one row can carry several issues)\n\n");
        Map<String, Long> perRow = new LinkedHashMap<>();
        for (String bucket : List.of("0", "1", "2", "3+")) {
            perRow.put(bucket, 0L);
        }
        claims.forEach(c -> perRow.merge(c.issues().size() >= 3 ? "3+" : String.valueOf(c.issues().size()), 1L, Long::sum));
        md.append(table(List.of("Issues per row", "Rows", "Share"),
                perRow.entrySet().stream().map(e -> List.of(e.getKey(), num(e.getValue()),
                        pct(e.getValue(), claims.size()))).toList()));

        // 3 & 4. Counts by type / status
        md.append("\n## 3. Claims by type\n\n");
        md.append(countTable("Claim type", claims, CleanClaim::claimType));
        md.append("\n## 4. Claims by status\n\n");
        md.append(countTable("Status", claims, CleanClaim::status));

        // 5. Average amount
        md.append("\n## 5. Average submitted amount by type\n\n");
        md.append("Only rows with a positive amount and a known currency. Currencies are **not** mixed "
                + "(no FX conversion), so each type is reported per currency.\n\n");
        md.append(averageTable(claims));

        // 6. Top diagnoses
        md.append("\n## 6. Top 5 most common diagnoses\n\n");
        Map<String, Long> diagnoses = claims.stream().filter(c -> c.diagnosis() != null)
                .collect(Collectors.groupingBy(CleanClaim::diagnosis, Collectors.counting()));
        List<Map.Entry<String, Long>> top = diagnoses.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed().thenComparing(Map.Entry.comparingByKey()))
                .limit(5).toList();
        List<List<String>> topRows = new ArrayList<>();
        for (int i = 0; i < top.size(); i++) {
            topRows.add(List.of(String.valueOf(i + 1), top.get(i).getKey(), num(top.get(i).getValue())));
        }
        md.append(table(List.of("Rank", "Diagnosis", "Claims"), topRows));
        long missingDiagnosis = claims.stream().filter(c -> c.diagnosis() == null).count();
        md.append("\n").append(num(missingDiagnosis)).append(" rows have no diagnosis (`NULL`) and are excluded from the ranking.\n");

        md.append("\n## 7. Cleaning rules applied\n\n");
        md.append("""
                - Exact duplicate rows are removed (first occurrence kept); rows that become identical after normalization are removed too.
                - `claim_id` / `policy_id`: trimmed + uppercased; a `claim_id` reused by rows with different data is flagged `DUPLICATE_CLAIM_ID`.
                - `member_name`: whitespace collapsed, Title Case. `diagnosis`: Sentence case.
                - `claim_type` / `status`: matched to a fixed vocabulary — case/separator differences, aliases (`OP`, `IP`) and typos (edit distance ≤ 2) are corrected; anything else becomes `NULL` and is flagged.
                - `submitted_amount`: thousands separators removed, 2 decimals; negative / zero / non-numeric amounts are flagged (kept for audit, excluded from averages).
                - `currency`: ISO 4217 uppercase (`thb`, `Baht` → `THB`; `vnd`, `dong` → `VND`).
                - `submitted_date`: `YYYY-MM-DD`, `DD/MM/YYYY` (day first) and `Month D, YYYY` → ISO 8601; impossible dates are flagged.
                - Empty, `N/A`, `n/a`, `NULL`, `-` → `NULL` (single null marker).
                """);
        return md.toString();
    }

    public static String consoleSummary(ClaimsCleaner.Result r) {
        return String.format(Locale.US,
                "Rows before: %d | duplicates removed: %d exact + %d near | rows after: %d (valid %d, flagged %d)",
                r.inputRows(), r.exactDuplicatesRemoved(), r.nearDuplicatesRemoved(), r.claims().size(),
                r.validRows(), r.claims().size() - r.validRows());
    }

    private static String countTable(String label, List<CleanClaim> claims, Function<CleanClaim, String> key) {
        Map<String, Long> counts = claims.stream().collect(Collectors.groupingBy(
                c -> key.apply(c) == null ? Normalizers.NULL_MARKER : key.apply(c), TreeMap::new, Collectors.counting()));
        List<List<String>> rows = counts.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .map(e -> List.of(e.getKey(), num(e.getValue()), pct(e.getValue(), claims.size())))
                .collect(Collectors.toCollection(ArrayList::new));
        rows.add(List.of("**Total**", num(claims.size()), "100.0%"));
        return table(List.of(label, "Claims", "Share"), rows);
    }

    private static String averageTable(List<CleanClaim> claims) {
        record Key(String type, String currency) {
        }
        Map<Key, List<BigDecimal>> groups = claims.stream()
                .filter(CleanClaim::hasUsableAmount)
                .filter(c -> c.claimType() != null)
                .collect(Collectors.groupingBy(c -> new Key(c.claimType(), c.currency()),
                        Collectors.mapping(CleanClaim::amount, Collectors.toList())));
        List<Key> keys = new ArrayList<>(groups.keySet());
        keys.sort(Comparator.comparing(Key::type).thenComparing(Key::currency));

        List<List<String>> rows = new ArrayList<>();
        for (Key k : keys) {
            List<BigDecimal> amounts = groups.get(k);
            BigDecimal total = amounts.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal avg = total.divide(BigDecimal.valueOf(amounts.size()), 2, RoundingMode.HALF_UP);
            rows.add(List.of(k.type(), k.currency(), num(amounts.size()), money(avg), money(total)));
        }
        return table(List.of("Claim type", "Currency", "Claims", "Average amount", "Total amount"), rows);
    }

    private static String table(List<String> header, List<List<String>> rows) {
        StringBuilder sb = new StringBuilder();
        sb.append("| ").append(String.join(" | ", header)).append(" |\n");
        sb.append("|").append("---|".repeat(header.size())).append("\n");
        for (List<String> row : rows) {
            sb.append("| ").append(String.join(" | ", row)).append(" |\n");
        }
        return sb.toString();
    }

    private static String num(long n) {
        return String.format(Locale.US, "%,d", n);
    }

    private static String money(BigDecimal v) {
        return String.format(Locale.US, "%,.2f", v);
    }

    private static String pct(long part, long whole) {
        return whole == 0 ? "0.0%" : String.format(Locale.US, "%.1f%%", 100.0 * part / whole);
    }
}
