#!/usr/bin/env bash
set -euo pipefail

IMAGE_TAG="${1:-textreaderrpi:latest}"

docker build -t "${IMAGE_TAG}" .
echo "Built Docker image: ${IMAGE_TAG}"

