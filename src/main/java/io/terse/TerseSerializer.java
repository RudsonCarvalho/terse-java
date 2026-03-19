package io.terse;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Converts Java objects to TERSE format strings.
 * <p>
 * Supported Java types:
 * <ul>
 *   <li>{@code null}                → {@code ~}</li>
 *   <li>{@code Boolean}             → {@code T} / {@code F}</li>
 *   <li>{@code Long}, {@code Integer} (and other integer Number) → integer literal</li>
 *   <li>{@code Double}, {@code Float} (and other decimal Number) → decimal literal</li>
 *   <li>{@code String}              → bare string or {@code "quoted"}</li>
 *   <li>{@code List<?>}             → schema array, inline array, or block array</li>
 *   <li>{@code Map<?,?>}            → inline object or block object</li>
 * </ul>
 */
final class TerseSerializer {

    static final int LINE_LIMIT = 80;
    static final int MAX_DEPTH  = 64;

    private static final Set<String> RESERVED = Set.of("T", "F", "~", "{}", "[]");

    // ── Public entry points ──────────────────────────────────────────────────

    static String serialize(Object value) {
        return serialize(value, 0);
    }

    static String serializeDocument(Map<?, ?> obj) {
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<?, ?> e : obj.entrySet()) {
            String k   = serializeKey(e.getKey().toString());
            String val = serialize(e.getValue(), 0);
            appendKeyValue(sb, k, val, 0);
        }
        return sb.toString();
    }

    // ── Core dispatcher ──────────────────────────────────────────────────────

    static String serialize(Object value, int depth) {
        if (depth > MAX_DEPTH) {
            throw new TerseException("Maximum nesting depth exceeded", -1, "MAX_DEPTH_EXCEEDED");
        }
        if (value == null)                          return "~";
        if (value instanceof Boolean)               return (Boolean) value ? "T" : "F";
        if (value instanceof Long || value instanceof Integer
                || value instanceof Short || value instanceof Byte) {
            return value.toString();
        }
        if (value instanceof Number)                return serializeDouble(((Number) value).doubleValue());
        if (value instanceof String)                return serializeString((String) value);
        if (value instanceof List)                  return serializeList((List<?>) value, depth);
        if (value instanceof Map)                   return serializeMap((Map<?, ?>) value, depth);
        throw new TerseException("Unsupported type: " + value.getClass().getName(), -1);
    }

    // ── Primitives ───────────────────────────────────────────────────────────

    private static String serializeDouble(double d) {
        if (!Double.isInfinite(d) && !Double.isNaN(d)
                && d == Math.floor(d) && Math.abs(d) < 1e15) {
            return Long.toString((long) d);
        }
        // Double.toString gives shortest round-trip representation in Java 11+
        return Double.toString(d).replace('E', 'e');
    }

    static String serializeString(String s) {
        if (isSafeId(s)) return s;
        StringBuilder sb = new StringBuilder("\"");
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"':  sb.append("\\\""); break;
                case '\\': sb.append("\\\\"); break;
                case '\n': sb.append("\\n");  break;
                case '\r': sb.append("\\r");  break;
                case '\t': sb.append("\\t");  break;
                case '\b': sb.append("\\b");  break;
                case '\f': sb.append("\\f");  break;
                default:
                    if (c < 0x20) sb.append(String.format("\\u%04x", (int) c));
                    else          sb.append(c);
            }
        }
        sb.append('"');
        return sb.toString();
    }

    // key serialization: safe-ids are bare, else quoted
    private static String serializeKey(String k) {
        return isSafeId(k) ? k : serializeString(k);
    }

    // ── Safe-id grammar ──────────────────────────────────────────────────────

    static boolean isSafeId(String s) {
        if (s == null || s.isEmpty()) return false;
        if (RESERVED.contains(s))     return false;
        char first = s.charAt(0);
        if (!isSafeStart(first)) return false;
        for (int i = 1; i < s.length(); i++) {
            if (!isSafeChar(s.charAt(i))) return false;
        }
        // Number-like strings must be quoted (§4.3 parser precedence rule)
        return !looksLikeNumber(s);
    }

    private static boolean isSafeStart(char c) {
        return isAsciiAlpha(c) || c == '_' || c == '.' || c == '/';
    }

    static boolean isSafeChar(char c) {
        return isSafeStart(c) || isAsciiDigit(c) || c == '-' || c == '@';
    }

    private static boolean isAsciiAlpha(char c) {
        return (c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z');
    }

    private static boolean isAsciiDigit(char c) {
        return c >= '0' && c <= '9';
    }

    private static boolean looksLikeNumber(String s) {
        // A safe-id that starts with a letter/./_ can never be a JSON number
        // (numbers start with - or digit). So no need to check further.
        return false;
    }

    // ── List serialization ───────────────────────────────────────────────────

    private static String serializeList(List<?> list, int depth) {
        if (list.isEmpty()) return "[]";

        // Schema array: 2+ elements, all Maps, same ordered keys, all primitive values
        List<String> keys = schemaKeys(list);
        if (keys != null) {
            return serializeSchemaArray(list, keys, depth);
        }

        // Inline if it fits
        String inline = tryInlineList(list, depth);
        if (inline != null && inline.length() <= LINE_LIMIT) return inline;

        // Block form — each element must be single line (inline-forced)
        StringBuilder sb = new StringBuilder("[\n");
        for (Object item : list) {
            String v = serializeForced(item);
            sb.append(v).append('\n');
        }
        sb.append(']');
        return sb.toString();
    }

    private static String serializeForced(Object value) {
        if (value == null)         return "~";
        if (value instanceof Boolean) return (Boolean) value ? "T" : "F";
        if (value instanceof Long || value instanceof Integer
                || value instanceof Short || value instanceof Byte)
            return value.toString();
        if (value instanceof Number) return serializeDouble(((Number) value).doubleValue());
        if (value instanceof String) return serializeString((String) value);
        if (value instanceof Map) {
            Map<?, ?> m = (Map<?, ?>) value;
            if (m.isEmpty()) return "{}";
            StringBuilder sb = new StringBuilder("{");
            boolean firstM = true;
            for (Map.Entry<?, ?> e : m.entrySet()) {
                if (!firstM) sb.append(' ');
                sb.append(serializeKey(e.getKey().toString()))
                  .append(':')
                  .append(serializeForced(e.getValue()));
                firstM = false;
            }
            sb.append('}');
            return sb.toString();
        }
        if (value instanceof List) {
            List<?> l = (List<?>) value;
            if (l.isEmpty()) return "[]";
            StringBuilder sb = new StringBuilder("[");
            boolean firstL = true;
            for (Object item : l) {
                if (!firstL) sb.append(' ');
                sb.append(serializeForced(item));
                firstL = false;
            }
            sb.append(']');
            return sb.toString();
        }
        return "~";
    }

    private static String serializeSchemaArray(List<?> list, List<String> keys, int depth) {
        StringBuilder sb = new StringBuilder("#[");
        sb.append(String.join(" ", keys));
        sb.append(']');
        for (Object item : list) {
            Map<?, ?> row = (Map<?, ?>) item;
            sb.append("\n  ");
            boolean first = true;
            for (String k : keys) {
                if (!first) sb.append(' ');
                sb.append(serialize(row.get(k), depth + 1));
                first = false;
            }
        }
        return sb.toString();
    }

    @SuppressWarnings("unchecked")
    private static List<String> schemaKeys(List<?> list) {
        if (list.size() < 2) return null;
        for (Object item : list) {
            if (!(item instanceof Map)) return null;
        }
        List<Map<?, ?>> maps = (List<Map<?, ?>>) (List<?>) list;
        List<String> keys = new java.util.ArrayList<>();
        for (Object k : maps.get(0).keySet()) keys.add(k.toString());
        for (Map<?, ?> m : maps) {
            List<String> mk = new java.util.ArrayList<>();
            for (Object k : m.keySet()) mk.add(k.toString());
            if (!mk.equals(keys)) return null;
            for (Object v : m.values()) {
                if (v instanceof Map || v instanceof List) return null;
            }
        }
        return keys;
    }

    // ── Map serialization ────────────────────────────────────────────────────

    private static String serializeMap(Map<?, ?> map, int depth) {
        if (map.isEmpty()) return "{}";

        String inline = tryInlineMap(map, depth);
        if (inline != null && inline.length() <= LINE_LIMIT) return inline;

        // Block form
        StringBuilder sb = new StringBuilder("{\n");
        for (Map.Entry<?, ?> e : map.entrySet()) {
            String k = serializeKey(e.getKey().toString());
            String v = serialize(e.getValue(), depth + 1);
            appendKeyValue(sb, k, v, 0);
        }
        sb.append('}');
        return sb.toString();
    }

    // ── tryInline helpers ────────────────────────────────────────────────────

    private static String tryInlineList(List<?> list, int depth) {
        if (depth > MAX_DEPTH) return null;
        StringBuilder sb = new StringBuilder("[");
        boolean first = true;
        for (int i = 0; i < list.size(); i++) {
            String v = tryInlineValue(list.get(i), depth + 1);
            if (v == null) return null;
            if (!first) sb.append(' ');
            sb.append(v);
            first = false;
        }
        sb.append(']');
        return sb.toString();
    }

    private static String tryInlineMap(Map<?, ?> map, int depth) {
        if (depth > MAX_DEPTH) return null;
        StringBuilder sb = new StringBuilder("{");
        boolean first = true;
        for (Map.Entry<?, ?> e : map.entrySet()) {
            String k = serializeKey(e.getKey().toString());
            String v = tryInlineValue(e.getValue(), depth + 1);
            if (v == null) return null;
            if (!first) sb.append(' ');
            sb.append(k).append(':').append(v);
            first = false;
        }
        sb.append('}');
        return sb.toString();
    }

    private static String tryInlineValue(Object value, int depth) {
        if (depth > MAX_DEPTH) return null;
        if (value == null)         return "~";
        if (value instanceof Boolean) return (Boolean) value ? "T" : "F";
        if (value instanceof Long || value instanceof Integer
                || value instanceof Short || value instanceof Byte)
            return value.toString();
        if (value instanceof Number) return serializeDouble(((Number) value).doubleValue());
        if (value instanceof String) return serializeString((String) value);
        if (value instanceof List)   return tryInlineList((List<?>) value, depth);
        if (value instanceof Map)    return tryInlineMap((Map<?, ?>) value, depth);
        return null;
    }

    // ── Indentation helpers ──────────────────────────────────────────────────

    /**
     * Appends {@code key: value\n} or {@code key:\n  <lines>\n} to {@code sb}.
     * {@code baseIndent} is the extra indent prefix to add to each line.
     */
    private static void appendKeyValue(StringBuilder sb, String key, String value, int baseIndent) {
        String indent = "  ".repeat(baseIndent);
        if (!value.contains("\n")) {
            sb.append(indent).append(key).append(':').append(value).append('\n');
        } else {
            sb.append(indent).append(key).append(":\n");
            appendIndentedLines(sb, value, indent + "  ");
        }
    }

    /** Appends each non-empty line of {@code text} prefixed with {@code prefix}. */
    private static void appendIndentedLines(StringBuilder sb, String text, String prefix) {
        String[] lines = text.split("\n", -1);
        for (String line : lines) {
            if (!line.isEmpty()) {
                sb.append(prefix).append(line).append('\n');
            }
        }
    }
}
