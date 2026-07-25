pipeline {
    agent any

    options {
        skipDefaultCheckout(true)
        disableConcurrentBuilds()
        timestamps()
        buildDiscarder(logRotator(numToKeepStr: '10'))
        timeout(time: 45, unit: 'MINUTES')
    }

    parameters {
        choice(
            name: 'PIPELINE_ACTION',
            choices: ['BUILD_ONLY', 'DEPLOY_DEV'],
            description: 'BUILD_ONLY：测试、打包、构建并推送Harbor；DEPLOY_DEV：推送后从Harbor部署开发环境'
        )
    }

    environment {
        APP_NAME = "user-service"

        HARBOR_REGISTRY = "host.docker.internal:8443"
        HARBOR_PROJECT = "devops-lab"

        MYSQL_DATABASE = "user_db"
        MYSQL_USER = "app"

        DEV_COMPOSE_PROJECT = "user-service-demo"
        DEV_APP_HOST_PORT = "8082"
        DEV_MYSQL_HOST_PORT = "3307"
    }

    stages {
        stage('Checkout') {
            steps {
                echo '从Git仓库获取项目代码'
                checkout scm
            }
        }

        stage('Initialize Version') {
            steps {
                script {
                    env.GIT_SHORT_COMMIT = sh(
                        script: 'git rev-parse --short=8 HEAD',
                        returnStdout: true
                    ).trim()

                    env.RELEASE_VERSION =
                        "${env.BUILD_NUMBER}-${env.GIT_SHORT_COMMIT}"

                    env.LOCAL_APP_IMAGE =
                        "${env.APP_NAME}:${env.RELEASE_VERSION}"

                    env.HARBOR_APP_IMAGE =
                        "${env.HARBOR_REGISTRY}/${env.HARBOR_PROJECT}/${env.APP_NAME}:${env.RELEASE_VERSION}"
                }

                echo "Git提交：${env.GIT_SHORT_COMMIT}"
                echo "发布版本：${env.RELEASE_VERSION}"
                echo "本地镜像：${env.LOCAL_APP_IMAGE}"
                echo "Harbor镜像：${env.HARBOR_APP_IMAGE}"
            }
        }

        stage('Environment') {
            steps {
                sh 'java -version'
                sh 'chmod +x mvnw'
                sh './mvnw -version'
                sh 'docker version'
                sh 'docker compose version'
            }
        }

        stage('Test and Package') {
            steps {
                sh './mvnw -B clean verify'
            }
        }

        stage('Archive') {
            steps {
                archiveArtifacts(
                    artifacts: 'target/*.jar',
                    fingerprint: true,
                    onlyIfSuccessful: true
                )
            }
        }

        stage('Build Docker Image') {
            steps {
                sh '''
                    set -eu

                    docker build \
                        -t "$LOCAL_APP_IMAGE" \
                        .
                '''
            }
        }

        stage('Push Image to Harbor') {
            steps {
                withCredentials([
                    usernamePassword(
                        credentialsId: 'harbor-devops-lab-robot',
                        usernameVariable: 'HARBOR_USERNAME',
                        passwordVariable: 'HARBOR_PASSWORD'
                    )
                ]) {
                    sh '''
                        set -eu
                        set +x

                        trap 'docker logout "$HARBOR_REGISTRY" >/dev/null 2>&1 || true' EXIT

                        printf '%s' "$HARBOR_PASSWORD" |
                            docker login "$HARBOR_REGISTRY" \
                                --username "$HARBOR_USERNAME" \
                                --password-stdin

                        docker tag \
                            "$LOCAL_APP_IMAGE" \
                            "$HARBOR_APP_IMAGE"

                        docker push "$HARBOR_APP_IMAGE"
                    '''
                }
            }
        }

        stage('Deploy Dev') {
            when {
                expression {
                    params.PIPELINE_ACTION == 'DEPLOY_DEV'
                }
            }

            steps {
                withCredentials([
                    usernamePassword(
                        credentialsId: 'harbor-devops-lab-robot',
                        usernameVariable: 'HARBOR_USERNAME',
                        passwordVariable: 'HARBOR_PASSWORD'
                    ),
                    string(
                        credentialsId: 'user-service-mysql-root-password',
                        variable: 'MYSQL_ROOT_PASSWORD'
                    ),
                    string(
                        credentialsId: 'user-service-mysql-password',
                        variable: 'MYSQL_PASSWORD'
                    )
                ]) {
                    sh '''
                        set -eu
                        set +x

                        trap 'docker logout "$HARBOR_REGISTRY" >/dev/null 2>&1 || true' EXIT

                        printf '%s' "$HARBOR_PASSWORD" |
                            docker login "$HARBOR_REGISTRY" \
                                --username "$HARBOR_USERNAME" \
                                --password-stdin

                        export APP_IMAGE="$HARBOR_APP_IMAGE"
                        export MYSQL_ROOT_PASSWORD
                        export MYSQL_DATABASE
                        export MYSQL_USER
                        export MYSQL_PASSWORD
                        export APP_HOST_PORT="$DEV_APP_HOST_PORT"
                        export MYSQL_HOST_PORT="$DEV_MYSQL_HOST_PORT"

                        echo "从Harbor拉取开发环境镜像：$APP_IMAGE"

                        docker compose \
                            -p "$DEV_COMPOSE_PROJECT" \
                            pull app

                        docker compose \
                            -p "$DEV_COMPOSE_PROJECT" \
                            up -d --no-build --remove-orphans

                        docker compose \
                            -p "$DEV_COMPOSE_PROJECT" \
                            ps
                    '''
                }
            }
        }

        stage('Verify Dev Deployment') {
            when {
                expression {
                    params.PIPELINE_ACTION == 'DEPLOY_DEV'
                }
            }

            steps {
                sh '''
                    for i in $(seq 1 30); do
                        if docker run --rm \
                            --network "${DEV_COMPOSE_PROJECT}_default" \
                            busybox:1.37 \
                            wget -qO- http://app:8082/actuator/health
                        then
                            echo "应用健康检查成功"
                            exit 0
                        fi

                        echo "等待应用启动：第 ${i} 次"
                        sleep 2
                    done

                    echo "应用健康检查失败"

                    docker compose \
                        -p "$DEV_COMPOSE_PROJECT" \
                        logs --tail=100 app || true

                    exit 1
                '''
            }
        }
    }

    post {
        always {
            junit(
                testResults: 'target/surefire-reports/*.xml',
                allowEmptyResults: true
            )
        }

        success {
            echo "流水线执行成功，操作类型：${params.PIPELINE_ACTION}"
            echo "Harbor镜像：${env.HARBOR_APP_IMAGE}"
        }

        failure {
            echo '流水线失败，请检查对应阶段日志'

            script {
                if (
                    params.PIPELINE_ACTION == 'DEPLOY_DEV' &&
                    fileExists('compose.yaml')
                ) {
                    sh '''
                        docker compose \
                            -p "$DEV_COMPOSE_PROJECT" \
                            logs --tail=100 app || true
                    '''
                } else {
                    echo '本次未执行开发环境部署，跳过应用容器日志收集'
                }
            }
        }
    }
}
