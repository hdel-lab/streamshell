#!/bin/sh
# Exports the exact page scripts (JsScriptsExportTest) and simulates them in node with a fake DOM and clock.
set -e
cd "$(dirname "$0")/../.."
./gradlew -q :app:testDebugUnitTest --tests '*JsScriptsExportTest' --rerun
node tools/js-sim/sim.js app/build/js-sim/ads.js app/build/js-sim/autoplay.js app/build/js-sim/router.js app/build/js-sim/compact.js app/build/js-sim/timestamps.js app/build/js-sim/live.js
# Extra features (dislikes, DeArrow, feed filter, SponsorBlock marks) in a real DOM (jsdom).
[ -d tools/js-sim/node_modules/jsdom ] || (cd tools/js-sim && npm install --silent --no-audit --no-fund)
node tools/js-sim/extras.js app/build/js-sim/extras.js app/build/js-sim/extras-config.js app/build/js-sim/extras-answers.js
node tools/js-sim/mini.js app/build/js-sim/compact.js app/build/js-sim/mini-on.js app/build/js-sim/mini-off.js
node tools/js-sim/diag.js app/build/js-sim/extras.js app/build/js-sim/extras-diag.js app/build/js-sim/ads.js app/build/js-sim/seekby.js
node tools/js-sim/silent.js app/build/js-sim/silent-on.js app/build/js-sim/silent-off.js
node tools/js-sim/release050.js app/build/js-sim/router-on.js app/build/js-sim/extras.js app/build/js-sim/extras-hide.js app/build/js-sim/repeat.js
node tools/js-sim/trace.js app/build/js-sim/extras.js app/build/js-sim/extras-diag.js app/build/js-sim/extras-trace.js
node tools/js-sim/media062.js app/build/js-sim/ms-capture.js app/build/js-sim/ms-next.js app/build/js-sim/ms-prev.js app/build/js-sim/ms-now.js app/build/js-sim/duck-on.js app/build/js-sim/duck-off.js
