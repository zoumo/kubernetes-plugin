FROM jenkins:2.32.3-alpine

USER root
WORKDIR /var/jenkins_home


ENV VERSION 0.11.1

COPY install-plugins.sh /usr/local/bin/install-plugins.sh

# RUN curl -fSL https://github.com/caicloud/kubernetes-plugin/releases/download/kubernetes-${VERSION}/kubernetes.hpi -o ${JENKINS_HOME}/kubernetes.hpi && \
#     install-plugins.sh ${JENKINS_HOME}/kubernetes.hpi

COPY target/kubernetes.hpi /var/jenkins_home/kubernetes.hpi

RUN install-plugins.sh /var/jenkins_home/kubernetes.hpi \ 
                       workflow-aggregator \
                       pipeline-stage-view \
                       git \
                       credentials-binding \
                       ws-cleanup \
                       ant \
                       ldap \
                       email-ext

COPY src/main/docker/master-executors.groovy /usr/share/jenkins/ref/init.groovy.d/
