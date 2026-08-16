pipeline {

    agent {
        label 'Devops-Worker'
    }
    environment {

        // =====================================================
        // AWS
        // =====================================================

        AWS_REGION      = 'ap-south-1'
        ECR_REPOSITORY  = 'my-java-app'

        // =====================================================
        // SONARQUBE
        // =====================================================

        SONARQUBE_SERVER = 'SonarQube'
        SONAR_PROJECT_KEY = 'my-java-app'

        // =====================================================
        // DOCKER
        // =====================================================

        IMAGE_TAG = "${BUILD_NUMBER}"

        // =====================================================
        // KUBERNETES / KIND
        // =====================================================

        KIND_CLUSTER = 'devops-cluster'
        NAMESPACE    = 'myapp'

        // =====================================================
        // HELM
        // =====================================================

        HELM_RELEASE = 'my-java-app'
        HELM_CHART   = './helm/my-java-app'
    }


    stages {

        // =====================================================
        // 1. CHECKOUT FROM GITHUB
        // =====================================================

        stage('Checkout') {

            steps {

                echo '======================================'
                echo 'CHECKOUT FROM GITHUB'
                echo '======================================'

                checkout scm

                echo 'Git checkout completed successfully'
            }
        }


        // =====================================================
        // 2. VERIFY WORKSPACE
        // =====================================================

        stage('Verify Workspace') {

            steps {

                sh '''
                    set -e

                    echo "======================================"
                    echo "VERIFY WORKSPACE"
                    echo "======================================"

                    echo "User:"
                    whoami

                    echo "Current directory:"
                    pwd

                    echo "Java:"
                    java -version

                    echo "Maven:"
                    mvn -version

                    echo "Docker:"
                    docker --version

                    echo "Kubectl:"
                    kubectl version --client

                    echo "Kind:"
                    kind version

                    echo "Helm:"
                    helm version

                    echo "AWS CLI:"
                    aws --version

                    echo ""
                    echo "Repository files:"
                    ls -la

                    echo ""
                    echo "Checking required files..."

                    test -f pom.xml
                    test -f Dockerfile
                    test -f Jenkinsfile

                    echo "pom.xml found"
                    echo "Dockerfile found"
                    echo "Jenkinsfile found"

                    if [ ! -d helm/my-java-app ]; then
                        echo "ERROR: helm/my-java-app directory not found"
                        exit 1
                    fi

                    echo "Helm chart found"

                    echo ""
                    echo "Workspace verification successful"
                '''
            }
        }


        // =====================================================
        // 3. BUILD & TEST
        // =====================================================

        stage('Build & Test') {

            steps {

                sh '''
                    set -e

                    echo "======================================"
                    echo "MAVEN BUILD & TEST"
                    echo "======================================"

                    mvn clean test package

                    echo ""
                    echo "Build completed successfully"

                    echo ""
                    echo "Generated files:"
                    ls -lh target/
                '''
            }
        }


        // =====================================================
        // 4. SONARQUBE ANALYSIS
        // =====================================================

        stage('SonarQube Analysis') {

            steps {

                withSonarQubeEnv("${SONARQUBE_SERVER}") {

                    sh '''
                        set -e

                        echo "======================================"
                        echo "SONARQUBE ANALYSIS"
                        echo "======================================"

                        mvn sonar:sonar \
                            -Dsonar.projectKey="${SONAR_PROJECT_KEY}"

                        echo ""
                        echo "SonarQube analysis completed successfully"
                    '''
                }
            }
        }


        // =====================================================
        // 5. SONARQUBE QUALITY GATE
        // =====================================================

        stage('SonarQube Quality Gate') {

            steps {

                echo '======================================'
                echo 'WAITING FOR SONARQUBE QUALITY GATE'
                echo '======================================'

                timeout(time: 5, unit: 'MINUTES') {

                    waitForQualityGate abortPipeline: true
                }

                echo 'SonarQube Quality Gate PASSED'
            }
        }


        // =====================================================
        // 6. GET AWS ACCOUNT
        // =====================================================

        stage('Get AWS Account') {

            steps {

                script {

                    echo '======================================'
                    echo 'GET AWS ACCOUNT'
                    echo '======================================'

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

                    echo "AWS Account : ${env.AWS_ACCOUNT_ID}"
                    echo "AWS Region  : ${env.AWS_REGION}"
                    echo "ECR Registry: ${env.ECR_REGISTRY}"
                    echo "Image       : ${env.IMAGE_URI}:${env.IMAGE_TAG}"
                }
            }
        }


        // =====================================================
        // 7. VERIFY ECR REPOSITORY
        // =====================================================

        stage('Verify ECR Repository') {

            steps {

                sh '''
                    set -e

                    echo "======================================"
                    echo "VERIFY ECR REPOSITORY"
                    echo "======================================"

                    aws ecr describe-repositories \
                        --repository-names "${ECR_REPOSITORY}" \
                        --region "${AWS_REGION}"

                    echo ""
                    echo "ECR repository exists"
                '''
            }
        }


        // =====================================================
        // 8. DOCKER BUILD
        // =====================================================

        stage('Docker Build') {

            steps {

                sh '''
                    set -e

                    echo "======================================"
                    echo "DOCKER BUILD"
                    echo "======================================"

                    docker --version

                    docker build \
                        -t "${IMAGE_URI}:${IMAGE_TAG}" .

                    echo ""
                    echo "Docker image created successfully"

                    docker images | grep my-java-app || true
                '''
            }
        }


        // =====================================================
        // 9. LOGIN TO ECR
        // =====================================================

        stage('Login to ECR') {

            steps {

                sh '''
                    set -e

                    echo "======================================"
                    echo "LOGIN TO ECR"
                    echo "======================================"

                    aws ecr get-login-password \
                        --region "${AWS_REGION}" | \
                    docker login \
                        --username AWS \
                        --password-stdin "${ECR_REGISTRY}"

                    echo ""
                    echo "ECR login successful"
                '''
            }
        }


        // =====================================================
        // 10. PUSH IMAGE TO ECR
        // =====================================================

        stage('Push Image to ECR') {

            steps {

                sh '''
                    set -e

                    echo "======================================"
                    echo "PUSH IMAGE TO ECR"
                    echo "======================================"

                    docker push \
                        "${IMAGE_URI}:${IMAGE_TAG}"

                    echo ""
                    echo "Image pushed successfully"
                '''
            }
        }


        // =====================================================
        // 11. CHECK KIND CLUSTER
        // =====================================================

        stage('Check Kind Cluster') {

            steps {

                sh '''
                    set -e

                    echo "======================================"
                    echo "CHECK KIND CLUSTER"
                    echo "======================================"

                    kind version

                    echo ""
                    echo "Kind clusters:"
                    kind get clusters

                    echo ""
                    echo "Kubernetes nodes:"
                    kubectl get nodes

                    echo ""
                    echo "Current context:"
                    kubectl config current-context

                    if ! kind get clusters | grep -q "^${KIND_CLUSTER}$"; then
                        echo "ERROR: Kind cluster ${KIND_CLUSTER} not found"
                        exit 1
                    fi

                    echo ""
                    echo "Kind cluster verified successfully"
                '''
            }
        }


        // =====================================================
        // 12. LOAD IMAGE INTO KIND
        // =====================================================

        stage('Load Image into Kind') {

            steps {

                sh '''
                    set -e

                    echo "======================================"
                    echo "LOAD IMAGE INTO KIND"
                    echo "======================================"

                    kind load docker-image \
                        "${IMAGE_URI}:${IMAGE_TAG}" \
                        --name "${KIND_CLUSTER}"

                    echo ""
                    echo "Image loaded into Kind successfully"
                '''
            }
        }


        // =====================================================
        // 13. HELM VALIDATION
        // =====================================================

        stage('Helm Validation') {

            steps {

                sh '''
                    set -e

                    echo "======================================"
                    echo "HELM VALIDATION"
                    echo "======================================"

                    helm version

                    echo ""
                    echo "Checking Helm chart:"
                    helm lint "${HELM_CHART}"

                    echo ""
                    echo "Helm chart validation successful"
                '''
            }
        }


        // =====================================================
        // 14. HELM TEMPLATE VALIDATION
        // =====================================================

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
                        --namespace "${NAMESPACE}"

                    echo ""
                    echo "Helm template validation successful"
                '''
            }
        }


        // =====================================================
        // 15. CREATE NAMESPACE
        // =====================================================

        stage('Create Namespace') {

            steps {

                sh '''
                    set -e

                    echo "======================================"
                    echo "CREATE KUBERNETES NAMESPACE"
                    echo "======================================"

                    kubectl create namespace "${NAMESPACE}" \
                        --dry-run=client \
                        -o yaml | kubectl apply -f -

                    echo ""
                    echo "Namespace verified:"
                    kubectl get namespace "${NAMESPACE}"
                '''
            }
        }


        // =====================================================
        // 16. CREATE ECR PULL SECRET
        // =====================================================

        stage('Create ECR Pull Secret') {

            steps {

                sh '''
                    set -e

                    echo "======================================"
                    echo "CREATE ECR PULL SECRET"
                    echo "======================================"

                    ECR_PASSWORD=$(aws ecr get-login-password \
                        --region "${AWS_REGION}")

                    kubectl -n "${NAMESPACE}" create secret docker-registry ecr-secret \
                        --docker-server="${ECR_REGISTRY}" \
                        --docker-username=AWS \
                        --docker-password="${ECR_PASSWORD}" \
                        --dry-run=client \
                        -o yaml | kubectl apply -f -

                    echo ""
                    echo "ECR pull secret created/updated"
                '''
            }
        }


        // =====================================================
        // 17. HELM DEPLOY
        // =====================================================

        stage('Helm Deploy') {

            steps {

                sh '''
                    set -e

                    echo "======================================"
                    echo "HELM DEPLOY"
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

                    echo ""
                    echo "Helm deployment completed successfully"
                '''
            }
        }


        // =====================================================
        // 18. HELM RELEASE VERIFICATION
        // =====================================================

        stage('Helm Release Verification') {

            steps {

                sh '''
                    set -e

                    echo "======================================"
                    echo "HELM RELEASE VERIFICATION"
                    echo "======================================"

                    helm list \
                        --namespace "${NAMESPACE}"

                    echo ""
                    echo "Helm release status:"

                    helm status \
                        "${HELM_RELEASE}" \
                        --namespace "${NAMESPACE}"

                    echo ""
                    echo "Helm release verified successfully"
                '''
            }
        }


        // =====================================================
        // 19. VERIFY APPLICATION
        // =====================================================

        stage('Verify Application') {

            steps {

                sh '''
                    set -e

                    echo "======================================"
                    echo "VERIFY APPLICATION"
                    echo "======================================"

                    echo ""
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
                    kubectl get services \
                        -n "${NAMESPACE}"

                    echo ""
                    echo "Ingress:"
                    kubectl get ingress \
                        -n "${NAMESPACE}" || true

                    echo ""
                    echo "Application verification completed"
                '''
            }
        }


        // =====================================================
        // 20. POD HEALTH CHECK
        // =====================================================

        stage('Pod Health Check') {

            steps {

                sh '''
                    set -e

                    echo "======================================"
                    echo "POD HEALTH CHECK"
                    echo "======================================"

                    kubectl wait \
                        --for=condition=ready \
                        pod \
                        --all \
                        -n "${NAMESPACE}" \
                        --timeout=5m

                    echo ""
                    echo "Pod status:"
                    kubectl get pods \
                        -n "${NAMESPACE}"

                    echo ""
                    echo "All pods are READY"
                '''
            }
        }
    }


    // =========================================================
    // POST ACTIONS
    // =========================================================

    post {

        success {

            echo '''
======================================
       CI/CD PIPELINE SUCCESSFUL
======================================

GitHub
  ↓
Maven Build & Test
  ↓
SonarQube Analysis
  ↓
SonarQube Quality Gate
  ↓
Docker Build
  ↓
Amazon ECR
  ↓
Kind Kubernetes
  ↓
Helm
  ↓
Application

======================================
'''
        }

        failure {

            echo '''
======================================
        CI/CD PIPELINE FAILED
======================================

Check the failed stage above.

======================================
'''
        }

        always {

            echo "Pipeline status: ${currentBuild.currentResult}"
            echo "Build number: ${BUILD_NUMBER}"

            sh '''
                echo "Cleaning unused Docker images..."

                docker image prune -f || true
            '''
        }
    }
}
