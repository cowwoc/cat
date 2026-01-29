package io.github.cowwoc.cat.hooks.skills;

/**
 * Output generator for /cat:help skill.
 *
 * Builds the complete help reference content.
 */
public final class GetHelpOutput
{
  private static final String WORKFLOW_DIAGRAM = """
      ```
      /cat:init --> /cat:add --> /cat:work
          ^                          |
          +------ /cat:status -------+
      ```""";

  private static final String HIERARCHY_TREE = """
      ```
      .claude/cat/
      +-- PROJECT.md              # Project overview
      +-- ROADMAP.md              # Version summaries
      +-- cat-config.json         # Configuration
      +-- v{major}/
          +-- STATE.md            # Major version state
          +-- PLAN.md             # Business-level plan
          +-- v{major}.{minor}/
              +-- STATE.md        # Minor version state
              +-- PLAN.md         # Feature-level plan
              +-- {task-name}/    # Tasks at minor level (2-level scheme)
              +-- v{major}.{minor}.{patch}/
                  +-- STATE.md    # Patch version state (optional 3-level)
                  +-- PLAN.md     # Patch-level plan
                  +-- {task-name}/  # Tasks at patch level
      ```""";

  /**
   * Creates a GetHelpOutput instance.
   */
  public GetHelpOutput()
  {
  }

  /**
   * Build and return help content.
   *
   * @return the formatted help content
   */
  public String getOutput()
  {
    return """
        # CAT Command Reference

        **CAT** enables hierarchical project planning with multi-agent task execution.

        ---

        ## Essential Commands (Start Here)

        These three commands cover 90% of daily use:

        | Command | What It Does |
        |---------|--------------|
        | `/cat:init` | Set up a new or existing project |
        | `/cat:status` | See what's happening and what to do next |
        | `/cat:work` | Execute the next available task |

        **Minimum viable workflow:**
        """ + WORKFLOW_DIAGRAM + """


        ---

        ## Planning Commands

        Use these when you need to structure your work:

        | Command | What It Does |
        |---------|--------------|
        | `/cat:add [desc]` | Add tasks/versions. With desc, creates task directly |
        | `/cat:remove` | Remove tasks or versions (with safety checks) |
        | `/cat:config` | Change workflow mode, trust level, preferences |

        ---

        ## Advanced Commands

        Power user features for complex workflows:

        | Command | What It Does |
        |---------|--------------|
        | `/cat:research` | Run stakeholder research on pending versions |
        | `/cat:cleanup` | Clean up abandoned worktrees and stale locks |
        | `/cat:spawn-subagent` | Launch isolated subagent for a task |
        | `/cat:monitor-subagents` | Check status of running subagents |
        | `/cat:collect-results` | Gather results from completed subagents |
        | `/cat:merge-subagent` | Merge subagent branch into task branch |
        | `/cat:token-report` | Generate token usage report |
        | `/cat:decompose-task` | Split oversized task into smaller tasks |
        | `/cat:parallel-execute` | Orchestrate multiple subagents concurrently |

        ---

        ## Full Reference

        <details>
        <summary>Hierarchy Structure</summary>

        CAT supports flexible version schemes:
        - **2-level:** MAJOR --> MINOR --> TASK (e.g., v1.0)
        - **3-level:** MAJOR --> MINOR --> PATCH --> TASK (e.g., v1.0.1)

        """ + HIERARCHY_TREE + """


        Task changelog content is embedded in commit messages.

        </details>

        <details>
        <summary>/cat:init Details</summary>

        Initialize CAT planning structure (new or existing project).
        - Creates PROJECT.md, ROADMAP.md, cat-config.json
        - Asks for trust level (how much autonomy your partner has)
        - For new projects: Deep questioning to gather project context
        - For existing codebases: Detects patterns and infers current state
        - Offers guided first-task creation after setup

        </details>

        <details>
        <summary>/cat:work Scope Options</summary>

        | Scope Format | Example | Behavior |
        |--------------|---------|----------|
        | (none) | `/cat:work` | Work through all incomplete tasks |
        | major | `/cat:work 1` | Work through all tasks in v1.x.x |
        | minor | `/cat:work 1.0` | Work through all tasks in v1.0.x |
        | patch | `/cat:work 1.0.1` | Work through all tasks in v1.0.1 |
        | task | `/cat:work 1.0-parse` | Work on specific task (2-level) |
        | task | `/cat:work 1.0.1-parse` | Work on specific task (3-level) |

        **Features:**
        - Auto-continues to next task when trust >= medium
        - Creates worktree and task branch per task
        - Spawns subagent for isolated execution
        - Monitors token usage
        - Runs approval gate (when trust < high)
        - Squashes commits by type
        - Merges to main and cleans up

        </details>

        <details>
        <summary>Task Naming Rules</summary>

        - Lowercase letters and hyphens only
        - Maximum 50 characters
        - Must be unique within minor version

        **Valid:** `parse-tokens`, `fix-memory-leak`, `add-user-auth`
        **Invalid:** `Parse_Tokens`, `fix memory leak`, `add-very-long-task-name-that-exceeds-limit`

        </details>

        <details>
        <summary>Branch Naming</summary>

        | Type | Pattern | Example |
        |------|---------|---------|
        | Task (2-level) | `{major}.{minor}-{task-name}` | `1.0-parse-tokens` |
        | Task (3-level) | `{major}.{minor}.{patch}-{task-name}` | `1.0.1-fix-edge-case` |
        | Subagent | `{task-branch}-sub-{uuid}` | `1.0-parse-tokens-sub-a1b2c3` |

        </details>

        ---

        ## Workflow Modes

        Set during `/cat:init` in cat-config.json:

        **Trust Levels**

        - **Low** - Check in often, verify each move
        - **Medium** (default) - Trust routine calls, review key decisions
        - **High** - Full autonomy, auto-merges on task completion

        Change anytime with `/cat:config` or edit `.claude/cat/cat-config.json`

        ---

        ## Common Workflows

        **Starting a new project:**
        ```
        /cat:init
        /cat:add          # Select "Major version", then "Task"
        /cat:work
        ```

        **Checking progress:**
        ```
        /cat:status
        ```

        **Adding more work:**
        ```
        /cat:add                       # Interactive: choose Task, Minor, or Major
        /cat:add make install easier   # Quick: creates task with description
        ```

        **Removing planned work:**
        ```
        /cat:remove       # Interactive: choose Task, Minor, or Major
        ```

        ## Configuration Options

        cat-config.json:
        ```json
        {
          "trust": "medium",            // low | medium | high (autonomy level)
          "verify": "changed",          // changed | all (verification scope)
          "curiosity": "medium",        // low | medium | high (exploration level)
          "patience": "medium"          // low | medium | high (refactoring tolerance)
        }
        ```

        **Note:** Context limits are fixed at 200K/40%/80% - see agent-architecture.md for details.

        ## Getting Help

        - Read `.claude/cat/PROJECT.md` for project vision
        - Check `.claude/cat/ROADMAP.md` for version overview
        - Use `/cat:status` to see current state
        - Review individual STATE.md files for detailed progress""";
  }
}
