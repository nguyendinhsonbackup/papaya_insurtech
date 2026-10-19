package claims;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Maps a free-text value onto a closed set of canonical values.
 * Order of checks: exact → alias → same letters ignoring case/separators → fuzzy (Damerau-Levenshtein).
 */
public final class Vocabulary {

    public enum Kind { EXACT, FORMAT, CORRECTED, UNKNOWN, MISSING }

    public record Match(String value, Kind kind) {
    }

    private final List<String> canonical;
    private final Map<String, String> aliases = new HashMap<>();
    private final Map<String, String> byKey = new HashMap<>();
    private final int maxDistance;

    /**
     * @param canonical   allowed output values
     * @param aliases     raw (upper-cased) or key → canonical value, e.g. "OP" → "OUTPATIENT"
     * @param maxDistance max edit distance for typo correction (0 = disabled)
     */
    public Vocabulary(List<String> canonical, Map<String, String> aliases, int maxDistance) {
        this.canonical = List.copyOf(canonical);
        this.maxDistance = maxDistance;
        aliases.forEach((k, v) -> this.aliases.put(k.toUpperCase(Locale.ROOT), v));
        canonical.forEach(c -> byKey.put(key(c), c));
    }

    public List<String> values() {
        return canonical;
    }

    public Match match(String raw) {
        if (Normalizers.isNullLike(raw)) {
            return new Match(null, Kind.MISSING);
        }
        if (canonical.contains(raw)) {
            return new Match(raw, Kind.EXACT);
        }
        String upper = raw.strip().toUpperCase(Locale.ROOT);
        String key = key(upper);

        String alias = aliases.containsKey(upper) ? aliases.get(upper) : aliases.get(key);
        if (alias != null) {
            return new Match(alias, Kind.CORRECTED);
        }
        String sameLetters = byKey.get(key);
        if (sameLetters != null) {
            return new Match(sameLetters, Kind.FORMAT);
        }
        String fuzzy = closest(key);
        return fuzzy != null ? new Match(fuzzy, Kind.CORRECTED) : new Match(null, Kind.UNKNOWN);
    }

    /** Closest canonical value within maxDistance; null if none or if two candidates tie (ambiguous). */
    private String closest(String key) {
        if (maxDistance == 0 || key.length() < 4) {
            return null; // chuỗi quá ngắn dễ sửa nhầm → không đoán
        }
        String best = null;
        int bestDistance = Integer.MAX_VALUE;
        boolean tie = false;
        for (Map.Entry<String, String> e : byKey.entrySet()) {
            int d = distance(key, e.getKey());
            if (d < bestDistance) {
                best = e.getValue();
                bestDistance = d;
                tie = false;
            } else if (d == bestDistance) {
                tie = true;
            }
        }
        return bestDistance <= maxDistance && !tie ? best : null;
    }

    /** Uppercase letters/digits only: "Out-Patient" → "OUTPATIENT", "In Review" → "INREVIEW". */
    static String key(String s) {
        StringBuilder sb = new StringBuilder();
        for (char c : s.toUpperCase(Locale.ROOT).toCharArray()) {
            if (Character.isLetterOrDigit(c)) {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    /** Optimal string alignment distance (Levenshtein + adjacent transposition = 1). */
    static int distance(String a, String b) {
        int[][] d = new int[a.length() + 1][b.length() + 1];
        for (int i = 0; i <= a.length(); i++) {
            d[i][0] = i;
        }
        for (int j = 0; j <= b.length(); j++) {
            d[0][j] = j;
        }
        for (int i = 1; i <= a.length(); i++) {
            for (int j = 1; j <= b.length(); j++) {
                int cost = a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1;
                d[i][j] = Math.min(Math.min(d[i - 1][j] + 1, d[i][j - 1] + 1), d[i - 1][j - 1] + cost);
                if (i > 1 && j > 1 && a.charAt(i - 1) == b.charAt(j - 2) && a.charAt(i - 2) == b.charAt(j - 1)) {
                    d[i][j] = Math.min(d[i][j], d[i - 2][j - 2] + 1);
                }
            }
        }
        return d[a.length()][b.length()];
    }
}
