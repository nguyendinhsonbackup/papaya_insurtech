package claims;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/** A normalized claim row plus every issue detected while cleaning it. */
public final class CleanClaim {

    public static final List<String> OUTPUT_HEADER;

    static {
        List<String> header = new ArrayList<>();
        header.add("source_row");
        header.addAll(ClaimsGenerator.HEADER);
        header.add("is_valid");
        header.add("dq_issues");
        OUTPUT_HEADER = List.copyOf(header);
    }

    /** Line number in the input file (header = line 1), kept for traceability. */
    final int sourceRow;
    final String claimId;
    final String policyId;
    final String memberName;
    final String claimType;
    final String diagnosis;
    final BigDecimal amount;
    final String currency;
    final LocalDate submittedDate;
    final String status;
    final Set<Issue> issues;

    CleanClaim(int sourceRow, List<String> raw) {
        this.sourceRow = sourceRow;
        Set<Issue> found = EnumSet.noneOf(Issue.class);
        this.claimId = Normalizers.id(raw.get(0), found, Issue.MISSING_CLAIM_ID);
        this.policyId = Normalizers.id(raw.get(1), found, Issue.MISSING_POLICY_ID);
        this.memberName = Normalizers.memberName(raw.get(2), found);
        this.claimType = Normalizers.claimType(raw.get(3), found);
        this.diagnosis = Normalizers.diagnosis(raw.get(4), found);
        this.amount = Normalizers.amount(raw.get(5), found);
        this.currency = Normalizers.currency(raw.get(6), found);
        this.submittedDate = Normalizers.date(raw.get(7), found);
        this.status = Normalizers.status(raw.get(8), found);
        this.issues = found;
    }

    public boolean isValid() {
        return issues.stream().noneMatch(Issue::isBlocking);
    }

    /** Amount usable for statistics: positive and with a known currency. */
    public boolean hasUsableAmount() {
        return amount != null && amount.signum() > 0 && currency != null;
    }

    /** The 9 business columns after normalization — used to detect near duplicates. */
    public List<String> businessValues() {
        return List.of(text(claimId), text(policyId), text(memberName), text(claimType), text(diagnosis),
                amount == null ? Normalizers.NULL_MARKER : amount.toPlainString(), text(currency),
                submittedDate == null ? Normalizers.NULL_MARKER : submittedDate.toString(), text(status));
    }

    public List<String> toOutputRow() {
        List<String> row = new ArrayList<>();
        row.add(String.valueOf(sourceRow));
        row.addAll(businessValues());
        row.add(String.valueOf(isValid()));
        row.add(issues.stream().map(Enum::name).collect(Collectors.joining("|")));
        return row;
    }

    public int sourceRow() {
        return sourceRow;
    }

    public String claimId() {
        return claimId;
    }

    public String claimType() {
        return claimType;
    }

    public String diagnosis() {
        return diagnosis;
    }

    public BigDecimal amount() {
        return amount;
    }

    public String currency() {
        return currency;
    }

    public String status() {
        return status;
    }

    public Set<Issue> issues() {
        return issues;
    }

    private static String text(String value) {
        return value == null ? Normalizers.NULL_MARKER : value;
    }
}
