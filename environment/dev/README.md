# Laptop development environment

This is the supported practice profile for the current dual-core i7 /
16 GB / 256 GB laptop:

- VM1: Jenkins and image build/scan, 2 vCPU and about 3-4 GiB RAM; set Jenkins
  executors to 1.
- VM2: Minikube, Helm, four single-replica services, and Ingress, 2 vCPU and
  about 5-6 GiB RAM.
- Leave at least 6 GiB for Windows and the VM host. VirtualBox CPU settings
  are scheduling shares, not extra physical cores; expect builds to be slow.
- Keep optional Prometheus/Grafana off during image builds. Stop VM1 and give
  VM2 at least 6 GiB before starting monitoring.
- Store no user or cart data you need to keep: the demo services are in-memory.
- Use `k8s/resource-management/shop-quota.yaml` and the laptop monitoring
  values as development guardrails, not production capacity settings.

Prepare the namespace and quota on VM2:

```bash
kubectl apply -f k8s/namespaces/shop.yaml
kubectl apply -f k8s/resource-management/shop-quota.yaml
```

See the root README and `Commands.txt` for VM networking and Jenkins setup.
