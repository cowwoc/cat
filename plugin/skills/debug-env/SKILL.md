---
description: Debug environment inspection
disable-model-invocation: true
user-invocable: true
---

!`cat /dev/stdin > /tmp/skill-stdin-dump.txt 2>&1; env | sort > /tmp/skill-env-dump.txt 2>&1; echo "Debug captured"`

