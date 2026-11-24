#!/usr/bin/env bash
set -euo pipefail

NAMESPACE="${1:-shop}"
TAIL_LINES="${TAIL_LINES:-60}"

if ! [[ "$TAIL_LINES" =~ ^[0-9]+$ ]]; then
  echo "TAIL_LINES must be a non-negative integer" >&2
  exit 2
fi

echo "== Cluster =="
kubectl cluster-info
kubectl get nodes -o wide

echo
echo "== Namespace resources: ${NAMESPACE} =="
kubectl -n "$NAMESPACE" get deployments,pods,services,ingress -o wide

echo
echo "== Recent namespace events =="
kubectl -n "$NAMESPACE" get events --sort-by=.metadata.creationTimestamp | tail -n "$TAIL_LINES"

echo
echo "== Current logs for non-ready/restarting shop pods =="
while IFS= read -r pod; do
  [[ -n "$pod" ]] || continue
  ready="$(kubectl -n "$NAMESPACE" get pod "$pod" -o jsonpath='{.status.containerStatuses[0].ready}' 2>/dev/null || true)"
  restarts="$(kubectl -n "$NAMESPACE" get pod "$pod" -o jsonpath='{.status.containerStatuses[0].restartCount}' 2>/dev/null || true)"
  if [[ "$ready" != "true" || "${restarts:-0}" -gt 0 ]]; then
    echo "--- ${pod} (ready=${ready:-unknown}, restarts=${restarts:-unknown}) ---"
    kubectl -n "$NAMESPACE" describe pod "$pod" | tail -n "$TAIL_LINES" || true
    kubectl -n "$NAMESPACE" logs "$pod" --all-containers=true --tail="$TAIL_LINES" || true
    if [[ "${restarts:-0}" -gt 0 ]]; then
      kubectl -n "$NAMESPACE" logs "$pod" --all-containers=true --previous --tail="$TAIL_LINES" || true
    fi
  fi
done < <(kubectl -n "$NAMESPACE" get pods -l app.kubernetes.io/instance=shop -o jsonpath='{range .items[*]}{.metadata.name}{"\n"}{end}')

echo
echo "== Helm releases =="
helm -n "$NAMESPACE" list --all
