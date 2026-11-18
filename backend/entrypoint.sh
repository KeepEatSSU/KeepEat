#!/bin/sh
set -e

exec java -Duser.timezone="${TZ:-Asia/Seoul}" ${JAVA_OPTS:-} -jar /app/app.jar
