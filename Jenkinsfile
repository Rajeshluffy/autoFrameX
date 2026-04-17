pipeline {
    agent any

    environment {
        // These environment variables will be picked up by ProjectDirector
        // and override the properties file values.
        BROWSER = 'chrome'
        ENVIRONMENT = 'qa'
        HEADLESS = 'true'
    }

    stages {
        stage('Checkout') {
            steps {
                checkout scm
            }
        }

        stage('Build & Test') {
            steps {
                // To safely pass credentials without exposing them in pipeline scripts:
                // Use Jenkins Credentials Binding plugin
                withCredentials([
                    usernamePassword(credentialsId: 'leaftaps-creds', passwordVariable: 'APP_PASSWORD', usernameVariable: 'APP_USERNAME'),
                    usernamePassword(credentialsId: 'leaftaps-db-creds', passwordVariable: 'DB_PASSWORD', usernameVariable: 'DB_USERNAME')
                ]) {
                    // Maven will run TestNG tests, dynamically using the environment variables
                    sh 'mvn clean test -Dtestng.suite.file=testng-parallel.xml'
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
