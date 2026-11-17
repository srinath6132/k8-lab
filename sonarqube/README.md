# Optional SonarQube

SonarQube is resource-intensive; install it only if VM2 has sufficient RAM. Follow the current official Kubernetes deployment instructions because chart names and values change between versions:
https://docs.sonarsource.com/sonarqube-server/latest/setup-and-upgrade/deploy-on-kubernetes/

After building and testing from the repository root, configure the scanner with `sonarqube/sonar-project.properties`:

```bash
mvn -B -f services/pom.xml clean verify
sonar-scanner -Dproject.settings=sonarqube/sonar-project.properties
```

Configure a Jenkins SonarQube server and token before adding scanner or quality-gate stages to the pipelines. The included Jenkins pipelines do not enforce a SonarQube quality gate by default.
