FROM tomcat:7-jre8

EXPOSE 8080
RUN rm -rf webapps/*
ADD target/simple-load-balancer.war webapps/ROOT.war
COPY run.sh /

ENTRYPOINT ["/run.sh"]

# Usage:
#
# build:
# docker build -t simple_load_balancer .
#
# run (use comma-separated list of upstream urls as argument):
# docker run -d --name simple_load_balancer -p 8089:8080 --link exposed_name1:container_name1 --link exposed_name2:container_name2 simple_load_balancer http://exposed_name1:8080,http://exposed_name2:8080
#
# use:
# curl http://docker:8089/path/to/resource
