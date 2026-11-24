# Pod startup and probe failures

## What it means

- `Startup probe failed` means the process has not answered the startup health
  endpoint yet. A few connection-refused events can be normal while Spring Boot
  initializes.
- Repeated restarts, `BackOff`, or exceeding the startup-probe budget means the
  container is crashing, starved for resources, or has a configuration error.
- Readiness failure removes the pod from Service endpoints. Liveness failure
  restarts the container.

## Triage

```bash
kubectl -n shop get pods -o wide
kubectl -n shop describe pod <pod-name>
kubectl -n shop logs <pod-name> --all-containers --tail=150
kubectl -n shop logs <pod-name> --all-containers --previous --tail=150
kubectl -n shop get events --sort-by=.metadata.creationTimestamp | tail -n 50
kubectl top node
kubectl top pods -n shop
```

Look for `APPLICATION FAILED TO START`, `OOMKilled`, `ImagePullBackOff`,
`CreateContainerConfigError`, CPU throttling, and whether the log reaches
`Started ... in ... seconds`.

## Recovery

1. If Spring takes longer than usual but eventually starts, confirm the
   deployment uses the Helm startup probe. Keep the startup budget appropriate
   to observed startup time; don't just increase liveness/readiness delays.
2. If startup crashes, fix the first application exception or missing
   Kubernetes Secret/config value. A probe cannot repair an application crash.
3. If the pod is resource-starved, stop VM1 while deploying, run one Jenkins
   executor, and reduce optional monitoring. Increase VM2's guest resources
   only if the laptop has enough memory headroom.
4. Inspect the Helm revision before rolling back:

   ```bash
   helm -n shop history shop-auth-service
   helm -n shop rollback shop-auth-service <previous-revision> --wait --timeout 5m
   ```

5. Verify the rollout and record the event/log output:

   ```bash
   kubectl -n shop rollout status deployment/shop-auth --timeout=10m
   kubectl -n shop get pods
   ```
