/*
 * Copyright (c) 2026 Gili Tzabari. All rights reserved.
 *
 * Licensed under the CAT Commercial License.
 * See LICENSE.md in the project root for license terms.
 */
package io.github.cowwoc.cat.hooks.skills;

import static io.github.cowwoc.cat.hooks.Strings.block;

import io.github.cowwoc.cat.hooks.ClaudeTool;
import io.github.cowwoc.cat.hooks.MainClaudeTool;
import io.github.cowwoc.cat.hooks.util.SkillOutput;

import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static io.github.cowwoc.requirements13.java.DefaultJavaValidators.requireThat;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.Objects;
/**
 * Output generator for checkpoint boxes used by the {@code /cat:work} skill's review phase.
 * <p>
 * Generates two types of checkpoint boxes:
 * <ul>
 *   <li>{@code issue-complete} - Displayed as CHECKPOINT_TASK_COMPLETE when an issue finishes</li>
 *   <li>{@code feedback-applied} - Displayed as CHECKPOINT_FEEDBACK_APPLIED after review iteration</li>
 * </ul>
 */
public final class GetCheckpointOutput implements SkillOutput
{
  /**
   * The JVM scope for accessing shared services.
   */
  private final ClaudeTool scope;

  /**
   * Creates a GetCheckpointOutput instance.
   *
   * @param scope the ClaudeTool for accessing shared services
   * @throws NullPointerException if scope is null
   */
  public GetCheckpointOutput(ClaudeTool scope)
  {
    requireThat(scope, "scope").isNotNull();
    this.scope = scope;
  }

  /**
   * Generates the output for this skill.
   * <p>
   * Parses {@code --project-dir PATH} from {@code args} if present; otherwise falls back to
   * {@code scope.getProjectPath()}.
   *
   * @param args the arguments from the preprocessor directive
   * @return the generated output
   * @throws NullPointerException     if {@code args} is null
   * @throws IllegalArgumentException if an unknown argument is provided or {@code --project-dir} lacks a value
   * @throws IOException              if an I/O error occurs
   */
  @Override
  public String getOutput(String[] args) throws IOException
  {
    requireThat(args, "args").isNotNull();
    Path projectPath = null;
    for (int i = 0; i < args.length; ++i)
    {
      if (args[i].equals("--project-dir"))
      {
        if (i + 1 >= args.length)
          throw new IllegalArgumentException("Missing PATH argument for --project-dir");
        projectPath = Path.of(args[i + 1]);
        ++i;
      }
      else
        throw new IllegalArgumentException("Unknown argument: " + args[i]);
    }
    if (projectPath == null)
      projectPath = scope.getProjectPath();
    return "GetCheckpointOutput: project-dir=" + projectPath;
  }

  /**
   * CLI entry point for generating checkpoint boxes.
   *
   * @param args command line arguments
   */
  public static void main(String[] args) throws IOException
  {
    try (ClaudeTool scope = new MainClaudeTool())
    {
      try
      {
        run(scope, args, System.out);
      }
      catch (IllegalArgumentException e)
      {
        System.out.println(block(scope,
          Objects.toString(e.getMessage(), e.getClass().getSimpleName())));
      }
      catch (RuntimeException | AssertionError e)
      {
        Logger log = LoggerFactory.getLogger(GetCheckpointOutput.class);
        log.error("Unexpected error", e);
        System.out.println(block(scope,
          Objects.toString(e.getMessage(), e.getClass().getSimpleName())));
      }
    }
  }

  /**
   * Executes the checkpoint output logic with a caller-provided output stream.
   *
   * @param scope the JVM scope
   * @param args  command line arguments
   * @param out   the output stream to write to
   * @throws NullPointerException     if {@code scope}, {@code args} or {@code out} are null
   * @throws IllegalArgumentException if arguments are invalid
   */
  public static void run(ClaudeTool scope, String[] args, PrintStream out)
  {
    requireThat(scope, "scope").isNotNull();
    requireThat(args, "args").isNotNull();
    requireThat(out, "out").isNotNull();
    if (args.length < 2)
      throw new IllegalArgumentException("Usage: GetCheckpointOutput --type TYPE [options]");

    String type = "";
    String issueName = "";
    String tokens = "";
    String percent = "";
    String branch = "";
    String iteration = "";
    String total = "";

    for (int i = 0; i + 1 < args.length; i += 2)
    {
      switch (args[i])
      {
        case "--type" -> type = args[i + 1];
        case "--issue-name" -> issueName = args[i + 1];
        case "--tokens" -> tokens = args[i + 1];
        case "--percent" -> percent = args[i + 1];
        case "--branch" -> branch = args[i + 1];
        case "--iteration" -> iteration = args[i + 1];
        case "--total" -> total = args[i + 1];
        default -> throw new IllegalArgumentException(
          "Unknown argument: " + args[i] + ". Valid arguments: --type, --issue-name, --tokens, " +
            "--percent, --branch, --iteration, --total");
      }
    }

    GetCheckpointOutput output = new GetCheckpointOutput(scope);

    switch (type)
    {
      case "issue-complete" ->
      {
        if (tokens.isEmpty() || percent.isEmpty())
          throw new IllegalArgumentException("--tokens and --percent required for issue-complete type");
        String box = output.getCheckpointIssueComplete(issueName, tokens, percent, branch);
        out.println(box);
      }
      case "feedback-applied" ->
      {
        if (iteration.isEmpty() || tokens.isEmpty() || total.isEmpty())
          throw new IllegalArgumentException(
            "--iteration, --tokens, and --total required for feedback-applied type");
        String box = output.getCheckpointFeedbackApplied(issueName, iteration, tokens, total, branch);
        out.println(box);
      }
      default -> throw new IllegalArgumentException("Invalid type: " + type);
    }
  }

