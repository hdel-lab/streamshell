// One page plays at a time (0.4.1): a silenced page pauses its media and anything that starts by itself (jsdom).
const fs = require('fs');
const { JSDOM } = require('jsdom');
const [on, off] = [2, 3].map(i => fs.readFileSync(process.argv[i], 'utf8'));
let failures = 0;
const check = (n, c) => { console.log((c ? 'PASS ' : 'FAIL ') + n); if (!c) failures++; };
const dom = new JSDOM('<html><body><video id="p"></video></body></html>', { url: 'https://m.youtube.com/', runScripts: 'outside-only' });
const w = dom.window, d = w.document, v = d.getElementById('p');
let paused = false; v.pause = () => { paused = true; };
w.eval(on);
check('silenced: media paused at once', paused);
paused = false; v.dispatchEvent(new w.Event('playing'));
check('silenced: a feed preview that starts is paused again', paused);
w.eval(off); paused = false; v.dispatchEvent(new w.Event('playing'));
check('allowed again: playing is left alone', !paused);
w.eval(on); w.eval(on);
paused = false; v.dispatchEvent(new w.Event('playing'));
check('installed once (no double handlers needed)', paused);
process.exit(failures ? 1 : 0);
