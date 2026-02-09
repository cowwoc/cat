module io.github.cowwoc.cat.hooks
{
  requires tools.jackson.databind;
  requires io.github.cowwoc.requirements13.java;
  requires io.github.cowwoc.pouch10.core;
  requires jtokkit;

  exports io.github.cowwoc.cat.hooks;
  exports io.github.cowwoc.cat.hooks.bash;
  exports io.github.cowwoc.cat.hooks.bash.post;
  exports io.github.cowwoc.cat.hooks.prompt;
  exports io.github.cowwoc.cat.hooks.skills;
  exports io.github.cowwoc.cat.hooks.tool.post;
  exports io.github.cowwoc.cat.hooks.read.post;
  exports io.github.cowwoc.cat.hooks.read.pre;
}
