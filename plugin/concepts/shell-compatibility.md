# Shell Compatibility

## Overview

The Bash tool may execute commands in either bash or zsh context depending on the user's shell.
Commands must be compatible with both shells.

## Common Incompatibilities

### Inequality Operator in `[[ ]]` (M304)

The `!=` operator inside `[[ ]]` conditionals fails in zsh:

```bash
# ❌ WRONG: Fails in zsh with "condition expected: \!="
if [[ "$VAR" != "value" ]]; then
  echo "not equal"
fi

# ✅ CORRECT: Use [ ] test command (POSIX)
if [ "$VAR" != "value" ]; then
  echo "not equal"
fi

# ✅ ALSO CORRECT: Use = with negation logic
if [[ "$VAR" = "value" ]]; then
  : # equal case
else
  echo "not equal"
fi
```

**Why:** zsh's `[[ ]]` uses different parsing rules. The `[ ]` test command is POSIX-compliant
and works identically in both shells.

### Array Indexing

```bash
# ❌ WRONG: Arrays are 0-indexed in bash, 1-indexed in zsh
arr=(a b c)
echo "${arr[0]}"  # 'a' in bash, '' in zsh

# ✅ CORRECT: Use explicit iteration or ${arr[@]}
for item in "${arr[@]}"; do
  echo "$item"
done
```

### Word Splitting

```bash
# ❌ RISKY: Word splitting differs between shells
VAR="a b c"
for item in $VAR; do  # Behavior differs
  echo "$item"
done

# ✅ CORRECT: Use explicit arrays
read -ra items <<< "a b c"
for item in "${items[@]}"; do
  echo "$item"
done
```

### Extended Glob Patterns

```bash
# ❌ WRONG: Extended globs may not be enabled in zsh
shopt -s extglob  # bash-only
ls !(*.txt)       # May not work in zsh

# ✅ CORRECT: Use find or explicit loops
find . -maxdepth 1 -type f ! -name "*.txt"
```

## Safe Patterns

### String Comparison

```bash
# Safe: POSIX test command
if [ "$a" = "$b" ]; then ...
if [ "$a" != "$b" ]; then ...
if [ -n "$a" ]; then ...
if [ -z "$a" ]; then ...

# Safe: [[ ]] for regex (both support this)
if [[ "$a" =~ ^[0-9]+$ ]]; then ...
```

### Arithmetic

```bash
# Safe: $(( )) arithmetic expansion
result=$((a + b))

# Safe: (( )) for conditions (works in both with minor differences)
if (( count > 5 )); then ...
```

### Command Substitution

```bash
# Safe: $() syntax (preferred over backticks)
result=$(command)
```

## Testing Recommendations

When writing bash commands that will be executed by the Bash tool:

1. **Prefer POSIX constructs** - `[ ]` over `[[ ]]` for simple tests
2. **Use `[[ ]]` only for** - regex matching, pattern matching, compound conditions
3. **Avoid shell-specific features** - `shopt`, `setopt`, shell-specific builtins
4. **Test edge cases** - empty strings, special characters, multi-word values

## Reference

Related mistakes:
- M304: `!=` operator in `[[ ]]` causing zsh failures
