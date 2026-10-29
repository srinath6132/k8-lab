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

## Important notes
- This is a learning lab, not a production HA architecture.
- Minikube is a single-node development cluster by default.
- Never expose Jenkins, Docker daemon, Kubernetes API, or dashboards directly to the public internet.
- If using RHEL instead of Ubuntu, the install scripts need to be adapted.
