# Prometheus monitoring

The four services expose Prometheus metrics at `/actuator/prometheus`. The `micrometer-registry-prometheus` dependency and actuator exposure are included in each service.

On the cluster, install the Prometheus Operator stack and then apply the ServiceMonitor:

```bash
helm repo add prometheus-community https://prometheus-community.github.io/helm-charts
helm repo update
helm upgrade --install monitoring prometheus-community/kube-prometheus-stack \
  --namespace monitoring --create-namespace
kubectl apply -f k8s/monitoring/shop-servicemonitor.yaml
```

The ServiceMonitor selects service labels for the four shop services and uses the conventional Helm release label `release: monitoring`. If the Prometheus release or ServiceMonitor selector differs, update that label to match the installed Prometheus resource. Ensure the `shop` network policy allows ingress from the `monitoring` namespace before applying it.

For Minikube port forwarding, see the monitoring section in the root README. This stack is optional and resource intensive.
