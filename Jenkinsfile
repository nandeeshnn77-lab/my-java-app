pipeline {

    agent any

    environment {

        // =====================================================
        // AWS / ECR
        // =====================================================

        AWS_REGION = 'ap-south-1'
        ECR_REPOSITORY = 'my-java-app'

        // =====================================================
        // DOCKER
        // =====================================================

        IMAGE_NAME = 'my-java-app'
        IMAGE_TAG = "${BUILD_NUMBER}"

        // =====================================================
        // KIND KUBERNETES
        // =====================================================

        KIND_CLUSTER = 'mycluster'
        KUBE_NAMESPACE = 'myapp'

        // =====================================================
        // SONARQUBE
        // =====================================================

        SONARQUBE_SERVER = 'SonarQube'
        SONAR_PROJECT_KEY = 'my-java-app'
    }


    stages {


        // =====================================================
        // 1. CHECKOUT
        // =====================================================

        stage('Checkout') {

            steps {

                echo '======================================'
                echo 'CHECKOUT FROM GITHUB'
                echo '======================================'

                checkout scm
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

                    echo "HOME:"
                    echo "$HOME"

                    echo "WORKSPACE:"
                    echo "$WORKSPACE"

                    echo "Current directory:"
                    pwd

                    echo "Files:"
                    ls -la

                    echo "Searching for pom.xml:"

                    find "$WORKSPACE" \
                        -name pom.xml \
                        -print

                    if [ ! -f "$WORKSPACE/pom.xml" ]; then
                        echo "ERROR: pom.xml not found"
                        exit 1
                    fi

                    echo "pom.xml found successfully"
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
                    echo "BUILD & TEST"
                    echo "======================================"

                    echo "Java version:"
                    java -version

                    echo "Maven version:"
                    mvn -version

                    echo "Running Maven build..."

                    mvn clean test package

                    echo "Build completed successfully"

                    echo "Target directory:"

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

                        echo "Running SonarQube..."

                        mvn sonar:sonar \
                            -Dsonar.projectKey="${SONAR_PROJECT_KEY}"

                        echo "SonarQube analysis completed"
                    '''
                }
            }
        }


        // =====================================================
        // 5. SONARQUBE QUALITY GATE
        // =====================================================

        stage('SonarQube Quality Gate') {

            steps {

                echo "======================================"
                echo "SONARQUBE QUALITY GATE"
                echo "======================================"

                timeout(time: 5, unit: 'MINUTES') {

                    waitForQualityGate abortPipeline: true
                }

                echo "Quality Gate PASSED"
            }
        }


        // =====================================================
        // 6. GET AWS ACCOUNT
        // =====================================================

        stage('Get AWS Account') {

            steps {

                script {

                    echo "======================================"
                    echo "GET AWS ACCOUNT"
                    echo "======================================"

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


                    echo "AWS Account:"
                    echo "${env.AWS_ACCOUNT_ID}"

                    echo "AWS Region:"
                    echo "${env.AWS_REGION}"

                    echo "ECR Registry:"
                    echo "${env.ECR_REGISTRY}"

                    echo "Image:"
                    echo "${env.IMAGE_URI}:${env.IMAGE_TAG}"
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

                    echo "Docker version:"

                    docker --version

                    echo "Building image:"

                    docker build \
                        -t "${IMAGE_URI}:${IMAGE_TAG}" .

                    echo "Docker image created successfully"

                    docker images | grep "${IMAGE_NAME}" || true
                '''
            }
        }


        // =====================================================
        // 9. TRIVY IMAGE SCAN
        // =====================================================

        stage('Trivy Scan') {

            steps {

                sh '''
                    set -e

                    echo "======================================"
                    echo "TRIVY SECURITY SCAN"
                    echo "======================================"

                    echo "Scanning image:"

                    echo "${IMAGE_URI}:${IMAGE_TAG}"

                    trivy image \
                        --exit-code 1 \
                        --severity HIGH,CRITICAL \
                        --ignore-unfixed \
                        "${IMAGE_URI}:${IMAGE_TAG}"

                    echo "Trivy scan PASSED"
                '''
            }
        }


        // =====================================================
        // 10. LOGIN TO ECR
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

                    echo "ECR login successful"
                '''
            }
        }


        // =====================================================
        // 11. PUSH IMAGE TO ECR
        // =====================================================

        stage('Push Image to ECR') {

            steps {

                sh '''
                    set -e

                    echo "======================================"
                    echo "PUSH IMAGE TO ECR"
                    echo "======================================"

                    echo "Pushing image:"

                    echo "${IMAGE_URI}:${IMAGE_TAG}"

                    docker push \
                        "${IMAGE_URI}:${IMAGE_TAG}"

                    echo "Image pushed successfully"
                '''
            }
        }


        // =====================================================
        // 12. VERIFY KIND CLUSTER
        // =====================================================

        stage('Verify Kind Cluster') {

            steps {

                sh '''
                    set -e

                    echo "======================================"
                    echo "VERIFY KIND CLUSTER"
                    echo "======================================"

                    echo "Available Kind clusters:"

                    kind get clusters

                    if ! kind get clusters | grep -q "^${KIND_CLUSTER}$"; then

                        echo "ERROR: Kind cluster '${KIND_CLUSTER}' not found"

                        echo "Create it first using:"
                        echo "kind create cluster --name ${KIND_CLUSTER}"

                        exit 1
                    fi

                    echo "Kind cluster exists"
                '''
            }
        }


        // =====================================================
        // 13. LOAD IMAGE INTO KIND
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

                    echo "Image loaded into Kind successfully"
                '''
            }
        }


        // =====================================================
        // 14. VERIFY KUBERNETES
        // =====================================================

        stage('Verify Kubernetes') {

            steps {

                sh '''
                    set -e

                    echo "======================================"
                    echo "VERIFY KUBERNETES"
                    echo "======================================"

                    kubectl version --client

                    echo "Kubernetes nodes:"

                    kubectl get nodes

                    echo "Kubernetes cluster information:"

                    kubectl cluster-info
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
                    echo "CREATE NAMESPACE"
                    echo "======================================"

                    kubectl apply \
                        -f k8s/namespace.yaml
                '''
            }
        }


        // =====================================================
        // 16. DEPLOY APPLICATION
        // =====================================================

        stage('Deploy Application') {

            steps {

                sh '''
                    set -e

                    echo "======================================"
                    echo "DEPLOY APPLICATION"
                    echo "======================================"

                    kubectl apply \
                        -f k8s/deployment.yaml
                '''
            }
        }


        // =====================================================
        // 17. CREATE SERVICE
        // =====================================================

        stage('Create Service') {

            steps {

                sh '''
                    set -e

                    echo "======================================"
                    echo "CREATE SERVICE"
                    echo "======================================"

                    kubectl apply \
                        -f k8s/service.yaml
                '''
            }
        }


        // =====================================================
        // 18. UPDATE IMAGE
        // =====================================================

        stage('Update Image') {

            steps {

                sh '''
                    set -e

                    echo "======================================"
                    echo "UPDATE KUBERNETES IMAGE"
                    echo "======================================"

                    kubectl -n "${KUBE_NAMESPACE}" \
                        set image deployment/myapp \
                        myapp="${IMAGE_URI}:${IMAGE_TAG}"

                    echo "Image updated to:"

                    kubectl -n "${KUBE_NAMESPACE}" \
                        get deployment myapp \
                        -o=jsonpath='{.spec.template.spec.containers[0].image}'

                    echo
                '''
            }
        }


        // =====================================================
        // 19. ROLLOUT STATUS
        // =====================================================

        stage('Rollout Status') {

            steps {

                sh '''
                    set -e

                    echo "======================================"
                    echo "ROLLOUT STATUS"
                    echo "======================================"

                    kubectl -n "${KUBE_NAMESPACE}" \
                        rollout status deployment/myapp \
                        --timeout=5m

                    echo "Rollout successful"
                '''
            }
        }


        // =====================================================
        // 20. VERIFY APPLICATION
        // =====================================================

        stage('Verify Application') {

            steps {

                sh '''
                    set -e

                    echo "======================================"
                    echo "VERIFY APPLICATION"
                    echo "======================================"

                    echo "Deployment:"
                    kubectl -n "${KUBE_NAMESPACE}" \
                        get deployment

                    echo
                    echo "Pods:"
                    kubectl -n "${KUBE_NAMESPACE}" \
                        get pods -o wide

                    echo
                    echo "Service:"
                    kubectl -n "${KUBE_NAMESPACE}" \
                        get service

                    echo
                    echo "Endpoints:"
                    kubectl -n "${KUBE_NAMESPACE}" \
                        get endpoints
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
            =========================================
            CI/CD PIPELINE SUCCESSFUL
            =========================================

            GitHub
               ↓
            Maven
               ↓
            SonarQube
               ↓
            Quality Gate
               ↓
            Docker
               ↓
            Trivy
               ↓
            ECR
               ↓
            Kind Kubernetes
               ↓
            NodePort
               ↓
            Browser

            =========================================
            '''
        }


        failure {

            echo '''
            =========================================
            CI/CD PIPELINE FAILED
            =========================================

            Check the failed stage above.

            =========================================
            '''
        }


        always {

            echo "Pipeline status: ${currentBuild.currentResult}"

            sh '''
                echo "Cleaning unused Docker images..."

                docker image prune -f || true
            '''
        }
    }
}
