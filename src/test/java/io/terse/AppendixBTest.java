package io.terse;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.*;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests covering Appendix B reference examples and full round-trip correctness.
 */
class AppendixBTest {

    // ── B.1 — Complete Application Configuration ─────────────────────────────

    static final Map<String, Object> B1_OBJ;
    static {
        Map<String, Object> scripts      = linked("dev", "vite", "build", "vite build", "test", "vitest");
        Map<String, Object> dependencies = linked("react", "^18.2.0", "zustand", "^4.4.1");
        Map<String, Object> author       = linked("name", "Alice", "email", "alice@co.com");
        Map<String, Object> config       = linked("port", 3000L, "debug", false,
                                                   "logLevel", "warn",
                                                   "tags", List.of("web", "typescript", "spa"));
        B1_OBJ = linked("name", "my-app", "version", "2.1.0", "private", true,
                         "author", author, "scripts", scripts,
                         "dependencies", dependencies, "config", config);
    }

    @Test
    void b1_serializeDocument_roundTrip() {
        String doc    = Terse.serializeDocument(B1_OBJ);
        Map<String, Object> result = Terse.parseDocument(doc);
        assertEquals(B1_OBJ, result);
    }

    @Test
    void b1_serializeValue_roundTrip() {
        String s      = Terse.serialize(B1_OBJ);
        Object result = Terse.parse(s);
        assertEquals(B1_OBJ, result);
    }

    @Test
    void b1_docSource_parsesCorrectly() {
        // NOTE: "2.1.0" must be quoted — §3.5 safe-id starts with ALPHA|_|.|/,
        // not a digit; "2.1.0" starts with a digit so it needs quotes.
        String src =
            "name: my-app\n" +
            "version: \"2.1.0\"\n" +
            "private: T\n" +
            "author: {name:Alice email:\"alice@co.com\"}\n" +
            "scripts: {dev:vite build:\"vite build\" test:vitest}\n" +
            "dependencies: {react:\"^18.2.0\" zustand:\"^4.4.1\"}\n" +
            "config:\n" +
            "  {\n" +
            "  port: 3000\n" +
            "  debug: F\n" +
            "  logLevel: warn\n" +
            "  tags: [web typescript spa]\n" +
            "  }\n";
        Map<String, Object> r = Terse.parseDocument(src);
        assertEquals("my-app",  r.get("name"));
        assertEquals("2.1.0",   r.get("version"));
        assertEquals(true,       r.get("private"));
        assertEquals("Alice",   ((Map<?,?>) r.get("author")).get("name"));
        assertEquals("vite",    ((Map<?,?>) r.get("scripts")).get("dev"));
        assertEquals(3000L,     ((Map<?,?>) r.get("config")).get("port"));
        assertEquals(List.of("web", "typescript", "spa"),
                     ((Map<?,?>) r.get("config")).get("tags"));
    }

    // ── B.2 — REST API Response with Schema Array ─────────────────────────────

    @Test
    void b2_schemaArray_parsesCorrectly() {
        String src =
            "total: 5\n" +
            "page: 1\n" +
            "data:\n" +
            "  #[id name email role active score]\n" +
            "  1 \"Ana Lima\" ana@co.com admin T 98.5\n" +
            "  2 \"Bruno Melo\" bruno@co.com editor T 87.2\n" +
            "  3 \"Carla Neves\" carla@co.com viewer F 72.0\n";
        Map<String, Object> r = Terse.parseDocument(src);
        assertEquals(5L, r.get("total"));
        assertEquals(1L, r.get("page"));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> data = (List<Map<String, Object>>) r.get("data");
        assertEquals(3, data.size());
        assertEquals("Ana Lima",  data.get(0).get("name"));
        assertEquals("admin",     data.get(0).get("role"));
        assertEquals(true,         data.get(0).get("active"));
        assertEquals(98.5,         data.get(0).get("score"));
        assertEquals("Carla Neves", data.get(2).get("name"));
        assertEquals(false,         data.get(2).get("active"));
    }

    @Test
    void b2_schemaArray_roundTrip() {
        Map<String, Object> r1 = linked("id", 1L, "name", "Ana Lima",
                                         "email", "ana@co.com", "role", "admin",
                                         "active", true, "score", 98.5);
        Map<String, Object> r2 = linked("id", 2L, "name", "Bruno Melo",
                                         "email", "bruno@co.com", "role", "editor",
                                         "active", true, "score", 87.2);
        List<Map<String, Object>> rows = List.of(r1, r2);
        String s   = Terse.serialize(rows);
        // Must start with schema header
        assertTrue(s.startsWith("#["), "Expected schema array; got: " + s);
        Object back = Terse.parse(s);
        assertEquals(rows, back);
    }

    // ── B.3 — Deeply Nested Order ─────────────────────────────────────────────

