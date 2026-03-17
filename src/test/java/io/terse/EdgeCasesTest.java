package io.terse;

import org.junit.jupiter.api.Test;

import java.util.*;

import static io.terse.AppendixBTest.asList;
import static io.terse.AppendixBTest.linked;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Edge-case tests: spec corner cases, error conditions, and serializer constraints.
 */
class EdgeCasesTest {

    // ── Tab rejection ─────────────────────────────────────────────────────────

    @Test
    void tabs_inValuePosition_throwsTerseException() {
        TerseException ex = assertThrows(TerseException.class, () -> Terse.parse("\t42"));
        assertEquals("ILLEGAL_CHARACTER", ex.getCode());
    }

    @Test
    void tabs_inIndentation_throwsTerseException() {
        TerseException ex = assertThrows(TerseException.class,
            () -> Terse.parseDocument("key:\n\t42\n"));
        assertEquals("ILLEGAL_CHARACTER", ex.getCode());
    }

    // ── CRLF normalization ────────────────────────────────────────────────────

    @Test
    void crlf_normalizedToLf() {
        Map<String, Object> r = Terse.parseDocument("name: my-app\r\nport: 3000\r\n");
        assertEquals("my-app", r.get("name"));
        assertEquals(3000L,    r.get("port"));
    }

    // ── Null / Boolean / String disambiguation ────────────────────────────────

    @Test
    void reserved_T_serializedQuoted_whenString() {
        String s = Terse.serialize("T");
        assertEquals("\"T\"", s);
    }

    @Test
    void reserved_F_serializedQuoted_whenString() {
        assertEquals("\"F\"", Terse.serialize("F"));
    }

    @Test
    void reserved_tilde_serializedQuoted_whenString() {
        assertEquals("\"~\"", Terse.serialize("~"));
    }

    @Test
    void parse_T_returnsBooleanTrue() {
        assertSame(Boolean.TRUE, Terse.parse("T"));
    }

    @Test
    void parse_F_returnsBooleanFalse() {
        assertSame(Boolean.FALSE, Terse.parse("F"));
    }

    @Test
    void parse_tilde_returnsNull() {
        assertNull(Terse.parse("~"));
    }

    // ── Number precedence over safe-id (§4.3) ────────────────────────────────

    @Test
    void numberLiteral_parsedAsLong_notString() {
        assertEquals(42L,    Terse.parse("42"));
        assertEquals(-7L,    Terse.parse("-7"));
        assertEquals(0L,     Terse.parse("0"));
    }

    @Test
    void floatLiteral_parsedAsDouble_notString() {
        assertEquals(3.14,    Terse.parse("3.14"));
        assertEquals(1.5e-10, Terse.parse("1.5e-10"));
        assertEquals(-4.2,    Terse.parse("-4.2"));
        assertEquals(1e3,     Terse.parse("1e3"));
    }

    @Test
    void stringThatLooksLikeNumber_mustBeQuoted() {
        // "404" as a string (not integer)
        assertEquals("404", Terse.parse("\"404\""));
    }

    @Test
    void serialize_integer_notFloat() {
        // integer-valued double should serialize as integer
        assertEquals("42",   Terse.serialize(42.0));
        assertEquals("-7",   Terse.serialize(-7.0));
        assertEquals("1000", Terse.serialize(1e3));
    }

    // ── Safe-id grammar ───────────────────────────────────────────────────────

    @Test
    void safeId_bareStrings_neverQuoted() {
        assertEquals("production",    Terse.serialize("production"));
        assertEquals("alice@co.com",  Terse.serialize("alice@co.com"));
        assertEquals("v2.1.0",        Terse.serialize("v2.1.0"));
        assertEquals("api.example.com", Terse.serialize("api.example.com"));
    }

    @Test
    void unsafeString_alwaysQuoted() {
        assertEquals("\"hello world\"", Terse.serialize("hello world")); // space
        assertEquals("\"^18.2.0\"",     Terse.serialize("^18.2.0"));     // ^ not safe
        assertEquals("\"vite build\"",  Terse.serialize("vite build"));  // space
    }

