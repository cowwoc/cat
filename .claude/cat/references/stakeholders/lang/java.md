# Language Supplement: Java

Language-specific red flags and patterns for Java codebases.

## Performance Red Flags

Automatically flag these Java-specific patterns:

- **String concatenation in loops**: Using `+` instead of `StringBuilder`
  ```java
  // ❌ Creates new String object each iteration
  for (Item item : items) {
      result = result + item.toString();
  }
  // ✅ Use StringBuilder
  StringBuilder sb = new StringBuilder();
  for (Item item : items) {
      sb.append(item);
  }
  ```

- **Object creation in tight loops**: `new` keyword for immutable objects
  ```java
  // ❌ Creates unnecessary objects
  for (int i = 0; i < n; i++) {
      Integer boxed = new Integer(i);  // or Integer.valueOf in hot path
  }
  ```

- **Missing `final` on cacheable fields**: Fields that should be computed once
  ```java
  // ❌ Recomputed on each access
  public String getKey() {
      return prefix + ":" + suffix;
  }
  // ✅ Cache immutable result
  private final String key;
  ```

- **Synchronization in hot paths**: `synchronized` blocks or methods in frequently-called code

## Security Red Flags

- **SQL injection via string concatenation**:
  ```java
  // ❌ Vulnerable
  String query = "SELECT * FROM users WHERE id = " + userId;
  // ✅ Use PreparedStatement
  PreparedStatement ps = conn.prepareStatement("SELECT * FROM users WHERE id = ?");
  ```

- **Deserialization of untrusted data**: `ObjectInputStream.readObject()` on external input

- **Hardcoded credentials**: Strings containing "password", "secret", "apikey" in source

## Quality Red Flags

- **Checked exceptions swallowed silently**:
  ```java
  // ❌ Silent failure
  catch (IOException e) { }
  // ✅ At minimum log or rethrow
  catch (IOException e) { throw new UncheckedIOException(e); }
  ```

- **Raw types instead of generics**: `List` instead of `List<String>`

- **Mutable static fields**: `static` fields that aren't `final` (thread safety)

## Testing Red Flags

- **Tests using `Thread.sleep()`**: Flaky timing-dependent tests
- **Missing `@Test` annotation**: Test methods that won't be executed
- **`assertEquals` with floating point**: Should use delta parameter

## Architecture Red Flags

- **Circular package dependencies**: Package A imports B, B imports A
- **God classes**: Classes with 20+ methods or 500+ lines
- **Utility class anti-pattern**: Classes with only static methods that should be instance methods
