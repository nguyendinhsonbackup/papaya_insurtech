package claims;

import claims.testkit.TestReportExporter;
import claims.testkit.TestResult;
import claims.testkit.TestResult.Status;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * Tiny dependency-free test runner: runs every {@code static void testXxx()} method of the listed classes,
 * then exports an Excel test report with one evidence screenshot per test case.
 * <p>
 * Usage: {@code java -cp build/test-classes claims.TestRunner [reportDir]} (default reportDir = test-report).
 * Exit code 0 = all passed and report written, 1 otherwise.
 */
public final class TestRunner {

    private static final Class<?>[] TEST_CLASSES = {
            CsvIOTest.class, NormalizersTest.class, ClaimsCleanerTest.class, EndToEndTest.class};

    static final String COMMAND = "java -cp build/test-classes claims.TestRunner";
    private static final String DEFAULT_REPORT_DIR = "test-report";

    /** Evidence lines and assertion count of the test currently running (tests run sequentially). */
    private static List<String> evidence = new ArrayList<>();
    private static int assertions;

    public static void main(String[] args) {
        System.setProperty("java.awt.headless", "true");
        Path reportDir = Path.of(args.length > 0 ? args[0] : DEFAULT_REPORT_DIR);
        LocalDateTime startedAt = LocalDateTime.now();
        List<String> console = new ArrayList<>();
        List<TestResult> results = new ArrayList<>();

        for (Class<?> testClass : TEST_CLASSES) {
            print(console, testClass.getSimpleName());
            for (Method m : testMethods(testClass)) {
                TestResult r = run(testClass, m);
                results.add(r);
                print(console, String.format("  %-5s %-7s %s%s", r.status(), r.id(), m.getName(),
                        r.error() == null ? "" : " -> " + r.error()));
            }
        }
        long passed = results.stream().filter(r -> r.status() == Status.PASS).count();
        long failed = results.stream().filter(r -> r.status() == Status.FAIL).count();
        long errors = results.stream().filter(r -> r.status() == Status.ERROR).count();
        print(console, "");
        print(console, String.format("Tests: %d passed, %d failed, %d errors", passed, failed, errors));

        boolean reportWritten = true;
        try {
            Path file = TestReportExporter.export(results, console, startedAt, COMMAND, reportDir);
            System.out.println("[report] Excel    -> " + file);
            System.out.println("[report] evidence -> " + reportDir.resolve("evidence"));
        } catch (IOException | RuntimeException e) {
            reportWritten = false;
            System.out.println("[report] FAILED to write the test report: " + e
                    + " (close the .xlsx if it is open in Excel, then run again)");
        }
        System.exit(passed == results.size() && reportWritten ? 0 : 1);
    }

    private static List<Method> testMethods(Class<?> testClass) {
        return Arrays.stream(testClass.getDeclaredMethods())
                .filter(m -> m.getName().startsWith("test") && Modifier.isStatic(m.getModifiers()))
                .sorted(Comparator.comparing(TestRunner::idOf))
                .toList();
    }

    private static String idOf(Method m) {
        TestCase tc = m.getAnnotation(TestCase.class);
        return tc != null ? tc.id() : m.getName();
    }

    private static TestResult run(Class<?> testClass, Method m) {
        evidence = new ArrayList<>();
        assertions = 0;
        TestCase tc = m.getAnnotation(TestCase.class);
        LocalDateTime startedAt = LocalDateTime.now();
        long start = System.nanoTime();
        Status status = Status.PASS;
        String error = null;
        try {
            m.setAccessible(true);
            m.invoke(null);
        } catch (InvocationTargetException e) {
            // AssertionError = kết quả sai (FAIL); exception khác = lỗi khi chạy (ERROR)
            status = e.getCause() instanceof AssertionError ? Status.FAIL : Status.ERROR;
            error = String.valueOf(e.getCause());
        } catch (ReflectiveOperationException e) {
            status = Status.ERROR;
            error = e.toString();
        }
        double durationMs = (System.nanoTime() - start) / 1_000_000.0;
        return new TestResult(idOf(m), testClass.getSimpleName(), m.getName(),
                tc != null ? tc.title() : m.getName(), tc != null ? tc.steps() : "", tc != null ? tc.expected() : "",
                status, startedAt, durationMs, assertions, List.copyOf(evidence), error);
    }

    private static void print(List<String> console, String line) {
        System.out.println(line);
        console.add(line);
    }

    // ---- evidence ----

    /** Adds one line to the current test's evidence (shown in its screenshot). */
    static void log(String format, Object... args) {
        evidence.add(args.length == 0 ? format : String.format(Locale.ROOT, format, args));
    }

    // ---- assertions ----

    static void assertEquals(Object expected, Object actual) {
        assertions++;
        if (!Objects.equals(expected, actual)) {
            throw new AssertionError("expected <" + expected + "> but was <" + actual + ">");
        }
    }

    static void assertTrue(boolean condition, String message) {
        assertions++;
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    static <T extends Throwable> T assertThrows(Class<T> type, Runnable action) {
        assertions++;
        try {
            action.run();
        } catch (Throwable t) {
            if (type.isInstance(t)) {
                return type.cast(t);
            }
            throw new AssertionError("expected " + type.getSimpleName() + " but got " + t);
        }
        throw new AssertionError("expected " + type.getSimpleName() + " to be thrown");
    }
}
