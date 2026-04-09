/*
 * Copyright (c) 2026 Gili Tzabari. All rights reserved.
 *
 * Licensed under the CAT Commercial License.
 * See LICENSE.md in the project root for license terms.
 */
package io.github.cowwoc.cat.claude.hook.util;

import static io.github.cowwoc.requirements13.java.DefaultJavaValidators.requireThat;
import static java.nio.charset.StandardCharsets.UTF_8;

import io.github.cowwoc.cat.claude.hook.JvmScope;
import io.github.cowwoc.cat.claude.tool.ClaudeTool;
import io.github.cowwoc.cat.claude.tool.MainClaudeTool;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;

/**
 * Closes the issue's {@code index.json} (sets {@code "status"} to {@code "closed"}) and stages it
 * with {@code git add} so the squash commit absorbs the closure.
 * <p>
 * This tool is the Java replacement for the bash {@code sed -i} block that was previously used in
 * {@code work-squash.md} Step 3. Using Jackson guarantees correct JSON manipulation regardless of
 * whitespace or field ordering.
 * <p>
 * Output contract (JSON on stdout):
 * <ul>
 *   <li>{@code {"index_updated": false}} — branch is not a CAT issue, or {@code index.json} is
 *       absent, or status is already {@code "closed"}</li>
 *   <li>{@code {"index_updated": true, "index_path": "..."}} — status updated and staged</li>
 * </ul>
 */
public final class AutoCloseIndexJson
{
  private final JvmScope scope;

  /**
   * Creates a new AutoCloseIndexJson.
   *
   * @param scope the JVM scope providing the JSON mapper
   * @throws NullPointerException if {@code scope} is null
   */
  public AutoCloseIndexJson(JvmScope scope)
  {
    requireThat(scope, "scope").isNotNull();
    this.scope = scope;
  }

  /**
   * Closes the issue's {@code index.json} and stages it, if needed.
   *
   * @param args exactly 2 arguments: {@code worktree_path}, {@code branch}
   * @return JSON string with {@code index_updated} boolean and optional {@code index_path}
   * @throws NullPointerException     if {@code args} is null
   * @throws IllegalArgumentException if {@code args} does not contain exactly 2 elements, if
   *   {@code args[0]} (worktree_path) is blank, or if {@code args[1]} (branch) is blank
   * @throws IOException              if reading/writing {@code index.json} or running {@code git add}
   *   fails
   */
  public String getOutput(String[] args) throws IOException
  {
    requireThat(args, "args").isNotNull();
    if (args.length != 2)
    {
      throw new IllegalArgumentException(
        "Expected exactly 2 arguments (worktree_path, branch), got " + args.length + ". " +
          "Usage: auto-close-index <worktree_path> <branch>");
    }
    String worktreePath = args[0];
    if (worktreePath.isBlank())
    {
      throw new IllegalArgumentException(
        "worktree_path is required as the first argument but was blank. " +
          "Usage: auto-close-index <worktree_path> <branch>");
    }
    String branch = args[1];
    if (branch.isBlank())
    {
      throw new IllegalArgumentException(
        "branch is required as the second argument but was blank. " +
          "Usage: auto-close-index <worktree_path> <branch>");
    }

    String relativePath = IssueDiscovery.branchToIndexJsonPath(branch);
    if (relativePath == null)
    {
      // Branch does not follow CAT naming convention — not a managed issue.
      return "{\"index_updated\": false}";
    }

    Path worktree = Path.of(worktreePath);
    Path indexJsonPath = worktree.resolve(relativePath);
    if (!Files.exists(indexJsonPath))
    {
      // index.json absent — nothing to close.
      return "{\"index_updated\": false}";
    }

    String content = Files.readString(indexJsonPath, UTF_8);
    JsonNode root = scope.getJsonMapper().readTree(content);
    JsonNode statusNode = root.get("status");
    if (statusNode != null && statusNode.isString() &&
      statusNode.asString().equals("closed"))
    {
      // Already closed — nothing to do.
      return "{\"index_updated\": false}";
    }

    // Update status to "closed" via Jackson (safe JSON manipulation, not sed).
    ((ObjectNode) root).put("status", "closed");
    String updated = scope.getJsonMapper().writeValueAsString(root);
    Files.writeString(indexJsonPath, updated, UTF_8);

    // Stage the updated file so it is absorbed into the squash commit.
    GitCommands.runGit(worktree, "add", relativePath);

    return "{\"index_updated\": true, \"index_path\": \"" +
      relativePath.replace("\\", "\\\\").replace("\"", "\\\"") + "\"}";
  }

  /**
   * Main entry point for command-line invocation.
   * <p>
   * Usage: {@code auto-close-index <worktree_path> <branch>}
   *
   * @param args command-line arguments: worktree_path, branch
   */
  public static void main(String[] args)
  {
    try (ClaudeTool scope = new MainClaudeTool())
    {
      try
      {
        run(scope, args, System.out);
      }
      catch (IllegalArgumentException | IOException e)
      {
        Logger log = LoggerFactory.getLogger(AutoCloseIndexJson.class);
        log.error("Expected error", e);
        String message = Objects.toString(e.getMessage(), e.getClass().getSimpleName()).
          replace("\\", "\\\\").replace("\"", "\\\"");
        System.out.println("{\"status\": \"ERROR\", \"message\": \"" + message + "\"}");
      }
      catch (RuntimeException | AssertionError e)
      {
        Logger log = LoggerFactory.getLogger(AutoCloseIndexJson.class);
        log.error("Unexpected error", e);
        String message = Objects.toString(e.getMessage(), e.getClass().getSimpleName()).
          replace("\\", "\\\\").replace("\"", "\\\"");
        System.out.println("{\"status\": \"ERROR\", \"message\": \"" + message + "\"}");
      }
    }
  }

  /**
   * Executes the auto-close logic with caller-provided streams.
   * <p>
   * Separated from {@link #main(String[])} to allow unit testing without JVM exit.
   *
   * @param scope the JVM scope
   * @param args  the command-line arguments: worktree_path, branch
   * @param out   the output stream to write to
   * @throws NullPointerException if {@code args} or {@code out} are null
   * @throws IOException          if reading/writing index.json or running git add fails
   */
  public static void run(JvmScope scope, String[] args, PrintStream out) throws IOException
  {
    requireThat(args, "args").isNotNull();
    requireThat(out, "out").isNotNull();
    String output = new AutoCloseIndexJson(scope).getOutput(args);
    out.println(output);
  }
}
