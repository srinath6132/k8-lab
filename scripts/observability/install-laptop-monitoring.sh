#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd -- "$SCRIPT_DIR/../.." && pwd)"
VALUES_FILE="$REPO_ROOT/observability/prometheus/values-laptop.yaml"

if [[ ! -f "$VALUES_FILE" ]]; then
  echo "Monitoring values file not found: $VALUES_FILE" >&2
  exit 1
fi

helm repo add prometheus-community https://prometheus-community.github.io/helm-charts --force-update
helm repo update
helm upgrade --install monitoring prometheus-community/kube-prometheus-stack \
  --namespace monitoring \
  --create-namespace \
  --values "$VALUES_FILE" \
  --wait \
  --timeout 10m

kubectl wait --for=condition=Established --timeout=120s \
  crd/servicemonitors.monitoring.coreos.com \
  crd/prometheusrules.monitoring.coreos.com
kubectl apply -f "$REPO_ROOT/k8s/monitoring/shop-servicemonitor.yaml"
kubectl apply -f "$REPO_ROOT/observability/prometheus/shop-alerts.yaml"
kubectl create configmap shop-overview-dashboard \
  --namespace monitoring \
  --from-file=shop-overview.json="$REPO_ROOT/observability/grafana/dashboards/shop-overview.json" \
  --dry-run=client -o yaml | kubectl apply -f -
kubectl label configmap shop-overview-dashboard \
  --namespace monitoring grafana_dashboard=1 --overwrite
kubectl -n monitoring get pods,svc
