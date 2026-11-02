// 0.6.2 (jsdom): the media-session capture keeps the page's handlers; next/previous call them or the page's buttons;
// now-playing reads the metadata; duck lowers and restores the volume.
const fs = require('fs');
const { JSDOM } = require('jsdom');
const [capture, next, prev, now, duckOn, duckOff] = [2, 3, 4, 5, 6, 7].map(i => fs.readFileSync(process.argv[i], 'utf8'));
let failures = 0;
const check = (n, c) => { console.log((c ? 'PASS ' : 'FAIL ') + n); if (!c) failures++; };
{
  const dom = new JSDOM('<html><body><video></video></body></html>', { url: 'https://music.youtube.com/watch?v=x', runScripts: 'outside-only' });
  const w = dom.window;
  const calls = [];
  w.navigator.mediaSession = { metadata: null, setActionHandler: (a, h) => calls.push(a) };
  w.eval(capture);
  let hit = '';
  w.navigator.mediaSession.setActionHandler('nexttrack', () => { hit = 'next'; });
  check('page registration still reaches the browser', calls.includes('nexttrack'));
  check('next calls the page handler', w.eval(next) === 'handler' && hit === 'next');
  w.navigator.mediaSession.metadata = { title: 'Song', artist: 'Band' };
  check('now playing: title and artist', w.eval(now) === 'Song\u0001Band');
  const v = w.document.querySelector('video'); v.volume = 0.8;
  w.eval(duckOn);
  check('duck: quarter volume', Math.abs(v.volume - 0.25) < 1e-9);
  w.eval(duckOn);
  check('duck twice keeps the original', v.__ssVol === 0.8);
  w.eval(duckOff);
  check('unduck: original volume back', Math.abs(v.volume - 0.8) < 1e-9 && v.__ssVol === null);
}
{
  const dom = new JSDOM('<html><body><button class="previous-button" id="p"></button><audio></audio></body></html>', { url: 'https://music.youtube.com/', runScripts: 'outside-only' });
  const w = dom.window;
  let clicked = false; w.document.getElementById('p').addEventListener('click', () => { clicked = true; });
  w.eval(capture);
  check('without handler: the page button', w.eval(prev) === 'button' && clicked);
  check('no metadata: empty', w.eval(now) === '');
}
process.exit(failures ? 1 : 0);
