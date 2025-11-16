#!/usr/bin/env bash
set -euo pipefail

# Path to the project root (directory where this script is located)
PROJECT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"
cd "$PROJECT_DIR"

echo "==> Step 1. Building fat JAR via Gradle"

# Build shadowJar (fat JAR)
./gradlew --no-daemon --configure-on-demand clean shadowJar

# Find the fat JAR (something like build/libs/name-version-all.jar)
JAR_PATH=$(find build/libs -maxdepth 1 -type f -name '*-all.jar' -print -quit 2>/dev/null || true)

if [[ -z "$JAR_PATH" || ! -f "$JAR_PATH" ]]; then
  echo "Error: could not find fat JAR in build/libs/*-all.jar" >&2
  exit 1
fi

echo "Found JAR: $JAR_PATH"

# Derive binary name from JAR file name (remove directory and '-all.jar' suffix)
JAR_FILE_NAME="$(basename "$JAR_PATH")"
BINARY_NAME="${JAR_FILE_NAME%-all.jar}"

echo "Binary name will be: $BINARY_NAME"

echo "==> Step 2. Building native image via Docker (GraalVM)"

# Ensure target directory exists
mkdir -p build/distributions

docker run --rm \
  --memory=3g --memory-swap=3g \
  -v "$PWD:/work" \
  -w /work \
  ghcr.io/graalvm/native-image-community:latest \
  --no-fallback \
  -O3 \
  -march=native \
  --initialize-at-build-time=me.emyar.ffmpegutils,kotlin,kotlinx \
  -H:+ReportExceptionStackTraces \
  --enable-http \
  -R:MaxHeapSize=32m \
  -Dfile.encoding=UTF-8 \
  -o "build/distributions/$BINARY_NAME" \
  -jar "$JAR_PATH"

echo "==> Done. Native binary built: ./build/distributions/$BINARY_NAME"
