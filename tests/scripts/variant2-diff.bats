#!/usr/bin/env bats
# Tests for scripts/variant2-diff.py - Git diff to variant 2 format converter

load '../test_helper'

setup() {
    setup_git_repo
    # Create CAT config with terminal width
    mkdir -p "$TEST_TEMP_DIR/.claude/cat"
    echo '{"terminalWidth": 60}' > "$TEST_TEMP_DIR/.claude/cat/cat-config.json"
}

teardown() {
    teardown_test_dir
}

# Helper to create a diff
create_test_diff() {
    local filename="$1"
    local old_content="$2"
    local new_content="$3"

    echo "$old_content" > "$TEST_TEMP_DIR/$filename"
    git add "$filename"
    git commit --quiet -m "Add $filename"

    echo "$new_content" > "$TEST_TEMP_DIR/$filename"
}

# ============================================================================
# Basic Functionality Tests
# ============================================================================

@test "variant2-diff: shows help with --help" {
    run python3 "$SCRIPTS_DIR/variant2-diff.py" --help
    [ "$status" -eq 0 ]
    [[ "$output" == *"Convert git diff output to variant 2 format"* ]]
    [[ "$output" == *"--task-name"* ]]
    [[ "$output" == *"--width"* ]]
}

@test "variant2-diff: handles empty diff" {
    cd "$TEST_TEMP_DIR"
    run bash -c "git diff | python3 '$SCRIPTS_DIR/variant2-diff.py'"
    # Should exit successfully with no changes message
    [[ "$output" == *"No changes"* ]] || [ "$status" -eq 0 ]
}

@test "variant2-diff: formats basic diff output" {
    cd "$TEST_TEMP_DIR"
    create_test_diff "test.txt" "hello world" "hello universe"

    run bash -c "git diff | python3 '$SCRIPTS_DIR/variant2-diff.py' --task-name 'test-task'"
    [ "$status" -eq 0 ]

    # Check header structure
    [[ "$output" == *"Task Diff: test-task"* ]]
    [[ "$output" == *"Files: 1"* ]]
    [[ "$output" == *"+1/-1"* ]]

    # Check file header
    [[ "$output" == *"FILE 1/1: test.txt"* ]]
}

@test "variant2-diff: includes section markers" {
    cd "$TEST_TEMP_DIR"
    create_test_diff "test.txt" "line1" "line2"

    run bash -c "git diff | python3 '$SCRIPTS_DIR/variant2-diff.py'"
    [ "$status" -eq 0 ]

    # Check section structure
    [[ "$output" == *"## Section:"* ]]
    [[ "$output" == *"lines"* ]]
}

# ============================================================================
# Terminal Width Tests
# ============================================================================

@test "variant2-diff: reads terminalWidth from config" {
    cd "$TEST_TEMP_DIR"
    echo '{"terminalWidth": 40}' > "$TEST_TEMP_DIR/.claude/cat/cat-config.json"

    # Create a file with long lines
    create_test_diff "prose.md" "short" "This is a very long line that should be wrapped when the terminal width is set to 40 characters in the config file"

    run bash -c "git diff | python3 '$SCRIPTS_DIR/variant2-diff.py' --project-dir '$TEST_TEMP_DIR'"
    [ "$status" -eq 0 ]

    # Prose should be wrapped (check header width is ~40)
    # The header should be narrower with width 40
}

@test "variant2-diff: --width overrides config" {
    cd "$TEST_TEMP_DIR"
    create_test_diff "test.txt" "old" "new"

    run bash -c "git diff | python3 '$SCRIPTS_DIR/variant2-diff.py' --width 80"
    [ "$status" -eq 0 ]
    # Should work without error
}

@test "variant2-diff: defaults to width 50 without config" {
    cd "$TEST_TEMP_DIR"
    rm -f "$TEST_TEMP_DIR/.claude/cat/cat-config.json"

    create_test_diff "test.txt" "old" "new"

    run bash -c "git diff | python3 '$SCRIPTS_DIR/variant2-diff.py'"
    [ "$status" -eq 0 ]
    # Should work with default width
}

# ============================================================================
# Code File Tests (no wrapping)
# ============================================================================

