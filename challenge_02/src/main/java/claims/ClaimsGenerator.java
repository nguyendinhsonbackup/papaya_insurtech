package claims;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.IntStream;

/**
 * Generates a reproducible (seeded) dirty claims dataset.
 * <p>
 * Steps: build clean base rows → inject 1–3 issues into ~15% of them → add near duplicates
 * (same claim, different formatting) → add exact duplicates. Overall ~18% of rows end up dirty.
 */
public final class ClaimsGenerator {

    public static final List<String> HEADER = List.of("claim_id", "policy_id", "member_name", "claim_type",
            "diagnosis", "submitted_amount", "currency", "submitted_date", "status");

    public static final double ISSUE_ROW_RATE = 0.15;
    public static final double EXACT_DUPLICATE_RATE = 0.024;
    public static final double NEAR_DUPLICATE_RATE = 0.016;

    public static final String EXACT_DUPLICATE = "EXACT_DUPLICATE";
    public static final String NEAR_DUPLICATE = "NEAR_DUPLICATE";

    private static final int ID = 0, POLICY = 1, NAME = 2, TYPE = 3, DIAGNOSIS = 4, AMOUNT = 5, CURRENCY = 6,
            DATE = 7, STATUS = 8;

    /** rows = data rows without header; injected.get(i) = issue labels injected into rows.get(i). */
    public record Generated(List<List<String>> rows, List<Set<String>> injected) {
        public long dirtyRowCount() {
            return injected.stream().filter(s -> !s.isEmpty()).count();
        }
    }

    private static final String[] FIRST_NAMES = {"John", "Mary", "Somchai", "Malee", "Anan", "Siriporn", "Nguyen",
            "Tran", "Le", "Pham", "David", "Sarah", "Kittipong", "Napat", "Linh", "Minh", "Emily", "Wei"};
    private static final String[] LAST_NAMES = {"Doe", "Smith", "Prasert", "Wongsa", "Chaiyaporn", "Van An",
            "Thi Mai", "Hoang", "Srisuk", "Tanaka", "Nguyen", "Brown", "Rattanakul", "Bui", "Lee"};

    private static final Map<String, String[]> DIAGNOSES = Map.of(
            "OUTPATIENT", new String[]{"Flu", "Common cold", "Gastritis", "Migraine", "Allergic rhinitis",
                    "Hypertension", "Flu", "Common cold"},
            "INPATIENT", new String[]{"Dengue fever", "Pneumonia", "Appendicitis", "Fracture", "Acute bronchitis"},
            "DENTAL", new String[]{"Tooth decay", "Gingivitis", "Wisdom tooth extraction"},
            "MATERNITY", new String[]{"Normal delivery", "Caesarean section", "Prenatal checkup"});

    private static final Map<String, int[]> THB_RANGE = Map.of(
            "OUTPATIENT", new int[]{500, 15_000}, "INPATIENT", new int[]{20_000, 300_000},
            "DENTAL", new int[]{1_000, 20_000}, "MATERNITY", new int[]{30_000, 150_000});

    private static final Map<String, String[]> TYPE_VARIANTS = Map.of(
            "OUTPATIENT", new String[]{"outpatient", "Outpateint", "OP", "Out-Patient", "OUTPATEINT"},
            "INPATIENT", new String[]{"inpatient", "Inpatinet", "IP", "In Patient"},
            "DENTAL", new String[]{"dental", "Dentl", "Dental "},
            "MATERNITY", new String[]{"maternity", "Maternty"});

    private static final Map<String, String[]> STATUS_VARIANTS = Map.of(
            "APPROVED", new String[]{"approved", "Approved", "APROVED"},
            "REJECTED", new String[]{"rejected", "REJECTD"},
            "PENDING", new String[]{"pending", "Pendng"},
            "IN_REVIEW", new String[]{"In Review", "in_review", "IN-REVIEW"});

    private static final Map<String, String[]> CURRENCY_VARIANTS = Map.of(
            "THB", new String[]{"thb", "Baht", "baht"},
            "VND", new String[]{"vnd", "Vnd", "dong"});

    private static final DateTimeFormatter DMY = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter LONG = DateTimeFormatter.ofPattern("MMMM d, yyyy", Locale.ENGLISH);

