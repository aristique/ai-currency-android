#!/usr/bin/env sh
set -eu
gradle --no-daemon --console=plain testDebugUnitTest assembleDebug lintDebug
cp app/build/outputs/apk/debug/app-debug.apk /out/lab4-currency-debug.apk
mkdir -p /out/test-results
cp app/build/test-results/testDebugUnitTest/*.xml /out/test-results/
cp app/build/reports/lint-results-debug.txt /out/
