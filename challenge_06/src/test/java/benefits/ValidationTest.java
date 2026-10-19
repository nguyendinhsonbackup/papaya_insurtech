package benefits;

import static benefits.TestRunner.assertEquals;
import static benefits.TestRunner.assertThrows;
import static benefits.TestRunner.assertTrue;
import static benefits.TestRunner.log;

import benefits.io.ExpenseReader;
import benefits.io.PolicyReader;
import benefits.json.Json;
import benefits.model.Benefit;
import benefits.model.Copay;
import benefits.model.Money;
import benefits.model.Policy;

import java.nio.file.Path;

final class ValidationTest {

    @TestCase(id = "VAL-01", title = "The sample policy file loads with all its rules",
            steps = "Read data/policy.json",
            expected = "4 benefits; OUTPATIENT 20% copay + 30 visits; INPATIENT deductible 2000; DENTAL fixed copay 200;"
                    + " MATERNITY 280-day waiting; 3 exclusions")
    static void testLoadsSamplePolicy() throws Exception {
        Policy p = PolicyReader.read(Path.of("data/policy.json"));
        log("policy     : %s (%s), %s to %s, annual limit %s %s", p.policyId(), p.policyHolder(), p.effectiveDate(),
                p.expiryDate(), Money.format(p.annualLimit()), p.currency());
        for (Benefit b : p.benefits()) {
            log("benefit    : %-10s limit=%-6s deductible=%-5s copay=%-22s waiting=%3d days, max visits=%s, sub=%s",
                    b.type(), Money.format(b.annualLimit()), Money.format(b.deductible()), b.copay().describe("THB"),
                    b.waitingPeriodDays(), b.maxVisits(), b.subBenefitNames());
        }
        p.exclusions().forEach(e -> log("exclusion  : %s %s %s", e.code(), e.description(), e.keywords()));
        assertEquals(4, p.benefits().size());
        assertEquals(Copay.Type.PERCENTAGE, p.benefit("OUTPATIENT").orElseThrow().copay().type());
        assertEquals(30, p.benefit("OUTPATIENT").orElseThrow().maxVisits());
        assertEquals(Money.scale(new java.math.BigDecimal("2000")), p.benefit("INPATIENT").orElseThrow().deductible());
        assertEquals(Copay.Type.FIXED, p.benefit("DENTAL").orElseThrow().copay().type());
        assertEquals(280, p.benefit("MATERNITY").orElseThrow().waitingPeriodDays());
        assertEquals(3, p.exclusions().size());
    }

    @TestCase(id = "VAL-02", title = "An invalid policy is rejected with the exact field path",
            steps = "Load policies with: (1) PERCENTAGE copay 120 (2) expiry before effective date (3) no sub_benefits",
            expected = "(1) policy.benefits[0].copay.value: must be between 0 and 100 ...\n"
                    + "(2) policy.expiry_date: must not be before effective_date\n(3) policy.benefits[0].sub_benefits: is required")
    static void testRejectsInvalidPolicy() {
        String bad1 = """
                {"policy_id": "P", "effective_date": "2024-01-01", "expiry_date": "2024-12-31", "annual_limit": 1000,
                 "benefits": [{"benefit_type": "OUTPATIENT", "annual_limit": 500, "copay": {"type": "PERCENTAGE", "value": 120},
                               "sub_benefits": [{"name": "Doctor Visit"}]}]}""";
        String bad2 = """
                {"policy_id": "P", "effective_date": "2024-12-31", "expiry_date": "2024-01-01", "annual_limit": 1000,
                 "benefits": []}""";
        String bad3 = """
                {"policy_id": "P", "effective_date": "2024-01-01", "expiry_date": "2024-12-31", "annual_limit": 1000,
                 "benefits": [{"benefit_type": "OUTPATIENT", "annual_limit": 500}]}""";
        String m1 = assertThrows(IllegalArgumentException.class, () -> PolicyReader.fromJson(Json.parse(bad1))).getMessage();
        String m2 = assertThrows(IllegalArgumentException.class, () -> PolicyReader.fromJson(Json.parse(bad2))).getMessage();
        String m3 = assertThrows(IllegalArgumentException.class, () -> PolicyReader.fromJson(Json.parse(bad3))).getMessage();
        log("(1) %s", m1);
        log("(2) %s", m2);
        log("(3) %s", m3);
        assertTrue(m1.startsWith("policy.benefits[0].copay.value: must be between 0 and 100"), m1);
        assertTrue(m2.startsWith("policy.expiry_date: must not be before effective_date"), m2);
        assertTrue(m3.startsWith("policy.benefits[0].sub_benefits: is required"), m3);
    }

    @TestCase(id = "VAL-03", title = "Invalid expenses are rejected with the exact field path",
            steps = "Load expenses with: (1) amount -100 (2) date 15/03/2024 (3) duplicate expense_id",
            expected = "(1) expenses[0].amount: must be greater than 0\n(2) expenses[0].date: must be an ISO date ...\n"
                    + "(3) expenses[1].expense_id: duplicate expense_id EXP-001")
    static void testRejectsInvalidExpenses() {
        String base = "{\"expense_id\": \"EXP-001\", \"date\": \"%s\", \"benefit_type\": \"OUTPATIENT\","
                + " \"sub_benefit\": \"Doctor Visit\", \"amount\": %s}";
        String bad1 = "[" + base.formatted("2024-03-15", "-100") + "]";
        String bad2 = "[" + base.formatted("15/03/2024", "100") + "]";
        String bad3 = "[" + base.formatted("2024-03-15", "100") + "," + base.formatted("2024-03-16", "200") + "]";
        String m1 = assertThrows(IllegalArgumentException.class, () -> ExpenseReader.fromJson(Json.parse(bad1))).getMessage();
        String m2 = assertThrows(IllegalArgumentException.class, () -> ExpenseReader.fromJson(Json.parse(bad2))).getMessage();
        String m3 = assertThrows(IllegalArgumentException.class, () -> ExpenseReader.fromJson(Json.parse(bad3))).getMessage();
        log("(1) %s", m1);
        log("(2) %s", m2);
        log("(3) %s", m3);
        assertEquals("expenses[0].amount: must be greater than 0", m1);
        assertTrue(m2.startsWith("expenses[0].date: must be an ISO date"), m2);
        assertEquals("expenses[1].expense_id: duplicate expense_id EXP-001", m3);
    }
}
