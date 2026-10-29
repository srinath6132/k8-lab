#!/usr/bin/env bash
set -euo pipefail
# Called on VM2 by Jenkins over SSH:
# deploy-remote.sh <image:tag>
IMAGE="${1:?Usage: deploy-remote.sh <image:tag>}"
NAMESPACE="${NAMESPACE:-demo}"
RELEASE="${RELEASE:-devops-demo}"
CHART_DIR="${CHART_DIR:-$HOME/virtualbox-devops-lab/helm/devops-demo}"

helm upgrade --install "$RELEASE" "$CHART_DIR" \
  --namespace "$NAMESPACE" --create-namespace \
  --set image.repository="${IMAGE%:*}" \
  --set image.tag="${IMAGE##*:}" \
  --wait --timeout 180s
kubectl -n "$NAMESPACE" rollout status deployment/"$RELEASE" --timeout=180s
kubectl -n "$NAMESPACE" get pods,svc
