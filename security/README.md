# Security practices for the lab

The repo demonstrates several Kubernetes security basics:

- The `shop` namespace uses Pod Security Admission `baseline` enforcement and
  audits/warns against `restricted`; service pods run as non-root, drop Linux
  capabilities, use the runtime-default seccomp profile, and disallow
  privilege escalation.
- `k8s/network-policies/shop-network-policies.yaml` documents default-deny
  ingress/egress and the required service paths. Verify that the selected
  Minikube CNI enforces NetworkPolicies before treating them as a control.
- JWT and registry credentials are Kubernetes Secrets, not chart defaults or
  committed values. Use local demo credentials only for disposable practice.
- Jenkins uses credential IDs and strict SSH host-key checking. The VM2 lab
  account currently uses its Minikube context; this is convenient for a lab,
  not least-privilege production RBAC.

Before using a real cluster, move secrets to a managed secret store, create
scoped Kubernetes RBAC for deploys, rotate credentials, enable TLS, and test
network policies and admission controls. Do not expose Jenkins, the Docker
daemon, the Kubernetes API, Prometheus, or Grafana to the public internet.
