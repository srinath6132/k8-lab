def configureJob() {
    properties([
        buildDiscarder(logRotator(numToKeepStr: '20')),
        disableConcurrentBuilds(),
        parameters([
            string(name: 'DOCKERHUB_IMAGE_BASE', defaultValue: 'srinathalgota/k8-lab',
                    description: 'Docker Hub prefix; a service suffix is appended'),
            string(name: 'VM2_HOST', defaultValue: '192.168.56.102', description: 'Minikube VM host-only address'),
            string(name: 'VM2_USER', defaultValue: 'deploy', description: 'SSH deployment user on VM2'),
            string(name: 'DEPLOY_BRANCH', defaultValue: 'main', description: 'Protected branch allowed to deploy'),
            string(name: 'IMAGE_PULL_SECRET_NAME', defaultValue: '', description: 'Optional Kubernetes Docker Hub pull secret'),
            booleanParam(name: 'DEPLOY_TO_MINIKUBE', defaultValue: false, description: 'Manually deploy this service after a successful build')
        ])
    ])
}

def executeSingleService(String serviceName) {
    def services = ['user-service', 'auth-service', 'product-service', 'cart-service']
    if (!services.contains(serviceName)) {
        error("Unsupported microservice: ${serviceName}")
    }

    configureJob()
    def imageBase = params.DOCKERHUB_IMAGE_BASE ?: 'srinathalgota/k8-lab'
    def vmHost = params.VM2_HOST ?: '192.168.56.102'
    def vmUser = params.VM2_USER ?: 'deploy'
    def deployBranch = params.DEPLOY_BRANCH ?: 'main'
    def pullSecret = params.IMAGE_PULL_SECRET_NAME ?: ''
    def deployEnabled = params.DEPLOY_TO_MINIKUBE == true
    def imageRef = null
    def chartPath = null

    if (!(imageBase ==~ /^[a-z0-9._-]+\/[a-z0-9._-]+$/)) {
        error('DOCKERHUB_IMAGE_BASE must be a Docker Hub namespace/repository, for example owner/k8-lab')
    }
    if (!(vmHost ==~ /^[A-Za-z0-9.-]+$/) || !(vmUser ==~ /^[A-Za-z_][A-Za-z0-9_.-]*$/)) {
        error('VM2_HOST or VM2_USER contains unsupported characters')
    }
    if (pullSecret && !(pullSecret ==~ /^[a-z0-9]([-a-z0-9]*[a-z0-9])?$/)) {
        error('IMAGE_PULL_SECRET_NAME must be a valid Kubernetes Secret name')
    }

    timestamps {
        try {
            stage('Unit tests') {
                withEnv(["SERVICE_NAME=${serviceName}"]) {
                    sh 'mvn -B -f services/pom.xml -pl "$SERVICE_NAME" -am clean verify'
                }
            }

            def sourceSha = sh(script: 'git rev-parse --short=12 HEAD', returnStdout: true).trim()
            def jobSlug = (env.JOB_NAME ?: 'jenkins').toLowerCase().replaceAll(/[^a-z0-9_.-]/, '-').take(20)
            def imageTag = "${sourceSha}-${jobSlug}-${env.BUILD_NUMBER ?: '0'}"
            imageRef = "${imageBase}-${serviceName}:${imageTag}"
            stage('Build and scan image') {
                withEnv(["SERVICE_NAME=${serviceName}", "IMAGE_REF=${imageRef}"]) {
                    sh 'bash scripts/ci/build-service-image.sh "$SERVICE_NAME" "$IMAGE_REF"'
                }
            }

            stage('Push image') {
                withCredentials([usernamePassword(
                    credentialsId: 'dockerhub-creds',
                    usernameVariable: 'DOCKERHUB_USER',
                    passwordVariable: 'DOCKERHUB_TOKEN'
                )]) {
                    withEnv(["IMAGE_REF=${imageRef}"]) {
                        sh '''
                            set +x
                            DOCKER_CONFIG="$(mktemp -d)"
                            export DOCKER_CONFIG
                            trap 'docker logout >/dev/null 2>&1 || true; rm -f -- "$DOCKER_CONFIG/config.json"; rmdir -- "$DOCKER_CONFIG" 2>/dev/null || true' EXIT
                            printf '%s' "$DOCKERHUB_TOKEN" | docker login -u "$DOCKERHUB_USER" --password-stdin
                            docker push "$IMAGE_REF"
                            docker image rm "$IMAGE_REF" >/dev/null
                        '''
                    }
                }
            }

            def branchName = env.BRANCH_NAME ?: ''
            if (deployEnabled && branchName == deployBranch) {
                def chartName = "amazon-microservices-${imageTag}.tgz"
                chartPath = ".pipeline-artifacts/${chartName}"
                sh "mkdir -p .pipeline-artifacts && tar -czf '${chartPath}' -C helm amazon-microservices"
                stage('Deploy service to Minikube') {
                    sshagent(credentials: ['vm2-ssh-key']) {
                        withEnv([
                            "DEPLOY_TARGET=${vmUser}@${vmHost}",
                            "SERVICE_NAME=${serviceName}",
                            "IMAGE_BASE=${imageBase}",
                            "IMAGE_TAG=${imageTag}",
                            "PULL_SECRET=${pullSecret}",
                            "CHART_NAME=${chartName}"
                        ]) {
                            sh '''
                                set -eu
                                remote_dir='.cache/k8-lab-deploy'
                                ssh -o BatchMode=yes -o StrictHostKeyChecking=yes "$DEPLOY_TARGET" "mkdir -p '$remote_dir'"
                                scp -o BatchMode=yes -o StrictHostKeyChecking=yes \
                                  ".pipeline-artifacts/$CHART_NAME" "$DEPLOY_TARGET:$remote_dir/$CHART_NAME"
                                scp -o BatchMode=yes -o StrictHostKeyChecking=yes \
                                  scripts/deploy-microservice.sh "$DEPLOY_TARGET:$remote_dir/deploy-microservice.sh"
                                ssh -o BatchMode=yes -o StrictHostKeyChecking=yes "$DEPLOY_TARGET" \
                                  "bash '$remote_dir/deploy-microservice.sh' '$SERVICE_NAME' '$IMAGE_BASE' '$IMAGE_TAG' '$remote_dir/$CHART_NAME' '$PULL_SECRET'"
                            '''
                        }
                    }
                }
            } else {
                echo "Skipping deployment: branch '${branchName}' is not the configured deploy branch '${deployBranch}', or deployment is disabled."
            }
        } finally {
            if (imageRef) {
                withEnv(["IMAGE_REF=${imageRef}"]) {
                    sh 'docker image rm "$IMAGE_REF" >/dev/null 2>&1 || true'
                }
            }
            if (chartPath) {
                withEnv(["CHART_PATH=${chartPath}"]) {
                    sh 'rm -f -- "$CHART_PATH"'
                }
            }
            sh 'docker image prune -f || true'
        }
    }
}

return this
