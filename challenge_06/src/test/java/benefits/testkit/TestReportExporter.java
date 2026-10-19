package benefits.testkit;

import static benefits.testkit.EvidenceImage.line;

import benefits.testkit.EvidenceImage.Line;
import benefits.testkit.TestResult.Status;
import benefits.testkit.XlsxWriter.Sheet;
import benefits.testkit.XlsxWriter.Style;

import java.awt.Color;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Stream;

/**
 * Exports test results to {@code <dir>/benefits_test_report.xlsx} with 3 sheets
 * (Summary / Testcase / Evidence) and one PNG per test case in {@code <dir>/evidence/}.
 */
public final class TestReportExporter {

    public static final String REPORT_FILE = "benefits_test_report.xlsx";

    private static final String SEPARATOR = "-".repeat(96);
    private static final int MAX_EVIDENCE_LINES = 60;
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final DateTimeFormatter TIME_MS = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS");

    private static final String[] CASE_HEADERS = {"No", "Test ID", "Test Class", "Test Method", "Title",
            "Test Steps / Input", "Expected Result", "Actual Result", "Status", "Duration (ms)", "Executed At",
            "Evidence"};
    private static final double[] CASE_WIDTHS = {5, 10, 18, 46, 34, 50, 50, 40, 9, 12, 21, 16};

    private TestReportExporter() {
    }

    public static Path export(List<TestResult> results, List<String> console, LocalDateTime startedAt,
                              String command, Path dir) throws IOException {
        Path evidenceDir = dir.resolve("evidence");
        resetEvidenceDir(evidenceDir);

        XlsxWriter workbook = new XlsxWriter();
        Sheet summary = workbook.sheet("Summary");
        Sheet cases = workbook.sheet("Testcase");
        Sheet evidence = workbook.sheet("Evidence");

        Map<String, Integer> evidenceRows = writeEvidence(evidence, results, command, evidenceDir);
        writeTestcases(cases, results, evidenceRows);
        EvidenceImage.Rendered consoleShot = EvidenceImage.render("Terminal - benefits.TestRunner",
                consoleLines(console, command));
        Files.write(evidenceDir.resolve("00_console.png"), consoleShot.png());
        writeSummary(summary, results, startedAt, command, consoleShot);

        Path file = dir.resolve(REPORT_FILE);
        workbook.write(file);
        return file;
    }

    // ---- sheet "Evidence": one heading row + one screenshot per test ----

    private static Map<String, Integer> writeEvidence(Sheet sheet, List<TestResult> results, String command,
                                                      Path evidenceDir) throws IOException {
        sheet.width(1, 12).width(11, 10).width(12, 18);
        Map<String, Integer> headingRows = new HashMap<>();
        int row = 1;
        for (int i = 0; i < results.size(); i++) {
            TestResult r = results.get(i);
            EvidenceImage.Rendered shot = EvidenceImage.render(
                    r.id() + " - " + r.testClass() + "." + r.method(), evidenceLines(r, command));
            Files.write(evidenceDir.resolve(r.id() + ".png"), shot.png());

            headingRows.put(r.id(), row);
            sheet.cell(row, 1, r.id(), Style.SECTION)
                    .mergedCell(row, 2, 10, r.testClass() + "." + r.method() + "   |   " + r.title(), Style.SECTION)
                    .cell(row, 11, r.status().name(), statusStyle(r.status()))
                    .cell(row, 12, "Back to Testcase", Style.LINK)
                    .link(row, 12, "'Testcase'!A" + (i + 2))
                    .height(row, 20)
                    .image(row + 1, 1, shot.png(), shot.width(), shot.height());
            row += 1 + XlsxWriter.rowsFor(shot.height()) + 2;
        }
        return headingRows;
    }

