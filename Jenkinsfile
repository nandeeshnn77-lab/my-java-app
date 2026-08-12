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

        SONAR_MAVEN_PLUGIN_VERSION = '5.7.0.6970'


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

                checkout scm
            }
        }


        // =====================================================
        // 2. VERIFY JENKINS WORKSPACE
        // =====================================================

        stage('Verify Workspace') {

            steps {

                sh '''
                    echo "=============================================="
                    echo "VERIFY JENKINS WORKSPACE"
                    echo "=============================================="

                    echo "User:"
                    whoami

                    echo "HOME:"
                    echo "$HOME"

                    echo "WORKSPACE:"
                    echo "$WORKSPACE"

                    echo "Current Directory:"
                    pwd

                    echo ""
                    echo "Workspace Files:"
                    ls -la

                    echo ""
                    echo "Searching for pom.xml:"
                    find "$WORKSPACE" -maxdepth 3 -name pom.xml -print

                    echo ""

                    if [ ! -f "$WORKSPACE/pom.xml" ]; then
                        echo "ERROR: pom.xml was not found!"
                        echo "Current directory: $(pwd)"
                        echo "Workspace: $WORKSPACE"
                        exit 1
                    fi

                    echo "SUCCESS: pom.xml found"

                    echo ""
                    echo "Maven Version:"
                    mvn -version

                    echo ""
                    echo "Java Version:"
                    java -version
                '''
            }
        }


        // =====================================================
        // 3. BUILD & TEST
        // =====================================================

        stage('Build & Test') {

            steps {

                dir("${WORKSPACE}") {

                    sh '''
                        echo "=============================================="
                        echo "BUILD & TEST"
                        echo "=============================================="

                        pwd

                        echo "Checking pom.xml..."
                        ls -lh pom.xml

                        echo ""
                        echo "Running Maven build and tests..."

                        mvn clean test package
                    '''
                }
            }
        }


        // =====================================================
        // 4. SONARQUBE ANALYSIS
        // =====================================================

        stage('SonarQube Analysis') {

            steps {

                dir("${WORKSPACE}") {

                    withSonarQubeEnv("${SONARQUBE_SERVER}") {

                        sh '''
                            echo "=============================================="
                            echo "SONARQUBE ANALYSIS"
                            echo "=============================================="

                            echo "User:"
                            whoami

                            echo "HOME:"
                            echo "$HOME"

                            echo "Workspace:"
                            echo "$WORKSPACE"

                            echo "Current Directory:"
                            pwd

                            echo ""
                            echo "Checking pom.xml..."
                            ls -lh pom.xml

                            echo ""
                            echo "Running SonarQube analysis..."

                            mvn clean verify \
                            org.sonarsource.scanner.maven:sonar-maven-plugin:${SONAR_MAVEN_PLUGIN_VERSION}:sonar \
                            -Dsonar.projectKey=${SONAR_PROJECT_KEY}

                            echo ""
                            echo "SonarQube analysis completed."
                        '''
                    }
                }
            }
        }


        // =====================================================
        // 5. SONARQUBE QUALITY GATE
        // =====================================================

        stage('SonarQube Quality Gate') {

            steps {

                echo "Waiting for SonarQube Quality Gate..."

                timeout(time: 5, unit: 'MINUTES') {

                    waitForQualityGate abortPipeline: true
                }
            }
        }


        // =====================================================
        // 6. GET AWS ACCOUNT ID
        // =====================================================

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


                    echo "=============================================="
                    echo "AWS INFORMATION"
                    echo "=============================================="

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
                    echo "=============================================="
                    echo "DOCKER BUILD"
                    echo "=============================================="

                    echo "Building Docker image:"

                    docker build \
                        -t ${IMAGE_URI}:${IMAGE_TAG} .

                    echo ""
                    echo "Docker image created successfully."

                    docker images | grep "${ECR_REPOSITORY}" || true
                '''
            }
        }


        // =====================================================
        // 8. TRIVY IMAGE SECURITY SCAN
        // =====================================================

        stage('Trivy Image Scan') {

            steps {

                sh '''
                    echo "=============================================="
                    echo "TRIVY SECURITY SCAN"
                    echo "=============================================="

                    echo "Scanning image:"
                    echo "${IMAGE_URI}:${IMAGE_TAG}"

                    trivy image \
                        --exit-code 1 \
                        --severity HIGH,CRITICAL \
                        --ignore-unfixed \
                        ${IMAGE_URI}:${IMAGE_TAG}
                '''
            }
        }


        // =====================================================
        // 9. LOGIN TO AWS ECR
        // =====================================================

        stage('Login to ECR') {

            steps {

                sh '''
                    echo "=============================================="
                    echo "LOGIN TO AWS ECR"
                    echo "=============================================="

                    aws ecr get-login-password \
                        --region ${AWS_REGION} | \
                    docker login \
                        --username AWS \
                        --password-stdin ${ECR_REGISTRY}
                '''
            }
        }


        // =====================================================
        // 10. PUSH IMAGE TO ECR
        // =====================================================

        stage('Push Image to ECR') {

            steps {

                sh '''
                    echo "=============================================="
                    echo "PUSH IMAGE TO ECR"
                    echo "=============================================="

                    echo "Pushing:"
                    echo "${IMAGE_URI}:${IMAGE_TAG}"

                    docker push ${IMAGE_URI}:${IMAGE_TAG}

                    echo ""
                    echo "Docker image pushed successfully."
                '''
            }
        }


        // =====================================================
        // 11. DEPLOY TO EKS
        // =====================================================

        stage('Deploy to EKS') {

            steps {

                sh '''
                    echo "=============================================="
                    echo "DEPLOY TO EKS"
                    echo "=============================================="


                    // -----------------------------------------
                    // AWS EKS KUBECONFIG
                    // -----------------------------------------

                    echo "Updating kubeconfig..."

                    aws eks update-kubeconfig \
                        --region ${AWS_REGION} \
                        --name ${EKS_CLUSTER}


                    // -----------------------------------------
                    // CHECK KUBERNETES CONNECTION
                    // -----------------------------------------

                    echo ""
                    echo "Checking Kubernetes connection..."

                    kubectl get nodes


                    // -----------------------------------------
                    // NAMESPACE
                    // -----------------------------------------

                    echo ""
                    echo "Applying namespace..."

                    kubectl apply -f k8s/namespace.yaml


                    // -----------------------------------------
                    // DEPLOYMENT
                    // -----------------------------------------

                    echo ""
                    echo "Applying deployment..."

                    kubectl apply -f k8s/deployment.yaml


                    // -----------------------------------------
                    // SERVICE
                    // -----------------------------------------

                    echo ""
                    echo "Applying service..."

                    kubectl apply -f k8s/service.yaml


                    // -----------------------------------------
                    // INGRESS
                    // -----------------------------------------

                    echo ""
                    echo "Applying ingress..."

                    kubectl apply -f k8s/ingress.yaml


                    // -----------------------------------------
                    // UPDATE IMAGE
                    // -----------------------------------------

                    echo ""
                    echo "Updating deployment image..."

                    kubectl -n myapp set image deployment/myapp \
                        myapp=${IMAGE_URI}:${IMAGE_TAG}


                    // -----------------------------------------
                    // ROLLOUT
                    // -----------------------------------------

                    echo ""
                    echo "Waiting for deployment rollout..."

                    kubectl -n myapp rollout status deployment/myapp \
                        --timeout=5m


                    // -----------------------------------------
                    // POD STATUS
                    // -----------------------------------------

                    echo ""
                    echo "Pods:"

                    kubectl -n myapp get pods -o wide


                    // -----------------------------------------
                    // DEPLOYMENT STATUS
                    // -----------------------------------------

                    echo ""
                    echo "Deployment:"

                    kubectl -n myapp get deployment


                    // -----------------------------------------
                    // SERVICE STATUS
                    // -----------------------------------------

                    echo ""
                    echo "Service:"

                    kubectl -n myapp get service


                    echo ""
                    echo "EKS deployment completed successfully."
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

            Build       : SUCCESS
            Tests       : SUCCESS
            SonarQube   : SUCCESS
            QualityGate : PASSED
            Docker      : SUCCESS
            Trivy       : PASSED
            ECR         : SUCCESS
            EKS         : SUCCESS

            ==============================================
            '''
        }


        failure {

            echo '''
            ==============================================
            CI/CD PIPELINE FAILED
            ==============================================

            Check the failed stage above.

            ==============================================
            '''
        }


        always {

            echo "=============================================="
            echo "PIPELINE COMPLETED"
            echo "=============================================="

            echo "Build Number: ${BUILD_NUMBER}"

            echo "Build Result: ${currentBuild.currentResult}"

            echo "Workspace: ${WORKSPACE}"


            sh '''
                echo ""
                echo "Cleaning unused Docker images..."

                docker image prune -f || true
            '''
        }
    }
}
