# Jenkins pipeline conventions

The files under `jenkins/pipelines/` are the Jenkins Multibranch Pipeline script paths:

| Pipeline | Script path |
| --- | --- |
| User service | `jenkins/pipelines/Jenkinsfile.user-service` |
| Auth service | `jenkins/pipelines/Jenkinsfile.auth-service` |
| Product service | `jenkins/pipelines/Jenkinsfile.product-service` |
| Cart service | `jenkins/pipelines/Jenkinsfile.cart-service` |
| Coordinated release | `jenkins/pipelines/Jenkinsfile.orchestrator` |

The service entry points use `single-service.groovy`; the orchestrator uses `all-services.groovy`. Both preserve a common test, image-tagging, scanning, credential, and SSH deployment policy. The shell scripts in `scripts/` are the executable implementation shared by Jenkins and operators.

Required Jenkins credentials:

- `dockerhub-creds`: Docker Hub username and access token, stored as Username with password.
- `vm2-ssh-key`: private key credential for the VM2 account that owns the Minikube context.

Do not put credentials in pipeline parameters, source files, or build logs. Configure the verified VM2 host key in Jenkins and keep strict SSH host-key checking enabled.

Use the orchestrator for a coordinated release of all four charts. Its Helm upgrades are atomic per service, not a transaction across all services; a later service failure can leave earlier services on the new version. Review each rollout and re-run the orchestrator after correcting the failure.
