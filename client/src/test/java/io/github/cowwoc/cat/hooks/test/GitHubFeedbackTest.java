/*
 * Copyright (c) 2026 Gili Tzabari. All rights reserved.
 *
 * Licensed under the CAT Commercial License.
 * See LICENSE.md in the project root for license terms.
 */
package io.github.cowwoc.cat.hooks.test;

import io.github.cowwoc.cat.hooks.util.GitHubFeedback;
import org.testng.annotations.Test;
import tools.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static io.github.cowwoc.requirements13.java.DefaultJavaValidators.requireThat;

/**
 * Tests for GitHubFeedback functionality.
 * <p>
 * Tests are designed for parallel execution - each test is self-contained
 * with no shared state.
 */
public class GitHubFeedbackTest
{
  /**
   * Verifies that openIssue returns status "url_only" when the browser fails to open.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void openIssueReturnsUrlOnlyWhenBrowserUnavailable() throws IOException
  {
    Path tempDir = Files.createTempDirectory("github-feedback-test");
    try (TestJvmScope scope = new TestJvmScope(tempDir, tempDir))
    {
      GitHubFeedback feedback = new GitHubFeedback(scope);
      // Inject a browser opener that always fails
      String result = feedback.openIssue("Test Title", "Test body content", "",
        url ->
        {
          throw new IOException("Browser unavailable in headless environment");
        });

      tools.jackson.databind.json.JsonMapper mapper = scope.getJsonMapper();
      ObjectNode resultNode = (ObjectNode) mapper.readTree(result);

      requireThat(resultNode.has("status"), "hasStatus").isTrue();
      requireThat(resultNode.get("status").asString(), "status").isEqualTo("url_only");
      requireThat(resultNode.has("url"), "hasUrl").isTrue();
      requireThat(resultNode.get("url").asString(), "url").contains("github.com");
      requireThat(resultNode.get("url").asString(), "url").contains("Test+Title");
      requireThat(resultNode.has("message"), "hasMessage").isTrue();
      requireThat(resultNode.get("message").asString(), "message").contains("Browser unavailable");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that openIssue returns status "opened" when the browser opens successfully.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void openIssueReturnsOpenedWhenBrowserSucceeds() throws IOException
  {
    Path tempDir = Files.createTempDirectory("github-feedback-test");
    try (TestJvmScope scope = new TestJvmScope(tempDir, tempDir))
    {
      GitHubFeedback feedback = new GitHubFeedback(scope);
      // Inject a browser opener that succeeds (no-op)
      String result = feedback.openIssue("Test Title", "Test body content", "",
        url ->
        {
        });

      tools.jackson.databind.json.JsonMapper mapper = scope.getJsonMapper();
      ObjectNode resultNode = (ObjectNode) mapper.readTree(result);

      requireThat(resultNode.has("status"), "hasStatus").isTrue();
      requireThat(resultNode.get("status").asString(), "status").isEqualTo("opened");
      requireThat(resultNode.has("url"), "hasUrl").isTrue();
      requireThat(resultNode.get("url").asString(), "url").contains("github.com");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that openIssue rejects null title.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test(expectedExceptions = NullPointerException.class)
  public void openIssueRejectsNullTitle() throws IOException
  {
    Path tempDir = Files.createTempDirectory("github-feedback-test");
    try (TestJvmScope scope = new TestJvmScope(tempDir, tempDir))
    {
      GitHubFeedback feedback = new GitHubFeedback(scope);
      feedback.openIssue(null, "body", "", url ->
      {
      });
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that openIssue rejects blank title.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test(expectedExceptions = IllegalArgumentException.class)
  public void openIssueRejectsBlankTitle() throws IOException
  {
    Path tempDir = Files.createTempDirectory("github-feedback-test");
    try (TestJvmScope scope = new TestJvmScope(tempDir, tempDir))
    {
      GitHubFeedback feedback = new GitHubFeedback(scope);
      feedback.openIssue("  ", "body", "", url ->
      {
      });
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that openIssue rejects null body.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test(expectedExceptions = NullPointerException.class)
  public void openIssueRejectsNullBody() throws IOException
  {
    Path tempDir = Files.createTempDirectory("github-feedback-test");
    try (TestJvmScope scope = new TestJvmScope(tempDir, tempDir))
    {
      GitHubFeedback feedback = new GitHubFeedback(scope);
      feedback.openIssue("title", null, "", url ->
      {
      });
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that openIssue rejects blank body.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test(expectedExceptions = IllegalArgumentException.class)
  public void openIssueRejectsBlankBody() throws IOException
  {
    Path tempDir = Files.createTempDirectory("github-feedback-test");
    try (TestJvmScope scope = new TestJvmScope(tempDir, tempDir))
    {
      GitHubFeedback feedback = new GitHubFeedback(scope);
      feedback.openIssue("title", "  ", "", url ->
      {
      });
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that openIssue rejects null labels.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test(expectedExceptions = NullPointerException.class)
  public void openIssueRejectsNullLabels() throws IOException
  {
    Path tempDir = Files.createTempDirectory("github-feedback-test");
    try (TestJvmScope scope = new TestJvmScope(tempDir, tempDir))
    {
      GitHubFeedback feedback = new GitHubFeedback(scope);
      feedback.openIssue("title", "body", null, url ->
      {
      });
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that openIssue rejects null browserOpener.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test(expectedExceptions = NullPointerException.class)
  public void openIssueRejectsNullBrowserOpener() throws IOException
  {
    Path tempDir = Files.createTempDirectory("github-feedback-test");
    try (TestJvmScope scope = new TestJvmScope(tempDir, tempDir))
    {
      GitHubFeedback feedback = new GitHubFeedback(scope);
      feedback.openIssue("title", "body", "", null);
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that openIssue encodes labels correctly in the URL.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void openIssueEncodesLabelsInUrl() throws IOException
  {
    Path tempDir = Files.createTempDirectory("github-feedback-test");
    try (TestJvmScope scope = new TestJvmScope(tempDir, tempDir))
    {
      GitHubFeedback feedback = new GitHubFeedback(scope);
      String result = feedback.openIssue("Test Title", "Test body", "bug,enhancement",
        url ->
        {
        });

      tools.jackson.databind.json.JsonMapper mapper = scope.getJsonMapper();
      ObjectNode resultNode = (ObjectNode) mapper.readTree(result);

      String url = resultNode.get("url").asString();
      requireThat(url, "url").contains("labels=");
      requireThat(url, "url").contains("bug");
      requireThat(url, "url").contains("enhancement");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }
}
