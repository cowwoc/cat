# Plan: centralize-json-mapper

## Goal
Centralize JsonMapper construction in JvmScope instead of each file creating its own instance.
Configure it with pretty print output for readable JSON.

## Satisfies
None - infrastructure/tech debt

## Risk Assessment
- **Risk Level:** MEDIUM
- **Concerns:** 23 files currently create their own JsonMapper; changing serialization format (pretty print) may
  affect JSON consumers that parse output
- **Mitigation:** Pretty print does not change JSON semantics, only formatting. Verify all tests pass after changes.

## Files to Modify
- `hooks/src/main/java/io/github/cowwoc/cat/hooks/JvmScope.java` - Add `getJsonMapper()` method
- `hooks/src/main/java/io/github/cowwoc/cat/hooks/MainJvmScope.java` - Implement lazy-loaded JsonMapper singleton
  with pretty print enabled
- 23 files that call `JsonMapper.builder().build()` - Replace with `scope.getJsonMapper()` or accept JsonMapper
  as parameter
- `.claude/cat/conventions/java.md` - Update JsonMapper convention to reference JvmScope

## Acceptance Criteria
- [ ] JvmScope provides a shared JsonMapper instance configured with pretty print
- [ ] No file in the codebase calls `JsonMapper.builder().build()` directly
- [ ] All JSON output is pretty-printed
- [ ] All tests pass (`mvn -f hooks/pom.xml verify`)

## Execution Steps
1. Add `getJsonMapper()` to `JvmScope` interface
2. Implement in `MainJvmScope` with `SerializationFeature.INDENT_OUTPUT` enabled
3. Update all 23 files to use the shared mapper from JvmScope (or accept it as a constructor/method parameter)
4. For utility classes without JvmScope access (static methods, records), accept JsonMapper as parameter
5. Update Java conventions to document the new pattern
6. Run `mvn -f hooks/pom.xml verify`

## Success Criteria
- [ ] Zero occurrences of `JsonMapper.builder().build()` in codebase
- [ ] All JSON output is pretty-printed
- [ ] All tests pass
