#!/bin/sh

set -eu

APP_HOME=$(CDPATH= cd -- "$(dirname "$0")" && pwd)
WRAPPER_JAR="$APP_HOME/gradle/wrapper/gradle-wrapper.jar"
WEBS_JAVA="/Applications/WebStorm.app/Contents/jbr/Contents/Home/bin/java"

if [ ! -f "$WRAPPER_JAR" ]; then
  echo "ERROR: missing $WRAPPER_JAR" >&2
  exit 1
fi

is_java_working() {
  "$1" -version >/dev/null 2>&1
}

if [ -n "${JAVA_HOME:-}" ] && [ -x "$JAVA_HOME/bin/java" ] && is_java_working "$JAVA_HOME/bin/java"; then
  JAVA_CMD="$JAVA_HOME/bin/java"
elif command -v java >/dev/null 2>&1 && is_java_working "$(command -v java)"; then
  JAVA_CMD="$(command -v java)"
elif [ -x "$WEBS_JAVA" ] && is_java_working "$WEBS_JAVA"; then
  JAVA_CMD="$WEBS_JAVA"
else
  echo "ERROR: Java runtime not found. Set JAVA_HOME or install JDK." >&2
  exit 1
fi

exec "$JAVA_CMD" -classpath "$WRAPPER_JAR" org.gradle.wrapper.GradleWrapperMain "$@"