    /** A row being built; keeps the original clean values so injectors can derive variants from them. */
    private static final class Row {
        final List<String> values;
        final Set<String> injected = new LinkedHashSet<>();
        final String id;
        final String type;
        final String currency;
        final String status;
        final long amount;
        final LocalDate date;

        Row(List<String> values, String id, String type, String currency, String status, long amount, LocalDate date) {
            this.values = values;
            this.id = id;
            this.type = type;
            this.currency = currency;
            this.status = status;
            this.amount = amount;
            this.date = date;
        }

        Row copy() {
            Row r = new Row(new ArrayList<>(values), id, type, currency, status, amount, date);
            r.injected.addAll(injected);
            return r;
        }
    }

    /** group: injectors of the same group are mutually exclusive on one row (e.g. negative vs zero amount). */
    private record Injector(String name, String group, Consumer<Row> apply) {
    }

    private final Random rnd;

    public ClaimsGenerator(long seed) {
        this.rnd = new Random(seed);
    }

    public Generated generate(int totalRows) {
        int exactDuplicates = (int) Math.round(totalRows * EXACT_DUPLICATE_RATE);
        int nearDuplicates = (int) Math.round(totalRows * NEAR_DUPLICATE_RATE);
        int baseCount = totalRows - exactDuplicates - nearDuplicates;

        List<Row> base = new ArrayList<>();
        for (int i = 0; i < baseCount; i++) {
            base.add(newCleanRow(i));
        }

        // 1. Cấy 1–3 lỗi vào ~15% dòng gốc
        List<Integer> order = new ArrayList<>(IntStream.range(0, baseCount).boxed().toList());
        Collections.shuffle(order, rnd);
        int issueRows = (int) Math.round(baseCount * ISSUE_ROW_RATE);
        // claim_id trùng chỉ mượn từ các dòng không bao giờ bị cấy lỗi (id của chúng chắc chắn không đổi)
        List<Row> idDonors = order.subList(issueRows + nearDuplicates, baseCount).stream().map(base::get).toList();
        List<Injector> injectors = injectors(idDonors);
        for (int k = 0; k < issueRows; k++) {
            injectIssues(base.get(order.get(k)), injectors);
        }

        List<Row> rows = new ArrayList<>(base);

        // 2. Near duplicate: cùng claim, chỉ khác casing tên + định dạng ngày (lấy từ các dòng còn sạch)
        for (int k = 0; k < nearDuplicates; k++) {
            Row nd = base.get(order.get(issueRows + k)).copy();
            nd.values.set(NAME, mangleCasing(nd.values.get(NAME)));
            nd.values.set(DATE, nd.date.format(rnd.nextBoolean() ? DMY : LONG));
            nd.injected.add(NEAR_DUPLICATE);
            rows.add(rnd.nextInt(rows.size() + 1), nd);
        }

        // 3. Exact duplicate: copy nguyên dòng (có thể là dòng sạch hoặc dòng lỗi)
        for (int k = 0; k < exactDuplicates; k++) {
            Row dup = rows.get(rnd.nextInt(rows.size())).copy();
            dup.injected.add(EXACT_DUPLICATE);
            rows.add(rnd.nextInt(rows.size() + 1), dup);
        }

        return new Generated(
                rows.stream().map(r -> List.copyOf(r.values)).toList(),
                rows.stream().map(r -> Set.copyOf(r.injected)).toList());
    }

    private void injectIssues(Row row, List<Injector> injectors) {
        int count = 1 + (rnd.nextDouble() < 0.30 ? 1 : 0) + (rnd.nextDouble() < 0.10 ? 1 : 0);
        List<Injector> shuffled = new ArrayList<>(injectors);
        Collections.shuffle(shuffled, rnd);
        Set<String> usedGroups = new HashSet<>();
        for (Injector inj : shuffled) {
            if (usedGroups.size() == count) {
                break;
            }
            if (usedGroups.add(inj.group())) {
                inj.apply().accept(row);
                row.injected.add(inj.name());
            }
        }
    }

