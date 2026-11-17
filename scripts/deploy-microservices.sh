#!/usr/bin/env bash
set -euo pipefail

IMAGE_BASE="${1:?Usage: deploy-microservices.sh <image-base> <image-tag> <service-charts.tgz> [pull-secret]}"
IMAGE_TAG="${2:?Usage: deploy-microservices.sh <image-base> <image-tag> <service-charts.tgz> [pull-secret]}"
CHART_ARCHIVE="${3:?Usage: deploy-microservices.sh <image-base> <image-tag> <service-charts.tgz> [pull-secret]}"
IMAGE_PULL_SECRET_NAME="${4:-}"
NAMESPACE="${SHOP_NAMESPACE:-shop}"
JWT_SECRET_NAME="${JWT_SECRET_NAME:-shop-jwt}"

if [[ ! "$IMAGE_BASE" =~ ^[a-z0-9._-]+/[a-z0-9._-]+$ ]]; then
  echo "Invalid Docker Hub image base: $IMAGE_BASE" >&2
  exit 2
fi
if [[ ! "$IMAGE_TAG" =~ ^[A-Za-z0-9_][A-Za-z0-9_.-]*$ ]]; then
  echo "Invalid image tag: $IMAGE_TAG" >&2
  exit 2
fi
if [[ "$CHART_ARCHIVE" != ".cache/k8-lab-deploy/service-charts-${IMAGE_TAG}.tgz" ]]; then
  echo "Chart archive must be the service chart bundle for this image tag" >&2
  exit 2
fi
if [[ -n "$IMAGE_PULL_SECRET_NAME" && ! "$IMAGE_PULL_SECRET_NAME" =~ ^[a-z0-9]([-a-z0-9]*[a-z0-9])?$ ]]; then
  echo "Invalid Kubernetes image pull secret name" >&2
  exit 2
fi
if [[ ! -f "$CHART_ARCHIVE" ]]; then
  echo "Chart archive not found: $CHART_ARCHIVE" >&2
  exit 2
fi
trap 'rm -f -- "$CHART_ARCHIVE"' EXIT

exec 9>/tmp/k8-lab-microservices-deploy.lock
flock -w 300 9 || { echo "Timed out waiting for the Helm deployment lock" >&2; exit 1; }

kubectl get namespace "$NAMESPACE" >/dev/null 2>&1 || kubectl create namespace "$NAMESPACE"
if ! kubectl -n "$NAMESPACE" get secret "$JWT_SECRET_NAME" >/dev/null 2>&1; then
  echo "Required JWT Secret '$JWT_SECRET_NAME' is missing in namespace '$NAMESPACE'" >&2
  exit 1
fi

HELM_ARGS=(
  --namespace "$NAMESPACE"
  --create-namespace
  --wait
  --atomic
  --timeout 5m
)
if [[ -n "$IMAGE_PULL_SECRET_NAME" ]]; then
  PULL_SECRET_ARGS=(--set-string "imagePullSecretName=$IMAGE_PULL_SECRET_NAME")
else
  PULL_SECRET_ARGS=()
fi

mkdir -p "${HOME}/.cache/k8-lab-deploy"
chart_dir="$(mktemp -d "${HOME}/.cache/k8-lab-deploy/service-charts.XXXXXX")"
trap 'rm -f -- "$CHART_ARCHIVE"; rm -rf -- "$chart_dir"' EXIT
archive_entries="$(tar -tzf "$CHART_ARCHIVE")"
while IFS= read -r entry; do
  case "$entry" in
    auth-service|auth-service/*|cart-service|cart-service/*|product-service|product-service/*|user-service|user-service/*) ;;
    *) echo "Invalid chart bundle entry: $entry" >&2; exit 2 ;;
  esac
  if [[ "$entry" == /* || "$entry" == ".." || "$entry" == "../"* || "$entry" == *"/.." || "$entry" == *"/../"* ]]; then
    echo "Unsafe chart bundle entry: $entry" >&2
    exit 2
  fi
done <<< "$archive_entries"
tar -xzf "$CHART_ARCHIVE" -C "$chart_dir"

for service in user-service auth-service product-service cart-service; do
  if [[ ! -f "$chart_dir/$service/Chart.yaml" ]]; then
    echo "Missing Helm chart for $service in bundle" >&2
    exit 2
  fi
  if [[ "$service" == "product-service" ]]; then
    JWT_ARGS=()
  else
    JWT_ARGS=(--set-string "jwtSecretName=$JWT_SECRET_NAME")
  fi
  helm upgrade --install "shop-${service}" "$chart_dir/$service" \
    "${HELM_ARGS[@]}" \
    "${JWT_ARGS[@]}" \
    "${PULL_SECRET_ARGS[@]}" \
    --set-string "image.repository=${IMAGE_BASE}-${service}" \
    --set-string "image.tag=${IMAGE_TAG}"
done

kubectl -n "$NAMESPACE" get pods,svc
