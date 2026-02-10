# Plan: json-core-api-migration

## Goal
Redesign core HookInput/HookOutput to use jackson-core streaming API, update all handler interface signatures from
JsonNode to Map<String, Object>, and migrate all simple handler implementations (~41 files with ≤4 JsonNode
occurrences). Keep jackson-databind temporarily in pom.xml for complex handlers migrated in the next sub-issue.

## Satisfies
Parent: optimize-hook-json-parser (core API migration)

## Risk Assessment
- **Risk Level:** HIGH
- **Concerns:** Changing API signatures across 50+ files; must maintain compilation. All handler interface signatures
  change simultaneously.
- **Mitigation:** Keep jackson-databind in pom.xml temporarily; run `mvn verify` after all changes; mechanical nature
  of most changes reduces error risk.

## Files to Modify

### Core infrastructure
- `hooks/src/main/java/io/github/cowwoc/cat/hooks/HookInput.java` - Replace JsonMapper/JsonNode with JsonParser,
  return Map<String, Object>
- `hooks/src/main/java/io/github/cowwoc/cat/hooks/HookOutput.java` - Replace JsonMapper/ObjectNode with manual JSON
  string building or JsonGenerator

### Handler interfaces (signature changes: JsonNode → Map<String, Object>)
- `hooks/src/main/java/io/github/cowwoc/cat/hooks/BashHandler.java`
- `hooks/src/main/java/io/github/cowwoc/cat/hooks/ReadHandler.java`
- `hooks/src/main/java/io/github/cowwoc/cat/hooks/EditHandler.java`
- `hooks/src/main/java/io/github/cowwoc/cat/hooks/AskHandler.java`
- `hooks/src/main/java/io/github/cowwoc/cat/hooks/FileWriteHandler.java`
- `hooks/src/main/java/io/github/cowwoc/cat/hooks/PosttoolHandler.java`
- `hooks/src/main/java/io/github/cowwoc/cat/hooks/TaskHandler.java`
- `hooks/src/main/java/io/github/cowwoc/cat/hooks/PromptHandler.java`

### Dispatcher/router classes (pass Map instead of JsonNode)
- `hooks/src/main/java/io/github/cowwoc/cat/hooks/GetBashPretoolOutput.java`
- `hooks/src/main/java/io/github/cowwoc/cat/hooks/GetBashPosttoolOutput.java`
- `hooks/src/main/java/io/github/cowwoc/cat/hooks/GetReadPretoolOutput.java`
- `hooks/src/main/java/io/github/cowwoc/cat/hooks/GetReadPosttoolOutput.java`
- `hooks/src/main/java/io/github/cowwoc/cat/hooks/GetPosttoolOutput.java`
- `hooks/src/main/java/io/github/cowwoc/cat/hooks/GetAskPretoolOutput.java`
- `hooks/src/main/java/io/github/cowwoc/cat/hooks/GetEditPretoolOutput.java`
- `hooks/src/main/java/io/github/cowwoc/cat/hooks/GetWriteEditPretoolOutput.java`
- `hooks/src/main/java/io/github/cowwoc/cat/hooks/GetTaskPretoolOutput.java`

