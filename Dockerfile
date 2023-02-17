FROM tomcat:9-jdk16-openjdk

MAINTAINER German Robayo <grobayo@mahisoft.com>

ARG CLIFF_PACKAGE=cliff-2.6.1
ARG CLAVIN_INDEX_VERSION=2020-11-25
ENV INDEX_PATH=/etc/cliff2/IndexDirectory

RUN apt-get update && \
    apt-get install -y git maven


RUN wget https://github.com/mediacloud/mediacloud-clavin-build-geonames-index/releases/download/$CLAVIN_INDEX_VERSION/IndexDirectory.tar.gz && \
    tar xfz IndexDirectory.tar.gz && \
    mkdir /etc/cliff2 && \
    mv IndexDirectory $INDEX_PATH

# when running locally this avoid downloading the index
#RUN mkdir -p $INDEX_PATH
#COPY IndexDirectory/* $INDEX_PATH

COPY webapp/target/$CLIFF_PACKAGE.war /usr/local/tomcat/webapps/

EXPOSE 8080
CMD ["catalina.sh", "run"]
