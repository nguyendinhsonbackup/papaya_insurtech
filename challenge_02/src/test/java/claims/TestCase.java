package claims;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** Test case metadata exported to the Excel test report (sheet "Testcase"). */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
@interface TestCase {

    /** Stable test case ID, e.g. "NORM-06". Tests of one class run in ID order. */
    String id();

    String title();

    /** Steps / input data. */
    String steps();

    String expected();
}
