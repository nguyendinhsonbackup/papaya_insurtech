package benefits;

import static benefits.TestRunner.assertEquals;
import static benefits.TestRunner.log;

import benefits.engine.BenefitsCalculator;
import benefits.engine.CalculationResult;
import benefits.engine.ExpenseResult;
import benefits.io.PolicyReader;
import benefits.json.Json;
import benefits.model.Expense;
import benefits.model.Money;
import benefits.model.Policy;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Small test policies written as JSON (so tests also exercise the policy format).
 * <pre>
 * OUTPATIENT  annual 10000, waiting 30 days (cover from 2024-01-31), 20% copay, max 5 visits
 *             Doctor Visit: 3000 per visit | Physiotherapy: 1000 per visit, 1500 per year, max 2 visits
 * INPATIENT   annual 50000, deductible 2000, no copay | Room &amp; Board: 5000 per visit | Surgery: no per-visit limit
 * DENTAL      annual 3000, waiting 90 days (cover from 2024-03-31), fixed copay 200 | Cleaning
 * </pre>
 */
final class Fixtures {

    static final String OUTPATIENT = """
            {"benefit_type": "OUTPATIENT", "annual_limit": 10000, "waiting_period_days": 30,
             "copay": {"type": "PERCENTAGE", "value": 20}, "max_visits": 5,
             "sub_benefits": [{"name": "Doctor Visit", "per_visit_limit": 3000},
                              {"name": "Physiotherapy", "per_visit_limit": 1000, "annual_limit": 1500, "max_visits": 2}]}""";

    static final String INPATIENT = """
            {"benefit_type": "INPATIENT", "annual_limit": 50000, "deductible": 2000,
             "sub_benefits": [{"name": "Room & Board", "per_visit_limit": 5000}, {"name": "Surgery"}]}""";

    /** Inpatient without deductible: a plain "fully covered" benefit. */
    static final String INPATIENT_NO_DEDUCTIBLE = """
            {"benefit_type": "INPATIENT", "annual_limit": 50000,
             "sub_benefits": [{"name": "Room & Board", "per_visit_limit": 5000}]}""";

    static final String DENTAL = """
            {"benefit_type": "DENTAL", "annual_limit": 3000, "waiting_period_days": 90,
             "copay": {"type": "FIXED", "value": 200}, "sub_benefits": [{"name": "Cleaning"}]}""";

    private Fixtures() {
    }

    static Policy policy(int annualLimit, String... benefits) {
        String json = """
                {"policy_id": "POL-TEST", "policy_holder": "Test Member", "currency": "THB",
                 "effective_date": "2024-01-01", "expiry_date": "2024-12-31", "annual_limit": %d,
                 "exclusions": [{"code": "EX-01", "description": "Cosmetic treatment", "keywords": ["cosmetic"]}],
                 "benefits": [%s]}""".formatted(annualLimit, String.join(",", benefits));
        return PolicyReader.fromJson(Json.parse(json));
    }

    static Policy standardPolicy() {
        return policy(100000, OUTPATIENT, INPATIENT, DENTAL);
    }

    static Expense expense(String id, String date, String type, String sub, String amount) {
        return expense(id, date, type, sub, amount, "Flu");
    }

    static Expense expense(String id, String date, String type, String sub, String amount, String diagnosis) {
        return new Expense(id, LocalDate.parse(date), type, sub, new BigDecimal(amount), diagnosis, "Test Hospital");
    }

    /** Runs the calculator and logs every result as evidence. */
    static CalculationResult calc(Policy policy, Expense... expenses) {
        log("policy  : %s, annual limit %s THB", policy.policyId(), Money.format(policy.annualLimit()));
        for (Expense e : expenses) {
            log("input   : %s %s %s/%s %s THB (%s)", e.expenseId(), e.date(), e.benefitType(), e.subBenefit(),
                    Money.format(e.amount()), e.diagnosis());
        }
        CalculationResult result = BenefitsCalculator.calculate(policy, List.of(expenses));
        result.results().forEach(Fixtures::logResult);
        return result;
    }

    static void logResult(ExpenseResult r) {
        log("result %d: %s %-17s covered=%s copay=%s deductible=%s member=%s | annual left=%s, visits left=%s %s",
                r.processingOrder(), r.expenseId(), r.decision(), Money.format(r.coveredAmount()),
                Money.format(r.copayAmount()), Money.format(r.deductibleAmount()), Money.format(r.memberPays()),
                Money.format(r.remainingAnnualLimit()), r.remainingVisitLimit(), r.reasonCodes());
        log("          reason: %s", r.reason());
    }

    /** Compares money with scale 2: assertAmount("2000", x) accepts 2000.00. */
    static void assertAmount(String expected, BigDecimal actual) {
        assertEquals(Money.scale(new BigDecimal(expected)), actual);
    }
}
