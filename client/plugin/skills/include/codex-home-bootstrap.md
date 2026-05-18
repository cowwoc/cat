CODEX_HOME_DIR="${CODEX_HOME:-${HOME}/.codex}"
if [ -d "${CODEX_HOME_DIR}/plugins/cache/cat/cat" ]; then
  CAT_PLUGIN_ROOT=$(find "${CODEX_HOME_DIR}/plugins/cache/cat/cat" -mindepth 1 -maxdepth 1 -type d | sort -V | tail -n 1)
fi
CAT_PLUGIN_DATA="${CODEX_HOME_DIR}/plugins/data/cat-cat"
