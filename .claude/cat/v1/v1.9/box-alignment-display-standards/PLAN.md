# Plan: box-alignment-display-standards

## Objective
correct box alignment in cat:config skill

## Details
Emojis take 2 character widths in terminals but were counted as 1,
causing right borders to misalign. Reduced spacing on emoji lines
to compensate. Also standardized all box widths to 61 characters.

## Acceptance Criteria
- [x] Implementation complete
- [x] Verified working
