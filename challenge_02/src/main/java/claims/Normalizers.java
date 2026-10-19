package claims;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * One normalizer per column. Each returns the clean value (null = missing/invalid)
 * and records what it found into {@code issues}.
 */
public final class Normalizers {

    /** Single null marker used everywhere in the clean CSV. */
    public static final String NULL_MARKER = "NULL";

    private static final Set<String> NULL_TOKENS = Set.of("", "N/A", "NA", "NULL", "NONE", "NIL", "-", "NAN");

    /** "15,000" / "1,234,567.50" — commas only accepted as thousands separators. */
    private static final Pattern THOUSANDS = Pattern.compile("-?\\d{1,3}(,\\d{3})+(\\.\\d+)?");
    private static final Pattern PLAIN_NUMBER = Pattern.compile("-?\\d+(\\.\\d+)?");

    private static final DateTimeFormatter ISO_DATE =
            DateTimeFormatter.ofPattern("uuuu-MM-dd").withResolverStyle(ResolverStyle.STRICT);

    /** Accepted non-ISO formats. Slash dates are day-first (dd/MM/yyyy), per the source spec. */
    private static final List<DateTimeFormatter> OTHER_DATE_FORMATS = List.of(
            DateTimeFormatter.ofPattern("d/M/uuuu").withResolverStyle(ResolverStyle.STRICT),
            textDate("MMMM d, uuuu"),
            textDate("MMM d, uuuu"));

    public static final Vocabulary CLAIM_TYPES = new Vocabulary(
            List.of("OUTPATIENT", "INPATIENT", "DENTAL", "MATERNITY"),
            Map.of("OP", "OUTPATIENT", "OPD", "OUTPATIENT", "IP", "INPATIENT", "IPD", "INPATIENT"),
            2);

    public static final Vocabulary STATUSES = new Vocabulary(
            List.of("APPROVED", "REJECTED", "PENDING", "IN_REVIEW"),
            Map.of(),
            2);

    public static final Vocabulary CURRENCIES = new Vocabulary(
            List.of("THB", "VND"),
            Map.of("BAHT", "THB", "฿", "THB", "DONG", "VND", "VNĐ", "VND", "₫", "VND"),
            0);

    private Normalizers() {
    }

    public static boolean isNullLike(String raw) {
        return raw == null || NULL_TOKENS.contains(raw.strip().toUpperCase(Locale.ROOT));
    }

    /** claim_id / policy_id: trim + uppercase. */
    public static String id(String raw, Set<Issue> issues, Issue missingIssue) {
        if (isNullLike(raw)) {
            issues.add(missingIssue);
            return null;
        }
        return raw.strip().toUpperCase(Locale.ROOT);
    }

    /** "  jOHN   doe " → "John Doe". */
    public static String memberName(String raw, Set<Issue> issues) {
        if (isNullLike(raw)) {
            issues.add(Issue.MISSING_MEMBER_NAME);
            return null;
        }
        String result = titleCase(collapseSpaces(raw).toLowerCase(Locale.ROOT));
        if (!result.equals(raw)) {
            issues.add(Issue.NAME_FORMAT);
        }
        return result;
    }

    /** Empty / N/A / n/a → null; otherwise Sentence case ("ACUTE BRONCHITIS" → "Acute bronchitis"). */
    public static String diagnosis(String raw, Set<Issue> issues) {
        if (isNullLike(raw)) {
            issues.add(Issue.MISSING_DIAGNOSIS);
            return null;
        }
        String lower = collapseSpaces(raw).toLowerCase(Locale.ROOT);
        String result = Character.toUpperCase(lower.charAt(0)) + lower.substring(1);
        if (!result.equals(raw)) {
            issues.add(Issue.DIAGNOSIS_FORMAT);
        }
        return result;
    }

    public static String claimType(String raw, Set<Issue> issues) {
        return fromVocabulary(raw, CLAIM_TYPES, issues,
                Issue.MISSING_CLAIM_TYPE, Issue.CLAIM_TYPE_FORMAT, Issue.CLAIM_TYPE_TYPO, Issue.INVALID_CLAIM_TYPE);
    }

