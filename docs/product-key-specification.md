# License Key Implementation Specification

Version 1.0

## Overview

License keys authenticate paid tier features (Pro and Enterprise). The system must be:

- **Secure** — Keys can't be forged or shared trivially
- **Offline-capable** — Don't break CI/CD if license server is down
- **Developer-friendly** — Multiple configuration methods

Core tier requires no license key.

---

## Key Format

```
CAT-{TIER}-{UNIQUE_ID}-{CHECKSUM}
```

**Examples:**
```
CAT-PRO-a7Kx9Pm2Qw4R-3f8a
CAT-ENT-b8Lm3Nv5Xt6Y-9c2d
```

| Component | Description |
|-----------|-------------|
| `CAT` | Prefix (identifies as CAT license) |
| `TIER` | `PRO` or `ENT` (no key needed for Core) |
| `UNIQUE_ID` | 12-char alphanumeric, server-generated |
| `CHECKSUM` | 4-char validation (prevents typos) |

---

## Storage Methods

CAT checks these locations in priority order, uses first valid key found:

| Priority | Method | Location | Use case |
|----------|--------|----------|----------|
| 1 | Environment variable | `CAT_LICENSE_KEY` | CI/CD, containers |
| 2 | Project config | `.claude/cat/license.json` | Pro shared config |
| 3 | User config | `~/.config/cat/license.json` | Personal machine |

### Environment Variable

```bash
export CAT_LICENSE_KEY="CAT-PRO-a7Kx9Pm2Qw4R-3f8a"
```

### Config File Format

```json
{
  "key": "CAT-PRO-a7Kx9Pm2Qw4R-3f8a",
  "email": "dev@company.com",
  "activated": "2026-01-15T10:30:00Z"
}
```

**File permissions:** Config files must be created with `0600` (owner read/write only).

---

## Activation Flow

### Interactive Activation

```bash
$ cat:activate
Enter license key: CAT-PRO-a7Kx9Pm2Qw4R-3f8a
Enter email: dev@company.com

✓ License validated
✓ Saved to ~/.config/cat/license.json
✓ Pro features unlocked

License: CAT Pro
Seats: 1 of 25 used
Expires: 2027-01-15

Manage your license: https://cat.dev/account
```

### Silent Activation (CI/CD)

```bash
$ CAT_LICENSE_KEY="CAT-PRO-..." cat:status
# Automatically validates and uses key
```

---

## Validation Logic

```
┌─────────────────────────────────────────────────────────────┐
│                    Key Validation                           │
├─────────────────────────────────────────────────────────────┤
│ 1. Check format (prefix, tier, length, checksum)            │
│    └─ Invalid → Error: "Invalid license key format"         │
│                                                             │
│ 2. Online validation (if network available)                 │
│    ├─ Valid → Cache result for 7 days                       │
│    ├─ Invalid → Error: "License key not recognized"         │
│    ├─ Expired → Error: "License expired on {date}"          │
│    └─ Seat limit → Error: "All seats in use"                │
│                                                             │
│ 3. Offline fallback (if network unavailable)                │
│    ├─ Cached valid result exists → Allow                    │
│    ├─ No cache, checksum valid → Allow (grace mode)         │
│    └─ Grace mode expires after 30 days                      │
└─────────────────────────────────────────────────────────────┘
```

---

## Security Considerations

| Threat | Mitigation |
|--------|------------|
| Key in git history | `.claude/cat/license.json` added to `.gitignore` by default. Warn if committed. |
| Key visible in process list | Env var visible to `ps`. Document that config file is more secure for sensitive environments. |
| Key sharing across orgs | Server tracks activations per key. Alert on anomalous patterns (50 IPs for 5-seat license). |
| Offline abuse | 30-day grace window. After that, must connect to validate. |
| Checksum forgery | Checksum algorithm not published. Validation requires server roundtrip for full auth. |

---

## Feature Gating Implementation

