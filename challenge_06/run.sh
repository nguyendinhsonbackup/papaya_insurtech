#!/usr/bin/env bash
# Build + run the pipeline. Usage: ./run.sh [calculate] [--policy file] [--expenses file] [--out-dir dir]
set -euo pipefail
cd "$(dirname "$0")"
rm -rf build/classes && mkdir -p build/classes
javac -encoding UTF-8 -d build/classes $(find src/main/java -name '*.java')
if [ $# -eq 0 ]; then set -- calculate; fi
java -cp build/classes benefits.Main "$@"
