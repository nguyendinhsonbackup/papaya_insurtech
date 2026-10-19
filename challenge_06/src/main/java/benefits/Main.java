package benefits;

import benefits.engine.BenefitsCalculator;
import benefits.engine.CalculationResult;
import benefits.engine.ExpenseResult;
import benefits.io.CoverageReport;
import benefits.io.ExpenseReader;
import benefits.io.PolicyReader;
import benefits.io.ResultJson;
import benefits.json.Json;
import benefits.model.Expense;
import benefits.model.Money;
import benefits.model.Policy;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * CLI around the reusable {@link BenefitsCalculator}.
 * <pre>
 *   calculate [--policy data/policy.json] [--expenses data/expenses.json] [--out-dir output]
 * </pre>
 * Writes {@code coverage_results.json} (per-expense results + summary) and {@code coverage_report.md}.
 */
public final class Main {

    private static final String DEFAULT_POLICY = "data/policy.json";
    private static final String DEFAULT_EXPENSES = "data/expenses.json";
    private static final String DEFAULT_OUT_DIR = "output";

    public static void main(String[] args) throws IOException {
        String command = args.length > 0 && !args[0].startsWith("--") ? args[0] : "calculate";
        if (!command.equals("calculate")) {
            System.err.println("Unknown command: " + command + " (expected: calculate)");
            System.exit(2);
        }
        Map<String, String> options = parseOptions(args);
        Path policyFile = Path.of(options.getOrDefault("policy", DEFAULT_POLICY));
        Path expensesFile = Path.of(options.getOrDefault("expenses", DEFAULT_EXPENSES));
        Path outDir = Path.of(options.getOrDefault("out-dir", DEFAULT_OUT_DIR));

        Policy policy;
        List<Expense> expenses;
        try {
            policy = PolicyReader.read(policyFile);
            expenses = ExpenseReader.read(expensesFile);
        } catch (IllegalArgumentException e) {
            System.err.println("Invalid input: " + e.getMessage());
            System.exit(2);
            return;
        }

        CalculationResult result = BenefitsCalculator.calculate(policy, expenses);

        Files.createDirectories(outDir);
        Path json = outDir.resolve("coverage_results.json");
        Path report = outDir.resolve("coverage_report.md");
        Files.writeString(json, Json.write(ResultJson.toJson(result)), StandardCharsets.UTF_8);
        Files.writeString(report, CoverageReport.toMarkdown(result, slash(policyFile), slash(expensesFile)),
                StandardCharsets.UTF_8);

        System.out.println("Policy " + policy.policyId() + " - " + expenses.size()
                + " expenses, processed in chronological order:");
        result.results().forEach(r -> System.out.println("  " + CoverageReport.consoleLine(r)));
        System.out.printf("Covered %s of %s %s submitted | policy annual limit left: %s%n",
                Money.format(result.total(ExpenseResult::coveredAmount)),
                Money.format(result.total(ExpenseResult::submittedAmount)), policy.currency(),
                Money.format(result.policyRemaining()));
        System.out.println("[calculate] results -> " + json);
        System.out.println("[calculate] report  -> " + report);
    }

    private static Map<String, String> parseOptions(String[] args) {
        Map<String, String> options = new HashMap<>();
        for (int i = 0; i < args.length; i++) {
            if (args[i].startsWith("--") && i + 1 < args.length) {
                options.put(args[i].substring(2), args[++i]);
            }
        }
        return options;
    }

    private static String slash(Path p) {
        return p.toString().replace('\\', '/');
    }
}