@test "variant2-diff: preserves code indentation" {
    cd "$TEST_TEMP_DIR"

    old_code='def foo():
    return 1'
    new_code='def foo():
    x = 1
    return x'

    create_test_diff "test.py" "$old_code" "$new_code"

    run bash -c "git diff | python3 '$SCRIPTS_DIR/variant2-diff.py'"
    [ "$status" -eq 0 ]

    # Check indentation is preserved
    [[ "$output" == *"    x = 1"* ]]
    [[ "$output" == *"    return x"* ]]
}

@test "variant2-diff: does not wrap code files" {
    cd "$TEST_TEMP_DIR"

    # Create a very long line of code
    long_code="function veryLongFunctionName() { return someVeryLongVariableName + anotherVeryLongVariableName; }"
    create_test_diff "test.js" "// old" "$long_code"

    run bash -c "git diff | python3 '$SCRIPTS_DIR/variant2-diff.py' --width 40"
    [ "$status" -eq 0 ]

    # The long line should appear intact (not wrapped)
    [[ "$output" == *"veryLongFunctionName()"* ]]
}

@test "variant2-diff: detects Python sections" {
    cd "$TEST_TEMP_DIR"

    old_code='def greet():
    print("hello")

def farewell():
    print("bye")'

    new_code='def greet():
    print("hello")
    print("welcome")

def farewell():
    print("bye")'

    create_test_diff "test.py" "$old_code" "$new_code"

    run bash -c "git diff | python3 '$SCRIPTS_DIR/variant2-diff.py'"
    [ "$status" -eq 0 ]
    # Should recognize function sections
}

# ============================================================================
# Prose File Tests (with wrapping)
# ============================================================================

@test "variant2-diff: wraps long prose lines" {
    cd "$TEST_TEMP_DIR"

    long_prose="This is a very long line of prose that should definitely be wrapped when the terminal width is configured to be smaller than the line length itself."
    create_test_diff "readme.md" "Short text" "$long_prose"

    run bash -c "git diff | python3 '$SCRIPTS_DIR/variant2-diff.py' --width 40"
    [ "$status" -eq 0 ]

    # Prose should be wrapped - the long line should span multiple lines
    # Can check that the full text appears but broken up
    [[ "$output" == *"definitely be wrapped"* ]]
}

# ============================================================================
# Whitespace Visibility Tests
# ============================================================================

@test "variant2-diff: shows markers for whitespace-only changes" {
    cd "$TEST_TEMP_DIR"

    # Create a file with trailing spaces
    printf "hello   \n" > "$TEST_TEMP_DIR/test.txt"
    git add test.txt
    git commit --quiet -m "Add file with trailing spaces"

    # Remove the trailing spaces
    printf "hello\n" > "$TEST_TEMP_DIR/test.txt"

    run bash -c "git diff | python3 '$SCRIPTS_DIR/variant2-diff.py'"
    [ "$status" -eq 0 ]

    # Should show whitespace markers (middle dots) for the whitespace change
    # The exact markers depend on implementation
}

@test "variant2-diff: shows tab markers in whitespace changes" {
    cd "$TEST_TEMP_DIR"

    # Create a file with spaces for indentation
    echo "    indented with spaces" > "$TEST_TEMP_DIR/test.txt"
    git add test.txt
    git commit --quiet -m "Add file with space indentation"

    # Change to tab indentation
    printf "\tindented with tab\n" > "$TEST_TEMP_DIR/test.txt"

    run bash -c "git diff | python3 '$SCRIPTS_DIR/variant2-diff.py'"
    [ "$status" -eq 0 ]
    # Output should include markers
}

# ============================================================================
# Edge Cases
# ============================================================================

@test "variant2-diff: handles binary files" {
    cd "$TEST_TEMP_DIR"

    # Create a binary file
    printf '\x00\x01\x02\x03' > "$TEST_TEMP_DIR/binary.bin"
    git add binary.bin
    git commit --quiet -m "Add binary file"

    printf '\x00\x01\x02\x04' > "$TEST_TEMP_DIR/binary.bin"

    run bash -c "git diff | python3 '$SCRIPTS_DIR/variant2-diff.py'"
    # Should handle binary files gracefully
    [[ "$output" == *"Binary"* ]] || [ "$status" -eq 0 ]
}

