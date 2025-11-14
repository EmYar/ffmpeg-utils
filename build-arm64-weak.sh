#!/usr/bin/env bash
set -euo pipefail

# Path to the project root (directory where this script is located)
PROJECT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"
cd "$PROJECT_DIR"

# --- CPU governor handling ---

ORIG_GOVERNORS=()

restore_governors() {
  if [[ ${#ORIG_GOVERNORS[@]} -eq 0 ]]; then
    return
  fi

  echo "==> Restoring previous CPU governors"
  for entry in "${ORIG_GOVERNORS[@]}"; do
    IFS=: read -r gov_file gov_value <<< "$entry"
    if [[ -w "$gov_file" ]]; then
      echo "$gov_value" > "$gov_file" || \
        echo "Warning: failed to restore governor for $gov_file" >&2
    else
      echo "Warning: governor file not writable: $gov_file" >&2
    fi
  done
}

trap restore_governors EXIT

set_governors_to_performance() {
  local p gov_file current
  local found_any=false

  for p in /sys/devices/system/cpu/cpufreq/policy*; do
    gov_file="$p/scaling_governor"
    if [[ -f "$gov_file" && -r "$gov_file" && -w "$gov_file" ]]; then
      found_any=true
      current="$(<"$gov_file")"
      ORIG_GOVERNORS+=("$gov_file:$current")
      if [[ "$current" != "performance" ]]; then
        echo "Setting governor for $(basename "$p") from '$current' to 'performance'"
        echo "performance" > "$gov_file"
      fi
    fi
  done

  if [[ "$found_any" != true ]]; then
    echo "Warning: no writable scaling_governor files found; CPU governor will not be changed" >&2
  fi
}

echo "==> Step 0. Setting CPU governor to 'performance' (if possible)"
set_governors_to_performance

echo "==> Step 1. Building fat JAR via Gradle"

# Build shadowJar (fat JAR)
./gradlew --no-daemon --configure-on-demand shadowJar

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

docker run --rm \
  --memory=2560m --memory-swap=2560m \
  -v "$PWD:/work" \
  -w /work \
  ghcr.io/graalvm/native-image-community:latest \
  --no-fallback \
  -O3 \
  -march=native \
  --initialize-at-build-time=me.emyar.ffmpegutils,io.ktor,kotlin,kotlinx,org.slf4j,ch.qos.logback \
  -H:+ReportExceptionStackTraces \
  -R:MaxHeapSize=32m \
  -o "build/distributions/$BINARY_NAME" \
  -jar "$JAR_PATH"

echo "==> Done. Native binary built: ./build/distributions/$BINARY_NAME"