    private static List<Line> evidenceLines(TestResult r, String command) {
        List<Line> lines = new ArrayList<>();
        lines.add(line("$ " + command, EvidenceImage.PROMPT));
        lines.add(line("Test ID  : " + r.id(), EvidenceImage.KEY));
        lines.add(line("Test     : " + r.testClass() + "." + r.method(), EvidenceImage.KEY));
        lines.add(line("Title    : " + r.title(), EvidenceImage.KEY));
        lines.add(line("Executed : " + r.startedAt().format(TIME_MS) + "   (" + ms(r.durationMs()) + " ms)",
                EvidenceImage.KEY));
        lines.add(line(SEPARATOR, EvidenceImage.DIM));
        if (r.evidence().isEmpty()) {
            lines.add(line("(no evidence logged)", EvidenceImage.DIM));
        }
        r.evidence().stream().limit(MAX_EVIDENCE_LINES).forEach(e -> lines.add(line(e)));
        if (r.evidence().size() > MAX_EVIDENCE_LINES) {
            lines.add(line("... " + (r.evidence().size() - MAX_EVIDENCE_LINES) + " more line(s)", EvidenceImage.DIM));
        }
        lines.add(line(SEPARATOR, EvidenceImage.DIM));
        Color color = statusColor(r.status());
        if (r.status() == Status.PASS) {
            lines.add(line("Assertions: " + r.assertions() + " passed"));
        } else {
            Stream.of(r.error().split("\n")).forEach(e -> lines.add(line(e, color)));
        }
        lines.add(line("RESULT: " + r.status(), color));
        return lines;
    }

    // ---- sheet "Testcase": one row per test ----

    private static void writeTestcases(Sheet sheet, List<TestResult> results, Map<String, Integer> evidenceRows) {
        for (int c = 0; c < CASE_HEADERS.length; c++) {
            sheet.width(c + 1, CASE_WIDTHS[c]).cell(1, c + 1, CASE_HEADERS[c], Style.HEADER);
        }
        sheet.height(1, 30).freezeTopRow();

        for (int i = 0; i < results.size(); i++) {
            TestResult r = results.get(i);
            int row = i + 2;
            String actual = r.status() == Status.PASS
                    ? "As expected: " + r.assertions() + " assertion(s) passed."
                    : r.error();
            Object[] values = {i + 1, r.id(), r.testClass(), r.method(), r.title(), r.steps(), r.expected(), actual,
                    r.status().name(), round(r.durationMs()), r.startedAt().format(TIME), "View " + r.id()};
            Style[] styles = {Style.CENTER, Style.CENTER, Style.CELL, Style.CELL, Style.CELL, Style.CELL, Style.CELL,
                    Style.CELL, statusStyle(r.status()), Style.DECIMAL, Style.CENTER, Style.LINK};
            int lines = 1;
            for (int c = 0; c < values.length; c++) {
                sheet.cell(row, c + 1, values[c], styles[c]);
                lines = Math.max(lines, wrappedLines(String.valueOf(values[c]), CASE_WIDTHS[c]));
            }
            sheet.link(row, 12, "'Evidence'!A" + evidenceRows.get(r.id()))
                    .height(row, lines * 15.0 + 4);
        }
    }

    // ---- sheet "Summary": run info, totals per class, console screenshot ----

    private static void writeSummary(Sheet sheet, List<TestResult> results, LocalDateTime startedAt, String command,
                                     EvidenceImage.Rendered consoleShot) {
        sheet.width(1, 28);
        for (int c = 2; c <= 7; c++) {
            sheet.width(c, 14);
        }
        sheet.mergedCell(1, 1, 7, "Test Report - AI Challenge 06: Policy Benefits Calculator", Style.TITLE)
                .height(1, 28);

        long total = results.size();
        long passed = count(results, Status.PASS);
        long failed = count(results, Status.FAIL);
        long errors = count(results, Status.ERROR);
        double duration = results.stream().mapToDouble(TestResult::durationMs).sum();
        boolean allPassed = passed == total;

        int row = 3;
        row = info(sheet, row, "Executed at", startedAt.format(TIME), Style.CELL);
        row = info(sheet, row, "Command", command + "   (run via test.sh / test.ps1)", Style.CELL);
        row = info(sheet, row, "Environment", "Java " + System.getProperty("java.version") + " ("
                + System.getProperty("java.vendor") + "), " + System.getProperty("os.name") + " "
                + System.getProperty("os.arch"), Style.CELL);
        row = info(sheet, row, "Total test cases", total, Style.CENTER);
        row = info(sheet, row, "Passed", passed, Style.PASS);
        row = info(sheet, row, "Failed", failed, failed > 0 ? Style.FAIL : Style.CENTER);
        row = info(sheet, row, "Errors", errors, errors > 0 ? Style.ERROR : Style.CENTER);
        row = info(sheet, row, "Pass rate", total == 0 ? 0 : (double) passed / total, Style.PERCENT);
        row = info(sheet, row, "Total duration (ms)", round(duration), Style.DECIMAL);
        row = info(sheet, row, "Overall result", allPassed ? "PASS" : "FAIL", allPassed ? Style.PASS : Style.FAIL);

        row++;
        sheet.mergedCell(row++, 1, 7, "Results by test class", Style.SECTION);
        String[] headers = {"Test Class", "Total", "Passed", "Failed", "Errors", "Pass rate", "Duration (ms)"};
        for (int c = 0; c < headers.length; c++) {
            sheet.cell(row, c + 1, headers[c], Style.HEADER);
        }
        row++;
        Map<String, List<TestResult>> byClass = new LinkedHashMap<>();
        results.forEach(r -> byClass.computeIfAbsent(r.testClass(), k -> new ArrayList<>()).add(r));
        for (Map.Entry<String, List<TestResult>> e : byClass.entrySet()) {
            classRow(sheet, row++, e.getKey(), e.getValue(), Style.CELL);
        }
        classRow(sheet, row++, "Total", results, Style.LABEL);

        row++;
        sheet.mergedCell(row++, 1, 7, "Console output (evidence/00_console.png)", Style.SECTION);
        sheet.image(row, 1, consoleShot.png(), consoleShot.width(), consoleShot.height());
    }

