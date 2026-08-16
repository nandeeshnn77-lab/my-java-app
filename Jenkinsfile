pipeline {

    agent { label 'Devops-Worker' }

    environment {
        AWS_REGION = 'ap-south-1'

        AWS_ACCOUNT_ID = '439536302486'

        ECR_REPO = 'my-java-app'

        ECR_REGISTRY = "${AWS_ACCOUNT_ID}.dkr.ecr.${AWS_REGION}.amazonaws.com"

        IMAGE_TAG = "${BUILD_NUMBER}"

        IMAGE_URI = "${ECR_REGISTRY}/${ECR_REPO}:${IMAGE_TAG}"

        NAMESPACE = 'myapp'

        RELEASE_NAME = 'my-java-app'

        HELM_CHART = './helm/my-java-app'

        KIND_CLUSTER = 'devops-cluster'
    }

    stages {

        stage('Checkout') {
            steps {
                echo '===== CHECKOUT ====='

                checkout scm

                sh '''
                    echo "Git commit:"
                    git rev-parse --short HEAD
                '''
            }
        }

        stage('Environment Check') {
            steps {
                echo '===== ENVIRONMENT ====='

                sh '''
                    java -version
                    mvn -version
                    docker --version
                    aws --version
                    kubectl version --client
                    helm version
                    kind version
                '''
            }
        }

        stage('Maven Build') {
            steps {
                echo '===== MAVEN BUILD ====='

                sh '''
                    mvn clean package -DskipTests
                '''
            }
        }

        stage('Docker Build') {
            steps {
                echo '===== DOCKER BUILD ====='

                sh '''
                    docker build \
                      -t ${ECR_REPO}:${IMAGE_TAG} \
                      .

                    docker tag \
                      ${ECR_REPO}:${IMAGE_TAG} \
                      ${IMAGE_URI}
                '''
            }
        }

        stage('ECR Login') {
            steps {
                echo '===== ECR LOGIN ====='

                sh '''
                    aws ecr get-login-password \
                      --region ${AWS_REGION} | \
                    docker login \
                      --username AWS \
                      --password-stdin ${ECR_REGISTRY}
                '''
            }
        }

        stage('Push Image to ECR') {
            steps {
                echo '===== PUSH IMAGE ====='

                sh '''
                    docker push ${IMAGE_URI}
                '''
            }
        }

        stage('Kubernetes Check') {
            steps {
                echo '===== KUBERNETES ====='

                sh '''
                    echo "Kind cluster:"
                    kind get clusters

                    echo "Current context:"
                    kubectl config current-context

                    echo "Nodes:"
                    kubectl get nodes

                    echo "Pods:"
                    kubectl get pods -A
                '''
            }
        }

        stage('Refresh ECR Secret') {
            steps {
                echo '===== REFRESH ECR SECRET ====='

                sh '''
                    kubectl create namespace ${NAMESPACE} \
                      --dry-run=client \
                      -o yaml | kubectl apply -f -

                    ECR_PASSWORD=$(aws ecr get-login-password \
                      --region ${AWS_REGION})

                    kubectl create secret docker-registry ecr-secret \
                      --docker-server=${ECR_REGISTRY} \
                      --docker-username=AWS \
                      --docker-password="${ECR_PASSWORD}" \
                      --namespace=${NAMESPACE} \
                      --dry-run=client \
                      -o yaml | kubectl apply -f -
                '''
            }
        }

        stage('Helm Lint') {
            steps {
                echo '===== HELM LINT ====='

                sh '''
                    helm lint ${HELM_CHART}
                '''
            }
        }

        stage('Helm Deploy') {
            steps {
                echo '===== HELM DEPLOY ====='

                sh '''
                    helm upgrade --install ${RELEASE_NAME} ${HELM_CHART} \
                      --namespace ${NAMESPACE} \
                      --create-namespace \
                      --set image.repository=${ECR_REGISTRY}/${ECR_REPO} \
                      --set image.tag=${IMAGE_TAG}
                '''
            }
        }

        stage('Rollout Status') {
            steps {
                echo '===== ROLLOUT ====='

                sh '''
                    kubectl rollout status \
                      deployment/myapp \
                      -n ${NAMESPACE} \
                      --timeout=180s
                '''
            }
        }

        stage('Verify Application') {
            steps {
                echo '===== VERIFY APPLICATION ====='

                sh '''
                    echo "===== DEPLOYMENT ====="
                    kubectl get deployment -n ${NAMESPACE}

                    echo "===== PODS ====="
                    kubectl get pods -n ${NAMESPACE} -o wide

                    echo "===== SERVICE ====="
                    kubectl get svc -n ${NAMESPACE}
                '''
            }
        }
    }

    post {

        success {
            echo '''
            ==========================================
              CI/CD PIPELINE SUCCESS
            ==========================================
            '''
        }

        failure {
            echo '''
            ==========================================
              CI/CD PIPELINE FAILED
            ==========================================
            '''
        }

        always {
            sh '''
                echo "===== FINAL POD STATUS ====="
                kubectl get pods -n ${NAMESPACE} 2>/dev/null || true

                echo "===== FINAL DEPLOYMENT STATUS ====="
                kubectl get deployment -n ${NAMESPACE} 2>/dev/null || true
            '''
        }
    }
}
