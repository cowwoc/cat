/*
 * Copyright (c) 2026 Gili Tzabari. All rights reserved.
 *
 * Licensed under the CAT Commercial License.
 * See LICENSE.md in the project root for license terms.
 */
package io.github.cowwoc.cat.codex.engine;

import static io.github.cowwoc.requirements13.java.DefaultJavaValidators.requireThat;

import java.util.Map;
import java.util.Objects;

/**
 * Command policy helpers for nested Codex executions.
 */
public final class CodexSandboxPolicy
{
  /**
   * Flag that mirrors parent yolo mode in nested Codex executions.
   */
  public static final String YOLO_FLAG = "--dangerously-bypass-approvals-and-sandbox";
  /**
   * Sandbox mode used for nested Codex executions in already-sandboxed CAT engines.
   */
  public static final String NESTED_SANDBOX_MODE = "danger-full-access";

  private CodexSandboxPolicy()
  {
  }

  /**
   * Returns {@code true} if the runner is already executing inside a Codex-managed sandbox.
   *
   * @param environment the environment to inspect
   * @return {@code true} if nested Codex executions need an explicit sandbox override
   * @throws NullPointerException if {@code environment} is null
   */
  public static boolean isExternallySandboxedEngine(Map<String, String> environment)
  {
    requireThat(environment, "environment").isNotNull();
    String codexTool = environment.get("CODEX_TOOL");
    String codexCi = environment.get("CODEX_CI");
    return Objects.equals(codexTool, "codex-cli") ||
      Objects.equals(codexCi, "1") ||
      (codexCi != null && codexCi.equalsIgnoreCase("true"));
  }

  /**
   * Returns {@code true} if nested executions should inherit yolo mode.
   *
   * @param environment the environment to inspect
   * @return {@code true} if yolo mode should be passed to nested Codex
   * @throws NullPointerException if {@code environment} is null
   */
  public static boolean shouldInheritYoloMode(Map<String, String> environment)
  {
    requireThat(environment, "environment").isNotNull();
    String approvalPolicy = environment.get("CODEX_APPROVAL_POLICY");
    String codexCi = environment.get("CODEX_CI");
    return (approvalPolicy != null && approvalPolicy.equalsIgnoreCase("never")) ||
      Objects.equals(codexCi, "1") ||
      (codexCi != null && codexCi.equalsIgnoreCase("true"));
  }
}
