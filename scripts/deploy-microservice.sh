#!/usr/bin/env bash
set -euo pipefail

SERVICE="${1:?Usage: deploy-microservice.sh <service-name> <image-base> <image-tag> <chart.tgz> [pull-secret]}"
IMAGE_BASE="${2:?Usage: deploy-microservice.sh <service-name> <image-base> <image-tag> <chart.tgz> [pull-secret]}"
IMAGE_TAG="${3:?Usage: deploy-microservice.sh <service-name> <image-base> <image-tag> <chart.tgz> [pull-secret]}"
CHART_ARCHIVE="${4:?Usage: deploy-microservice.sh <service-name> <image-base> <image-tag> <service-chart.tgz> [pull-secret]}"
IMAGE_PULL_SECRET_NAME="${5:-}"
NAMESPACE="${SHOP_NAMESPACE:-shop}"
JWT_SECRET_NAME="${JWT_SECRET_NAME:-shop-jwt}"

case "$SERVICE" in
  user-service) COMPONENT=user ;;
  auth-service) COMPONENT=auth ;;
  product-service) COMPONENT=product ;;
  cart-service) COMPONENT=cart ;;
  *) echo "Unsupported service: $SERVICE" >&2; exit 2 ;;
esac
if [[ ! "$IMAGE_BASE" =~ ^[a-z0-9._-]+/[a-z0-9._-]+$ ]]; then
  echo "Invalid Docker Hub image base: $IMAGE_BASE" >&2
  exit 2
fi
if [[ ! "$IMAGE_TAG" =~ ^[A-Za-z0-9_][A-Za-z0-9_.-]*$ ]]; then
  echo "Invalid image tag: $IMAGE_TAG" >&2
  exit 2
fi
if [[ "$CHART_ARCHIVE" != ".cache/k8-lab-deploy/${SERVICE}-${IMAGE_TAG}.tgz" ]]; then
  echo "Chart archive must be the service chart package for this image tag" >&2
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

if [[ "$SERVICE" != "product-service" ]] && ! kubectl -n "$NAMESPACE" get secret "$JWT_SECRET_NAME" >/dev/null 2>&1; then
  echo "Required JWT Secret '$JWT_SECRET_NAME' is missing in namespace '$NAMESPACE'" >&2
  exit 1
fi

HELM_ARGS=(
  --namespace "$NAMESPACE"
  --create-namespace
  --wait
  --atomic
  --timeout 5m
  --set-string "image.repository=${IMAGE_BASE}-${SERVICE}"
  --set-string "image.tag=${IMAGE_TAG}"
)
if [[ "$SERVICE" != "product-service" ]]; then
  HELM_ARGS+=(--set-string "jwtSecretName=$JWT_SECRET_NAME")
fi
if [[ -n "$IMAGE_PULL_SECRET_NAME" ]]; then
  HELM_ARGS+=(--set-string "imagePullSecretName=$IMAGE_PULL_SECRET_NAME")
fi

helm upgrade --install "shop-${SERVICE}" "$CHART_ARCHIVE" "${HELM_ARGS[@]}"
kubectl -n "$NAMESPACE" rollout status "deployment/shop-${COMPONENT}" --timeout=5m
kubectl -n "$NAMESPACE" get pods,svc
