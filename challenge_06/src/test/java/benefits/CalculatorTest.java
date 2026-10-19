package benefits;

import static benefits.Fixtures.assertAmount;
import static benefits.Fixtures.calc;
import static benefits.Fixtures.expense;
import static benefits.Fixtures.policy;
import static benefits.Fixtures.standardPolicy;
import static benefits.TestRunner.assertEquals;
import static benefits.TestRunner.assertTrue;
import static benefits.TestRunner.log;

import benefits.engine.BenefitsCalculator;
import benefits.engine.CalculationResult;
import benefits.engine.Decision;
import benefits.engine.ExpenseResult;
import benefits.engine.ReasonCode;
import benefits.model.Expense;
import benefits.model.Policy;

import java.util.List;

/** Core calculation rules, each on a small policy (see {@link Fixtures}). */
final class CalculatorTest {

    @TestCase(id = "CALC-01", title = "Normal coverage: expense paid in full",
            steps = "Policy with INPATIENT (no deductible, no copay, Room & Board 5000/visit), annual limit 100000.\n"
                    + "Expense: Room & Board 4000 on 2024-02-01",
            expected = "COVERED, covered 4000, member pays 0, remaining annual limit 96000, reason 'Covered in full: 4000 THB.'")
    static void testNormalCoverage() {
        CalculationResult r = calc(policy(100000, Fixtures.INPATIENT_NO_DEDUCTIBLE),
                expense("E1", "2024-02-01", "INPATIENT", "Room & Board", "4000"));
        ExpenseResult x = r.results().get(0);
        assertEquals(Decision.COVERED, x.decision());
        assertAmount("4000", x.coveredAmount());
        assertAmount("0", x.memberPays());
        assertAmount("96000", x.remainingAnnualLimit());
        assertEquals("Covered in full: 4000 THB.", x.reason());
        assertEquals(List.of(), x.reasonCodes());
    }

    @TestCase(id = "CALC-02", title = "Percentage copay (challenge example)",
            steps = "Standard policy (OUTPATIENT 20% copay, max 5 visits).\nExpense: Doctor Visit 2500 on 2024-03-15",
            expected = "PARTIALLY_COVERED, covered 2000, copay 500, member pays 500, annual left 98000, visits left 4,\n"
                    + "reason '20% copay applied. Covered: 2000 THB. Member copay: 500 THB.'")
    static void testPercentageCopay() {
        ExpenseResult x = calc(standardPolicy(),
                expense("E1", "2024-03-15", "OUTPATIENT", "Doctor Visit", "2500")).results().get(0);
        assertEquals(Decision.PARTIALLY_COVERED, x.decision());
        assertAmount("2000", x.coveredAmount());
        assertAmount("500", x.copayAmount());
        assertAmount("500", x.memberPays());
        assertAmount("98000", x.remainingAnnualLimit());
        assertEquals(4, x.remainingVisitLimit());
        assertEquals("20% copay applied. Covered: 2000 THB. Member copay: 500 THB.", x.reason());
    }

    @TestCase(id = "CALC-03", title = "Fixed copay, including an expense smaller than the copay",
            steps = "Standard policy (DENTAL fixed copay 200).\nExpenses: Cleaning 1200 on 2024-04-10, Cleaning 150 on 2024-04-20",
            expected = "1200 -> covered 1000, copay 200.\n150 -> copay limited to 150, covered 0, DENIED ('Nothing payable')")
    static void testFixedCopay() {
        CalculationResult r = calc(standardPolicy(),
                expense("E1", "2024-04-10", "DENTAL", "Cleaning", "1200"),
                expense("E2", "2024-04-20", "DENTAL", "Cleaning", "150"));
        ExpenseResult first = r.results().get(0);
        ExpenseResult second = r.results().get(1);
        assertAmount("1000", first.coveredAmount());
        assertAmount("200", first.copayAmount());
        assertEquals(List.of(ReasonCode.COPAY_FIXED), first.reasonCodes());
        assertAmount("0", second.coveredAmount());
        assertAmount("150", second.copayAmount());
        assertEquals(Decision.DENIED, second.decision());
        assertTrue(second.reason().contains("limited to the 150 THB eligible amount")
                && second.reason().contains("Nothing payable"), second.reason());
    }

