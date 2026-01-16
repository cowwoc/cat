---
name: write-and-commit
description: Create file and commit atomically (60-75% faster than step-by-step)
allowed-tools: Bash, Write
---

# Write and Commit Skill

**Purpose**: Create a new file and commit it in a single atomic operation, reducing LLM round-trips from 4-5 to 2-3.

**Performance**: 60-75% faster than traditional workflow

## When to Use This Skill

### ✅ Use write-and-commit When:

- Creating a **new file** that will be committed immediately
- File content is **ready and complete** (no iterative edits needed)
- You want to **save time** on routine file creation
- Creating **scripts** that need executable permissions
- Adding **configuration files** or **documentation**
- Creating **test files** or **example code**

### ❌ Do NOT Use When:

- **Editing existing files** (use Edit tool instead)
- File content is **complex and may need iteration**
- Creating multiple related files that should be in **one commit together**
- File is **part of larger refactoring** (commit all changes together)
- You need to **review the file** before committing

## Performance Comparison

### Traditional Workflow (4-5 LLM round-trips, 20-30s)

```
[LLM Round 1] Write file
  → Write tool: Create file with content

[LLM Round 2] Make executable (if needed)
  → Bash: chmod +x file.sh

[LLM Round 3] Stage file
  → Bash: git add file.sh

[LLM Round 4] Commit file
  → Bash: git commit -m "Add file"

[LLM Round 5] Report success
  → Report commit SHA to user
```

**Total**: 20-30 seconds, 4-5 LLM round-trips

### Optimized Workflow (2-3 LLM round-trips, 5-8s)

```
[LLM Round 1] Change and execute
  → Write content to temp file
  → Write commit message to temp file
  → Bash: write-and-commit.sh file.sh /tmp/content /tmp/msg --executable

[LLM Round 2] Report success
  → Parse JSON result
  → Report commit SHA to user
```

**Total**: 5-8 seconds, 2-3 LLM round-trips

**Savings**: 60-75% faster

## Usage

### Basic File Creation

```bash
# Step 1: Prepare content and commit message (in LLM)
cat > /tmp/content-$$.txt <<'EOF'
File content goes here...
EOF

cat > /tmp/commit-msg-$$.txt <<'EOF'
Add new feature file

Description of what this file does.

EOF

# Step 2: Execute atomic creation
~/.claude/scripts/write-and-commit.sh \
  "path/to/file.txt" \
  "/tmp/content-$$.txt" \
  "/tmp/commit-msg-$$.txt"
```

### Executable Script Creation

```bash
# For scripts that need executable permissions
~/.claude/scripts/write-and-commit.sh \
  ".claude/scripts/my-script.sh" \
  "/tmp/content-$$.txt" \
  "/tmp/commit-msg-$$.txt" \
  --executable
```

## Output Format

Script returns JSON:

```json
{
  "status": "success",
  "message": "File created and committed successfully",
  "duration_seconds": 2,
  "file_path": "path/to/file.txt",
  "executable": false,
  "commit_sha": "abc123def456",
  "working_directory": "/path/to/project",
  "timestamp": "2025-11-08T00:15:30-05:00"
}
```

## LLM Integration Pattern

### Example: User asks to create a new utility script

**User**: Create a script to clean up temporary files

**LLM Round 1** (Planning + Execution):
```bash
# Draft script content
cat > /tmp/cleanup-script-$$.sh <<'SCRIPT'
#!/bin/bash
# Clean up temporary files
find /tmp -name "*.tmp" -mtime +7 -delete
SCRIPT

# Draft commit message
cat > /tmp/commit-msg-$$.txt <<'MSG'
Add temporary file cleanup script

Removes .tmp files older than 7 days from /tmp directory.
MSG

# Execute atomic creation
~/.claude/scripts/write-and-commit.sh \
  ".claude/scripts/cleanup-temp.sh" \
  "/tmp/cleanup-script-$$.sh" \
  "/tmp/commit-msg-$$.txt" \
  --executable
```

**Script Output**:
```json
{
  "status": "success",
  "commit_sha": "3f8a920",
  "duration_seconds": 2,
  ...
}
```

**LLM Round 2** (Verification):
```
Created cleanup script successfully!
- File: .claude/scripts/cleanup-temp.sh
- Executable: Yes
- Commit: 3f8a920
- Duration: 2 seconds
```

## Safety Features

### Precondition Validation

- ✅ Verifies we're in a git repository
- ✅ Creates parent directories if needed
- ✅ Warns if overwriting existing file

### Atomic Operation

- ✅ File creation and commit happen together
- ✅ If commit fails, file still exists (staged)
- ✅ Can be reverted with standard git commands

### Error Handling

On any error, script:
- Exits immediately with clear error message
- Returns JSON with error status
- Leaves repository in clean state (file may be staged)

**Recovery**: If script fails after file creation but before commit:
```bash
# File exists but not committed
git reset HEAD <file>  # Unstage
rm <file>              # Remove if unwanted
```

## Common Use Cases

### 1. Create Configuration File

```bash
# Creating .gitignore
cat > /tmp/content.txt <<'EOF'
*.tmp
*.log
build/
EOF

write-and-commit.sh ".gitignore" /tmp/content.txt /tmp/msg.txt
```

### 2. Create Documentation

```bash
# Creating README for a subdirectory
cat > /tmp/content.txt <<'EOF'
# Component Documentation
...
EOF

write-and-commit.sh "docs/component/README.md" /tmp/content.txt /tmp/msg.txt
```

### 3. Create Test File

```bash
# Creating a test file
cat > /tmp/content.txt <<'EOF'
public class MyTest {
  @Test
  public void testFeature() {
    // Test implementation
  }
}
EOF

write-and-commit.sh "src/test/java/MyTest.java" /tmp/content.txt /tmp/msg.txt
```

### 4. Create Executable Script

```bash
# Creating a hook script
cat > /tmp/content.txt <<'EOF'
#!/bin/bash
# Hook implementation
EOF

write-and-commit.sh ".claude/hooks/my-hook.sh" /tmp/content.txt /tmp/msg.txt --executable
```

## When NOT to Use

### Multiple Related Files

**❌ Wrong**: Create files one-by-one with write-and-commit
```bash
write-and-commit.sh file1.txt /tmp/content1 /tmp/msg1
write-and-commit.sh file2.txt /tmp/content2 /tmp/msg2
write-and-commit.sh file3.txt /tmp/content3 /tmp/msg3
# Result: 3 separate commits
```

**✅ Correct**: Use traditional workflow, commit together
```bash
Write file1.txt
Write file2.txt
Write file3.txt
git add file1.txt file2.txt file3.txt
git commit -m "Add related files"
# Result: 1 commit with all files
```

### Files Needing Review

**❌ Wrong**: Commit immediately if unsure about content
**✅ Correct**: Create with Write tool, review, then commit manually

## Frequency and Impact

**Expected Usage**: 10-20 times per day

**Time Savings per Use**: ~15 seconds

**Daily Impact**: 150-300 seconds (2.5-5 minutes)

**Monthly Impact**: 50-100 minutes (almost 2 hours)

## Related

- **Write tool**: For creating files without immediate commit
- **Edit tool**: For modifying existing files
- **git-squash skill**: For combining commits after creation
- **CLAUDE.md § Backup-Verify-Cleanup Pattern**: Git operation best practices