    @Test
    void string_startingWithDigit_mustBeQuoted() {
        // "2.1.0" starts with digit — not a valid safe-start
        assertEquals("\"2.1.0\"", Terse.serialize("2.1.0"));
    }

    // ── Comments ──────────────────────────────────────────────────────────────

    @Test
    void comments_ignoredInDocument() {
        String src = "// App config\nname: my-app\n// end\nport: 3000\n";
        Map<String, Object> r = Terse.parseDocument(src);
        assertEquals("my-app", r.get("name"));
        assertEquals(3000L,    r.get("port"));
    }

    // ── Duplicate key detection ───────────────────────────────────────────────

    @Test
    void duplicateKey_inDocument_throws() {
        TerseException ex = assertThrows(TerseException.class,
            () -> Terse.parseDocument("a: 1\nb: 2\na: 3\n"));
        assertEquals("DUPLICATE_KEY", ex.getCode());
    }

    @Test
    void duplicateKey_inBlockObject_throws() {
        TerseException ex = assertThrows(TerseException.class,
            () -> Terse.parse("{\na: 1\nb: 2\na: 3\n}"));
        assertEquals("DUPLICATE_KEY", ex.getCode());
    }

    @Test
    void duplicateKey_inInlineObject_throws() {
        assertThrows(TerseException.class, () -> Terse.parse("{a:1 a:2}"));
    }

    // ── Max depth (64 levels) ────────────────────────────────────────────────

    @Test
    void maxDepth_exactly64_allowed() {
        // Build 64-level nested list value
        Object v = "leaf";
        for (int i = 0; i < 63; i++) v = List.of(v);
        String s = Terse.serialize(v);
        assertNotNull(s);
        // Parse it back (depth counter is reset per parse call)
        Object back = Terse.parse(s);
        assertNotNull(back);
    }

    @Test
    void maxDepth_65_throwsOnSerialize() {
        Object v = "leaf";
        for (int i = 0; i < 65; i++) v = List.of(v);
        final Object deep = v;
        TerseException ex = assertThrows(TerseException.class, () -> Terse.serialize(deep));
        assertEquals("MAX_DEPTH_EXCEEDED", ex.getCode());
    }

