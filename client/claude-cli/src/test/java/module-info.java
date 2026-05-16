/*
 * Copyright (c) 2026 Gili Tzabari. All rights reserved.
 *
 * Licensed under the CAT Commercial License.
 * See LICENSE.md in the project root for license terms.
 */
module io.github.cowwoc.cat.client.test
{
  requires io.github.cowwoc.cat.common.cli;
  requires io.github.cowwoc.cat.claude.cli;
  requires io.github.cowwoc.cat.codex.cli;
  requires org.testng;
  requires io.github.cowwoc.requirements13.java;
  requires io.github.cowwoc.requirements13.jackson;
  requires tools.jackson.core;
  requires tools.jackson.databind;
  requires tools.jackson.dataformat.yaml;
  requires io.github.cowwoc.pouch10.core;

  exports io.github.cowwoc.cat.client.test to org.testng;
  exports io.github.cowwoc.cat.client.test.claude to org.testng;
  exports io.github.cowwoc.cat.client.test.codex to org.testng;
  exports io.github.cowwoc.cat.client.test.common to org.testng;
}
