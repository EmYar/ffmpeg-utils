#!/usr/bin/env bash
set -euo pipefail

# Path to the project root (directory where this script is located)
PROJECT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"
cd "$PROJECT_DIR"

echo "==> Step 1. Building fat JAR via Gradle"

# Build shadowJar (fat JAR)
./gradlew --no-daemon shadowJar

# Find the fat JAR (something like build/libs/name-version-all.jar)
JAR_PATH=$(ls build/libs/*-all.jar 2>/dev/null | head -n 1 || true)

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

# Classes/packages to initialize at build time
INITIALIZE_AT_BUILD_TIME="me.emyar.ffmpegutils,io.ktor,kotlin,kotlinx,org.slf4j,ch.qos.logback"

# Limit container memory
DOCKER_MEMORY_OPTS=(--memory=2g --memory-swap=2g)

docker run --rm \
  "${DOCKER_MEMORY_OPTS[@]}" \
  -v "$PWD:/work" \
  -w /work \
  ghcr.io/graalvm/native-image-community:latest \
  --no-fallback \
  -Ob \
  -march=native \
  "--initialize-at-build-time=${INITIALIZE_AT_BUILD_TIME}" \
  -H:+ReportExceptionStackTraces \
  -R:MaxHeapSize=32m \
  -o "build/distributions/$BINARY_NAME" \
  -jar "$JAR_PATH"

echo "==> Done. Native binary built: ./build/distributions/$BINARY_NAME"
