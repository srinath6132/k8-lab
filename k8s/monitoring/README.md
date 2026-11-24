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

Run on VM2 as the same Linux account that owns Minikube. Prefer the
repository installer, which installs the chart and then applies the
ServiceMonitor, alert rules, and Grafana dashboard:

```bash
MONITORING_VALUES_OVERRIDE="$HOME/.config/k8-lab/monitoring-values.yaml" \
  bash scripts/observability/install-laptop-monitoring.sh
```

For password generation and creation of the private override file, follow
the Grafana setup in `Commands.txt`. The override is merged on top of the
checked-in laptop values and stays outside Git. If you have not created an
override, omit `MONITORING_VALUES_OVERRIDE=...`; the checked-in password is
for private local practice only. Reuse the same override on upgrades so
Helm does not reset the Grafana password.

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
kubectl -n monitoring port-forward --address 127.0.0.1 svc/monitoring-grafana 3000:80
kubectl -n monitoring port-forward --address 127.0.0.1 svc/monitoring-kube-prometheus-prometheus 9090:9090
```

From the laptop, open one SSH tunnel to VM2 and keep it running:

```bash
ssh -N -L 3000:127.0.0.1:3000 -L 9090:127.0.0.1:9090 <vm2-user>@<vm2-host-only-ip>
```

Browse to `http://localhost:3000` (user `admin`, password from your local
override file, or the checked-in values for private practice only) and
`http://localhost:9090`. This keeps monitoring ports off the host-only
network. Do not use the example Grafana password on a shared network.

Remove the optional stack when done to free laptop resources:

```bash
helm uninstall monitoring -n monitoring
kubectl delete namespace monitoring
```

This lab setup records metrics and evaluates alerts but does not configure
external notification delivery, durable retention, or an on-call service.
