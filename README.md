# terse-java

[![CI](https://github.com/RudsonCarvalho/terse-java/actions/workflows/ci.yml/badge.svg)](https://github.com/RudsonCarvalho/terse-java/actions/workflows/ci.yml)

Java 11+ implementation of [TERSE](https://rudsoncarvalho.github.io/terse-format/) — Token-Efficient Recursive Serialization Encoding.

## Quick start

```java
// Serialize
Terse.serialize(null);              // "~"
Terse.serialize(true);             // "T"
Terse.serialize(42L);              // "42"
Terse.serialize("hello");          // "hello"
Terse.serialize("T");              // "\"T\""   ← reserved, so quoted
Terse.serialize(Map.of("a", 1L)); // "{a:1}"

// Parse
Terse.parse("~");                    // null
Terse.parse("T");                    // Boolean.TRUE
Terse.parse("{name:Alice age:30}");  // Map {"name":"Alice", "age":30L}
Terse.parse("[1 2 3]");              // List [1L, 2L, 3L]

// Document API (top-level key-value pairs, no outer braces)
Map<String, Object> doc = Terse.parseDocument("name: my-app\nport: 3000\n");
String src = Terse.serializeDocument(doc);
```

## TERSE vs JSON

| JSON | TERSE |
|------|-------|
| `{"name":"my-app","port":3000,"debug":false}` | `{name:my-app port:3000 debug:F}` |
| `[true, null, "hello"]` | `[T ~ hello]` |
| `[{"id":1,"role":"admin"},{"id":2,"role":"user"}]` | `#[id role]\n  1 admin\n  2 user` |

TERSE omits unnecessary quotes, braces, commas, and colons — reducing token count by 20–60% for typical payloads.

## Type mapping

| Java type | TERSE | Parsed back as |
|-----------|-------|----------------|
| `null` | `~` | `null` |
| `Boolean.TRUE` | `T` | `Boolean.TRUE` |
| `Boolean.FALSE` | `F` | `Boolean.FALSE` |
| `Long` / `Integer` | `42` | `Long` |
| `Double` / `Float` | `3.14` | `Double` |
| `String` (safe-id) | `hello` | `String` |
| `String` (unsafe) | `"hello world"` | `String` |
| `Map<?,?>` | `{k:v ...}` | `LinkedHashMap<String,Object>` |
| `List<?>` | `[v1 v2 ...]` | `ArrayList<Object>` |
| Uniform list of Maps | `#[f1 f2]\n  v1 v2` | `ArrayList<LinkedHashMap<String,Object>>` |

## Error handling

All methods throw `TerseException` (unchecked) on invalid input. The exception carries a machine-readable code:

```java
try {
    Terse.parse("\t42");
} catch (TerseException e) {
    e.getCode();     // "ILLEGAL_CHARACTER"
    e.getPosition(); // byte offset in source
    e.getMessage();  // human-readable description
}
```

Error codes: `ILLEGAL_CHARACTER`, `DUPLICATE_KEY`, `MAX_DEPTH_EXCEEDED`, `UNEXPECTED_TOKEN`, `UNEXPECTED_EOF`.

## Build

**Maven**
```bash
mvn test
mvn package   # produces target/terse-java-0.1.0.jar
```

**Gradle**
```bash
./gradlew test
./gradlew jar
```

Zero runtime dependencies — only JDK 11+ required.

## Spec

Full specification: [terse-format](https://github.com/RudsonCarvalho/terse-format)
Current version: **v0.5**

## License

MIT
