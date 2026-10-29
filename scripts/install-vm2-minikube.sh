#!/usr/bin/env bash
set -euo pipefail
sudo apt-get update
sudo apt-get install -y ca-certificates curl gnupg apt-transport-https conntrack socat docker.io openssh-server
sudo systemctl enable --now docker ssh
sudo usermod -aG docker "$USER"

# kubectl
curl -LO "https://dl.k8s.io/release/$(curl -L -s https://dl.k8s.io/release/stable.txt)/bin/linux/amd64/kubectl"
sudo install -o root -g root -m 0755 kubectl /usr/local/bin/kubectl
rm -f kubectl

# Minikube
curl -LO https://storage.googleapis.com/minikube/releases/latest/minikube-linux-amd64
sudo install minikube-linux-amd64 /usr/local/bin/minikube
rm -f minikube-linux-amd64

# Helm
curl -fsSL https://raw.githubusercontent.com/helm/helm/main/scripts/get-helm-3 | bash

echo
echo "Log out/in, then run:"
echo "minikube start --driver=docker --cpus=4 --memory=6144"
echo "kubectl get nodes"
