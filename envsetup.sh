#!/bin/bash

HOST_ADDR=${DOCKER_GATEWAY_HOST}

# https://docs.aws.amazon.com/AWSEC2/latest/WindowsGuide/instancedata-data-retrieval.html
if [ -z "${HOST_ADDR}" ]; then
  HOST_ADDR=$(curl -m 5 -s http://169.254.169.254/latest/meta-data/local-ipv4)
fi

export JAVA_OPTS="${JAVA_OPTS} \
    -server \
    -verbose:gc \
    -Dspring.profiles.active="${SPRING_PROFILE}" \
    -Dcom.sun.management.jmxremote \
    -Dcom.sun.management.jmxremote.port=11619 \
    -Dcom.sun.management.jmxremote.ssl=false \
    -Dcom.sun.management.jmxremote.authenticate=false \
    -Dcom.sun.management.jmxremote.rmi.port=11619 \
    -Djava.rmi.server.hostname="${HOST_ADDR:-${HOSTNAME}}" \
    -XX:+HeapDumpOnOutOfMemoryError \
    "
