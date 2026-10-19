package claims;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** Minimal RFC 4180 CSV reader/writer (quoted fields, escaped quotes, commas/newlines inside quotes). */
public final class CsvIO {

    private CsvIO() {
    }

    public static List<List<String>> read(Path path) throws IOException {
        return parse(Files.readString(path, StandardCharsets.UTF_8));
    }

    public static void write(Path path, List<String> header, List<List<String>> rows) throws IOException {
        if (path.getParent() != null) {
            Files.createDirectories(path.getParent());
        }
        StringBuilder sb = new StringBuilder();
        appendLine(sb, header);
        for (List<String> row : rows) {
            appendLine(sb, row);
        }
        Files.writeString(path, sb.toString(), StandardCharsets.UTF_8);
    }

    static List<List<String>> parse(String text) {
        if (text.startsWith("﻿")) {
            text = text.substring(1); // bỏ BOM nếu file được lưu từ Excel
        }
        List<List<String>> rows = new ArrayList<>();
        List<String> row = new ArrayList<>();
        StringBuilder field = new StringBuilder();
        boolean inQuotes = false;

        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (inQuotes) {
                if (c == '"') {
                    if (i + 1 < text.length() && text.charAt(i + 1) == '"') {
                        field.append('"');
                        i++;
                    } else {
                        inQuotes = false;
                    }
                } else {
                    field.append(c);
                }
                continue;
            }
            switch (c) {
                case '"' -> inQuotes = true;
                case ',' -> {
                    row.add(field.toString());
                    field.setLength(0);
                }
                case '\r' -> { /* bỏ qua, xử lý ở '\n' */ }
                case '\n' -> {
                    row.add(field.toString());
                    field.setLength(0);
                    addIfNotBlank(rows, row);
                    row = new ArrayList<>();
                }
                default -> field.append(c);
            }
        }
        if (field.length() > 0 || !row.isEmpty()) {
            row.add(field.toString());
            addIfNotBlank(rows, row);
        }
        return rows;
    }

    private static void addIfNotBlank(List<List<String>> rows, List<String> row) {
        if (!(row.size() == 1 && row.get(0).isEmpty())) {
            rows.add(row);
        }
    }

    private static void appendLine(StringBuilder sb, List<String> values) {
        for (int i = 0; i < values.size(); i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append(escape(values.get(i)));
        }
        sb.append('\n');
    }

    static String escape(String value) {
        if (value == null) {
            return "";
        }
        boolean needsQuotes = value.contains(",") || value.contains("\"") || value.contains("\n")
                || value.contains("\r") || !value.equals(value.strip());
        return needsQuotes ? "\"" + value.replace("\"", "\"\"") + "\"" : value;
    }
}
