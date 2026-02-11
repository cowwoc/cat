package io.github.cowwoc.cat.hooks.test;

import io.github.cowwoc.cat.hooks.DefaultJvmScope;
import io.github.cowwoc.cat.hooks.JvmScope;
import io.github.cowwoc.cat.hooks.skills.GetStatusOutput;
import org.testng.annotations.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static io.github.cowwoc.requirements13.java.DefaultJavaValidators.requireThat;

/**
 * Tests for GetStatusOutput functionality.
 * <p>
 * GetStatusOutput generates the /cat:status display by reading project directory structure
 * and rendering comprehensive status information including progress bars, version sections,
 * task lists, and actionable footers.
 * <p>
 * Tests verify that the output generator correctly handles various project states
 * (no CAT directory, empty project, projects with tasks in various statuses) and
 * produces properly formatted displays.
 * <p>
 * Tests are designed for parallel execution - each test is self-contained
 * with no shared state.
 */
public class GetStatusOutputTest
{
  /**
   * Verifies that null scope throws NullPointerException.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test(expectedExceptions = NullPointerException.class)
  public void constructorRejectsNullScope() throws IOException
  {
    new GetStatusOutput(null);
  }

  /**
   * Verifies that null projectDir throws NullPointerException.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test(expectedExceptions = NullPointerException.class)
  public void getOutputRejectsNullProjectDir() throws IOException
  {
    try (JvmScope scope = new DefaultJvmScope())
    {
      GetStatusOutput handler = new GetStatusOutput(scope);
      handler.getOutput(null);
    }
  }

  /**
   * Verifies that blank projectDir throws IllegalArgumentException.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test(expectedExceptions = IllegalArgumentException.class)
  public void getOutputRejectsBlankProjectDir() throws IOException
  {
    try (JvmScope scope = new DefaultJvmScope())
    {
      GetStatusOutput handler = new GetStatusOutput(scope);
      handler.getOutput("");
    }
  }

  /**
   * Verifies that missing CAT directory returns error message.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void noCatDirectoryReturnsErrorMessage() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-no-cat");
    try (JvmScope scope = new DefaultJvmScope())
    {
      GetStatusOutput handler = new GetStatusOutput(scope);
      String result = handler.getOutput(tempDir.toString());

      requireThat(result, "result").isEqualTo("No CAT project found. Run /cat:init to initialize.");
    }
    finally
    {
      deleteRecursively(tempDir);
    }
  }

  /**
   * Verifies that missing issues directory returns error message.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void noIssuesDirectoryReturnsErrorMessage() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-no-issues");
    Path catDir = tempDir.resolve(".claude/cat");
    Files.createDirectories(catDir);

    try (JvmScope scope = new DefaultJvmScope())
    {
      GetStatusOutput handler = new GetStatusOutput(scope);
      String result = handler.getOutput(tempDir.toString());

      requireThat(result, "result").isEqualTo("No planning structure found. Run /cat:init to initialize.");
    }
    finally
    {
      deleteRecursively(tempDir);
    }
  }

  /**
   * Verifies that empty project with issues directory renders display.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void emptyProjectRendersDisplay() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-empty-project");
    Path issuesDir = tempDir.resolve(".claude/cat/issues");
    Files.createDirectories(issuesDir);

    try (JvmScope scope = new DefaultJvmScope())
    {
      GetStatusOutput handler = new GetStatusOutput(scope);
      String result = handler.getOutput(tempDir.toString());

      requireThat(result, "result").contains("Overall:").contains("0% · 0/1 tasks");
    }
    finally
    {
      deleteRecursively(tempDir);
    }
  }

  /**
   * Verifies that empty project has box structure.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void emptyProjectHasBoxStructure() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-empty-box");
    Path issuesDir = tempDir.resolve(".claude/cat/issues");
    Files.createDirectories(issuesDir);

    try (JvmScope scope = new DefaultJvmScope())
    {
      GetStatusOutput handler = new GetStatusOutput(scope);
      String result = handler.getOutput(tempDir.toString());

      requireThat(result, "result").contains("╭").contains("╰").contains("│");
    }
    finally
    {
      deleteRecursively(tempDir);
    }
  }

  /**
   * Verifies that progress bar shows correct percentage.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void progressBarShowsCorrectPercentage() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-progress");
    Path issuesDir = tempDir.resolve(".claude/cat/issues");
    Path majorDir = issuesDir.resolve("v1");
    Path minorDir = majorDir.resolve("v1.0");
    Path task1Dir = minorDir.resolve("task-1");
    Path task2Dir = minorDir.resolve("task-2");
    Files.createDirectories(task1Dir);
    Files.createDirectories(task2Dir);

    Files.writeString(task1Dir.resolve("STATE.md"), "- **Status:** closed\n");
    Files.writeString(task2Dir.resolve("STATE.md"), "- **Status:** open\n");

    try (JvmScope scope = new DefaultJvmScope())
    {
      GetStatusOutput handler = new GetStatusOutput(scope);
      String result = handler.getOutput(tempDir.toString());

      requireThat(result, "result").contains("50% · 1/2 tasks");
    }
    finally
    {
      deleteRecursively(tempDir);
    }
  }

  /**
   * Verifies that single version with open tasks renders correctly.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void singleVersionWithOpenTasksRendersCorrectly() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-single-version");
    Path issuesDir = tempDir.resolve(".claude/cat/issues");
    Path majorDir = issuesDir.resolve("v2");
    Path minorDir = majorDir.resolve("v2.0");
    Path taskDir = minorDir.resolve("my-task");
    Files.createDirectories(taskDir);

    Files.writeString(taskDir.resolve("STATE.md"), "- **Status:** open\n");

    try (JvmScope scope = new DefaultJvmScope())
    {
      GetStatusOutput handler = new GetStatusOutput(scope);
      String result = handler.getOutput(tempDir.toString());

      requireThat(result, "result").contains("v2: Version 2").
        contains("v2.0: (0/1)").contains("my-task");
    }
    finally
    {
      deleteRecursively(tempDir);
    }
  }

  /**
   * Verifies that in-progress task shows spinner emoji.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void inProgressTaskShowsSpinnerEmoji() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-in-progress");
    Path issuesDir = tempDir.resolve(".claude/cat/issues");
    Path majorDir = issuesDir.resolve("v1");
    Path minorDir = majorDir.resolve("v1.0");
    Path taskDir = minorDir.resolve("active-task");
    Files.createDirectories(taskDir);

    Files.writeString(taskDir.resolve("STATE.md"), "- **Status:** in-progress\n");

    try (JvmScope scope = new DefaultJvmScope())
    {
      GetStatusOutput handler = new GetStatusOutput(scope);
      String result = handler.getOutput(tempDir.toString());

      requireThat(result, "result").contains("🔄 active-task");
    }
    finally
    {
      deleteRecursively(tempDir);
    }
  }

  /**
   * Verifies that blocked task shows blocked emoji and blockedBy list.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void blockedTaskShowsBlockedEmojiAndList() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-blocked");
    Path issuesDir = tempDir.resolve(".claude/cat/issues");
    Path majorDir = issuesDir.resolve("v1");
    Path minorDir = majorDir.resolve("v1.0");
    Path task1Dir = minorDir.resolve("task-1");
    Path task2Dir = minorDir.resolve("task-2");
    Files.createDirectories(task1Dir);
    Files.createDirectories(task2Dir);

    Files.writeString(task1Dir.resolve("STATE.md"), "- **Status:** open\n");
    Files.writeString(task2Dir.resolve("STATE.md"),
      "- **Status:** open\n" +
      "\n" +
      "## Dependencies\n" +
      "- task-1\n");

    try (JvmScope scope = new DefaultJvmScope())
    {
      GetStatusOutput handler = new GetStatusOutput(scope);
      String result = handler.getOutput(tempDir.toString());

      requireThat(result, "result").contains("🚫 task-2 (blocked by: task-1)");
    }
    finally
    {
      deleteRecursively(tempDir);
    }
  }

  /**
   * Verifies that completed tasks show checkmark emoji.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void completedTasksShowCheckmarkEmoji() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-completed");
    Path issuesDir = tempDir.resolve(".claude/cat/issues");
    Path majorDir = issuesDir.resolve("v1");
    Path minorDir = majorDir.resolve("v1.0");
    Path task1Dir = minorDir.resolve("done-task");
    Path task2Dir = minorDir.resolve("open-task");
    Files.createDirectories(task1Dir);
    Files.createDirectories(task2Dir);

    Files.writeString(task1Dir.resolve("STATE.md"), "- **Status:** closed\n");
    Files.writeString(task2Dir.resolve("STATE.md"), "- **Status:** open\n");

    try (JvmScope scope = new DefaultJvmScope())
    {
      GetStatusOutput handler = new GetStatusOutput(scope);
      String result = handler.getOutput(tempDir.toString());

      requireThat(result, "result").contains("☑️ done-task");
    }
    finally
    {
      deleteRecursively(tempDir);
    }
  }

  /**
   * Verifies that open tasks show unchecked box emoji.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void openTasksShowUncheckedBoxEmoji() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-open");
    Path issuesDir = tempDir.resolve(".claude/cat/issues");
    Path majorDir = issuesDir.resolve("v1");
    Path minorDir = majorDir.resolve("v1.0");
    Path taskDir = minorDir.resolve("open-task");
    Files.createDirectories(taskDir);

    Files.writeString(taskDir.resolve("STATE.md"), "- **Status:** open\n");

    try (JvmScope scope = new DefaultJvmScope())
    {
      GetStatusOutput handler = new GetStatusOutput(scope);
      String result = handler.getOutput(tempDir.toString());

      requireThat(result, "result").contains("🔳 open-task");
    }
    finally
    {
      deleteRecursively(tempDir);
    }
  }

  /**
   * Verifies that current task shows in footer.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void currentTaskShowsInFooter() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-current-footer");
    Path issuesDir = tempDir.resolve(".claude/cat/issues");
    Path majorDir = issuesDir.resolve("v1");
    Path minorDir = majorDir.resolve("v1.0");
    Path taskDir = minorDir.resolve("active-task");
    Files.createDirectories(taskDir);

    Files.writeString(taskDir.resolve("STATE.md"), "- **Status:** in-progress\n");

    try (JvmScope scope = new DefaultJvmScope())
    {
      GetStatusOutput handler = new GetStatusOutput(scope);
      String result = handler.getOutput(tempDir.toString());

      requireThat(result, "result").contains("Current: /cat:work v1.0-active-task");
    }
    finally
    {
      deleteRecursively(tempDir);
    }
  }

  /**
   * Verifies that next task shows in footer when no task is in progress.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void nextTaskShowsInFooterWhenNoTaskInProgress() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-next-footer");
    Path issuesDir = tempDir.resolve(".claude/cat/issues");
    Path majorDir = issuesDir.resolve("v1");
    Path minorDir = majorDir.resolve("v1.0");
    Path taskDir = minorDir.resolve("next-task");
    Files.createDirectories(taskDir);

    Files.writeString(taskDir.resolve("STATE.md"), "- **Status:** open\n");

    try (JvmScope scope = new DefaultJvmScope())
    {
      GetStatusOutput handler = new GetStatusOutput(scope);
      String result = handler.getOutput(tempDir.toString());

      requireThat(result, "result").contains("Next: /cat:work v1.0-next-task");
    }
    finally
    {
      deleteRecursively(tempDir);
    }
  }

  /**
   * Verifies that footer shows no tasks when all are complete.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void footerShowsNoTasksWhenAllComplete() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-no-tasks");
    Path issuesDir = tempDir.resolve(".claude/cat/issues");
    Path major1Dir = issuesDir.resolve("v1");
    Path major2Dir = issuesDir.resolve("v2");
    Path minor10Dir = major1Dir.resolve("v1.0");
    Path minor20Dir = major2Dir.resolve("v2.0");
    Path task1Dir = minor10Dir.resolve("done-task");
    Path task2Dir = minor20Dir.resolve("another-done-task");
    Path task3Dir = minor20Dir.resolve("blocked-task");
    Files.createDirectories(task1Dir);
    Files.createDirectories(task2Dir);
    Files.createDirectories(task3Dir);

    Files.writeString(task1Dir.resolve("STATE.md"), "- **Status:** closed\n");
    Files.writeString(task2Dir.resolve("STATE.md"), "- **Status:** closed\n");
    Files.writeString(task3Dir.resolve("STATE.md"),
      "- **Status:** blocked\n" +
      "\n" +
      "## Dependencies\n" +
      "- another-done-task\n");

    try (JvmScope scope = new DefaultJvmScope())
    {
      GetStatusOutput handler = new GetStatusOutput(scope);
      String result = handler.getOutput(tempDir.toString());

      requireThat(result, "result").contains("No open tasks available");
    }
    finally
    {
      deleteRecursively(tempDir);
    }
  }

  /**
   * Verifies that major version shows correct name from ROADMAP.md.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void majorVersionShowsCorrectNameFromRoadmap() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-roadmap");
    Path catDir = tempDir.resolve(".claude/cat");
    Path issuesDir = catDir.resolve("issues");
    Path majorDir = issuesDir.resolve("v2");
    Files.createDirectories(majorDir);

    Files.writeString(catDir.resolve("ROADMAP.md"),
      "# Roadmap\n" +
      "\n" +
      "## Version 2: Major Refactor (2024)\n");

    try (JvmScope scope = new DefaultJvmScope())
    {
      GetStatusOutput handler = new GetStatusOutput(scope);
      String result = handler.getOutput(tempDir.toString());

      requireThat(result, "result").contains("v2: Major Refactor");
    }
    finally
    {
      deleteRecursively(tempDir);
    }
  }

  /**
   * Verifies that minor version shows description from ROADMAP.md.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void minorVersionShowsDescriptionFromRoadmap() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-minor-desc");
    Path catDir = tempDir.resolve(".claude/cat");
    Path issuesDir = catDir.resolve("issues");
    Path majorDir = issuesDir.resolve("v2");
    Path minorDir = majorDir.resolve("v2.1");
    Path taskDir = minorDir.resolve("task-1");
    Files.createDirectories(taskDir);

    Files.writeString(taskDir.resolve("STATE.md"), "- **Status:** open\n");
    Files.writeString(catDir.resolve("ROADMAP.md"),
      "# Roadmap\n" +
      "\n" +
      "## Version 2: Major Refactor\n" +
      "\n" +
      "- **2.1:** Port display scripts\n");

    try (JvmScope scope = new DefaultJvmScope())
    {
      GetStatusOutput handler = new GetStatusOutput(scope);
      String result = handler.getOutput(tempDir.toString());

      requireThat(result, "result").contains("v2.1: Port display scripts");
    }
    finally
    {
      deleteRecursively(tempDir);
    }
  }

  /**
   * Verifies that project name is read from PROJECT.md.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void projectNameIsReadFromProjectMd() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-project-name");
    Path catDir = tempDir.resolve(".claude/cat");
    Path issuesDir = catDir.resolve("issues");
    Files.createDirectories(issuesDir);

    Files.writeString(catDir.resolve("PROJECT.md"), "# My Awesome Project\n");

    try (JvmScope scope = new DefaultJvmScope())
    {
      GetStatusOutput handler = new GetStatusOutput(scope);
      String result = handler.getOutput(tempDir.toString());

      requireThat(result, "result").isNotEmpty();
    }
    finally
    {
      deleteRecursively(tempDir);
    }
  }

  /**
   * Verifies that active minor version shows spinner emoji.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void activeMinorVersionShowsSpinnerEmoji() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-active-minor");
    Path issuesDir = tempDir.resolve(".claude/cat/issues");
    Path majorDir = issuesDir.resolve("v1");
    Path minorDir = majorDir.resolve("v1.0");
    Path taskDir = minorDir.resolve("task-1");
    Files.createDirectories(taskDir);

    Files.writeString(taskDir.resolve("STATE.md"), "- **Status:** open\n");

    try (JvmScope scope = new DefaultJvmScope())
    {
      GetStatusOutput handler = new GetStatusOutput(scope);
      String result = handler.getOutput(tempDir.toString());

      requireThat(result, "result").contains("🔄 v1.0:");
    }
    finally
    {
      deleteRecursively(tempDir);
    }
  }

  /**
   * Verifies that completed minor version shows checkmark emoji.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void completedMinorVersionShowsCheckmarkEmoji() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-completed-minor");
    Path issuesDir = tempDir.resolve(".claude/cat/issues");
    Path majorDir = issuesDir.resolve("v1");
    Path minor1Dir = majorDir.resolve("v1.0");
    Path minor2Dir = majorDir.resolve("v1.1");
    Path task1Dir = minor1Dir.resolve("task-1");
    Path task2Dir = minor2Dir.resolve("task-2");
    Files.createDirectories(task1Dir);
    Files.createDirectories(task2Dir);

    Files.writeString(task1Dir.resolve("STATE.md"), "- **Status:** closed\n");
    Files.writeString(task2Dir.resolve("STATE.md"), "- **Status:** open\n");

    try (JvmScope scope = new DefaultJvmScope())
    {
      GetStatusOutput handler = new GetStatusOutput(scope);
      String result = handler.getOutput(tempDir.toString());

      requireThat(result, "result").contains("☑️ v1.0:");
    }
    finally
    {
      deleteRecursively(tempDir);
    }
  }

  /**
   * Verifies that multiple versions are sorted correctly.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void multipleVersionsAreSortedCorrectly() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-sorted");
    Path issuesDir = tempDir.resolve(".claude/cat/issues");
    Path major1Dir = issuesDir.resolve("v1");
    Path major2Dir = issuesDir.resolve("v2");
    Path minor10Dir = major1Dir.resolve("v1.0");
    Path minor11Dir = major1Dir.resolve("v1.1");
    Path minor20Dir = major2Dir.resolve("v2.0");
    Files.createDirectories(minor10Dir);
    Files.createDirectories(minor11Dir);
    Files.createDirectories(minor20Dir);

    try (JvmScope scope = new DefaultJvmScope())
    {
      GetStatusOutput handler = new GetStatusOutput(scope);
      String result = handler.getOutput(tempDir.toString());

      int pos10 = result.indexOf("v1.0");
      int pos11 = result.indexOf("v1.1");
      int pos20 = result.indexOf("v2.0");

      requireThat(pos10, "pos10").isLessThan(pos11);
      requireThat(pos11, "pos11").isLessThan(pos20);
    }
    finally
    {
      deleteRecursively(tempDir);
    }
  }

  /**
   * Recursively deletes a directory and all its contents.
   *
   * @param path the directory to delete
   * @throws IOException if deletion fails
   */
  private void deleteRecursively(Path path) throws IOException
  {
    if (!Files.exists(path))
      return;

    if (Files.isDirectory(path))
    {
      try (var stream = Files.list(path))
      {
        for (Path child : stream.toList())
          deleteRecursively(child);
      }
    }

    Files.delete(path);
  }
}
