# Jenkins build failures and GitHub status reporting

## Maven test/build failure

Read the first Surefire/compiler failure, not only the final `BUILD FAILURE`:

```bash
WS=/var/lib/jenkins/workspace/<branch-job-workspace>
find "$WS/services" -path '*/target/surefire-reports/*.txt' -print -exec cat {} \;
```

For workspace permission or disk failures:

```bash
df -h "$WS"
df -i "$WS"
namei -l "$WS/services"
sudo -u jenkins test -w "$WS" && echo writable || echo not-writable
```

Do not delete or recursively change ownership of a workspace while a build is
running. Preserve uncommitted changes before wiping/recreating a workspace.

## GitHub commit-status HTTP 403

This is separate from Maven/build/deploy status. Grant the Jenkins GitHub
credential (or GitHub App) repository permission to write commit statuses,
then update the Jenkins credential. Do not copy tokens into pipeline files,
parameters, or logs. A missing status permission does not itself fail a
successful build unless the pipeline explicitly treats status reporting as
fatal.

## Deployment skipped or failed

- Check `BRANCH_NAME`, `DEPLOY_BRANCH`, and `DEPLOY_TO_MINIKUBE`.
- Check the VM2 host-only IP, SSH credential, verified `known_hosts`, and
  that the SSH account uses the intended Minikube profile.
- Follow `helm-deployment-failure.md` or
  `pod-startup-and-probes.md` for Kubernetes-side failures.
