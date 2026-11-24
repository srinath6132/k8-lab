# Helm deployment failure

## Triage

```bash
helm -n shop list --all
helm -n shop history <release>
helm -n shop status <release> --show-desc
kubectl -n shop get pods,events --sort-by=.metadata.creationTimestamp
```

`pending-install`, `pending-upgrade`, or `pending-rollback` may mean a Helm
operation is still running. Check Jenkins and other terminals before retrying;
do not delete Helm release Secrets as a first response.

For a failed pod, use the [pod startup and probes runbook](pod-startup-and-probes.md).
For an image pull failure, verify the image repository/tag and optional image
pull Secret. For a missing required config, verify Secret names/keys without
printing Secret values.

## Rollback

```bash
helm -n shop history <release>
helm -n shop rollback <release> <previous-revision> --wait --timeout 5m
kubectl -n shop rollout status deployment/<deployment> --timeout=10m
```

The orchestrator uses atomic upgrades per release, not one transaction across
all four services. A later release may fail after earlier services upgraded.
Check every release after a coordinated deployment.
