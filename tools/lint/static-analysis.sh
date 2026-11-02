#!/bin/sh
# Static analysis (0.4.0): ktlint (formatting, .editorconfig) and detekt (code smells, config/detekt.yml + baseline),
# ESLint for the YouTube page scripts (tools/js-sim/eslint.config.mjs). Tools are downloaded once into build/tools.
set -e
cd "$(dirname "$0")/../.."
mkdir -p build/tools
KTLINT=1.8.0; DETEKT=1.23.8
[ -f build/tools/ktlint.jar ] || curl -sSfL -o build/tools/ktlint.jar "https://repo.maven.apache.org/maven2/com/pinterest/ktlint/ktlint-cli/$KTLINT/ktlint-cli-$KTLINT-all.jar"
[ -f build/tools/detekt.jar ] || curl -sSfL -o build/tools/detekt.jar "https://repo.maven.apache.org/maven2/io/gitlab/arturbosch/detekt/detekt-cli/$DETEKT/detekt-cli-$DETEKT-all.jar"
java -jar build/tools/ktlint.jar "app/src/**/*.kt"
java -jar build/tools/detekt.jar --config config/detekt.yml --build-upon-default-config --baseline config/detekt-baseline.xml --input app/src/main/java
./gradlew -q :app:testDebugUnitTest --tests '*JsScriptsExportTest' --rerun
rm -rf tools/js-sim/.lint && mkdir tools/js-sim/.lint && cp app/build/js-sim/*.js tools/js-sim/.lint/
(cd tools/js-sim && npx eslint .lint)