### Simple handler implementations (≤4 JsonNode occurrences each, ~22 files)
- `hooks/src/main/java/io/github/cowwoc/cat/hooks/bash/BlockWorktreeCd.java`
- `hooks/src/main/java/io/github/cowwoc/cat/hooks/bash/BlockMainRebase.java`
- `hooks/src/main/java/io/github/cowwoc/cat/hooks/bash/BlockReflogDestruction.java`
- `hooks/src/main/java/io/github/cowwoc/cat/hooks/bash/BlockMergeCommits.java`
- `hooks/src/main/java/io/github/cowwoc/cat/hooks/bash/BlockLockManipulation.java`
- `hooks/src/main/java/io/github/cowwoc/cat/hooks/bash/ComputeBoxLines.java`
- `hooks/src/main/java/io/github/cowwoc/cat/hooks/bash/RemindGitSquash.java`
- `hooks/src/main/java/io/github/cowwoc/cat/hooks/bash/ValidateCommitType.java`
- `hooks/src/main/java/io/github/cowwoc/cat/hooks/bash/ValidateGitFilterBranch.java`
- `hooks/src/main/java/io/github/cowwoc/cat/hooks/bash/ValidateGitOperations.java`
- `hooks/src/main/java/io/github/cowwoc/cat/hooks/bash/WarnFileExtraction.java`
- `hooks/src/main/java/io/github/cowwoc/cat/hooks/bash/post/DetectConcatenatedCommit.java`
- `hooks/src/main/java/io/github/cowwoc/cat/hooks/bash/post/ValidateRebaseTarget.java`
- `hooks/src/main/java/io/github/cowwoc/cat/hooks/bash/post/VerifyCommitType.java`
- `hooks/src/main/java/io/github/cowwoc/cat/hooks/ask/WarnApprovalWithoutRenderDiff.java`
- `hooks/src/main/java/io/github/cowwoc/cat/hooks/ask/WarnUnsquashedApproval.java`
- `hooks/src/main/java/io/github/cowwoc/cat/hooks/edit/WarnSkillEditWithoutBuilder.java`
- `hooks/src/main/java/io/github/cowwoc/cat/hooks/write/EnforcePluginFileIsolation.java`
- `hooks/src/main/java/io/github/cowwoc/cat/hooks/write/WarnBaseBranchEdit.java`
- `hooks/src/main/java/io/github/cowwoc/cat/hooks/task/EnforceApprovalBeforeMerge.java`
- `hooks/src/main/java/io/github/cowwoc/cat/hooks/Config.java`
- `hooks/src/main/java/io/github/cowwoc/cat/hooks/skills/DisplayUtils.java`

### Medium-complexity implementations (4 occurrences each)
- `hooks/src/main/java/io/github/cowwoc/cat/hooks/TokenCounter.java`
- `hooks/src/main/java/io/github/cowwoc/cat/hooks/write/ValidateStateMdFormat.java`
- `hooks/src/main/java/io/github/cowwoc/cat/hooks/edit/EnforceWorkflowCompletion.java`

### Build (temporary bridge state)
- `hooks/pom.xml` - Add jackson-core dependency alongside jackson-databind (temporary)
- `hooks/src/main/java/io/github/cowwoc/cat/hooks/module-info.java` - Add `requires tools.jackson.core`

## Acceptance Criteria
- [ ] HookInput parses JSON using JsonParser (jackson-core streaming) into Map<String, Object>
- [ ] HookOutput builds JSON without ObjectNode (manual string building or JsonGenerator)
- [ ] All handler interfaces use Map<String, Object> instead of JsonNode
- [ ] All simple and medium handler implementations compile and work with new API
- [ ] `mvn -f hooks/pom.xml verify` passes
- [ ] jackson-databind remains temporarily for complex handlers (removed in next sub-issue)

## Execution Steps
1. **Add jackson-core to pom.xml** alongside jackson-databind and add `requires tools.jackson.core` to module-info
2. **Redesign HookInput:** Replace JsonMapper/JsonNode internals with JsonParser from jackson-core. Parse stdin
   JSON into `Map<String, Object>` where values are String for scalars, `Map<String, Object>` for nested objects,
   and `List<Object>` for arrays. Change `getObject()`, `getRaw()`, `getToolInput()`, `getToolResult()` to return
   `Map<String, Object>` instead of JsonNode. Remove jackson-databind imports.
3. **Redesign HookOutput:** Replace JsonMapper/ObjectNode with manual JSON string building. The output JSON is
   always simple flat objects with string values, so manual construction is sufficient. Remove jackson-databind
   imports.
4. **Update all handler interfaces:** Change method signatures from `JsonNode toolInput` to
   `Map<String, Object> toolInput` (and similarly for toolResult). Update BashHandler, ReadHandler, EditHandler,
   AskHandler, FileWriteHandler, PosttoolHandler, TaskHandler, PromptHandler.
5. **Update all dispatcher/router classes:** Change Get*Output classes to pass Map<String, Object> from HookInput
   to handler implementations.
6. **Migrate simple handler implementations:** For each of the ~22 simple files, update method signatures and
   replace any remaining JsonNode API calls with Map operations.
7. **Migrate medium-complexity implementations:** TokenCounter, ValidateStateMdFormat, EnforceWorkflowCompletion -
   these have a few more internal JsonNode usages to convert.
8. **Run `mvn -f hooks/pom.xml verify`** to ensure everything compiles and tests pass.

## Success Criteria
- [ ] `mvn -f hooks/pom.xml verify` exits 0
- [ ] HookInput uses jackson-core JsonParser internally
- [ ] All handler interfaces use Map<String, Object>
