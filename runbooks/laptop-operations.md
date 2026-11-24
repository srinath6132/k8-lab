# Laptop lab operating profile

This repository is for practicing corporate workflows on a dual-core i7,
16 GB laptop, not for production capacity testing.

- Use VM1 for Jenkins/builds and VM2 for Minikube.
- While both VMs run, set Jenkins executors to 1 and run deployments
  sequentially.
- Expect CPU contention during Maven and Docker builds; VirtualBox vCPUs do
  not add physical cores.
- Stop VM1 while operating optional Prometheus/Grafana monitoring.
- Keep each of the four services at one replica; do not enable HPA or
  additional replicas on this single-node lab.
- Avoid running Prometheus/Grafana and Jenkins builds concurrently.
- Skip distributed logging/tracing backends (Loki, Elasticsearch, Tempo) on
  this laptop; use Kubernetes logs and correlation IDs for practice.
- Retention in the optional monitoring profile is 12 hours and ephemeral.
- Product catalog, users, and carts are in-memory demo data; restarts reset it.

Check pressure before a rollout:

```bash
uptime
free -h
df -h
kubectl top node
kubectl top pods -A
```

If the node is saturated, pause builds and monitoring before increasing VM
memory or probe timeouts.
