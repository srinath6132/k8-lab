# VirtualBox DevOps Lab (Ubuntu + two VMs)

This is a practical starter lab for:
- VM1 (192.168.56.101): Jenkins, Java, Maven, Docker CLI/Engine, Trivy
- VM2 (192.168.56.102): Minikube/Kubernetes, Helm, application, Prometheus/Grafana
- Java Spring Boot app -> Maven tests -> Docker build -> Trivy scan -> Docker Hub push -> SSH-triggered Helm deployment

## Assumptions
- Both VMs run Ubuntu Server 22.04/24.04.
- VirtualBox Adapter 1 is NAT (internet access).
- Adapter 2 is Host-only Adapter, using a host-only network such as `vboxnet0` / `192.168.56.0/24`.
- Give VM1 at least 4 vCPU / 6 GB RAM and VM2 at least 4 vCPU / 8 GB RAM for the full lab. If your host has only 8 GB RAM, use one VM and run only selected services at a time.
- The IPs below are examples. Confirm they are unused and update them if your Host-only network differs.
- Docker Hub account/repository and credentials are yours to supply. Do not put passwords/tokens in source files.

## 1. VirtualBox networking
For each VM: Settings -> Network:
- Adapter 1: NAT (leave enabled for internet)
- Adapter 2: Host-only Adapter; select the same host-only network for both VMs.

In each VM, identify interface names:
```bash
ip -br addr
ip route
```
For a quick lab, configure static host-only addresses through NetworkManager/netplan appropriate to your Ubuntu version, or use DHCP if your Host-only network has a DHCP server. Confirm VM1 can reach VM2:
```bash
ping -c 3 192.168.56.102
```

If SSH is not already installed on VM2:
```bash
sudo apt update && sudo apt install -y openssh-server
sudo systemctl enable --now ssh
```
Allow SSH on the host-only interface/subnet if UFW is enabled:
```bash
sudo ufw allow from 192.168.56.0/24 to any port 22 proto tcp
```

## 2. Install VM1 (Jenkins/build server)
Copy `scripts/install-vm1-ubuntu.sh` to VM1 and run:
```bash
chmod +x install-vm1-ubuntu.sh
./install-vm1-ubuntu.sh
```
The script installs Java 17, Maven, Docker, Git, SSH client, and Trivy. It prints Jenkins installation guidance at the end; install Jenkins from the official Jenkins Ubuntu package repository following https://www.jenkins.io/doc/book/installing/linux/#debianubuntu.

After installing Jenkins:
```bash
sudo usermod -aG docker jenkins
sudo systemctl restart jenkins
```
Note: membership in the Docker group grants root-equivalent privileges. Only do this on a dedicated lab VM.

## 3. Install VM2 (Kubernetes host)
Copy `scripts/install-vm2-minikube.sh` to VM2 and run:
```bash
chmod +x install-vm2-minikube.sh
./install-vm2-minikube.sh
```
Log out/in if group membership changed. Then:
```bash
minikube start --driver=docker --cpus=4 --memory=6144
kubectl get nodes
```
Install monitoring:
```bash
helm repo add prometheus-community https://prometheus-community.github.io/helm-charts
helm repo update
helm upgrade --install monitoring prometheus-community/kube-prometheus-stack \
  --namespace monitoring --create-namespace
```
This chart can be resource-heavy. Skip it on low-memory hosts.

## 4. Install the sample app
The app source is in `app/`. Build and test locally:
```bash
cd app
mvn test package
```
The Dockerfile builds the packaged Spring Boot JAR. The Jenkins pipeline uses Docker Hub to publish the image.

## 5. Jenkins credentials and job
Create these Jenkins credentials:
1. `dockerhub-creds`: Username with password/token (Username with password credential type).
2. `vm2-ssh-key`: SSH private key credential for a Linux account on VM2 that can run the deployment script. Prefer a dedicated account and restrict sudo access.
3. `sonarqube-token` (optional): Secret text if you configure SonarQube separately.

