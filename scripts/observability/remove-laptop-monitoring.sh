#!/usr/bin/env bash
set -euo pipefail

helm uninstall monitoring --namespace monitoring
kubectl delete namespace monitoring
