/*
 * Copyright (c) 2026 Gili Tzabari. All rights reserved.
 *
 * Licensed under the CAT Commercial License.
 * See LICENSE.md in the project root for license terms.
 */
package io.github.cowwoc.cat.claude.hook.licensing;

import static io.github.cowwoc.requirements13.java.DefaultJavaValidators.requireThat;

import io.github.cowwoc.cat.claude.tool.ClaudeTool;
import io.github.cowwoc.cat.tool.licensing.LicenseResult;

import java.nio.file.Path;

/**
 * Claude adapter for the runtime-neutral license validator.
 */
public final class LicenseValidator
{
  private final io.github.cowwoc.cat.tool.licensing.LicenseValidator delegate;

  /**
   * Creates a new license validator.
   *
   * @param scope the scope providing JSON mapper and plugin root
   * @throws NullPointerException if {@code scope} is null
   */
  public LicenseValidator(ClaudeTool scope)
  {
    requireThat(scope, "scope").isNotNull();
    this.delegate = new io.github.cowwoc.cat.tool.licensing.LicenseValidator(scope);
  }

  /**
   * Validates the license token from the project configuration.
   *
   * @param projectPath the project root directory containing .cat/
   * @return the validation result
   */
  public LicenseResult validate(Path projectPath)
  {
    return delegate.validate(projectPath);
  }
}
