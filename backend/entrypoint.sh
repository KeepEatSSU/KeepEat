#!/bin/sh
set -e

if [ -n "${FIREBASE_KEY_BASE64:-}" ]; then
    mkdir -p /run/secrets
    echo "$FIREBASE_KEY_BASE64" | base64 -d > /run/secrets/firebase.json
    chmod 600 /run/secrets/firebase.json
    export FIREBASE_KEY_PATH=/run/secrets/firebase.json
fi

exec java ${JAVA_OPTS:-} -jar /app/app.jar
