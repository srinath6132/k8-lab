# Image pull failures

## Triage

```bash
kubectl -n shop get pods
kubectl -n shop describe pod <pod-name>
kubectl -n shop get events --sort-by=.metadata.creationTimestamp | tail -n 40
helm -n shop get values <release>
```

Check the exact image repository and tag in `describe pod`. `ErrImagePull` or
`ImagePullBackOff` usually means a misspelled/nonexistent tag, a private image
without an image pull Secret, or Docker Hub rate limiting.

## Recovery

- Confirm the image exists and is public, or create a `docker-registry` Secret
  in namespace `shop`.
- Set the chart's `imagePullSecretName` to that Secret name and redeploy.
- Do not put Docker Hub passwords/tokens in Helm values committed to Git.
- Check the pod events again; don't repeatedly restart the pod before fixing
  the image or authentication issue.