    @Test
    void maxDepth_65_throwsOnParse() {
        // Build a 65-deep inline array string manually
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 65; i++) sb.append('[');
        sb.append("leaf");
        for (int i = 0; i < 65; i++) sb.append(']');
        TerseException ex = assertThrows(TerseException.class, () -> Terse.parse(sb.toString()));
        assertEquals("MAX_DEPTH_EXCEEDED", ex.getCode());
    }

    // ── Schema arrays ────────────────────────────────────────────────────────

    @Test
    void schemaArray_uniformObjects_usesSchemaForm() {
        List<Object> rows = List.of(
            linked("id", 1L, "name", "Alice"),
            linked("id", 2L, "name", "Bob")
        );
        String s = Terse.serialize(rows);
        assertTrue(s.startsWith("#["), "Expected schema array header; got: " + s);
        assertEquals(rows, Terse.parse(s));
    }

    @Test
    void schemaArray_withNullValue_roundTrips() {
        List<Object> rows = List.of(
            linked("id", 1L, "role", "admin"),
            linked("id", 2L, "role", null)
        );
        String s = Terse.serialize(rows);
        assertTrue(s.contains("~"), "Null value should be serialized as ~");
        assertEquals(rows, Terse.parse(s));
    }

    @Test
    void schemaArray_singleElement_notSchemaForm() {
        // Schema form requires ≥2 elements
        List<Object> rows = List.of(linked("a", 1L));
        String s = Terse.serialize(rows);
        assertFalse(s.startsWith("#["), "Single-element list should not use schema form");
    }

    @Test
    void schemaArray_withNestedValue_notSchemaForm() {
        // Values must all be primitives for schema form
        List<Object> rows = List.of(
            linked("x", 1L, "y", List.of(1L, 2L)),
            linked("x", 2L, "y", List.of(3L, 4L))
        );
        String s = Terse.serialize(rows);
        assertFalse(s.startsWith("#["), "Non-primitive values prevent schema form");
    }

    @Test
    void schemaArray_differentKeys_notSchemaForm() {
        List<Object> rows = asList(
            linked("a", 1L),
            linked("b", 2L)
        );
        String s = Terse.serialize(rows);
        assertFalse(s.startsWith("#["));
    }

    // ── Inline vs block form ──────────────────────────────────────────────────

    @Test
    void shortObject_serializedInline() {
        String s = Terse.serialize(linked("a", 1L, "b", "foo"));
        assertFalse(s.contains("\n"), "Short object should be inline: " + s);
    }

    @Test
    void emptyObject_serialized() {
        assertEquals("{}", Terse.serialize(new LinkedHashMap<>()));
    }

    @Test
    void emptyArray_serialized() {
        assertEquals("[]", Terse.serialize(new ArrayList<>()));
    }

    // ── Quoted string escapes ─────────────────────────────────────────────────

    @Test
    void quotedString_escapesNewline() {
        assertEquals("hello\nworld", Terse.parse("\"hello\\nworld\""));
    }

    @Test
    void quotedString_escapesBackslash() {
        assertEquals("C:\\Users\\Alice", Terse.parse("\"C:\\\\Users\\\\Alice\""));
    }

    @Test
    void quotedString_unicodeEscape() {
        assertEquals("\u00e9", Terse.parse("\"\\u00e9\""));
    }

    @Test
    void quotedString_roundTrips() {
        String tricky = "Hello, \"world\"!\nLine2\\tab\t";
        assertEquals(tricky, Terse.parse(Terse.serialize(tricky)));
    }

    // ── Document API ─────────────────────────────────────────────────────────

    @Test
    void parseDocument_emptyInput_returnsEmptyMap() {
        assertTrue(Terse.parseDocument("").isEmpty());
        assertTrue(Terse.parseDocument("  \n  \n").isEmpty());
    }

    @Test
    void parseDocument_blockValue_parsedCorrectly() {
        String src = "config:\n  {port:3000 debug:F}\n";
        Map<String, Object> r = Terse.parseDocument(src);
        Map<?,?> config = (Map<?,?>) r.get("config");
        assertEquals(3000L, config.get("port"));
        assertEquals(false, config.get("debug"));
    }

    @Test
    void serializeDocument_roundTrip() {
        Map<String, Object> obj = linked("name", "my-app", "port", 8080L,
                                          "debug", false, "tags", List.of("a", "b"));
        String doc  = Terse.serializeDocument(obj);
        Map<String, Object> back = Terse.parseDocument(doc);
        assertEquals(obj, back);
    }

    // ── Integer types ────────────────────────────────────────────────────────

    @Test
    void serialize_int_worksLikeInteger() {
        assertEquals("42",  Terse.serialize(42));     // Integer
        assertEquals("42",  Terse.serialize(42L));    // Long
        assertEquals("-7",  Terse.serialize(-7));
    }

    // ── Inline object with nested object ────────────────────────────────────

    @Test
    void inlineObject_nestedInlineObject_roundTrips() {
        Object v = linked("a", linked("b", 1L));
        assertEquals(v, Terse.parse(Terse.serialize(v)));
    }

    // ── Parse value, then document ───────────────────────────────────────────

    @Test
    void parse_blockObject_parsesCorrectly() {
        String src = "{\nport: 3000\ndebug: F\n}";
        Map<?,?> r = (Map<?,?>) Terse.parse(src);
        assertEquals(3000L, r.get("port"));
        assertEquals(false, r.get("debug"));
    }

    @Test
    void parse_blockArray_parsesCorrectly() {
        String src = "[\n1\nhello\nT\n]";
        @SuppressWarnings("unchecked")
        List<Object> r = (List<Object>) Terse.parse(src);
        assertEquals(List.of(1L, "hello", true), r);
    }
}
