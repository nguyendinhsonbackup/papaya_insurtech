package claims.testkit;

import java.time.LocalDateTime;
import java.util.List;

/** Outcome of one test case, as exported to the Excel report. */
public record TestResult(String id, String testClass, String method, String title, String steps, String expected,
                         Status status, LocalDateTime startedAt, double durationMs, int assertions,
                         List<String> evidence, String error) {

    /** PASS = all assertions held; FAIL = an assertion failed; ERROR = unexpected exception. */
    public enum Status { PASS, FAIL, ERROR }
}
