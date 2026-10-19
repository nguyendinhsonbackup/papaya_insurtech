package claims;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Cleaning pipeline:
 * <ol>
 *   <li>map columns by header name (column order does not matter)</li>
 *   <li>remove exact duplicate rows (identical raw values)</li>
 *   <li>normalize every column and collect issues</li>
 *   <li>remove near duplicates (rows identical after normalization, e.g. only casing differed)</li>
 *   <li>flag claim_ids still shared by rows with different data</li>
 * </ol>
 * Invalid rows are kept with {@code is_valid=false} + {@code dq_issues} rather than dropped, so nothing is lost silently.
 */
public final class ClaimsCleaner {

    public record Result(int inputRows, int malformedRows, int exactDuplicatesRemoved,
                         int nearDuplicatesRemoved, List<CleanClaim> claims) {

        public long validRows() {
            return claims.stream().filter(CleanClaim::isValid).count();
        }

        public List<List<String>> outputRows() {
            return claims.stream().map(CleanClaim::toOutputRow).toList();
        }
    }

    public Result clean(List<List<String>> table) {
        if (table.isEmpty()) {
            throw new IllegalArgumentException("Input CSV is empty");
        }
        int[] columnIndex = resolveColumns(table.get(0));

        List<CleanClaim> claims = new ArrayList<>();
        Set<List<String>> seenRaw = new HashSet<>();
        Set<List<String>> seenNormalized = new HashSet<>();
        int malformed = 0;
        int exactDuplicates = 0;
        int nearDuplicates = 0;

        for (int i = 1; i < table.size(); i++) {
            List<String> line = table.get(i);
            if (line.size() != table.get(0).size()) {
                malformed++; // thiếu/thừa cột → vẫn xử lý, cột thiếu coi như rỗng
            }
            List<String> raw = new ArrayList<>(columnIndex.length);
            for (int idx : columnIndex) {
                raw.add(idx < line.size() ? line.get(idx) : "");
            }

            if (!seenRaw.add(raw)) {
                exactDuplicates++;
                continue;
            }
            CleanClaim claim = new CleanClaim(i + 1, raw);
            if (!seenNormalized.add(claim.businessValues())) {
                nearDuplicates++;
                continue;
            }
            claims.add(claim);
        }

        flagConflictingClaimIds(claims);
        return new Result(table.size() - 1, malformed, exactDuplicates, nearDuplicates, claims);
    }

    private static void flagConflictingClaimIds(List<CleanClaim> claims) {
        Map<String, List<CleanClaim>> byId = new HashMap<>();
        for (CleanClaim c : claims) {
            if (c.claimId != null) {
                byId.computeIfAbsent(c.claimId, k -> new ArrayList<>()).add(c);
            }
        }
        byId.values().stream()
                .filter(group -> group.size() > 1)
                .flatMap(List::stream)
                .forEach(c -> c.issues.add(Issue.DUPLICATE_CLAIM_ID));
    }

    /** Returns, for each expected column, its index in the input header. */
    private static int[] resolveColumns(List<String> header) {
        Map<String, Integer> positions = new LinkedHashMap<>();
        for (int i = 0; i < header.size(); i++) {
            positions.put(header.get(i).strip().toLowerCase(Locale.ROOT), i);
        }
        List<String> expected = ClaimsGenerator.HEADER;
        int[] index = new int[expected.size()];
        for (int i = 0; i < expected.size(); i++) {
            Integer pos = positions.get(expected.get(i));
            if (pos == null) {
                throw new IllegalArgumentException("Missing required column: " + expected.get(i));
            }
            index[i] = pos;
        }
        return index;
    }
}
