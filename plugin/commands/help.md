---
name: cat:help
description: Use for quick reference to all CAT commands and skills
model: haiku
context: fork
---

# CAT Help

## Purpose

Display the CAT command reference.

---

## Procedure

A UserPromptSubmit hook has pre-computed the formatted help content and provided it
in the context above. Output that content EXACTLY as provided.

Do NOT:
- Recalculate or reformat any content
- Add project-specific analysis
- Add git status or file context
- Add next-step suggestions
- Add any commentary beyond the reference
