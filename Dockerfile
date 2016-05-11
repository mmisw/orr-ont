FROM jeanblanchard/tomcat:8

MAINTAINER Carlos Rueda <carueda@gmail.com>

RUN mkdir -p /opt/orr-ont-base-directory

COPY target/scala-2.11/orr-ont_2.11-0.3.1.war /opt/tomcat/webapps/ont.war
