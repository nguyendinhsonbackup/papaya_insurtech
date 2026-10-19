#!/usr/bin/env bash
# Compile main + test sources and run all tests. Exit code 0 = all passed.
set -euo pipefail
cd "$(dirname "$0")"
rm -rf build/test-classes && mkdir -p build/test-classes
javac -encoding UTF-8 -d build/test-classes $(find src/main/java src/test/java -name '*.java')
java -cp build/test-classes claims.TestRunner
