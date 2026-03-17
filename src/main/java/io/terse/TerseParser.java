package io.terse;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Parses TERSE format strings into Java objects.
 * <p>
 * Return type mapping:
 * <ul>
 *   <li>{@code ~}          → {@code null}</li>
 *   <li>{@code T} / {@code F} → {@code Boolean}</li>
 *   <li>integer literal    → {@code Long}</li>
 *   <li>decimal literal    → {@code Double}</li>
 *   <li>bare / quoted str  → {@code String}</li>
 *   <li>{@code {...}}      → {@code LinkedHashMap<String,Object>}</li>
 *   <li>{@code [...]}      → {@code ArrayList<Object>}</li>
 *   <li>{@code #[...]}     → {@code ArrayList<LinkedHashMap<String,Object>>}</li>
 * </ul>
 */
final class TerseParser {

    private final String src;
    private int pos;
    private int depth;

    TerseParser(String raw) {
        // Normalize line endings
        String s = raw.replace("\r\n", "\n").replace("\r", "\n");
        // Reject tabs
        for (int i = 0; i < s.length(); i++) {
            if (s.charAt(i) == '\t') {
                throw new TerseException("Tab characters are not permitted", i, "ILLEGAL_CHARACTER");
            }
        }
        this.src   = s;
        this.pos   = 0;
        this.depth = 0;
    }

    // ── Public entry points ──────────────────────────────────────────────────

    Object parse() {
        skipBlanks();
        Object result = parseValue();
        skipBlanks();
        if (pos < src.length()) {
            throw new TerseException("Unexpected content after value", pos);
        }
        return result;
    }

    Map<String, Object> parseDocument() {
        Map<String, Object> result = new LinkedHashMap<>();
        while (pos < src.length()) {
            skipBlankLines();
            if (pos >= src.length()) break;

            // Skip comment lines
            if (isCommentStart()) {
                skipToEol(); advance('\n');
                continue;
            }

            String key = parseKey();
            skipSpaces();
            expect(':');
            pos++; // consume ':'

            Object value;
            if (pos < src.length() && src.charAt(pos) == '\n') {
                // Block value: on next line(s), indented
                pos++; // consume '\n'
                skipSpaces();
                value = parseValue();
            } else {
                skipSpaces();
                value = parseValue();
            }

            if (result.containsKey(key)) {
                throw new TerseException("Duplicate key: " + key, pos, "DUPLICATE_KEY");
            }
            result.put(key, value);

            skipSpaces();
            if (pos < src.length() && src.charAt(pos) == '\n') pos++;
        }
        return result;
    }

    // ── Value dispatcher ─────────────────────────────────────────────────────

    Object parseValue() {
        skipSpaces();
        if (pos >= src.length()) {
            throw new TerseException("Unexpected end of input", pos, "UNEXPECTED_EOF");
        }

        depth++;
        if (depth > TerseSerializer.MAX_DEPTH) {
            throw new TerseException("Maximum nesting depth exceeded", pos, "MAX_DEPTH_EXCEEDED");
        }

        try {
            char c = src.charAt(pos);

            // Comment (inside inline context, skip and re-parse)
            if (c == '/' && peek(1) == '/') {
                skipToEol();
                return parseValue();
            }

            // Null
            if (c == '~') { pos++; return null; }

            // Quoted string
            if (c == '"') return parseQuotedString();

            // Schema array
            if (c == '#' && peek(1) == '[') return parseSchemaArray();

            // Object
            if (c == '{') return parseObject();

            // Array
            if (c == '[') return parseArray();

            // Number (precedence over safe-id: §4.3)
            if (c == '-' || isDigit(c)) return parseNumber();

            // Safe-id (includes bare strings, T, F)
            if (isSafeStart(c)) {
                int start = pos;
                pos++;
                while (pos < src.length() && isSafeChar(src.charAt(pos))) pos++;
                String id = src.substring(start, pos);
                if (id.equals("T")) return Boolean.TRUE;
                if (id.equals("F")) return Boolean.FALSE;
                return id;
            }

            throw new TerseException("Unexpected character '" + c + "'", pos, "ILLEGAL_CHARACTER");

        } finally {
            depth--;
        }
    }

    // ── Number ───────────────────────────────────────────────────────────────

    private Object parseNumber() {
        int start = pos;
        if (pos < src.length() && src.charAt(pos) == '-') pos++;

        if (pos >= src.length() || !isDigit(src.charAt(pos))) {
            throw new TerseException("Invalid number", start, "ILLEGAL_CHARACTER");
        }
        while (pos < src.length() && isDigit(src.charAt(pos))) pos++;

        boolean isFloat = false;
        if (pos < src.length() && src.charAt(pos) == '.') {
            isFloat = true;
            pos++;
            while (pos < src.length() && isDigit(src.charAt(pos))) pos++;
        }
        if (pos < src.length() && (src.charAt(pos) == 'e' || src.charAt(pos) == 'E')) {
            isFloat = true;
            pos++;
            if (pos < src.length() && (src.charAt(pos) == '+' || src.charAt(pos) == '-')) pos++;
            while (pos < src.length() && isDigit(src.charAt(pos))) pos++;
        }

        String numStr = src.substring(start, pos);
        if (isFloat) return Double.parseDouble(numStr);
        try {
            return Long.parseLong(numStr);
        } catch (NumberFormatException e) {
            return Double.parseDouble(numStr); // overflow: treat as double
        }
    }

    // ── Object ───────────────────────────────────────────────────────────────

    private Map<String, Object> parseObject() {
        pos++; // consume '{'
        boolean isBlock = pos < src.length() && src.charAt(pos) == '\n';
        Map<String, Object> result = new LinkedHashMap<>();

        if (isBlock) {
            pos++; // consume '\n'
            while (pos < src.length()) {
                skipSpaces();
                if (pos >= src.length()) throw new TerseException("Unclosed block object", pos);
                if (src.charAt(pos) == '}') { pos++; break; }
                if (isCommentStart()) { skipToEol(); advance('\n'); continue; }

                String key   = parseKey();
                skipSpaces(); expect(':'); pos++;

                Object value;
                if (pos < src.length() && src.charAt(pos) == '\n') {
                    pos++; skipSpaces(); value = parseValue();
                } else {
                    skipSpaces(); value = parseValue();
                }
                putUnique(result, key, value);
                skipSpaces();
                if (pos < src.length() && src.charAt(pos) == '\n') pos++;
            }
        } else {
            // Inline: parse key:value pairs separated by spaces until '}'
            while (pos < src.length() && src.charAt(pos) != '}') {
                skipSpaces();
                if (pos >= src.length() || src.charAt(pos) == '}') break;
                String key = parseKey();
                expect(':'); pos++;
                Object value = parseValue();
                putUnique(result, key, value);
                skipSpaces();
            }
            expect('}'); pos++;
        }
        return result;
    }

    // ── Array ────────────────────────────────────────────────────────────────

    private List<Object> parseArray() {
        pos++; // consume '['
        boolean isBlock = pos < src.length() && src.charAt(pos) == '\n';
        List<Object> result = new ArrayList<>();

        if (isBlock) {
            pos++; // consume '\n'
            while (pos < src.length()) {
                skipSpaces();
                if (pos >= src.length()) throw new TerseException("Unclosed block array", pos);
                if (src.charAt(pos) == ']') { pos++; break; }
                result.add(parseValue());
                skipSpaces();
                if (pos < src.length() && src.charAt(pos) == '\n') pos++;
            }
        } else {
            skipSpaces();
            while (pos < src.length() && src.charAt(pos) != ']') {
                result.add(parseValue());
                skipSpaces();
            }
            expect(']'); pos++;
        }
        return result;
    }

    // ── Schema array ─────────────────────────────────────────────────────────

    private List<Map<String, Object>> parseSchemaArray() {
        pos += 2; // consume '#['

        // Parse field names
        List<String> fields = new ArrayList<>();
        while (pos < src.length() && src.charAt(pos) != ']') {
            skipSpaces();
            if (pos < src.length() && src.charAt(pos) == ']') break;
            fields.add(parseKey());
            skipSpaces();
        }
        expect(']'); pos++;

        // Consume newline after header
        if (pos < src.length() && src.charAt(pos) == '\n') pos++;

        List<Map<String, Object>> result = new ArrayList<>();

        // Each data row must be indented by ≥2 spaces
        while (pos < src.length()) {
            // Skip blank lines, then count indent of next non-blank line
            int scan = pos;
            while (scan < src.length() && src.charAt(scan) == '\n') scan++;
            int spaces = 0;
            while (scan + spaces < src.length() && src.charAt(scan + spaces) == ' ') spaces++;
            if (spaces < 2) break; // end of schema array (next doc key or EOF)

            pos = scan + spaces; // advance past indent

            Map<String, Object> row = new LinkedHashMap<>();
            for (int i = 0; i < fields.size(); i++) {
                if (i > 0) skipSpaces();
                row.put(fields.get(i), parseValue());
            }
            result.add(row);

            skipSpaces();
            if (pos < src.length() && src.charAt(pos) == '\n') pos++;
        }
        return result;
    }

    // ── Quoted string ────────────────────────────────────────────────────────

    private String parseQuotedString() {
        expect('"'); pos++;
        StringBuilder sb = new StringBuilder();
        while (pos < src.length()) {
            char c = src.charAt(pos);
            if (c == '"') { pos++; return sb.toString(); }
            if (c == '\\') {
                pos++;
                if (pos >= src.length()) {
                    throw new TerseException("Unexpected EOF in string escape", pos, "UNEXPECTED_EOF");
                }
                char esc = src.charAt(pos++);
                switch (esc) {
                    case '"':  sb.append('"');  break;
                    case '\\': sb.append('\\'); break;
                    case '/':  sb.append('/');  break;
                    case 'n':  sb.append('\n'); break;
                    case 'r':  sb.append('\r'); break;
                    case 't':  sb.append('\t'); break;
                    case 'b':  sb.append('\b'); break;
                    case 'f':  sb.append('\f'); break;
                    case 'u':
                        if (pos + 4 > src.length()) {
                            throw new TerseException("Incomplete \\u escape", pos, "INVALID_ESCAPE");
                        }
                        sb.append((char) Integer.parseInt(src.substring(pos, pos + 4), 16));
                        pos += 4;
                        break;
                    default:
                        throw new TerseException("Invalid escape '\\" + esc + "'", pos - 1, "INVALID_ESCAPE");
                }
            } else {
                sb.append(c);
                pos++;
            }
        }
        throw new TerseException("Unclosed string literal", pos, "UNEXPECTED_EOF");
    }

    // ── Key parsing ───────────────────────────────────────────────────────────

    String parseKey() {
        if (pos < src.length() && src.charAt(pos) == '"') return parseQuotedString();
        if (pos >= src.length() || !isSafeStart(src.charAt(pos))) {
            throw new TerseException("Expected key (safe-id or quoted string)", pos);
        }
        int start = pos++;
        while (pos < src.length() && isSafeChar(src.charAt(pos))) pos++;
        return src.substring(start, pos);
    }

    // ── Character helpers ────────────────────────────────────────────────────

    private static boolean isSafeStart(char c) {
        return ((c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z') || c == '_' || c == '.' || c == '/');
    }

    private static boolean isSafeChar(char c) {
        return isSafeStart(c) || isDigit(c) || c == '-' || c == '@';
    }

    private static boolean isDigit(char c) {
        return c >= '0' && c <= '9';
    }

    private boolean isCommentStart() {
        return pos + 1 < src.length() && src.charAt(pos) == '/' && src.charAt(pos + 1) == '/';
    }

    private char peek(int offset) {
        int i = pos + offset;
        return i < src.length() ? src.charAt(i) : '\0';
    }

    private void skipSpaces() {
        while (pos < src.length() && src.charAt(pos) == ' ') pos++;
    }

    private void skipBlankLines() {
        while (pos < src.length() && (src.charAt(pos) == '\n' || src.charAt(pos) == ' ')) pos++;
    }

    private void skipBlanks() {
        while (pos < src.length() && (src.charAt(pos) == ' ' || src.charAt(pos) == '\n')) pos++;
    }

    private void skipToEol() {
        while (pos < src.length() && src.charAt(pos) != '\n') pos++;
    }

    private void advance(char expected) {
        if (pos < src.length() && src.charAt(pos) == expected) pos++;
    }

    private void expect(char expected) {
        if (pos >= src.length() || src.charAt(pos) != expected) {
            char got = pos < src.length() ? src.charAt(pos) : '\0';
            throw new TerseException("Expected '" + expected + "' but got '" + got + "'", pos);
        }
    }

    private void putUnique(Map<String, Object> map, String key, Object value) {
        if (map.containsKey(key)) {
            throw new TerseException("Duplicate key: " + key, pos, "DUPLICATE_KEY");
        }
        map.put(key, value);
    }
}
