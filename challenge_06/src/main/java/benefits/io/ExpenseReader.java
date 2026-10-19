package benefits.io;

import benefits.json.Json;
import benefits.json.JsonObject;
import benefits.model.Expense;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Reads and validates expenses: a JSON array, or an object with an "expenses" array. */
public final class ExpenseReader {

    private ExpenseReader() {
    }

    public static List<Expense> read(Path path) throws IOException {
        return fromJson(Json.parse(Files.readString(path, StandardCharsets.UTF_8)));
    }

    public static List<Expense> fromJson(Object json) {
        Object array = json instanceof Map<?, ?> m ? m.get("expenses") : json;
        if (!(array instanceof List<?> list)) {
            throw new IllegalArgumentException("expenses: must be a JSON array of expense objects");
        }
        List<Expense> expenses = new ArrayList<>();
        Set<String> ids = new HashSet<>();
        for (int i = 0; i < list.size(); i++) {
            JsonObject e = JsonObject.of(list.get(i), "expenses[" + i + "]");
            String id = e.requiredString("expense_id");
            if (!ids.add(id)) {
                throw e.error("expense_id", "duplicate expense_id " + id);
            }
            BigDecimal amount = e.requiredDecimal("amount");
            if (amount.signum() <= 0) {
                throw e.error("amount", "must be greater than 0");
            }
            expenses.add(new Expense(id, e.requiredDate("date"), e.requiredString("benefit_type"),
                    e.requiredString("sub_benefit"), amount, e.optionalString("diagnosis", ""),
                    e.optionalString("provider", "")));
        }
        return List.copyOf(expenses);
    }
}
