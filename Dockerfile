# Pull base image

FROM tomcat:7-jre8

EXPOSE 8080
ADD  workspace.war /usr/local/tomcat/webapps/workspace.war 

CMD ["/usr/local/tomcat/bin/catalina.sh","run"]







