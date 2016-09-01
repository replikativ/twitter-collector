FROM ubuntu:xenial

# Update the APT cache
RUN apt-get update

# Install and setup project dependencies
RUN apt-get install -y curl git wget unzip

# prepare for Java download
RUN apt-get install -y software-properties-common
RUN apt-get -y install openjdk-8-jdk
ENV JAVA_HOME /usr/lib/jvm/java-8-openjdk-amd64

# fix wget
RUN export HTTP_CLIENT="wget --no-check-certificate -O"

# grab leiningen
RUN wget https://raw.github.com/technomancy/leiningen/stable/bin/lein -O /usr/local/bin/lein
RUN chmod +x /usr/local/bin/lein
ENV LEIN_ROOT yes
RUN lein

# add scripts
ADD ./  /twitter-collector

# grab project
RUN cd /twitter-collector && lein deps

ADD ./start-app /start-app

EXPOSE 9095

CMD ["/start-app"]
