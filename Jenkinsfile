pipeline {

    agent any

    environment {

        // ==============================
        // AWS
        // ==============================
        AWS_REGION = 'ap-south-1'
        ECR_REPOSITORY = 'my-java-app'
        EKS_CLUSTER = 'my-eks-cluster'

        // ==============================
        // SONARQUBE
        // ==============================
        SONARQUBE_SERVER = 'SonarQube'
        SONAR_PROJECT_KEY = 'my-java-app'

        // ==============================
        // DOCKER
        // ==============================
        IMAGE_TAG = "${BUILD_NUMBER}"
    }

    stages {

        // ==============================
        // 1. CHECKOUT
        // ==============================

        stage('Checkout') {
            steps {
                checkout scm
            }
        }


        // ==============================
        // 2. VERIFY WORKSPACE
        // ==============================

        stage('Verify Workspace') {
            steps {
                sh '''
                    echo "======================================"
                    echo "VERIFY WORKSPACE"
                    echo "======================================"

                    echo "WORKSPACE = $WORKSPACE"

                    pwd

                    echo "Files in workspace:"
                    ls -la

                    echo "Searching for pom.xml:"
                    find "$WORKSPACE" -name pom.xml -print

                    if [ ! -f "$WORKSPACE/pom.xml" ]; then
                        echo "ERROR: pom.xml not found!"
                        exit 1
                    fi

                    echo "SUCCESS: pom.xml found."
                '''
            }
        }


        // ==============================
        // 3. BUILD & TEST
        // ==============================

        stage('Build & Test') {
            steps {

                dir("${WORKSPACE}") {

                    sh '''
                        echo "======================================"
                        echo "BUILD & TEST"
                        echo "======================================"

                        pwd

                        mvn clean test package
                    '''
                }
            }
        }


        // ==============================
        // 4. SONARQUBE ANALYSIS
        // ==============================

        stage('SonarQube Analysis') {
            steps {

                dir("${WORKSPACE}") {

                    withSonarQubeEnv("${SONARQUBE_SERVER}") {

                        sh '''
                            echo "======================================"
                            echo "SONARQUBE ANALYSIS"
                            echo "======================================"

                            echo "Workspace:"
                            pwd

                            echo "Checking POM:"
                            ls -lh pom.xml

                            echo "Running SonarQube analysis..."

                            mvn sonar:sonar \
                                -Dsonar.projectKey=${SONAR_PROJECT_KEY}
                        '''
                    }
                }
            }
        }


        // ==============================
        // 5. QUALITY GATE
        // ==============================

        stage('SonarQube Quality Gate') {
            steps {

                echo "Waiting for SonarQube Quality Gate..."

                timeout(time: 5, unit: 'MINUTES') {

                    waitForQualityGate abortPipeline: true
                }
            }
        }


        // ==============================
        // 6. GET AWS ACCOUNT
        // ==============================

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

                    echo "AWS Account ID: ${env.AWS_ACCOUNT_ID}"
                    echo "ECR Registry: ${env.ECR_REGISTRY}"
                    echo "Docker Image: ${env.IMAGE_URI}:${env.IMAGE_TAG}"
                }
            }
        }


        // ==============================
        // 7. DOCKER BUILD
        // ==============================

        stage('Docker Build') {
            steps {

                sh '''
                    echo "======================================"
                    echo "DOCKER BUILD"
                    echo "======================================"

                    docker build \
                        -t ${IMAGE_URI}:${IMAGE_TAG} .
                '''
            }
        }


        // ==============================
        // 8. TRIVY SCAN
        // ==============================

        stage('Trivy Image Scan') {
            steps {

                sh '''
                    echo "======================================"
                    echo "TRIVY SECURITY SCAN"
                    echo "======================================"

                    trivy image \
                        --exit-code 1 \
                        --severity HIGH,CRITICAL \
                        --ignore-unfixed \
                        ${IMAGE_URI}:${IMAGE_TAG}
                '''
            }
        }


        // ==============================
        // 9. LOGIN TO ECR
        // ==============================

        stage('Login to ECR') {
            steps {

                sh '''
                    echo "======================================"
                    echo "LOGIN TO ECR"
                    echo "======================================"

                    aws ecr get-login-password \
                        --region ${AWS_REGION} | \
                    docker login \
                        --username AWS \
                        --password-stdin ${ECR_REGISTRY}
                '''
            }
        }


        // ==============================
        // 10. PUSH IMAGE
        // ==============================

        stage('Push Image to ECR') {
            steps {

                sh '''
                    echo "======================================"
                    echo "PUSH IMAGE TO ECR"
                    echo "======================================"

                    docker push ${IMAGE_URI}:${IMAGE_TAG}
                '''
            }
        }


        // ==============================
        // 11. DEPLOY TO EKS
        // ==============================

        stage('Deploy to EKS') {
            steps {

                sh '''
                    echo "======================================"
                    echo "DEPLOY TO EKS"
                    echo "======================================"

                    echo "Updating kubeconfig..."

                    aws eks update-kubeconfig \
                        --region ${AWS_REGION} \
                        --name ${EKS_CLUSTER}


                    echo "Applying namespace..."

                    kubectl apply \
                        -f k8s/namespace.yaml


                    echo "Applying deployment..."

                    kubectl apply \
                        -f k8s/deployment.yaml


                    echo "Applying service..."

                    kubectl apply \
                        -f k8s/service.yaml


                    echo "Applying ingress..."

                    kubectl apply \
                        -f k8s/ingress.yaml


                    echo "Updating application image..."

                    kubectl -n myapp set image \
                        deployment/myapp \
                        myapp=${IMAGE_URI}:${IMAGE_TAG}


                    echo "Waiting for deployment rollout..."

                    kubectl -n myapp rollout status \
                        deployment/myapp \
                        --timeout=5m
                '''
            }
        }
    }


    // ==============================
    // POST ACTIONS
    // ==============================

    post {

        success {

            echo '''
========================================
CI/CD PIPELINE SUCCESSFUL
========================================
'''
        }

        failure {

            echo '''
========================================
CI/CD PIPELINE FAILED
========================================
'''
        }

        always {

            echo "Pipeline completed with status: ${currentBuild.currentResult}"

            sh '''
                echo "Removing unused Docker images..."

                docker image prune -f || true
            '''
        }
    }
}
