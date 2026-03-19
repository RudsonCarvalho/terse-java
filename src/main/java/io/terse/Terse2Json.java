package io.terse;

import java.util.*;

/**
 * CLI: reads TERSE from stdin, writes JSON to stdout.
 */
public final class Terse2Json {

    public static void main(String[] args) throws Exception {
        String terse = new String(System.in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
        Object parsed = Terse.parseDocument(terse.trim());
        System.out.write(toJson(parsed).getBytes(java.nio.charset.StandardCharsets.UTF_8));
        System.out.flush();
    }

    static String toJson(Object v) {
        if (v == null)             return "null";
        if (v instanceof Boolean)  return v.toString();
        if (v instanceof Long || v instanceof Integer) return v.toString();
        if (v instanceof Double) {
            double d = (Double) v;
            if (d == Math.floor(d) && !Double.isInfinite(d) && Math.abs(d) < 1e15)
                return Long.toString((long) d);
            return Double.toString(d);
        }
        if (v instanceof Number)   return v.toString();
        if (v instanceof String)   return jsonStr((String) v);
        if (v instanceof List) {
            List<?> l = (List<?>) v;
            StringBuilder sb = new StringBuilder("[");
            for (int i = 0; i < l.size(); i++) {
                if (i > 0) sb.append(',');
                sb.append(toJson(l.get(i)));
            }
            return sb.append(']').toString();
        }
        if (v instanceof Map) {
            Map<?, ?> m = (Map<?, ?>) v;
            StringBuilder sb = new StringBuilder("{");
            boolean first = true;
            for (Map.Entry<?, ?> e : m.entrySet()) {
                if (!first) sb.append(',');
                sb.append(jsonStr(e.getKey().toString()));
                sb.append(':');
                sb.append(toJson(e.getValue()));
                first = false;
            }
            return sb.append('}').toString();
        }
        return "null";
    }

    private static String jsonStr(String s) {
        StringBuilder sb = new StringBuilder("\"");
        for (char c : s.toCharArray()) {
            switch (c) {
                case '"':  sb.append("\\\""); break;
                case '\\': sb.append("\\\\"); break;
                case '\n': sb.append("\\n");  break;
                case '\r': sb.append("\\r");  break;
                case '\t': sb.append("\\t");  break;
                default:
                    if (c < 0x20) sb.append(String.format("\\u%04x", (int) c));
                    else sb.append(c);
            }
        }
        return sb.append('"').toString();
    }
}
