# State

- **Status:** closed
- **Progress:** 100%
- **Dependencies:** []
- **Blocks:** []
- **Resolution:** implemented

## Implementation Summary

**Updated 22 CLI tool main() methods to convert RuntimeException/AssertionError to HookOutput JSON.**

All CLI tools invoked by skills now properly handle unexpected exceptions by:
1. Catching RuntimeException and AssertionError in main() methods
2. Logging the error for debugging
3. Converting to HookOutput.block() JSON response
4. Writing to stdout with exit code 0 for proper skill JSON parsing

This ensures skills receive structured JSON error responses instead of unhandled exceptions or malformed output.

**Files modified:**
- 22 Java CLI tools (hooks and skills) main() methods
- .claude/rules/hooks.md - Added pattern documentation for error handling in main()

**Test results:** All 2431 tests pass with no regressions
