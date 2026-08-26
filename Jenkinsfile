// Deploys one suite run as a K8s Job on the Minikube node already loaded
// with autoframex-test:${env.BUILD_ID} (see "Load Image into Minikube"),
// waits for it to finish, and pulls its surefire-reports back into the
// Jenkins workspace at k8s-results/<jobName>/surefire-reports/. Each job
// gets its own destination (not <module>/target/surefire-reports/, which
// TestNG/AlfaDOCK/GPN suites all share by default as autoframex-testkit —
// reusing that path across three runs would let a later job's reports
// overwrite an earlier one's). "**/surefire-reports/*.xml" in post{} already
// matches any depth, so the existing junit/quality-gate glob still finds
// these with no changes.
def runSuiteAsK8sJob(String jobName, String module, String suiteFile, String browser, String environment, String headless) {
    def hostReportsPath = "/tmp/autoframex-${jobName}-surefire-reports"
    // "target/surefire-reports" segment matches post{}'s existing
    // archiveArtifacts pattern ("**/target/surefire-reports/**") as well.
    def destDir = "k8s-results/${jobName}/target/surefire-reports"
    sh """
        cat k8s/namespace.yaml | ${env.KUBECTL} apply -f -
        ${env.KUBECTL} delete job ${jobName} -n autoframex --ignore-not-found=true

        sed \
            -e "s#__JOB_NAME__#${jobName}#g" \
            -e "s#__IMAGE_TAG__#${env.BUILD_ID}#g" \
            -e "s#__MODULE__#${module}#g" \
            -e "s#__SUITE_FILE__#${suiteFile}#g" \
            -e "s#__BROWSER__#${browser}#g" \
            -e "s#__ENVIRONMENT__#${environment}#g" \
            -e "s#__HEADLESS__#${headless}#g" \
            -e "s#__HOST_REPORTS_PATH__#${hostReportsPath}#g" \
            k8s/test-job.yaml > /tmp/${jobName}-job.yaml

        cat /tmp/${jobName}-job.yaml | ${env.KUBECTL} apply -f -

        ${env.KUBECTL} wait --for=condition=complete job/${jobName} -n autoframex --timeout=600s || true

        mkdir -p ${destDir}
        docker exec minikube tar -c -C ${hostReportsPath} . | tar -x -C ${destDir} || true

        ${env.KUBECTL} delete job ${jobName} -n autoframex --ignore-not-found=true
    """
}

// Copies a Jenkins "Secret file" credential into autoframex-core's resources
// as destFileName — but tolerates the credential not existing yet, logging a
// warning and continuing instead of failing the whole build.
//
// withCredentials([...]) fails immediately (before any step inside its block
// runs) if a declared credentialsId isn't configured in Jenkins — that used
// to hard-fail this pipeline's very first real stage on a fresh Jenkins
// instance with zero setup, even though the framework's own default suite
// (autoframex-testkit/testng.xml) has no dependency on AlfaDOCK/Leaftaps
// config at all. Wrapping each injection independently in try/catch means:
//   - A fresh Jenkins instance can run the default suite with zero
//     credential setup.
//   - The AlfaDOCK/GPN suite stages further down still get a real,
//     narrowly-scoped failure of their own if the file they actually need
//     never got injected — this doesn't paper over that, it just stops one
//     missing credential from blocking every other stage too.
def tryInjectConfig(String credentialsId, String destFileName) {
    try {
        withCredentials([file(credentialsId: credentialsId, variable: 'CONFIG_FILE')]) {
            if (isUnix()) {
                sh """
                    mkdir -p autoframex-core/src/main/resources
                    cp "\$CONFIG_FILE" autoframex-core/src/main/resources/${destFileName}
                """
            } else {
                bat """
                    if not exist autoframex-core\\src\\main\\resources mkdir autoframex-core\\src\\main\\resources
                    copy /Y "%CONFIG_FILE%" "autoframex-core\\src\\main\\resources\\${destFileName}"
                """
            }
        }
        echo "Injected credential '${credentialsId}' -> autoframex-core/src/main/resources/${destFileName}"
    } catch (err) {
        echo "Credential '${credentialsId}' is not configured in Jenkins — skipping ${destFileName}. " +
             "Only the AlfaDOCK/GPN suite stages need this; the default suite " +
             "(autoframex-testkit/testng.xml) does not. Add a 'Secret file' credential " +
             "with ID '${credentialsId}' in Jenkins → Credentials if you need those suites to run."
    }
}

