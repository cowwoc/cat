# Terminology: CAT Issues vs Claude TaskList

**CRITICAL DISTINCTION:** Two different "task" systems exist. Never conflate them.

| System | Tool/Location | Purpose | Example |
|--------|---------------|---------|---------|
| **CAT Issues** | `/cat:status`, index.json files | Project work items across sessions | `v2.1-compress-skills-md` |
| **Claude TaskList** | `TaskList`, `TaskCreate` tools | Within-session work tracking | "Fix the login bug" |

**When reporting status:**
- CAT issues shown in `/cat:status` are **project issues** (persistent across sessions)
- Claude TaskList items are **session tasks** (exist only within current conversation)
- An empty TaskList does NOT mean "no work in progress" - CAT issues may be active
- A CAT issue "in progress" does NOT mean TaskList will have items
