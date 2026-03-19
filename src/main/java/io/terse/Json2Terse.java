package io.terse;

import java.io.*;
import java.util.*;

/**
 * CLI: reads JSON from stdin, writes TERSE document to stdout.
 * Depends on a bundled JSON parser (minimal, no external deps).
 */
public final class Json2Terse {

    public static void main(String[] args) throws Exception {
        String json = new String(System.in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
        Object parsed = parseJson(json.trim());
        String out;
        if (parsed instanceof Map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> doc = (Map<String, Object>) parsed;
            out = Terse.serializeDocument(doc);
        } else {
            out = Terse.serialize(parsed);
        }
        System.out.write(out.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        System.out.flush();
    }

    // ── Minimal JSON parser ───────────────────────────────────────────────────

    private static final class JP {
        final String s;
        int pos;
        JP(String s) { this.s = s; }

        void ws() { while (pos < s.length() && s.charAt(pos) <= ' ') pos++; }

        Object value() {
            ws();
            if (pos >= s.length()) throw new RuntimeException("unexpected EOF");
            char c = s.charAt(pos);
            if (c == '"') return str();
            if (c == '{') return obj();
            if (c == '[') return arr();
            if (c == 't') { pos += 4; return Boolean.TRUE; }
            if (c == 'f') { pos += 5; return Boolean.FALSE; }
            if (c == 'n') { pos += 4; return null; }
            return num();
        }

        String str() {
            pos++; // skip "
            StringBuilder sb = new StringBuilder();
            while (pos < s.length()) {
                char c = s.charAt(pos++);
                if (c == '"') return sb.toString();
                if (c == '\\') {
                    char e = s.charAt(pos++);
                    switch (e) {
                        case '"': sb.append('"'); break;
                        case '\\': sb.append('\\'); break;
                        case '/':  sb.append('/');  break;
                        case 'n':  sb.append('\n'); break;
                        case 'r':  sb.append('\r'); break;
                        case 't':  sb.append('\t'); break;
                        case 'b':  sb.append('\b'); break;
                        case 'f':  sb.append('\f'); break;
                        case 'u':
                            sb.append((char) Integer.parseInt(s.substring(pos, pos + 4), 16));
                            pos += 4;
                            break;
                        default: sb.append(e);
                    }
                } else {
                    sb.append(c);
                }
            }
            throw new RuntimeException("unterminated string");
        }

        Map<String, Object> obj() {
            pos++; // skip {
            Map<String, Object> m = new LinkedHashMap<>();
            ws();
            if (pos < s.length() && s.charAt(pos) == '}') { pos++; return m; }
            while (true) {
                ws();
                String k = str();
                ws();
                if (s.charAt(pos++) != ':') throw new RuntimeException("expected :");
                m.put(k, value());
                ws();
                char sep = s.charAt(pos++);
                if (sep == '}') break;
                if (sep != ',') throw new RuntimeException("expected , or }");
            }
            return m;
        }

        List<Object> arr() {
            pos++; // skip [
            List<Object> l = new ArrayList<>();
            ws();
            if (pos < s.length() && s.charAt(pos) == ']') { pos++; return l; }
            while (true) {
                l.add(value());
                ws();
                char sep = s.charAt(pos++);
                if (sep == ']') break;
                if (sep != ',') throw new RuntimeException("expected , or ]");
            }
            return l;
        }

        Number num() {
            int start = pos;
            if (pos < s.length() && s.charAt(pos) == '-') pos++;
            while (pos < s.length() && s.charAt(pos) >= '0' && s.charAt(pos) <= '9') pos++;
            boolean isFloat = false;
            if (pos < s.length() && s.charAt(pos) == '.') { isFloat = true; pos++;
                while (pos < s.length() && s.charAt(pos) >= '0' && s.charAt(pos) <= '9') pos++;
            }
            if (pos < s.length() && (s.charAt(pos) == 'e' || s.charAt(pos) == 'E')) {
                isFloat = true; pos++;
                if (pos < s.length() && (s.charAt(pos) == '+' || s.charAt(pos) == '-')) pos++;
                while (pos < s.length() && s.charAt(pos) >= '0' && s.charAt(pos) <= '9') pos++;
            }
            String tok = s.substring(start, pos);
            if (isFloat) return Double.parseDouble(tok);
            try { return Long.parseLong(tok); } catch (NumberFormatException e) { return Double.parseDouble(tok); }
        }
    }

    static Object parseJson(String json) { return new JP(json).value(); }
}
