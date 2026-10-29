# Optional SonarQube
SonarQube is resource-intensive. For this lab, install it only if VM2 has enough RAM.
A simple chart-based install can change between chart versions, so follow the current official SonarQube Helm chart instructions:
https://docs.sonarsource.com/sonarqube-server/latest/setup-and-upgrade/deploy-on-kubernetes/
Then configure a Jenkins SonarQube server and token. The starter Jenkinsfile does not yet enforce a SonarQube quality gate; add that after SonarQube is installed and configured.
