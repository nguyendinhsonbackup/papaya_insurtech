package benefits.json;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Minimal JSON reader/writer (RFC 8259) — no dependencies.
 * <ul>
 *   <li>object → {@link LinkedHashMap} (key order kept), array → {@link List}</li>
 *   <li>number → {@link BigDecimal} (exact, no floating point), string, boolean, null</li>
 * </ul>
 */
public final class Json {

    private Json() {
    }

    public static Object parse(String text) {
        Parser p = new Parser(text);
        p.skipWhitespace();
        Object value = p.value();
        p.skipWhitespace();
        if (!p.atEnd()) {
            throw p.error("unexpected content after the JSON value");
        }
        return value;
    }

    /** Pretty-prints with 2-space indentation; arrays of scalars are kept on one line. */
    public static String write(Object value) {
        StringBuilder sb = new StringBuilder();
        write(sb, value, 0);
        return sb.append('\n').toString();
    }

    private static void write(StringBuilder sb, Object v, int indent) {
        if (v == null) {
            sb.append("null");
        } else if (v instanceof String s) {
            quote(sb, s);
        } else if (v instanceof BigDecimal d) {
            sb.append(d.stripTrailingZeros().toPlainString());
        } else if (v instanceof Number || v instanceof Boolean) {
            sb.append(v);
        } else if (v instanceof Enum<?> e) {
            quote(sb, e.name());
        } else if (v instanceof LocalDate d) {
            quote(sb, d.toString());
        } else if (v instanceof Map<?, ?> map) {
            writeObject(sb, map, indent);
        } else if (v instanceof Collection<?> list) {
            writeArray(sb, list, indent);
        } else {
            throw new IllegalArgumentException("Cannot write " + v.getClass().getName() + " as JSON");
        }
    }

    private static void writeObject(StringBuilder sb, Map<?, ?> map, int indent) {
        if (map.isEmpty()) {
            sb.append("{}");
            return;
        }
        sb.append("{\n");
        int i = 0;
        for (Map.Entry<?, ?> e : map.entrySet()) {
            sb.append("  ".repeat(indent + 1));
            quote(sb, String.valueOf(e.getKey()));
            sb.append(": ");
            write(sb, e.getValue(), indent + 1);
            sb.append(++i < map.size() ? ",\n" : "\n");
        }
        sb.append("  ".repeat(indent)).append('}');
    }

    private static void writeArray(StringBuilder sb, Collection<?> list, int indent) {
        boolean scalars = list.stream().noneMatch(x -> x instanceof Map || x instanceof Collection);
        if (list.isEmpty() || scalars) {
            sb.append('[');
            int i = 0;
            for (Object x : list) {
                write(sb, x, indent);
                if (++i < list.size()) {
                    sb.append(", ");
                }
            }
            sb.append(']');
            return;
        }
        sb.append("[\n");
        int i = 0;
        for (Object x : list) {
            sb.append("  ".repeat(indent + 1));
            write(sb, x, indent + 1);
            sb.append(++i < list.size() ? ",\n" : "\n");
        }
        sb.append("  ".repeat(indent)).append(']');
    }

