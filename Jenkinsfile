pipeline {
  agent any
  options { timestamps(); disableConcurrentBuilds() }
  parameters {
    string(name: 'DOCKERHUB_REPO', defaultValue: 'your-dockerhub-username/devops-demo', description: 'Docker Hub repository')
    string(name: 'VM2_HOST', defaultValue: '192.168.56.102', description: 'VM2 host-only IP')
    string(name: 'VM2_USER', defaultValue: 'deploy', description: 'SSH deployment user on VM2')
  }
  environment {
    DOCKER_CREDENTIALS = 'dockerhub-creds'
    SSH_CREDENTIALS = 'vm2-ssh-key'
    IMAGE_TAG = "${BUILD_NUMBER}"
  }
  stages {
    stage('Checkout') { steps { checkout scm } }
    stage('Unit tests and package') {
      steps { dir('app') { sh 'mvn -B clean test package' } }
    }
    stage('Build image') {
      steps {
        withEnv(["IMAGE_REF=${params.DOCKERHUB_REPO}:${IMAGE_TAG}", "IMAGE_LATEST=${params.DOCKERHUB_REPO}:latest"]) {
          sh 'docker build -t "$IMAGE_REF" -t "$IMAGE_LATEST" app'
        }
      }
    }
    stage('Trivy image scan') {
      steps {
        withEnv(["IMAGE_REF=${params.DOCKERHUB_REPO}:${IMAGE_TAG}"]) {
          sh 'trivy image --exit-code 1 --severity HIGH,CRITICAL "$IMAGE_REF"'
        }
      }
    }
    stage('Push image') {
      steps {
        withEnv(["IMAGE_REF=${params.DOCKERHUB_REPO}:${IMAGE_TAG}", "IMAGE_LATEST=${params.DOCKERHUB_REPO}:latest"]) {
          withCredentials([usernamePassword(credentialsId: "${DOCKER_CREDENTIALS}", usernameVariable: 'DH_USER', passwordVariable: 'DH_TOKEN')]) {
            sh '''
              set +x
              echo "$DH_TOKEN" | docker login -u "$DH_USER" --password-stdin
              docker push "$IMAGE_REF"
              docker push "$IMAGE_LATEST"
              docker logout
            '''
          }
        }
      }
    }
    stage('Deploy to Minikube on VM2') {
      steps {
        withEnv(["TARGET=${params.VM2_USER}@${params.VM2_HOST}", "IMAGE_REF=${params.DOCKERHUB_REPO}:${IMAGE_TAG}"]) {
          sshagent(credentials: ["${SSH_CREDENTIALS}"]) {
            sh '''
              set -eu
              ssh -o StrictHostKeyChecking=yes "$TARGET" "/usr/local/bin/deploy-remote.sh '$IMAGE_REF'"
            '''
          }
        }
      }
    }
  }
  post { always { sh 'docker image prune -f || true' } }
}
