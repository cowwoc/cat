# Plan: Rename and Merge Stakeholders

## Goal
Rename `stakeholder-architect` to `stakeholder-architecture` for naming consistency (domain, not role), and merge
`stakeholder-sales` + `stakeholder-marketing` into `stakeholder-business` since they evaluate the same artifact from
nearly identical commercial readiness perspectives.

## Satisfies
None (naming consistency and consolidation)

## Risk Assessment
- **Risk Level:** LOW
- **Concerns:** References scattered across agent definitions, skill files, documentation, and issue planning files.
  Must update all references consistently.
- **Mitigation:** Comprehensive search identified ~20 locations across 9 files. Mechanical rename with grep
  verification.

## Files to Modify

### Rename: stakeholder-architect → stakeholder-architecture
- `plugin/agents/stakeholder-architect.md` → rename to `stakeholder-architecture.md`, update name/heading
- `plugin/skills/stakeholder-review/first-use.md` — update agent reference
- `plugin/skills/research/first-use.md` — update agent reference
- `plugin/agents/README.md` — update list and tree diagram

### Merge: stakeholder-sales + stakeholder-marketing → stakeholder-business
- `plugin/agents/stakeholder-sales.md` — delete
- `plugin/agents/stakeholder-marketing.md` — delete
- `plugin/agents/stakeholder-business.md` — create (merge content from both)
- `plugin/skills/stakeholder-review/first-use.md` — replace two entries with one
- `plugin/skills/research/first-use.md` — replace two entries with one
- `plugin/agents/README.md` — replace two entries with one

### Update references in planning files
- `.claude/cat/issues/v2/v2.1/convert-stakeholders-to-agents/PLAN.md` — update references
- `.claude/cat/issues/v2/v2.0/pricing-page/PLAN.md` — update section headings

## Acceptance Criteria
- [ ] `stakeholder-architect.md` renamed to `stakeholder-architecture.md` with updated name/heading
- [ ] `stakeholder-sales.md` and `stakeholder-marketing.md` merged into `stakeholder-business.md`
- [ ] All skill references updated (stakeholder-review, research)
- [ ] README.md updated with new names
- [ ] No stale references remain (grep verification)
- [ ] Tests pass
- [ ] E2E: Invoke stakeholder-review skill selection and confirm new names appear correctly

## Execution Steps
1. **Rename architect agent:** Rename file, update frontmatter name field and heading
2. **Create business agent:** Merge sales and marketing agent content into new `stakeholder-business.md`
3. **Delete old agents:** Remove `stakeholder-sales.md` and `stakeholder-marketing.md`
4. **Update skill references:** Update `stakeholder-review/first-use.md` and `research/first-use.md`
5. **Update README:** Update agent list and tree diagram in `plugin/agents/README.md`
6. **Update planning files:** Update references in issue PLAN.md files
7. **Verify no stale references:** Grep for old names across entire codebase
8. **Run tests:** `mvn -f client/pom.xml test`
