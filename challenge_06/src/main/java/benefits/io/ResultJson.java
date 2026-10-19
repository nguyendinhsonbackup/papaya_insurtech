package benefits.io;

import benefits.engine.BenefitBalance;
import benefits.engine.BenefitBalance.SubBenefitBalance;
import benefits.engine.CalculationResult;
import benefits.engine.Decision;
import benefits.engine.ExpenseResult;
import benefits.model.Policy;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Converts a {@link CalculationResult} into the output JSON structure (snake_case, as in the challenge). */
public final class ResultJson {

    private ResultJson() {
    }

    public static Map<String, Object> toJson(CalculationResult r) {
        Policy p = r.policy();
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("policy_id", p.policyId());
        root.put("policy_holder", p.policyHolder());
        root.put("currency", p.currency());
        root.put("results", r.results().stream().map(ResultJson::result).toList());
        root.put("summary", summary(r));
        return root;
    }

    /** Challenge fields first, in the challenge's order, then the extra detail fields. */
    public static Map<String, Object> result(ExpenseResult x) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("expense_id", x.expenseId());
        m.put("submitted_amount", x.submittedAmount());
        m.put("covered_amount", x.coveredAmount());
        m.put("copay_amount", x.copayAmount());
        m.put("member_pays", x.memberPays());
        m.put("decision", x.decision());
        m.put("reason", x.reason());
        m.put("remaining_annual_limit", x.remainingAnnualLimit());
        m.put("remaining_visit_limit", x.remainingVisitLimit());
        m.put("processing_order", x.processingOrder());
        m.put("date", x.date());
        m.put("benefit_type", x.benefitType());
        m.put("sub_benefit", x.subBenefit());
        m.put("deductible_amount", x.deductibleAmount());
        m.put("not_covered_amount", x.notCoveredAmount());
        m.put("remaining_benefit_limit", x.remainingBenefitLimit());
        m.put("reason_codes", x.reasonCodes());
        return m;
    }

    private static Map<String, Object> summary(CalculationResult r) {
        Map<String, Object> policy = new LinkedHashMap<>();
        policy.put("annual_limit", r.policy().annualLimit());
        policy.put("used", r.policyUsed());
        policy.put("remaining", r.policyRemaining());

        Map<String, Object> totals = new LinkedHashMap<>();
        totals.put("expenses", r.results().size());
        totals.put("covered", r.count(Decision.COVERED));
        totals.put("partially_covered", r.count(Decision.PARTIALLY_COVERED));
        totals.put("denied", r.count(Decision.DENIED));
        totals.put("submitted_amount", r.total(ExpenseResult::submittedAmount));
        totals.put("covered_amount", r.total(ExpenseResult::coveredAmount));
        totals.put("copay_amount", r.total(ExpenseResult::copayAmount));
        totals.put("deductible_amount", r.total(ExpenseResult::deductibleAmount));
        totals.put("not_covered_amount", r.total(ExpenseResult::notCoveredAmount));
        totals.put("member_pays", r.total(ExpenseResult::memberPays));

        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("policy_annual_limit", policy);
        summary.put("totals", totals);
        summary.put("benefits", r.balances().stream().map(ResultJson::balance).toList());
        return summary;
    }

    private static Map<String, Object> balance(BenefitBalance b) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("benefit_type", b.benefitType());
        m.put("annual_limit", b.annualLimit());
        m.put("used", b.used());
        m.put("remaining", b.remaining());
        m.put("available_now", b.availableNow());
        m.put("deductible", b.deductible());
        m.put("deductible_met", b.deductibleMet());
        m.put("max_visits", b.maxVisits());
        m.put("visits_used", b.visitsUsed());
        m.put("sub_benefits", b.subBenefits().stream().map(ResultJson::subBalance).toList());
        return m;
    }

    private static Map<String, Object> subBalance(SubBenefitBalance s) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("name", s.name());
        m.put("per_visit_limit", s.perVisitLimit());
        m.put("annual_limit", s.annualLimit());
        m.put("used", s.used());
        m.put("remaining", s.remaining());
        m.put("max_visits", s.maxVisits());
        m.put("visits_used", s.visitsUsed());
        return m;
    }

    public static List<Map<String, Object>> results(CalculationResult r) {
        return r.results().stream().map(ResultJson::result).toList();
    }
}
