/*
 * Copyright (c) 2026 Gili Tzabari. All rights reserved.
 *
 * Licensed under the CAT Commercial License.
 * See LICENSE.md in the project root for license terms.
 */
package io.github.cowwoc.cat.client.test;

import org.testng.annotations.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static io.github.cowwoc.requirements13.java.DefaultJavaValidators.requireThat;

/**
 * Verifies that instruction fixtures covering orchestration-optimization behavior are present and
 * encode the intended contract.
 */
public final class InstructionFixtureCoverageTest
{
  /**
   * Verifies that the canonical SPRT monitoring fixture requires run-status delta monitoring and
   * the longer wait cadence.
   *
   * @throws IOException if reading the fixture fails
   */
  @Test
  public void sprtMonitoringFixtureCoversStatusPath() throws IOException
  {
    String content = Files.readString(fixture(
      "plugin/tests/skills/sprt-runner/first-use/monitoring-uses-run-status-deltas.md"));
    requireThat(content, "content").contains("sprt-runner run-status");
    requireThat(content, "content").contains("last_event_seq");
    requireThat(content, "content").contains("60 seconds");
    requireThat(content, "content").contains("120 seconds");
    requireThat(content, "content").contains("300 seconds");
    requireThat(content, "content").contains("progress.json");
    requireThat(content, "content").contains("tail -f");
    requireThat(content, "content").contains("`ps`");
  }

  /**
   * Verifies that the instruction-builder fixture requires detect-changes and delegates normal
   * monitoring ownership to {@code cat:sprt-runner}.
   *
   * @throws IOException if reading the fixture fails
   */
  @Test
  public void instructionBuilderDelegatesMonitoring() throws IOException
  {
    String content = Files.readString(fixture(
      "plugin/tests/skills/instruction-builder/first-use/" +
        "step6-delegates-normal-monitoring-to-run-status.md"));
    requireThat(content, "content").contains("detect-changes");
    requireThat(content, "content").contains("cat:sprt-runner");
    requireThat(content, "content").contains("progress.json");
    requireThat(content, "content").contains("tail -f");
    requireThat(content, "content").contains("output-grep");
  }

  /**
   * Verifies that the grep-and-read fixture continues to require the single search-plus-read path.
   *
   * @throws IOException if reading the fixture fails
   */
  @Test
  public void grepAndReadFixtureRequiresSinglePath() throws IOException
  {
    String content = Files.readString(fixture(
      "plugin/tests/skills/grep-and-read/first-use/e2e_subagent_invocation.md"));
    requireThat(content, "content").contains("grep-and-read");
    requireThat(content, "content").contains("search and read multiple files in a single operation");
    requireThat(content, "content").contains("raw Grep call");
    requireThat(content, "content").contains("separate individual Read calls across separate messages");
  }

  /**
   * Resolves a plugin test fixture relative to the Claude test module.
   *
   * @param relativePath the path relative to {@code client/}
   * @return the resolved fixture path
   */
  private static Path fixture(String relativePath)
  {
    Path path = Paths.get(System.getProperty("user.dir"), "..", relativePath).normalize();
    requireThat(path.toFile().exists(), "path").withContext("fixture", path.toString()).isTrue();
    return path;
  }
}
