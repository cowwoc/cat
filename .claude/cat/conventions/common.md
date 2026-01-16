# Common Conventions

Cross-cutting rules that apply to all CAT development work.

## Language Requirements

| Component Type | Language | Rationale |
|----------------|----------|-----------|
| Server-side code | **Java** | Type safety, enterprise ecosystem, crypto libraries |
| CLI tools/hooks | Bash | Claude Code plugin integration, Unix tooling |
| Configuration | JSON | Standard, machine-readable |
| Documentation | Markdown | Human-readable, version-controlled |

### Server-Side Code (Java)

**MANDATORY:** All server-side components must be written in Java.

This includes:
- License validation server
- JWT token generation and validation
- API endpoints
- Cryptographic operations (signing, verification)
- Database interactions
- Webhook handlers

**Rationale:**
- Strong type system catches errors at compile time
- Mature cryptographic libraries (java.security, Bouncy Castle)
- Enterprise-grade HTTP servers (Spring Boot, Jetty)
- Better maintainability for complex business logic
- Existing enterprise adoption and support

**Java Version:** 25+ (latest LTS)

**Testing Framework:** TestNG (preferred over JUnit for better parallel test execution and data-driven testing)

**Build Tool:** Maven or Gradle (prefer Maven for consistency)

### Client-Side/CLI (Bash)

Bash scripts are appropriate for:
- Claude Code hooks (PreToolUse, PostToolUse, etc.)
- CLI wrappers that invoke Java services
- Simple validation scripts
- Git operations
- File manipulation

Bash scripts should NOT contain:
- Complex business logic
- Cryptographic operations (beyond calling Java services)
- State management beyond simple files

## Code Organization

```
project/
├── server/                 # Java server-side code
│   ├── src/main/java/
│   ├── src/test/java/
│   └── pom.xml
├── hooks/                  # Bash hooks for Claude Code
├── scripts/                # Bash utility scripts
├── skills/                 # Skill definitions (Markdown)
└── commands/               # Command definitions (Markdown)
```

## Error Handling

- Java: Use checked exceptions for recoverable errors, unchecked for programming errors
- Bash: Use `set -euo pipefail` and trap handlers
- Always provide meaningful error messages
- Log errors with context (what failed, why, how to fix)

### Fail-Fast Principle

**MANDATORY:** Prefer failing immediately with a clear error over using fallback values.

**Why:**
- Silent fallbacks mask configuration errors and can cause catastrophic failures
- Example: Falling back to "main" when the base branch should be "v1.10" causes merges to wrong branch
- Fail-fast errors are easier to debug than mysterious wrong behavior

**Pattern:**
```bash
# ❌ WRONG: Silent fallback
BASE_BRANCH=$(cat "$CONFIG_FILE" 2>/dev/null || echo "main")

# ✅ CORRECT: Fail-fast with clear error
if [[ ! -f "$CONFIG_FILE" ]]; then
  echo "ERROR: Config file not found: $CONFIG_FILE" >&2
  echo "Solution: Run /cat:work to create worktree properly." >&2
  exit 1
fi
BASE_BRANCH=$(cat "$CONFIG_FILE")
```

**When fallbacks ARE acceptable:**
- User-facing defaults (e.g., terminal width defaults to 80)
- Non-critical display settings
- Optional enhancements

**When fallbacks are NOT acceptable:**
- Branch names (wrong branch = wrong merge target)
- File paths (wrong path = data loss or corruption)
- Security settings (wrong default = vulnerability)
- Any value that affects data integrity

## Testing

- Java: TestNG for unit tests, integration tests with embedded servers
- Bash: Bats (Bash Automated Testing System)
- Minimum coverage: 80% for business logic
- All edge cases must have tests
