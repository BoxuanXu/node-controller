FROM metersphere/fabric8-java-alpine-openjdk8-jre

LABEL maintainer="FIT2CLOUD <support@fit2cloud.com>"

ARG MS_VERSION=dev
ARG DEPENDENCY=target/dependency

COPY ${DEPENDENCY}/BOOT-INF/lib /opt/lib
COPY ${DEPENDENCY}/META-INF /opt/META-INF
COPY ${DEPENDENCY}/BOOT-INF/classes /opt

ENV JAVA_CLASSPATH=/opt/lib/*
ENV JAVA_MAIN_CLASS=io.metersphere.Application
ENV AB_OFF=true
ENV MS_VERSION=${MS_VERSION}
ENV JAVA_OPTIONS=-Dfile.encoding=utf-8

CMD ["/deployments/run-java.sh"]