    @TestCase(id = "CALC-04", title = "Annual deductible is consumed across several expenses",
            steps = "Standard policy (INPATIENT deductible 2000, no copay).\n"
                    + "Room & Board 1500 (05-01), 4000 (05-02), 3000 (05-03)",
            expected = "1500 -> all to deductible, covered 0 (DENIED);\n4000 -> deductible 500, covered 3500;\n"
                    + "3000 -> deductible already met, COVERED 3000. Deductible met = 2000")
    static void testDeductibleAcrossExpenses() {
        CalculationResult r = calc(standardPolicy(),
                expense("E1", "2024-05-01", "INPATIENT", "Room & Board", "1500"),
                expense("E2", "2024-05-02", "INPATIENT", "Room & Board", "4000"),
                expense("E3", "2024-05-03", "INPATIENT", "Room & Board", "3000"));
        List<ExpenseResult> x = r.results();
        assertAmount("1500", x.get(0).deductibleAmount());
        assertAmount("0", x.get(0).coveredAmount());
        assertEquals(Decision.DENIED, x.get(0).decision());
        assertAmount("500", x.get(1).deductibleAmount());
        assertAmount("3500", x.get(1).coveredAmount());
        assertTrue(x.get(1).reason().contains("now fully met"), x.get(1).reason());
        assertAmount("0", x.get(2).deductibleAmount());
        assertEquals(Decision.COVERED, x.get(2).decision());
        assertAmount("2000", r.balance("INPATIENT").orElseThrow().deductibleMet());
    }

    @TestCase(id = "CALC-05", title = "Per-visit sub-limit caps the payment after copay",
            steps = "Standard policy (Doctor Visit 3000 per visit, 20% copay).\nExpense: Doctor Visit 4500 on 2024-03-15",
            expected = "copay 900 -> payable 3600 -> capped at 3000: covered 3000, not covered 600, member pays 1500,\n"
                    + "codes [COPAY_PERCENTAGE, PER_VISIT_LIMIT]")
    static void testPerVisitLimit() {
        ExpenseResult x = calc(standardPolicy(),
                expense("E1", "2024-03-15", "OUTPATIENT", "Doctor Visit", "4500")).results().get(0);
        assertAmount("3000", x.coveredAmount());
        assertAmount("900", x.copayAmount());
        assertAmount("600", x.notCoveredAmount());
        assertAmount("1500", x.memberPays());
        assertEquals(List.of(ReasonCode.COPAY_PERCENTAGE, ReasonCode.PER_VISIT_LIMIT), x.reasonCodes());
        assertTrue(x.reason().contains("Capped at the Doctor Visit per-visit limit of 3000 THB"), x.reason());
    }

    @TestCase(id = "CALC-06", title = "Waiting period: denied the day before cover starts, covered on the first day",
            steps = "Standard policy (OUTPATIENT 30-day waiting, effective 2024-01-01 -> cover from 2024-01-31).\n"
                    + "Doctor Visit 1000 on 2024-01-30 and on 2024-01-31",
            expected = "2024-01-30 -> DENIED WAITING_PERIOD ('cover starts on 2024-01-31');\n2024-01-31 -> covered 800")
    static void testWaitingPeriodBoundary() {
        CalculationResult r = calc(standardPolicy(),
                expense("E1", "2024-01-30", "OUTPATIENT", "Doctor Visit", "1000"),
                expense("E2", "2024-01-31", "OUTPATIENT", "Doctor Visit", "1000"));
        ExpenseResult before = r.results().get(0);
        assertEquals(Decision.DENIED, before.decision());
        assertEquals(List.of(ReasonCode.WAITING_PERIOD), before.reasonCodes());
        assertTrue(before.reason().contains("cover starts on 2024-01-31"), before.reason());
        assertAmount("800", r.results().get(1).coveredAmount());
    }

    @TestCase(id = "CALC-07", title = "Exclusion: diagnosis matching an exclusion keyword is denied",
            steps = "Standard policy (EX-01 keyword 'cosmetic').\n"
                    + "Doctor Visit 2000 'COSMETIC botox consultation' and Doctor Visit 2000 'Flu' (2024-03-01/02)",
            expected = "Cosmetic -> DENIED EXCLUSION (case-insensitive match, reason names EX-01); Flu -> covered 1600")
    static void testExclusion() {
        CalculationResult r = calc(standardPolicy(),
                expense("E1", "2024-03-01", "OUTPATIENT", "Doctor Visit", "2000", "COSMETIC botox consultation"),
                expense("E2", "2024-03-02", "OUTPATIENT", "Doctor Visit", "2000", "Flu"));
        ExpenseResult excluded = r.results().get(0);
        assertEquals(Decision.DENIED, excluded.decision());
        assertEquals(List.of(ReasonCode.EXCLUSION), excluded.reasonCodes());
        assertTrue(excluded.reason().contains("EX-01"), excluded.reason());
        assertAmount("1600", r.results().get(1).coveredAmount());
    }

