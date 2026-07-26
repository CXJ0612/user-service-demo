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
            choices: ['BUILD_ONLY', 'DEPLOY_DEV', 'ROLLBACK_DEV'],
            description: 'BUILD_ONLY：构建并推送；DEPLOY_DEV：构建、推送并部署；ROLLBACK_DEV：直接部署Harbor旧镜像'
        )

        string(
            name: 'ROLLBACK_IMAGE_TAG',
            defaultValue: '',
            trim: true,
            description: '仅ROLLBACK_DEV使用，例如：4-8f18aab4；其他操作请留空'
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

        stage('Validate Parameters') {
            steps {
                script {
                    if (params.PIPELINE_ACTION == 'ROLLBACK_DEV') {
                        def rollbackTag = params.ROLLBACK_IMAGE_TAG?.trim()

                        if (!rollbackTag) {
                            error('ROLLBACK_DEV必须填写ROLLBACK_IMAGE_TAG')
                        }

                        if (!(rollbackTag ==~ /^[A-Za-z0-9_][A-Za-z0-9_.-]{0,127}$/)) {
                            error(
                                'ROLLBACK_IMAGE_TAG格式非法，只允许字母、数字、下划线、点和短横线，最大128个字符'
                            )
                        }

                        env.ROLLBACK_IMAGE_TAG_SAFE = rollbackTag
                        env.ROLLBACK_APP_IMAGE =
                            "${env.HARBOR_REGISTRY}/${env.HARBOR_PROJECT}/${env.APP_NAME}:${rollbackTag}"

                        echo "回滚目标镜像：${env.ROLLBACK_APP_IMAGE}"
                    } else if (params.ROLLBACK_IMAGE_TAG?.trim()) {
                        echo '当前不是ROLLBACK_DEV，ROLLBACK_IMAGE_TAG将被忽略'
                    }
                }
            }
        }

        stage('Initialize Version') {
            when {
                expression {
                    params.PIPELINE_ACTION != 'ROLLBACK_DEV'
                }
            }

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
                sh 'docker version'
                sh 'docker compose version'

                script {
                    if (params.PIPELINE_ACTION != 'ROLLBACK_DEV') {
                        sh 'java -version'
                        sh 'chmod +x mvnw'
                        sh './mvnw -version'
                    } else {
                        echo '回滚操作仅检查Docker环境，跳过Java和Maven检查'
                    }
                }
            }
        }

        stage('Test and Package') {
            when {
                expression {
                    params.PIPELINE_ACTION != 'ROLLBACK_DEV'
                }
            }

            steps {
                sh './mvnw -B clean verify'
            }
        }

        stage('Archive') {
            when {
                expression {
                    params.PIPELINE_ACTION != 'ROLLBACK_DEV'
                }
            }

            steps {
                archiveArtifacts(
                    artifacts: 'target/*.jar',
                    fingerprint: true,
                    onlyIfSuccessful: true
                )
            }
        }

        stage('Build Docker Image') {
            when {
                expression {
                    params.PIPELINE_ACTION != 'ROLLBACK_DEV'
                }
            }

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
            when {
                expression {
                    params.PIPELINE_ACTION != 'ROLLBACK_DEV'
                }
            }

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

        stage('Rollback Dev') {
            when {
                expression {
                    params.PIPELINE_ACTION == 'ROLLBACK_DEV'
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

                        export APP_IMAGE="$ROLLBACK_APP_IMAGE"
                        export MYSQL_ROOT_PASSWORD
                        export MYSQL_DATABASE
                        export MYSQL_USER
                        export MYSQL_PASSWORD
                        export APP_HOST_PORT="$DEV_APP_HOST_PORT"
                        export MYSQL_HOST_PORT="$DEV_MYSQL_HOST_PORT"

                        MYSQL_CONTAINER_ID_BEFORE="$(
                            docker compose \
                                -p "$DEV_COMPOSE_PROJECT" \
                                ps -q mysql
                        )"

                        if [ -z "$MYSQL_CONTAINER_ID_BEFORE" ]; then
                            echo "未找到正在运行的MySQL容器，拒绝执行回滚"
                            exit 1
                        fi

                        echo "回滚前MySQL容器ID：$MYSQL_CONTAINER_ID_BEFORE"
                        echo "从Harbor拉取回滚镜像：$APP_IMAGE"

                        docker compose \
                            -p "$DEV_COMPOSE_PROJECT" \
                            pull app

                        docker compose \
                            -p "$DEV_COMPOSE_PROJECT" \
                            up -d --no-deps --no-build app

                        APP_CONTAINER_ID="$(
                            docker compose \
                                -p "$DEV_COMPOSE_PROJECT" \
                                ps -q app
                        )"

                        MYSQL_CONTAINER_ID_AFTER="$(
                            docker compose \
                                -p "$DEV_COMPOSE_PROJECT" \
                                ps -q mysql
                        )"

                        if [ -z "$APP_CONTAINER_ID" ]; then
                            echo "回滚后未找到app容器"
                            exit 1
                        fi

                        if [ "$MYSQL_CONTAINER_ID_BEFORE" != "$MYSQL_CONTAINER_ID_AFTER" ]; then
                            echo "MySQL容器ID发生变化，回滚失败"
                            echo "回滚前：$MYSQL_CONTAINER_ID_BEFORE"
                            echo "回滚后：$MYSQL_CONTAINER_ID_AFTER"
                            exit 1
                        fi

                        ACTUAL_IMAGE="$(
                            docker inspect \
                                "$APP_CONTAINER_ID" \
                                --format '{{.Config.Image}}'
                        )"

                        echo "期望运行镜像：$APP_IMAGE"
                        echo "实际运行镜像：$ACTUAL_IMAGE"
                        echo "回滚后MySQL容器ID：$MYSQL_CONTAINER_ID_AFTER"

                        if [ "$ACTUAL_IMAGE" != "$APP_IMAGE" ]; then
                            echo "实际运行镜像与回滚目标不一致"
                            exit 1
                        fi

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
                    ['DEPLOY_DEV', 'ROLLBACK_DEV'].contains(
                        params.PIPELINE_ACTION
                    )
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
            script {
                if (params.PIPELINE_ACTION != 'ROLLBACK_DEV') {
                    junit(
                        testResults: 'target/surefire-reports/*.xml',
                        allowEmptyResults: true
                    )
                } else {
                    echo '回滚操作未执行测试，跳过JUnit报告收集'
                }
            }
        }

        success {
            echo "流水线执行成功，操作类型：${params.PIPELINE_ACTION}"

            script {
                if (params.PIPELINE_ACTION == 'ROLLBACK_DEV') {
                    echo "实际回滚镜像：${env.ROLLBACK_APP_IMAGE}"
                } else {
                    echo "Harbor镜像：${env.HARBOR_APP_IMAGE}"
                }
            }
        }

        failure {
            echo '流水线失败，请检查对应阶段日志'

            script {
                if (
                    ['DEPLOY_DEV', 'ROLLBACK_DEV'].contains(
                        params.PIPELINE_ACTION
                    ) &&
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
