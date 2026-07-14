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

# Layer 1: dependency cache — only invalidated when pom.xml changes
COPY pom.xml .
RUN mvn dependency:go-offline -q

# Layer 2: source — invalidated on any source change
COPY src/ src/
COPY testng.xml .
COPY testng-ci.xml .

# Compile at image build time to validate source and warm the build cache
RUN mvn clean compile -q

# Runtime defaults — all overridable via --env at docker run or in docker-compose.yml
ENV BROWSER=chrome
ENV HEADLESS=true
ENV ENVIRONMENT=qa
ENV SUITE_FILE=testng.xml

# Mount these volumes to retrieve test artifacts from the host after the container exits
VOLUME ["/app/reports", "/app/logs", "/app/target/surefire-reports"]

# Shell-form ENTRYPOINT so ${ENV_VAR} values expand at container runtime
ENTRYPOINT mvn test \
    -Dtestng.suite.file=${SUITE_FILE} \
    -Dbrowser=${BROWSER} \
    -Denv=${ENVIRONMENT} \
    -Dheadless=${HEADLESS}
