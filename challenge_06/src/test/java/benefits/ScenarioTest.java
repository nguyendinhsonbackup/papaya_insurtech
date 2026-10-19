package benefits;

import static benefits.TestRunner.assertEquals;
import static benefits.TestRunner.assertTrue;
import static benefits.TestRunner.log;

import benefits.engine.BenefitBalance;
import benefits.engine.BenefitsCalculator;
import benefits.engine.CalculationResult;
import benefits.engine.Decision;
import benefits.engine.ExpenseResult;
import benefits.engine.ReasonCode;
import benefits.io.ExpenseReader;
import benefits.io.PolicyReader;
import benefits.io.ResultJson;
import benefits.json.Json;
import benefits.json.JsonObject;
import benefits.model.Money;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Predicate;

/** The 20 sample expenses (data/expenses.json) against data/policy.json, checked against hand-calculated results. */
final class ScenarioTest {

    private static CalculationResult run() throws Exception {
        return BenefitsCalculator.calculate(PolicyReader.read(Path.of("data/policy.json")),
                ExpenseReader.read(Path.of("data/expenses.json")));
    }

    private static JsonObject expected() throws Exception {
        return JsonObject.of(Json.parse(Files.readString(Path.of("data/expected_results.json"), StandardCharsets.UTF_8)),
                "expected");
    }

    @TestCase(id = "E2E-01", title = "All 20 expenses match the hand-calculated expected results",
            steps = "Calculate data/expenses.json under data/policy.json; compare each result, in processing order,\n"
                    + "with data/expected_results.json",
            expected = "20/20 match on: expense order, decision, covered, copay, member pays, remaining annual limit,\n"
                    + "remaining visit limit, reason codes")
    static void testMatchesExpectedResults() throws Exception {
        CalculationResult r = run();
        List<JsonObject> expected = expected().objectList("results", true);
        assertEquals(20, r.results().size());
        assertEquals(expected.size(), r.results().size());
        log("%-3s %-8s %-17s %8s %6s %7s %8s %6s  %s", "#", "expense", "decision", "covered", "copay", "member",
                "annual", "visits", "check");
        List<String> mismatches = new ArrayList<>();
        for (int i = 0; i < expected.size(); i++) {
            JsonObject e = expected.get(i);
            ExpenseResult a = r.results().get(i);
            List<String> diff = new ArrayList<>();
            compare(diff, "expense_id", e.requiredString("expense_id"), a.expenseId());
            compare(diff, "decision", e.requiredString("decision"), a.decision().name());
            compareMoney(diff, "covered_amount", e.requiredDecimal("covered_amount"), a.coveredAmount());
            compareMoney(diff, "copay_amount", e.requiredDecimal("copay_amount"), a.copayAmount());
            compareMoney(diff, "member_pays", e.requiredDecimal("member_pays"), a.memberPays());
            compareMoney(diff, "remaining_annual_limit", e.requiredDecimal("remaining_annual_limit"),
                    a.remainingAnnualLimit());
            compare(diff, "remaining_visit_limit", e.optionalInt("remaining_visit_limit"), a.remainingVisitLimit());
            compare(diff, "reason_codes", e.stringList("reason_codes"),
                    a.reasonCodes().stream().map(ReasonCode::name).toList());
            log("%-3d %-8s %-17s %8s %6s %7s %8s %6s  %s", i + 1, a.expenseId(), a.decision(),
                    Money.format(a.coveredAmount()), Money.format(a.copayAmount()), Money.format(a.memberPays()),
                    Money.format(a.remainingAnnualLimit()), a.remainingVisitLimit() == null ? "-" : a.remainingVisitLimit(),
                    diff.isEmpty() ? "OK" : "MISMATCH " + diff);
            diff.forEach(d -> mismatches.add(a.expenseId() + " " + d));
        }
        log("matched: %d / %d", expected.size() - mismatches.stream().map(m -> m.split(" ")[0]).distinct().count(),
                expected.size());
        assertTrue(mismatches.isEmpty(), mismatches.size() + " mismatch(es): " + mismatches);
    }

