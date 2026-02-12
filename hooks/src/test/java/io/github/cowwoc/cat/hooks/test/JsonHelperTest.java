package io.github.cowwoc.cat.hooks.test;

import io.github.cowwoc.cat.hooks.JvmScope;
import io.github.cowwoc.cat.hooks.skills.JsonHelper;
import org.testng.annotations.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.util.List;

import static io.github.cowwoc.requirements13.java.DefaultJavaValidators.requireThat;

/**
 * Tests for JsonHelper utility methods.
 * <p>
 * Tests verify JSON extraction utilities handle various scenarios correctly.
 * <p>
 * Tests are designed for parallel execution - each test is self-contained
 * with no shared state.
 */
public class JsonHelperTest
{
  /**
   * Verifies that getStringOrDefault returns value when present.
   *
   * @throws IOException if JSON parsing fails
   */
  @Test
  public void getStringOrDefaultReturnsValueWhenPresent() throws IOException
  {
    try (JvmScope scope = new TestJvmScope())
    {
      JsonMapper mapper = scope.getJsonMapper();
      JsonNode node = mapper.readTree("{\"key\": \"value\"}");
      String result = JsonHelper.getStringOrDefault(node, "key", "default");
      requireThat(result, "result").isEqualTo("value");
    }
  }

  /**
   * Verifies that getStringOrDefault returns default when key is missing.
   *
   * @throws IOException if JSON parsing fails
   */
  @Test
  public void getStringOrDefaultReturnsDefaultWhenMissing() throws IOException
  {
    try (JvmScope scope = new TestJvmScope())
    {
      JsonMapper mapper = scope.getJsonMapper();
      JsonNode node = mapper.readTree("{}");
      String result = JsonHelper.getStringOrDefault(node, "key", "default");
      requireThat(result, "result").isEqualTo("default");
    }
  }

  /**
   * Verifies that getStringOrDefault returns default when value is empty.
   *
   * @throws IOException if JSON parsing fails
   */
  @Test
  public void getStringOrDefaultReturnsDefaultWhenEmpty() throws IOException
  {
    try (JvmScope scope = new TestJvmScope())
    {
      JsonMapper mapper = scope.getJsonMapper();
      JsonNode node = mapper.readTree("{\"key\": \"\"}");
      String result = JsonHelper.getStringOrDefault(node, "key", "default");
      requireThat(result, "result").isEqualTo("default");
    }
  }

  /**
   * Verifies that getIntOrDefault returns value when present.
   *
   * @throws IOException if JSON parsing fails
   */
  @Test
  public void getIntOrDefaultReturnsValueWhenPresent() throws IOException
  {
    try (JvmScope scope = new TestJvmScope())
    {
      JsonMapper mapper = scope.getJsonMapper();
      JsonNode node = mapper.readTree("{\"count\": 42}");
      int result = JsonHelper.getIntOrDefault(node, "count", 0);
      requireThat(result, "result").isEqualTo(42);
    }
  }

  /**
   * Verifies that getIntOrDefault returns default when key is missing.
   *
   * @throws IOException if JSON parsing fails
   */
  @Test
  public void getIntOrDefaultReturnsDefaultWhenMissing() throws IOException
  {
    try (JvmScope scope = new TestJvmScope())
    {
      JsonMapper mapper = scope.getJsonMapper();
      JsonNode node = mapper.readTree("{}");
      int result = JsonHelper.getIntOrDefault(node, "count", 10);
      requireThat(result, "result").isEqualTo(10);
    }
  }

  /**
   * Verifies that getIntOrDefault returns default when value is not a number.
   *
   * @throws IOException if JSON parsing fails
   */
  @Test
  public void getIntOrDefaultReturnsDefaultWhenNotNumber() throws IOException
  {
    try (JvmScope scope = new TestJvmScope())
    {
      JsonMapper mapper = scope.getJsonMapper();
      JsonNode node = mapper.readTree("{\"count\": \"text\"}");
      int result = JsonHelper.getIntOrDefault(node, "count", 10);
      requireThat(result, "result").isEqualTo(10);
    }
  }

  /**
   * Verifies that getArray returns list when array is present.
   *
   * @throws IOException if JSON parsing fails
   */
  @Test
  public void getArrayReturnsListWhenPresent() throws IOException
  {
    try (JvmScope scope = new TestJvmScope())
    {
      JsonMapper mapper = scope.getJsonMapper();
      JsonNode node = mapper.readTree("{\"items\": [\"a\", \"b\", \"c\"]}");
      List<JsonNode> result = JsonHelper.getArray(node, "items");
      requireThat(result, "result").size().isEqualTo(3);
    }
  }

  /**
   * Verifies that getArray returns empty list when key is missing.
   *
   * @throws IOException if JSON parsing fails
   */
  @Test
  public void getArrayReturnsEmptyListWhenMissing() throws IOException
  {
    try (JvmScope scope = new TestJvmScope())
    {
      JsonMapper mapper = scope.getJsonMapper();
      JsonNode node = mapper.readTree("{}");
      List<JsonNode> result = JsonHelper.getArray(node, "items");
      requireThat(result, "result").isEmpty();
    }
  }

