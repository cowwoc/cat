# Task Plan: license-commands

## Objective
Implement cat:license command (consolidating activate/deactivate/status functionality).

## Tasks
- [ ] Design unified /cat:license command interface
- [ ] Implement license activation flow (enter key, validate, store)
- [ ] Implement license deactivation flow (clear stored key)
- [ ] Implement license status display (current tier, expiry, features)
- [ ] Add license key secure storage (OS keychain if available)
- [ ] Handle offline scenarios gracefully

## Technical Approach
Single command with subcommands or interactive menu:
- /cat:license activate <key>
- /cat:license deactivate
- /cat:license status
- /cat:license (interactive menu)

## Verification
- [ ] Can activate with valid license key
- [ ] Can deactivate and return to free tier
- [ ] Status shows correct tier and features
- [ ] Key stored securely (not in plain text config)
