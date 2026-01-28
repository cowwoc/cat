package io.github.cowwoc.cat.hooks;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Registry for all hook handlers.
 *
 * <p>Handlers register themselves with this registry, and entry points query it
 * to dispatch to the appropriate handlers.</p>
 *
 * <p>This class is not thread-safe. Handlers should be registered during
 * static initialization before any concurrent access.</p>
 */
public final class HandlerRegistry
{
  private static final Map<String, SkillHandler> SKILL_HANDLERS = new HashMap<>();
  private static final List<PromptHandler> PROMPT_HANDLERS = new ArrayList<>();
  private static final List<BashHandler> BASH_PRETOOL_HANDLERS = new ArrayList<>();
  private static final List<BashHandler> BASH_POSTTOOL_HANDLERS = new ArrayList<>();
  private static final List<ReadHandler> READ_PRETOOL_HANDLERS = new ArrayList<>();
  private static final List<ReadHandler> READ_POSTTOOL_HANDLERS = new ArrayList<>();
  private static final List<PosttoolHandler> POSTTOOL_HANDLERS = new ArrayList<>();

  private HandlerRegistry()
  {
    // Utility class
  }

  // --- Skill Handlers ---

  /**
   * Register a skill handler for a skill name.
   *
   * @param skillName the skill name (e.g., "status", "work")
   * @param handler the handler to register
   */
  public static void registerSkillHandler(String skillName, SkillHandler handler)
  {
    SKILL_HANDLERS.put(skillName, handler);
  }

  /**
   * Get the skill handler for a skill name.
   *
   * @param skillName the skill name
   * @return the handler, or null if not registered
   */
  public static SkillHandler getSkillHandler(String skillName)
  {
    return SKILL_HANDLERS.get(skillName);
  }

  // --- Prompt Handlers ---

  /**
   * Register a prompt handler.
   *
   * @param handler the handler to register
   */
  public static void registerPromptHandler(PromptHandler handler)
  {
    PROMPT_HANDLERS.add(handler);
  }

  /**
   * Get all registered prompt handlers.
   *
   * @return a copy of the prompt handler list
   */
  public static List<PromptHandler> getPromptHandlers()
  {
    return new ArrayList<>(PROMPT_HANDLERS);
  }

  // --- Bash Handlers ---

  /**
   * Register a bash pretool handler.
   *
   * @param handler the handler to register
   */
  public static void registerBashPretoolHandler(BashHandler handler)
  {
    BASH_PRETOOL_HANDLERS.add(handler);
  }

  /**
   * Get all registered bash pretool handlers.
   *
   * @return a copy of the handler list
   */
  public static List<BashHandler> getBashPretoolHandlers()
  {
    return new ArrayList<>(BASH_PRETOOL_HANDLERS);
  }

  /**
   * Register a bash posttool handler.
   *
   * @param handler the handler to register
   */
  public static void registerBashPosttoolHandler(BashHandler handler)
  {
    BASH_POSTTOOL_HANDLERS.add(handler);
  }

  /**
   * Get all registered bash posttool handlers.
   *
   * @return a copy of the handler list
   */
  public static List<BashHandler> getBashPosttoolHandlers()
  {
    return new ArrayList<>(BASH_POSTTOOL_HANDLERS);
  }

  // --- Read Handlers ---

  /**
   * Register a read pretool handler.
   *
   * @param handler the handler to register
   */
  public static void registerReadPretoolHandler(ReadHandler handler)
  {
    READ_PRETOOL_HANDLERS.add(handler);
  }

  /**
   * Get all registered read pretool handlers.
   *
   * @return a copy of the handler list
   */
  public static List<ReadHandler> getReadPretoolHandlers()
  {
    return new ArrayList<>(READ_PRETOOL_HANDLERS);
  }

  /**
   * Register a read posttool handler.
   *
   * @param handler the handler to register
   */
  public static void registerReadPosttoolHandler(ReadHandler handler)
  {
    READ_POSTTOOL_HANDLERS.add(handler);
  }

  /**
   * Get all registered read posttool handlers.
   *
   * @return a copy of the handler list
   */
  public static List<ReadHandler> getReadPosttoolHandlers()
  {
    return new ArrayList<>(READ_POSTTOOL_HANDLERS);
  }

  // --- Posttool Handlers (General) ---

  /**
   * Register a general posttool handler.
   *
   * @param handler the handler to register
   */
  public static void registerPosttoolHandler(PosttoolHandler handler)
  {
    POSTTOOL_HANDLERS.add(handler);
  }

  /**
   * Get all registered general posttool handlers.
   *
   * @return a copy of the handler list
   */
  public static List<PosttoolHandler> getPosttoolHandlers()
  {
    return new ArrayList<>(POSTTOOL_HANDLERS);
  }

  // --- Testing Support ---

  /**
   * Clear all registered handlers. Used for testing only.
   */
  public static void clearAll()
  {
    SKILL_HANDLERS.clear();
    PROMPT_HANDLERS.clear();
    BASH_PRETOOL_HANDLERS.clear();
    BASH_POSTTOOL_HANDLERS.clear();
    READ_PRETOOL_HANDLERS.clear();
    READ_POSTTOOL_HANDLERS.clear();
    POSTTOOL_HANDLERS.clear();
  }
}
