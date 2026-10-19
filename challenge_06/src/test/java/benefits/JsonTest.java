package benefits;

import static benefits.TestRunner.assertEquals;
import static benefits.TestRunner.assertThrows;
import static benefits.TestRunner.assertTrue;
import static benefits.TestRunner.log;

import benefits.json.Json;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class JsonTest {

    @TestCase(id = "JSON-01", title = "Parse objects, arrays, escapes and exact decimal numbers",
            steps = "Parse: {\"name\": \"Room & Board \\\"VIP\\\"\\u00e9\", \"amount\": 1234.50, \"list\": [1, true, null],"
                    + " \"nested\": {\"a\": -0.1}}",
            expected = "Map with key order kept; amount = BigDecimal 1234.50 (no floating point); escapes decoded")
    static void testParsesValues() {
        String input = "{\"name\": \"Room & Board \\\"VIP\\\"\\u00e9\", \"amount\": 1234.50, "
                + "\"list\": [1, true, null], \"nested\": {\"a\": -0.1}}";
        @SuppressWarnings("unchecked")
        Map<String, Object> m = (Map<String, Object>) Json.parse(input);
        log("input  : %s", input);
        log("keys   : %s", m.keySet());
        log("name   : %s", m.get("name"));
        log("amount : %s (%s)", m.get("amount"), m.get("amount").getClass().getSimpleName());
        log("list   : %s", m.get("list"));
        log("nested : %s", m.get("nested"));
        assertEquals(List.of("name", "amount", "list", "nested"), List.copyOf(m.keySet()));
        assertEquals("Room & Board \"VIP\"\u00e9", m.get("name"));
        assertEquals(new BigDecimal("1234.50"), m.get("amount"));
        assertEquals(Arrays.asList(new BigDecimal("1"), Boolean.TRUE, null), m.get("list"));
        assertEquals(new BigDecimal("-0.1"), ((Map<?, ?>) m.get("nested")).get("a"));
    }

    @TestCase(id = "JSON-02", title = "Invalid JSON is rejected with line and column",
            steps = "Parse: (1) {\"a\": 1,\\n \"b\": } (2) {\"a\": 1, \"a\": 2} (3) [1, 2",
            expected = "IllegalArgumentException with 'line 2, column 7', 'duplicate key', 'unexpected end'")
    static void testRejectsInvalidJson() {
        IllegalArgumentException e1 = assertThrows(IllegalArgumentException.class, () -> Json.parse("{\"a\": 1,\n \"b\": }"));
        IllegalArgumentException e2 = assertThrows(IllegalArgumentException.class, () -> Json.parse("{\"a\": 1, \"a\": 2}"));
        IllegalArgumentException e3 = assertThrows(IllegalArgumentException.class, () -> Json.parse("[1, 2"));
        log("(1) %s", e1.getMessage());
        log("(2) %s", e2.getMessage());
        log("(3) %s", e3.getMessage());
        assertTrue(e1.getMessage().contains("line 2, column 7"), e1.getMessage());
        assertTrue(e2.getMessage().contains("duplicate key"), e2.getMessage());
        assertTrue(e3.getMessage().contains("unexpected end"), e3.getMessage());
    }

    @TestCase(id = "JSON-03", title = "Write then parse returns the same data",
            steps = "Write a map with string (quotes, newline), BigDecimal 2000.00, null, nested list; parse the text back",
            expected = "Parsed value equals the original; 2000.00 written as 2000")
    static void testWriteParseRoundTrip() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("reason", "Covered: \"2000\" THB\nok");
        data.put("covered_amount", new BigDecimal("2000.00"));
        data.put("remaining_visit_limit", null);
        data.put("codes", List.of("COPAY_PERCENTAGE", "PER_VISIT_LIMIT"));
        String text = Json.write(data);
        log("written:");
        text.lines().forEach(l -> log("  %s", l));
        Object parsed = Json.parse(text);
        @SuppressWarnings("unchecked")
        Map<String, Object> back = (Map<String, Object>) parsed;
        log("parsed : %s", back);
        assertTrue(text.contains("\"covered_amount\": 2000,"), "2000.00 should be written as 2000");
        assertEquals(data.get("reason"), back.get("reason"));
        assertEquals(new BigDecimal("2000"), back.get("covered_amount"));
        assertEquals(data.get("codes"), back.get("codes"));
        assertTrue(back.containsKey("remaining_visit_limit") && back.get("remaining_visit_limit") == null, "null kept");
    }
}