    private static void quote(StringBuilder sb, String s) {
        sb.append('"');
        for (char c : s.toCharArray()) {
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> {
                    if (c < 0x20) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
                }
            }
        }
        sb.append('"');
    }

    /** Recursive-descent parser; errors report line and column. */
    private static final class Parser {
        private final String s;
        private int i;

        Parser(String text) {
            this.s = text.startsWith("﻿") ? text.substring(1) : text;
        }

        boolean atEnd() {
            return i >= s.length();
        }

        void skipWhitespace() {
            while (!atEnd() && (s.charAt(i) == ' ' || s.charAt(i) == '\t' || s.charAt(i) == '\n' || s.charAt(i) == '\r')) {
                i++;
            }
        }

        Object value() {
            if (atEnd()) {
                throw error("unexpected end of input");
            }
            char c = s.charAt(i);
            return switch (c) {
                case '{' -> object();
                case '[' -> array();
                case '"' -> string();
                case 't' -> literal("true", Boolean.TRUE);
                case 'f' -> literal("false", Boolean.FALSE);
                case 'n' -> literal("null", null);
                default -> {
                    if (c == '-' || Character.isDigit(c)) {
                        yield number();
                    }
                    throw error("unexpected character '" + c + "'");
                }
            };
        }

        private Map<String, Object> object() {
            Map<String, Object> map = new LinkedHashMap<>();
            i++; // '{'
            skipWhitespace();
            if (peek('}')) {
                i++;
                return map;
            }
            while (true) {
                skipWhitespace();
                if (!peek('"')) {
                    throw error("expected a quoted object key");
                }
                String key = string();
                skipWhitespace();
                expect(':');
                skipWhitespace();
                if (map.containsKey(key)) {
                    throw error("duplicate key \"" + key + "\"");
                }
                map.put(key, value());
                skipWhitespace();
                if (peek(',')) {
                    i++;
                } else if (peek('}')) {
                    i++;
                    return map;
                } else if (atEnd()) {
                    throw error("unexpected end of input");
                } else {
                    throw error("expected ',' or '}'");
                }
            }
        }

        private List<Object> array() {
            List<Object> list = new ArrayList<>();
            i++; // '['
            skipWhitespace();
            if (peek(']')) {
                i++;
                return list;
            }
            while (true) {
                skipWhitespace();
                list.add(value());
                skipWhitespace();
                if (peek(',')) {
                    i++;
                } else if (peek(']')) {
                    i++;
                    return list;
                } else if (atEnd()) {
                    throw error("unexpected end of input");
                } else {
                    throw error("expected ',' or ']'");
                }
            }
        }

        private String string() {
            StringBuilder sb = new StringBuilder();
            i++; // '"'
            while (true) {
                if (atEnd()) {
                    throw error("unterminated string");
                }
                char c = s.charAt(i++);
                if (c == '"') {
                    return sb.toString();
                }
                if (c < 0x20) {
                    throw error("control character in string");
                }
                if (c != '\\') {
                    sb.append(c);
                    continue;
                }
                if (atEnd()) {
                    throw error("unterminated escape");
                }
                char e = s.charAt(i++);
                switch (e) {
                    case '"', '\\', '/' -> sb.append(e);
                    case 'b' -> sb.append('\b');
                    case 'f' -> sb.append('\f');
                    case 'n' -> sb.append('\n');
                    case 'r' -> sb.append('\r');
                    case 't' -> sb.append('\t');
                    case 'u' -> {
                        if (i + 4 > s.length()) {
                            throw error("invalid \\u escape");
                        }
                        try {
                            sb.append((char) Integer.parseInt(s.substring(i, i + 4), 16));
                        } catch (NumberFormatException ex) {
                            throw error("invalid \\u escape");
                        }
                        i += 4;
                    }
                    default -> throw error("invalid escape \\" + e);
                }
            }
        }

        private BigDecimal number() {
            int start = i;
            if (peek('-')) {
                i++;
            }
            digits();
            if (peek('.')) {
                i++;
                digits();
            }
            if (peek('e') || peek('E')) {
                i++;
                if (peek('+') || peek('-')) {
                    i++;
                }
                digits();
            }
            return new BigDecimal(s.substring(start, i));
        }

        private void digits() {
            int start = i;
            while (!atEnd() && Character.isDigit(s.charAt(i))) {
                i++;
            }
            if (start == i) {
                throw error("expected a digit");
            }
        }

        private Object literal(String word, Object value) {
            if (!s.startsWith(word, i)) {
                throw error("unexpected token");
            }
            i += word.length();
            return value;
        }

        private boolean peek(char c) {
            return !atEnd() && s.charAt(i) == c;
        }

        private void expect(char c) {
            if (!peek(c)) {
                throw error("expected '" + c + "'");
            }
            i++;
        }

        IllegalArgumentException error(String message) {
            int line = 1;
            int column = 1;
            for (int k = 0; k < Math.min(i, s.length()); k++) {
                if (s.charAt(k) == '\n') {
                    line++;
                    column = 1;
                } else {
                    column++;
                }
            }
            return new IllegalArgumentException("Invalid JSON at line " + line + ", column " + column + ": " + message);
        }
    }
}
