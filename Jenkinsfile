pipeline {

    agent any

    environment {

        // =====================================================
        // AWS
        // =====================================================

        AWS_REGION = 'ap-south-1'
        ECR_REPOSITORY = 'my-java-app'
        EKS_CLUSTER = 'my-eks-cluster'

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
        // 1. CHECKOUT
        // =====================================================

        stage('Checkout') {

            steps {

                echo '======================================'
                echo 'CHECKOUT'
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
                    find "$WORKSPACE" -name pom.xml -print

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

                    echo "Building application..."

                    mvn clean test package

                    echo "Build completed successfully"

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

                        echo "Checking pom.xml:"
                        ls -lh pom.xml

                        echo "Running SonarQube analysis..."

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

                echo "SonarQube Quality Gate passed"
            }
        }


        // =====================================================
        // 6. GET AWS ACCOUNT
        // =====================================================

        stage('Get AWS Account') {

            steps {

                script {

                    echo "======================================"
                    echo "AWS ACCOUNT"
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

                    echo "AWS Account ID: ${env.AWS_ACCOUNT_ID}"
                    echo "AWS Region: ${env.AWS_REGION}"
                    echo "ECR Registry: ${env.ECR_REGISTRY}"
                    echo "Image URI: ${env.IMAGE_URI}:${env.IMAGE_TAG}"
                }
            }
        }


        // =====================================================
        // 7. DOCKER BUILD
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

                    docker images | grep my-java-app || true
                '''
            }
        }


        // =====================================================
        // 8. LOGIN TO ECR
        // =====================================================

        stage('Login to ECR') {

            steps {

                sh '''
                    set -e

                    echo "======================================"
                    echo "LOGIN TO ECR"
                    echo "======================================"

                    echo "Logging into ECR..."

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
        // 9. PUSH IMAGE TO ECR
        // =====================================================

        stage('Push Image to ECR') {

            steps {

                sh '''
                    set -e

                    echo "======================================"
                    echo "PUSH IMAGE TO ECR"
                    echo "======================================"

                    echo "Pushing:"
                    echo "${IMAGE_URI}:${IMAGE_TAG}"

                    docker push "${IMAGE_URI}:${IMAGE_TAG}"

                    echo "Docker image pushed successfully"
                '''
            }
        }


        // =====================================================
        // 10. DEPLOY TO EKS
        // =====================================================

        stage('Deploy to EKS') {

            steps {

                sh '''
                    set -e

                    echo "======================================"
                    echo "DEPLOY TO EKS"
                    echo "======================================"

                    echo "AWS CLI:"
                    aws --version

                    echo "Kubectl:"
                    kubectl version --client

                    echo "Updating kubeconfig..."

                    aws eks update-kubeconfig \
                        --region "${AWS_REGION}" \
                        --name "${EKS_CLUSTER}"

                    echo "Kubeconfig updated successfully"


                    echo "======================================"
                    echo "CREATE NAMESPACE"
                    echo "======================================"

                    kubectl apply -f k8s/namespace.yaml


                    echo "======================================"
                    echo "DEPLOY APPLICATION"
                    echo "======================================"

                    kubectl apply -f k8s/deployment.yaml


                    echo "======================================"
                    echo "CREATE SERVICE"
                    echo "======================================"

                    kubectl apply -f k8s/service.yaml


                    echo "======================================"
                    echo "CREATE INGRESS"
                    echo "======================================"

                    kubectl apply -f k8s/ingress.yaml


                    echo "======================================"
                    echo "UPDATE IMAGE"
                    echo "======================================"

                    kubectl -n myapp set image deployment/myapp \
                        myapp="${IMAGE_URI}:${IMAGE_TAG}"


                    echo "======================================"
                    echo "ROLLOUT STATUS"
                    echo "======================================"

                    kubectl -n myapp rollout status deployment/myapp \
                        --timeout=5m


                    echo "======================================"
                    echo "DEPLOYMENT SUCCESSFUL"
                    echo "======================================"

                    kubectl -n myapp get deployment
                    kubectl -n myapp get pods
                    kubectl -n myapp get service
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
            '''
        }

        failure {

            echo '''
            ======================================
            CI/CD PIPELINE FAILED
            ======================================
            '''
        }

        always {

            echo "Pipeline completed with status: ${currentBuild.currentResult}"

            sh '''
                echo "Cleaning unused Docker images..."

                docker image prune -f || true
            '''
        }
    }
}
