/*
 * Copyright (c) 2026 Gili Tzabari. All rights reserved.
 *
 * Licensed under the CAT Commercial License.
 * See LICENSE.md in the project root for license terms.
 */
package io.github.cowwoc.cat.client.test.codex;

import static io.github.cowwoc.requirements13.java.DefaultJavaValidators.requireThat;

import io.github.cowwoc.cat.codex.engine.CodexSandboxPolicy;

import java.util.Map;
import org.testng.annotations.Test;

/**
 * Tests for {@link CodexSandboxPolicy}.
 */
public final class CodexSandboxPolicyTest
{
  /**
   * Verifies that CAT session runs are treated as nested sandboxed executions.
   */
  @Test
  public void catSessionDoesNotForceNestedSandboxOverride()
  {
    boolean result = CodexSandboxPolicy.isExternallySandboxedEngine(Map.of("CAT_SESSION_ID",
      "session-123"));

    requireThat(result, "result").isFalse();
  }

  /**
   * Verifies that approval policy never enables yolo inheritance.
   */
  @Test
  public void approvalNeverEnablesYoloInheritance()
  {
    boolean result = CodexSandboxPolicy.shouldInheritYoloMode(Map.of("CODEX_APPROVAL_POLICY",
      "never"));

    requireThat(result, "result").isTrue();
  }

  /**
   * Verifies that CI-mode Codex runs inherit yolo mode.
   */
  @Test
  public void codexCiEnablesYoloInheritance()
  {
    boolean result = CodexSandboxPolicy.shouldInheritYoloMode(Map.of("CODEX_CI", "1"));

    requireThat(result, "result").isTrue();
  }
}
