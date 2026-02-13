# Version Tracking

## Version History

### 2026-01-12: v1.0

CAT v2.0 is a complete reimagining of the plugin, introducing:

**Architecture:**
- **MAJOR → MINOR → TASK** hierarchy (replaces MILESTONE → RELEASE → CHANGE)
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
