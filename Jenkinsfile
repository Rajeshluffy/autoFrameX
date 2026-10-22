// K8s-Job-per-suite deploy: each stage below runs its own inline copy of
// this shell script (not a shared `def` function) — confirmed via three
// real Jenkins runs that this Jenkins/Pipeline-plugin combination can't
// resolve a top-level script method from inside ANY parallel execution
// context (declarative `parallel { stage {...} }`, scripted `parallel(map)`
// with or without an explicit `this.` receiver — all threw the identical
// "No such DSL method 'runSuiteAsK8sJob' found among steps [...]"). Plain
// `sh` steps + string interpolation are proven to work everywhere in this
// file, so that's what every suite-run stage below uses directly instead.
//
// Each job gets its own destination (not <module>/target/surefire-reports/,
// which TestNG/AlfaDOCK/GPN suites all share by default as autoframex-testkit
// — reusing that path across runs would let a later job's reports overwrite
// an earlier one's). "**/surefire-reports/*.xml" in post{} already matches
// any depth, so the existing junit/quality-gate glob still finds these.

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
            defaultValue: 'testng-build.xml',
            description: 'TestNG suite XML file to execute (relative to the MODULE directory below, not the repo root). Default testng-build.xml needs no target app; testng.xml also runs the Cucumber UI scenarios, which need a real app at the configured URL (localhost:8080 by default, nothing listens there in the K8s pod).'
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
        booleanParam(
            name: 'RUN_ALFADOCK_SUITE',
            defaultValue: false,
            description: 'Also run alfaDOCKtestng.xml as a K8s Job. Off by default: that suite file is supplied by the AlfaDOCK project, not this repo, so the Job fails without it.'
        )
        booleanParam(
            name: 'RUN_GPN_SUITE',
            defaultValue: false,
            description: 'Also run gpn.xml as a K8s Job. Off by default: that suite file is supplied by the GPN project, not this repo, so the Job fails without it.'
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
        // -i is required: without it, `docker exec` doesn't attach the
        // host's stdin to the container process, so every `cat foo.yaml |
        // ${env.KUBECTL} apply -f -` pipe silently delivers zero bytes —
        // confirmed via a real Jenkins run ("error: no objects passed to
        // apply") that this was broken before -i was added. Harmless for
        // the KUBECTL invocations that don't read stdin (delete/wait).
        //
        // The kubectl path contains minikube's Kubernetes version, which
        // changes whenever the minikube container is recreated (v1.35.1 went
        // missing after an upgrade to v1.37.0), so it is looked up in the
        // running container instead of hardcoded. The fallback only applies
        // when minikube can't be reached at all.
        KUBECTL_BIN = sh(returnStdout: true, script: '''
            p=$(docker exec minikube sh -c 'ls /var/lib/minikube/binaries/*/kubectl 2>/dev/null | sort -V | tail -n1') || true
            echo "${p:-/var/lib/minikube/binaries/v1.37.0/kubectl}"
        ''').trim()
        KUBECTL = "docker exec -i minikube ${env.KUBECTL_BIN} --kubeconfig=/etc/kubernetes/admin.conf"
    }

    stages {

        stage('Checkout') {
            steps {
                checkout scm
            }
        }

        stage('Validate Parameters') {
            // MODULE and SUITE_FILE are free-text and end up inside shell
            // scripts below (sed expressions, file paths). Anyone who can start
            // a build could otherwise smuggle shell syntax into them, so only
            // plain module / file names are accepted. BROWSER and ENVIRONMENT
            // are Jenkins choice parameters and already constrained.
            steps {
                script {
                    if (!(params.MODULE ==~ /autoframex-[a-z0-9-]+/)) {
                        error("MODULE '${params.MODULE}' must look like autoframex-<name> (letters, digits, '-')")
                    }
                    if (!(params.SUITE_FILE ==~ /[A-Za-z0-9_.-]+\.xml/)) {
                        error("SUITE_FILE '${params.SUITE_FILE}' must be a plain *.xml file name (no paths or shell characters)")
                    }
                    if (!(params.THREAD_COUNT ==~ /[0-9]{1,3}/)) {
                        error("THREAD_COUNT '${params.THREAD_COUNT}' must be a number")
                    }
                }
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
                    //
                    // ENVIRONMENT is blanked for this build only: Jenkins exports
                    // the job parameter as an env var, and ConfigResolver ranks
                    // env vars above -D properties, so a build with ENVIRONMENT=qa
                    // overrode the -Denvironment=prod that
                    // BrowserSecurityPolicyTest sets. The parameter is the target
                    // of the suite stages below, not of the unit tests.
                    withEnv(['ENVIRONMENT=']) {
                        if (isUnix()) {
                            sh 'mvn clean install -q'
                        } else {
                            bat 'mvn clean install -q'
                        }
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
                // root. The credential files injected by "Inject Configs" are
                // deliberately NOT part of the image: .dockerignore excludes
                // them (**/alfaDOCKConfig.properties, **/leafTapConfig.properties)
                // so secrets never land in an image layer. The default testng.xml
                // suite doesn't need them. A suite that does (AlfaDOCK / Leaftaps)
                // must get its file at runtime, e.g. a Kubernetes Secret mounted
                // into the Job's pod -- not implemented yet, see RUN_ALFADOCK_SUITE.
                sh 'docker build --platform linux/amd64 --provenance=false -t autoframex-test:${BUILD_ID} .'
            }
        }

        stage('Load Image into Minikube') {
            // minikube's container runtime depends on how the cluster was created:
            // with the docker runtime `docker load` works inside the node, but with
            // containerd (minikube v1.37.0 here) there is no docker daemon in the
            // node ("failed to connect to the docker API ... docker.sock") and the
            // image has to go into containerd's k8s.io namespace -- the one the
            // kubelet reads -- with ctr. Pick whichever the node actually has.
            steps {
                sh '''
                    docker save -o autoframex-test.tar autoframex-test:${BUILD_ID}
                    docker cp autoframex-test.tar minikube:/autoframex-test.tar
                    docker exec minikube sh -c 'if docker info >/dev/null 2>&1; then docker load -i /autoframex-test.tar; else ctr --namespace=k8s.io images import /autoframex-test.tar; fi'
                    rm autoframex-test.tar
                    docker exec minikube rm /autoframex-test.tar
                '''
            }
        }

        stage('Run Parallel Suites') {
            // Each runs as its own K8s Job on the Minikube node (distinct Job
            // names and hostPaths, so true parallel execution is safe). See the
            // comment at the top of this file for why the shell script is
            // inlined here instead of a shared function.
            //
            // AlfaDOCK is opt-in (RUN_ALFADOCK_SUITE): alfaDOCKtestng.xml is not
            // in this repo, it is supplied by the consuming AlfaDOCK project.
            // Leaving it on by default meant a guaranteed-failing Job on every run.
            //
            // NOTE: THREAD_COUNT is not wired through to the containerized
            // path -- ../Dockerfile's ENTRYPOINT doesn't expose a
            // -Dsurefire.threadCount override. Set parallel/thread-count
            // directly in the suite XML if you need it, or extend the
            // Dockerfile/k8s/test-job.yaml template to add a THREAD_COUNT
            // env var if per-run overrides become necessary.
            steps {
                script {
                    def branches = [:]

                    branches['TestNG Suite'] = {
                        // MODULE/SUITE_FILE pair together identify which reactor
                        // module owns the suite (TD-20) -- default testng.xml lives
                        // in autoframex-testkit. Both are validated in the
                        // 'Validate Parameters' stage before they reach this script.
                        sh """
                            cat k8s/namespace.yaml | ${env.KUBECTL} apply -f -
                            ${env.KUBECTL} delete job autoframex-testng-suite -n autoframex --ignore-not-found=true

                            sed \
                                -e "s#__JOB_NAME__#autoframex-testng-suite#g" \
                                -e "s#__IMAGE_TAG__#${env.BUILD_ID}#g" \
                                -e "s#__MODULE__#${params.MODULE}#g" \
                                -e "s#__SUITE_FILE__#${params.SUITE_FILE}#g" \
                                -e "s#__BROWSER__#${params.BROWSER}#g" \
                                -e "s#__ENVIRONMENT__#${params.ENVIRONMENT}#g" \
                                -e "s#__HEADLESS__#${params.HEADLESS}#g" \
                                -e "s#__HOST_REPORTS_PATH__#/tmp/autoframex-autoframex-testng-suite-surefire-reports#g" \
                                k8s/test-job.yaml > /tmp/autoframex-testng-suite-job.yaml

                            cat /tmp/autoframex-testng-suite-job.yaml | ${env.KUBECTL} apply -f -

                            # Wait for the Job to reach Complete OR Failed, whichever comes first.
                            # `kubectl wait --for=condition=complete` alone sits out its whole timeout
                            # when the Job fails (backoffLimit: 0), so a broken suite used to cost
                            # 10 minutes before anything noticed. The 10 minute ceiling is a wall-clock
                            # deadline and each kubectl call is capped at 20s, so a hung API server
                            # can't stretch it (a counted loop did: run #6 sat 53 minutes).
                            JOB_RESULT=timeout
                            DEADLINE=\$(( \$(date +%s) + 600 ))
                            while [ \$(date +%s) -lt \$DEADLINE ]; do
                                SUCCEEDED=\$(timeout 20 ${env.KUBECTL} get job autoframex-testng-suite -n autoframex -o jsonpath='{.status.succeeded}') || true
                                FAILED=\$(timeout 20 ${env.KUBECTL} get job autoframex-testng-suite -n autoframex -o jsonpath='{.status.failed}') || true
                                if [ "\${SUCCEEDED:-0}" -ge 1 ]; then JOB_RESULT=complete; break; fi
                                if [ "\${FAILED:-0}" -ge 1 ]; then JOB_RESULT=failed; break; fi
                                sleep 5
                            done
                            echo "autoframex-testng-suite finished: \$JOB_RESULT"
                            if [ "\$JOB_RESULT" != "complete" ]; then
                                ${env.KUBECTL} logs job/autoframex-testng-suite -n autoframex --tail=60 || true
                            fi

                            mkdir -p k8s-results/autoframex-testng-suite/target/surefire-reports
                            docker exec minikube tar -c -C /tmp/autoframex-autoframex-testng-suite-surefire-reports . | tar -x -C k8s-results/autoframex-testng-suite/target/surefire-reports || true

                            ${env.KUBECTL} delete job autoframex-testng-suite -n autoframex --ignore-not-found=true

                            # A suite that produced no report XML never really ran. Fail this stage
                            # loudly instead of letting the quality gate see "nothing to count".
                            if ! ls k8s-results/autoframex-testng-suite/target/surefire-reports/*.xml >/dev/null 2>&1; then
                                echo "ERROR: autoframex-testng-suite produced no surefire XML (job result: \$JOB_RESULT)"
                                exit 1
                            fi
                        """
                    }

                    if (params.RUN_ALFADOCK_SUITE) {
                        branches['AlfaDOCK Suite'] = {
                            // Module targets autoframex-testkit as a placeholder; point
                            // this at whichever module the AlfaDOCK project actually owns.
                            sh """
                                cat k8s/namespace.yaml | ${env.KUBECTL} apply -f -
                                ${env.KUBECTL} delete job autoframex-alfadock-suite -n autoframex --ignore-not-found=true

                                sed \
                                    -e "s#__JOB_NAME__#autoframex-alfadock-suite#g" \
                                    -e "s#__IMAGE_TAG__#${env.BUILD_ID}#g" \
                                    -e "s#__MODULE__#autoframex-testkit#g" \
                                    -e "s#__SUITE_FILE__#alfaDOCKtestng.xml#g" \
                                    -e "s#__BROWSER__#${params.BROWSER}#g" \
                                    -e "s#__ENVIRONMENT__#${params.ENVIRONMENT}#g" \
                                    -e "s#__HEADLESS__#${params.HEADLESS}#g" \
                                    -e "s#__HOST_REPORTS_PATH__#/tmp/autoframex-autoframex-alfadock-suite-surefire-reports#g" \
                                    k8s/test-job.yaml > /tmp/autoframex-alfadock-suite-job.yaml

                                cat /tmp/autoframex-alfadock-suite-job.yaml | ${env.KUBECTL} apply -f -

                                # Wait for the Job to reach Complete OR Failed, whichever comes first.
                                # `kubectl wait --for=condition=complete` alone sits out its whole timeout
                                # when the Job fails (backoffLimit: 0), so a broken suite used to cost
                                # 10 minutes before anything noticed. The 10 minute ceiling is a wall-clock
                                # deadline and each kubectl call is capped at 20s, so a hung API server
                                # can't stretch it (a counted loop did: run #6 sat 53 minutes).
                                JOB_RESULT=timeout
                                DEADLINE=\$(( \$(date +%s) + 600 ))
                                while [ \$(date +%s) -lt \$DEADLINE ]; do
                                    SUCCEEDED=\$(timeout 20 ${env.KUBECTL} get job autoframex-alfadock-suite -n autoframex -o jsonpath='{.status.succeeded}') || true
                                    FAILED=\$(timeout 20 ${env.KUBECTL} get job autoframex-alfadock-suite -n autoframex -o jsonpath='{.status.failed}') || true
                                    if [ "\${SUCCEEDED:-0}" -ge 1 ]; then JOB_RESULT=complete; break; fi
                                    if [ "\${FAILED:-0}" -ge 1 ]; then JOB_RESULT=failed; break; fi
                                    sleep 5
                                done
                                echo "autoframex-alfadock-suite finished: \$JOB_RESULT"
                                if [ "\$JOB_RESULT" != "complete" ]; then
                                    ${env.KUBECTL} logs job/autoframex-alfadock-suite -n autoframex --tail=60 || true
                                fi

                                mkdir -p k8s-results/autoframex-alfadock-suite/target/surefire-reports
                                docker exec minikube tar -c -C /tmp/autoframex-autoframex-alfadock-suite-surefire-reports . | tar -x -C k8s-results/autoframex-alfadock-suite/target/surefire-reports || true

                                ${env.KUBECTL} delete job autoframex-alfadock-suite -n autoframex --ignore-not-found=true

                                # A suite that produced no report XML never really ran. Fail this stage
                                # loudly instead of letting the quality gate see "nothing to count".
                                if ! ls k8s-results/autoframex-alfadock-suite/target/surefire-reports/*.xml >/dev/null 2>&1; then
                                    echo "ERROR: autoframex-alfadock-suite produced no surefire XML (job result: \$JOB_RESULT)"
                                    exit 1
                                fi
                            """
                        }
                    }

                    parallel(branches)
                }
            }
        }

        stage('GPN Suite') {
            // Opt-in for the same reason as AlfaDOCK above: gpn.xml is not in this repo.
            when { expression { return params.RUN_GPN_SUITE } }
            steps {
                script {
                    sh """
                        cat k8s/namespace.yaml | ${env.KUBECTL} apply -f -
                        ${env.KUBECTL} delete job autoframex-gpn-suite -n autoframex --ignore-not-found=true

                        sed \
                            -e "s#__JOB_NAME__#autoframex-gpn-suite#g" \
                            -e "s#__IMAGE_TAG__#${env.BUILD_ID}#g" \
                            -e "s#__MODULE__#autoframex-testkit#g" \
                            -e "s#__SUITE_FILE__#gpn.xml#g" \
                            -e "s#__BROWSER__#${params.BROWSER}#g" \
                            -e "s#__ENVIRONMENT__#${params.ENVIRONMENT}#g" \
                            -e "s#__HEADLESS__#${params.HEADLESS}#g" \
                            -e "s#__HOST_REPORTS_PATH__#/tmp/autoframex-autoframex-gpn-suite-surefire-reports#g" \
                            k8s/test-job.yaml > /tmp/autoframex-gpn-suite-job.yaml

                        cat /tmp/autoframex-gpn-suite-job.yaml | ${env.KUBECTL} apply -f -

                        # Wait for the Job to reach Complete OR Failed, whichever comes first.
                        # `kubectl wait --for=condition=complete` alone sits out its whole timeout
                        # when the Job fails (backoffLimit: 0), so a broken suite used to cost
                        # 10 minutes before anything noticed. The 10 minute ceiling is a wall-clock
                        # deadline and each kubectl call is capped at 20s, so a hung API server
                        # can't stretch it (a counted loop did: run #6 sat 53 minutes).
                        JOB_RESULT=timeout
                        DEADLINE=\$(( \$(date +%s) + 600 ))
                        while [ \$(date +%s) -lt \$DEADLINE ]; do
                            SUCCEEDED=\$(timeout 20 ${env.KUBECTL} get job autoframex-gpn-suite -n autoframex -o jsonpath='{.status.succeeded}') || true
                            FAILED=\$(timeout 20 ${env.KUBECTL} get job autoframex-gpn-suite -n autoframex -o jsonpath='{.status.failed}') || true
                            if [ "\${SUCCEEDED:-0}" -ge 1 ]; then JOB_RESULT=complete; break; fi
                            if [ "\${FAILED:-0}" -ge 1 ]; then JOB_RESULT=failed; break; fi
                            sleep 5
                        done
                        echo "autoframex-gpn-suite finished: \$JOB_RESULT"
                        if [ "\$JOB_RESULT" != "complete" ]; then
                            ${env.KUBECTL} logs job/autoframex-gpn-suite -n autoframex --tail=60 || true
                        fi

                        mkdir -p k8s-results/autoframex-gpn-suite/target/surefire-reports
                        docker exec minikube tar -c -C /tmp/autoframex-autoframex-gpn-suite-surefire-reports . | tar -x -C k8s-results/autoframex-gpn-suite/target/surefire-reports || true

                        ${env.KUBECTL} delete job autoframex-gpn-suite -n autoframex --ignore-not-found=true

                        # A suite that produced no report XML never really ran. Fail this stage
                        # loudly instead of letting the quality gate see "nothing to count".
                        if ! ls k8s-results/autoframex-gpn-suite/target/surefire-reports/*.xml >/dev/null 2>&1; then
                            echo "ERROR: autoframex-gpn-suite produced no surefire XML (job result: \$JOB_RESULT)"
                            exit 1
                        fi
                    """
                }
            }
        }

        stage('SonarQube Analysis') {
            // Requires: 'sonar-token' and 'sonar-host-url' credentials (Secret
            // Text) in the Jenkins Credentials store. The stage is skipped when
            // either is missing. (It used to test env.SONAR_TOKEN, which only
            // exists *inside* withCredentials below, so the stage never ran.)
            when {
                expression {
                    def configured = false
                    try {
                        withCredentials([
                            string(credentialsId: 'sonar-token',    variable: 'PROBE_TOKEN'),
                            string(credentialsId: 'sonar-host-url', variable: 'PROBE_HOST')
                        ]) {
                            configured = true
                        }
                    } catch (err) {
                        echo "SonarQube credentials not configured -- skipping Sonar analysis."
                    }
                    return configured
                }
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
                        //
                        // The token is read by the shell from the environment
                        // (single-quoted so Groovy never interpolates it), keeping
                        // it out of the Groovy string and the build's command log.
                        if (isUnix()) {
                            sh 'mvn sonar:sonar -Dsonar.projectKey=autoFrameX -Dsonar.host.url="$SONAR_HOST_URL" -Dsonar.token="$SONAR_TOKEN" -Dsonar.coverage.jacoco.xmlReportPaths=**/target/site/jacoco/jacoco.xml'
                        } else {
                            bat 'mvn sonar:sonar -Dsonar.projectKey=autoFrameX -Dsonar.host.url="%SONAR_HOST_URL%" -Dsonar.token="%SONAR_TOKEN%" -Dsonar.coverage.jacoco.xmlReportPaths=**/target/site/jacoco/jacoco.xml'
                        }
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
