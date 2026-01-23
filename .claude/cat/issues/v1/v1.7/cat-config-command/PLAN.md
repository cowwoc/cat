# Plan: cat-config-command

## Objective
add /cat:config command with parent menu navigation

## Details
Renamed from update-config to config for brevity. After updating a setting,
returns to parent menu instead of asking "Configure another setting?"

Changes:
- Created commands/config.md with adventure-style settings wizard
- Renamed /cat:update-config to /cat:config
- All submenus include "← Back" option for navigation
- Clarified approach descriptions (Conservative/Balanced/Aggressive)

## Acceptance Criteria
- [x] Implementation complete
- [x] Verified working
