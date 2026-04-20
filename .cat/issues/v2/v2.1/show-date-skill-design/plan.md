# Design show-date-skill

## Goal

Create a simple skill named "show-date-skill" that displays the current date in YYYY-MM-DD format.

## Requirements

- **Display current date**: Output the current system date in ISO 8601 date format (YYYY-MM-DD)
- **Use CURIOSITY=medium**: Enable test generation for the skill
- **Simple implementation**: Single-purpose skill with minimal complexity

## Scope

The skill should:
1. Use the `date` command to retrieve the current date
2. Format output as YYYY-MM-DD (e.g., 2026-04-19)
3. Be user-invocable
4. Include proper license header per CAT Commercial License
5. Have Purpose, Procedure, and Verification sections

## Test Generation

With CURIOSITY=medium, the instruction-builder-agent will:
- Extract semantic units from the skill instruction
- Generate test scenarios covering the requirement
- Run SPRT (Sequential Probability Ratio Test) to verify compliance
- Harden the instruction against adversarial probing
- Compress the instruction for efficiency

## Acceptance Criteria

- [ ] SKILL.md created with proper delegation structure
- [ ] first-use.md created with Purpose, Procedure, Verification sections
- [ ] License header included in both files
- [ ] Skill properly formatted per CAT conventions
- [ ] SPRT test cases generated and pass acceptance criteria
- [ ] Adversarial hardening completes successfully
- [ ] Instruction compression reduces size while maintaining compliance

## Implementation Notes

The skill should be straightforward:
- SKILL.md: Frontmatter + delegation to get-skill binary
- first-use.md: Simple instruction with date output procedure

No complex state management, no conditional branching, no external dependencies beyond the standard `date` command.