  /**
   * Verifies that getArray returns empty list when value is not an array.
   *
   * @throws IOException if JSON parsing fails
   */
  @Test
  public void getArrayReturnsEmptyListWhenNotArray() throws IOException
  {
    try (JvmScope scope = new TestJvmScope())
    {
      JsonMapper mapper = scope.getJsonMapper();
      JsonNode node = mapper.readTree("{\"items\": \"text\"}");
      List<JsonNode> result = JsonHelper.getArray(node, "items");
      requireThat(result, "result").isEmpty();
    }
  }

  /**
   * Verifies that getStringArray returns string list when array is present.
   *
   * @throws IOException if JSON parsing fails
   */
  @Test
  public void getStringArrayReturnsListWhenPresent() throws IOException
  {
    try (JvmScope scope = new TestJvmScope())
    {
      JsonMapper mapper = scope.getJsonMapper();
      JsonNode node = mapper.readTree("{\"tags\": [\"tag1\", \"tag2\"]}");
      List<String> result = JsonHelper.getStringArray(node, "tags");
      requireThat(result, "result").size().isEqualTo(2);
      requireThat(result.get(0), "firstTag").isEqualTo("tag1");
      requireThat(result.get(1), "secondTag").isEqualTo("tag2");
    }
  }

  /**
   * Verifies that getStringArray returns empty list when key is missing.
   *
   * @throws IOException if JSON parsing fails
   */
  @Test
  public void getStringArrayReturnsEmptyListWhenMissing() throws IOException
  {
    try (JvmScope scope = new TestJvmScope())
    {
      JsonMapper mapper = scope.getJsonMapper();
      JsonNode node = mapper.readTree("{}");
      List<String> result = JsonHelper.getStringArray(node, "tags");
      requireThat(result, "result").isEmpty();
    }
  }

  /**
   * Verifies that getStringArray skips non-string items.
   *
   * @throws IOException if JSON parsing fails
   */
  @Test
  public void getStringArraySkipsNonStringItems() throws IOException
  {
    try (JvmScope scope = new TestJvmScope())
    {
      JsonMapper mapper = scope.getJsonMapper();
      JsonNode node = mapper.readTree("{\"mixed\": [\"text\", 123, \"more\"]}");
      List<String> result = JsonHelper.getStringArray(node, "mixed");
      requireThat(result, "result").size().isEqualTo(2);
      requireThat(result.get(0), "firstItem").isEqualTo("text");
      requireThat(result.get(1), "secondItem").isEqualTo("more");
    }
  }

  // --- Null validation tests ---

  /**
   * Verifies that getStringOrDefault with null node throws NullPointerException.
   */
  @Test(expectedExceptions = NullPointerException.class)
  public void getStringOrDefaultWithNullNodeThrows()
  {
    JsonHelper.getStringOrDefault(null, "key", "default");
  }

  /**
   * Verifies that getStringOrDefault with null key throws NullPointerException.
   *
   * @throws IOException if JSON parsing fails
   */
  @Test(expectedExceptions = NullPointerException.class)
  public void getStringOrDefaultWithNullKeyThrows() throws IOException
  {
    try (JvmScope scope = new TestJvmScope())
    {
      JsonMapper mapper = scope.getJsonMapper();
      JsonNode node = mapper.readTree("{}");
      JsonHelper.getStringOrDefault(node, null, "default");
    }
  }

  /**
   * Verifies that getIntOrDefault with null node throws NullPointerException.
   */
  @Test(expectedExceptions = NullPointerException.class)
  public void getIntOrDefaultWithNullNodeThrows()
  {
    JsonHelper.getIntOrDefault(null, "key", 0);
  }

  /**
   * Verifies that getIntOrDefault with null key throws NullPointerException.
   *
   * @throws IOException if JSON parsing fails
   */
  @Test(expectedExceptions = NullPointerException.class)
  public void getIntOrDefaultWithNullKeyThrows() throws IOException
  {
    try (JvmScope scope = new TestJvmScope())
    {
      JsonMapper mapper = scope.getJsonMapper();
      JsonNode node = mapper.readTree("{}");
      JsonHelper.getIntOrDefault(node, null, 0);
    }
  }

  /**
   * Verifies that getArray with null node throws NullPointerException.
   */
  @Test(expectedExceptions = NullPointerException.class)
  public void getArrayWithNullNodeThrows()
  {
    JsonHelper.getArray(null, "key");
  }

  /**
   * Verifies that getArray with null key throws NullPointerException.
   *
   * @throws IOException if JSON parsing fails
   */
  @Test(expectedExceptions = NullPointerException.class)
  public void getArrayWithNullKeyThrows() throws IOException
  {
    try (JvmScope scope = new TestJvmScope())
    {
      JsonMapper mapper = scope.getJsonMapper();
      JsonNode node = mapper.readTree("{}");
      JsonHelper.getArray(node, null);
    }
  }

  /**
   * Verifies that getStringArray with null node throws NullPointerException.
   */
  @Test(expectedExceptions = NullPointerException.class)
  public void getStringArrayWithNullNodeThrows()
  {
    JsonHelper.getStringArray(null, "key");
  }

  /**
   * Verifies that getStringArray with null key throws NullPointerException.
   *
   * @throws IOException if JSON parsing fails
   */
  @Test(expectedExceptions = NullPointerException.class)
  public void getStringArrayWithNullKeyThrows() throws IOException
  {
    try (JvmScope scope = new TestJvmScope())
    {
      JsonMapper mapper = scope.getJsonMapper();
      JsonNode node = mapper.readTree("{}");
      JsonHelper.getStringArray(node, null);
    }
  }
}
