# Version Tracking

## Version History

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
- **Parallel execution**: Independent subtasks spawn concurrent subagents in wave-based execution
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

CAT v2.0 is a complete reimagining of the plugin, introducing:

**Architecture:**
- **MAJOR → MINOR → TASK** hierarchy
- Multi-agent orchestration with parallel subagent execution
- Main agent coordinates, subagents execute in dedicated worktrees
- Token-aware task decomposition to prevent context overflow

**Commands:**
- `/cat:init` - Initialize CAT structure (new or existing project)
- `/cat:execute-task` - Execute task (continues incomplete work)
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
