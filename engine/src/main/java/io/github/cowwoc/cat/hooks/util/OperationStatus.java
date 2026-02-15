/*
 * Copyright (c) 2026 Gili Tzabari. All rights reserved.
 *
 * Licensed under the CAT Commercial License.
 * See LICENSE.md in the project root for license terms.
 */
package io.github.cowwoc.cat.hooks.util;

/**
 * Status of an operation result.
 */
public enum OperationStatus
{
  /** Operation completed successfully. */
  SUCCESS("success"),
  /** Operation failed with an error. */
  ERROR("error");

  private final String jsonValue;

  OperationStatus(String jsonValue)
  {
    this.jsonValue = jsonValue;
  }

  /**
   * Returns the JSON string representation.
   *
   * @return the JSON value
   */
  public String toJson()
  {
    return jsonValue;
  }
}
