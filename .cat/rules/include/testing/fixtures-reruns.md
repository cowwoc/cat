## Testing

### Test Fixture Files

Pre-recorded data files used to make tests deterministic belong in a `fixtures/` subdirectory of the test
directory, not in the test directory root.

**Correct:**
```
plugin/tests/agents/instruction-grader-agent/
  fixtures/
    negative_1_runner.json
    req_grader_schema_runner.json
  negative_1.md
  req_grader_schema.md
```

**Incorrect:**
```
plugin/tests/agents/instruction-grader-agent/
  negative_1_runner.json        ← should be under fixtures/
  negative_1.md
```

---

- Java: TestNG for unit tests
- Bash: Bats (Bash Automated Testing System)
- Minimum coverage: 80% for business logic
- All edge cases must have tests

### No Redundant Builds

**Do not re-run a build or test suite if no source files changed since the last successful run.** A passing
build remains valid until files are modified. Re-running an unchanged build wastes time and adds noise to the
session.

**When a re-run IS required:**
- Any tracked source file was added, modified, or deleted since the last successful build
- The build tool configuration changed (e.g., `pom.xml`, `build.gradle`, `Makefile`)
- An external dependency changed (e.g., a dependency was upgraded)

**When a re-run is NOT required:**
- Only documentation, comments, or non-source files changed (e.g., `.md`, `.txt`)
- Only planning artifacts or engine-loaded project instruction files changed
- The last build passed and nothing has been committed or staged since
