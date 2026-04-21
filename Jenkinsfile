pipeline {
   agent any
   
   environment {
      BROWSER = 'chrome'
      ENVIRONMENT = 'qa'
      HEADLESS = 'false'
      
      JAVA_HOME = 'C:\\Program Files\\Java\\jdk-17'
      PATH = "${JAVA_HOME}\\bin;${env.PATH}"
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
            file(credentialsId: 'leaftap-config', variable: 'LEAF_CONFIG')
            ]) {
               script {
                  if (isUnix()) {
                     sh '''
                     mkdir -p src/main/resources
                     cp $ALFA_CONFIG src/main/resources/alfaDOCKConfig.properties
                     cp $LEAF_CONFIG src/main/resources/leafTapConfig.properties
                     '''
                  } else {
                     bat '''
                     if not exist src\\main\\resources mkdir src\\main\\resources
                     copy /Y "%ALFA_CONFIG%" "src\\main\\resources\\alfaDOCKConfig.properties"
                     copy /Y "%LEAF_CONFIG%" "src\\main\\resources\\leafTapConfig.properties"
                     '''
                  }
               }
            }
         }
      }
      
      stage('Run Parallel Suites') {
         parallel {
            
            stage('TestNG Suite') {
               steps {
                  script {
                     if (isUnix()) {
                        sh '''
                        mvn clean test \
                        -Dtestng.suite.file=testng-test.xml \
                        -Dsurefire.reportNameSuffix=testng
                        '''
                     } else {
                        bat '''
                        mvn clean test ^
                        -Dtestng.suite.file=testng-test.xml ^
                        -Dsurefire.reportNameSuffix=testng
                        '''
                     }
                  }
               }
            }
            
            stage('AlfaDOCK Suite') {
               steps {
                  script {
                     if (isUnix()) {
                        sh '''
                        mvn clean test \
                        -Dtestng.suite.file=alfaDOCKtestng.xml \
                        -Dsurefire.reportNameSuffix=alfadock
                        '''
                     } else {
                        bat '''
                        mvn clean test ^
                        -Dtestng.suite.file=alfaDOCKtestng.xml ^
                        -Dsurefire.reportNameSuffix=alfadock
                        '''
                     }
                  }
               }
            }
         }
      }
      
      stage('GPN Suite') {
         steps {
            script {
               if (isUnix()) {
                  sh '''
                  mvn test \
                  -Dtestng.suite.file=gpn.xml \
                  -Dsurefire.reportNameSuffix=gpn
                  '''
               } else {
                  bat '''
                  mvn test ^
                  -Dtestng.suite.file=gpn.xml ^
                  -Dsurefire.reportNameSuffix=gpn
                  '''
               }
            }
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