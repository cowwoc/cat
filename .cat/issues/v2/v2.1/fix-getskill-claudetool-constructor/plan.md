# Goal

GetSkill's invokeSkillOutput() uses getConstructor(JvmScope.class) via reflection to instantiate SkillOutput classes, but 4 classes (GetOutput, GetNextIssueOutput, GetTokenReportOutput, GetStatusOutput) have constructors that take ClaudeTool instead of JvmScope. Java reflection requires exact parameter type match, so these constructors are never found. Additionally, GetSkill's main() uses MainJvmScope which doesn't implement ClaudeTool, so even if found, the scope couldn't be passed. Fix: Change GetSkill's main() to use MainClaudeTool and add ClaudeTool.class as a fallback constructor parameter type in invokeSkillOutput().

# Post-conditions

- GetSkill's main() uses MainClaudeTool instead of MainJvmScope
- invokeSkillOutput() tries ClaudeTool.class constructor as fallback after JvmScope.class
- All 4 affected SkillOutput classes (GetOutput, GetNextIssueOutput, GetTokenReportOutput, GetStatusOutput) can be instantiated via invokeSkillOutput()
- All existing tests pass
- E2E: Invoke /cat:add-agent and verify the preprocessor directive for get-output executes without error
