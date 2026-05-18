/*
 * Copyright (c) 2026 Gili Tzabari. All rights reserved.
 *
 * Licensed under the CAT Commercial License.
 * See LICENSE.md in the project root for license terms.
 */
module io.github.cowwoc.cat.codex.cli
{
  requires io.github.cowwoc.cat.common.cli;
  requires tools.jackson.databind;
  requires tools.jackson.dataformat.yaml;
  requires io.github.cowwoc.requirements13.java;
  requires io.github.cowwoc.requirements13.jackson;
  requires io.github.cowwoc.pouch10.core;
  requires java.net.http;
  requires org.slf4j;
  requires ch.qos.logback.classic;

  exports io.github.cowwoc.cat.codex.hook;
  exports io.github.cowwoc.cat.codex.engine;
  exports io.github.cowwoc.cat.codex.tool;
}
