// Diagnostics (jsdom): the log lines the extras script and the other scripts produce, and nothing when switched off.
const fs = require('fs');
const { JSDOM } = require('jsdom');
const [extras, cfgOn, ads, seekBy] = [2, 3, 4, 5].map(i => fs.readFileSync(process.argv[i], 'utf8'));
let failures = 0;
const check = (n, c) => { console.log((c ? 'PASS ' : 'FAIL ') + n); if (!c) failures++; };
const html = `<html><head></head><body><div class="html5-video-player" id="movie_player"><video></video></div></body></html>`;
const dom = new JSDOM(html, { url: 'https://m.youtube.com/watch?v=dQw4w9WgXcQ', runScripts: 'outside-only', pretendToBeVisual: true });
const w = dom.window, d = w.document, posted = [];
w.streamShellSponsor = { postMessage: m => posted.push(m) };
const mp = d.getElementById('movie_player');
mp.getPlayerResponse = () => ({ videoDetails: { videoId: 'dQw4w9WgXcQ', lengthSeconds: '212' }, adPlacements: [{ adPlacementRenderer: { config: { adPlacementConfig: { kind: 'AD_PLACEMENT_KIND_START' } } } }], playerConfig: { ssapConfig: {} } });
let t = 30; mp.getCurrentTime = () => t; mp.getDuration = () => 212; const seeks = []; mp.seekTo = (x) => { seeks.push(x); };
const v = d.querySelector('video'); Object.defineProperty(v, 'duration', { get: () => 212 }); Object.defineProperty(v, 'currentTime', { get: () => t, set: x => { t = x; } });
// Off: nothing is logged.
w.eval(extras); w.eval(seekBy);
check('off: no diagnostics lines', posted.filter(m => m.startsWith('diag|')).length === 0);
// On.
w.eval(cfgOn);
const lines = () => posted.filter(m => m.startsWith('diag|')).map(m => m.slice(5));
check('on: started', lines().some(l => l.startsWith('diagnostics on')));
check('on: summary of the video (ad placements, ssap key)', lines().some(l => /video dQw4w9WgXcQ: adPlacements 1 \[AD_PLACEMENT_KIND_START\].*keys: ssap/.test(l)));
w.eval(seekBy);
check('app jump logged and done through YouTube\'s player', lines().some(l => l === 'app: jump 10 s') && seeks[0] === 40);
v.dispatchEvent(new w.Event('seeking', { bubbles: false }));
check('seeking event logged with times', lines().some(l => /^seeking from \d+\.\d t=30\.0 d=212/.test(l)));
mp.classList.add('ad-showing');
setTimeout(() => {
  check('ad state change logged', lines().some(l => l.startsWith('player AD')));
  // Rate limit: at most 15 lines per second.
  const before = lines().length; for (let i = 0; i < 40; i++) w.__ssDiagLog('x' + i);
  check('at most 15 lines per second', lines().length - before <= 15);
  w.eval('window.__ssExtras={feedCss:"",dislikes:false,deArrow:false,markers:false,diag:false};window.__ssExtrasApply();');
  const n = lines().length; w.eval(seekBy); v.dispatchEvent(new w.Event('seeking'));
  check('switched off again: no more lines', lines().length === n && w.__ssDiagLog === undefined);
  w.close(); process.exit(failures ? 1 : 0);
}, 1100);
