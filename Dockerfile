FROM jenkins:2.19.4-alpine

USER root

COPY install-plugins.sh /usr/local/bin/install-plugins.sh

USER jenkins

ENV VERSION 0.11.1

RUN curl -fSL https://github.com/caicloud/kubernetes-plugin/releases/download/kubernetes-${VERSION}/kubernetes.hpi -o ${JENKINS_HOME}/kubernetes.hpi && \
    install-plugins.sh ${JENKINS_HOME}/kubernetes.hpi

COPY src/main/docker/master-executors.groovy /usr/share/jenkins/ref/init.groovy.d/
