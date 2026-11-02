// 0.6.1 "Trace everything" (jsdom): page taps (kind of element, never text or address), scrolls, buffering, frames,
// page/script errors — and nothing of it with only the player diagnostics on.
const fs = require('fs');
const { JSDOM } = require('jsdom');
const [extras, cfgDiag, cfgTrace] = [2, 3, 4].map(i => fs.readFileSync(process.argv[i], 'utf8'));
let failures = 0;
const check = (n, c) => { console.log((c ? 'PASS ' : 'FAIL ') + n); if (!c) failures++; };
const html = `<html><head></head><body>
  <ytm-video-with-context-renderer><a id="tile" href="/watch?v=aaaaaaaaaaa"><h3>Secret title</h3></a></ytm-video-with-context-renderer>
  <div class="html5-video-player" id="movie_player"><button id="pb" class="ytp-play-button">Play</button><video></video></div></body></html>`;
const dom = new JSDOM(html, { url: 'https://m.youtube.com/watch?v=aaaaaaaaaaa', runScripts: 'outside-only', pretendToBeVisual: true });
const w = dom.window, d = w.document, posted = [];
w.streamShellSponsor = { postMessage: m => posted.push(m) };
const lines = () => posted.filter(m => m.startsWith('diag|')).map(m => m.slice(5));
const v = d.querySelector('video');
let paused = false;
Object.defineProperty(v, 'paused', { get: () => paused });
Object.defineProperty(v, 'videoWidth', { get: () => 1280 });
Object.defineProperty(v, 'videoHeight', { get: () => 720 });
let q = { droppedVideoFrames: 2, totalVideoFrames: 300 };
v.getVideoPlaybackQuality = () => q;
d.getElementById('movie_player').getPlaybackQuality = () => 'hd720';
const ptr = (type, el, x, y) => { const e = new w.MouseEvent(type, { bubbles: true, clientX: x, clientY: y }); el.dispatchEvent(e); };

w.eval(cfgDiag); w.eval(extras);
ptr('pointerdown', d.getElementById('tile'), 10, 10); ptr('pointerup', d.getElementById('tile'), 10, 10);
check('player diagnostics only: no touch lines', !lines().some(l => /touch|click on/.test(l)));

w.eval(cfgTrace);
check('trace on', lines().includes('trace everything on'));
ptr('pointerdown', d.getElementById('tile'), 10, 10); ptr('pointerup', d.getElementById('tile'), 12, 11);
check('tap on a tile: kind and component, no title, no address', lines().some(l => /^touch \d+ ms on video link <a> in ytm-video-with-context-renderer at 12,11$/.test(l)) &&
  !lines().some(l => l.includes('Secret') || l.includes('aaaaaaaaaaa?') || l.includes('/watch')));
ptr('pointerdown', d.getElementById('pb'), 50, 300); ptr('pointerup', d.getElementById('pb'), 50, 380);
check('drag on a player button', lines().some(l => /^drag 0,80 \d+ ms on player button <button>/.test(l)));
d.getElementById('pb').dispatchEvent(new w.MouseEvent('click', { bubbles: true }));
check('click logged with the element kind', lines().some(l => l === 'click on player button <button>'));
v.dispatchEvent(new w.Event('waiting'));
setTimeout(() => {
  v.dispatchEvent(new w.Event('playing'));
  check('buffering time', lines().some(l => /^buffering \d+ ms$/.test(l)));
  const err = new w.ErrorEvent('error', { message: 'boom at https://x.example/a.js?token=1' });
  w.dispatchEvent(err);
  check('page error without the address', lines().some(l => l === 'page error: boom at <address>'));
  w.dispatchEvent(new w.Event('scroll'));
  setTimeout(() => {
    check('scroll session', lines().some(l => /^scroll -?\d+ px in \d+ ms$/.test(l)));
    q = { droppedVideoFrames: 5, totalVideoFrames: 450 };
  }, 400);
}, 60);
setTimeout(() => {
  check('frames every 5 s with quality and size', lines().some(l => /^frames: dropped \d+ of \d+ in 5 s \(total \d+\/\d+\), quality hd720, size 1280x720, buffered ahead [\d.]+ s$/.test(l)));
  const before = lines().length; for (let i = 0; i < 80; i++) w.__ssDiagLog('x' + i);
  check('trace: at most 40 lines per second', lines().length - before <= 40);
  w.close(); process.exit(failures ? 1 : 0);
}, 5300);
