FROM tomcat:8.0

MAINTAINER Carlos Rueda <carueda@gmail.com>

RUN mkdir -p /opt/orr-ont-base-directory

COPY target/scala-2.11/orr-ont_2.11-0.3.1.war /usr/local/tomcat/webapps/ont.war
