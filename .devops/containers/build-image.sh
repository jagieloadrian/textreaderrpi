#!/usr/bin/env bash
# Builds the Docker image via the Ktor Gradle plugin (no Dockerfile needed).
# Loads the image directly into the local Docker daemon.
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "${SCRIPT_DIR}/../.." && pwd)"
IMAGE_TAG="${1:-textreaderrpi:latest}"

echo "Building Docker image via Ktor Gradle plugin..."
(cd "${PROJECT_ROOT}" && ./gradlew publishImageToLocalRegistry --no-daemon)

# Retag if a custom name/tag was requested
if [[ "${IMAGE_TAG}" != "textreaderrpi:latest" ]]; then
  docker tag textreaderrpi:latest "${IMAGE_TAG}"
  echo "Tagged as: ${IMAGE_TAG}"
fi

echo ""
echo "Done. Run with:"
echo "  docker run --rm -p 8080:8080 --env-file .env.local ${IMAGE_TAG}"
echo ""
echo "Or with Docker Compose:"
echo "  cd .devops/containers && docker compose up -d"
