package claims;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * CLI entry point.
 * <pre>
 *   generate [--rows 500] [--seed 42] [--dirty data/claims_dirty.csv]
 *   clean    [--dirty data/claims_dirty.csv] [--out-dir output]
 *   all      (generate + clean, default)
 * </pre>
 */
public final class Main {

    private static final String DEFAULT_DIRTY = "data/claims_dirty.csv";
    private static final String DEFAULT_OUT_DIR = "output";
    private static final String CLEAN_FILE = "claims_clean.csv";
    private static final String REPORT_FILE = "data_quality_report.md";

    public static void main(String[] args) throws IOException {
        String command = args.length > 0 && !args[0].startsWith("--") ? args[0] : "all";
        Map<String, String> options = parseOptions(args);
        Path dirty = Path.of(options.getOrDefault("dirty", DEFAULT_DIRTY));
        Path outDir = Path.of(options.getOrDefault("out-dir", DEFAULT_OUT_DIR));

        switch (command) {
            case "generate" -> generate(dirty, options);
            case "clean" -> clean(dirty, outDir);
            case "all" -> {
                generate(dirty, options);
                clean(dirty, outDir);
            }
            default -> {
                System.err.println("Unknown command: " + command + " (expected: generate | clean | all)");
                System.exit(2);
            }
        }
    }

    private static void generate(Path dirty, Map<String, String> options) throws IOException {
        int rows = Integer.parseInt(options.getOrDefault("rows", "500"));
        long seed = Long.parseLong(options.getOrDefault("seed", "42"));
        ClaimsGenerator.Generated g = new ClaimsGenerator(seed).generate(rows);
        CsvIO.write(dirty, ClaimsGenerator.HEADER, g.rows());
        System.out.printf("[generate] %d rows (%d with injected issues, %.1f%%) -> %s%n",
                g.rows().size(), g.dirtyRowCount(), 100.0 * g.dirtyRowCount() / g.rows().size(), dirty);
    }

    private static void clean(Path dirty, Path outDir) throws IOException {
        List<List<String>> table = CsvIO.read(dirty);
        ClaimsCleaner.Result result = new ClaimsCleaner().clean(table);

        Path cleanCsv = outDir.resolve(CLEAN_FILE);
        Path report = outDir.resolve(REPORT_FILE);
        CsvIO.write(cleanCsv, CleanClaim.OUTPUT_HEADER, result.outputRows());
        Files.writeString(report, ClaimsReport.toMarkdown(result, slash(dirty), slash(cleanCsv)), StandardCharsets.UTF_8);

        System.out.println("[clean] " + ClaimsReport.consoleSummary(result));
        System.out.println("[clean] clean CSV -> " + cleanCsv);
        System.out.println("[clean] report    -> " + report);
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
