#!/usr/bin/env bash
set -euo pipefail

TARGET="${1:?Usage: deploy.sh <all|service-name> <image-base> <image-tag> <chart-bundle.tgz> [pull-secret]}"
shift
SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"

case "$TARGET" in
  all)
    exec bash "$SCRIPT_DIR/deploy-microservices.sh" "$@"
    ;;
  user-service|auth-service|product-service|cart-service)
    exec bash "$SCRIPT_DIR/deploy-microservice.sh" "$TARGET" "$@"
    ;;
  *)
    echo "Unsupported deployment target: $TARGET" >&2
    exit 2
    ;;
esac
