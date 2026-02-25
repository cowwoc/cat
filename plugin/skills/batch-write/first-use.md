<!--
Copyright (c) 2026 Gili Tzabari. All rights reserved.
Licensed under the CAT Commercial License.
See LICENSE.md in the project root for license terms.
-->
# Batch Write Skill

**Purpose**: Issue multiple Write/Edit tool calls in a single LLM response when modifying independent files, reducing
round-trips from N to 1. Similar to how batch-read groups reads, batch-write groups writes for efficient parallel
execution within a single response.

**Performance**: 50-70% faster for writing 3+ independent files

## When to Use This Skill

### Use batch-write When:

- **Creating multiple new files** that do not depend on each other
- **Editing multiple existing files** with unrelated changes
- **Applying a refactor** that touches several independent files
- **Updating configuration files** across the project simultaneously
- **Adding tests** for multiple independent components at once
- **Scaffolding a feature** with multiple new files (handler, test, config, etc.)
- Writing **related but independent** files in a single pass

### Do NOT Use When:

- Writing a **single file** (no batching benefit)

## Performance Comparison

### Traditional Workflow (N LLM round-trips, 10s * N)

```
[LLM Round 1] Write file1.java
  -> Write: src/main/java/Foo.java
  -> Returns: success

[LLM Round 2] Write file2.java
  -> Write: src/main/java/Bar.java
  -> Returns: success

[LLM Round 3] Write file3.java
  -> Write: src/test/java/FooTest.java
  -> Returns: success

[LLM Round 4] Analyze and report
  -> Summarize changes made
```

**Total**: 10s * 3 = 30s, 4 LLM round-trips

### Optimized Workflow (1 LLM round-trip for all writes)

```
[LLM Round 1] Write all files in one response
  -> Write: src/main/java/Foo.java
  -> Write: src/main/java/Bar.java
  -> Write: src/test/java/FooTest.java
  -> [All three tool calls execute in parallel]

[LLM Round 2] Analyze and report
  -> Summarize changes made
```

**Total**: ~12s, 2 LLM round-trips

**Savings**: 50-70% faster for N>=3 independent files

## Usage Patterns

### Pattern 1: Multiple Write Calls in One Response (New Files)

Issue all Write tool calls in the same response when the files are independent:

```
[Single LLM response]:
  Write: plugin/skills/my-skill/SKILL.md      <- new file
  Write: plugin/skills/my-skill/first-use.md  <- new file (independent)
  Write: tests/my-skill-test.md               <- new file (independent)
```

All three writes execute concurrently. The LLM does not need to wait for each to complete before issuing the next.

### Pattern 2: Multiple Edit Calls in One Response (Existing Files)

When applying the same refactor to several files, batch the edits:

```
[Single LLM response]:
  Edit: src/main/java/Foo.java   <- rename method
  Edit: src/main/java/Bar.java   <- rename same method
  Edit: src/main/java/Baz.java   <- rename same method
```

Each Edit is independent — no file depends on the result of another edit.

### Pattern 3: Mixed Write + Edit in One Response

Combine new file creation with edits to existing files:

```
[Single LLM response]:
  Write: src/main/java/NewFeature.java         <- create new file
  Edit: src/main/java/ExistingRegistry.java    <- register new feature
  Edit: src/test/java/RegistryTest.java        <- add test case
```

### Pattern 4: Bash Heredoc Approach (Multiple Files via Single Bash Call)

For simple file creation, a single Bash call with heredocs can create multiple files atomically:

```bash
# Create multiple config files in one Bash call
cat > config/database.yml << 'EOF'
host: localhost
port: 5432
EOF

cat > config/cache.yml << 'EOF'
host: localhost
port: 6379
EOF

cat > config/app.yml << 'EOF'
debug: false
port: 8080
EOF
```

This approach is best for simple text files where inline content is straightforward. For complex files with special
characters or binary content, prefer individual Write tool calls.

## Error Handling

Tool calls within a single response are **independent** — if one Write or Edit fails, the others still execute and
succeed. Failures are reported per-tool-call, not for the entire batch.

**Example scenario:**

```
[Single LLM response]:
  Write: src/main/java/Foo.java   <- succeeds
  Write: /read-only/Bar.java      <- fails (permission denied)
  Write: src/test/java/Test.java  <- succeeds
```

Result: `Foo.java` and `Test.java` are written successfully. `Bar.java` fails. The LLM receives individual results
for each and can retry only the failed write.

**Recommended approach:**
- Review results from the batch response
- Retry only the failed tool calls individually
- Do not re-issue successful writes

## Performance Characteristics

### Time Savings by File Count

| Files | Traditional | Optimized | Savings |
|-------|-------------|-----------|---------|
| 1 file | 10s | 10s | 0% |
| 2 files | 20s | 11s | 45% |
| 3 files | 30s | 12s | 60% |
| 5 files | 50s | 13s | 74% |
| 10 files | 100s | 16s | 84% |

### Frequency and Impact

**Expected Usage**: 5-10 times per day

**Time Savings per Use**: ~15-30 seconds (average 3-5 files)

**Daily Impact**: 75-300 seconds (1.25-5 minutes)

**Monthly Impact**: 30-150 minutes (0.5-2.5 hours)

## Related

- **batch-read skill**: For reading 3+ related files in a batch operation
- **Write tool**: For writing individual files
- **Edit tool**: For editing individual existing files
- **Bash tool**: For multi-file creation via heredocs in a single call
