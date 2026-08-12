pipeline {

    agent any


    // =========================================================
    // ENVIRONMENT
    // =========================================================

    environment {

        // -----------------------------------------------------
        // AWS
        // -----------------------------------------------------

        AWS_REGION = 'ap-south-1'

        ECR_REPOSITORY = 'my-java-app'

        EKS_CLUSTER = 'my-eks-cluster'


        // -----------------------------------------------------
        // Kubernetes
        // -----------------------------------------------------

        K8S_NAMESPACE = 'myapp'

        K8S_DEPLOYMENT = 'myapp'

        K8S_CONTAINER = 'myapp'


        // -----------------------------------------------------
        // SonarQube
        // -----------------------------------------------------

        SONARQUBE_SERVER = 'SonarQube'

        SONAR_PROJECT_KEY = 'my-java-app'


        // -----------------------------------------------------
        // Docker
        // -----------------------------------------------------

        IMAGE_TAG = "${BUILD_NUMBER}"

    }


    // =========================================================
    // STAGES
    // =========================================================

    stages {


        // =====================================================
        // 1. CHECKOUT
        // =====================================================

        stage('Checkout') {

            steps {

                echo '======================================'
                echo 'CHECKOUT SOURCE CODE'
                echo '======================================'

                checkout scm
            }
        }


        // =====================================================
        // 2. VERIFY JENKINS WORKSPACE
        // =====================================================

        stage('Verify Workspace') {

            steps {

                dir("${WORKSPACE}") {

                    sh '''
                        set -e

                        echo "======================================"
                        echo "VERIFY JENKINS WORKSPACE"
                        echo "======================================"

                        echo "User:"
                        whoami

                        echo ""
                        echo "HOME:"
                        echo "$HOME"

                        echo ""
                        echo "WORKSPACE:"
                        echo "$WORKSPACE"

                        echo ""
                        echo "Current directory:"
                        pwd

                        echo ""
                        echo "Workspace files:"
                        ls -la

                        echo ""
                        echo "Searching for pom.xml:"
                        find "$WORKSPACE" -name pom.xml -print

                        echo ""
                        echo "Checking pom.xml..."

                        if [ ! -f "$WORKSPACE/pom.xml" ]; then
                            echo "ERROR: pom.xml NOT FOUND"
                            exit 1
                        fi

                        echo "SUCCESS: pom.xml found"

                        echo ""
                        echo "POM location:"
                        ls -lh "$WORKSPACE/pom.xml"
                    '''
                }
            }
        }


        // =====================================================
        // 3. JAVA / MAVEN CHECK
        // =====================================================

        stage('Check Java and Maven') {

            steps {

                dir("${WORKSPACE}") {

                    sh '''
                        set -e

                        echo "======================================"
                        echo "JAVA AND MAVEN"
                        echo "======================================"

                        echo ""
                        echo "Java version:"
                        java -version

                        echo ""
                        echo "Maven version:"
                        mvn -version

                        echo ""
                        echo "Maven HOME:"
                        echo "$M2_HOME"

                        echo ""
                        echo "HOME:"
                        echo "$HOME"

                        echo ""
                        echo "Workspace:"
                        pwd
                    '''
                }
            }
        }


        // =====================================================
        // 4. BUILD & TEST
        // =====================================================

        stage('Build & Test') {

            steps {

                dir("${WORKSPACE}") {

                    sh '''
                        set -e

                        echo "======================================"
                        echo "BUILD AND TEST"
                        echo "======================================"

                        echo "Current directory:"
                        pwd

                        echo ""
                        echo "POM:"
                        ls -lh pom.xml

                        echo ""
                        echo "Cleaning old build..."

                        mvn clean


                        echo ""
                        echo "Running unit tests..."

                        mvn test


                        echo ""
                        echo "Creating JAR..."

                        mvn package -DskipTests


                        echo ""
                        echo "Build artifacts:"

                        ls -lh target/


                        echo ""
                        echo "Checking JAR..."

                        ls -lh target/*.jar
                    '''
                }
            }
        }


        // =====================================================
        // 5. SONARQUBE ANALYSIS
        // =====================================================

        stage('SonarQube Analysis') {

            steps {

                dir("${WORKSPACE}") {

                    withSonarQubeEnv("${SONARQUBE_SERVER}") {

                        sh '''
                            set -e

                            echo "======================================"
                            echo "SONARQUBE ANALYSIS"
                            echo "======================================"

                            echo "Current user:"
                            whoami

                            echo ""
                            echo "HOME:"
                            echo "$HOME"

                            echo ""
                            echo "Workspace:"
                            pwd

                            echo ""
                            echo "Checking POM:"
                            ls -lh pom.xml


                            echo ""
                            echo "Running SonarQube..."


                            mvn \
                            org.sonarsource.scanner.maven:sonar-maven-plugin:5.7.0.6970:sonar \
                            -Dsonar.projectKey=${SONAR_PROJECT_KEY}


                            echo ""
                            echo "SonarQube analysis completed"
                        '''
                    }
                }
            }
        }


        // =====================================================
        // 6. SONARQUBE QUALITY GATE
        // =====================================================

        stage('SonarQube Quality Gate') {

            steps {

                echo "======================================"
                echo "SONARQUBE QUALITY GATE"
                echo "======================================"

                timeout(time: 5, unit: 'MINUTES') {

                    waitForQualityGate abortPipeline: true
                }
            }
        }


        // =====================================================
        // 7. GET AWS ACCOUNT
        // =====================================================

        stage('Get AWS Account') {

            steps {

                script {

                    echo "======================================"
                    echo "AWS ACCOUNT INFORMATION"
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


                    echo "AWS Account ID:"
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
        // 8. CHECK DOCKER
        // =====================================================

        stage('Check Docker') {

            steps {

                sh '''
                    set -e

                    echo "======================================"
                    echo "DOCKER CHECK"
                    echo "======================================"

                    docker --version

                    echo ""
                    echo "Docker information:"

                    docker info
                '''
            }
        }


        // =====================================================
        // 9. DOCKER BUILD
        // =====================================================

        stage('Docker Build') {

            steps {

                dir("${WORKSPACE}") {

                    sh '''
                        set -e

                        echo "======================================"
                        echo "DOCKER BUILD"
                        echo "======================================"

                        echo "Dockerfile:"
                        ls -lh Dockerfile


                        echo ""
                        echo "Building image:"

                        echo "${IMAGE_URI}:${IMAGE_TAG}"


                        docker build \
                            -t "${IMAGE_URI}:${IMAGE_TAG}" \
                            .


                        echo ""
                        echo "Docker image created successfully:"


                        docker images | grep "${ECR_REPOSITORY}" || true
                    '''
                }
            }
        }


        // =====================================================
        // 10. TRIVY SECURITY SCAN
        // =====================================================

        stage('Trivy Image Scan') {

            steps {

                sh '''
                    set -e

                    echo "======================================"
                    echo "TRIVY SECURITY SCAN"
                    echo "======================================"

                    echo "Image:"
                    echo "${IMAGE_URI}:${IMAGE_TAG}"


                    echo ""
                    echo "Starting vulnerability scan..."


                    trivy image \
                        --scanners vuln \
                        --severity HIGH,CRITICAL \
                        --ignore-unfixed \
                        --exit-code 1 \
                        "${IMAGE_URI}:${IMAGE_TAG}"


                    echo ""
                    echo "Trivy scan completed successfully"
                '''
            }
        }


        // =====================================================
        // 11. LOGIN TO ECR
        // =====================================================

        stage('Login to ECR') {

            steps {

                sh '''
                    set -e

                    echo "======================================"
                    echo "LOGIN TO AWS ECR"
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
        // 12. CHECK ECR REPOSITORY
        // =====================================================

        stage('Check ECR Repository') {

            steps {

                sh '''
                    set -e

                    echo "======================================"
                    echo "CHECK ECR REPOSITORY"
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
        // 13. PUSH IMAGE TO ECR
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


                    docker push \
                        "${IMAGE_URI}:${IMAGE_TAG}"


                    echo ""
                    echo "Docker image pushed successfully"
                '''
            }
        }


        // =====================================================
        // 14. VERIFY ECR IMAGE
        // =====================================================

        stage('Verify ECR Image') {

            steps {

                sh '''
                    set -e

                    echo "======================================"
                    echo "VERIFY ECR IMAGE"
                    echo "======================================"


                    aws ecr describe-images \
                        --repository-name "${ECR_REPOSITORY}" \
                        --image-ids imageTag="${IMAGE_TAG}" \
                        --region "${AWS_REGION}"


                    echo ""
                    echo "Image exists in ECR"
                '''
            }
        }


        // =====================================================
        // 15. UPDATE KUBECONFIG
        // =====================================================

        stage('Configure EKS') {

            steps {

                sh '''
                    set -e

                    echo "======================================"
                    echo "CONFIGURE EKS"
                    echo "======================================"


                    echo "Updating kubeconfig..."


                    aws eks update-kubeconfig \
                        --region "${AWS_REGION}" \
                        --name "${EKS_CLUSTER}"


                    echo ""
                    echo "Checking Kubernetes connection..."


                    kubectl cluster-info


                    echo ""
                    echo "Kubernetes nodes:"


                    kubectl get nodes
                '''
            }
        }


        // =====================================================
        // 16. CREATE NAMESPACE
        // =====================================================

        stage('Create Kubernetes Namespace') {

            steps {

                dir("${WORKSPACE}") {

                    sh '''
                        set -e

                        echo "======================================"
                        echo "KUBERNETES NAMESPACE"
                        echo "======================================"


                        if [ -f k8s/namespace.yaml ]; then

                            kubectl apply \
                                -f k8s/namespace.yaml

                        else

                            echo "namespace.yaml not found"
                            echo "Creating namespace manually..."

                            kubectl create namespace "${K8S_NAMESPACE}" \
                                --dry-run=client \
                                -o yaml | kubectl apply -f -

                        fi


                        echo ""
                        echo "Namespace:"

                        kubectl get namespace "${K8S_NAMESPACE}"
                    '''
                }
            }
        }


        // =====================================================
        // 17. DEPLOY KUBERNETES APPLICATION
        // =====================================================

        stage('Deploy Application') {

            steps {

                dir("${WORKSPACE}") {

                    sh '''
                        set -e

                        echo "======================================"
                        echo "DEPLOY APPLICATION"
                        echo "======================================"


                        echo "Applying deployment..."


                        kubectl apply \
                            -f k8s/deployment.yaml


                        echo ""
                        echo "Applying service..."


                        kubectl apply \
                            -f k8s/service.yaml


                        echo ""
                        echo "Applying ingress..."


                        if [ -f k8s/ingress.yaml ]; then

                            kubectl apply \
                                -f k8s/ingress.yaml

                        else

                            echo "Ingress file not found - skipping"

                        fi
                    '''
                }
            }
        }


        // =====================================================
        // 18. UPDATE IMAGE
        // =====================================================

        stage('Update Deployment Image') {

            steps {

                sh '''
                    set -e

                    echo "======================================"
                    echo "UPDATE DEPLOYMENT IMAGE"
                    echo "======================================"


                    echo "New image:"
                    echo "${IMAGE_URI}:${IMAGE_TAG}"


                    kubectl -n "${K8S_NAMESPACE}" \
                        set image deployment/"${K8S_DEPLOYMENT}" \
                        "${K8S_CONTAINER}"="${IMAGE_URI}:${IMAGE_TAG}"


                    echo ""
                    echo "Deployment image updated"
                '''
            }
        }


        // =====================================================
        // 19. ROLLOUT
        // =====================================================

        stage('Kubernetes Rollout') {

            steps {

                sh '''
                    set -e

                    echo "======================================"
                    echo "KUBERNETES ROLLOUT"
                    echo "======================================"


                    kubectl -n "${K8S_NAMESPACE}" \
                        rollout status \
                        deployment/"${K8S_DEPLOYMENT}" \
                        --timeout=5m


                    echo ""
                    echo "Rollout successful"
                '''
            }
        }


        // =====================================================
        // 20. VERIFY APPLICATION
        // =====================================================

        stage('Verify Deployment') {

            steps {

                sh '''
                    set -e

                    echo "======================================"
                    echo "VERIFY KUBERNETES DEPLOYMENT"
                    echo "======================================"


                    echo "Deployment:"
                    kubectl -n "${K8S_NAMESPACE}" \
                        get deployment


                    echo ""
                    echo "Pods:"
                    kubectl -n "${K8S_NAMESPACE}" \
                        get pods -o wide


                    echo ""
                    echo "Services:"
                    kubectl -n "${K8S_NAMESPACE}" \
                        get services


                    echo ""
                    echo "Ingress:"
                    kubectl -n "${K8S_NAMESPACE}" \
                        get ingress || true
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
            ==============================================
                    CI/CD PIPELINE SUCCESSFUL
            ==============================================
            '''
        }


        failure {

            echo '''
            ==============================================
                    CI/CD PIPELINE FAILED
            ==============================================
            '''
        }


        always {

            echo "=============================================="
            echo "PIPELINE COMPLETED"
            echo "STATUS: ${currentBuild.currentResult}"
            echo "=============================================="


            sh '''
                echo "Cleaning unused Docker images..."

                docker image prune -f || true
            '''
        }
    }
}
