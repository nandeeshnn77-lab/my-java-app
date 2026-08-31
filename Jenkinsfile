// Jenkinsfile

pipeline {

    agent {
        label 'Devops-Worker'
    }

    environment {
        AWS_REGION     = 'ap-south-1'
        ECR_REPOSITORY = 'my-java-app'
        IMAGE_TAG      = "${BUILD_NUMBER}"

        KIND_CLUSTER   = 'devops-cluster'
        NAMESPACE      = 'myapp'

        HELM_RELEASE   = 'my-java-app'
        HELM_CHART     = './helm/my-java-app'
    }

    options {
        timestamps()
        disableConcurrentBuilds()
    }

    stages {

        stage('Checkout') {
            steps {
                checkout scm
            }
        }

        stage('Verify Tools') {
            steps {
                sh '''
                    set -e

                    echo "===== JAVA ====="
                    java -version

                    echo "===== MAVEN ====="
                    mvn -version

                    echo "===== GIT ====="
                    git --version

                    echo "===== DOCKER ====="
                    docker --version
                    docker ps

                    echo "===== AWS ====="
                    aws --version
                    aws sts get-caller-identity

                    echo "===== KIND ====="
                    kind version
                    kind get clusters

                    echo "===== KUBERNETES ====="
                    kubectl config current-context
                    kubectl get nodes

                    echo "===== HELM ====="
                    helm version
                '''
            }
        }

        stage('Build and Test') {
            steps {
                sh '''
                    set -e
                    mvn clean test package
                '''
            }
        }

        stage('Get AWS Account') {
            steps {
                script {

                    env.AWS_ACCOUNT_ID = sh(
                        script: '''
                            aws sts get-caller-identity \
                            --query Account \
                            --output text
                        ''',
                        returnStdout: true
                    ).trim()

                    env.ECR_REGISTRY =
                        "${env.AWS_ACCOUNT_ID}.dkr.ecr.${env.AWS_REGION}.amazonaws.com"

                    env.IMAGE_URI =
                        "${env.ECR_REGISTRY}/${env.ECR_REPOSITORY}"

                    echo "Image: ${env.IMAGE_URI}:${env.IMAGE_TAG}"
                }
            }
        }

        stage('Verify ECR Repository') {
            steps {
                sh '''
                    set -e

                    if aws ecr describe-repositories \
                        --repository-names "${ECR_REPOSITORY}" \
                        --region "${AWS_REGION}" >/dev/null 2>&1
                    then
                        echo "ECR repository exists."
                    else
                        echo "Creating ECR repository."

                        aws ecr create-repository \
                            --repository-name "${ECR_REPOSITORY}" \
                            --region "${AWS_REGION}"
                    fi
                '''
            }
        }

        stage('Docker Build') {
            steps {
                sh '''
                    set -e

                    docker build \
                        -t "${IMAGE_URI}:${IMAGE_TAG}" \
                        .
                '''
            }
        }

        stage('Login to ECR') {
            steps {
                sh '''
                    set -e

                    aws ecr get-login-password \
                        --region "${AWS_REGION}" \
                    | docker login \
                        --username AWS \
                        --password-stdin "${ECR_REGISTRY}"
                '''
            }
        }

        stage('Push Image to ECR') {
            steps {
                sh '''
                    set -e

                    docker push "${IMAGE_URI}:${IMAGE_TAG}"
                '''
            }
        }

        stage('Check KIND Cluster') {
            steps {
                sh '''
                    set -e

                    if ! kind get clusters | grep -q "^${KIND_CLUSTER}$"
                    then
                        echo "KIND cluster ${KIND_CLUSTER} not found"
                        exit 1
                    fi

                    kubectl get nodes
                '''
            }
        }

        stage('Create Namespace') {
            steps {
                sh '''
                    set -e

                    kubectl create namespace "${NAMESPACE}" \
                        --dry-run=client \
                        -o yaml \
                    | kubectl apply -f -
                '''
            }
        }

        stage('Create ECR Pull Secret') {
            steps {
                sh '''
                    set -e

                    ECR_PASSWORD=$(aws ecr get-login-password \
                        --region "${AWS_REGION}")

                    kubectl create secret docker-registry ecr-secret \
                        --namespace "${NAMESPACE}" \
                        --docker-server="${ECR_REGISTRY}" \
                        --docker-username=AWS \
                        --docker-password="${ECR_PASSWORD}" \
                        --dry-run=client \
                        -o yaml \
                    | kubectl apply -f -
                '''
            }
        }

        stage('Helm Lint') {
            steps {
                sh '''
                    set -e
                    helm lint "${HELM_CHART}"
                '''
            }
        }

        stage('Helm Template Validation') {
            steps {
                sh '''
                    set -e

                    helm template \
                        "${HELM_RELEASE}" \
                        "${HELM_CHART}" \
                        --namespace "${NAMESPACE}" \
                        --set image.repository="${IMAGE_URI}" \
                        --set image.tag="${IMAGE_TAG}" \
                        > /tmp/rendered-manifests.yaml
                '''
            }
        }

        stage('Helm Deploy') {
            steps {
                sh '''
                    set -e

                    helm upgrade --install \
                        "${HELM_RELEASE}" \
                        "${HELM_CHART}" \
                        --namespace "${NAMESPACE}" \
                        --create-namespace \
                        --set image.repository="${IMAGE_URI}" \
                        --set image.tag="${IMAGE_TAG}" \
                        --wait \
                        --timeout 5m
                '''
            }
        }

        stage('Verify Application') {
            steps {
                sh '''
                    set -e

                    kubectl get deployments -n "${NAMESPACE}"
                    kubectl get pods -n "${NAMESPACE}" -o wide
                    kubectl get svc -n "${NAMESPACE}"

                    kubectl wait \
                        --for=condition=ready \
                        pod \
                        --all \
                        -n "${NAMESPACE}" \
                        --timeout=300s
                '''
            }
        }
    }

    post {

        success {
            echo 'PIPELINE SUCCESSFUL'
        }

        failure {
            echo 'PIPELINE FAILED'

            sh '''
                kubectl get pods -n "${NAMESPACE}" 2>/dev/null || true
                kubectl get events \
                    -n "${NAMESPACE}" \
                    --sort-by=.lastTimestamp \
                    2>/dev/null || true
            '''
        }

        always {
            sh '''
                docker logout "${ECR_REGISTRY}" 2>/dev/null || true
            '''
        }
    }
}
