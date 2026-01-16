# Plan: config-terminal-width

## Goal
Add terminal width configuration option to /cat:config interactive wizard, allowing users to set their preferred display width for diffs and output formatting.

## Requirements

| ID | Requirement | Priority | Acceptance Criteria |
|----|-------------|----------|---------------------|
| REQ-001 | *Define requirement* | must-have | *How to verify* |


## Satisfies
- None (usability improvement)

## Approach Outlines

### Conservative
Add single question to /cat:config for terminal width with preset options (50, 80, 120).
- **Risk:** LOW
- **Tradeoff:** Limited customization

### Balanced
Add terminal width to /cat:config with preset options plus custom input, validate range (40-200), persist to cat-config.json.
- **Risk:** MEDIUM
- **Tradeoff:** Standard implementation

### Aggressive
Auto-detect terminal width where possible, offer presets for common scenarios (mobile, laptop, wide monitor), remember per-device.
- **Risk:** HIGH
- **Tradeoff:** Over-engineering for simple config

## Acceptance Criteria
- [ ] /cat:config wizard includes terminal width option
- [ ] Wizard asks about device type: Desktop/Laptop (120) vs Mobile (50) vs Custom
- [ ] Value persisted to cat-config.json as terminalWidth
- [ ] Reasonable validation (min 40, max 200)
- [ ] Default value: 120 characters (modern wide terminal standard)
- [ ] README.md recommends 120 for desktop/laptops, 50 for mobile devices
