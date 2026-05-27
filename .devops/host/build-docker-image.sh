#!/usr/bin/env bash
set -euo pipefail

IMAGE_TAG="${1:-textreaderrpi:latest}"

docker build -f .devops/containers/Dockerfile -t "${IMAGE_TAG}" .
echo "Built Docker image: ${IMAGE_TAG}"