  /**
   * Builds the checkpoint issue complete box.
   *
   * @param issueName the completed issue name
   * @param tokens the token count as a string
   * @param percent the percentage of context as a string
   * @param branch the branch name
   * @return the formatted box
   * @throws NullPointerException if any parameter is null
   * @throws IllegalArgumentException if any parameter is blank
   */
  public String getCheckpointIssueComplete(String issueName, String tokens, String percent, String branch)
  {
    requireThat(issueName, "issueName").isNotBlank();
    requireThat(tokens, "tokens").isNotBlank();
    requireThat(percent, "percent").isNotBlank();
    requireThat(branch, "branch").isNotBlank();

    DisplayUtils display = scope.getDisplayUtils();
    String header = "✅ **CHECKPOINT: Issue Complete**";

    List<String> mainContent = List.of(
      "",
      "**Issue:** " + issueName,
      "");
    List<String> metrics = List.of(
      "**Tokens:** " + tokens + " (" + percent + "% of context)");
    List<String> branchContent = List.of(
      "**Branch:** " + branch,
      "");

    return buildMultiSectionBox(display, header, mainContent, metrics, branchContent);
  }

  /**
   * Builds the checkpoint feedback applied box.
   *
   * @param issueName the issue name
   * @param iteration the feedback iteration number as a string
   * @param tokens the token count in thousands as a string
   * @param total the total tokens in thousands as a string
   * @param branch the branch name
   * @return the formatted box
   * @throws NullPointerException if any parameter is null
   * @throws IllegalArgumentException if any parameter is blank
   */
  public String getCheckpointFeedbackApplied(String issueName, String iteration, String tokens, String total,
                                             String branch)
  {
    requireThat(issueName, "issueName").isNotBlank();
    requireThat(iteration, "iteration").isNotBlank();
    requireThat(tokens, "tokens").isNotBlank();
    requireThat(total, "total").isNotBlank();
    requireThat(branch, "branch").isNotBlank();

    DisplayUtils display = scope.getDisplayUtils();
    String header = "✅ **CHECKPOINT: Feedback Applied**";

    List<String> mainContent = List.of(
      "",
      "**Issue:** " + issueName,
      "**Feedback iteration:** " + iteration,
      "");
    List<String> metrics = List.of(
      "**Feedback subagent:** " + tokens + "K tokens",
      "**Total tokens (all iterations):** " + total + "K");
    List<String> branchContent = List.of(
      "**Branch:** " + branch,
      "");

    return buildMultiSectionBox(display, header, mainContent, metrics, branchContent);
  }

  /**
   * Builds a multi-section box with three content sections separated by horizontal lines.
   *
   * @param display the display utilities
   * @param header the header text
   * @param content1 the first section content
   * @param content2 the second section content
   * @param content3 the third section content
   * @return the formatted box
   */
  private String buildMultiSectionBox(DisplayUtils display, String header, List<String> content1,
                                      List<String> content2, List<String> content3)
  {
    List<String> allContent = new ArrayList<>();
    allContent.addAll(content1);
    allContent.addAll(content2);
    allContent.addAll(content3);

    int headerWidth = display.displayWidth(header);
    int maxWidth = headerWidth;
    for (String line : allContent)
    {
      int w = display.displayWidth(line);
      if (w > maxWidth)
        maxWidth = w;
    }

    List<String> lines = new ArrayList<>();
    lines.add(header);
    lines.add(display.buildTopBorder(maxWidth));
    for (String content : content1)
      lines.add(display.buildLine(content, maxWidth));
    lines.add(display.buildSeparator(maxWidth));
    for (String content : content2)
      lines.add(display.buildLine(content, maxWidth));
    lines.add(display.buildSeparator(maxWidth));
    for (String content : content3)
      lines.add(display.buildLine(content, maxWidth));
    lines.add(display.buildBottomBorder(maxWidth));

    return String.join("\n", lines);
  }
}
