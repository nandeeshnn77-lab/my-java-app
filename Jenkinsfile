pipeline {

    agent any

    environment {

        // =====================================================
        // AWS
        // =====================================================

        AWS_REGION = 'ap-south-1'
        ECR_REPOSITORY = 'my-java-app'

        // =====================================================
        // SONARQUBE
        // =====================================================

        SONARQUBE_SERVER = 'SonarQube'
        SONAR_PROJECT_KEY = 'my-java-app'

        // =====================================================
        // DOCKER
        // =====================================================

        IMAGE_TAG = "${BUILD_NUMBER}"
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

                    echo "Java:"
                    java -version

                    echo "Maven:"
                    mvn -version

                    echo "Docker:"
                    docker --version

                    echo "Kubectl:"
                    kubectl version --client

                    echo "Current directory:"
                    pwd

                    echo "Files:"
                    ls -la

                    echo "Checking pom.xml..."

                    if [ ! -f pom.xml ]; then
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

                    mvn clean test package

                    echo "======================================"
                    echo "BUILD SUCCESSFUL"
                    echo "======================================"

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

                echo "SonarQube Quality Gate PASSED"
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

                    echo "AWS Account: ${env.AWS_ACCOUNT_ID}"
                    echo "AWS Region: ${env.AWS_REGION}"
                    echo "ECR Registry: ${env.ECR_REGISTRY}"
                    echo "Image: ${env.IMAGE_URI}:${env.IMAGE_TAG}"
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

                    docker --version

                    docker build \
                        -t "${IMAGE_URI}:${IMAGE_TAG}" .

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

                    echo "Kind clusters:"
                    kind get clusters

                    echo "Kubernetes nodes:"
                    kubectl get nodes

                    echo "Current context:"
                    kubectl config current-context
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
                        "${IMAGE_URI}:${IMAGE_TAG}"

                    echo "Image loaded into Kind successfully"
                '''
            }
        }


        // =====================================================
        // 13. CREATE NAMESPACE
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
        // 14. DEPLOY APPLICATION
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
        // 15. CREATE SERVICE
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
        // 16. UPDATE IMAGE
        // =====================================================

        stage('Update Image') {

            steps {

                sh '''
                    set -e

                    echo "======================================"
                    echo "UPDATE APPLICATION IMAGE"
                    echo "======================================"

                    kubectl -n myapp set image \
                        deployment/myapp \
                        myapp="${IMAGE_URI}:${IMAGE_TAG}"
                '''
            }
        }


        // =====================================================
        // 17. ROLLOUT
        // =====================================================

        stage('Rollout Status') {

            steps {

                sh '''
                    set -e

                    echo "======================================"
                    echo "ROLLOUT STATUS"
                    echo "======================================"

                    kubectl -n myapp rollout status \
                        deployment/myapp \
                        --timeout=5m
                '''
            }
        }


        // =====================================================
        // 18. VERIFY APPLICATION
        // =====================================================

        stage('Verify Application') {

            steps {

                sh '''
                    set -e

                    echo "======================================"
                    echo "VERIFY APPLICATION"
                    echo "======================================"

                    echo "Deployment:"
                    kubectl -n myapp get deployment

                    echo "Pods:"
                    kubectl -n myapp get pods -o wide

                    echo "Service:"
                    kubectl -n myapp get service

                    echo "Application details:"
                    kubectl -n myapp describe service myapp || true
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
            Maven
              ↓
            SonarQube
              ↓
            Docker
              ↓
            ECR
              ↓
            Kind Kubernetes
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

            sh '''
                echo "Cleaning unused Docker images..."

                docker image prune -f || true
            '''
        }
    }
}