    @Test
    void b3_nestedOrder_parsesCorrectly() {
        String src =
            "orderId: ORD-88421\n" +
            "status: confirmed\n" +
            "customer: {id:1042 name:\"Rafael Torres\" email:\"r@email.com\"}\n" +
            "shipping:\n" +
            "  {\n" +
            "  address: \"Rua das Flores, 123\"\n" +
            "  city: \"São Paulo\"\n" +
            "  method: express\n" +
            "  estimatedDays: 2\n" +
            "  }\n" +
            "items:\n" +
            "  #[sku name qty unitPrice]\n" +
            "  PRD-001 \"Notebook Pro 15\" 1 4599.90\n" +
            "  PRD-002 \"Mouse Wireless\" 2 149.90\n" +
            "payment:\n" +
            "  {\n" +
            "  method: credit_card\n" +
            "  installments: 12\n" +
            "  total: 4924.70\n" +
            "  }\n";
        Map<String, Object> r = Terse.parseDocument(src);
        assertEquals("ORD-88421",     r.get("orderId"));
        assertEquals("confirmed",      r.get("status"));
        assertEquals("Rafael Torres", ((Map<?,?>) r.get("customer")).get("name"));
        assertEquals("express",        ((Map<?,?>) r.get("shipping")).get("method"));
        assertEquals(2L,               ((Map<?,?>) r.get("shipping")).get("estimatedDays"));
        @SuppressWarnings("unchecked")
        List<Map<?,?>> items = (List<Map<?,?>>) r.get("items");
        assertEquals(2, items.size());
        assertEquals("Notebook Pro 15", items.get(0).get("name"));
        assertEquals(4599.90,            items.get(0).get("unitPrice"));
        assertEquals(12L,                ((Map<?,?>) r.get("payment")).get("installments"));
    }

    // ── B.4 — Mixed Types and Edge Cases ─────────────────────────────────────

    @Test
    void b4_booleanTrue_parsedAsBoolean() {
        assertEquals(true, Terse.parse("T"));
    }

    @Test
    void b4_literalT_parsedAsString() {
        assertEquals("T", Terse.parse("\"T\""));
    }

    @Test
    void b4_null_parsedAsNull() {
        assertNull(Terse.parse("~"));
    }

    @Test
    void b4_literalTilde_parsedAsString() {
        assertEquals("~", Terse.parse("\"~\""));
    }

    @Test
    void b4_emptyObject() {
        assertEquals(new LinkedHashMap<>(), Terse.parse("{}"));
    }

    @Test
    void b4_emptyArray() {
        assertEquals(new ArrayList<>(), Terse.parse("[]"));
    }

    @Test
    void b4_heterogeneousArray_parsesCorrectly() {
        // mixed: [1 hello T ~ {x:1 y:2}]
        String src = "[1 hello T ~ {x:1 y:2}]";
        Object parsed = Terse.parse(src);
        @SuppressWarnings("unchecked")
        List<Object> list = (List<Object>) parsed;
        assertEquals(5,           list.size());
        assertEquals(1L,          list.get(0));
        assertEquals("hello",     list.get(1));
        assertEquals(true,         list.get(2));
        assertNull(               list.get(3));
        assertEquals(linked("x", 1L, "y", 2L), list.get(4));
    }

    // ── Round-trip fixtures ───────────────────────────────────────────────────

    static Stream<Object[]> roundTripFixtures() {
        return Stream.of(
            new Object[]{"null",           null},
            new Object[]{"true",           true},
            new Object[]{"false",          false},
            new Object[]{"zero",           0L},
            new Object[]{"int",            42L},
            new Object[]{"neg",            -7L},
            new Object[]{"float",          3.14},
            new Object[]{"sci",            1.5e-10},
            new Object[]{"string",         "hello"},
            new Object[]{"strT",           "T"},
            new Object[]{"strF",           "F"},
            new Object[]{"strTilde",       "~"},
            new Object[]{"strSpace",       "hello world"},
            new Object[]{"empty-obj",      new LinkedHashMap<>()},
            new Object[]{"empty-arr",      new ArrayList<>()},
            new Object[]{"simple-obj",     linked("a", 1L, "b", "foo")},
            new Object[]{"simple-arr",     List.of(1L, 2L, 3L)},
            new Object[]{"mixed-arr",      asList(1L, "hello", true, null)},
            new Object[]{"nested-obj",     linked("a", linked("b", linked("c", 1L)))},
            new Object[]{"arr-of-obj",     List.of(
                                               linked("a", 1L, "b", 2L),
                                               linked("a", 3L, "b", 4L))},
            new Object[]{"mixed-arr-obj",  List.of(linked("x", 1L, "y", List.of(1L, 2L)))},
            new Object[]{"B1",             B1_OBJ}
        );
    }

    @ParameterizedTest(name = "round-trip: {0}")
    @MethodSource("roundTripFixtures")
    void roundTrip(String label, Object value) {
        String serialized = Terse.serialize(value);
        Object back       = Terse.parse(serialized);
        assertEquals(value, back, "Round-trip failed for: " + label + " → " + serialized);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    @SafeVarargs
    static Map<String, Object> linked(Object... kv) {
        Map<String, Object> m = new LinkedHashMap<>();
        for (int i = 0; i < kv.length; i += 2) m.put((String) kv[i], kv[i + 1]);
        return m;
    }

    static List<Object> asList(Object... items) {
        return new ArrayList<>(Arrays.asList(items));
    }
}
