#!/usr/bin/env bash
# Build + run the pipeline. Usage: ./run.sh [generate|clean|all] [--rows 500] [--seed 42] [--dirty path] [--out-dir dir]
set -euo pipefail
cd "$(dirname "$0")"
rm -rf build/classes && mkdir -p build/classes
javac -encoding UTF-8 -d build/classes $(find src/main/java -name '*.java')
if [ $# -eq 0 ]; then set -- all; fi
java -cp build/classes claims.Main "$@"
