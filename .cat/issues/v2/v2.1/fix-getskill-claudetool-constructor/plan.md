# Goal

GetSkill's invokeSkillOutput() uses getConstructor(JvmScope.class) via reflection to instantiate SkillOutput
classes, but 4 classes (GetOutput, GetNextIssueOutput, GetTokenReportOutput, GetStatusOutput) have constructors
that take ClaudeTool instead of JvmScope. Java reflection requires exact parameter type match, so these
constructors are never found. Additionally, GetSkill's main() uses MainJvmScope which doesn't implement
ClaudeTool, so even if found, the scope couldn't be passed. Fix: Change GetSkill's main() to use MainClaudeTool
and add ClaudeTool.class as a fallback constructor parameter type in invokeSkillOutput().

# Post-conditions

- GetSkill's main() uses MainClaudeTool instead of MainJvmScope
- invokeSkillOutput() tries ClaudeTool.class constructor as fallback after JvmScope.class
- All 4 affected SkillOutput classes (GetOutput, GetNextIssueOutput, GetTokenReportOutput, GetStatusOutput) can
  be instantiated via invokeSkillOutput()
- All existing tests pass
- E2E: Invoke /cat:add-agent and verify the preprocessor directive for get-output executes without error

## Sub-Agent Waves

### Wave 1

- In `client/src/main/java/io/github/cowwoc/cat/hooks/util/GetSkill.java`, update `invokeSkillOutput()` to
  try `ClaudeTool.class` constructor first, then fall back to `JvmScope.class`, then to no-arg constructor.
  Exact change: replace the inner try-catch block at lines ~657-664 with three nested try-catch blocks:
  ```java
  Object instance;
  try
  {
    instance = targetClass.getConstructor(ClaudeTool.class).newInstance(scope);
  }
  catch (NoSuchMethodException _)
  {
    try
    {
      instance = targetClass.getConstructor(JvmScope.class).newInstance(scope);
    }
    catch (NoSuchMethodException _)
    {
      instance = targetClass.getConstructor().newInstance();
    }
  }
  ```
- In the same file `GetSkill.java`, verify that `main()` already uses `MainClaudeTool` (it does at line ~901).
  No change needed there.
- Add a new test in `GetSkillTest.java` to verify that a SkillOutput class with a ClaudeTool constructor
  (not JvmScope constructor) can be instantiated by invokeSkillOutput(). The test should:
  - Create a new test helper class `TestClaudeToolSkillOutput` in the test module with constructor
    `TestClaudeToolSkillOutput(ClaudeTool scope)` that implements SkillOutput
  - Set up a temp plugin dir with a launcher file for this class
  - Call GetSkill.load() with a skill that has a preprocessor directive pointing to this launcher
  - Verify the directive output is returned without preprocessor error
- Run `mvn -f client/pom.xml verify -e` to confirm all tests pass
- Update index.json: status=closed, progress=100%
