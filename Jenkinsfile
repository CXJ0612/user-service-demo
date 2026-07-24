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
            description: 'BUILD_ONLY：只测试、打包和构建镜像；DEPLOY_DEV：构建后部署开发环境'
        )
    }

    environment {
        APP_NAME = "user-service"
        MYSQL_DATABASE = "user_db"
        MYSQL_USER = "app"
    }

    stages {
        stage('Checkout') {
            steps {
                echo '从 Git 仓库获取项目代码'
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

                    env.RELEASE_VERSION = "${env.BUILD_NUMBER}-${env.GIT_SHORT_COMMIT}"
                    env.APP_IMAGE = "${env.APP_NAME}:${env.RELEASE_VERSION}"
                }

                echo "Git提交：${env.GIT_SHORT_COMMIT}"
                echo "发布版本：${env.RELEASE_VERSION}"
                echo "镜像名称：${env.APP_IMAGE}"
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
                sh 'docker build -t "$APP_IMAGE" .'
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
                        set +x

                        APP_IMAGE="$APP_IMAGE" \
                        MYSQL_ROOT_PASSWORD="$MYSQL_ROOT_PASSWORD" \
                        MYSQL_DATABASE="$MYSQL_DATABASE" \
                        MYSQL_USER="$MYSQL_USER" \
                        MYSQL_PASSWORD="$MYSQL_PASSWORD" \
                        docker compose up -d --no-build --remove-orphans

                        docker compose ps
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
                            --network user-service-demo_default \
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
                    docker logs --tail 100 user-service-app || true
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
            echo '测试、打包和部署全部成功'
        }

        failure {
            echo '流水线失败，请检查对应阶段日志'
            sh 'docker logs --tail 100 user-service-app || true'
        }
    }
}