Create a Jenkins Pipeline job pointing to this repository and set the following environment/job parameters:
- `DOCKERHUB_REPO`: e.g. `your-dockerhub-username/devops-demo`
- `VM2_HOST`: `192.168.56.102`
- `VM2_USER`: deployment user on VM2
- Jenkins credential IDs in `Jenkinsfile` must match the IDs above.

On VM2, copy `scripts/deploy-remote.sh` to `/usr/local/bin/deploy-remote.sh` and make executable:
```bash
sudo install -m 0755 scripts/deploy-remote.sh /usr/local/bin/deploy-remote.sh
```
The deployment user must have `helm` and `kubectl` in PATH and access to Minikube. Ensure its kubeconfig is readable and `minikube status` works as that user. The SSH user should be permitted to run this script. For a quick isolated lab only, you may grant passwordless sudo for this exact script; do not grant unrestricted sudo.

## 6. Deploy from Jenkins
The Jenkinsfile:
1. Checks out source
2. Runs Maven tests
3. Builds Docker image
4. Scans the image with Trivy (fails on HIGH/CRITICAL by default; tune as needed)
5. Pushes to Docker Hub
6. SSHs to VM2 and runs Helm upgrade with the published image

Configure SSH host key verification securely. For a private lab you can add VM2's verified host key to Jenkins' known_hosts. Avoid disabling host-key checking in shared/production environments.

## 7. Access the application and dashboards
On VM2:
```bash
kubectl get svc -A
kubectl get pods -A
kubectl port-forward --address 0.0.0.0 svc/devops-demo 8080:80
```
For access from Windows, configure VirtualBox NAT port forwarding on VM2:
- Host IP `127.0.0.1`, Host Port `8080`, Guest Port `8080`, TCP
Then open `http://127.0.0.1:8080/actuator/health` on Windows.
Keep the port-forward command running in a terminal. You can instead use NodePort plus the host-only IP, depending on your VirtualBox network setup.

Prometheus/Grafana port-forward examples:
```bash
kubectl -n monitoring port-forward --address 0.0.0.0 svc/monitoring-grafana 3000:80
kubectl -n monitoring port-forward --address 0.0.0.0 svc/monitoring-kube-prometheus-prometheus 9090:9090
```
Forward host ports 3000/9090 if needed. Get the Grafana admin password:
```bash
kubectl -n monitoring get secret monitoring-grafana -o jsonpath='{.data.admin-password}' | base64 -d; echo
```

## 8. Run the shopping microservices locally or on Minikube

The separate `services/` Maven build contains four Spring Boot services:
- **user-service** stores user profiles and password hashes.
- **auth-service** registers/logs in users and returns a one-hour signed bearer token.
- **product-service** searches the sample product catalog and returns product image URLs.
- **cart-service** keeps a per-user cart and gets product details from product-service.

The sample catalog, user accounts, and carts are in memory and reset when a service restarts. Keep the chart at one replica per service until a shared persistent store is added. Product images are external URLs. This is a local learning demo, not a production storefront.

Run the focused service tests from the repository root with:
```bash
mvn -B -f services/pom.xml test
```

### Docker Compose

Install Docker Compose v2, then from the repository root run:
```bash
docker compose up --build
```
The APIs are available on localhost: users `8081`, auth `8082`, products `8083`, and cart `8084`. To use a non-demo token key, export a value of at least 32 bytes as `JWT_SECRET` before starting Compose. Stop with `Ctrl+C`, then run `docker compose down`.

Try the API flow. The curl examples below require Bash or Git Bash; in PowerShell, use:
```powershell
Invoke-RestMethod -Uri 'http://localhost:8083/api/products?q=camera'
$registration = @{ name = 'Sam Shopper'; email = 'sam@example.com'; password = 'change-me-123' } |
  ConvertTo-Json
$session = Invoke-RestMethod -Uri 'http://localhost:8082/api/auth/register' `
  -Method Post -ContentType 'application/json' -Body $registration