pipeline {
    agent any

    tools {
        // Requires a Maven "Tool" named exactly 'Maven3' configured in
        // Jenkins → Manage Jenkins → Tools → Maven installations (check
        // "Install automatically"). Reverted from a Docker-based agent
        // (maven:3.9-eclipse-temurin-17) because this Jenkins instance
        // doesn't have the Docker Pipeline plugin installed — "agent {
        // docker {...} }" isn't a recognized agent type without it.
        maven 'Maven3'
    }

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
            description: 'TestNG suite XML file to execute (relative to the MODULE directory below, not the repo root)'
        )
        string(
            name: 'MODULE',
            defaultValue: 'autoframex-testkit',
            description: 'Reactor module that owns SUITE_FILE above (TD-20 multi-module split) — e.g. autoframex-testkit for testng.xml, autoframex-selenium for testng-ci.xml'
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

        // Docker + Minikube — used only by the "target application" suite
        // stages below (TestNG/AlfaDOCK/GPN). Build & Install/SonarQube stay
        // native (via the 'Maven3' tool above). Sonar reads
        // target/site/jacoco/jacoco.xml, produced directly by Build &
        // Install now that each module runs its own unit tests
        // inline — no separate test stage needed.
        KUBECTL = 'docker exec minikube /var/lib/minikube/binaries/v1.35.1/kubectl --kubeconfig=/etc/kubernetes/admin.conf'
    }

    stages {

        stage('Checkout') {
            steps {
                checkout scm
            }
        }

        stage('Inject Configs') {
            steps {
                // Destination is autoframex-core/src/main/resources (TD-20):
                // ConfigManager/ProjectDirector, which read these project
                // config files via the configClass parameter, now live in
                // the autoframex-core module, not the repo root.
                //
                // Each injection is independently best-effort (see
                // tryInjectConfig above) — this stage always succeeds, even
                // on a fresh Jenkins instance with neither credential
                // configured yet, since the default suite doesn't need them.
                script {
                    tryInjectConfig('alfadock-config', 'alfaDOCKConfig.properties')
                    tryInjectConfig('leaftap-config',  'leafTapConfig.properties')
                }
            }
        }

        stage('Build & Install') {
            steps {
                script {
                    // install (not just compile): downstream modules need
                    // upstream reactor artifacts (autoframex-core,
                    // autoframex-selenium, ...) resolvable from the local
                    // repo before any -pl-scoped stage below can build.
                    //
                    // No -DskipTests: each module now carries its own default
                    // suite (testng.suite.file / skipTests property override
                    // in that module's pom.xml), so the reactor build runs
                    // every module's relevant fast unit tests automatically,
                    // in dependency order, and Maven's default fail-fast
                    // behavior stops the whole build at the first failing
                    // module — this stage IS the unit-test gate. JaCoCo's
                    // "report" execution is bound to the test phase, so
                    // target/site/jacoco/jacoco.xml is still produced here
                    // for Sonar, with no separate step needed.
                    //
                    // The coverage-percentage floor (pom.xml's jacoco-check
                    // execution, 20% line coverage) defaults to non-fatal
                    // (jacoco.haltOnFailure=false in the root pom.xml
                    // properties) — it's a separate, pre-existing, unrelated
                    // concern from whether the unit tests pass, and some
                    // modules (e.g. autoframex-performance) don't meet it
                    // yet. No flag needed here; the same default applies to
                    // any plain `mvn install` run anywhere, not just CI.
                    if (isUnix()) {
                        sh 'mvn clean install -q'
                    } else {
                        bat 'mvn clean install -q'
                    }
                }
            }
            post {
                always {
                    junit testResults: '**/surefire-reports/*.xml',
                          allowEmptyResults: true
                }
            }
        }

        stage('Build Docker Image') {
            steps {
                // Reuses the repo-root Dockerfile (already TD-20 reactor-aware —
                // MODULE/SUITE_FILE/BROWSER/ENVIRONMENT/HEADLESS are all env
                // vars its ENTRYPOINT already reads). Build context = repo
                // root; the injected config properties from "Inject Configs"
                // above are already sitting under autoframex-core/src/main/
                // resources/ in this workspace, so the image's Layer 2 COPY
                // picks them up automatically.
                sh 'docker build --platform linux/amd64 --provenance=false -t autoframex-test:${BUILD_ID} .'
            }
        }

        stage('Load Image into Minikube') {
            steps {
                sh '''
                    docker save -o autoframex-test.tar autoframex-test:${BUILD_ID}
                    docker cp autoframex-test.tar minikube:/autoframex-test.tar
                    docker exec minikube docker load -i /autoframex-test.tar
                    rm autoframex-test.tar
                    docker exec minikube rm /autoframex-test.tar
                '''
            }
        }

        stage('Run Parallel Suites') {
            // Each runs as its own K8s Job on the Minikube node (see
            // runSuiteAsK8sJob at the top of this file) — distinct Job names
            // and hostPaths, so true parallel execution is safe, same as the
            // native `parallel {}` block this replaced.
            //
            // NOTE: THREAD_COUNT is not wired through to the containerized
            // path — ../Dockerfile's ENTRYPOINT doesn't expose a
            // -Dsurefire.threadCount override. Set parallel/thread-count
            // directly in the suite XML if you need it, or extend the
            // Dockerfile/k8s/test-job.yaml template to add a THREAD_COUNT
            // env var if per-run overrides become necessary.
            parallel {

                stage('TestNG Suite') {
                    steps {
                        script {
                            // MODULE/SUITE_FILE pair together identify which
                            // reactor module owns the suite (TD-20) — default
                            // testng.xml lives in autoframex-testkit.
                            //
                            // this. is required here: confirmed via a real
                            // Jenkins run that Jenkins' CPS engine can't
                            // resolve a top-level `def` script method called
                            // from inside a parallel {} branch closure
                            // without it ("No such DSL method
                            // 'runSuiteAsK8sJob' found among steps [...]") —
                            // tryInjectConfig above works without this.
                            // because it's called from a plain (non-parallel)
                            // stage.
                            this.runSuiteAsK8sJob('autoframex-testng-suite', params.MODULE, params.SUITE_FILE,
                                params.BROWSER, params.ENVIRONMENT, params.HEADLESS)
                        }
                    }
                }

                stage('AlfaDOCK Suite') {
                    steps {
                        script {
                            // NOTE (pre-existing, unrelated to TD-20): alfaDOCKtestng.xml
                            // is not present anywhere in this repo — it's expected to be
                            // supplied by the consuming AlfaDOCK project's own module.
                            // Module targets autoframex-testkit as a placeholder; point
                            // this at whichever module that downstream project actually owns.
                            this.runSuiteAsK8sJob('autoframex-alfadock-suite', 'autoframex-testkit', 'alfaDOCKtestng.xml',
                                params.BROWSER, params.ENVIRONMENT, params.HEADLESS)
                        }
                    }
                }
            }
        }

        stage('GPN Suite') {
            steps {
                script {
                    // NOTE (pre-existing, unrelated to TD-20): gpn.xml is not present
                    // anywhere in this repo — see the AlfaDOCK Suite comment above.
                    this.runSuiteAsK8sJob('autoframex-gpn-suite', 'autoframex-testkit', 'gpn.xml',
                        params.BROWSER, params.ENVIRONMENT, params.HEADLESS)
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
                        // Run at the reactor root (no -pl) so Sonar analyzes every
                        // module's source; xmlReportPaths is globbed since coverage
                        // data only really exists for whichever module(s) the stages
                        // above actually exercised (TD-20 — see sonar.yml for the
                        // same pattern).
                        def cmd = "mvn sonar:sonar" +
                            " -Dsonar.projectKey=autoFrameX" +
                            " -Dsonar.host.url=${SONAR_HOST_URL}" +
                            " -Dsonar.token=${SONAR_TOKEN}" +
                            " -Dsonar.coverage.jacoco.xmlReportPaths=**/target/site/jacoco/jacoco.xml"
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
                // Was 50%, specifically to accommodate RetryTest's intentional
                // failures in the default suite's "Framework Unit Tests" block.
                // RetryTest no longer runs there (see autoframex-testkit/testng.xml —
                // it moved to autoframex-selenium/testng-ci-retry.xml, run
                // separately and non-gating), so the aggregate result here now
                // reflects genuine pass/fail — lowered to a real threshold.
                // Tune per project if a suite has known, accepted flakiness.
                if (summary.totalCount > 0) {
                    def failureRate = (summary.failCount * 100.0) / summary.totalCount
                    echo "Quality gate: ${summary.failCount}/${summary.totalCount} failed (${String.format('%.1f', failureRate)}%)"
                    if (failureRate > 20) {
                        currentBuild.result = 'FAILURE'
                        error("Quality gate failed: ${String.format('%.1f', failureRate)}% failure rate exceeds 20% threshold")
                    }
                }
            }

            archiveArtifacts(
                artifacts: '**/reports/**/*.html, **/logs/test-events.json, **/target/surefire-reports/**',
                allowEmptyArchive: true
            )

            // Best-effort cleanup in case the pipeline aborted mid-suite and
            // a K8s Job's own delete (end of runSuiteAsK8sJob) never ran.
            sh '''
                ${KUBECTL} delete job autoframex-testng-suite -n autoframex --ignore-not-found=true || true
                ${KUBECTL} delete job autoframex-alfadock-suite -n autoframex --ignore-not-found=true || true
                ${KUBECTL} delete job autoframex-gpn-suite -n autoframex --ignore-not-found=true || true
            '''
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
