pipeline {

    agent {
        label 'Devops-Worker'
    }

    environment {
        AWS_REGION     = 'ap-south-1'
        ECR_REPOSITORY = 'my-java-app'
        IMAGE_TAG      = "${BUILD_NUMBER}"

        HELM_RELEASE   = 'my-java-app'
        HELM_CHART     = './helm/my-java-app'

        GITOPS_REPO    = 'https://github.com/nandeeshnn77-lab/my-java-app-gitops.git'
        GITOPS_BRANCH  = 'main'
        GITOPS_VALUES  = 'helm/my-java-app/values.yaml'
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

                    echo "===== JAVA ====="
                    java -version

                    echo "===== MAVEN ====="
                    mvn -version

                    echo "===== GIT ====="
                    git --version

                    echo "===== DOCKER ====="
                    docker --version
                    docker ps

                    echo "===== AWS ====="
                    aws --version
                    aws sts get-caller-identity

                    echo "===== HELM ====="
                    helm version
                '''
            }
        }

        stage('Build and Test') {
            steps {
                sh '''
                    set -e

                    echo "===== MAVEN BUILD ====="
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

                    echo "AWS Account: ${env.AWS_ACCOUNT_ID}"
                    echo "ECR Registry: ${env.ECR_REGISTRY}"
                    echo "Docker Image: ${env.IMAGE_URI}:${env.IMAGE_TAG}"
                }
            }
        }

        stage('Verify ECR Repository') {
            steps {
                sh '''
                    set -e

                    echo "===== VERIFY ECR REPOSITORY ====="

                    if aws ecr describe-repositories \
                        --repository-names "${ECR_REPOSITORY}" \
                        --region "${AWS_REGION}" >/dev/null 2>&1
                    then
                        echo "ECR repository exists."
                    else
                        echo "Creating ECR repository..."

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

                    echo "===== DOCKER BUILD ====="

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

                    echo "===== ECR LOGIN ====="

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

                    echo "===== PUSH IMAGE TO ECR ====="

                    docker push "${IMAGE_URI}:${IMAGE_TAG}"
                '''
            }
        }

        stage('Verify Image in ECR') {
            steps {
                sh '''
                    set -e

                    echo "===== VERIFY IMAGE ====="

                    aws ecr describe-images \
                        --repository-name "${ECR_REPOSITORY}" \
                        --region "${AWS_REGION}" \
                        --image-ids imageTag="${IMAGE_TAG}"
                '''
            }
        }

        stage('Helm Lint') {
            steps {
                sh '''
                    set -e

                    echo "===== HELM LINT ====="

                    helm lint "${HELM_CHART}"
                '''
            }
        }

        stage('Helm Template Validation') {
            steps {
                sh '''
                    set -e

                    echo "===== HELM TEMPLATE VALIDATION ====="

                    helm template \
                        "${HELM_RELEASE}" \
                        "${HELM_CHART}" \
                        --set image.repository="${IMAGE_URI}" \
                        --set image.tag="${IMAGE_TAG}" \
                        > /tmp/rendered-manifests.yaml

                    echo "Helm template validation successful."
                '''
            }
        }

        stage('Update GitOps Repository') {

            steps {

                withCredentials([
                    usernamePassword(
                        credentialsId: 'github-credentials',
                        usernameVariable: 'GIT_USERNAME',
                        passwordVariable: 'GIT_TOKEN'
                    )
                ]) {

                    sh '''
                        set -e

                        echo "===== UPDATE GITOPS REPOSITORY ====="

                        rm -rf gitops-repo

                        git clone \
                            --branch "${GITOPS_BRANCH}" \
                            "https://${GIT_USERNAME}:${GIT_TOKEN}@github.com/nandeeshnn77-lab/my-java-app-gitops.git" \
                            gitops-repo

                        cd gitops-repo

                        echo "Current image configuration:"
                        grep -A3 '^image:' "${GITOPS_VALUES}"

                        sed -i \
                            's/^  tag:.*/  tag: "'"${IMAGE_TAG}"'"/' \
                            "${GITOPS_VALUES}"

                        echo "Updated image configuration:"
                        grep -A3 '^image:' "${GITOPS_VALUES}"

                        git config user.name "Jenkins"
                        git config user.email "jenkins@local"

                        git add "${GITOPS_VALUES}"

                        if git diff --cached --quiet
                        then
                            echo "GitOps repository already contains image tag ${IMAGE_TAG}."
                        else
                            git commit \
                                -m "Deploy my-java-app image ${IMAGE_TAG}"

                            git push origin "${GITOPS_BRANCH}"

                            echo "GitOps repository updated successfully."
                        fi
                    '''
                }
            }
        }

        stage('Deployment Summary') {
            steps {
                echo """
========================================
CI PIPELINE SUCCESS
========================================

Image:
${env.IMAGE_URI}:${env.IMAGE_TAG}

Image pushed to:
AWS ECR

GitOps repository:
${env.GITOPS_REPO}

Argo CD will deploy the new image automatically.

========================================
"""
            }
        }
    }

    post {

        success {
            echo 'CI PIPELINE SUCCESSFUL - GITOPS REPOSITORY UPDATED'
        }

        failure {
            echo 'CI PIPELINE FAILED'
        }

        always {
            sh '''
                if [ -n "${ECR_REGISTRY}" ]
                then
                    docker logout "${ECR_REGISTRY}" 2>/dev/null || true
                fi
            '''
        }
    }
}
