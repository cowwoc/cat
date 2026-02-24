# Plan: Create Config Property Enums

## Goal
Create Java enums for the cat-config.json properties `trust`, `verify`, `curiosity`, and `patience` to replace raw
string comparisons throughout the codebase.

## Satisfies
- None (infrastructure improvement)

## Risk Assessment
- **Risk Level:** LOW
- **Concerns:** Must update all existing string comparisons to use enums; existing `WorkPrepare.TrustLevel` needs
  consolidation
- **Mitigation:** Comprehensive grep for all property references before and after

## Files to Modify
- `client/src/main/java/io/github/cowwoc/cat/hooks/util/TrustLevel.java` - New top-level enum (move from WorkPrepare)
- `client/src/main/java/io/github/cowwoc/cat/hooks/util/VerifyLevel.java` - New enum: NONE, CHANGED, ALL
- `client/src/main/java/io/github/cowwoc/cat/hooks/util/CuriosityLevel.java` - New enum: LOW, MEDIUM, HIGH
- `client/src/main/java/io/github/cowwoc/cat/hooks/util/PatienceLevel.java` - New enum: LOW, MEDIUM, HIGH
- `client/src/main/java/io/github/cowwoc/cat/hooks/util/WorkPrepare.java` - Remove inner TrustLevel, use top-level enum
- `client/src/main/java/io/github/cowwoc/cat/hooks/Config.java` - Add typed getters (e.g., `getTrust()` returning
  `TrustLevel`)
- `client/src/main/java/io/github/cowwoc/cat/hooks/session/EnforceApprovalBeforeMerge.java` - Use TrustLevel enum
  instead of raw string
- `client/src/main/java/io/github/cowwoc/cat/hooks/output/GetConfigOutput.java` - Use enum getters

## Pre-conditions
- [ ] All dependent issues are closed

## Execution Steps
1. **Create TrustLevel enum:** Extract `WorkPrepare.TrustLevel` to a top-level
   `client/src/main/java/io/github/cowwoc/cat/hooks/util/TrustLevel.java` with values `LOW`, `MEDIUM`, `HIGH` and a
   `fromString()` method
2. **Create VerifyLevel enum:** New enum with values `NONE`, `CHANGED`, `ALL` and `fromString()`
3. **Create CuriosityLevel enum:** New enum with values `LOW`, `MEDIUM`, `HIGH` and `fromString()`
4. **Create PatienceLevel enum:** New enum with values `LOW`, `MEDIUM`, `HIGH` and `fromString()`
5. **Add typed getters to Config:** Add `getTrust()`, `getVerify()`, `getCuriosity()`, `getPatience()` methods that
   parse strings into enums
6. **Update WorkPrepare:** Remove inner `TrustLevel` class, use top-level enum
7. **Update EnforceApprovalBeforeMerge:** Replace `config.getString("trust", "medium")` with `config.getTrust()`
8. **Update GetConfigOutput:** Use typed getters where applicable
9. **Write tests:** Unit tests for each enum's `fromString()`, including invalid input handling
10. **Run all tests:** `mvn -f client/pom.xml test` must pass

## Post-conditions
- [ ] Four enum classes exist: TrustLevel, VerifyLevel, CuriosityLevel, PatienceLevel
- [ ] Each enum has a `fromString()` method that fails fast on invalid values
- [ ] Config class has typed getters for all four properties
- [ ] No raw string comparisons remain for these four properties in Java code
- [ ] WorkPrepare.TrustLevel inner class is removed
- [ ] All tests pass
