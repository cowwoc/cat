/*
 * Copyright (c) 2026 Gili Tzabari. All rights reserved.
 *
 * Licensed under the CAT Commercial License.
 * See LICENSE.md in the project root for license terms.
 */
package io.github.cowwoc.cat.hooks;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodHandles.Lookup;
import java.util.List;

/**
 * A repository of "shared secrets" for calling package-private constructors from other modules without
 * using reflection.
 * <p>
 * Each class that exposes package-private functionality registers an access implementation via a static
 * initializer. Consumers retrieve the access object and invoke methods on it.
 *
 * @see <a href="https://stackoverflow.com/questions/46722452/how-does-the-sharedsecrets-mechanism-work">
 *   How does the SharedSecrets mechanism work?</a>
 */
public final class SharedSecrets
{
  private static final Lookup LOOKUP = MethodHandles.lookup();
  private static PostToolUseHookAccess postToolUseHookAccess;
  private static PostToolUseFailureHookAccess postToolUseFailureHookAccess;

  private SharedSecrets()
  {
  }

  /**
   * Registers the access object for {@link PostToolUseHook}.
   *
   * @param access the access object
   */
  public static void setPostToolUseHookAccess(PostToolUseHookAccess access)
  {
    postToolUseHookAccess = access;
  }

  /**
   * Creates a new {@link PostToolUseHook} with the specified handlers.
   *
   * @param handlers the handlers to use
   * @return a new PostToolUseHook
   * @throws NullPointerException if {@code handlers} is null
   */
  public static PostToolUseHook newPostToolUseHook(List<PostToolHandler> handlers)
  {
    PostToolUseHookAccess access = postToolUseHookAccess;
    if (access == null)
    {
      initialize(PostToolUseHook.class);
      access = postToolUseHookAccess;
      assert access != null;
    }
    return access.newPostToolUseHook(handlers);
  }

  /**
   * Registers the access object for {@link PostToolUseFailureHook}.
   *
   * @param access the access object
   */
  public static void setPostToolUseFailureHookAccess(PostToolUseFailureHookAccess access)
  {
    postToolUseFailureHookAccess = access;
  }

  /**
   * Creates a new {@link PostToolUseFailureHook} with the specified handlers.
   *
   * @param handlers the handlers to use
   * @return a new PostToolUseFailureHook
   * @throws NullPointerException if {@code handlers} is null
   */
  public static PostToolUseFailureHook newPostToolUseFailureHook(List<PostToolHandler> handlers)
  {
    PostToolUseFailureHookAccess access = postToolUseFailureHookAccess;
    if (access == null)
    {
      initialize(PostToolUseFailureHook.class);
      access = postToolUseFailureHookAccess;
      assert access != null;
    }
    return access.newPostToolUseFailureHook(handlers);
  }

  /**
   * Initializes a class. If the class is already initialized, this method has no effect.
   *
   * @param clazz the class
   */
  private static void initialize(Class<?> clazz)
  {
    try
    {
      LOOKUP.ensureInitialized(clazz);
    }
    catch (IllegalAccessException e)
    {
      throw new AssertionError(e);
    }
  }
}
