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

        stage('Execute TestNG Suite') {
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

        stage('Execute AlfaDOCK Suite') {
            steps {
                script {
                    if (isUnix()) {
                        sh "mvn test -Dtestng.suite.file=alfaDOCKtestng.xml"
                    } else {
                        bat "mvn test -Dtestng.suite.file=alfaDOCKtestng.xml"
                    }
                }
            }
<<<<<<< HEAD
         }
      }
   }
   
   post {
      always {
         // Archive all HTML reports and supplementary files (images, css, etc.)
         archiveArtifacts artifacts: 'reports/**/*', allowEmptyArchive: true
         
         // Publish all Surefire reports (from all suites)
         junit testResults: '**/surefire-reports/*.xml', allowEmptyResults: false
      }
      
      success {
         echo '✅ All test suites executed successfully!'
      }
      
      failure {
         echo '❌ Some tests failed. Check reports.'
      }
   }
}
=======
        }
    }

    post {
        always {
            // Archive the Extent Reports HTML files
            archiveArtifacts artifacts: 'reports/**/*.html', allowEmptyArchive: true

            // Publish JUnit XML results for BOTH suites.
            //
            // ── Why the old path failed ──────────────────────────────────────
            // junit 'target/surefire-reports/testng-results.xml'  ← WRONG
            //
            // Maven Surefire does NOT create a file called testng-results.xml.
            // It generates one XML per test class, named TEST-<fully.qualified.ClassName>.xml,
            // inside target/surefire-reports/. Both mvn runs accumulate into that
            // same directory (no clean between runs) because each suite tests
            // different classes — the files are never overwritten.
            //
            // ── Correct pattern ──────────────────────────────────────────────
            // target/surefire-reports/*.xml  →  picks up all TEST-*.xml files
            //                                    from both suite runs.
            junit testResults: 'target/surefire-reports/*.xml',
                  allowEmptyResults: false
        }
    }
}
>>>>>>> parent of 43777b2 (jenkins pipeline update)
