#!/usr/bin/env bash
set -euo pipefail
sudo apt-get update
sudo apt-get install -y ca-certificates curl gnupg lsb-release git openjdk-17-jdk maven docker.io openssh-client unzip
sudo systemctl enable --now docker
sudo usermod -aG docker "$USER"

# Install Trivy from Aqua Security's official repository.
sudo install -d -m 0755 /usr/share/keyrings
curl -fsSL https://aquasecurity.github.io/trivy-repo/deb/public.key | sudo gpg --dearmor -o /usr/share/keyrings/trivy.gpg
echo "deb [signed-by=/usr/share/keyrings/trivy.gpg] https://aquasecurity.github.io/trivy-repo/deb $(. /etc/os-release && echo "${VERSION_CODENAME}") main" | sudo tee /etc/apt/sources.list.d/trivy.list >/dev/null
sudo apt-get update
sudo apt-get install -y trivy

echo
echo "Installed Java/Maven/Docker/Git/Trivy. Log out and back in for Docker group membership."
echo "Install Jenkins from the official Jenkins Ubuntu instructions:"
echo "https://www.jenkins.io/doc/book/installing/linux/#debianubuntu"
echo "After Jenkins install: sudo usermod -aG docker jenkins && sudo systemctl restart jenkins"