    @TestCase(id = "E2E-02", title = "Remaining balance per benefit matches the expected summary",
            steps = "Same run as E2E-01; compare totals and the balance of each benefit with expected_results.json",
            expected = "Submitted 136400, covered 100000, member pays 36400, policy remaining 0;\n"
                    + "OUTPATIENT 6280 used / 13720 left, INPATIENT 70000 / 0, DENTAL 4000 / 0, MATERNITY 19720 / 10280;\n"
                    + "available now 0 everywhere (policy limit exhausted)")
    static void testSummaryBalances() throws Exception {
        CalculationResult r = run();
        JsonObject s = expected().optionalObject("summary");
        List<String> diff = new ArrayList<>();
        compareMoney(diff, "total_submitted", s.requiredDecimal("total_submitted"), r.total(ExpenseResult::submittedAmount));
        compareMoney(diff, "total_covered", s.requiredDecimal("total_covered"), r.total(ExpenseResult::coveredAmount));
        compareMoney(diff, "total_member_pays", s.requiredDecimal("total_member_pays"), r.total(ExpenseResult::memberPays));
        compareMoney(diff, "policy_remaining", s.requiredDecimal("policy_annual_limit_remaining"), r.policyRemaining());
        log("totals : submitted %s, covered %s, member pays %s, policy remaining %s",
                Money.format(r.total(ExpenseResult::submittedAmount)), Money.format(r.total(ExpenseResult::coveredAmount)),
                Money.format(r.total(ExpenseResult::memberPays)), Money.format(r.policyRemaining()));
        JsonObject benefits = s.optionalObject("benefits");
        log("%-11s %8s %8s %10s %13s %8s", "benefit", "used", "left", "available", "deductible", "visits");
        for (BenefitBalance b : r.balances()) {
            JsonObject e = benefits.optionalObject(b.benefitType());
            compareMoney(diff, b.benefitType() + ".used", e.requiredDecimal("used"), b.used());
            compareMoney(diff, b.benefitType() + ".remaining", e.requiredDecimal("remaining"), b.remaining());
            compareMoney(diff, b.benefitType() + ".available_now", e.requiredDecimal("available_now"), b.availableNow());
            compare(diff, b.benefitType() + ".visits_used", e.optionalInt("visits_used"), b.visitsUsed());
            if (e.has("deductible_met")) {
                compareMoney(diff, b.benefitType() + ".deductible_met", e.requiredDecimal("deductible_met"), b.deductibleMet());
            }
            log("%-11s %8s %8s %10s %13s %8s", b.benefitType(), Money.format(b.used()), Money.format(b.remaining()),
                    Money.format(b.availableNow()), Money.format(b.deductibleMet()) + "/" + Money.format(b.deductible()),
                    b.visitsUsed() + (b.maxVisits() == null ? "" : "/" + b.maxVisits()));
        }
        diff.forEach(d -> log("MISMATCH %s", d));
        assertTrue(diff.isEmpty(), diff.toString());
    }

