package io.github.cowwoc.cat.hooks.skills;

import io.github.cowwoc.cat.hooks.MainJvmScope;
import io.github.cowwoc.cat.hooks.JvmScope;
import java.io.IOException;

/**
 * Runner for GetStatusOutput.
 *
 * Entry point for generating CAT status display from command line.
 * Reads project directory from CLAUDE_PROJECT_DIR environment variable.
 */
public final class RunGetStatusOutput
{
  /**
   * Prevent instantiation.
   */
  private RunGetStatusOutput()
  {
  }

  /**
   * Main entry point.
   *
   * @param args command line arguments (unused)
   */
  public static void main(String[] args)
  {
    String projectDir = System.getenv("CLAUDE_PROJECT_DIR");
    if (projectDir == null || projectDir.isEmpty())
      projectDir = System.getProperty("user.dir");

    try (JvmScope scope = new MainJvmScope())
    {
      GetStatusOutput generator = new GetStatusOutput(scope);
      String output = generator.getOutput(projectDir);
      System.out.println(output);
    }
    catch (IOException e)
    {
      System.err.println("Error generating status: " + e.getMessage());
      System.exit(1);
    }
  }
}
