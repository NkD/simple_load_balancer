#!/bin/bash
export JAVA_OPTS="-Durls=$1"
bin/catalina.sh run
