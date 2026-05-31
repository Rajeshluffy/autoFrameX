pipeline {
    agent any

    parameters {
        choice(
            name: 'BROWSER',
            choices: ['chrome', 'firefox', 'edge'],
            description: 'Browser to use for UI test execution'
        )
        choice(
            name: 'ENVIRONMENT',
            choices: ['qa', 'staging', 'prod'],
            description: 'Target environment'
        )
        booleanParam(
            name: 'HEADLESS',
            defaultValue: true,
            description: 'Run browser in headless mode (recommended for CI)'
        )
        string(
            name: 'SUITE_FILE',
            defaultValue: 'testng.xml',
            description: 'TestNG suite XML file to execute (relative to project root)'
        )
        string(
            name: 'THREAD_COUNT',
            defaultValue: '1',
            description: 'Parallel thread count for Surefire (parallel=methods)'
        )
    }

    options {
        timestamps()
        timeout(time: 60, unit: 'MINUTES')
        buildDiscarder(logRotator(numToKeepStr: '5'))
    }

    environment {
        // JAVA_HOME is resolved from the agent's tool installation.
        // Configure a JDK 17 tool named 'JDK17' in Jenkins → Global Tool Configuration,
        // or set JAVA_HOME as a node-level environment variable on each agent.
        // Do NOT hardcode a path here — it breaks Linux/Mac agents.
    }

    stages {

        stage('Checkout') {
            steps {
                checkout scm
            }
        }

        stage('Inject Configs') {
            steps {
                withCredentials([
                    file(credentialsId: 'alfadock-config', variable: 'ALFA_CONFIG'),
                    file(credentialsId: 'leaftap-config',  variable: 'LEAF_CONFIG')
                ]) {
                    script {
                        if (isUnix()) {
                            sh '''
                                mkdir -p src/main/resources
                                cp "$ALFA_CONFIG" src/main/resources/alfaDOCKConfig.properties
                                cp "$LEAF_CONFIG"  src/main/resources/leafTapConfig.properties
                            '''
                        } else {
                            bat '''
                                if not exist src\\main\\resources mkdir src\\main\\resources
                                copy /Y "%ALFA_CONFIG%" "src\\main\\resources\\alfaDOCKConfig.properties"
                                copy /Y "%LEAF_CONFIG%"  "src\\main\\resources\\leafTapConfig.properties"
                            '''
                        }
                    }
                }
            }
        }

        stage('Build & Compile') {
            steps {
                script {
                    if (isUnix()) {
                        sh 'mvn clean compile -q'
                    } else {
                        bat 'mvn clean compile -q'
                    }
                }
            }
        }

        stage('Framework Unit Tests') {
            steps {
                script {
                    if (isUnix()) {
                        sh 'mvn test -Dtestng.suite.file=testng-ci.xml -Dsurefire.reportNameSuffix=framework-unit'
                    } else {
                        bat 'mvn test -Dtestng.suite.file=testng-ci.xml -Dsurefire.reportNameSuffix=framework-unit'
                    }
                }
            }
            post {
                always {
                    junit testResults: '**/surefire-reports/*framework-unit*.xml',
                          allowEmptyResults: true
                }
            }
        }

        stage('Run Parallel Suites') {
            parallel {

                stage('TestNG Suite') {
                    steps {
                        script {
                            def cmd = "mvn test" +
                                " -Dtestng.suite.file=${params.SUITE_FILE}" +
                                " -Dbrowser=${params.BROWSER}" +
                                " -Denv=${params.ENVIRONMENT}" +
                                " -Dheadless=${params.HEADLESS}" +
                                " -Dsurefire.threadCount=${params.THREAD_COUNT}" +
                                " -Dsurefire.reportNameSuffix=testng"
                            if (isUnix()) { sh cmd } else { bat cmd }
                        }
                    }
                }

                stage('AlfaDOCK Suite') {
                    steps {
                        script {
                            def cmd = "mvn test" +
                                " -Dtestng.suite.file=alfaDOCKtestng.xml" +
                                " -Dbrowser=${params.BROWSER}" +
                                " -Denv=${params.ENVIRONMENT}" +
                                " -Dheadless=${params.HEADLESS}" +
                                " -Dsurefire.reportNameSuffix=alfadock"
                            if (isUnix()) { sh cmd } else { bat cmd }
                        }
                    }
                }
            }
        }

        stage('GPN Suite') {
            steps {
                script {
                    def cmd = "mvn test" +
                        " -Dtestng.suite.file=gpn.xml" +
                        " -Dbrowser=${params.BROWSER}" +
                        " -Denv=${params.ENVIRONMENT}" +
                        " -Dheadless=${params.HEADLESS}" +
                        " -Dsurefire.reportNameSuffix=gpn"
                    if (isUnix()) { sh cmd } else { bat cmd }
                }
            }
        }

        stage('SonarQube Analysis') {
            // Requires: SONAR_TOKEN credential (Secret Text) and SONAR_HOST_URL
            // credential (Secret Text) configured in Jenkins Credentials store.
            // Skip this stage by setting SONAR_TOKEN to an empty string.
            when {
                expression { return env.SONAR_TOKEN != null && env.SONAR_TOKEN != '' }
            }
            steps {
                withCredentials([
                    string(credentialsId: 'sonar-token',    variable: 'SONAR_TOKEN'),
                    string(credentialsId: 'sonar-host-url', variable: 'SONAR_HOST_URL')
                ]) {
                    script {
                        def cmd = "mvn sonar:sonar" +
                            " -Dsonar.projectKey=autoFrameX" +
                            " -Dsonar.host.url=${SONAR_HOST_URL}" +
                            " -Dsonar.token=${SONAR_TOKEN}" +
                            " -Dsonar.coverage.jacoco.xmlReportPaths=target/site/jacoco/jacoco.xml"
                        if (isUnix()) { sh cmd } else { bat cmd }
                    }
                }
            }
        }
    }

    post {
        always {
            script {
                // Collect all JUnit XML results and apply quality gate
                def summary = junit(
                    testResults: '**/surefire-reports/*.xml',
                    allowEmptyResults: false
                )

                // Quality gate: fail build if failure rate exceeds threshold.
                // Threshold is 50% to accommodate RetryTest's intentional failures
                // in the Framework Unit Tests stage.
                // Downstream projects running real suites should lower this to 10-20%.
                if (summary.totalCount > 0) {
                    def failureRate = (summary.failCount * 100.0) / summary.totalCount
                    echo "Quality gate: ${summary.failCount}/${summary.totalCount} failed (${String.format('%.1f', failureRate)}%)"
                    if (failureRate > 50) {
                        currentBuild.result = 'FAILURE'
                        error("Quality gate failed: ${String.format('%.1f', failureRate)}% failure rate exceeds 50% threshold")
                    }
                }
            }

            archiveArtifacts(
                artifacts: 'reports/**/*.html, logs/test-events.json, target/surefire-reports/**',
                allowEmptyArchive: true
            )
        }

        success {
            echo 'All test suites executed successfully.'

            // ── Slack notification stub ──────────────────────────────────────
            // Requires: Jenkins Slack plugin + SLACK_WEBHOOK credential configured.
            // Uncomment to enable.
            //
            // slackSend(
            //     channel: '#automation-results',
            //     color: 'good',
            //     message: "PASSED: ${env.JOB_NAME} #${env.BUILD_NUMBER} | ${env.BUILD_URL}"
            // )

            // ── Email notification stub ──────────────────────────────────────
            // Requires: mail server configured in Jenkins global settings.
            // Uncomment to enable.
            //
            // mail(
            //     to: 'qa-team@example.com',
            //     subject: "PASSED: ${env.JOB_NAME} #${env.BUILD_NUMBER}",
            //     body: "All suites passed. See: ${env.BUILD_URL}"
            // )
        }

        failure {
            echo 'One or more test suites failed. Check archived reports.'

            // ── Slack notification stub ──────────────────────────────────────
            //
            // slackSend(
            //     channel: '#automation-results',
            //     color: 'danger',
            //     message: "FAILED: ${env.JOB_NAME} #${env.BUILD_NUMBER} | ${env.BUILD_URL}"
            // )

            // ── Email notification stub ──────────────────────────────────────
            //
            // mail(
            //     to: 'qa-team@example.com',
            //     subject: "FAILED: ${env.JOB_NAME} #${env.BUILD_NUMBER}",
            //     body: "Failures detected. See: ${env.BUILD_URL}"
            // )
        }

        unstable {
            echo 'Build is unstable — some tests were skipped or reported as flaky.'
        }
    }
}
