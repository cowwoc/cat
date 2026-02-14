<!--
Copyright (c) 2026 Gili Tzabari. All rights reserved.
Licensed under the CAT Commercial License.
See LICENSE.md in the project root for license terms.
-->
# Batch Read Skill

**Purpose**: Find files matching a pattern and read them in a single atomic operation, reducing LLM round-trips from 1+N
to 2-3. Similar to wave-based parallel execution in delegate, batch-read groups related files for efficient processing,
though it executes within a single context rather than spawning subagents.

**Performance**: 50-70% faster for reading 3+ files during codebase exploration

## When to Use This Skill

### Use batch-read When:

- **Exploring codebase** to understand how a feature works
- **Finding examples** of a pattern across multiple files
- **Gathering context** for a code change
- **Reviewing implementations** of similar functionality
- **Understanding usage** of a particular API or method
- Reading **related configuration files**
- Collecting **test examples** from multiple test files

### Do NOT Use When:

- Reading **specific known files** (use Read tool directly)
- Need to read **entire large files** (>1000 lines each)
- Files are **unrelated** (no common pattern)
- Need **precise file selection** (specific paths known)
- Reading **binary files** or **generated code**
- Files require **deep analysis** (better to read individually)

## Performance Comparison

### Traditional Workflow (1+N LLM round-trips, 10s + 5s*N)

```
[LLM Round 1] Search for pattern
  -> Grep: Find files containing "FormattingRule"
  -> Returns: file1.java, file2.java, file3.java

[LLM Round 2] Read first file
  -> Read: file1.java

[LLM Round 3] Read second file
  -> Read: file2.java

[LLM Round 4] Read third file
  -> Read: file3.java

[LLM Round 5] Analyze and report
  -> Summarize findings from all files
```

**Total**: 10s + (5s * 3) = 25s, 5 LLM round-trips

### Optimized Workflow (2-3 LLM round-trips, 8-12s)

```
[LLM Round 1] Execute batch-read
  -> Bash: batch-read.sh "FormattingRule" --max-files 3
  -> [Script finds files + reads all + returns content]

[LLM Round 2] Analyze and report
  -> Parse combined output
  -> Summarize findings
```

**Total**: 8-12s, 2-3 LLM round-trips

**Savings**: 50-70% faster for N>=3 files

## Usage

This skill uses inline Bash commands - no external script required.

### Basic Pattern Search

```bash
# Find and read files containing "FormattingRule"
# Step 1: Find files
FILES=$(grep -rl "FormattingRule" . --include="*.java" 2>/dev/null | head -5)
# Step 2: Read each file with line numbers
for f in $FILES; do echo "=== $f ==="; cat -n "$f" | head -100; done
```

### With File Type Filter

```bash
# Only search Java files
grep -rl "FormattingRule" . --include="*.java" 2>/dev/null | head -5
```

### Limit Results

```bash
# Read at most 3 files
FILES=$(grep -rl "FormattingRule" . --include="*.java" 2>/dev/null | head -3)
for f in $FILES; do echo "=== $f ==="; cat -n "$f" | head -100; done
```

### Control Output Size

```bash
# Show only first 50 lines of each file
FILES=$(grep -rl "FormattingRule" . --include="*.java" 2>/dev/null | head -5)
for f in $FILES; do echo "=== $f ==="; cat -n "$f" | head -50; done
```

### Read Entire Files

```bash
# Read complete files (no truncation)
FILES=$(grep -rl "FormattingRule" . --include="*.java" 2>/dev/null | head -5)
for f in $FILES; do echo "=== $f ==="; cat -n "$f"; done
```

### Combined Options

```bash
# Search Java files, read 5 files, show 100 lines each
FILES=$(grep -rl "FormattingRule" . --include="*.java" 2>/dev/null | head -5)
for f in $FILES; do echo "=== $f ==="; cat -n "$f" | head -100; echo "---"; done
```

## Output Format

The inline bash commands produce combined file contents:

```
===============================================================
FILE: src/main/java/FormattingRule.java
===============================================================

     1  package io.github.cowwoc.styler;
     2
     3  public interface FormattingRule {
     4      void apply(StyleContext context);
     5  }

[... file content ...]

---------------------------------------------------------------

===============================================================
FILE: src/test/java/FormattingRuleTest.java
===============================================================

     1  package io.github.cowwoc.styler;
     2
     3  import org.testng.annotations.Test;
     4
     5  public class FormattingRuleTest {
     6      @Test
     7      public void testRule() {
     8          // Test implementation
     9      }
    10  }

[... file content ...]

---------------------------------------------------------------
```

## Common Use Cases

### 1. Understanding Feature Implementation

```bash
# Find all files implementing a specific feature
FILES=$(grep -rl "ValidationEngine" . --include="*.java" 2>/dev/null | head -5)
for f in $FILES; do echo "=== $f ==="; cat -n "$f" | head -100; done
```

### 2. Reviewing Test Coverage

```bash
# Find all test files for a component
FILES=$(grep -rl "FormatterTest" . --include="*.java" 2>/dev/null | head -5)
for f in $FILES; do echo "=== $f ==="; cat -n "$f" | head -100; done
```

### 3. Finding Usage Examples

```bash
# See how an API is used across the codebase
FILES=$(grep -rl "StyleContext.apply" . --include="*.java" 2>/dev/null | head -5)
for f in $FILES; do echo "=== $f ==="; cat -n "$f" | head -100; done
```

### 4. Configuration Review

```bash
# Review all configuration files
find . -name "*.properties" -o -name "*.yml" 2>/dev/null | head -10 | while read f; do
  echo "=== $f ==="; cat -n "$f"; done
```

### 5. Documentation Gathering

```bash
# Collect all README files
find . -name "README*.md" 2>/dev/null | head -20 | while read f; do
  echo "=== $f ==="; cat -n "$f"; done
```

## Smart Filtering Features

### Automatic Deduplication

Using `grep -l` (list files only) avoids duplicate results from multiple matches in same file.

### Line Number Preservation

Using `cat -n` includes line numbers to help locate code:
```
     42  public void validate() {
     43      // Implementation
     44  }
```

### Truncation Indication

Use `head -N` to limit output lines per file:
```bash
cat -n "$f" | head -100  # First 100 lines only
```

### Size Warnings

For large codebases, limit file count and lines per file:
```bash
# Limit to 5 files, 50 lines each
FILES=$(grep -rl "pattern" . --include="*.java" 2>/dev/null | head -5)
for f in $FILES; do cat -n "$f" | head -50; done
```

## Performance Characteristics

### Time Savings by File Count

| Files | Traditional | Optimized | Savings |
|-------|-------------|-----------|---------|
| 1 file | 15s | 10s | 33% |
| 2 files | 20s | 10s | 50% |
| 3 files | 25s | 11s | 56% |
| 5 files | 35s | 12s | 66% |
| 10 files | 60s | 15s | 75% |

### Frequency and Impact

**Expected Usage**: 5-10 times per day

**Time Savings per Use**: ~15-30 seconds (average 3-5 files)

**Daily Impact**: 75-300 seconds (1.25-5 minutes)

**Monthly Impact**: 30-150 minutes (0.5-2.5 hours)

## Limitations

### File Size Limits

- Default: 100 lines per file
- Can read entire files with `--context-lines 0`
- Large files (>1000 lines) may produce too much output

### Pattern Matching

- Uses grep regex (not fuzzy matching)
- Case-sensitive by default
- Searches file contents, not file names

### File Type Detection

- `--type` filter uses file extension only
- Example: `--type java` matches `*.java` files
- Does not inspect file contents for type detection

## When NOT to Use

### Known Specific Files

**Wrong**: Use batch-read to find and read one known file
```bash
grep -rl "MyClass" . | xargs cat  # Overkill for one file
```

**Correct**: Use Read tool directly
```
Read: /path/to/project/src/main/java/MyClass.java
```

### Unrelated Files

**Wrong**: Read random files that happen to match pattern
```bash
grep -rl "test" .  # Too generic, matches everything
```

**Correct**: Use specific pattern or file type
```bash
grep -rl "ValidationTest" . --include="*.java" | head -5
```

### Deep Analysis Needed

**Wrong**: Read 10 large files for detailed analysis
**Correct**: Read files one-by-one for thorough review

## Related

- **Read tool**: For reading specific known files
- **Grep tool**: For finding files without reading them
- **Glob tool**: For finding files by pattern (name-based)
