# Task Naming Rules

## Requirements

| Rule | Constraint |
|------|------------|
| Characters | Lowercase letters and hyphens only |
| Length | Maximum 50 characters |
| Special chars | Not allowed |
| Uniqueness | Must be unique within minor version |

## Valid Examples

- `parse-tokens`
- `fix-memory-leak`
- `add-user-auth`
- `refactor-database-layer`
- `implement-caching`

## Invalid Examples

| Name | Problem |
|------|---------|
| `Parse_Tokens` | Uppercase, underscores |
| `fix memory leak` | Spaces |
| `add-user-authentication-system-with-oauth-support-and-social-login` | Exceeds 50 chars |
| `fix#123` | Special character |
| `ADD-FEATURE` | Uppercase |

## Naming Guidelines

### Be Descriptive but Concise
- `parse-expressions` over `parse`
- `fix-null-pointer` over `fix-npe`

### Use Action Verbs
- `add-*` for new features
- `fix-*` for bug fixes
- `refactor-*` for restructuring
- `update-*` for modifications
- `remove-*` for deletions

### Reference Components
- `parse-switch-statements`
- `validate-user-input`
- `cache-database-queries`

## Branch Name Derivation

Task name becomes part of branch:
```
Task: parse-switch-statements
Branch: 1.0-parse-switch-statements
```

## Uniqueness Scope

Tasks must be unique within their minor version:
```
.claude/cat/v1/v1/parse-tokens/     # OK
.claude/cat/v1/v1/parse-tokens/     # CONFLICT
.claude/cat/v1/v2/parse-tokens/     # OK (different minor)
```
