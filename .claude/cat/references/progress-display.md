# Progress Display Reference

Standard visual elements for displaying progress across CAT workflows.

## Progress Bar Format {#progress-bar-format}

**MANDATORY** for all progress displays.

### Algorithm

1. Bar width: 20 characters inside brackets
2. Filled characters: `=` for each 5% of progress (e.g., 75% = 15 `=` chars)
3. Arrow head: `>` at the end of filled section (except at 100%)
4. Empty characters: spaces for remaining width
5. Format: `[{filled}{arrow}{empty}] {percent}% ({completed}/{total} {unit})`

### Calculation

```
filled_count = floor(percentage / 5)
arrow = ">" if percentage < 100 else ""
empty_count = 20 - filled_count - len(arrow)
```

### Examples

| Percent | Progress Bar                          |
|---------|---------------------------------------|
| 0%      | `[>                   ] 0% (0/20)`    |
| 10%     | `[==>                 ] 10% (2/20)`   |
| 25%     | `[=====>              ] 25% (5/20)`   |
| 50%     | `[==========>         ] 50% (10/20)`  |
| 75%     | `[===============>    ] 75% (15/20)`  |
| 90%     | `[==================> ] 90% (18/20)`  |
| 100%    | `[====================] 100% (20/20)` |

### Usage Contexts

**Project-level progress** (status command):
```
**Progress:** [===============>    ] 75% (15/20 tasks)
```

**Task-level progress** (execute-task display):
```
**Progress:** [==========>         ] 50%
```

**Minor version progress**:
```
### v1.0: Description [=====>              ] 25% (1/4 tasks)
```

## Step Progress Format {#step-progress-format}

For multi-step workflow execution (distinct from completion progress):

```
[Step N/T] Step description [=====>              ] P% (Xs | ~Ys remaining)
```

Where:
- `N` = current step number
- `T` = total steps
- Visual bar = same algorithm as completion progress (20 chars, based on P%)
- `P%` = percentage through workflow
- `Xs` = elapsed time (e.g., `45s`, `2m`, `1h5m`)
- `~Ys` = estimated remaining (e.g., `~30s`, `~3m`)

### Examples

```
[Step 1/14] Verifying structure    [>                   ] 7% (2s | ~28s remaining)
[Step 7/14] Executing task         [==========>         ] 50% (1m | ~1m remaining)
[Step 14/14] Suggesting next action [====================] 100% (2m15s | done)
```
