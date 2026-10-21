FROM jenkins/jenkins:lts-jdk21

USER root

# Docker CLI only (the daemon is PC-2's, reached through the mounted /var/run/docker.sock).
# On Debian 13 (trixie) the `docker.io` package ships only dockerd; the `docker` command is in `docker-cli`.
# Needed by the Jenkinsfile's `docker build` / `docker save` / `docker exec minikube ...` steps.
RUN apt-get update \
    && apt-get install -y --no-install-recommends docker-cli \
    && rm -rf /var/lib/apt/lists/*
