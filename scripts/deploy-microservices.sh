#!/usr/bin/env bash
set -euo pipefail

IMAGE_BASE="${1:?Usage: deploy-microservices.sh <image-base> <image-tag> <chart.tgz> [pull-secret]}"
IMAGE_TAG="${2:?Usage: deploy-microservices.sh <image-base> <image-tag> <chart.tgz> [pull-secret]}"
CHART_ARCHIVE="${3:?Usage: deploy-microservices.sh <image-base> <image-tag> <chart.tgz> [pull-secret]}"
IMAGE_PULL_SECRET_NAME="${4:-}"
NAMESPACE="${SHOP_NAMESPACE:-shop}"
RELEASE="${SHOP_RELEASE:-shop}"
JWT_SECRET_NAME="${JWT_SECRET_NAME:-${RELEASE}-jwt}"

if [[ ! "$IMAGE_BASE" =~ ^[a-z0-9._-]+/[a-z0-9._-]+$ ]]; then
  echo "Invalid Docker Hub image base: $IMAGE_BASE" >&2
  exit 2
fi
if [[ ! "$IMAGE_TAG" =~ ^[A-Za-z0-9_][A-Za-z0-9_.-]*$ ]]; then
  echo "Invalid image tag: $IMAGE_TAG" >&2
  exit 2
fi
if [[ "$CHART_ARCHIVE" != ".cache/k8-lab-deploy/amazon-microservices-${IMAGE_TAG}.tgz" ]]; then
  echo "Chart archive must be the pipeline package for this image tag" >&2
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
  --set-string "jwtSecretName=$JWT_SECRET_NAME"
)
if [[ -n "$IMAGE_PULL_SECRET_NAME" ]]; then
  HELM_ARGS+=(--set-string "imagePullSecretName=$IMAGE_PULL_SECRET_NAME")
fi
for service in user auth product cart; do
  HELM_ARGS+=(--set-string "services.${service}.image=${IMAGE_BASE}-${service}-service:${IMAGE_TAG}")
done

helm upgrade --install "$RELEASE" "$CHART_ARCHIVE" "${HELM_ARGS[@]}"
kubectl -n "$NAMESPACE" get pods,svc