$headers = @{ Authorization = "Bearer $($session.token)" }
Invoke-RestMethod -Uri 'http://localhost:8081/api/users/me' -Headers $headers
$item = @{ productId = 'camera-001'; quantity = 1 } | ConvertTo-Json
Invoke-RestMethod -Uri 'http://localhost:8084/api/cart/items' `
  -Method Post -Headers $headers -ContentType 'application/json' -Body $item
Invoke-RestMethod -Uri 'http://localhost:8084/api/cart' -Headers $headers
```

Or with Bash/Git Bash:
```bash
curl -s http://localhost:8083/api/products?q=camera
curl -s -X POST http://localhost:8082/api/auth/register \
  -H 'Content-Type: application/json' \
  -d '{"name":"Sam Shopper","email":"sam@example.com","password":"change-me-123"}'
```
Copy the returned `token` into a shell variable named `TOKEN`, then:
```bash
curl -s http://localhost:8081/api/users/me -H "Authorization: Bearer $TOKEN"
curl -s -X POST http://localhost:8084/api/cart/items \
  -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  -d '{"productId":"camera-001","quantity":1}'
curl -s http://localhost:8084/api/cart -H "Authorization: Bearer $TOKEN"
```
Login uses `POST /api/auth/login` with `{"email":"sam@example.com","password":"change-me-123"}`. Remove an item with `DELETE /api/cart/items/{productId}`, or clear the cart with `DELETE /api/cart`.

### Minikube / Helm

With Docker, Minikube, kubectl, and Helm installed, build each image into Minikube's image store from the repository root:
```bash
minikube start
docker build -f services/Dockerfile --build-arg SERVICE=user-service -t k8-lab/user-service:local .
docker build -f services/Dockerfile --build-arg SERVICE=auth-service -t k8-lab/auth-service:local .
docker build -f services/Dockerfile --build-arg SERVICE=product-service -t k8-lab/product-service:local .
docker build -f services/Dockerfile --build-arg SERVICE=cart-service -t k8-lab/cart-service:local .
minikube image load k8-lab/user-service:local
minikube image load k8-lab/auth-service:local
minikube image load k8-lab/product-service:local
minikube image load k8-lab/cart-service:local
helm upgrade --install shop helm/amazon-microservices --namespace shop --create-namespace \
  --set-string jwtSecret='replace-this-with-a-random-secret-at-least-32-bytes-long'
kubectl -n shop get pods,svc
```
Replace the example secret before using the chart outside a disposable local cluster. To publish all images to Docker Hub, log in with `docker login`, set `DOCKERHUB_USER`, then run:
```bash
DOCKERHUB_USER=your-dockerhub-user
for service in user auth product cart; do
  docker build -f services/Dockerfile --build-arg SERVICE="${service}-service" \
    -t "$DOCKERHUB_USER/k8-lab-${service}-service:1.0.0" .
  docker push "$DOCKERHUB_USER/k8-lab-${service}-service:1.0.0"
done
helm upgrade --install shop helm/amazon-microservices --namespace shop --create-namespace \
  --set-string jwtSecret='replace-this-with-a-random-secret-at-least-32-bytes-long' \
  --set services.user.image="$DOCKERHUB_USER/k8-lab-user-service:1.0.0" \
  --set services.auth.image="$DOCKERHUB_USER/k8-lab-auth-service:1.0.0" \
  --set services.product.image="$DOCKERHUB_USER/k8-lab-product-service:1.0.0" \
  --set services.cart.image="$DOCKERHUB_USER/k8-lab-cart-service:1.0.0"
```

For access from the host, keep these commands running in separate terminals:
```bash
kubectl -n shop port-forward svc/shop-user 8081:8080
kubectl -n shop port-forward svc/shop-auth 8082:8080
kubectl -n shop port-forward svc/shop-product 8083:8080
kubectl -n shop port-forward svc/shop-cart 8084:8080
```
Use the same API examples as above. The existing `app/` demo and `helm/devops-demo` chart remain available.

## 9. Jenkins CI/CD for individual services and full releases