    @TestCase(id = "CALC-08", title = "Visit limit: the visit after the cap is denied",
            steps = "Standard policy (Physiotherapy max 2 visits, 20% copay).\nPhysiotherapy 500 on 03-01, 03-08, 03-15",
            expected = "Visits 1-2 covered 400 each (visits left 1, 0); visit 3 DENIED VISIT_LIMIT_REACHED '(2 of 2 visits used'")
    static void testVisitLimit() {
        CalculationResult r = calc(standardPolicy(),
                expense("E1", "2024-03-01", "OUTPATIENT", "Physiotherapy", "500"),
                expense("E2", "2024-03-08", "OUTPATIENT", "Physiotherapy", "500"),
                expense("E3", "2024-03-15", "OUTPATIENT", "Physiotherapy", "500"));
        List<ExpenseResult> x = r.results();
        assertAmount("400", x.get(0).coveredAmount());
        assertEquals(1, x.get(0).remainingVisitLimit());
        assertAmount("400", x.get(1).coveredAmount());
        assertEquals(0, x.get(1).remainingVisitLimit());
        assertEquals(Decision.DENIED, x.get(2).decision());
        assertEquals(List.of(ReasonCode.VISIT_LIMIT_REACHED), x.get(2).reasonCodes());
        assertTrue(x.get(2).reason().contains("(2 of 2 visits used"), x.get(2).reason());
    }

    @TestCase(id = "CALC-09", title = "Several expenses consume the same benefit limit until it is exhausted",
            steps = "Standard policy (DENTAL annual 3000, fixed copay 200).\nCleaning 1500 on 04-01, 04-08, 04-15 and 1000 on 04-22",
            expected = "1300, 1300 covered; 3rd: only 400 left -> covered 400 (BENEFIT_ANNUAL_LIMIT);\n"
                    + "4th: DENIED LIMIT_EXHAUSTED; DENTAL remaining 0")
    static void testBenefitLimitExhaustion() {
        CalculationResult r = calc(standardPolicy(),
                expense("E1", "2024-04-01", "DENTAL", "Cleaning", "1500"),
                expense("E2", "2024-04-08", "DENTAL", "Cleaning", "1500"),
                expense("E3", "2024-04-15", "DENTAL", "Cleaning", "1500"),
                expense("E4", "2024-04-22", "DENTAL", "Cleaning", "1000"));
        List<ExpenseResult> x = r.results();
        assertAmount("1300", x.get(0).coveredAmount());
        assertAmount("1300", x.get(1).coveredAmount());
        assertAmount("400", x.get(2).coveredAmount());
        assertEquals(Decision.PARTIALLY_COVERED, x.get(2).decision());
        assertEquals(List.of(ReasonCode.COPAY_FIXED, ReasonCode.BENEFIT_ANNUAL_LIMIT), x.get(2).reasonCodes());
        assertEquals(Decision.DENIED, x.get(3).decision());
        assertEquals(List.of(ReasonCode.LIMIT_EXHAUSTED), x.get(3).reasonCodes());
        assertAmount("0", x.get(3).remainingBenefitLimit());
        assertAmount("0", r.balance("DENTAL").orElseThrow().remaining());
    }

