/*
 * Copyright (c) 2026 Gili Tzabari. All rights reserved.
 *
 * Licensed under the CAT Commercial License.
 * See LICENSE.md in the project root for license terms.
 */
package io.github.cowwoc.cat.client.test.common;

import static io.github.cowwoc.requirements13.java.DefaultJavaValidators.requireThat;

import io.github.cowwoc.cat.client.test.TestUtils;
import io.github.cowwoc.cat.agent.PluginArtifactBuilder;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;
import org.testng.SkipException;
import org.testng.annotations.Test;

/**
 * Tests for {@link PluginArtifactBuilder}.
 */
public final class PluginArtifactBuilderTest
{
  private static final String MARKDOWN_LICENSE = """
    <!--
    Copyright (c) 2026 Gili Tzabari. All rights reserved.
    Licensed under the CAT Commercial License.
    See LICENSE.md in the project root for license terms.
    -->
    """;
  /**
   * Verifies that release artifacts contain only engine-specific files and copied license terms.
   */
  @Test
  public void buildFlattensEngineSpecificArtifacts() throws IOException, InterruptedException
  {
    Path tempDir = Files.createTempDirectory("test-");
    try
    {
      Path repoRoot = tempDir.resolve("repo");
      Path clientDir = repoRoot.resolve("client");
      Path pluginDir = clientDir.resolve("plugin");
      Path targetDir = clientDir.resolve("distribution/target/engine");
      createPluginSource(repoRoot, clientDir, pluginDir);

      new PluginArtifactBuilder(pluginDir, clientDir, targetDir).build();

      Path claudeRoot = targetDir.resolve("claude");
      Path codexRoot = targetDir.resolve("codex");
      requireThat(claudeRoot.resolve("LICENSE.md"), "claudeLicense").isRegularFile();
      requireThat(codexRoot.resolve("LICENSE.md"), "codexLicense").isRegularFile();
      requireThat(Files.readString(claudeRoot.resolve("client/VERSION"), StandardCharsets.UTF_8),
        "claudeVersion").isEqualTo("1.2.3\n");
      requireThat(Files.readString(codexRoot.resolve("client/VERSION"), StandardCharsets.UTF_8),
        "codexVersion").isEqualTo("1.2.3\n");
      requireThat(Files.readString(claudeRoot.resolve(".claude-plugin/plugin.json"), StandardCharsets.UTF_8),
        "claudePluginJson").doesNotContain("\"commands\"");
      requireThat(Files.readString(codexRoot.resolve(".codex-plugin/plugin.json"), StandardCharsets.UTF_8),
        "codexPluginJson").doesNotContain("\"commands\"");
      requireThat(countSkillDirectories(claudeRoot), "claudeSkillDirectoryCount").isEqualTo(2L);
      requireThat(countSkillDirectories(codexRoot), "codexSkillDirectoryCount").isEqualTo(3L);
      requireThat(claudeRoot.resolve("skills/common-skill/SKILL.md"), "claudeCommonSkill").isRegularFile();
      requireThat(Files.exists(claudeRoot.resolve("skills/uninstall/SKILL.md")),
        "claudeDoesNotShipUninstallSkill").isFalse();
      requireThat(claudeRoot.resolve("skills/common-skill/first-use.md"), "claudeFirstUse").isRegularFile();
      requireThat(claudeRoot.resolve("skills/common-skill/helper.md"), "claudeReferencedCompanion").isRegularFile();
      requireThat(Files.exists(claudeRoot.resolve("skills/common-skill/testing.md")),
        "claudeDoesNotShipUnreferencedCompanion").isFalse();
      requireThat(claudeRoot.resolve("skills/claude-skill/SKILL.md"), "claudeSkill").isRegularFile();
      requireThat(Files.exists(claudeRoot.resolve("skills/codex-skill/SKILL.md")),
        "claudeDoesNotSeeCodexSkill").isFalse();
      requireThat(codexRoot.resolve("skills/common-skill/SKILL.md"), "codexCommonSkill").isRegularFile();
      requireThat(codexRoot.resolve("skills/uninstall/SKILL.md"), "codexUninstallSkill").isRegularFile();
      requireThat(codexRoot.resolve("skills/common-skill/first-use.md"), "codexFirstUse").isRegularFile();
      requireThat(codexRoot.resolve("skills/codex-skill/SKILL.md"), "codexSkill").isRegularFile();
      requireThat(Files.exists(codexRoot.resolve("skills/claude-skill/SKILL.md")),
        "codexDoesNotSeeClaudeSkill").isFalse();
      requireThat(Files.exists(claudeRoot.resolve("agents/common/agent.md")),
        "claudeDoesNotShipCommonAgentSources").isFalse();
      requireThat(Files.exists(codexRoot.resolve("agents/common/agent.md")),
        "codexDoesNotShipCommonAgentSources").isFalse();
      requireThat(codexRoot.resolve("agents/agent.toml"), "codexAgentToml").isRegularFile();
      String codexAgent = Files.readString(codexRoot.resolve("agents/agent.toml"), StandardCharsets.UTF_8);
      requireThat(codexAgent, "codexAgent").contains("name = \"agent\"");
      requireThat(codexAgent, "codexAgent").doesNotContain("Copyright (c) 2026");
      requireThat(Files.exists(claudeRoot.resolve("skills/common-skill/tests/test.bats")),
        "claudeDoesNotShipSkillTests").isFalse();
      requireThat(Files.exists(claudeRoot.resolve("skills/common-skill/instruction-test/case.md")),
        "claudeDoesNotShipInstructionTests").isFalse();
      requireThat(Files.exists(codexRoot.resolve("skills/common-skill/tests/test.bats")),
        "codexDoesNotShipSkillTests").isFalse();
      requireThat(Files.exists(codexRoot.resolve("skills/common-skill/instruction-test/case.md")),
        "codexDoesNotShipInstructionTests").isFalse();
      requireThat(claudeRoot.resolve("hooks/hooks.json"), "claudeHookRegistration").isRegularFile();
      requireThat(codexRoot.resolve("hooks/hooks.json"), "codexHookRegistration").isRegularFile();
      requireThat(Files.readString(claudeRoot.resolve("client/bin/pre-read"), StandardCharsets.UTF_8),
        "claudePreReadLauncher").contains("io.github.cowwoc.cat.claude.hook.PreReadHook");
      requireThat(Files.exists(codexRoot.resolve("client/bin/pre-read")),
        "codexDoesNotShipClaudePreReadLauncher").isFalse();
      requireThat(Files.readString(codexRoot.resolve("client/bin/pre-bash"), StandardCharsets.UTF_8),
        "codexPreBashLauncher").contains("io.github.cowwoc.cat.codex.hook.PreBashHook");
      requireThat(Files.exists(codexRoot.resolve("client/bin/pre-write")),
        "codexDoesNotShipUnsupportedPreWriteLauncher").isFalse();
      String preBashOutput = runLauncher(codexRoot.resolve("client/bin/pre-bash"));
      requireThat(preBashOutput, "preBashOutput").contains(
        codexRoot.resolve("client/bin/../lib/server/aot-cache.aot").toString());
      requireThat(preBashOutput, "preBashOutput").contains(
        "io.github.cowwoc.cat.codex.cli/io.github.cowwoc.cat.codex.hook.PreBashHook");
      String sessionStartOutput = runLauncher(codexRoot.resolve("client/bin/session-start"));
      requireThat(sessionStartOutput, "sessionStartOutput").contains(
        codexRoot.resolve("client/bin/../lib/server/aot-cache.aot").toString());
      requireThat(sessionStartOutput, "sessionStartOutput").contains(
        "io.github.cowwoc.cat.codex.cli/io.github.cowwoc.cat.codex.hook.SessionStartHook");
      requireThat(Files.exists(claudeRoot.resolve("client/bin/claude")), "claudeLauncherVariantsRemoved").isFalse();
      requireThat(Files.exists(codexRoot.resolve("client/bin/codex")), "codexLauncherVariantsRemoved").isFalse();
      requireThat(claudeRoot.resolve("hooks/common/shared.sh"), "claudeCommonHook").isRegularFile();
      requireThat(codexRoot.resolve("hooks/common/shared.sh"), "codexCommonHook").isRegularFile();
      requireThat(claudeRoot.resolve("hooks/claude/session-start.sh"), "claudeSessionStart").isRegularFile();
      requireThat(Files.isExecutable(claudeRoot.resolve("hooks/claude/session-start.sh")),
        "claudeSessionStartExecutable").isTrue();
      requireThat(codexRoot.resolve("hooks/codex/session-start.sh"), "codexSessionStart").isRegularFile();
      requireThat(Files.isExecutable(codexRoot.resolve("hooks/codex/session-start.sh")),
        "codexSessionStartExecutable").isTrue();
      requireThat(Files.exists(claudeRoot.resolve("commands")), "claudeDoesNotShipCommands").isFalse();
      requireThat(Files.exists(codexRoot.resolve("commands")), "codexDoesNotShipCommands").isFalse();
      requireThat(claudeRoot.resolve("lib/git-filter-repo-linux-x64"), "claudeBundledGitFilterRepo").isRegularFile();
      requireThat(Files.isExecutable(claudeRoot.resolve("lib/git-filter-repo-linux-x64")),
        "claudeBundledGitFilterRepoExecutable").isTrue();
      requireThat(Files.exists(claudeRoot.resolve("lib/git-filter-repo-linux-x64.tmp.1234.5678")),
        "claudeTransientGitFilterRepoDownload").isFalse();
      requireThat(codexRoot.resolve("lib/git-filter-repo-linux-x64"), "codexBundledGitFilterRepo").isRegularFile();
      requireThat(Files.isExecutable(codexRoot.resolve("lib/git-filter-repo-linux-x64")),
        "codexBundledGitFilterRepoExecutable").isTrue();
      requireThat(Files.exists(codexRoot.resolve("lib/git-filter-repo-linux-x64.tmp.1234.5678")),
        "codexTransientGitFilterRepoDownload").isFalse();

      requireThat(claudeRoot.resolve("skills/claude-skill/SKILL.md"), "claudeSkillAfterGeneration").
        isRegularFile();

      Files.createDirectories(claudeRoot.resolve("skills/stale-skill"));
      Files.writeString(claudeRoot.resolve("skills/stale-skill/SKILL.md"), "stale\n", StandardCharsets.UTF_8);
      new PluginArtifactBuilder(pluginDir, clientDir, targetDir).build();
      requireThat(Files.exists(claudeRoot.resolve("skills/stale-skill/SKILL.md")),
        "staleSkillRemoved").isFalse();
      requireThat(claudeRoot.resolve("skills/common-skill/SKILL.md"), "claudeCommonSkillAfterRebuild").
        isRegularFile();
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that engine-specific skill wrappers can share common skill bodies and companion files.
   *
   * @throws IOException if file operations fail
   */
  @Test
  public void buildEngineSkillWrappersIncludeCommonCompanions() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-");
    try
    {
      Path repoRoot = tempDir.resolve("repo");
      Path clientDir = repoRoot.resolve("client");
      Path pluginDir = clientDir.resolve("plugin");
      Path targetDir = clientDir.resolve("distribution/target/engine");
      createPluginSource(repoRoot, clientDir, pluginDir);
      Path commonSkill = pluginDir.resolve("skills/common/common-skill");
      Files.delete(commonSkill.resolve("SKILL.md"));
      Files.createDirectories(pluginDir.resolve("skills/include"));
      Files.writeString(pluginDir.resolve("skills/include/common-skill.md"), MARKDOWN_LICENSE + "shared wrapper body\n",
        StandardCharsets.UTF_8);
      Files.createDirectories(pluginDir.resolve("skills/claude/common-skill"));
      Files.writeString(pluginDir.resolve("skills/claude/common-skill/SKILL.md"),
        MARKDOWN_LICENSE + "---\ndescription: Shared Claude skill\nmodel: haiku\neffort: low\n---\n" +
          "<!-- cat:include ../../include/common-skill.md -->\n",
        StandardCharsets.UTF_8);
      Files.createDirectories(pluginDir.resolve("skills/codex/common-skill"));
      Files.writeString(pluginDir.resolve("skills/codex/common-skill/SKILL.md"),
        MARKDOWN_LICENSE + "---\ndescription: Shared Codex skill\n---\n" +
          "<!-- cat:include ../../include/common-skill.md -->\n",
        StandardCharsets.UTF_8);

      new PluginArtifactBuilder(pluginDir, clientDir, targetDir).build();

      Path claudeSkill = targetDir.resolve("claude/skills/common-skill");
      Path codexSkill = targetDir.resolve("codex/skills/common-skill");
      requireThat(Files.readString(claudeSkill.resolve("SKILL.md"), StandardCharsets.UTF_8),
        "claudeSharedSkill").contains("shared wrapper body");
      requireThat(Files.readString(codexSkill.resolve("SKILL.md"), StandardCharsets.UTF_8),
        "codexSharedSkill").contains("shared wrapper body");
      requireThat(claudeSkill.resolve("first-use.md"), "claudeFirstUse").isRegularFile();
      requireThat(codexSkill.resolve("first-use.md"), "codexFirstUse").isRegularFile();
      requireThat(claudeSkill.resolve("helper.md"), "claudeHelper").isRegularFile();
      requireThat(codexSkill.resolve("helper.md"), "codexHelper").isRegularFile();
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that engine-specific companion files shadow common companion files that are referenced by the common
   * skill body.
   *
   * @throws IOException if file operations fail
   */
  @Test
  public void buildEngineSkillWrappersShadowCommonCompanions() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-");
    try
    {
      Path repoRoot = tempDir.resolve("repo");
      Path clientDir = repoRoot.resolve("client");
      Path pluginDir = clientDir.resolve("plugin");
      Path targetDir = clientDir.resolve("distribution/target/engine");
      createPluginSource(repoRoot, clientDir, pluginDir);
      Files.createDirectories(pluginDir.resolve("skills/claude/common-skill"));
      Files.writeString(pluginDir.resolve("skills/claude/common-skill/SKILL.md"),
        MARKDOWN_LICENSE + "---\ndescription: Shared Claude skill\nmodel: haiku\neffort: low\n---\n" +
          "<!-- cat:include ../../common/common-skill/SKILL.md -->\n",
        StandardCharsets.UTF_8);
      Files.writeString(pluginDir.resolve("skills/claude/common-skill/helper.md"),
        MARKDOWN_LICENSE + "claude overlay content\n", StandardCharsets.UTF_8);

      new PluginArtifactBuilder(pluginDir, clientDir, targetDir).build();

      Path claudeHelper = targetDir.resolve("claude/skills/common-skill/helper.md");
      String helper = Files.readString(claudeHelper, StandardCharsets.UTF_8);
      requireThat(helper, "claudeHelper").contains("claude overlay content").doesNotContain("helper body");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that shared skill fragments can render engine-specific command prefixes.
   *
   * @throws IOException if file operations fail
   */
  @Test
  public void buildReplacesEngineCommandPrefixPlaceholders() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-");
    try
    {
      Path repoRoot = tempDir.resolve("repo");
      Path clientDir = repoRoot.resolve("client");
      Path pluginDir = clientDir.resolve("plugin");
      Path targetDir = clientDir.resolve("distribution/target/engine");
      createPluginSource(repoRoot, clientDir, pluginDir);
      Path commonSkill = pluginDir.resolve("skills/common/common-skill");
      Files.writeString(commonSkill.resolve("first-use.md"),
        MARKDOWN_LICENSE + "Run ${CAT_COMMAND_PREFIX}cat:status\\n", StandardCharsets.UTF_8);

      new PluginArtifactBuilder(pluginDir, clientDir, targetDir).build();

      String claudeFirstUse = Files.readString(
        targetDir.resolve("claude/skills/common-skill/first-use.md"), StandardCharsets.UTF_8);
      String codexFirstUse = Files.readString(
        targetDir.resolve("codex/skills/common-skill/first-use.md"), StandardCharsets.UTF_8);
      requireThat(claudeFirstUse, "claudeFirstUse").contains("Run /cat:status");
      requireThat(claudeFirstUse, "claudeFirstUse").doesNotContain("${CAT_COMMAND_PREFIX}");
      requireThat(codexFirstUse, "codexFirstUse").contains("Run $cat:status");
      requireThat(codexFirstUse, "codexFirstUse").doesNotContain("${CAT_COMMAND_PREFIX}");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that shared skill fragments can render engine-specific deterministic output commands.
   *
   * @throws IOException if file operations fail
   */
  @Test
  public void buildReplacesEngineOutputRenderDirectives() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-");
    try
    {
      Path repoRoot = tempDir.resolve("repo");
      Path clientDir = repoRoot.resolve("client");
      Path pluginDir = clientDir.resolve("plugin");
      Path targetDir = clientDir.resolve("distribution/target/engine");
      createPluginSource(repoRoot, clientDir, pluginDir);
      Path commonSkill = pluginDir.resolve("skills/common/common-skill");
      Files.writeString(commonSkill.resolve("first-use.md"),
        MARKDOWN_LICENSE + "<!-- cat:render-output get-status-output -->\n", StandardCharsets.UTF_8);

      new PluginArtifactBuilder(pluginDir, clientDir, targetDir).build();

      String claudeFirstUse = Files.readString(
        targetDir.resolve("claude/skills/common-skill/first-use.md"), StandardCharsets.UTF_8);
      String codexFirstUse = Files.readString(
        targetDir.resolve("codex/skills/common-skill/first-use.md"), StandardCharsets.UTF_8);
      requireThat(claudeFirstUse, "claudeFirstUse").
        contains("Render the display with the deterministic Java output command. " +
          "Return the generated display exactly.").
        contains("!`: \"${CAT_PLUGIN_ROOT:?CAT_PLUGIN_ROOT is required}\"; " +
          "if [ -z \"${CAT_PLUGIN_DATA:-}\" ]; then echo \"CAT_PLUGIN_DATA is required\" >&2; exit 1; fi; " +
          "\"${CAT_PLUGIN_ROOT}/client/bin/get-status-output\"`").
        doesNotContain("cat:render-output").
        doesNotContain("\"$0\"").
        doesNotContain("```bash");
      requireThat(codexFirstUse, "codexFirstUse").
        contains("Render the display with the deterministic Java output command. " +
          "Return the generated display exactly.").
        contains("Run the deterministic implementation through Bash:").
        contains("\"${CAT_PLUGIN_ROOT}/client/bin/get-status-output\"").
        doesNotContain("cat:render-output").
        doesNotContain("!`");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that malformed render-output tokens fail the artifact build instead of generating shell text.
   *
   * @throws IOException if file operations fail
   */
  @Test
  public void buildRejectsInvalidRenderOutputDirectiveToken() throws IOException
  {
    assertRenderOutputDirectiveRejected("<!-- cat:render-output get-output bad$token -->",
      "Invalid cat:render-output token: bad$token");
  }

  /**
   * Verifies that blank render-output directives fail the artifact build instead of leaking into engine artifacts.
   *
   * @throws IOException if file operations fail
   */
  @Test
  public void buildRejectsBlankRenderOutputDirective() throws IOException
  {
    assertRenderOutputDirectiveRejected("<!-- cat:render-output -->",
      "cat:render-output command must not be blank");
  }

  /**
   * Verifies that render-output directives require the first token to be a deterministic command.
   *
   * @throws IOException if file operations fail
   */
  @Test
  public void buildRejectsRenderOutputPlaceholderCommand() throws IOException
  {
    assertRenderOutputDirectiveRejected("<!-- cat:render-output <issue-path> -->",
      "cat:render-output command must not be a placeholder: <issue-path>");
  }

  /**
   * Verifies that render-output placeholders must use literal angle brackets, not HTML entities.
   *
   * @throws IOException if file operations fail
   */
  @Test
  public void buildRejectsEscapedRenderOutputPlaceholder() throws IOException
  {
    assertRenderOutputDirectiveRejected("<!-- cat:render-output get-output &lt;issue-path&gt; -->",
      "Invalid cat:render-output token: &lt;issue-path&gt;");
  }

  private static void assertRenderOutputDirectiveRejected(String directive, String expectedMessage)
    throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-");
    try
    {
      Path repoRoot = tempDir.resolve("repo");
      Path clientDir = repoRoot.resolve("client");
      Path pluginDir = clientDir.resolve("plugin");
      Path targetDir = clientDir.resolve("distribution/target/engine");
      createPluginSource(repoRoot, clientDir, pluginDir);
      Path commonSkill = pluginDir.resolve("skills/common/common-skill");
      Files.writeString(commonSkill.resolve("first-use.md"),
        MARKDOWN_LICENSE + directive + "\n", StandardCharsets.UTF_8);

      try
      {
        new PluginArtifactBuilder(pluginDir, clientDir, targetDir).build();
      }
      catch (IllegalStateException e)
      {
        requireThat(e.getMessage(), "message").contains(expectedMessage);
        return;
      }
      throw new AssertionError("Expected render-output directive rejection");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies current engine artifacts expand shared agent contracts for both engines.
   *
   * @throws IOException if file operations fail
   */
  @Test
  public void buildCurrentEngineArtifactsExpandAgentContracts() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-");
    try
    {
      Path sourceRoot = findSourceRoot();
      Path tempRepo = tempDir.resolve("repo");
      Path tempClient = tempRepo.resolve("client");
      Files.createDirectories(tempRepo);
      Files.writeString(tempRepo.resolve("LICENSE.md"), "license\n", StandardCharsets.UTF_8);
      copyDirectory(sourceRoot.resolve("client/plugin"), tempClient.resolve("plugin"));
      ensureBundledGitFilterRepo(tempClient.resolve("plugin"));

      Path targetDir = tempClient.resolve("distribution/target/engine");
      new PluginArtifactBuilder(tempClient.resolve("plugin"), tempClient, targetDir).build();

      for (String engine : new String[]{"claude", "codex"})
      {
        Path engineRoot = targetDir.resolve(engine);
        assertEngineArtifactDoesNotContain(engineRoot, "cat:include", engine + "UnresolvedInclude");
        assertEngineArtifactDoesNotContain(engineRoot, "${CAT_COMMAND_PREFIX}", engine + "UnresolvedPrefix");
        String helpFirstUse = Files.readString(engineRoot.resolve("skills/help/first-use.md"),
          StandardCharsets.UTF_8);
        requireThat(helpFirstUse, engine + "HelpFirstUse").doesNotContain("cat:include");
        requireThat(helpFirstUse, engine + "HelpFirstUse").doesNotContain("${CAT_COMMAND_PREFIX}");
        assertOutputRenderSkillConvention(engineRoot, engine, "get-diff", "get-output");
        assertOutputRenderSkillConvention(engineRoot, engine, "status", "get-status-output");
        assertOutputRenderSkillConvention(engineRoot, engine, "token-report", "get-output");
        assertOutputRenderSkillConvention(engineRoot, engine, "work-complete", "get-output");
        String workExecuteAgent = "agents/work-execute.toml";
        if (engine.equals("claude"))
          workExecuteAgent = "agents/work-execute.md";
        String workExecute = Files.readString(engineRoot.resolve(workExecuteAgent), StandardCharsets.UTF_8);
        requireThat(workExecute, engine + "WorkExecute").contains(
          "Read plan.md before deciding whether implementation is already applied");
        requireThat(workExecute, engine + "WorkExecute").contains(
          "A clean pre-implementation branch with no implementation diff is normal");
        requireThat(workExecute, engine + "WorkExecute").contains(
          "Do not classify an empty implementation diff as already applied");
        requireThat(workExecute, engine + "WorkExecute").contains(
          "Only use the already-applied path when there is positive evidence");
        requireThat(workExecute, engine + "WorkExecute").contains(
          "If positive evidence is absent, proceed with the implementation plan even when");
        requireThat(workExecute, engine + "WorkExecute").contains(
          "`git diff ${TARGET_BRANCH}..HEAD -- <implementation-files>` is empty");
        requireThat(workExecute, engine + "WorkExecute").contains("ALREADY_IMPLEMENTED");
        assertNoEmptyDiffAlreadyImplementedRule(workExecute, engine + "WorkExecute");
        requireThat(workExecute, engine + "WorkExecute").doesNotContain("cat:include");

        String workImplement = Files.readString(engineRoot.resolve("skills/work-implement/first-use.md"),
          StandardCharsets.UTF_8);
        assertWorkImplementCleanupConvention(workImplement, engine + "WorkImplement");
        requireThat(workImplement, engine + "WorkImplement").doesNotContain("cat:include");

        String workVerifyAgent = "agents/work-verify.toml";
        if (engine.equals("claude"))
          workVerifyAgent = "agents/work-verify.md";
        String workVerify = Files.readString(engineRoot.resolve(workVerifyAgent), StandardCharsets.UTF_8);
        assertContainsNormalized(workVerify, engine + "WorkVerify",
          "E2E tests must use the engine selected by `CAT_ENGINE`");
        assertContainsNormalized(workVerify, engine + "WorkVerify",
          "E2E runs must use the selected engine's artifacts and engine-native test infrastructure");
        assertNoEngineSpecificCommonE2ESkip(workVerify, engine + "WorkVerify");
        assertNoBlanketCodexE2ESkipRule(workVerify, engine + "WorkVerify");
        requireThat(workVerify, engine + "WorkVerify").doesNotContain("cat:include");

        String workConfirm = Files.readString(engineRoot.resolve("skills/work-confirm/first-use.md"),
          StandardCharsets.UTF_8);
        assertContainsNormalized(workConfirm, engine + "WorkConfirm",
          "E2E tests must use the engine selected by `CAT_ENGINE`");
        assertContainsNormalized(workConfirm, engine + "WorkConfirm",
          "E2E runs must use the selected engine's artifacts and engine-native test infrastructure");
        assertNoEngineSpecificCommonE2ESkip(workConfirm, engine + "WorkConfirm");
        assertNoBlanketCodexE2ESkipRule(workConfirm, engine + "WorkConfirm");
        requireThat(workConfirm, engine + "WorkConfirm").doesNotContain("cat:include");

        String stakeholderReview = Files.readString(engineRoot.resolve("skills/stakeholder-review/first-use.md"),
          StandardCharsets.UTF_8);
        assertContainsNormalized(stakeholderReview, engine + "StakeholderReview",
          "Prepare prompts: for each stakeholder in $SELECTED, collect conventions from CONVENTION_MAP, gather " +
            "ISSUE_PLAN_PATH and VERSION_PLAN_PATH (use VERSION_ID extraction from Step 1), extract " +
            "DOMAIN_KNOWLEDGE from plan.md `## Domain Knowledge` section (if present), and convert CHANGED_FILES " +
            "to bullets.");
        requireThat(stakeholderReview, engine + "StakeholderReview").contains("Spawn each stakeholder with:");
        requireThat(stakeholderReview, engine + "StakeholderReview").contains("## Working Directory");
        requireThat(stakeholderReview, engine + "StakeholderReview").contains("WORKTREE_PATH={WORKTREE_PATH}");
        requireThat(stakeholderReview, engine + "StakeholderReview").contains(
          "Changed files (read from WORKTREE_PATH): {CHANGED_FILES_BULLETS}");
        requireThat(stakeholderReview, engine + "StakeholderReview").contains(
          "DISPATCH_HEAD_SHA=$(git rev-parse --verify \"HEAD^{commit}\")");
        requireThat(stakeholderReview, engine + "StakeholderReview").contains(
          "Reviewer dispatch HEAD ${DISPATCH_HEAD_SHA} does not match manifest HEAD ${HEAD_SHA}");
        requireThat(stakeholderReview, engine + "StakeholderReview").contains(
          "Before reading files, verify that the current worktree HEAD is exactly `{HEAD_SHA}`");
        requireThat(stakeholderReview, engine + "StakeholderReview").contains(
          "return REJECTED with a reviewer execution concern instead of reviewing stale content");
        requireThat(stakeholderReview, engine + "StakeholderReview").contains(
          "The line above is canonical and must remain the only worktree variable assignment in this prompt.");
        requireThat(stakeholderReview, engine + "StakeholderReview").doesNotContain(
          "\nWORKTREE_PATH: {WORKTREE_PATH}");
        requireThat(stakeholderReview, engine + "StakeholderReview").contains(
          "Read every changed file using absolute paths rooted at {WORKTREE_PATH}/.");
        requireThat(stakeholderReview, engine + "StakeholderReview").contains(
          "Each reviewer MUST also use its stakeholder-specific agent type");
        requireThat(stakeholderReview, engine + "StakeholderReview").contains(
          "Do NOT use a generic/default agent type for stakeholder review");
        requireThat(stakeholderReview, engine + "StakeholderReview").contains(
          "Reviewer agents are leaf reviewers");
        requireThat(stakeholderReview, engine + "StakeholderReview").contains(
          "Do NOT call `spawn_agent`, `wait_agent`, `list_agents`,");
        requireThat(stakeholderReview, engine + "StakeholderReview").contains(
          "agent_type=<stakeholder-agent-type>");
        requireThat(stakeholderReview, engine + "StakeholderReview").contains(
          "subagent_type=<stakeholder-agent-type>");
        requireThat(stakeholderReview, engine + "StakeholderReview").contains("spawn_agent(message=prompt");
        requireThat(stakeholderReview, engine + "StakeholderReview").contains("Agent(prompt=prompt");
        requireThat(stakeholderReview, engine + "StakeholderReview").doesNotContain("cat:include");

        String workReview = Files.readString(engineRoot.resolve("skills/work-review/first-use.md"),
          StandardCharsets.UTF_8);
        requireThat(workReview, engine + "WorkReview").contains("reviewed_head_sha");
        requireThat(workReview, engine + "WorkReview").contains(
          "REVIEWED_HEAD_SHA=$(cd \"${WORKTREE_PATH}\" && git rev-parse HEAD)");
        requireThat(workReview, engine + "WorkReview").contains(
          "reject stale review results after later implementation changes");
        assertWorkReviewAutoFixPlanArtifactContract(workReview, engine + "WorkReview");
        requireThat(workReview, engine + "WorkReview").doesNotContain("cat:include");

        String workMerge = Files.readString(engineRoot.resolve("skills/work-merge/first-use.md"),
          StandardCharsets.UTF_8);
        requireThat(workMerge, engine + "WorkMerge").contains("PERSISTED_HEAD_SHA=");
        requireThat(workMerge, engine + "WorkMerge").contains(
          "CURRENT_HEAD_SHA=$(cd \"${WORKTREE_PATH}\" && git rev-parse HEAD)");
        requireThat(workMerge, engine + "WorkMerge").contains("Review result is stale");
        requireThat(workMerge, engine + "WorkMerge").contains(
          "Re-run stakeholder review after the latest implementation change before presenting the approval gate");
        requireThat(workMerge, engine + "WorkMerge").contains(
          "case-insensitive exact match");
        requireThat(workMerge, engine + "WorkMerge").contains(
          "${USER_RESPONSE,,}");
        requireThat(workMerge, engine + "WorkMerge").contains(
          "Do not rerun `cat:stakeholder-review` solely because the rebase changed the commit SHA");
        requireThat(workMerge, engine + "WorkMerge").contains(
          "Preserve the approval only when the rebase is mechanical");
        requireThat(workMerge, engine + "WorkMerge").contains(
          "write `approved:invalidated`");
        requireThat(workMerge, engine + "WorkMerge").contains(
          "return to the appropriate review or approval path");
        requireThat(workMerge, engine + "WorkMerge").doesNotContain("cat:include");

        String workWithIssue = Files.readString(engineRoot.resolve("skills/work-with-issue/first-use.md"),
          StandardCharsets.UTF_8);
        requireThat(workWithIssue, engine + "WorkWithIssue").contains("Step 5 freshness check");
        requireThat(workWithIssue, engine + "WorkWithIssue").contains(
          "the merge phase must block the approval gate when the persisted");
        requireThat(workWithIssue, engine + "WorkWithIssue").doesNotContain("cat:include");

        String configFirstUse = Files.readString(engineRoot.resolve("skills/config/first-use.md"),
          StandardCharsets.UTF_8);
        String configSettingsStep = configFirstUse.substring(configFirstUse.indexOf("### Step 2:"),
          configFirstUse.indexOf("### Step 3:"));
        if (engine.equals("claude"))
        {
          requireThat(configSettingsStep, "claudeConfigSettingsStep").
            contains("!`: \"${CAT_PLUGIN_ROOT:?CAT_PLUGIN_ROOT is required}\"; " +
              "\"${CAT_PLUGIN_ROOT}/client/bin/get-output\" \"$0\" config.settings`").
            contains("<output type=\"config.settings\">").
            doesNotContain("INVOKE: Skill(\"cat:get-output\", args=\"config.settings\")");
        }
        else
        {
          requireThat(configSettingsStep, "codexConfigSettingsStep").
            contains("INVOKE: Skill(\"cat:get-output\", args=\"config.settings\")").
            doesNotContain("!`");
        }
      }
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies common output-rendering skills use render directives without repeating generated boilerplate.
   *
   * @throws IOException if file operations fail
   */
  @Test
  public void sourceOutputRenderSkillsOnlyUseRenderDirectives() throws IOException
  {
    Path sourceRoot = findSourceRoot();
    Path skillsRoot = sourceRoot.resolve("client/plugin/skills");
    for (String skillName : new String[]{"get-diff", "status", "token-report", "work-complete"})
    {
      String firstUse = Files.readString(skillsRoot.resolve("common/" + skillName + "/first-use.md"),
        StandardCharsets.UTF_8);
      requireThat(firstUse, skillName + "FirstUse").doesNotContain("cat:include");
      requireThat(firstUse, skillName + "FirstUse").contains("cat:render-output");
      requireThat(firstUse, skillName + "FirstUse").doesNotContain("deterministic Java output command");
      requireThat(firstUse, skillName + "FirstUse").doesNotContain("Return the generated display exactly");
      requireThat(Files.exists(skillsRoot.resolve("include/" + skillName + ".md"), LinkOption.NOFOLLOW_LINKS),
        skillName + "IncludeExists").isFalse();
    }
  }

  /**
   * Verifies that implementation agent worktrees and branches are cleaned up after successful merges.
   *
   * @throws IOException if file operations fail
   */
  @Test
  public void workImplementCleansUpSubagentWorktreesAfterMerge() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-");
    try
    {
      Path sourceRoot = findSourceRoot();
      Path tempRepo = tempDir.resolve("repo");
      Path tempClient = tempRepo.resolve("client");
      Files.createDirectories(tempRepo);
      Files.writeString(tempRepo.resolve("LICENSE.md"), "license\n", StandardCharsets.UTF_8);
      copyDirectory(sourceRoot.resolve("client/plugin"), tempClient.resolve("plugin"));
      ensureBundledGitFilterRepo(tempClient.resolve("plugin"));

      Path targetDir = tempClient.resolve("distribution/target/engine");
      new PluginArtifactBuilder(tempClient.resolve("plugin"), tempClient, targetDir).build();

      for (String engine : new String[]{"claude", "codex"})
      {
        Path workImplement = targetDir.resolve(engine + "/skills/work-implement/first-use.md");
        String firstUse = Files.readString(workImplement, StandardCharsets.UTF_8);
        assertWorkImplementCleanupConvention(firstUse, engine + "WorkImplementCleanup");
      }
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that the generated stakeholder-review dispatch guard refuses stale manifest HEADs
   * before reviewer agents can be spawned.
   *
   * @throws IOException if file operations fail
   * @throws InterruptedException if interrupted while running the guard
   */
  @Test
  public void stakeholderReviewDispatchGuardBlocksStaleHeadBeforeReviewerSpawn()
    throws IOException, InterruptedException
  {
    Path tempDir = Files.createTempDirectory("test-");
    try
    {
      Path sourceRoot = findSourceRoot();
      Path tempRepo = tempDir.resolve("repo");
      Path tempClient = tempRepo.resolve("client");
      Files.createDirectories(tempRepo);
      Files.writeString(tempRepo.resolve("LICENSE.md"), "license\n", StandardCharsets.UTF_8);
      copyDirectory(sourceRoot.resolve("client/plugin"), tempClient.resolve("plugin"));
      ensureBundledGitFilterRepo(tempClient.resolve("plugin"));

      Path targetDir = tempClient.resolve("distribution/target/engine");
      new PluginArtifactBuilder(tempClient.resolve("plugin"), tempClient, targetDir).build();
      String stakeholderReview = Files.readString(
        targetDir.resolve("claude/skills/stakeholder-review/first-use.md"), StandardCharsets.UTF_8);
      String dispatchGuard = extractExactHeadDispatchGuard(stakeholderReview);

      Path gitRepo = TestUtils.createTempGitRepo("2.1-review-guard");
      try
      {
        String currentHead = TestUtils.runGitCommandWithOutput(gitRepo, "rev-parse", "HEAD");
        String staleHead = "0" + currentHead.substring(1);
        if (staleHead.equals(currentHead))
          staleHead = "1" + currentHead.substring(1);
        GuardResult staleResult = runDispatchGuard(gitRepo, dispatchGuard, staleHead);

        requireThat(staleResult.exitCode(), "staleExitCode").isNotEqualTo(0);
        requireThat(staleResult.output(), "staleOutput").contains("does not match manifest HEAD");
        requireThat(staleResult.output(), "staleOutput").doesNotContain("REVIEWERS_SPAWNED");

        GuardResult currentResult = runDispatchGuard(gitRepo, dispatchGuard, currentHead);

        requireThat(currentResult.exitCode(), "currentExitCode").isEqualTo(0);
        requireThat(currentResult.output(), "currentOutput").contains("REVIEWERS_SPAWNED");
      }
      finally
      {
        TestUtils.deleteDirectoryRecursively(gitRepo);
      }
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that symlinks are rejected instead of preserved in release artifacts.
   */
  @Test
  public void buildRejectsSourceSymlinks() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-");
    try
    {
      Path repoRoot = tempDir.resolve("repo");
      Path clientDir = repoRoot.resolve("client");
      Path pluginDir = clientDir.resolve("plugin");
      Path targetDir = clientDir.resolve("distribution/target/engine");
      createPluginSource(repoRoot, clientDir, pluginDir);
      Path link = pluginDir.resolve("concepts/link.md");
      try
      {
        Files.createSymbolicLink(link, Path.of("shared-fragment.md"));
      }
      catch (UnsupportedOperationException | IOException e)
      {
        throw new SkipException("Symbolic links are not supported by this filesystem", e);
      }

      try
      {
        new PluginArtifactBuilder(pluginDir, clientDir, targetDir).build();
      }
      catch (IOException e)
      {
        requireThat(e.getMessage(), "message").contains("symbolic link");
        return;
      }
      throw new AssertionError("Expected symbolic link rejection");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that jlink symlinks are dereferenced when they point at files inside the jlink image.
   */
  @Test
  public void buildDereferencesSafeJlinkSymlinks() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-");
    try
    {
      Path repoRoot = tempDir.resolve("repo");
      Path clientDir = repoRoot.resolve("client");
      Path pluginDir = clientDir.resolve("plugin");
      Path targetDir = clientDir.resolve("distribution/target/engine");
      createPluginSource(repoRoot, clientDir, pluginDir);
      Path claudeLibDir = clientDir.resolve("distribution/target/jlink/claude/lib");
      Path codexLibDir = clientDir.resolve("distribution/target/jlink/codex/lib");
      Files.createDirectories(claudeLibDir);
      Files.createDirectories(codexLibDir);
      Files.writeString(claudeLibDir.resolve("real.txt"), "safe\n", StandardCharsets.UTF_8);
      Files.writeString(codexLibDir.resolve("real.txt"), "safe\n", StandardCharsets.UTF_8);
      try
      {
        Files.createSymbolicLink(claudeLibDir.resolve("alias.txt"), Path.of("real.txt"));
        Files.createSymbolicLink(codexLibDir.resolve("alias.txt"), Path.of("real.txt"));
      }
      catch (UnsupportedOperationException | IOException e)
      {
        throw new SkipException("Symbolic links are not supported by this filesystem", e);
      }

      new PluginArtifactBuilder(pluginDir, clientDir, targetDir).build();

      requireThat(Files.readString(targetDir.resolve("claude/client/lib/alias.txt"), StandardCharsets.UTF_8),
        "claudeDereferencedLink").isEqualTo("safe\n");
      requireThat(Files.readString(targetDir.resolve("codex/client/lib/alias.txt"), StandardCharsets.UTF_8),
        "codexDereferencedLink").isEqualTo("safe\n");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that jlink symlinks are rejected when they escape the jlink image.
   */
  @Test
  public void buildRejectsUnsafeJlinkSymlinks() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-");
    try
    {
      Path repoRoot = tempDir.resolve("repo");
      Path clientDir = repoRoot.resolve("client");
      Path pluginDir = clientDir.resolve("plugin");
      Path targetDir = clientDir.resolve("distribution/target/engine");
      createPluginSource(repoRoot, clientDir, pluginDir);
      Path claudeLibDir = clientDir.resolve("distribution/target/jlink/claude/lib");
      Path codexLibDir = clientDir.resolve("distribution/target/jlink/codex/lib");
      Files.createDirectories(claudeLibDir);
      Files.createDirectories(codexLibDir);
      Files.writeString(tempDir.resolve("outside.txt"), "unsafe\n", StandardCharsets.UTF_8);
      try
      {
        Files.createSymbolicLink(claudeLibDir.resolve("alias.txt"), tempDir.resolve("outside.txt"));
        Files.createSymbolicLink(codexLibDir.resolve("alias.txt"), tempDir.resolve("outside.txt"));
      }
      catch (UnsupportedOperationException | IOException e)
      {
        throw new SkipException("Symbolic links are not supported by this filesystem", e);
      }

      try
      {
        new PluginArtifactBuilder(pluginDir, clientDir, targetDir).build();
      }
      catch (IOException e)
      {
        requireThat(e.getMessage(), "message").contains("unsafe jlink symbolic link");
        return;
      }
      throw new AssertionError("Expected unsafe jlink symbolic link rejection");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that include expansion cannot cross engine boundaries.
   */
  @Test
  public void buildRejectsCrossEngineIncludeTargets() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-");
    try
    {
      Path repoRoot = tempDir.resolve("repo");
      Path clientDir = repoRoot.resolve("client");
      Path pluginDir = clientDir.resolve("plugin");
      Path targetDir = clientDir.resolve("distribution/target/engine");
      createPluginSource(repoRoot, clientDir, pluginDir);
      Files.writeString(pluginDir.resolve("agents/claude/agent.md"),
        MARKDOWN_LICENSE + "claude agent\n<!-- cat:include ../codex/agent.toml -->",
        StandardCharsets.UTF_8);

      try
      {
        new PluginArtifactBuilder(pluginDir, clientDir, targetDir).build();
      }
      catch (IllegalStateException e)
      {
        requireThat(e.getMessage(), "message").contains("not allowed");
        return;
      }
      throw new AssertionError("Expected cross-engine cat:include rejection");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that include expansion cannot pull source-only skill fixtures into the engine artifact.
   */
  @Test
  public void buildRejectsSourceOnlyIncludeTargets() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-");
    try
    {
      Path repoRoot = tempDir.resolve("repo");
      Path clientDir = repoRoot.resolve("client");
      Path pluginDir = clientDir.resolve("plugin");
      Path targetDir = clientDir.resolve("distribution/target/engine");
      createPluginSource(repoRoot, clientDir, pluginDir);
      Files.writeString(pluginDir.resolve("agents/claude/agent.md"),
        MARKDOWN_LICENSE + "claude agent\n<!-- cat:include ../../skills/common/common-skill/tests/test.bats -->",
        StandardCharsets.UTF_8);

      try
      {
        new PluginArtifactBuilder(pluginDir, clientDir, targetDir).build();
      }
      catch (IllegalStateException e)
      {
        requireThat(e.getMessage(), "message").contains("not allowed");
        return;
      }
      throw new AssertionError("Expected source-only cat:include rejection");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that duplicate engine companion basenames are rejected instead of guessed.
   */
  @Test
  public void buildRejectsDuplicateEngineSkillCompanionNames() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-");
    try
    {
      Path repoRoot = tempDir.resolve("repo");
      Path clientDir = repoRoot.resolve("client");
      Path pluginDir = clientDir.resolve("plugin");
      Path targetDir = clientDir.resolve("distribution/target/engine");
      createPluginSource(repoRoot, clientDir, pluginDir);
      Path nested = pluginDir.resolve("skills/common/common-skill/nested");
      Files.createDirectories(nested);
      Files.writeString(nested.resolve("helper.md"), MARKDOWN_LICENSE + "ambiguous helper\n",
        StandardCharsets.UTF_8);

      try
      {
        new PluginArtifactBuilder(pluginDir, clientDir, targetDir).build();
      }
      catch (IllegalStateException e)
      {
        requireThat(e.getMessage(), "message").contains("Duplicate engine skill companion filename");
        return;
      }
      throw new AssertionError("Expected duplicate engine skill companion filename rejection");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that Codex artifacts preserve path-scoped common rules without generating session stubs.
   */
  @Test
  public void buildDoesNotGenerateCodexStubsForPathScopedCommonRules() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-");
    try
    {
      Path repoRoot = tempDir.resolve("repo");
      Path clientDir = repoRoot.resolve("client");
      Path pluginDir = clientDir.resolve("plugin");
      Path targetDir = clientDir.resolve("distribution/target/engine");
      createPluginSource(repoRoot, clientDir, pluginDir);
      Files.writeString(pluginDir.resolve("rules/common/java.md"), """
        ---
        paths: ["*.java"]
        ---
        # Java Conventions

        Use Allman braces.
        """, StandardCharsets.UTF_8);

      new PluginArtifactBuilder(pluginDir, clientDir, targetDir).build();

      requireThat(Files.exists(targetDir.resolve("codex/rules/codex/java.md")),
        "codexStubExists").isFalse();
      requireThat(Files.readString(targetDir.resolve("codex/rules/common/java.md"), StandardCharsets.UTF_8),
        "commonRuleContent").contains("paths: [\"*.java\"]");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies engine artifacts include migration utility scripts needed by migration entrypoints.
   */
  @Test
  public void buildIncludesMigrationUtilityScripts() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-");
    try
    {
      Path repoRoot = tempDir.resolve("repo");
      Path clientDir = repoRoot.resolve("client");
      Path pluginDir = clientDir.resolve("plugin");
      Path targetDir = clientDir.resolve("distribution/target/engine");
      createPluginSource(repoRoot, clientDir, pluginDir);

      new PluginArtifactBuilder(pluginDir, clientDir, targetDir).build();

      requireThat(Files.isRegularFile(targetDir.resolve("claude/migrations/lib/utils.sh")),
        "claudeMigrationUtils").isTrue();
      requireThat(Files.isRegularFile(targetDir.resolve("codex/migrations/lib/utils.sh")),
        "codexMigrationUtils").isTrue();
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Creates a minimal plugin source tree for engine artifact tests.
   *
   * @param repoRoot the temporary repository root
   * @param clientDir the temporary client directory
   * @param pluginDir the temporary plugin directory
   * @throws IOException if file operations fail
   */
  private static void createPluginSource(Path repoRoot, Path clientDir, Path pluginDir)
    throws IOException
  {
    Files.createDirectories(clientDir);
    Files.writeString(repoRoot.resolve("LICENSE.md"), "license\n", StandardCharsets.UTF_8);
    for (String directory : new String[]{
      ".git-filter-repo-config", "concepts", "config", "lang", "lib", "migrations", "migrations/lib", "scripts",
      "templates", ".claude-plugin", ".codex-plugin", "rules/common", "rules/claude",
      "rules/codex", "hooks/common", "hooks/claude", "hooks/codex",
      "skills/common/common-skill", "skills/claude/claude-skill",
      "skills/codex/codex-skill", "skills/codex/uninstall", "agents/common", "agents/claude", "agents/codex"})
    {
      Files.createDirectories(pluginDir.resolve(directory));
    }
    Files.createDirectories(clientDir.resolve("distribution/target/jlink/claude/bin"));
    Files.createDirectories(clientDir.resolve("distribution/target/jlink/claude/lib/server"));
    Files.createDirectories(clientDir.resolve("distribution/target/jlink/codex/bin"));
    Files.createDirectories(clientDir.resolve("distribution/target/jlink/codex/lib/server"));

    Files.writeString(pluginDir.resolve(".claude-plugin/plugin.json"), "{\"version\":\"1.2.3\"}\n",
      StandardCharsets.UTF_8);
    Files.writeString(pluginDir.resolve(".codex-plugin/plugin.json"),
      "{\"version\":\"1.2.3\"}\n",
      StandardCharsets.UTF_8);
    Files.writeString(pluginDir.resolve("emoji-widths.json"), "{}\n", StandardCharsets.UTF_8);
    Files.writeString(pluginDir.resolve("package.json"), "{}\n", StandardCharsets.UTF_8);
    Files.writeString(pluginDir.resolve("package-lock.json"), "{}\n", StandardCharsets.UTF_8);
    ensureBundledGitFilterRepo(pluginDir);
    Path javaLauncher = clientDir.resolve("distribution/target/jlink/claude/bin/java");
    Files.writeString(javaLauncher, """
      #!/bin/sh
      printf '%s\\n' "$@"
      """, StandardCharsets.UTF_8);
    javaLauncher.toFile().setExecutable(true, false);
    Path codexJavaLauncher = clientDir.resolve("distribution/target/jlink/codex/bin/java");
    Files.writeString(codexJavaLauncher, """
      #!/bin/sh
      printf '%s\\n' "$@"
      """, StandardCharsets.UTF_8);
    codexJavaLauncher.toFile().setExecutable(true, false);
    Files.writeString(clientDir.resolve("distribution/target/jlink/claude/bin/tool"), "tool\n", StandardCharsets.UTF_8);
    Files.writeString(clientDir.resolve("distribution/target/jlink/codex/bin/tool"), "tool\n", StandardCharsets.UTF_8);
    Files.writeString(clientDir.resolve("distribution/target/jlink/claude/bin/pre-read"),
      "io.github.cowwoc.cat.claude.hook.PreReadHook\n", StandardCharsets.UTF_8);
    writeEngineLauncher(clientDir.resolve("distribution/target/jlink/codex/bin/pre-bash"),
      "io.github.cowwoc.cat.codex.hook.PreBashHook");
    writeEngineLauncher(clientDir.resolve("distribution/target/jlink/codex/bin/session-start"),
      "io.github.cowwoc.cat.codex.hook.SessionStartHook");
    Files.writeString(pluginDir.resolve("hooks/claude/hooks.json"), "{}\n", StandardCharsets.UTF_8);
    Files.writeString(pluginDir.resolve("hooks/codex/hooks.json"), "{}\n", StandardCharsets.UTF_8);
    Files.writeString(pluginDir.resolve("hooks/common/shared.sh"), """
      #!/usr/bin/env bash
      # Copyright (c) 2026 Gili Tzabari. All rights reserved.
      #
      # Licensed under the CAT Commercial License.
      # See LICENSE.md in the project root for license terms.
      exit 0
      """, StandardCharsets.UTF_8);
    Files.writeString(pluginDir.resolve("hooks/claude/session-start.sh"), """
      #!/usr/bin/env bash
      # Copyright (c) 2026 Gili Tzabari. All rights reserved.
      #
      # Licensed under the CAT Commercial License.
      # See LICENSE.md in the project root for license terms.
      exit 0
      """, StandardCharsets.UTF_8);
    Files.writeString(pluginDir.resolve("hooks/codex/session-start.sh"), """
      #!/usr/bin/env bash
      # Copyright (c) 2026 Gili Tzabari. All rights reserved.
      #
      # Licensed under the CAT Commercial License.
      # See LICENSE.md in the project root for license terms.
      exit 0
      """, StandardCharsets.UTF_8);
    Files.writeString(pluginDir.resolve("migrations/lib/utils.sh"), """
      #!/usr/bin/env bash
      # Copyright (c) 2026 Gili Tzabari. All rights reserved.
      #
      # Licensed under the CAT Commercial License.
      # See LICENSE.md in the project root for license terms.
      log_migration() { printf '%s\\n' "$*"; }
      """, StandardCharsets.UTF_8);
    Files.writeString(pluginDir.resolve("concepts/shared-fragment.md"), MARKDOWN_LICENSE +
      "shared body\n", StandardCharsets.UTF_8);
    Files.writeString(pluginDir.resolve("skills/common/common-skill/SKILL.md"),
      MARKDOWN_LICENSE + "common body\n", StandardCharsets.UTF_8);
    Files.writeString(pluginDir.resolve("skills/common/common-skill/first-use.md"),
      MARKDOWN_LICENSE + "Read helper.md\n", StandardCharsets.UTF_8);
    Files.writeString(pluginDir.resolve("skills/common/common-skill/helper.md"),
      MARKDOWN_LICENSE + "helper body\n", StandardCharsets.UTF_8);
    Files.writeString(pluginDir.resolve("skills/common/common-skill/testing.md"),
      MARKDOWN_LICENSE + "authoring-only test guidance\n", StandardCharsets.UTF_8);
    Files.createDirectories(pluginDir.resolve("skills/common/common-skill/tests"));
    Files.writeString(pluginDir.resolve("skills/common/common-skill/tests/test.bats"),
      "#!/usr/bin/env bats\n", StandardCharsets.UTF_8);
    Files.createDirectories(pluginDir.resolve("skills/common/common-skill/instruction-test"));
    Files.writeString(pluginDir.resolve("skills/common/common-skill/instruction-test/case.md"),
      "source-only test case\n", StandardCharsets.UTF_8);
    Files.writeString(pluginDir.resolve("skills/claude/claude-skill/SKILL.md"),
      MARKDOWN_LICENSE + "claude body\n<!-- cat:include ../../../concepts/shared-fragment.md -->",
      StandardCharsets.UTF_8);
    Files.writeString(pluginDir.resolve("skills/codex/codex-skill/SKILL.md"),
      MARKDOWN_LICENSE + "codex body\n", StandardCharsets.UTF_8);
    Files.writeString(pluginDir.resolve("skills/codex/uninstall/SKILL.md"),
      MARKDOWN_LICENSE + "codex uninstall body\n", StandardCharsets.UTF_8);
    Files.writeString(pluginDir.resolve("agents/common/agent.md"), MARKDOWN_LICENSE +
      "common agent\n", StandardCharsets.UTF_8);
    Files.writeString(pluginDir.resolve("agents/claude/agent.md"), MARKDOWN_LICENSE +
      "claude agent\n", StandardCharsets.UTF_8);
    Files.writeString(pluginDir.resolve("agents/codex/agent.toml"),
      "# Copyright (c) 2026 Gili Tzabari. All rights reserved.\n" +
        "#\n" +
        "# Licensed under the CAT Commercial License.\n" +
        "# See LICENSE.md in the project root for license terms.\n" +
        "name = \"agent\"\n", StandardCharsets.UTF_8);
  }

  /**
   * Ensures the test plugin tree contains an executable bundled git-filter-repo binary.
   *
   * @param pluginDir the plugin root directory
   * @throws IOException if file operations fail
   */
  private static void ensureBundledGitFilterRepo(Path pluginDir) throws IOException
  {
    Files.createDirectories(pluginDir.resolve("lib"));
    Path bundledBinary = pluginDir.resolve("lib/git-filter-repo-linux-x64");
    if (!Files.exists(bundledBinary, LinkOption.NOFOLLOW_LINKS))
      Files.writeString(bundledBinary, "binary\n", StandardCharsets.UTF_8);
    bundledBinary.toFile().setExecutable(true, false);
    Files.writeString(pluginDir.resolve("lib/git-filter-repo-linux-x64.tmp.1234.5678"), "incomplete\n",
      StandardCharsets.UTF_8);
  }

  /**
   * Finds the repository source root from the Maven test working directory.
   *
   * @return the repository root
   * @throws IOException if the source root cannot be found
   */
  private static Path findSourceRoot() throws IOException
  {
    Path current = Path.of("").toAbsolutePath().normalize();
    while (current != null)
    {
      if (Files.isDirectory(current.resolve("client/plugin"), LinkOption.NOFOLLOW_LINKS) &&
        Files.isRegularFile(current.resolve("client/pom.xml"), LinkOption.NOFOLLOW_LINKS))
      {
        return current;
      }
      current = current.getParent();
    }
    throw new FileNotFoundException("Unable to find CAT source root");
  }

  /**
   * Copies a directory tree without following symlinks.
   *
   * @param source the source directory
   * @param target the target directory
   * @throws IOException if copying fails
   */
  private static void copyDirectory(Path source, Path target) throws IOException
  {
    Files.walkFileTree(source, new SimpleFileVisitor<>()
    {
      @Override
      public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException
      {
        Files.createDirectories(target.resolve(source.relativize(dir)));
        return FileVisitResult.CONTINUE;
      }

      @Override
      public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException
      {
        Files.copy(file, target.resolve(source.relativize(file)));
        return FileVisitResult.CONTINUE;
      }
    });
  }

  /**
   * Counts the skill directories in a engine artifact.
   *
   * @param engineRoot the engine artifact root
   * @return the number of skill directories
   * @throws IOException if file operations fail
   */
  private static long countSkillDirectories(Path engineRoot) throws IOException
  {
    try (Stream<Path> skills = Files.list(engineRoot.resolve("skills")))
    {
      return skills.filter(Files::isDirectory).count();
    }
  }

  /**
   * Verifies that no generated engine artifact file contains source-only markers.
   *
   * @param engineRoot the engine artifact root
   * @param text        the marker text that must not be shipped
   * @param name        the assertion name
   * @throws IOException if file operations fail
   */
  private static void assertEngineArtifactDoesNotContain(Path engineRoot, String text, String name)
    throws IOException
  {
    byte[] needle = text.getBytes(StandardCharsets.UTF_8);
    try (Stream<Path> files = Files.walk(engineRoot))
    {
      for (Path file : files.filter(path -> Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)).toList())
      {
        byte[] content = Files.readAllBytes(file);
        if (containsBytes(content, needle))
          throw new AssertionError(name + ": " + engineRoot.relativize(file) + " contains " + text);
      }
    }
  }

  /**
   * Returns true if {@code haystack} contains {@code needle} as a contiguous sequence.
   *
   * @param haystack the bytes to scan
   * @param needle   the bytes to look for
   * @return true if found
   */
  private static boolean containsBytes(byte[] haystack, byte[] needle)
  {
    if (needle.length == 0)
      return true;
    if (haystack.length < needle.length)
      return false;
    for (int i = 0; i <= haystack.length - needle.length; ++i)
    {
      boolean match = true;
      for (int j = 0; j < needle.length; j += 1)
      {
        if (haystack[i + j] != needle[j])
        {
          match = false;
          break;
        }
      }
      if (match)
        return true;
    }
    return false;
  }

  /**
   * Verifies the generated engine work-review instructions enforce the auto-fix planning artifact contract.
   *
   * @param content the rendered work-review skill content
   * @param name    the assertion name
   */
  private static void assertWorkReviewAutoFixPlanArtifactContract(String content, String name)
  {
    String planningSection = content.substring(content.indexOf(
      "description: \"Plan per-concern fixes (iteration ${AUTOFIX_ITERATION})\""),
      content.indexOf("**Fix plan validation (MANDATORY):**"));
    requireThat(planningSection, name + "AutoFixPlanning").
      contains("FIX_PLAN_OUTPUT_PATH: ${WORKTREE_PATH}/.cat/work/review-fix-plans.md").
      contains("The output path in `FIX_PLAN_OUTPUT_PATH` is authoritative; do not substitute another path.").
      contains("Write the complete fix plan to `${WORKTREE_PATH}/.cat/work/review-fix-plans.md`.").
      contains("Do not write the fix plan to any other location.").
      contains("read `${WORKTREE_PATH}/.cat/work/review-fix-plans.md`").
      contains("If the file is missing or empty, treat this as a planning failure for the iteration.").
      doesNotContain(".claude");
  }

  /**
   * Verifies that common E2E verification does not accept another engine's infrastructure as a skip reason.
   *
   * @param content the generated work verification content
   * @param name    the assertion name
   */
  private static void assertNoEngineSpecificCommonE2ESkip(String content, String name)
  {
    String normalized = normalizeInstructionText(content);
    requireThat(normalized, name).doesNotContain(
      "another engine's infrastructure is unavailable, set e2e status to skipped");
    requireThat(normalized, name).doesNotContain(
      "codex sessions without claude code e2e infrastructure");
    requireThat(normalized, name).doesNotContain(
      "codex session lacks claude code e2e infrastructure");
    requireThat(normalized, name).doesNotContain(
      "codex missing-claude infrastructure skip");
    requireThat(normalized, name).doesNotContain(
      "claude code infrastructure is unavailable");
  }

  /**
   * Verifies that work-execute does not contain contradictory empty-diff already-applied guidance.
   *
   * @param content the generated work-execute content
   * @param name    the assertion name
   */
  private static void assertNoEmptyDiffAlreadyImplementedRule(String content, String name)
  {
    requireThat(normalizeInstructionText(content), name).doesNotContain(
      "return already_implemented when the implementation diff is empty");
    requireThat(normalizeInstructionText(content), name).doesNotContain(
      "implementation diff is empty, return already_implemented");
    requireThat(content, name).doesNotContain("no diff for the\nimplementation files");
  }

  /**
   * Verifies that work-implement requires branch-bound cleanup of merged agent worktrees.
   *
   * @param content the work-implement skill content
   * @param name    the assertion name
   */
  private static void assertWorkImplementCleanupConvention(String content, String name)
  {
    String cleanupSection = content.substring(
      content.indexOf("### Cleanup Successfully Merged Agent Worktrees"),
      content.indexOf("### Parallel Agent Execution"));
    String parallelMergeSection = content.substring(
      content.indexOf("For each job's branch name received from the Task tool result"),
      content.indexOf("The agent branch name and worktree path for each job"));

    requireThat(content, name).contains("### Cleanup Successfully Merged Agent Worktrees");
    requireThat(cleanupSection, name + "Cleanup").contains(
      "REGISTERED_BRANCH=$(git -C \"${WORKTREE_PATH}\" worktree list --porcelain");
    requireThat(cleanupSection, name + "Cleanup").contains(
      "git -C \"${WORKTREE_PATH}\" worktree remove --force \"${SUBAGENT_WORKTREE}\"");
    requireThat(cleanupSection, name + "Cleanup").contains(
      "git -C \"${WORKTREE_PATH}\" branch -d \"${SUBAGENT_BRANCH}\"");
    requireThat(content, name).contains("skip cleanup and leave the agent worktree available for diagnosis");
    requireThat(parallelMergeSection, name + "Parallel").contains(
      "REGISTERED_BRANCH=$(git -C \"${WORKTREE_PATH}\" worktree list --porcelain");
    requireThat(parallelMergeSection, name + "Parallel").contains(
      "git -C \"${WORKTREE_PATH}\" worktree remove --force \"${SUBAGENT_WORKTREE}\"");
    requireThat(parallelMergeSection, name + "Parallel").contains(
      "git -C \"${WORKTREE_PATH}\" branch -d \"${SUBAGENT_BRANCH}\"");
    requireThat(content, name).contains("before incrementing `NEXT_MERGE`");
  }

  /**
   * Verifies that Codex E2E skipping is not broadened beyond missing Claude Code infrastructure.
   *
   * @param content the generated work verification content
   * @param name    the assertion name
   */
  private static void assertNoBlanketCodexE2ESkipRule(String content, String name)
  {
    requireThat(normalizeInstructionText(content), name).doesNotContain(
      "treat any codex e2e failure as skipped");
    requireThat(normalizeInstructionText(content), name).doesNotContain(
      "all codex e2e failures as skipped");
    requireThat(normalizeInstructionText(content), name).doesNotContain(
      "any e2e failure as skipped");
  }

  /**
   * Extracts the executable exact-HEAD dispatch guard from generated stakeholder-review instructions.
   *
   * @param content the generated stakeholder-review content
   * @return the dispatch guard shell body
   */
  private static String extractExactHeadDispatchGuard(String content)
  {
    String guardStart = "DISPATCH_HEAD_SHA=$(git rev-parse --verify \"HEAD^{commit}\")";
    int start = content.indexOf(guardStart);
    requireThat(start, "guardStart").isGreaterThanOrEqualTo(0);
    int end = content.indexOf("\n```", start);
    requireThat(end, "guardEnd").isGreaterThan(start);
    return content.substring(start, end);
  }

  /**
   * Runs the generated exact-HEAD dispatch guard in a git repository.
   *
   * @param gitRepo the git repository
   * @param dispatchGuard the guard shell body
   * @param manifestHead the manifest HEAD to compare against
   * @return the guard process result
   * @throws IOException if the guard cannot be started
   * @throws InterruptedException if interrupted while waiting for the guard
   */
  private static GuardResult runDispatchGuard(Path gitRepo, String dispatchGuard, String manifestHead)
    throws IOException, InterruptedException
  {
    String script = """
      set -euo pipefail
      HEAD_SHA='%s'
      %s
      echo REVIEWERS_SPAWNED
      """.formatted(manifestHead, dispatchGuard);
    try (Process process = new ProcessBuilder("bash", "-c", script).
      directory(gitRepo.toFile()).
      redirectErrorStream(true).
      start())
    {
      boolean completed = process.waitFor(10, TimeUnit.SECONDS);
      if (!completed)
      {
        process.destroyForcibly();
        throw new AssertionError("Timed out running exact-head dispatch guard");
      }
      String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
      return new GuardResult(process.exitValue(), output);
    }
  }

  /**
   * Result of running the exact-HEAD dispatch guard.
   *
   * @param exitCode the process exit code
   * @param output the process output
   */
  private record GuardResult(int exitCode, String output)
  {
  }

  /**
   * Verifies content while ignoring line wrapping.
   *
   * @param content  the actual content
   * @param name     the assertion name
   * @param expected the expected content
   */
  private static void assertContainsNormalized(String content, String name, String expected)
  {
    requireThat(normalizeInstructionText(content), name).contains(normalizeInstructionText(expected));
  }

  /**
   * Verifies engine-specific output rendering conventions for generated skill wrappers.
   *
   * @param engineRoot the engine artifact root
   * @param engine the engine name
   * @param skillName the skill directory name
   * @param commandName the expected command name
   * @throws IOException if file operations fail
   */
  private static void assertOutputRenderSkillConvention(Path engineRoot, String engine, String skillName,
    String commandName) throws IOException
  {
    Path skillDirectory = engineRoot.resolve("skills/" + skillName);
    String content = Files.readString(skillDirectory.resolve("SKILL.md"), StandardCharsets.UTF_8);
    Path firstUse = skillDirectory.resolve("first-use.md");
    if (Files.isRegularFile(firstUse, LinkOption.NOFOLLOW_LINKS))
      content += "\n" + Files.readString(firstUse, StandardCharsets.UTF_8);
    String assertionPrefix = engine + capitalize(skillName);
    assertContainsNormalized(content, assertionPrefix + "DeterministicCommand",
      "deterministic Java output command");
    assertContainsNormalized(content, assertionPrefix + "VerbatimOutput",
      "Return the generated display exactly");
    requireThat(content, assertionPrefix + "Command").contains(commandName);
    requireThat(content, assertionPrefix + "NoRenderOutputDirective").doesNotContain("cat:render-output");
    if (engine.equals("claude"))
    {
      requireThat(content, assertionPrefix + "Preprocessor").contains("!`");
      requireThat(content, assertionPrefix + "NoDollarZero").doesNotContain("\"$0\"");
      requireThat(content, assertionPrefix + "NoBash").doesNotContain("```bash");
      if (skillName.equals("get-diff"))
        requireThat(content, assertionPrefix + "IssuePathPlaceholder").contains("\"$1\"");
      if (skillName.equals("work-complete"))
      {
        requireThat(content, assertionPrefix + "CompletedIssuePlaceholder").contains("\"$1\"");
        requireThat(content, assertionPrefix + "TargetBranchPlaceholder").contains("\"$2\"");
      }
    }
    else
    {
      assertContainsNormalized(content, assertionPrefix + "BashInstruction",
        "Run the deterministic implementation through Bash");
      requireThat(content, assertionPrefix + "NoPreprocessor").doesNotContain("!`");
      if (skillName.equals("get-diff"))
        requireThat(content, assertionPrefix + "IssuePathPlaceholder").contains("\"<issue-path>\"");
      if (skillName.equals("work-complete"))
      {
        requireThat(content, assertionPrefix + "CompletedIssuePlaceholder").contains("\"<completed-issue>\"");
        requireThat(content, assertionPrefix + "TargetBranchPlaceholder").contains("\"<target-branch>\"");
      }
    }
  }

  /**
   * Normalizes generated instruction text for phrase assertions.
   *
   * @param content the content to normalize
   * @return normalized content
   */
  private static String normalizeInstructionText(String content)
  {
    return content.toLowerCase(Locale.ROOT).replace("`", "").replaceAll("\\s+", " ");
  }

  /**
   * Capitalizes the first character of a string.
   *
   * @param value the value to capitalize
   * @return the capitalized value
   */
  private static String capitalize(String value)
  {
    if (value.isEmpty())
      return value;
    return Character.toUpperCase(value.charAt(0)) + value.substring(1);
  }

  /**
   * Writes a launcher script that mimics a jlink-generated engine launcher.
   *
   * @param launcher the launcher path
   * @param className the module main class
   * @throws IOException if the launcher cannot be written
   */
  private static void writeEngineLauncher(Path launcher, String className) throws IOException
  {
    String moduleName;
    if (className.startsWith("io.github.cowwoc.cat.codex."))
      moduleName = "io.github.cowwoc.cat.codex.cli";
    else
      moduleName = "io.github.cowwoc.cat.claude.cli";
    Files.writeString(launcher, """
      #!/bin/sh
      DIR=`dirname $0`
      exec "$DIR/java" \\
        -XX:AOTCache="$DIR/../lib/server/aot-cache.aot" \\
        -m %s/%s "$@"
      """.formatted(moduleName, className), StandardCharsets.UTF_8);
    launcher.toFile().setExecutable(true, false);
  }

  /**
   * Runs a generated launcher and returns its output.
   *
   * @param launcher the launcher to run
   * @return the launcher output
   * @throws IOException if the launcher cannot be started or read
   * @throws InterruptedException if interrupted while waiting for the launcher
   */
  private static String runLauncher(Path launcher) throws IOException, InterruptedException
  {
    try (Process process = new ProcessBuilder(launcher.toString()).redirectErrorStream(true).start())
    {
      boolean completed = process.waitFor(10, TimeUnit.SECONDS);
      if (!completed)
      {
        process.destroyForcibly();
        throw new AssertionError("Timed out running launcher: " + launcher);
      }
      String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
      requireThat(process.exitValue(), "launcherExitCode").isEqualTo(0);
      return output;
    }
  }
}
