module io.github.cowwoc.cat.hooks
{
  requires tools.jackson.databind;
  requires io.github.cowwoc.requirements13.java;
  requires io.github.cowwoc.pouch10.core;
  requires jtokkit;
  requires java.net.http;
  requires org.slf4j;
  requires ch.qos.logback.classic;

  exports io.github.cowwoc.cat.hooks;
  exports io.github.cowwoc.cat.hooks.ask;
  exports io.github.cowwoc.cat.hooks.bash;
  exports io.github.cowwoc.cat.hooks.bash.post;
  exports io.github.cowwoc.cat.hooks.edit;
  exports io.github.cowwoc.cat.hooks.licensing;
  exports io.github.cowwoc.cat.hooks.prompt;
  exports io.github.cowwoc.cat.hooks.read.post;
  exports io.github.cowwoc.cat.hooks.read.pre;
  exports io.github.cowwoc.cat.hooks.session;
  exports io.github.cowwoc.cat.hooks.skills;
  exports io.github.cowwoc.cat.hooks.task;
  exports io.github.cowwoc.cat.hooks.tool.post;
  exports io.github.cowwoc.cat.hooks.util;
  exports io.github.cowwoc.cat.hooks.write;
}