    @TestCase(id = "CALC-10", title = "Overlapping limits: the policy annual limit binds across benefits",
            steps = "Policy annual limit 5000 with INPATIENT (no deductible) + OUTPATIENT (10000).\n"
                    + "Room & Board 4000 (02-01), Doctor Visit 2500 (03-01), Doctor Visit 500 (03-02)",
            expected = "4000 covered; Doctor Visit: payable 2000 but only 1000 left in policy -> covered 1000 "
                    + "(POLICY_ANNUAL_LIMIT);\n3rd DENIED LIMIT_EXHAUSTED although OUTPATIENT still has 9000 (available now 0)")
    static void testPolicyLimitAcrossBenefits() {
        CalculationResult r = calc(policy(5000, Fixtures.INPATIENT_NO_DEDUCTIBLE, Fixtures.OUTPATIENT),
                expense("E1", "2024-02-01", "INPATIENT", "Room & Board", "4000"),
                expense("E2", "2024-03-01", "OUTPATIENT", "Doctor Visit", "2500"),
                expense("E3", "2024-03-02", "OUTPATIENT", "Doctor Visit", "500"));
        List<ExpenseResult> x = r.results();
        assertAmount("4000", x.get(0).coveredAmount());
        assertAmount("1000", x.get(1).coveredAmount());
        assertEquals(List.of(ReasonCode.COPAY_PERCENTAGE, ReasonCode.POLICY_ANNUAL_LIMIT), x.get(1).reasonCodes());
        assertEquals(List.of(ReasonCode.LIMIT_EXHAUSTED), x.get(2).reasonCodes());
        assertTrue(x.get(2).reason().contains("policy annual limit"), x.get(2).reason());
        assertAmount("9000", r.balance("OUTPATIENT").orElseThrow().remaining());
        assertAmount("0", r.balance("OUTPATIENT").orElseThrow().availableNow());
    }

    @TestCase(id = "CALC-11", title = "Sub-benefit annual limit caps a later visit",
            steps = "Standard policy (Physiotherapy 1000 per visit, 1500 per year, 20% copay).\n"
                    + "Physiotherapy 1250 on 03-01 and 03-08",
            expected = "1st: copay 250 -> covered 1000; 2nd: only 500 left in Physiotherapy annual limit -> covered 500 "
                    + "(SUB_BENEFIT_ANNUAL_LIMIT)")
    static void testSubBenefitAnnualLimit() {
        CalculationResult r = calc(standardPolicy(),
                expense("E1", "2024-03-01", "OUTPATIENT", "Physiotherapy", "1250"),
                expense("E2", "2024-03-08", "OUTPATIENT", "Physiotherapy", "1250"));
        assertAmount("1000", r.results().get(0).coveredAmount());
        ExpenseResult second = r.results().get(1);
        assertAmount("500", second.coveredAmount());
        assertEquals(List.of(ReasonCode.COPAY_PERCENTAGE, ReasonCode.SUB_BENEFIT_ANNUAL_LIMIT), second.reasonCodes());
        assertTrue(second.reason().contains("remaining Physiotherapy annual limit of 500 THB"), second.reason());
    }

    @TestCase(id = "CALC-12", title = "Expenses are processed chronologically whatever the input order",
            steps = "Standard policy (DENTAL annual 3000, copay 200).\nInput order: A 04-20, B 04-05, C 04-12 (1500 each); "
                    + "then the same list reversed",
            expected = "Processing order B, C, A: B and C get 1300, A (latest) only the remaining 400.\n"
                    + "Reversed input gives identical results")
    static void testChronologicalOrder() {
        Expense a = expense("A", "2024-04-20", "DENTAL", "Cleaning", "1500");
        Expense b = expense("B", "2024-04-05", "DENTAL", "Cleaning", "1500");
        Expense c = expense("C", "2024-04-12", "DENTAL", "Cleaning", "1500");
        CalculationResult r = calc(standardPolicy(), a, b, c);
        CalculationResult reversed = BenefitsCalculator.calculate(standardPolicy(), List.of(c, b, a));
        log("reversed input -> same results: %s", r.results().equals(reversed.results()));
        assertEquals(List.of("B", "C", "A"), r.results().stream().map(ExpenseResult::expenseId).toList());
        assertAmount("400", r.result("A").orElseThrow().coveredAmount());
        assertEquals(r.results(), reversed.results());
    }

