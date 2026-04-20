pipeline {
    agent any

    // Parameters block removed since both suites are run concurrently now.

    environment {
        // These environment variables will be picked up by ProjectDirector
        // and override the properties file values.
        BROWSER = 'chrome'
        ENVIRONMENT = 'qa'
        HEADLESS = 'false'
        // Define JAVA_HOME explicitly for the Jenkins environment
        JAVA_HOME = 'C:\\Program Files\\Java\\jdk-17'
    }

    stages {
        stage('Checkout') {
            steps {
                checkout scm
            }
        }

        stage('Inject Configs') {
            steps {
                // Download Secret files and place them into the resource directory dynamically
                withCredentials([
                    file(credentialsId: 'alfadock-config', variable: 'ALFA_CONFIG'),
                    file(credentialsId: 'leaftap-config', variable: 'LEAF_CONFIG')
                ]) {
                    script {
                        if (isUnix()) {
                            sh "mkdir -p src/main/resources"
                            sh "cp \$ALFA_CONFIG src/main/resources/alfaDOCKConfig.properties"
                            sh "cp \$LEAF_CONFIG src/main/resources/leafTapConfig.properties"
                        } else {
                            bat "if not exist src\\main\\resources mkdir src\\main\\resources"
                            bat "copy /Y \"%ALFA_CONFIG%\" \"src\\main\\resources\\alfaDOCKConfig.properties\""
                            bat "copy /Y \"%LEAF_CONFIG%\" \"src\\main\\resources\\leafTapConfig.properties\""
                        }
                    }
                }
            }
        }

        stage('Execute Test Suites') {
            parallel {
                stage('TestNG Suite') {
                    steps {
                        script {
                            if (isUnix()) {
                                sh "mvn test -Dtestng.suite.file=testng-test.xml"
                            } else {
                                bat "mvn test -Dtestng.suite.file=testng-test.xml"
                            }
                        }
                    }
                }
                stage('AlfaDOCK Suite') {
                    steps {
                        script {
                            if (isUnix()) {
                                sh "mvn test -Dtestng.suite.file=alfaDOCKtestng.xml"
                            } else {
                                bat "mvn test -Dtestng.suite.file=alfaDOCKtestng.xml"
                            }
                        }
                    }
                }
            }
        }
    }

    post {
        always {
            // Archive the Extent Reports HTML file
            archiveArtifacts artifacts: 'reports/*.html', allowEmptyArchive: true
            
            // Publish TestNG XML reports
            junit 'target/surefire-reports/testng-results.xml'
        }
    }
}