    @TestCase(id = "E2E-03", title = "The 20 expenses exercise every required rule, with a reason for each decision",
            steps = "Same run; classify each result; check amounts add up and every reason is specific",
            expected = "At least one of: covered in full, partial (copay), denied (waiting period), denied (exclusion),\n"
                    + "denied (limit exhausted), partial (remaining limit < expense). submitted = covered + member pays;\n"
                    + "member pays = deductible + copay + not covered; non-empty reason everywhere")
    static void testScenarioCoverageAndInvariants() throws Exception {
        CalculationResult r = run();
        Map<String, Predicate<ExpenseResult>> categories = new LinkedHashMap<>();
        categories.put("normal covered (in full)", x -> x.decision() == Decision.COVERED);
        categories.put("partially covered (copay)", x -> x.decision() == Decision.PARTIALLY_COVERED
                && (x.reasonCodes().contains(ReasonCode.COPAY_PERCENTAGE) || x.reasonCodes().contains(ReasonCode.COPAY_FIXED)));
        categories.put("denied (waiting period)", x -> x.reasonCodes().contains(ReasonCode.WAITING_PERIOD));
        categories.put("denied (exclusion)", x -> x.reasonCodes().contains(ReasonCode.EXCLUSION));
        categories.put("denied (limit exhausted)", x -> x.reasonCodes().contains(ReasonCode.LIMIT_EXHAUSTED));
        categories.put("partial (remaining limit < expense)", x -> x.decision() == Decision.PARTIALLY_COVERED
                && x.reasonCodes().stream().anyMatch(Set.of(ReasonCode.SUB_BENEFIT_ANNUAL_LIMIT,
                ReasonCode.BENEFIT_ANNUAL_LIMIT, ReasonCode.POLICY_ANNUAL_LIMIT)::contains));
        categories.put("partial (per-visit sub-limit)", x -> x.reasonCodes().contains(ReasonCode.PER_VISIT_LIMIT));
        categories.put("deductible applied", x -> x.reasonCodes().contains(ReasonCode.DEDUCTIBLE));
        categories.put("denied (visit limit)", x -> x.reasonCodes().contains(ReasonCode.VISIT_LIMIT_REACHED));
        List<String> missing = new ArrayList<>();
        categories.forEach((name, test) -> {
            List<String> ids = r.results().stream().filter(test).map(ExpenseResult::expenseId).toList();
            log("%-36s : %s", name, ids);
            if (ids.isEmpty()) {
                missing.add(name);
            }
        });
        List<String> broken = new ArrayList<>();
        for (ExpenseResult x : r.results()) {
            if (x.submittedAmount().compareTo(x.coveredAmount().add(x.memberPays())) != 0
                    || x.memberPays().compareTo(x.deductibleAmount().add(x.copayAmount()).add(x.notCoveredAmount())) != 0) {
                broken.add(x.expenseId() + " amounts do not add up");
            }
            if (x.reason() == null || x.reason().isBlank()) {
                broken.add(x.expenseId() + " has no reason");
            }
            if (x.decision() == Decision.DENIED && !(x.reason().startsWith("Denied:") || x.reason().contains("Nothing payable"))) {
                broken.add(x.expenseId() + " denial without explanation");
            }
        }
        log("decisions: COVERED %d, PARTIALLY_COVERED %d, DENIED %d", r.count(Decision.COVERED),
                r.count(Decision.PARTIALLY_COVERED), r.count(Decision.DENIED));
        log("invariant checks: %d expenses, %d problem(s)", r.results().size(), broken.size());
        assertTrue(missing.isEmpty(), "scenario not exercised: " + missing);
        assertTrue(broken.isEmpty(), broken.toString());
    }

    @TestCase(id = "E2E-04", title = "EXP-001 output is identical to the example in the challenge",
            steps = "Serialize the EXP-001 result to JSON and keep the 9 fields of the challenge example",
            expected = "{expense_id EXP-001, submitted 2500, covered 2000, copay 500, member_pays 500, PARTIALLY_COVERED,\n"
                    + "reason '20% copay applied. Covered: 2000 THB. Member copay: 500 THB.', remaining annual 98000, visits 29}")
    static void testExampleOutputFormat() throws Exception {
        String example = """
                {
                  "expense_id": "EXP-001",
                  "submitted_amount": 2500,
                  "covered_amount": 2000,
                  "copay_amount": 500,
                  "member_pays": 500,
                  "decision": "PARTIALLY_COVERED",
                  "reason": "20% copay applied. Covered: 2000 THB. Member copay: 500 THB.",
                  "remaining_annual_limit": 98000,
                  "remaining_visit_limit": 29
                }""";
        Map<String, Object> full = ResultJson.result(run().result("EXP-001").orElseThrow());
        Map<String, Object> firstNine = new LinkedHashMap<>();
        full.entrySet().stream().limit(9).forEach(e -> firstNine.put(e.getKey(), e.getValue()));
        String actual = Json.write(firstNine);
        log("actual (challenge fields):");
        actual.lines().forEach(l -> log("  %s", l));
        log("extra fields: %s", full.keySet().stream().skip(9).toList());
        assertEquals(Json.write(Json.parse(example)), actual);
    }

    private static void compare(List<String> diff, String field, Object expected, Object actual) {
        if (!Objects.equals(expected, actual)) {
            diff.add(field + " expected " + expected + " but was " + actual);
        }
    }

    private static void compareMoney(List<String> diff, String field, BigDecimal expected, BigDecimal actual) {
        if (expected.compareTo(actual) != 0) {
            diff.add(field + " expected " + Money.format(expected) + " but was " + Money.format(actual));
        }
    }
}
