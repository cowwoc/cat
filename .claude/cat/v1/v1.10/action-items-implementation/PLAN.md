# Plan: action-items-implementation

## Objective
implement action items A020, A021, A022

## Details
A020: Box Rendering Verification Protocol (config.md)
- Character width lookup requirement
- Line-by-line verification checklist
- Pre-output checklist with blocking condition
- M136 anti-pattern reference

A021: Display-Before-Prompt Protocol (config.md)
- BLOCKING requirement for visual display before AskUserQuestion
- Verification sequence

A022: Prevention File Edit Verification (learn-from-mistakes/SKILL.md)
- Strengthened BLOCKING GATE with explicit file list
- prevention_path validation references A022

Also includes M136 fix: Added four_pointed_star to emoji-widths.json with width 2

## Acceptance Criteria
- [x] Implementation complete
- [x] Verified working