    public static String status(String raw, Set<Issue> issues) {
        return fromVocabulary(raw, STATUSES, issues,
                Issue.MISSING_STATUS, Issue.STATUS_FORMAT, Issue.STATUS_TYPO, Issue.INVALID_STATUS);
    }

    public static String currency(String raw, Set<Issue> issues) {
        return fromVocabulary(raw, CURRENCIES, issues,
                Issue.MISSING_CURRENCY, Issue.CURRENCY_FORMAT, Issue.CURRENCY_ALIAS, Issue.INVALID_CURRENCY);
    }

    /**
     * Parses the amount (scale 2). Negative/zero values are returned as-is but flagged,
     * so the row can be audited instead of silently disappearing.
     */
    public static BigDecimal amount(String raw, Set<Issue> issues) {
        if (isNullLike(raw)) {
            issues.add(Issue.MISSING_AMOUNT);
            return null;
        }
        String s = raw.replace(" ", "").replace(" ", "");
        if (THOUSANDS.matcher(s).matches()) {
            s = s.replace(",", "");
        }
        if (!PLAIN_NUMBER.matcher(s).matches()) {
            issues.add(Issue.INVALID_AMOUNT); // vd: "abc", "1,5" (dấu phẩy thập phân → mơ hồ, không đoán)
            return null;
        }
        if (!s.equals(raw)) {
            issues.add(Issue.AMOUNT_FORMAT);
        }
        BigDecimal value = new BigDecimal(s).setScale(2, RoundingMode.HALF_UP);
        if (value.signum() < 0) {
            issues.add(Issue.NEGATIVE_AMOUNT);
        } else if (value.signum() == 0) {
            issues.add(Issue.ZERO_AMOUNT);
        }
        return value;
    }

    /** Accepts 2024-03-15, 15/03/2024, March 15, 2024 (and Mar 15, 2024) → LocalDate. */
    public static LocalDate date(String raw, Set<Issue> issues) {
        if (isNullLike(raw)) {
            issues.add(Issue.MISSING_DATE);
            return null;
        }
        String s = collapseSpaces(raw);
        LocalDate iso = tryParse(s, ISO_DATE);
        if (iso != null) {
            if (!s.equals(raw)) {
                issues.add(Issue.DATE_FORMAT);
            }
            return iso;
        }
        for (DateTimeFormatter f : OTHER_DATE_FORMATS) {
            LocalDate d = tryParse(s, f);
            if (d != null) {
                issues.add(Issue.DATE_FORMAT);
                return d;
            }
        }
        issues.add(Issue.INVALID_DATE);
        return null;
    }

    private static String fromVocabulary(String raw, Vocabulary vocabulary, Set<Issue> issues,
                                         Issue missing, Issue format, Issue corrected, Issue invalid) {
        Vocabulary.Match m = vocabulary.match(raw);
        switch (m.kind()) {
            case MISSING -> issues.add(missing);
            case FORMAT -> issues.add(format);
            case CORRECTED -> issues.add(corrected);
            case UNKNOWN -> issues.add(invalid);
            case EXACT -> { /* ok */ }
        }
        return m.value();
    }

    private static LocalDate tryParse(String s, DateTimeFormatter f) {
        try {
            return LocalDate.parse(s, f);
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    private static DateTimeFormatter textDate(String pattern) {
        return new DateTimeFormatterBuilder().parseCaseInsensitive().appendPattern(pattern)
                .toFormatter(Locale.ENGLISH).withResolverStyle(ResolverStyle.STRICT);
    }

    private static String collapseSpaces(String s) {
        return s.strip().replaceAll("\\s+", " ");
    }

    /** Uppercase the first letter of each word, including after '-' and '\''. */
    private static String titleCase(String s) {
        StringBuilder sb = new StringBuilder(s.length());
        boolean upperNext = true;
        for (char c : s.toCharArray()) {
            sb.append(upperNext ? Character.toUpperCase(c) : c);
            upperNext = c == ' ' || c == '-' || c == '\'';
        }
        return sb.toString();
    }
}
