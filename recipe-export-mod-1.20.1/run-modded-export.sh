#!/bin/bash
# Full headless modded-export pipeline: fresh flat world (with mods), then auto-export.
set -x
cd "$(dirname "$0")"
export JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home

pkill -f bootstraplauncher 2>/dev/null
sleep 2
rm -rf run/world run/saves/export-test run/jei-exports

echo "=== SERVER: generating world with mods ==="
./gradlew runServer > runserver.log 2>&1 &
GRADLE_PID=$!
for i in $(seq 1 240); do
  if grep -q 'Done (' runserver.log 2>/dev/null; then break; fi
  if ! kill -0 $GRADLE_PID 2>/dev/null; then
    echo "SERVER_FAILED"; tail -50 runserver.log; exit 1
  fi
  sleep 5
done
if ! grep -q 'Done (' runserver.log 2>/dev/null; then echo "SERVER_TIMEOUT"; exit 1; fi
sleep 5
pkill -TERM -f bootstraplauncher
wait $GRADLE_PID
sleep 2
[ -f run/world/level.dat ] || { echo "NO_WORLD"; exit 1; }

mkdir -p run/saves
cp -R run/world run/saves/export-test
rm -f run/saves/export-test/session.lock

echo "=== CLIENT: headless export ==="
./gradlew runExportClient > runclient.log 2>&1
echo "PIPELINE_DONE exit=$?"