    @TestCase(id = "CALC-13", title = "Unknown benefit, unknown sub-benefit and out-of-period expenses are denied",
            steps = "Standard policy. Expenses: VISION/Glasses 3000; OUTPATIENT/Acupuncture 800; "
                    + "OUTPATIENT/Doctor Visit 1000 on 2025-01-05",
            expected = "All DENIED: BENEFIT_NOT_COVERED (reason lists covered benefits / sub-benefits), OUTSIDE_POLICY_PERIOD")
    static void testNotCoveredAndOutOfPeriod() {
        CalculationResult r = calc(standardPolicy(),
                expense("E1", "2024-03-01", "VISION", "Glasses", "3000"),
                expense("E2", "2024-03-02", "OUTPATIENT", "Acupuncture", "800"),
                expense("E3", "2025-01-05", "OUTPATIENT", "Doctor Visit", "1000"));
        List<ExpenseResult> x = r.results();
        assertEquals(List.of(ReasonCode.BENEFIT_NOT_COVERED), x.get(0).reasonCodes());
        assertTrue(x.get(0).reason().contains("covered: OUTPATIENT, INPATIENT, DENTAL"), x.get(0).reason());
        assertEquals(null, x.get(0).remainingBenefitLimit());
        assertEquals(List.of(ReasonCode.BENEFIT_NOT_COVERED), x.get(1).reasonCodes());
        assertTrue(x.get(1).reason().contains("covered: Doctor Visit, Physiotherapy"), x.get(1).reason());
        assertEquals(List.of(ReasonCode.OUTSIDE_POLICY_PERIOD), x.get(2).reasonCodes());
        assertTrue(x.stream().allMatch(e -> e.decision() == Decision.DENIED), "all denied");
    }

    @TestCase(id = "CALC-14", title = "Denied expenses consume neither limits nor visits",
            steps = "Standard policy. Doctor Visit 1000: 01-15 (waiting period), 03-01 'Cosmetic' (excluded), 03-05 (normal)",
            expected = "Two denials leave everything untouched; 3rd covered 800 -> OUTPATIENT remaining 9200, "
                    + "visits used 1 (4 left), annual left 99200")
    static void testDeniedDoNotConsume() {
        CalculationResult r = calc(standardPolicy(),
                expense("E1", "2024-01-15", "OUTPATIENT", "Doctor Visit", "1000"),
                expense("E2", "2024-03-01", "OUTPATIENT", "Doctor Visit", "1000", "Cosmetic surgery consult"),
                expense("E3", "2024-03-05", "OUTPATIENT", "Doctor Visit", "1000"));
        ExpenseResult last = r.results().get(2);
        assertAmount("800", last.coveredAmount());
        assertAmount("9200", last.remainingBenefitLimit());
        assertEquals(4, last.remainingVisitLimit());
        assertAmount("99200", last.remainingAnnualLimit());
        assertEquals(1, r.balance("OUTPATIENT").orElseThrow().visitsUsed());
    }

    @TestCase(id = "CALC-15", title = "Calculator is reusable: no state leaks between calls",
            steps = "One BenefitsCalculator instance: calculate(list1), calculate(list1) again, then calculate(list2)",
            expected = "Both list1 runs identical (limits restart from full); list2 unaffected by list1")
    static void testReusableWithoutStateLeak() {
        Policy policy = standardPolicy();
        BenefitsCalculator calculator = new BenefitsCalculator(policy);
        List<Expense> list1 = List.of(expense("E1", "2024-04-01", "DENTAL", "Cleaning", "3200"));
        List<Expense> list2 = List.of(expense("E2", "2024-04-02", "DENTAL", "Cleaning", "1000"));
        CalculationResult run1 = calculator.calculate(list1);
        CalculationResult run2 = calculator.calculate(list1);
        CalculationResult run3 = calculator.calculate(list2);
        run1.results().forEach(Fixtures::logResult);
        run2.results().forEach(Fixtures::logResult);
        run3.results().forEach(Fixtures::logResult);
        assertEquals(run1.results(), run2.results());
        assertAmount("3000", run1.results().get(0).coveredAmount());
        assertAmount("800", run3.results().get(0).coveredAmount());
        assertAmount("2200", run3.results().get(0).remainingBenefitLimit());
    }

    @TestCase(id = "CALC-16", title = "Percentage copay is rounded HALF_UP to 2 decimals; amounts always add up",
            steps = "Standard policy (20% copay). Doctor Visit 1234.57",
            expected = "copay 246.914 -> 246.91; covered 987.66; covered + member pays = 1234.57")
    static void testRoundingAndInvariants() {
        ExpenseResult x = calc(standardPolicy(),
                expense("E1", "2024-03-01", "OUTPATIENT", "Doctor Visit", "1234.57")).results().get(0);
        assertAmount("246.91", x.copayAmount());
        assertAmount("987.66", x.coveredAmount());
        assertEquals(x.submittedAmount(), x.coveredAmount().add(x.memberPays()));
        assertEquals(x.memberPays(), x.deductibleAmount().add(x.copayAmount()).add(x.notCoveredAmount()));
    }
}
