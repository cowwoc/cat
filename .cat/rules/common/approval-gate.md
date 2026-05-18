---
subAgents: []
---
# Approval Gate Workflow

Squash commits by topic before every approval gate, including re-presentation after feedback.

Requirements:
1. All implementation commits must be squashed into logical groups before approval gate presentation.
2. After user feedback and additional changes, re-squash all commits before returning to approval gate.
3. Do not present an approval gate with a higher commit count than a prior squash attempt.
4. This applies to first presentation and every re-presentation.

Pattern:
- Initial implementation: multiple commits -> squash by topic -> present approval gate
- Feedback round: make changes -> re-squash all commits -> present approval gate
- Additional rounds: repeat re-squash before each gate
