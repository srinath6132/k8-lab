#!/usr/bin/env bash
set -euo pipefail

NAMESPACE="${1:-shop}"
TIMEOUT="${2:-5m}"
deployments=(shop-user shop-auth shop-product shop-cart)

kubectl get namespace "$NAMESPACE" >/dev/null
for deployment in "${deployments[@]}"; do
  kubectl -n "$NAMESPACE" rollout status "deployment/${deployment}" --timeout="$TIMEOUT"
done

kubectl -n "$NAMESPACE" get deployments,pods,services,endpoints