    private static int info(Sheet sheet, int row, String label, Object value, Style style) {
        sheet.cell(row, 1, label, Style.LABEL).mergedCell(row, 2, 7, value, style);
        return row + 1;
    }

    private static void classRow(Sheet sheet, int row, String name, List<TestResult> rs, Style nameStyle) {
        long passed = count(rs, Status.PASS);
        sheet.cell(row, 1, name, nameStyle)
                .cell(row, 2, rs.size(), Style.CENTER)
                .cell(row, 3, passed, Style.CENTER)
                .cell(row, 4, count(rs, Status.FAIL), Style.CENTER)
                .cell(row, 5, count(rs, Status.ERROR), Style.CENTER)
                .cell(row, 6, rs.isEmpty() ? 0 : (double) passed / rs.size(), Style.PERCENT)
                .cell(row, 7, round(rs.stream().mapToDouble(TestResult::durationMs).sum()), Style.DECIMAL);
    }

    private static List<Line> consoleLines(List<String> console, String command) {
        List<Line> lines = new ArrayList<>();
        lines.add(line("$ " + command, EvidenceImage.PROMPT));
        for (String s : console) {
            Color color;
            if (s.startsWith("  PASS")) {
                color = EvidenceImage.GREEN;
            } else if (s.startsWith("  FAIL")) {
                color = EvidenceImage.RED;
            } else if (s.startsWith("  ERROR")) {
                color = EvidenceImage.YELLOW;
            } else if (s.startsWith("Tests:")) {
                color = s.contains(" 0 failed, 0 errors") ? EvidenceImage.GREEN : EvidenceImage.RED;
            } else {
                color = EvidenceImage.KEY;
            }
            lines.add(line(s, color));
        }
        return lines;
    }

    // ---- helpers ----

    private static void resetEvidenceDir(Path dir) throws IOException {
        Files.createDirectories(dir);
        try (Stream<Path> old = Files.list(dir)) {
            for (Path p : old.filter(p -> p.toString().endsWith(".png")).toList()) {
                Files.delete(p); // xóa ảnh của lần chạy trước để không lẫn evidence cũ
            }
        }
    }

    /** Approximate number of lines Excel needs to show the text in a wrapped column of the given width. */
    private static int wrappedLines(String text, double columnWidth) {
        int perLine = Math.max(1, (int) columnWidth - 1);
        int lines = 0;
        for (String part : text.split("\n", -1)) {
            lines += Math.max(1, (int) Math.ceil(part.length() / (double) perLine));
        }
        return lines;
    }

    private static long count(List<TestResult> rs, Status status) {
        return rs.stream().filter(r -> r.status() == status).count();
    }

    private static Style statusStyle(Status s) {
        return switch (s) {
            case PASS -> Style.PASS;
            case FAIL -> Style.FAIL;
            case ERROR -> Style.ERROR;
        };
    }

    private static Color statusColor(Status s) {
        return switch (s) {
            case PASS -> EvidenceImage.GREEN;
            case FAIL -> EvidenceImage.RED;
            case ERROR -> EvidenceImage.YELLOW;
        };
    }

    private static double round(double v) {
        return Math.round(v * 100) / 100.0;
    }

    private static String ms(double v) {
        return String.format(Locale.ROOT, "%.2f", v);
    }
}
