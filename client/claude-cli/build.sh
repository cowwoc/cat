#!/usr/bin/env bash
# Copyright (c) 2026 Gili Tzabari. All rights reserved.
#
# Licensed under the CAT Commercial License.
# See LICENSE.md in the project root for license terms.
#
# build.sh - Build CAT Claude CLI using Maven
#
# Usage:
#   ./build.sh              Build the JAR
#   ./build.sh clean        Clean build artifacts
#   ./build.sh test         Run Maven tests
#
# Requirements:
#   - JDK 25 (JAVA_HOME set or java on PATH)
#   - Maven Wrapper included (../mvnw)

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
MVN="${SCRIPT_DIR}/../mvnw"

# Suppress Maven deprecation warnings from JDK 25
export MAVEN_OPTS="${MAVEN_OPTS:-} --enable-native-access=ALL-UNNAMED"

case "${1:-build}" in
    clean)
        "$MVN" -f "${SCRIPT_DIR}/pom.xml" clean -q
        echo "Clean complete."
        ;;
    build)
    echo "Building CAT Claude CLI JAR..."
    "$MVN" -f "${SCRIPT_DIR}/pom.xml" package -DskipTests -q
    echo "Done: ${SCRIPT_DIR}/target/cat-claude-cli-2.1.jar"
        ;;
    test)
        "$MVN" -f "${SCRIPT_DIR}/pom.xml" test
        ;;
    *)
        echo "Usage: $0 {build|clean|test}"
        exit 1
        ;;
esac
