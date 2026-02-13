package io.github.cowwoc.cat.hooks.util;

import java.io.IOException;

/**
 * Interface for skill handlers that generate dynamic output.
 * <p>
 * Skill handlers are invoked by {@link SkillLoader} when their variable is referenced in skill content.
 * Each handler provides contextual output based on the current project state.
 * <p>
 * <b>Implementation requirements:</b> Implementing classes must provide a public constructor
 * accepting a single {@link io.github.cowwoc.cat.hooks.JvmScope} parameter. The class is
 * registered in a skill's {@code bindings.json} file and instantiated via reflection by
 * {@link SkillLoader}.
 *
 * @see SkillLoader
 */
@FunctionalInterface
public interface SkillOutput
{
  /**
   * Generates the handler output.
   * <p>
   * The output is substituted into the skill content wherever the bound variable is referenced.
   *
   * @return the handler output, never null
   * @throws IOException if generation fails
   */
  String getOutput() throws IOException;
}
