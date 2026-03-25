# Plan

## Goal

Increase surefire parallel threadCount to 8 per core to speed up test execution.

## Pre-conditions

(none)

## Post-conditions

- [ ] `<threadCount>8</threadCount>` is set in `client/pom.xml` surefire configuration
- [ ] All tests pass with the new thread count (no functionality regression)
- [ ] E2E verification: run full test suite and confirm all 2722+ tests pass with the new parallel configuration

## Research Findings

Current surefire configuration in `client/pom.xml` (lines 146-163):
- Plugin: `maven-surefire-plugin` version 3.5.4
- Has `<parallel>methods</parallel>` already set
- No `<threadCount>` or `<perCoreThreadCount>` elements exist yet
- The `<configuration>` block also contains `<argLine>`, `<environmentVariables>`, and `<properties>` elements

The two new elements (`<threadCount>8</threadCount>` and `<perCoreThreadCount>true</perCoreThreadCount>`) should be
added inside the existing `<configuration>` block, adjacent to the existing `<parallel>methods</parallel>` element.

## Sub-Agent Waves

### Wave 1

1. Edit `client/pom.xml`: Inside the `maven-surefire-plugin` `<configuration>` block, add `<threadCount>8</threadCount>` and `<perCoreThreadCount>true</perCoreThreadCount>` immediately after the existing `<parallel>methods</parallel>` line (line 154). The result should look like:
   ```xml
   <parallel>methods</parallel>
   <threadCount>8</threadCount>
   <perCoreThreadCount>true</perCoreThreadCount>
   ```

2. Update `index.json`: Set `"status": "closed"` and `"progress": 100`.

3. Run the full test suite to verify no regressions: `mvn -f client/pom.xml test`

4. Commit all changes with message: `performance: increase surefire parallel threadCount to 8 per core`

## Commit Type

`performance:`
