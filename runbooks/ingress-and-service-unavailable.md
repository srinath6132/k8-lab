# Ingress or service unavailable

## Triage

```bash
kubectl -n shop get ingress,svc,endpoints,pods -o wide
kubectl -n ingress-nginx get pods,svc
kubectl -n shop describe ingress shop
kubectl -n shop get events --sort-by=.metadata.creationTimestamp | tail -n 40
```

Confirm the service has ready endpoints, the Ingress backend service names
and ports exist, and the Minikube ingress addon/controller is running.

From the laptop lab, confirm VM2 is reachable and the VM2 port-forward remains
running. Use `curl -v http://shop.local:8080/` to distinguish a connection
problem from an application HTTP response.

## Recovery

Re-apply only the checked-in Ingress after confirming the controller exists:

```bash
kubectl apply -f k8s/ingress/shop-ingress.yaml
kubectl -n shop get ingress
```

Do not expose ClusterIP application services directly to the host as a
substitute for fixing an incorrect Ingress/backend.
