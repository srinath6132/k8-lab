# Optional Prometheus and Grafana

The four Spring services expose health/readiness endpoints and Prometheus
metrics at `/actuator/prometheus`. The `shop` ServiceMonitor selects the
service labels and targets that endpoint. The Grafana dashboard and alert
rules are maintained alongside the monitoring configuration:

- `observability/grafana/dashboards/shop-overview.json`
- `observability/prometheus/shop-alerts.yaml`
- `observability/prometheus/values-laptop.yaml`

## Laptop resource note

Monitoring is optional and resource-intensive. On a 16 GB laptop, stop VM1
while monitoring runs and keep approximately 2 GiB free in the VM2 guest for
the monitoring pods. Allocate at least 5-6 GiB to VM2 and avoid concurrent
Jenkins builds. The lab profile limits retention to 12 hours and
uses ephemeral Prometheus/Grafana storage; data is lost when the pods are
recreated. Do not install Loki/Elasticsearch or tracing backends on this
practice profile.

## Install

Run on VM2 as the same Linux account that owns Minikube:

```bash
helm repo add prometheus-community https://prometheus-community.github.io/helm-charts
helm repo update
helm upgrade --install monitoring prometheus-community/kube-prometheus-stack \
  --namespace monitoring --create-namespace \
  --values observability/prometheus/values-laptop.yaml \
  --wait --timeout 10m
kubectl apply -f k8s/monitoring/shop-servicemonitor.yaml
kubectl apply -f observability/prometheus/shop-alerts.yaml
kubectl create configmap shop-overview-dashboard \
  --namespace monitoring \
  --from-file=shop-overview.json=observability/grafana/dashboards/shop-overview.json \
  --dry-run=client -o yaml | kubectl apply -f -
kubectl label configmap shop-overview-dashboard \
  --namespace monitoring grafana_dashboard=1 --overwrite
```

If `kubectl top` is needed, enable Minikube metrics-server separately:

```bash
minikube addons enable metrics-server
kubectl top nodes
kubectl top pods -A
```

Check targets and alerts in Prometheus/Grafana. The ServiceMonitor uses the
conventional `release: monitoring` label. If the Helm release name changes,
update this label to match the Prometheus selector. The `shop` network policy
allows ingress to the service pods from the `monitoring` namespace.

## Access and cleanup

Keep port-forwards open in separate VM2 terminals (they listen on VM2 loopback):

```bash
kubectl -n monitoring port-forward svc/monitoring-grafana 3000:80
kubectl -n monitoring port-forward svc/monitoring-kube-prometheus-prometheus 9090:9090
```

From the laptop, create SSH tunnels to VM2 in another terminal:

```bash
ssh -L 3000:127.0.0.1:3000 <vm2-user>@<vm2-host-only-ip>
ssh -L 9090:127.0.0.1:9090 <vm2-user>@<vm2-host-only-ip>
```

Browse to `http://localhost:3000` (user `admin`, password configured in the
values file) and `http://localhost:9090`. This keeps monitoring ports off the
host-only network. Change the example Grafana password before installing.

Remove the optional stack when done to free laptop resources:

```bash
helm uninstall monitoring -n monitoring
kubectl delete namespace monitoring
```

This lab setup records metrics and evaluates alerts but does not configure
external notification delivery, durable retention, or an on-call service.
