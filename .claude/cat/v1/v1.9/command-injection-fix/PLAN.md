# Plan: command-injection-fix

## Objective
fix command injection and add script validation

## Details
batch-read.sh:
- Remove eval with user-controlled PATTERN variable
- Use array-based grep arguments to prevent injection

register-hook.sh:
- Require shebang in script content
- Add bash syntax validation before writing
- Warn on dangerous patterns (curl|bash, rm -rf /, etc.)

## Acceptance Criteria
- [x] Implementation complete
- [x] Verified working