The existing root `Jenkinsfile` continues to build and deploy only the legacy `app/` demo. The microservices use separate pipelines so service teams can release one service without rebuilding the others, while the full-release pipeline verifies and deploys all four together.

Create five Jenkins Multibranch Pipeline jobs for this repository. Install/configure the GitHub Branch Source integration, enable push-event indexing with a GitHub webhook, and set the branch source to discover the branches you want to build. Each job can use its own Script Path:

| Job | Script Path | Behavior |
| --- | --- | --- |
| User service | `Jenkinsfile.user-service` | Test, build, scan, and publish only user-service; optionally deploy it manually |
| Auth service | `Jenkinsfile.auth-service` | Same flow for auth-service |
| Product service | `Jenkinsfile.product-service` | Same flow for product-service |
| Cart service | `Jenkinsfile.cart-service` | Same flow for cart-service |
| Full release | `Jenkinsfile.microservices` | Test all modules, build/scan/push all four images, and roll out the complete chart |

These pipelines use the existing `dockerhub-creds` and `vm2-ssh-key` credentials. VM1 needs Java 17, Maven, Docker, Trivy, and the Jenkins SSH Agent/Credentials Binding plugins. Configure VM2's verified SSH host key in Jenkins `known_hosts`; do not disable host-key checking. The job parameters default to `DOCKERHUB_IMAGE_BASE=srinathalgota/k8-lab`, `VM2_HOST=192.168.56.102`, `VM2_USER=deploy`, and `DEPLOY_BRANCH=main`. Change `DEPLOY_BRANCH` to `master` if that is the protected release branch in your Jenkins job. Feature branches run build/test/scan/push but do not deploy. The **Full release** job auto-deploys on the protected branch; an individual service job only deploys when you manually enable its `DEPLOY_TO_MINIKUBE` parameter, preventing four independent jobs from racing to publish a mixed release.

### Prepare VM2 once

Run these commands as the VM2 account Jenkins will SSH into. That same account must be able to run `minikube status`, `kubectl`, and `helm` without sudo:
```bash
minikube status
helm version --short
kubectl create namespace shop --dry-run=client -o yaml | kubectl apply -f -
kubectl -n shop create secret generic shop-jwt \
  --from-literal=jwt-secret="$(openssl rand -hex 32)" \
  --dry-run=client -o yaml | kubectl apply -f -
```
Keep the JWT secret in the Kubernetes Secret; do not commit it or put it in the Jenkinsfile. If the Docker Hub repositories are private, create a Kubernetes Docker registry pull secret in `shop` and set the Jenkins parameter `IMAGE_PULL_SECRET_NAME` to that Secret's name.

The pipelines package the Helm chart from the exact checked-out commit and copy it, with the appropriate deploy script, to VM2 over SSH. They serialize Helm changes with a VM2 lock, use unique image tags containing the 12-character Git SHA/job/build number, and use Helm `--wait --atomic` for rollback on a failed rollout. Run the **Full release** job once to bootstrap the `shop` release; individual service jobs require that release to exist and then update only their own image. Jenkins never needs VM2's kubeconfig.

Once deployed, keep one command per terminal on VM2 to access the APIs from the host-only network (replace the IP if VM2 uses a different host-only address):
```bash
kubectl -n shop port-forward --address 192.168.56.102 svc/shop-user 8081:8080
kubectl -n shop port-forward --address 192.168.56.102 svc/shop-auth 8082:8080
kubectl -n shop port-forward --address 192.168.56.102 svc/shop-product 8083:8080
kubectl -n shop port-forward --address 192.168.56.102 svc/shop-cart 8084:8080
```
This is a corporate-style learning workflow, not production HA: Minikube is single-node and the demo services keep application data in memory. Use persistent databases, TLS/Ingress, managed secrets, backups, and a production Kubernetes cluster before handling real users or orders.

## Important notes
- This is a learning lab, not a production HA architecture.
- Minikube is a single-node development cluster by default.
- Never expose Jenkins, Docker daemon, Kubernetes API, or dashboards directly to the public internet.
- If using RHEL instead of Ubuntu, the install scripts need to be adapted.