```python
# Pseudocode for feature check
def require_tier(minimum_tier: Tier):
    license = load_license()

    if license is None:
        current_tier = Tier.CORE
    else:
        current_tier = validate_and_get_tier(license)

    if current_tier < minimum_tier:
        raise FeatureGatedError(
            f"This feature requires {minimum_tier.name}. "
            f"Current: {current_tier.name}. "
            f"Upgrade: https://cat.dev/pricing"
        )

# Usage in skills
@require_tier(Tier.PRO)
def task_lock_acquire(task_id: str):
    ...

@require_tier(Tier.ENTERPRISE)
def sync_jira_issues():
    ...
```

---

## CLI Commands

| Command | Description |
|---------|-------------|
| `cat:activate` | Interactive license activation |
| `cat:license` | Show current license status |
| `cat:deactivate` | Remove license from current machine (frees seat) |

### Example Output: `cat:license`

```
License Status
──────────────
Plan:       Pro
Key:        CAT-PRO-****-3f8a (masked)
Email:      dev@company.com
Activated:  2026-01-15
Expires:    2027-01-15
Seats:      12 of 25 used
Source:     ~/.config/cat/license.json

Features:   ✓ Collision Prevention
            ✓ Team Pulse
            ✓ Shared Brain
            ✓ Team Analytics
            ✗ SSO/SAML (Enterprise)         Upgrade → https://cat.dev/pricing
            ✗ Issue Tracker Sync (Enterprise)

Manage: https://cat.dev/account
```

---

## Feature Gating Matrix

| Feature | Core | Pro | Enterprise |
|---------|:-----:|:----:|:----------:|
| **Seats** | 1 | 1-50 | Unlimited |
| **Core CAT workflow** | ✓ | ✓ | ✓ |
| **Task locking** | ✗ | ✓ | ✓ |
| **Team activity feed** | ✗ | ✓ | ✓ |
| **Shared config sync** | ✗ | ✓ | ✓ |
| **Cross-session handoff** | ✗ | ✓ | ✓ |
| **Branch policies** | ✗ | ✓ | ✓ |
| **Team analytics** | ✗ | ✓ | ✓ |
| **Project budgets** | ✗ | ✓ | ✓ |
| **SSO/SAML** | ✗ | ✗ | ✓ |
| **Issue tracker sync** | ✗ | ✗ | ✓ |
| **Slack/Teams notifications** | ✗ | ✗ | ✓ |
| **Custom LLM endpoints** | ✗ | ✗ | ✓ |
| **Webhook API** | ✗ | ✗ | ✓ |
| **Audit logs** | ✗ | ✗ | ✓ |
| **Data residency** | ✗ | ✗ | ✓ |
| **SLA & priority support** | ✗ | ✗ | ✓ |

---

## Error Messages

| Scenario | Message |
|----------|---------|
| No license, Pro feature | `This feature requires Pro. Current: Core. Upgrade: https://cat.dev/pricing` |
| No license, Enterprise feature | `This feature requires Enterprise. Current: Core. Upgrade: https://cat.dev/pricing` |
| Pro license, Enterprise feature | `This feature requires Enterprise. Current: Pro. Upgrade: https://cat.dev/pricing` |
| Expired license | `License expired on 2026-12-15. Renew: https://cat.dev/account` |
| All seats used | `All 25 seats are in use. Add seats: https://cat.dev/account` |
| Invalid key format | `Invalid license key format. Expected: CAT-PRO-xxxx-xxxx or CAT-ENT-xxxx-xxxx` |
| Network unavailable, no cache | `Cannot validate license (offline). Grace period: 28 days remaining.` |
| Grace period expired | `License validation required. Connect to internet and retry.` |

---

## Implementation Notes

1. **Never log full license keys** — Always mask middle characters: `CAT-PRO-****-3f8a`

2. **Cache validation results** — Store in `~/.cache/cat/license-validation.json` with 7-day TTL

3. **Graceful degradation** — If validation fails due to network, allow 30-day grace with warning

4. **Audit logging** — Enterprise tier logs all feature access for compliance export

5. **Seat counting** — Track unique machine IDs per key, not concurrent sessions
