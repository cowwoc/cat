# Build Verification Gates

Project-type aware verification before review.

## Project Type Detection

Detect project type from marker files:

| Marker File | Project Type | Priority |
|-------------|--------------|----------|
| pom.xml | maven | 1 |
| build.gradle | gradle | 1 |
| package.json | node | 2 |
| requirements.txt | python | 3 |
| pyproject.toml | python | 3 |
| Cargo.toml | rust | 3 |
| go.mod | go | 3 |
| Makefile | make | 4 |

Higher priority wins if multiple detected.

## Verification Commands

### Node.js / TypeScript
```yaml
node:
  build:
    command: "npm run build"
    required: true
    timeout: 300000
  test:
    command: "npm test"
    required: true
    timeout: 600000
  lint:
    command: "npm run lint"
    required: false
    blocking: false
  typecheck:
    command: "npm run typecheck"
    required: false
    blocking: true
```

### Maven (Java)
```yaml
maven:
  build:
    command: "./mvnw compile -q"
    required: true
    timeout: 300000
  test:
    command: "./mvnw test -q"
    required: true
    timeout: 600000
  lint:
    command: "./mvnw checkstyle:check pmd:check -q"
    required: false
    blocking: false
  verify:
    command: "./mvnw verify -q -DskipTests"
    required: false
    blocking: false
```

### Python
```yaml
python:
  build:
    command: "python -m py_compile $(find . -name '*.py' -not -path './venv/*')"
    required: true
    timeout: 60000
  test:
    command: "pytest"
    required: true
    timeout: 600000
  lint:
    command: "ruff check ."
    required: false
    blocking: false
  typecheck:
    command: "mypy ."
    required: false
    blocking: false
```

### Rust
```yaml
rust:
  build:
    command: "cargo build"
    required: true
    timeout: 600000
  test:
    command: "cargo test"
    required: true
    timeout: 600000
  lint:
    command: "cargo clippy"
    required: false
    blocking: false
```

### Go
```yaml
go:
  build:
    command: "go build ./..."
    required: true
    timeout: 300000
  test:
    command: "go test ./..."
    required: true
    timeout: 600000
  lint:
    command: "golangci-lint run"
    required: false
    blocking: false
```

## Verification Flow

```
After execution completes:

1. Detect project type
2. Run build command
   → FAIL? Block progression, report error
3. Run test command
   → FAIL? Block progression, report failures
4. Run lint command (if configured)
   → FAIL? Warn (non-blocking unless configured)
5. Run additional checks (typecheck, verify)
   → Handle per configuration
6. All required checks pass?
   → YES: Proceed to agent review
   → NO: Block, present failures
```

## Verification Report

```markdown
## Build Verification Report

**Project Type:** node
**Timestamp:** 2025-01-15T10:45:00Z

| Check | Status | Duration | Output |
|-------|--------|----------|--------|
| Build | PASS | 12.3s | Compiled successfully |
| Test | PASS | 45.2s | 42 tests passed |
| Lint | WARN | 3.1s | 3 warnings (non-blocking) |
| Typecheck | PASS | 8.7s | No errors |

**Result:** PASS - Proceed to review

**Warnings:**
- Line 45: Unused variable 'temp'
- Line 123: Missing JSDoc
- Line 201: Complex function (cyclomatic: 12)
```

## Configuration

In `.planning/config.json`:

```json
{
  "verification": {
    "enabled": true,
    "build": {
      "required": true,
      "timeout": 300000
    },
    "test": {
      "required": true,
      "timeout": 600000
    },
    "lint": {
      "required": false,
      "blocking": false
    },
    "custom_commands": {
      "security_scan": {
        "command": "npm audit",
        "required": false,
        "blocking": false
      }
    }
  }
}
```

## Failure Handling

### Build Failure
- Block progression completely
- Display full error output
- Require fix before retry

### Test Failure
- Block progression
- Display failing tests with output
- Require fix or explicit skip (logged)

### Lint Failure
- Warn but allow progression (default)
- Can be configured as blocking
- Style agent will also flag issues
