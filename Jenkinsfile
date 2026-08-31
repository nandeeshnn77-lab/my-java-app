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

        TF_IN_AUTOMATION = 'true'
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

                    echo "======================================"
                    echo "VERIFY REQUIRED TOOLS"
                    echo "======================================"

                    echo "User:"
                    whoami

                    echo "Hostname:"
                    hostname

                    echo "Java:"
                    java -version

                    echo "Maven:"
                    mvn -version

                    echo "Git:"
                    git --version

                    echo "Docker:"
                    docker --version
                    docker ps

                    echo "AWS CLI:"
                    aws --version

                    echo "AWS Identity:"
                    aws sts get-caller-identity

                    echo "Kubectl:"
                    kubectl version --client

                    echo "KIND:"
                    kind version
                    kind get clusters

                    echo "Kubernetes Context:"
                    kubectl config current-context

                    echo "Kubernetes Nodes:"
                    kubectl get nodes

                    echo "Helm:"
                    helm version
                '''
            }
        }


        stage('Build and Test') {
            steps {
                sh '''
                    set -e

                    echo "======================================"
                    echo "MAVEN BUILD AND TEST"
                    echo "======================================"

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

                    echo "AWS Account ID : ${env.AWS_ACCOUNT_ID}"
                    echo "ECR Registry   : ${env.ECR_REGISTRY}"
                    echo "Image          : ${env.IMAGE_URI}:${env.IMAGE_TAG}"
                }
            }
        }


        stage('Verify ECR Repository') {
            steps {
                sh '''
                    set -e

                    echo "======================================"
                    echo "VERIFY ECR REPOSITORY"
                    echo "======================================"

                    if aws ecr describe-repositories \
                        --repository-names "${ECR_REPOSITORY}" \
                        --region "${AWS_REGION}" >/dev/null 2>&1
                    then
                        echo "ECR repository exists."
                    else
                        echo "ECR repository does not exist."
                        echo "Creating repository..."

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

                    echo "======================================"
                    echo "DOCKER BUILD"
                    echo "======================================"

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

                    echo "======================================"
                    echo "LOGIN TO ECR"
                    echo "======================================"

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

                    echo "======================================"
                    echo "PUSH IMAGE TO ECR"
                    echo "======================================"

                    docker push "${IMAGE_URI}:${IMAGE_TAG}"

                    echo ""
                    echo "Image pushed successfully:"
                    echo "${IMAGE_URI}:${IMAGE_TAG}"
                '''
            }
        }


        stage('Check KIND Cluster') {
            steps {
                sh '''
                    set -e

                    echo "======================================"
                    echo "CHECK KIND CLUSTER"
                    echo "======================================"

                    kind get clusters

                    if ! kind get clusters | grep -q "^${KIND_CLUSTER}$"
                    then
                        echo "ERROR: KIND cluster ${KIND_CLUSTER} not found."
                        exit 1
                    fi

                    kubectl config current-context
                    kubectl get nodes
                '''
            }
        }


        stage('Create Namespace') {
            steps {
                sh '''
                    set -e

                    echo "======================================"
                    echo "CREATE NAMESPACE"
                    echo "======================================"

                    kubectl create namespace "${NAMESPACE}" \
                        --dry-run=client \
                        -o yaml \
                    | kubectl apply -f -

                    kubectl get namespace "${NAMESPACE}"
                '''
            }
        }


        stage('Create ECR Pull Secret') {
            steps {
                sh '''
                    set -e

                    echo "======================================"
                    echo "CREATE ECR IMAGE PULL SECRET"
                    echo "======================================"

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

                    kubectl get secret ecr-secret \
                        -n "${NAMESPACE}"
                '''
            }
        }


        stage('Helm Lint') {
            steps {
                sh '''
                    set -e

                    echo "======================================"
                    echo "HELM LINT"
                    echo "======================================"

                    helm lint "${HELM_CHART}"
                '''
            }
        }


        stage('Helm Template Validation') {
            steps {
                sh '''
                    set -e

                    echo "======================================"
                    echo "HELM TEMPLATE VALIDATION"
                    echo "======================================"

                    helm template \
                        "${HELM_RELEASE}" \
                        "${HELM_CHART}" \
                        --namespace "${NAMESPACE}" \
                        --set image.repository="${IMAGE_URI}" \
                        --set image.tag="${IMAGE_TAG}" \
                        --set imagePullSecrets[0].name=ecr-secret \
                        > /tmp/rendered-manifests.yaml

                    echo "Helm template generated successfully."
                '''
            }
        }


        stage('Helm Deploy') {
            steps {
                sh '''
                    set -e

                    echo "======================================"
                    echo "DEPLOY APPLICATION USING HELM"
                    echo "======================================"

                    helm upgrade --install \
                        "${HELM_RELEASE}" \
                        "${HELM_CHART}" \
                        --namespace "${NAMESPACE}" \
                        --create-namespace \
                        --set image.repository="${IMAGE_URI}" \
                        --set image.tag="${IMAGE_TAG}" \
                        --set imagePullSecrets[0].name=ecr-secret \
                        --wait \
                        --timeout 5m
                '''
            }
        }


        stage('Verify Helm Release') {
            steps {
                sh '''
                    set -e

                    echo "======================================"
                    echo "VERIFY HELM RELEASE"
                    echo "======================================"

                    helm list -n "${NAMESPACE}"

                    helm status \
                        "${HELM_RELEASE}" \
                        -n "${NAMESPACE}"
                '''
            }
        }


        stage('Verify Application') {
            steps {
                sh '''
                    set -e

                    echo "======================================"
                    echo "VERIFY APPLICATION"
                    echo "======================================"

                    echo "Deployments:"
                    kubectl get deployments \
                        -n "${NAMESPACE}"

                    echo ""
                    echo "Pods:"
                    kubectl get pods \
                        -n "${NAMESPACE}" \
                        -o wide

                    echo ""
                    echo "Services:"
                    kubectl get svc \
                        -n "${NAMESPACE}"

                    echo ""
                    echo "Waiting for pods..."

                    kubectl wait \
                        --for=condition=ready \
                        pod \
                        --all \
                        -n "${NAMESPACE}" \
                        --timeout=300s
                '''
            }
        }


        stage('Deployment Summary') {
            steps {
                sh '''
                    echo "======================================"
                    echo "DEPLOYMENT SUCCESSFUL"
                    echo "======================================"

                    echo "Image:"
                    echo "${IMAGE_URI}:${IMAGE_TAG}"

                    echo ""
                    echo "Namespace:"
                    echo "${NAMESPACE}"

                    echo ""
                    echo "Helm Release:"
                    echo "${HELM_RELEASE}"

                    echo ""
                    echo "Pods:"
                    kubectl get pods -n "${NAMESPACE}"

                    echo ""
                    echo "Services:"
                    kubectl get svc -n "${NAMESPACE}"
                '''
            }
        }
    }


    post {

        success {
            echo '======================================'
            echo 'PIPELINE SUCCESSFUL'
            echo '======================================'
        }

        failure {
            echo '======================================'
            echo 'PIPELINE FAILED'
            echo '======================================'

            sh '''
                echo "Current pods:"
                kubectl get pods -n "${NAMESPACE}" 2>/dev/null || true

                echo ""
                echo "Pod events:"
                kubectl get events \
                    -n "${NAMESPACE}" \
                    --sort-by=.lastTimestamp \
                    2>/dev/null || true
            '''
        }

        always {
            sh '''
                echo "======================================"
                echo "CLEANUP"
                echo "======================================"

                docker logout "${ECR_REGISTRY}" 2>/dev/null || true
            '''
        }
    }
}
