/*
 * Copyright (c) 2026 Gili Tzabari. All rights reserved.
 *
 * Licensed under the CAT Commercial License.
 * See LICENSE.md in the project root for license terms.
 */
package io.github.cowwoc.cat.client.test;

import static io.github.cowwoc.requirements13.java.DefaultJavaValidators.requireThat;

import org.testng.annotations.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Tests release source contracts.
 */
public final class ReleaseDocumentationTest
{
  /**
   * Verifies release source contracts that are independent of documentation wording.
   *
   * @throws IOException if reading source files fails
   */
  @Test
  public void releaseSourcesUseCatReleaseAssets() throws IOException
  {
    Path sourceRoot = findSourceRoot();
    Path clientRoot = sourceRoot.resolve("client");

    String claudeMarketplace = Files.readString(sourceRoot.resolve(".claude-plugin/marketplace.json"),
      StandardCharsets.UTF_8);
    requireThat(claudeMarketplace, "claudeMarketplace").contains("\"source\": \"./client/plugin\"");

    requireThat(Files.exists(clientRoot.resolve("plugin/skills/claude/uninstall/SKILL.md")),
      "claudeUninstallSkill").isFalse();

    requireThat(Files.exists(clientRoot.resolve("plugin/skills/common/help/SKILL.md")),
      "commonHelpSkill").isFalse();
    requireThat(clientRoot.resolve("plugin/skills/claude/help/SKILL.md"), "claudeHelpSkill").
      isRegularFile();
    requireThat(clientRoot.resolve("plugin/skills/codex/help/SKILL.md"), "codexHelpSkill").
      isRegularFile();

    requireThat(clientRoot.resolve("plugin/skills/codex/uninstall/first-use.md"), "codexUninstallSkill").
      isRegularFile();

    String gitFilterRepoDownloader = Files.readString(clientRoot.resolve("plugin/scripts/download-git-filter-repo.sh"),
      StandardCharsets.UTF_8);
    requireThat(gitFilterRepoDownloader, "gitFilterRepoDownloader").
      doesNotContain("curl");
    requireThat(gitFilterRepoDownloader, "gitFilterRepoDownloader").
      doesNotContain("BINARY_URL=");
    requireThat(gitFilterRepoDownloader, "gitFilterRepoDownloader").
      doesNotContain("command -v git-filter-repo");
    requireThat(gitFilterRepoDownloader, "gitFilterRepoDownloader").
      contains("ERROR: Bundled git-filter-repo executable not found");

    String commonGitRewriteHistory = Files.readString(
      clientRoot.resolve("plugin/skills/common/git-rewrite-history/first-use.md"), StandardCharsets.UTF_8);
    requireThat(commonGitRewriteHistory, "commonGitRewriteHistory").
      contains("fail-fast resolution of the bundled standalone binary");
    requireThat(commonGitRewriteHistory, "commonGitRewriteHistory").
      doesNotContain("executable on `PATH`");
    requireThat(commonGitRewriteHistory, "commonGitRewriteHistory").
      doesNotContain("executable on PATH");
    requireThat(commonGitRewriteHistory, "commonGitRewriteHistory").
      doesNotContain("on `PATH` or the path to the bundled");

    String claudeGitRewriteHistory = Files.readString(
      clientRoot.resolve("plugin/skills/claude/git-rewrite-history/SKILL.md"), StandardCharsets.UTF_8);
    requireThat(claudeGitRewriteHistory, "claudeGitRewriteHistory").
      doesNotContain("PATH or bundled binary resolution");

    String codexGitRewriteHistory = Files.readString(
      clientRoot.resolve("plugin/skills/codex/git-rewrite-history/SKILL.md"), StandardCharsets.UTF_8);
    requireThat(codexGitRewriteHistory, "codexGitRewriteHistory").
      doesNotContain("PATH or bundled binary resolution");
  }

  /**
   * Verifies skills that invoke CAT launchers define the complete engine environment first.
   *
   * @throws IOException if reading source files fails
   */
  @Test
  public void catLauncherSkillsDefineEngineEnvironment() throws IOException
  {
    Path sourceRoot = findSourceRoot();
    Path skillRoot = sourceRoot.resolve("client/plugin/skills/common");
    assertDefinesCatEngineEnvironment(skillRoot.resolve("git-squash/first-use.md"), "gitSquashSkill");
    assertDefinesCatEngineEnvironment(skillRoot.resolve("get-output/first-use.md"), "getOutputSkill");
    assertDefinesCatEngineEnvironment(skillRoot.resolve("learn/first-use.md"), "learnSkill");

    String learnSkill = Files.readString(skillRoot.resolve("learn/first-use.md"), StandardCharsets.UTF_8);
    requireThat(learnSkill, "learnSkill").contains("TIMEOUT=300");
    requireThat(learnSkill, "learnSkill").contains("300 seconds");
    requireThat(learnSkill, "learnSkill").doesNotContain("TIMEOUT=150");
    requireThat(learnSkill, "learnSkill").doesNotContain("150 seconds");
  }

