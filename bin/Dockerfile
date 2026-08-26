FROM maven:3.9-eclipse-temurin-17

LABEL org.opencontainers.image.title="autoFrameX"
LABEL org.opencontainers.image.description="autoFrameX test framework with Chrome — reusable base image for downstream projects"
LABEL org.opencontainers.image.source="https://github.com/your-org/autoFrameX"

# Install Chrome via official Google .deb (stable, Debian-compatible base image)
RUN apt-get update -qq && \
    apt-get install -y --no-install-recommends \
        wget \
        gnupg \
        ca-certificates \
        fonts-liberation \
        libappindicator3-1 \
        libasound2 \
        libatk-bridge2.0-0 \
        libatk1.0-0 \
        libcups2 \
        libdbus-1-3 \
        libgdk-pixbuf2.0-0 \
        libnspr4 \
        libnss3 \
        libx11-xcb1 \
        libxcomposite1 \
        libxdamage1 \
        libxrandr2 \
        xdg-utils && \
    wget -q -O /tmp/google-chrome.deb \
        https://dl.google.com/linux/direct/google-chrome-stable_current_amd64.deb && \
    apt-get install -y --no-install-recommends /tmp/google-chrome.deb && \
    rm /tmp/google-chrome.deb && \
    apt-get clean && \
    rm -rf /var/lib/apt/lists/*

RUN google-chrome --version

WORKDIR /app

# Layer 1: dependency cache — every module's pom.xml, so `go-offline` sees the
# whole reactor graph (TD-20: 8-module split). Only invalidated when a pom
# changes, not on every source edit.
COPY pom.xml .
COPY autoframex-core/pom.xml autoframex-core/
COPY autoframex-selenium/pom.xml autoframex-selenium/
COPY autoframex-api/pom.xml autoframex-api/
COPY autoframex-database/pom.xml autoframex-database/
COPY autoframex-cucumber/pom.xml autoframex-cucumber/
COPY autoframex-performance/pom.xml autoframex-performance/
COPY autoframex-security/pom.xml autoframex-security/
COPY autoframex-testkit/pom.xml autoframex-testkit/
RUN mvn dependency:go-offline -q

# Layer 2: source — invalidated on any source change. Suite XMLs now live
# inside the modules that own them (testng.xml under autoframex-testkit,
# testng-ci.xml under autoframex-selenium) so a plain `COPY src/` no longer
# covers everything — copy each module's full tree instead.
COPY autoframex-core/ autoframex-core/
COPY autoframex-selenium/ autoframex-selenium/
COPY autoframex-api/ autoframex-api/
COPY autoframex-database/ autoframex-database/
COPY autoframex-cucumber/ autoframex-cucumber/
COPY autoframex-performance/ autoframex-performance/
COPY autoframex-security/ autoframex-security/
COPY autoframex-testkit/ autoframex-testkit/

# Install (not just compile) at image build time: downstream modules
# (e.g. autoframex-testkit) depend on upstream reactor artifacts
# (autoframex-core, autoframex-selenium, ...) being present in the local
# repo, not just compiled in-place — mirrors the CI workflows' install step.
RUN mvn clean install -DskipTests -Djacoco.skip=true -q

# Runtime defaults — all overridable via --env at docker run or in docker-compose.yml
ENV BROWSER=chrome
ENV HEADLESS=true
ENV ENVIRONMENT=qa
ENV SUITE_FILE=testng.xml
# Reactor module that owns SUITE_FILE above (TD-20). testng.xml (the
# aggregate suite spanning every module) lives in autoframex-testkit, so
# that's the default; override to e.g. autoframex-selenium to run
# testng-ci.xml instead.
ENV MODULE=autoframex-testkit

# Mount these volumes to retrieve test artifacts from the host after the
# container exits. Paths match the MODULE default above (autoframex-testkit)
# since Surefire's working directory is the invoking module's own basedir,
# not the reactor root — if you override MODULE at `docker run`, mount
# /app/<your-module>/{reports,logs,target/surefire-reports} instead.
VOLUME ["/app/autoframex-testkit/reports", "/app/autoframex-testkit/logs", "/app/autoframex-testkit/target/surefire-reports"]

# Shell-form ENTRYPOINT so ${ENV_VAR} values expand at container runtime
ENTRYPOINT mvn test \
    -pl ${MODULE} \
    -Dtestng.suite.file=${SUITE_FILE} \
    -Dbrowser=${BROWSER} \
    -Denv=${ENVIRONMENT} \
    -Dheadless=${HEADLESS}