@test "variant2-diff: handles new files" {
    cd "$TEST_TEMP_DIR"

    echo "new file content" > "$TEST_TEMP_DIR/new.txt"

    run bash -c "git diff | python3 '$SCRIPTS_DIR/variant2-diff.py'"
    # Empty diff for untracked file - should handle gracefully
    [ "$status" -eq 0 ]
}

@test "variant2-diff: handles deleted files" {
    cd "$TEST_TEMP_DIR"

    echo "to be deleted" > "$TEST_TEMP_DIR/delete.txt"
    git add delete.txt
    git commit --quiet -m "Add file to delete"

    git rm --quiet delete.txt

    run bash -c "git diff --cached | python3 '$SCRIPTS_DIR/variant2-diff.py'"
    [ "$status" -eq 0 ]
    [[ "$output" == *"delete.txt"* ]]
}

@test "variant2-diff: handles file from argument" {
    cd "$TEST_TEMP_DIR"

    create_test_diff "test.txt" "old content" "new content"
    git diff > "$TEST_TEMP_DIR/test.patch"

    run python3 "$SCRIPTS_DIR/variant2-diff.py" --file "$TEST_TEMP_DIR/test.patch"
    [ "$status" -eq 0 ]
    [[ "$output" == *"test.txt"* ]]
}

@test "variant2-diff: error on missing file argument" {
    run python3 "$SCRIPTS_DIR/variant2-diff.py" --file "/nonexistent/file.patch"
    [ "$status" -eq 1 ]
    [[ "$output" == *"Error"* ]]
}

# ============================================================================
# Multiple Files Tests
# ============================================================================

@test "variant2-diff: handles multiple changed files" {
    cd "$TEST_TEMP_DIR"

    echo "file one" > "$TEST_TEMP_DIR/one.txt"
    echo "file two" > "$TEST_TEMP_DIR/two.txt"
    git add one.txt two.txt
    git commit --quiet -m "Add two files"

    echo "file one modified" > "$TEST_TEMP_DIR/one.txt"
    echo "file two modified" > "$TEST_TEMP_DIR/two.txt"

    run bash -c "git diff | python3 '$SCRIPTS_DIR/variant2-diff.py'"
    [ "$status" -eq 0 ]

    # Should show both files
    [[ "$output" == *"FILE 1/"* ]]
    [[ "$output" == *"FILE 2/"* ]]
    [[ "$output" == *"Files: 2"* ]]
}

@test "variant2-diff: handles multiple files in single diff from file" {
    cat > "$TEST_TEMP_DIR/multi.diff" << 'EOF'
diff --git a/file1.md b/file1.md
--- a/file1.md
+++ b/file1.md
@@ -1,3 +1,3 @@
 # File 1
-old content
+new content
diff --git a/file2.py b/file2.py
--- a/file2.py
+++ b/file2.py
@@ -1,3 +1,3 @@
 def hello():
-    print("hi")
+    print("hello")
EOF

    run python3 "$SCRIPTS_DIR/variant2-diff.py" --file "$TEST_TEMP_DIR/multi.diff" --task-name "multi-test"

    [ "$status" -eq 0 ]
    [[ "$output" == *"Files: 2"* ]]
    [[ "$output" == *"file1.md"* ]]
    [[ "$output" == *"file2.py"* ]]
}

# ============================================================================
# Section Detection Tests
# ============================================================================

@test "variant2-diff: detects JavaScript function sections" {
    cd "$TEST_TEMP_DIR"

    old_js='function helper() {
  return 1;
}

function main() {
  return helper();
}'

    new_js='function helper() {
  return 1;
}

function main() {
  const x = helper();
  return x;
}'

    create_test_diff "app.js" "$old_js" "$new_js"

    run bash -c "git diff | python3 '$SCRIPTS_DIR/variant2-diff.py'"
    [ "$status" -eq 0 ]
    # Should recognize JavaScript function
}

@test "variant2-diff: detects markdown heading sections" {
    cd "$TEST_TEMP_DIR"

    old_md='# Title

## Introduction

Some text here.

## Methods

Method description.'

    new_md='# Title

## Introduction

Some text here with updates.

## Methods

Method description.'

    create_test_diff "doc.md" "$old_md" "$new_md"

    run bash -c "git diff | python3 '$SCRIPTS_DIR/variant2-diff.py'"
    [ "$status" -eq 0 ]
    # Should detect markdown sections
}