    private List<Injector> injectors(List<Row> idDonors) {
        return List.of(
                new Injector("MISSING_CLAIM_ID", "id", r -> r.values.set(ID, "")),
                new Injector("DUPLICATE_CLAIM_ID", "id", r -> r.values.set(ID, pick(idDonors).id)),
                new Injector("MISSING_POLICY_ID", "policy", r -> r.values.set(POLICY, pick("", "N/A"))),
                new Injector("NAME_CASING", "name", r -> r.values.set(NAME, mangleCasing(r.values.get(NAME)))),
                new Injector("CLAIM_TYPE_TYPO", "type", r -> r.values.set(TYPE, pick(TYPE_VARIANTS.get(r.type)))),
                new Injector("DIAGNOSIS_NULL", "diagnosis", r -> r.values.set(DIAGNOSIS, pick("", "N/A", "n/a"))),
                new Injector("NEGATIVE_AMOUNT", "amount", r -> r.values.set(AMOUNT, "-" + r.amount)),
                new Injector("ZERO_AMOUNT", "amount", r -> r.values.set(AMOUNT, pick("0", "0.00"))),
                new Injector("AMOUNT_WITH_COMMAS", "amount",
                        r -> r.values.set(AMOUNT, String.format(Locale.US, "%,d", Math.max(r.amount, 1_000)))),
                new Injector("CURRENCY_VARIANT", "currency",
                        r -> r.values.set(CURRENCY, pick(CURRENCY_VARIANTS.get(r.currency)))),
                new Injector("DATE_FORMAT", "date",
                        r -> r.values.set(DATE, r.date.format(rnd.nextBoolean() ? DMY : LONG))),
                new Injector("STATUS_VARIANT", "status",
                        r -> r.values.set(STATUS, pick(STATUS_VARIANTS.get(r.status)))));
    }

    private Row newCleanRow(int index) {
        String id = String.format("CLM-%05d", 100 + index);
        String policy = String.format("POL-%03d", 100 + rnd.nextInt(900));
        String name = pick(FIRST_NAMES) + " " + pick(LAST_NAMES);
        String type = weighted(new String[]{"OUTPATIENT", "INPATIENT", "DENTAL", "MATERNITY"}, new int[]{50, 25, 15, 10});
        String diagnosis = pick(DIAGNOSES.get(type));
        String currency = rnd.nextDouble() < 0.25 ? "VND" : "THB";
        int[] range = THB_RANGE.get(type);
        long amount = (range[0] + rnd.nextInt(range[1] - range[0])) / 10 * 10L;
        if (currency.equals("VND")) {
            amount = amount * 700 / 1_000 * 1_000; // quy đổi xấp xỉ 1 THB ≈ 700 VND
        }
        LocalDate date = LocalDate.of(2024, 1, 1).plusDays(rnd.nextInt(366));
        String status = weighted(new String[]{"APPROVED", "PENDING", "REJECTED", "IN_REVIEW"}, new int[]{45, 20, 15, 20});

        List<String> values = new ArrayList<>(List.of(id, policy, name, type, diagnosis, String.valueOf(amount),
                currency, date.toString(), status));
        return new Row(values, id, type, currency, status, amount, date);
    }

    /** "John Doe" → "JOHN DOE" | "john doe" | "jOHN dOE" | "  John   Doe ". */
    private String mangleCasing(String name) {
        return switch (rnd.nextInt(4)) {
            case 0 -> name.toUpperCase(Locale.ROOT);
            case 1 -> name.toLowerCase(Locale.ROOT);
            case 2 -> swapCase(name);
            default -> "  " + name.replace(" ", "   ") + " ";
        };
    }

    private static String swapCase(String s) {
        StringBuilder sb = new StringBuilder();
        for (char c : s.toCharArray()) {
            sb.append(Character.isUpperCase(c) ? Character.toLowerCase(c) : Character.toUpperCase(c));
        }
        return sb.toString();
    }

    private <T> T pick(List<T> values) {
        return values.get(rnd.nextInt(values.size()));
    }

    private String pick(String... values) {
        return values[rnd.nextInt(values.length)];
    }

    private String weighted(String[] values, int[] weights) {
        int total = 0;
        for (int w : weights) {
            total += w;
        }
        int r = rnd.nextInt(total);
        for (int i = 0; i < values.length; i++) {
            r -= weights[i];
            if (r < 0) {
                return values[i];
            }
        }
        return values[values.length - 1];
    }
}
