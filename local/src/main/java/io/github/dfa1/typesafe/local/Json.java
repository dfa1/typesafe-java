package io.github.dfa1.typesafe.local;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Just enough JSON for {@code tokenizer.json}, {@code rl_agent_config.json} and Laya's state
 * serialization, so the library needs no JSON dependency. Objects parse to {@code LinkedHashMap},
 * arrays to {@code List}, numbers to {@code Double} or {@code Long}.
 */
final class Json {

    private final String s;
    private int i;

    private Json(String s) {
        this.s = s;
    }

    static Object parse(String text) {
        Json p = new Json(text);
        Object value = p.value();
        p.space();
        if (p.i != text.length()) {
            throw p.error("trailing content");
        }
        return value;
    }

    /** Python's {@code json.dumps(value, ensure_ascii=False)}: {@code ", "} and {@code ": "} separators. */
    static String write(Object value) {
        return switch (value) {
            case null -> "null";
            case String str -> quote(str);
            case Map<?, ?> m -> {
                List<String> parts = new ArrayList<>();
                m.forEach((k, v) -> parts.add(quote(String.valueOf(k)) + ": " + write(v)));
                yield "{" + String.join(", ", parts) + "}";
            }
            case List<?> l -> "[" + String.join(", ", l.stream().map(Json::write).toList()) + "]";
            case Boolean b -> b ? "true" : "false";
            default -> String.valueOf(value);
        };
    }

    private static String quote(String str) {
        StringBuilder sb = new StringBuilder("\"");
        for (char c : str.toCharArray()) {
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                case '\b' -> sb.append("\\b");
                case '\f' -> sb.append("\\f");
                default -> {
                    if (c < 0x20) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
                }
            }
        }
        return sb.append('"').toString();
    }

    private Object value() {
        space();
        if (i >= s.length()) {
            throw error("unexpected end");
        }
        char c = s.charAt(i);
        return switch (c) {
            case '{' -> object();
            case '[' -> array();
            case '"' -> string();
            case 't' -> literal("true", Boolean.TRUE);
            case 'f' -> literal("false", Boolean.FALSE);
            case 'n' -> literal("null", null);
            default -> number();
        };
    }

    private Map<String, Object> object() {
        Map<String, Object> result = new LinkedHashMap<>();
        i++;
        space();
        if (s.charAt(i) == '}') {
            i++;
            return result;
        }
        while (true) {
            space();
            String key = string();
            space();
            expect(':');
            result.put(key, value());
            space();
            if (s.charAt(i) == ',') {
                i++;
            } else {
                expect('}');
                return result;
            }
        }
    }

    private List<Object> array() {
        List<Object> result = new ArrayList<>();
        i++;
        space();
        if (s.charAt(i) == ']') {
            i++;
            return result;
        }
        while (true) {
            result.add(value());
            space();
            if (s.charAt(i) == ',') {
                i++;
            } else {
                expect(']');
                return result;
            }
        }
    }

    private String string() {
        expect('"');
        StringBuilder sb = new StringBuilder();
        while (true) {
            char c = s.charAt(i++);
            if (c == '"') {
                return sb.toString();
            }
            if (c != '\\') {
                sb.append(c);
                continue;
            }
            char e = s.charAt(i++);
            switch (e) {
                case 'n' -> sb.append('\n');
                case 't' -> sb.append('\t');
                case 'r' -> sb.append('\r');
                case 'b' -> sb.append('\b');
                case 'f' -> sb.append('\f');
                case 'u' -> {
                    sb.append((char) Integer.parseInt(s, i, i + 4, 16)); // surrogate pairs arrive as two escapes
                    i += 4;
                }
                default -> sb.append(e); // \" \\ \/
            }
        }
    }

    private Object number() {
        int start = i;
        while (i < s.length() && "+-0123456789.eE".indexOf(s.charAt(i)) >= 0) {
            i++;
        }
        String n = s.substring(start, i);
        if (n.isEmpty()) {
            throw error("unexpected character");
        }
        return n.contains(".") || n.contains("e") || n.contains("E") ? (Object) Double.parseDouble(n) : (Object) Long.parseLong(n);
    }

    private Object literal(String word, Object value) {
        if (!s.startsWith(word, i)) {
            throw error("expected " + word);
        }
        i += word.length();
        return value;
    }

    private void expect(char c) {
        if (i >= s.length() || s.charAt(i) != c) {
            throw error("expected '" + c + "'");
        }
        i++;
    }

    private void space() {
        while (i < s.length() && Character.isWhitespace(s.charAt(i))) {
            i++;
        }
    }

    private IllegalArgumentException error(String message) {
        return new IllegalArgumentException("invalid JSON at " + i + ": " + message);
    }
}
