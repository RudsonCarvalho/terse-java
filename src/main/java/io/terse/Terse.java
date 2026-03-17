package io.terse;

import java.util.Map;

/**
 * Public API for the TERSE (Token-Efficient Recursive Serialization Encoding) format.
 *
 * <h2>Quick start</h2>
 * <pre>{@code
 * // Serialize
 * Terse.serialize(null);              // "~"
 * Terse.serialize(true);             // "T"
 * Terse.serialize(42L);              // "42"
 * Terse.serialize("hello");          // "hello"
 * Terse.serialize("T");              // "\"T\""
 * Terse.serialize(Map.of("a", 1L));  // "{a:1}"
 *
 * // Parse
 * Terse.parse("~");                    // null
 * Terse.parse("T");                    // Boolean.TRUE
 * Terse.parse("{name:Alice age:30}");  // Map {"name":"Alice","age":30L}
 * Terse.parse("[1 2 3]");              // List [1L, 2L, 3L]
 *
 * // Document API (top-level key-value pairs, no outer braces)
 * Map<String,Object> doc = Terse.parseDocument("name: my-app\nversion: \"1.0.0\"\n");
 * String src = Terse.serializeDocument(doc);
 * }</pre>
 *
 * <h2>Error handling</h2>
 * All methods throw {@link TerseException} (unchecked) on invalid input.
 */
public final class Terse {

    private Terse() {}

    /**
     * Serializes any Java value to a TERSE string.
     *
     * @param value null, Boolean, Long, Integer, Double, Float, String,
     *              {@code List<?>}, or {@code Map<?,?>}
     * @return TERSE representation
     * @throws TerseException if the value type is unsupported or nesting exceeds 64 levels
     */
    public static String serialize(Object value) {
        return TerseSerializer.serialize(value);
    }

    /**
     * Serializes a {@code Map} as a TERSE <em>document</em> — top-level key-value pairs
     * with no enclosing braces.
     *
     * @param obj the map to serialize (must not be null)
     * @return multi-line TERSE document string
     */
    public static String serializeDocument(Map<?, ?> obj) {
        return TerseSerializer.serializeDocument(obj);
    }

    /**
     * Parses a TERSE <em>value</em> string.
     *
     * @param src the TERSE input (may be null-terminated or padded with whitespace)
     * @return parsed value: null, Boolean, Long, Double, String, List, or Map
     * @throws TerseException on malformed input
     */
    public static Object parse(String src) {
        return new TerseParser(src).parse();
    }

    /**
     * Parses a TERSE <em>document</em> — a sequence of top-level key-value pairs.
     *
     * @param src the TERSE document string
     * @return {@code LinkedHashMap<String,Object>} with parsed key-value pairs
     * @throws TerseException on malformed input or duplicate keys
     */
    @SuppressWarnings("unchecked")
    public static Map<String, Object> parseDocument(String src) {
        return new TerseParser(src).parseDocument();
    }
}