  /**
   * Verifies work-execute instructions do not treat a clean pre-implementation branch as already applied.
   *
   * @throws IOException if reading source files fails
   */
  @Test
  public void workExecuteAllowsCleanPreImplementation() throws IOException
  {
    Path sourceRoot = findSourceRoot();
    String workExecute = Files.readString(sourceRoot.resolve("client/plugin/agents/common/work-execute.md"),
      StandardCharsets.UTF_8);

    requireThat(workExecute, "workExecute").
      contains("A clean pre-implementation branch with no implementation diff is normal");
    requireThat(workExecute, "workExecute").
      contains("Do not classify an empty implementation diff as already applied");
    requireThat(workExecute, "workExecute").
      doesNotContain("no diff for the\nimplementation files");
  }

  /**
   * Verifies common work verification instructions stay engine-neutral.
   *
   * @throws IOException if reading source files fails
   */
  @Test
  public void commonWorkVerificationUsesEngineNeutral() throws IOException
  {
    Path sourceRoot = findSourceRoot();
    String workVerify = Files.readString(sourceRoot.resolve("client/plugin/agents/common/work-verify.md"),
      StandardCharsets.UTF_8);
    assertEngineNeutralWorkVerification(workVerify, "workVerify");
  }

  /**
   * Verifies work-skill turn fixtures use descriptive testcase IDs.
   *
   * @throws IOException if reading source files fails
   */
  @Test
  public void workSkillTurnFixturesUseDescriptive() throws IOException
  {
    Path sourceRoot = findSourceRoot();
    Path fixtureRoot = sourceRoot.resolve("client/plugin/tests/skills/work/first-use");
    try (Stream<Path> paths = Files.walk(fixtureRoot))
    {
      String numericFixtures = paths.filter(Files::isRegularFile).
        map(fixtureRoot::relativize).
        map(Path::toString).
        filter(path -> path.matches(".*tc\\d+_turn\\d+\\.md")).
        sorted().
        collect(Collectors.joining("\n"));
      requireThat(numericFixtures, "numericFixtures").isEmpty();
    }

    String testResults = Files.readString(fixtureRoot.resolve("test-results.json"), StandardCharsets.UTF_8);
    requireThat(testResults.matches("(?s).*\"test_case_id\"\\s*:\\s*\"tc\\d+\".*"),
      "numericTestCaseId").isFalse();
  }

  private static void assertEngineNeutralWorkVerification(String content, String name)
  {
    requireThat(content, name).contains("E2E tests must use the engine selected by `CAT_ENGINE`");
    requireThat(content, name).contains("engine-native test infrastructure");
    requireThat(content, name).contains("Do not skip E2E because another engine's infrastructure is unavailable");
    requireThat(content, name).doesNotContain("Claude");
    requireThat(content, name).doesNotContain("Codex");
    requireThat(content, name).doesNotContain("CAT_ENGINE:-claude");
    requireThat(content, name).doesNotContain("ModelIdResolver.detectClaudeCodeVersion");
  }

  private static void assertDefinesCatEngineEnvironment(Path path, String name) throws IOException
  {
    String content = Files.readString(path, StandardCharsets.UTF_8);
    requireThat(content, name).contains("CAT_PLUGIN_ROOT");
    requireThat(content, name).contains("CAT_PLUGIN_DATA");
    requireThat(content, name).contains("CAT_PROJECT_DIR");
    requireThat(content, name).contains("CAT_ENGINE");
    requireThat(content, name).contains("CAT_SESSION_ID");
    requireThat(content, name).contains("CODEX_THREAD_ID");
    requireThat(content, name).contains("do not generate a fallback UUID");
    requireThat(content, name).contains("CAT_PLUGIN_ROOT is required from CAT engine injection");
    requireThat(content, name).doesNotContain("find \"${CODEX_HOME}/plugins/cache\"");
  }

  private static Path findSourceRoot()
  {
    Path current = Path.of("").toAbsolutePath().normalize();
    while (current != null)
    {
      if (Files.isDirectory(current.resolve("client/plugin")) &&
        Files.isRegularFile(current.resolve("client/pom.xml")))
      {
        return current;
      }
      current = current.getParent();
    }
    throw new AssertionError("Unable to find CAT source root");
  }
}
