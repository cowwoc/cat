# Task Plan: feature-gate-middleware

## Objective

Build feature gate system with secure license validation and tier-based access control.

## Architecture Overview

```
┌─────────────────────────────────────────────────────────────────┐
│                    /cat:login Command                            │
├─────────────────────────────────────────────────────────────────┤
│  1. User runs /cat:login                                        │
│  2. Opens browser to license server authentication              │
│  3. Server returns signed JWT token                             │
│  4. Token saved to cat-config.local.json                        │
└─────────────────────────────────────────────────────────────────┘
                              ↓
┌─────────────────────────────────────────────────────────────────┐
│                 cat-config.local.json                            │
├─────────────────────────────────────────────────────────────────┤
│  {                                                              │
│    "license": "eyJhbGciOiJFZERTQSIsInR5cCI6IkpXVCJ9..."        │
│  }                                                              │
│  (User-specific, in .gitignore, overrides cat-config.json)      │
└─────────────────────────────────────────────────────────────────┘
                              ↓
┌─────────────────────────────────────────────────────────────────┐
│              validate-license.py (Shared)                        │
├─────────────────────────────────────────────────────────────────┤
│  1. Read token from cat-config.local.json                       │
│  2. Verify Ed25519 signature using bundled public key           │
│  3. Check expiration (with grace period handling)               │
│  4. Return: { tier, customerId, valid, expired, inGrace }       │
└─────────────────────────────────────────────────────────────────┘
                              ↓
┌─────────────────────────────────────────────────────────────────┐
│                    Feature Gate Check                            │
├─────────────────────────────────────────────────────────────────┤
│  1. Call validate-license.py to get tier                        │
│  2. Call entitlements.sh <tier> <feature>                       │
│  3. If denied → return upgrade message                          │
│  4. If allowed → proceed                                        │
└─────────────────────────────────────────────────────────────────┘
```

## Files to Create

### 1. plugin/scripts/validate-license.py
Shared Python module for license validation:
- Ed25519 signature verification using `cryptography` library
- JWT parsing (header.payload.signature)
- Expiration checking with grace period support
- Returns JSON: `{ "valid": bool, "tier": str, "expired": bool, "inGrace": bool, "error": str }`

### 2. plugin/config/cat-public-key.pem
Ed25519 public key for signature verification (bundled with plugin).

### 3. plugin/commands/login.md
`/cat:login` command:
- Opens browser to license server OAuth flow
- Receives callback with signed JWT
- Writes token to cat-config.local.json
- Verifies token is valid before saving

### 4. plugin/scripts/feature-gate.sh
Feature gate wrapper:
- Calls validate-license.py for tier
- Calls entitlements.sh for feature check
- Returns structured result with upgrade message if blocked

### 5. .gitignore update
Add `cat-config.local.json` to prevent accidental commit.

## Validation Behavior

| Token State | Tier Used | User Message |
|-------------|-----------|--------------|
| Valid, not expired | Token tier | (none) |
| Valid, expired but in grace | Token tier | "License expires in N days" |
| Valid, expired past grace | indie | "License expired. Run /cat:login" |
| Invalid signature | indie | "Invalid license. Run /cat:login" |
| No token | indie | (none - free tier) |
| Validation error | indie | "License check failed: {error}" |

## Feature Gating Points

| Operation | Required Feature | Required Tier |
|-----------|------------------|---------------|
| Spawn parallel subagents | parallel-task-execution | team |
| /cat:stakeholder-review | stakeholder-reviews | team |
| /cat:decompose-task | task-decomposition | team |
| /cat:learn-from-mistakes | learn-from-mistakes | team |
| Custom hooks | custom-hooks | enterprise |
| Audit export | compliance-export | enterprise |

## Integration Points

1. **SessionStart hook** - Validate license, cache tier for session
2. **PreToolUse hook** - Gate parallel agent spawning
3. **Skill invocation** - Gate premium skills
4. **Command execution** - Gate premium commands

## Dependencies

- tier-feature-mapping (completed) - provides tiers.json and entitlements.sh
- jwt-token-generation (completed) - provides token format specification

## Test Cases

- [ ] Valid token returns correct tier
- [ ] Expired token within grace period shows warning but allows access
- [ ] Expired token past grace period falls back to indie
- [ ] Tampered token rejected (invalid signature)
- [ ] Missing token defaults to indie tier
- [ ] Feature gate blocks team features for indie tier
- [ ] Feature gate allows team features for team tier
- [ ] Upgrade message shown when blocked

## Execution Steps

1. **Create validate-license.py**
   - Implement Ed25519 signature verification
   - Parse JWT structure (no external JWT library needed)
   - Handle all token states (valid, expired, grace, invalid)
   - Unit tests with test tokens

2. **Create feature-gate.sh**
   - Integrate validate-license.py with entitlements.sh
   - Format user-friendly upgrade messages
   - Exit codes: 0=allowed, 1=blocked, 2=error

3. **Create /cat:login command**
   - Browser-based OAuth flow
   - Token storage in cat-config.local.json
   - Validation before saving

4. **Update .gitignore**
   - Add cat-config.local.json pattern

5. **Integration hooks**
   - SessionStart: validate and cache tier
   - PreToolUse: gate parallel operations
   - Skill/command gating

## Verification

- [ ] `validate-license.py` passes all unit tests
- [ ] Feature gates correctly block/allow based on tier
- [ ] Grace period warning displayed correctly
- [ ] /cat:login flow works end-to-end
- [ ] cat-config.local.json not tracked by git
