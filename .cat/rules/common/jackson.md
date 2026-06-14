---
paths: ["*.java"]
---
# Jackson Conventions

## JsonMapper Usage

- Use `JsonMapper` instead of `ObjectMapper` for JSON parsing
- Obtain the shared instance from `AgentScope.getJsonMapper()` — never call `JsonMapper.builder().build()` directly
- The shared instance is configured with pretty print (`SerializationFeature.INDENT_OUTPUT`)
- In production code, get the mapper from the scope passed to your class
- In tests, create a test scope and call `scope.getJsonMapper()`
- In CLI `main()` methods, create the appropriate engine scope and call `scope.getJsonMapper()`

## JsonNode API

- `JsonNode.asString()` never returns `null` when `JsonNode.isString()` is `true`. The node is a `TextNode` and
  `asString()` returns the string content directly. Do not add redundant `value != null` checks after an `isString()`
  guard.
