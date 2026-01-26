# Version Tracking

## Version History

### In Development: v2.1

**Pre-Demo Polish**

*In development - see planned tasks in .claude/cat/issues/v2/v2.1/*

### 2026-01-26: v2.0

**Commercialization & Display Overhaul**

Major release preparing CAT for commercial use with licensing infrastructure, comprehensive display
redesign, and workflow hardening. 74 tasks completed across 310 commits.

**Licensing & Commercial Features:**
- **License Validation**: JWT-based license token generation and validation
- **Feature Gating**: Tier-based feature entitlement mapping (Free/Pro/Enterprise)
- **Update Check**: Startup version check with upgrade notifications
- **Legal Review**: LICENSE.md reviewed for commercial release compliance

**Display Redesign:**
- **Ultra-Compact Status**: Redesigned `/cat:status` with condensed visual layout
- **Pre-Computed Boxes**: All display output pre-computed via hooks to prevent alignment errors
- **Render-Diff Skill**: New skill for approval gate code review with 4-column table format
- **Dynamic Box Sizing**: Boxes automatically expand to fit content width
- **Emoji-Aware Alignment**: Correct padding for emoji characters in box borders

**Workflow Improvements:**
- **Version Boundary Gates**: Approval gates at version completion milestones
- **Git Workflow Wizard**: `/cat:init` now configures merge style, squash preferences, and branching
- **Soft Decomposition Threshold**: Suggest task decomposition when approaching context limits
- **Context-Aware Acceptance Criteria**: `/cat:add` options adapt to task type
- **Local Config Override**: `cat-config.local.json` for machine-specific settings
- **Task Tools Integration**: Native Claude Code task tracking replaces custom backup system

**Plugin Architecture:**
- **Reorganized Directory Structure**: Skills, commands, and concepts consolidated under `plugin/`
- **Centralized Version Paths**: Unified path resolution for version/task lookups
- **Conventions Directory**: Split between `.claude/rules/` and `.claude/cat/conventions/`
- **Stakeholders Relocated**: Moved to `plugin/stakeholders/` for portability
- **Shared Libraries**: `emoji_widths.py` and hook utilities extracted for reuse

**Research & Planning:**
- **Recursive Drill-Down**: `/cat:research` supports multi-level exploration with scorecards
- **Executive Summary**: Research results include strategic recommendations
- **Skill Builder Rewrite**: 12 core skills rewritten with validation-driven approach

**Bugfixes:**
- Fix fail-fast status validation to prevent stale display (M253)
- Fix emoji width calculations for box alignment
- Fix HEREDOC commit message parsing in type validation
- Fix render-diff bracket placement at word boundaries (M212)
- Fix work box right border alignment
- Standardize STATE.md format parsing (M224)

**Retrospectives:**
- R001: First retrospective analysis (10 mistakes)
- M220-M255: 35 learnings recorded with prevention rules

**Configuration:**
- Default to task creation for work requests
- Worktree isolation requirement enforced (M252)
- Project-local convention hooks (M255)

### 2026-01-20: v1.10

**Workflow Refinements & Display Improvements**

Final v1.0 release completing the core rewrite with workflow refinements, display improvements, and planning
structure for v1.1 commercialization.

**New Features:**
- **Review Feedback Loop**: Approval gate now spawns subagent for review feedback implementation
- **Task Branch Forking**: Task branches fork from current branch instead of main
- **Inline Diff Display**: Updated diff output to inline context style (Proposal I)
- **Progress Phase Indicators**: 4-phase progress display replaces 17-step tracker
- **Render Box Skill**: Centralized ASCII box rendering with emoji-aware alignment
- **Terminal Width Config**: Add terminal width setting to `/cat:config` wizard
- **Context Limit Enforcement**: Subagents have enforced context limits

**Workflow Improvements:**
- Config-driven approach selection with confidence-based fork wizard
- Expanded exploration subagent role for preparation and verification
- Positive prescriptive language replaces negative language in skills
- Validation-driven status display using scripts (M140-M145)
- Base branch configuration replaces hardcoded main references
- Optimized git-merge-linear skill for worktree-based merging

**Bugfixes:**
- Fix multi-file diff parsing dropping first files
- Fix box_header() alignment using pad() for emoji-aware width
- Fix status.sh arithmetic bug and empty array key guard
- Fix token estimate vs measurement confusion (M146)
- Fix commit type validation for retrospectives (M139)

**Planning:**
- v1.1 Commercialization: 12 tasks for licensing, legal, and enterprise features
- Remote lock metadata support for distributed task coordination
- Context-aware stakeholder selection

**Retrospectives:**
- R002: 10 mistakes analyzed
- R003: Status display resolution
- R008: 13 mistakes analyzed

### 2026-01-17: v1.9

**Display Standards & Workflow Hardening**

Comprehensive display standardization, test infrastructure, and workflow stability improvements.

**New Features:**
- **Fork-in-the-Road Wizard**: Improved approach selection with wizard-style presentation
- **Exit Gate Dependencies**: Task dependencies for exit gate validation
- **Test Framework**: Added bats test framework with 66+ tests for hooks and scripts
- **Language Supplements**: Stakeholder reviews can load language-specific guidance

**Configuration:**
- New settings schema: `trust`, `verify`, `curiosity`, `patience` replace previous options
- Worktree isolation protection (M101) prevents commits to wrong worktree
- Task lock checking before offering tasks (M097)
- Hook to block direct lock file deletion (M096)
- Commit message guidance: don't list modified files (redundant with diff)
- Require failing test cases for bugfix tasks
- Strengthen token measurement requirements (A017)

**Bugfixes:**
- Fix bold rendering in display templates (M125)
- Fix box formatting for display standard compliance
- Fix subagent token measurement session ID issue (M109)
- Fix inconsistent task path patterns (M108)
- Fix parse_error false positives when command succeeds (M100)
- Fix HEREDOC message extraction in commit type validation
- Fix emoji display width calculations in box templates
- Fix docs vs config validation for Claude-facing files

**Documentation:**
- Display standards with markdown rendering rules (A018)
- Simplified emoji width handling
- Task locking protocol documentation

### 2026-01-15: v1.8

**Version Migration & Workflow Stability**

Introduces automated version migration system and workflow stability improvements.

**New Features:**
- **Version Migration System**: Automated migrations for CAT upgrades with backup/restore
- **Version Entry/Exit Gates**: Pre/post-upgrade validation for safe version transitions
- **Duplicate Detection**: Exploration step now detects duplicate tasks (M087)

**Documentation:**
- README: Added version and autoRemoveWorktrees config options
- README: Updated problem section with visual diagram
- Token tracking guidance for compaction scenarios
- Pre-edit self-check for main agent (M088)
- Commit separation guidance for `.claude/rules/` (M089)
- Mandatory user preference respect in `choose-approach` skill

**Bugfixes:**
- Fixed commit-type hook validation (M091-M094)
- Fixed emoji alignment in box-drawing documentation (HTML deprecation)

### 2026-01-15: v1.7

**Config Command & Documentation**

- **Feature**: Renamed `/cat:update-config` to `/cat:config` for brevity
- **Feature**: Config wizard now returns to parent menu after changing settings
- **Docs**: Clarified approach descriptions (Conservative/Balanced/Aggressive) with actionable impact
- **Bugfix**: Fixed emoji alignment in box-drawing documentation

### 2026-01-14: v1.6

**Adventure Mode**

Introduces the "fun AND reliable" philosophy with adventure-style workflow enhancements.

**New Features:**
- **Adventure Mode**: Express development style preferences during `/cat:init`
- **Choose Approach**: "Fork in the Road" decision points with smart recommendations
- **Visual Status**: Adventure-style progress display in `/cat:status`
- **User Preferences**: Approach (conservative/balanced/aggressive), stakeholder review
  frequency, refactoring appetite - stored in cat-config.json

**Skills:**
- `choose-approach` - Present approach options at task forks with recommendations
- `config` - Interactive wizard to customize CAT settings

**Workflow Enhancements:**
- Enhanced approval gate with adventure-style checkpoint display
- 15-step work flow (added approach selection)
- Progress bars show user style and preferences

**Documentation:**
- README rewrite with "fun AND reliable" philosophy
- New "Development Philosophy" section
- Adventure mode configuration documentation

### 2026-01-14: v1.5

**Quality Gates, Progress Indicators, and Workflow Stability**

Major release with multi-perspective stakeholder reviews, visual progress indicators, and extensive
workflow stability improvements.

**New Features:**
- **Multi-Perspective Stakeholder Review**: New `stakeholder-review` skill providing architect, security,
  quality, tester, and performance perspectives before code approval
- **Progress Indicators**: Long-running workflows now display progress bars for file operations,
  verification steps, and batch processing
- **Duplicate Task Resolution**: Automatic detection and handling of duplicate or obsolete tasks
- **Escalation Requirements**: When prevention rules already exist for a mistake type, escalation is
  required for pattern-level analysis
- **Main Agent Boundaries (M063)**: Main agent is orchestrator only - all code implementation must be
  delegated to subagents

**Bugfixes:**
- **STATE.md Verification (M085)**: Approval gates now verify STATE.md is committed before presentation
- **Lock Denial Guidance (M084)**: Clear instructions when task lock acquisition fails
- **Lock Expiration (M065)**: Removed automatic expiration - requires explicit user cleanup
- **Plugin Paths**: Replaced hardcoded paths with `CLAUDE_PLUGIN_ROOT` for portability

**Workflow Improvements:**
- **RCA A/B Testing**: Root cause analysis method comparison for effectiveness tracking
- **Skill Workflow Compliance**: SessionStart hook enforces complete skill execution
- **Release Plugin Skill**: Streamlined version release process with branch deletion
- **Validation-Driven Skills**: Restored shrink-doc and compare-docs with validation requirements
- **Worktree Directory**: Main agent must work from worktree, not project root
- **STATE.md Commit Rules**: Refined ordering and verification via hooks (M070, M076, M077)

**Documentation:**
- Contributing section clarifying project scope and plugin boundaries
- STATE.md template expanded with optional sections
- Parser test anti-patterns (M062)
- Spawn-subagent updated to use Task tool

### 2026-01-13: v1.3

**Maintenance Release**

- Renamed VERSION.md to CHANGELOG.md for clarity
- Merged v1.2 features into main branch
- **Bugfix**: Fixed SESSION_ID usage in skills (M058) - skills now correctly instruct agents to read
  SESSION_ID from conversation context (SessionStart system-reminder) instead of expecting a shell
  environment variable

### 2026-01-13: v1.2

**Auto-Decomposition and Parallel Execution**

Major enhancement enabling proactive task decomposition and parallel subagent execution.

**New Features:**
- **Auto-decomposition**: Tasks exceeding context threshold (default 40% of 200K = 80K tokens) are
  automatically decomposed before execution
- **Parallel execution**: Independent subtasks spawn concurrent subagents in sub-task-based execution
- **Task size estimation**: Pre-execution analysis estimates token requirements from PLAN.md
- **Mandatory token reporting**: Subagent execution reports always show token usage and compaction events

**Subagent Improvements:**
- Task-level locking prevents concurrent execution of same task
- Lightweight completion markers (`.completion.json`) for efficient monitoring
- Mandatory SESSION_ID verification before worktree creation (M057)
- Hook inheritance documentation for subagent prompts (A008)

**Workflow Fixes:**
- Subagent cleanup now happens BEFORE approval gate presentation (M053)
- Approval gate requires re-presentation after feedback (M052)
- Bugfix tests must be in same commit as fix (M051)
- Checkbox rendering fixed in cat:status output (M056)

**Git Safety Hooks:**
- Block `git merge --no-ff` (enforce linear history)
- Warn on `git filter-branch` (recommend git-filter-repo)
- Block deletion of `.git/refs/original` without explicit request
- Block `git reflog expire --expire=now` (preserve recovery options)

### 2026-01-12: v1.1

**Workflow Refinements and Context Injection**

Stabilization release with workflow improvements and better context management.

**Architecture Changes:**
- **Direct context injection**: Replaced CLAUDE.md injection with `inject-session-instructions.sh` hook
  for cleaner context loading
- **Flattened task structure**: Changed from nested directories to task ID format (e.g., `1.0-task-name`)
- **Commit message changelogs**: Task changelog content now embedded in commit messages, not separate files

**Workflow Improvements:**
- Integrated changelog workflow with minor/major version CHANGELOG.md files
- Clarified task STATE.md belongs with implementation commit
- Required subagent for bulk operations (shrink-doc)
- Improved git skills with safety requirements

**Bug Fixes:**
- Fixed workflow gaps that caused M020-M022 mistakes
- Fixed mistakes.json and retrospectives.json commit synchronization
- Retrospective action items A005, A007 implementation

**Documentation:**
- Comprehensive README rewrite with full documentation
- Updated tagline to "AI Agents that land on their feet"
- Added Session Instructions section to README
- Migration check for retrospective file path

### 2026-01-12: v1.0

CAT v1.0 introduces multi-agent orchestration for AI-assisted software development:

**Architecture:**
- **MAJOR → MINOR → TASK** hierarchy
- Multi-agent orchestration with parallel subagent execution
- Main agent coordinates, subagents execute in dedicated worktrees
- Token-aware task decomposition to prevent context overflow

**Commands:**
- `/cat:init` - Initialize CAT structure (new or existing project)
- `/cat:work` - Execute task (continues incomplete work)
- `/cat:status` - Show hierarchy status with visual tree
- `/cat:add-task`, `/cat:add-minor-version`, `/cat:add-major-version`
- `/cat:remove-task`, `/cat:remove-minor-version`, `/cat:remove-major-version`

**Skills:**
- `spawn-subagent` - Launch subagent with task context in isolated worktree
- `monitor-subagents` - Check status of running subagents including token usage
- `collect-results` - Gather results from completed subagents
- `merge-subagent` - Merge subagent branch into task branch
- `parallel-execute` - Orchestrate multiple independent subagents concurrently
- `decompose-task` - Split oversized tasks based on token analysis
- `token-report` - Generate detailed token usage report

**Document Types:**
- `STATE.md` - Task metadata and status tracking
- `PLAN.md` - Template-based planning (feature, bugfix, refactor)
- `CHANGELOG.md` - Minor/major version changelog (aggregates tasks)
- `ROADMAP.md` - Major/minor version overview
- `PROJECT.md` - Project overview and goals

> **NOTE**: Task-level changelog content is embedded in commit messages, not separate files.

**Configuration:**
- `cat-config.json` with yoloMode, contextLimit, targetContextUsage settings
- Interactive mode (default) with approval gates
- Yolo mode for automatic progression

**Quality Controls (preserved from v1.x):**
- Comprehensive planning before each task
- TDD integration to prevent regressions
- Learn-from-mistakes with conversation length analysis
- Git safety validation
