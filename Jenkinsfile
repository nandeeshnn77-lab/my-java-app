pipeline {

    agent any

    environment {

        AWS_REGION = 'ap-south-1'

        ECR_REPOSITORY = 'my-java-app'

        EKS_CLUSTER = 'my-eks-cluster'

        SONARQUBE_SERVER = 'SonarQube'

        IMAGE_TAG = "${BUILD_NUMBER}"
    }

    stages {

        stage('Checkout') {
            steps {
                checkout scm
            }
        }

        stage('Build & Test') {
            steps {
                sh 'mvn clean test package'
            }
        }

        stage('SonarQube Analysis') {
            steps {

                withSonarQubeEnv("${SONARQUBE_SERVER}") {

                    sh '''
                        mvn sonar:sonar \
                        -Dsonar.projectKey=my-java-app
                    '''
                }
            }
        }

        stage('SonarQube Quality Gate') {
            steps {

                timeout(time: 5, unit: 'MINUTES') {

                    waitForQualityGate abortPipeline: true
                }
            }
        }

        stage('Get AWS Account') {
            steps {

                script {

                    env.AWS_ACCOUNT_ID = sh(
                        script: 'aws sts get-caller-identity --query Account --output text',
                        returnStdout: true
                    ).trim()

                    env.ECR_REGISTRY =
                        "${env.AWS_ACCOUNT_ID}.dkr.ecr.${env.AWS_REGION}.amazonaws.com"

                    env.IMAGE_URI =
                        "${env.ECR_REGISTRY}/${env.ECR_REPOSITORY}"

                    echo "ECR Registry: ${env.ECR_REGISTRY}"
                    echo "Image: ${env.IMAGE_URI}:${env.IMAGE_TAG}"
                }
            }
        }

        stage('Docker Build') {
            steps {

                sh '''
                    docker build \
                    -t ${IMAGE_URI}:${IMAGE_TAG} .
                '''
            }
        }

        stage('Trivy Image Scan') {
            steps {

                sh '''
                    trivy image \
                    --exit-code 1 \
                    --severity HIGH,CRITICAL \
                    --ignore-unfixed \
                    ${IMAGE_URI}:${IMAGE_TAG}
                '''
            }
        }

        stage('Login to ECR') {
            steps {

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

                sh '''
                    docker push ${IMAGE_URI}:${IMAGE_TAG}
                '''
            }
        }

        stage('Deploy to EKS') {
            steps {

                sh '''
                    aws eks update-kubeconfig \
                    --region ${AWS_REGION} \
                    --name ${EKS_CLUSTER}

                    kubectl apply -f k8s/namespace.yaml

                    kubectl apply -f k8s/deployment.yaml

                    kubectl apply -f k8s/service.yaml

                    kubectl apply -f k8s/ingress.yaml

                    kubectl -n myapp set image deployment/myapp \
                    myapp=${IMAGE_URI}:${IMAGE_TAG}

                    kubectl -n myapp rollout status deployment/myapp \
                    --timeout=5m
                '''
            }
        }
    }

    post {

        success {
            echo 'CI/CD PIPELINE SUCCESSFUL'
        }

        failure {
            echo 'CI/CD PIPELINE FAILED'
        }

        always {
            echo "Pipeline completed: ${currentBuild.currentResult}"
        }
    }
}
