// Behavioural simulation of StreamShell's YouTube page scripts with a fake DOM and a fake clock.
// Run: tools/js-sim/run.sh (exports the exact scripts via a JVM test, then simulates them in node).
const fs = require('fs');
if (!globalThis.getComputedStyle) globalThis.getComputedStyle = (e) => ({ visibility: 'visible', display: 'block', opacity: (e && e._opacity) || '1' });
const [adsSrc, autoSrc, routerSrc, compactSrc, tsSrc, liveSrc] = [2, 3, 4, 5, 6, 7].map(i => fs.readFileSync(process.argv[i], 'utf8'));

function makeEnv() {
  let now = 0; const timers = [];
  const video = { muted: false, paused: true, ended: false, readyState: 4, duration: 15, currentSrc: 'blob:ad1', src: '', _t: 0, _rate: 1,
    frames: 0, rates: [], seeks: [], plays: 0, pauses: 0,
    play() { this.plays++; this.paused = false; return Promise.resolve(); }, pause() { this.pauses++; this.paused = true; },
    getVideoPlaybackQuality() { return { totalVideoFrames: this.frames }; } };
  Object.defineProperty(video, 'currentTime', { get() { return this._t; }, set(t) { this._t = t; this.seeks.push({ t, src: this.currentSrc }); } });
  Object.defineProperty(video, 'playbackRate', { get() { return this._rate; }, set(r) { this._rate = r; this.rates.push({ r, src: this.currentSrc }); } });
  const mp = { enabled: false, calls: 0 }; mp.playVideo = undefined;
  const enableApi = () => { mp.playVideo = () => { mp.calls++; video.paused = false; }; };
  mp.loads = []; const enableReload = () => { mp.loadVideoById = (id, t) => { mp.loads.push([id, t]); }; };
  const classes = new Set(['ad-showing']);
  const player = { classList: { contains: c => classes.has(c) }, querySelector: s => (s === 'video' ? video : null) };
  // YouTube's Skip button is position:fixed, so offsetParent is null even while it is on screen.
  const skip = { shown: false, clicks: 0, offsetParent: null, disabled: false, getClientRects() { return this.shown ? [{}] : []; }, getBoundingClientRect() { return { left: 300, top: 180, width: 90, height: 36 }; }, click() { this.clicks++; } };
  const docListeners = {};
  const document = {
    visibilityState: 'visible', head: { appendChild() {} }, documentElement: { appendChild() {} }, _l: docListeners,
    addEventListener: (t, f) => { (docListeners[t] = docListeners[t] || []).push(f); },
    getElementById: id => (id === 'movie_player' ? mp : null), createElement: () => ({}),
    querySelector: s => (s === '.html5-video-player' ? player : s === 'video' ? video : null),
    querySelectorAll: s => (s.includes('skip') ? [skip] : []),
  };
  const location = { href: 'https://m.youtube.com/watch?v=dQw4w9WgXcQ' };
  const messages = [];
  const window = { innerWidth: 412, innerHeight: 900, streamShellSponsor: { postMessage: m => messages.push(m) } };
  const setInterval = (f, ms) => timers.push({ f, ms, next: now + ms });
  const FakeDate = { now: () => now };
  const run = src => new Function('window', 'document', 'location', 'setInterval', 'URL', 'Date', src)(window, document, location, setInterval, URL, FakeDate);
  // While playing, time and frames advance (unless "black" is set: the frozen-picture bug).
  let black = false;
  const advance = ms => {
    const end = now + ms;
    for (;;) {
      let t = null; for (const x of timers) if (!t || x.next < t.next) t = x;
      const step = t && t.next <= end ? t.next : end;
      if (!video.paused) { video._t += (step - now) / 1000 * video._rate; if (!black) video.frames += Math.round((step - now) / 33); }
      now = step;
      if (!t || t.next > end) break;
      t.f(); t.next += t.ms;
    }
  };
  const content = (src, dur) => { video.currentSrc = src; video.duration = dur; video._t = 0; };
  return { video, mp, enableApi, enableReload, location, classes, skip, run, advance, messages, document, content, setBlack: b => { black = b; } };
}

let failures = 0;
const flush = () => new Promise(r => setImmediate(r));
const check = (name, cond) => { console.log((cond ? 'PASS ' : 'FAIL ') + name); if (!cond) failures++; };

(async () => {
  { // 1. Unskippable 15 s ad: muted, 16x, one jump; then the real video while the marker lingers 600 ms
    const e = makeEnv(); e.run(adsSrc); e.run(autoSrc);
    e.video.paused = false; e.advance(1000);
    check('ad muted', e.video.muted === true);
    check('ad played at 16x', e.video.rates.some(r => r.src === 'blob:ad1' && r.r === 16));
    check('exactly one jump inside the ad', e.video.seeks.filter(s => s.src === 'blob:ad1').length === 1);
    e.content('blob:content', 600); e.advance(600);
    check('real video never sped up', e.video._rate === 1 && !e.video.rates.some(r => r.src === 'blob:content' && r.r > 2));
    e.classes.clear(); e.advance(3000);
    check('real video never jumped', e.video.seeks.filter(s => s.src === 'blob:content').length === 0);
    check('sound restored', e.video.muted === false);
    check('real video plays', !e.video.paused);
    check('no needless pause when the picture is fine', e.video.pauses === 0);
    check('one "ad skipped" message', e.messages.filter(m => m === 'ad|0').length === 1);
  }
  { // 1b. Same blob for ad and video (MSE), only the duration changes
    const e = makeEnv(); e.run(adsSrc); e.video.paused = false; e.advance(500);
    e.video.duration = 300; e.video._t = 0; e.advance(700);
    check('same source, new duration: not touched', !e.video.seeks.some(s => s.t > 100) && e.video._rate === 1);
  }
  { // 2. Skippable ad: fixed-position Skip button is pressed as soon as it is shown
    const e = makeEnv(); e.run(adsSrc); e.video.duration = 200; e.video.paused = false; e.advance(1000);
    check('Skip not pressed before it appears', e.skip.clicks === 0);
    e.skip.shown = true; e.advance(300);
    check('fixed-position Skip pressed', e.skip.clicks >= 1);
  }
  { // 3. Media longer than 3 min is never sped up or jumped
    const e = makeEnv(); e.run(adsSrc); e.video.duration = 900; e.video.paused = false; e.advance(1000);
    check('no 16x / jump on long media', e.video.seeks.length === 0 && e.video._rate === 1);
  }
  { // 4. Ad break with two ads: the second ad (marker still set > 1 s) is handled too
    const e = makeEnv(); e.run(adsSrc); e.video.paused = false; e.advance(500);
    e.content('blob:ad2', 20); e.advance(1600);
    check('second ad of the break muted, 16x and jumped', e.video.muted && e.video._rate === 16 && e.video.seeks.some(s => s.src === 'blob:ad2'));
  }
  { // 5. Black picture after the ad (frames stop): one pause + play, like doing it by hand
    const e = makeEnv(); e.run(adsSrc); e.run(autoSrc); e.video.paused = false; e.advance(500);
    e.content('blob:content', 600); e.classes.clear(); e.setBlack(true); e.advance(1200);
    check('no kick before the check', e.video.pauses === 0);
    e.setBlack(false); e.setBlack(true); e.advance(1500);
    check('black picture: paused once', e.video.pauses === 1);
    e.advance(800);
    check('black picture: playing again', !e.video.paused && e.video.plays >= 1);
    e.advance(6000);
    check('only one kick per ad break', e.video.pauses === 1);
  }
  { // 5b. Your own ±10 s jump right after an ad is not mistaken for a frozen picture (no pause/play stutter)
    const e = makeEnv(); e.run(adsSrc); e.run(autoSrc); e.video.paused = false; e.advance(500);
    e.content('blob:content', 600); e.classes.clear(); e.advance(1000);
    e.setBlack(true); e.video._t = 0; (e.document._l.seeking || []).forEach(f => f()); e.advance(1500);
    check('jump after an ad: no pause/play kick', e.video.pauses === 0 && !e.video.paused);
  }
  { // 5c. Inside a frame (live chat iframe, a player embedded on another site): autoplay stays out
    const e = makeEnv(); e.classes.clear(); e.content('blob:content', 600);
    const w = { top: {} };
    const r = new Function('window', 'document', 'location', 'setInterval', 'URL', 'Date', 'return ' + autoSrc)(w, e.document, e.location, () => { throw new Error('timer in frame'); }, URL, Date);
    check('autoplay script does nothing inside a frame', r === 'frame');
  }
  { // 6. No ad: autoplay once, manual pause respected, nothing seeked
    const e = makeEnv(); e.classes.clear(); e.content('blob:content', 600);
    e.run(adsSrc); e.run(autoSrc); e.advance(500); await flush();
    check('autoplay started', e.video.plays === 1 && !e.video.paused);
    e.advance(2000);                       // it really plays for a while, then you pause it
    e.video.paused = true; e.advance(3000);
    check('manual pause respected', e.video.plays === 1);
    check('no seeks or speed changes without ads', e.video.seeks.length === 0 && e.video.rates.length === 0);
  }
  { // 7. Hidden (background) tab is never started
    const e = makeEnv(); e.classes.clear(); e.document.visibilityState = 'hidden'; e.run(autoSrc); e.advance(3000);
    check('background tab not started', e.video.plays === 0);
  }
  { // 8. YouTube's own player API is used to start when it exists (no direct play() needed)
    const e = makeEnv(); e.classes.clear(); e.enableApi(); e.content('blob:content', 600);
    e.run(autoSrc); e.advance(500);
    check('player API playVideo() used', e.mp.calls === 1 && !e.video.paused && e.video.plays === 0);
  }
  { // 9. First start attempt refused (autoplay policy): retried, limited
    const e = makeEnv(); e.classes.clear(); e.content('blob:content', 600);
    let refuse = 2; const real = e.video.play.bind(e.video);
    e.video.play = function () { if (refuse-- > 0) { this.plays++; return Promise.reject(new Error('NotAllowed')); } return real(); };
    e.run(autoSrc); e.advance(4000); await flush();
    check('refused start retried until playing', !e.video.paused && e.video.plays === 3);
    e.video.play = function () { this.plays++; return Promise.reject(new Error('x')); }; e.video.paused = true;
    const before = e.video.plays; e.advance(8000);
    check('no endless retries after the user pauses', e.video.plays === before);
  }
  { // 11. (0.4.1, from the user's diagnostics log) A short ad is NOT re-loaded — a reload made YouTube serve a new ad each
    // time — it is muted, sped up and jumped; only an ad that cannot be jumped is re-loaded, after 4 s, at most 2 per minute
    const e = makeEnv(); e.enableReload(); e.video.paused = false; e.run(adsSrc); e.advance(5000);
    check('short ad: no reload, muted, 16x and jumped', e.mp.loads.length === 0 && e.video.muted && e.video.rates.some(r => r.r === 16) && e.video.seeks.length === 1);
    const l = makeEnv(); l.enableReload(); l.content('blob:longad', 700); l.video.paused = false; l.run(adsSrc); l.advance(3000);
    check('long ad: not re-loaded in the first 4 s', l.mp.loads.length === 0);
    l.advance(1500);
    check('long ad: re-loaded once after 4 s, at its start', l.mp.loads.length === 1 && l.mp.loads[0][0] === 'dQw4w9WgXcQ' && l.mp.loads[0][1] === 0);
    for (let i = 0; i < 3; i++) { l.classes.clear(); l.advance(500); l.classes.add('ad-showing'); l.content('blob:longad' + i, 700); l.advance(4600); }
    check('at most 2 reloads per minute', l.mp.loads.length === 2);
  }
  { // 11c. (0.5.0, sixth log) An ad whose length changes by a second while loading (6 s → 7 s) is the same ad: one jump,
    // no second wait of a second
    const e = makeEnv(); e.enableReload(); e.content('blob:ad6', 6); e.video.paused = false; e.run(adsSrc); e.advance(300);
    e.video.duration = 7; e.advance(700);
    check('6 s → 7 s ad: jumped within 1 s, once', e.video.seeks.length === 1 && e.mp.loads.length === 0);
  }
  { // 11b. (0.4.5, fifth log) A 183 s ad is sped up and jumped like a short one, no reload
    const e = makeEnv(); e.enableReload(); e.content('blob:ad183', 183); e.video.paused = false; e.run(adsSrc); e.advance(5000);
    check('183 s ad: 16x and jumped, not re-loaded', e.mp.loads.length === 0 && e.video.rates.some(r => r.r === 16) && e.video.seeks.length === 1);
  }
  { // 12. Mid-roll: re-loaded at the position of the real video
    const e = makeEnv(); e.enableReload(); e.classes.clear(); e.content('blob:content', 600); e.video.paused = false; e.run(adsSrc);
    e.advance(42000); e.classes.add('ad-showing'); e.content('blob:ad2', 700); e.advance(4600);
    check('mid-roll resumes at the content position', e.mp.loads.length === 1 && e.mp.loads[0][1] >= 40 && e.mp.loads[0][1] <= 42);
  }
  { // 13. Visible Skip: the app is asked to press it with a real touch, rate-limited, max. 3 per ad
    const e = makeEnv(); e.video.paused = false; e.run(adsSrc); e.skip.shown = true; e.advance(10000);
    const taps = e.messages.filter(m => m.startsWith('adtap|'));
    check('real tap requested at the button centre', taps[0] === 'adtap|345|198|412|900');
    check('max. 3 tap requests per ad', taps.length === 3);
  }
  { // 14. Video page: a sticky bar right under the player is released (scrolls with the list); the player stays fixed
    const mk = (cls, pos, top) => { const o = { nodeType: 1, tagName: 'DIV', className: cls, children: [], parentElement: null, _pos: pos, _top: top,
      contains(x) { return x === o; } }; o.style = { setProperty(k, v) { if (k === 'top') o._top = v; if (k === 'position') o._pos = v; if (k === 'display') o._display = v; } }; return o; };
    const player = mk('player-container sticky-player', 'sticky', 0);
    player.getBoundingClientRect = () => ({ bottom: 232, height: 232 });
    const wrap = mk('x-unknown-wrapper', 'sticky', 280); wrap.getBoundingClientRect = () => ({ height: 48 });
    wrap.querySelector = q => (q.includes('chip') ? chip : null);
    const titleRow = mk('x-title-header', 'sticky', 240); titleRow.getBoundingClientRect = () => ({ height: 56 }); titleRow.querySelector = () => null;
    const tall = mk('x-tall', 'sticky', 400); tall.parentElement = null; tall.getBoundingClientRect = () => ({ height: 300 }); const chip = mk('chip', 'static', 0); chip.parentElement = wrap;
    const thumb = mk('thumb', 'static', 0);
    const body = { nodeType: 1, tagName: 'BODY', children: [], className: '', style: { setProperty() {} }, contains: () => true }; wrap.parentElement = body; thumb.parentElement = body; titleRow.parentElement = body;
    const listeners = {}; let rafs = [];
    const doc = { body, documentElement: { appendChild() {} }, head: { appendChild() {} }, getElementById: () => null, createElement: () => ({}),
      addEventListener() {}, querySelector: q => (q.includes('player-container') ? player : null), querySelectorAll: () => [],
      elementsFromPoint: (x, y) => (y >= 276 && y <= 300 ? [chip, wrap, body] : y > 300 ? [tall, body] : y >= 236 && y < 276 ? [titleRow, body] : [thumb, body]) };
    const win = { innerWidth: 412, addEventListener: (t, f) => { listeners[t] = f; } };
    const gcs = el => ({ position: el._pos || 'static', top: typeof el._top === 'number' ? el._top + 'px' : 'auto', paddingTop: '0px', marginTop: '0px' });
    new Function('window', 'document', 'location', 'getComputedStyle', 'requestAnimationFrame', 'MutationObserver', 'setTimeout', 'WeakSet', 'Date', compactSrc)(
      win, doc, { pathname: '/watch', href: 'https://m.youtube.com/watch?v=dQw4w9WgXcQ' }, gcs, f => { rafs.push(f); return 1; },
      function () { this.observe = () => {}; }, () => 0, WeakSet, { now: () => 10000 });
    listeners.scroll(); rafs.forEach(f => f()); rafs = [];
    check('thin sticky row under the player removed', wrap._display === 'none');
    check('thin sticky row without chips (e.g. the title) is kept, only released', titleRow._display === undefined && titleRow._pos === 'relative');
    check('taller sticky block only released (scrolls)', tall._pos === 'relative' && tall._display === undefined);
    check('player itself stays fixed', player._pos === 'sticky' && player._top === 0);
  }
  { // 15. Description/comments panel (position:fixed at top bar + player) is docked to the player's bottom
    const mk = (cls, pos, top, h) => { const o = { nodeType: 1, tagName: 'DIV', className: cls, children: [], parentElement: null, _pos: pos, _top: top, _h: h,
      contains(x) { return x === o; } }; o.style = { setProperty(k, v) { if (k === 'top') o._top = parseFloat(v); if (k === 'height') o._h = parseFloat(v); } };
      o.getBoundingClientRect = () => ({ top: o._top, bottom: o._top + o._h, height: o._h }); return o; };
    const player = mk('player-container sticky-player', 'sticky', 0, 232);
    const panel = mk('engagement-panel', 'fixed', 280, 620);   // 232 + 48 (removed top bar), reaches the bottom (900)
    const body = { nodeType: 1, tagName: 'BODY', children: [], className: '', style: { setProperty() {} }, contains: () => true }; panel.parentElement = body;
    const listeners = {}; let rafs = [];
    const doc = { body, documentElement: { appendChild() {} }, head: { appendChild() {} }, getElementById: () => null, createElement: () => ({}),
      addEventListener() {}, querySelector: q => (q.includes('player-container') ? player : null),
      querySelectorAll: q => (q.includes('engagement-panel') ? [panel] : []), elementsFromPoint: () => [body] };
    const win = { innerWidth: 412, innerHeight: 900, addEventListener: (t, f) => { listeners[t] = f; } };
    const gcs = el => ({ position: el._pos || 'static', top: typeof el._top === 'number' ? el._top + 'px' : 'auto', height: el._h ? el._h + 'px' : 'auto', bottom: 'auto', paddingTop: '0px', marginTop: '0px' });
    new Function('window', 'document', 'location', 'getComputedStyle', 'requestAnimationFrame', 'MutationObserver', 'setTimeout', 'WeakSet', 'Date', compactSrc)(
      win, doc, { pathname: '/watch', href: 'https://m.youtube.com/watch?v=dQw4w9WgXcQ' }, gcs, f => { rafs.push(f); return 1; },
      function () { this.observe = () => {}; }, () => 0, WeakSet, { now: () => 10000 });
    listeners.scroll(); rafs.forEach(f => f()); rafs = [];
    check('panel docked at the player bottom', panel._top === 232);
    check('panel still reaches the bottom of the screen', panel._top + panel._h === 900);
  }
  { // 16. Sound: an old stored "muted" is repaired once; after an ad the sound comes back through YouTube's player
    const store = {}; globalThis.localStorage = { getItem: k => (k in store ? store[k] : null), setItem: (k, v) => { store[k] = String(v); }, removeItem: k => { delete store[k]; } };
    const e = makeEnv(); e.classes.clear(); e.content('blob:content', 600); e.video.muted = true; e.video.paused = false;
    let ytMuted = true; e.mp.isMuted = () => ytMuted; e.mp.unMute = () => { ytMuted = false; };
    e.run(adsSrc); e.advance(300);
    check('stale mute repaired once', e.video.muted === false && ytMuted === false);
    e.video.muted = true; ytMuted = true; e.advance(1000);                 // the user mutes on purpose
    check('a mute you choose is kept', e.video.muted === true);
    e.video.muted = false; ytMuted = false;
    e.classes.add('ad-showing'); e.content('blob:ad9', 15); e.advance(300);
    check('ad muted', e.video.muted === true && store['streamshell-ad-muted'] === '1');
    ytMuted = true;                                                          // YouTube stored the mute
    e.classes.clear(); e.content('blob:content2', 600); e.advance(600);
    check('sound back after the ad (element and player)', e.video.muted === false && ytMuted === false && !('streamshell-ad-muted' in store));
    delete globalThis.localStorage;
  }
  { // 17. YouTube pauses by itself right after the start: autoplay tries again (that was not you)
    const e = makeEnv(); e.classes.clear(); e.content('blob:content', 600);
    e.run(autoSrc); e.advance(500);
    e.video.paused = true;                 // start-up hiccup at ~0.1 s
    e.advance(1000);
    check('start-up pause is retried', !e.video.paused && e.video.plays >= 2);
  }
  { // 18. Comments panel: its sticky header (with the close X) is never released or hidden
    const mk = (cls, pos, top, h) => { const o = { nodeType: 1, tagName: 'DIV', className: cls, children: [], parentElement: null, _pos: pos, _top: top, _h: h, isConnected: true,
      contains(x) { return x === o; } }; o.style = { setProperty(k, v) { o['_' + k] = v; } };
      o.getBoundingClientRect = () => ({ top: o._top, bottom: o._top + o._h, height: o._h }); return o; };
    const player = mk('player-container sticky-player', 'sticky', 0, 232);
    const panel = mk('engagement-panel-container', 'fixed', 232, 668);
    const header = mk('panel-header', 'sticky', 0, 52); header.parentElement = panel;
    const body = { nodeType: 1, tagName: 'BODY', children: [], className: '', style: { setProperty() {} }, contains: () => true }; panel.parentElement = body;
    const listeners = {}; let rafs = []; const posted = [];
    const doc = { body, documentElement: { appendChild() {} }, head: { appendChild() {} }, getElementById: () => null, createElement: () => ({}),
      addEventListener() {}, querySelector: q => (q.includes('player-container') ? player : null),
      querySelectorAll: q => (q.includes('engagement-panel') ? [panel] : []), elementsFromPoint: () => [header, panel, body] };
    const win = { innerWidth: 412, innerHeight: 900, addEventListener: (t, f) => { listeners[t] = f; }, streamShellSponsor: { postMessage: m => posted.push(m) } };
    const gcs = el => ({ position: el._pos || 'static', top: typeof el._top === 'number' ? el._top + 'px' : 'auto', height: el._h ? el._h + 'px' : 'auto', bottom: 'auto', display: 'block', visibility: 'visible', opacity: '1', paddingTop: '0px', marginTop: '0px' });
    new Function('window', 'document', 'location', 'getComputedStyle', 'requestAnimationFrame', 'MutationObserver', 'setTimeout', 'WeakSet', 'Date', compactSrc)(
      win, doc, { pathname: '/watch', href: 'https://m.youtube.com/watch?v=dQw4w9WgXcQ' }, gcs, f => { rafs.push(f); return 1; },
      function () { this.observe = () => {}; }, () => 0, WeakSet, { now: () => 10000 });
    listeners.scroll(); rafs.forEach(f => f()); rafs = [];
    check('panel header kept sticky and visible', header._pos === 'sticky' && header._display === undefined);
  }
  { // 19. Loading player placed under the removed top bar (48 px): moved to the very top and marked for the shadow
    const classes = new Set();
    const player = { nodeType: 1, tagName: 'DIV', className: 'player-container', _pos: 'sticky', _top: 48, parentElement: null,
      classList: { add: c => classes.add(c), contains: c => classes.has(c) }, contains(x) { return x === player; },
      style: { setProperty(k, v) { if (k === 'top') player._top = parseFloat(v); } } };
    player.getBoundingClientRect = () => ({ top: player._top, bottom: player._top + 232, height: 232 });
    const html = { appendChild() {} };
    const body = { nodeType: 1, tagName: 'BODY', children: [], className: '', style: { setProperty() {} }, contains: () => true, parentElement: html };
    player.parentElement = body;
    const doc = { body, documentElement: html, head: { appendChild() {} }, getElementById: () => null, createElement: () => ({}),
      addEventListener() {}, querySelector: q => (q.includes('player-container') ? player : null), querySelectorAll: () => [], elementsFromPoint: () => [body] };
    const win = { innerWidth: 412, innerHeight: 900, scrollY: 0, addEventListener() {} };
    const gcs = el => ({ position: el._pos || 'static', top: typeof el._top === 'number' ? el._top + 'px' : 'auto', paddingTop: '0px', marginTop: '0px', bottom: 'auto' });
    new Function('window', 'document', 'location', 'getComputedStyle', 'requestAnimationFrame', 'MutationObserver', 'setTimeout', 'WeakSet', 'Date', compactSrc)(
      win, doc, { pathname: '/watch', href: 'https://m.youtube.com/watch?v=dQw4w9WgXcQ' }, gcs, () => 1,
      function () { this.observe = () => {}; this.disconnect = () => {}; }, () => 0, WeakSet, { now: () => 10000 });
    check('loading player moved to the very top', player._top === 0);
    check('player marked for the raised look', classes.has('ss-player'));
  }
  { // 20. Timestamp of the video on the page: the player jumps there and plays; other links are left alone
    const listeners = []; const seeks = []; let plays = 0;
    const mp = { seekTo: (t) => seeks.push(t), playVideo: () => { plays++; } };
    const video = { paused: true, play() { this.paused = false; return Promise.resolve(); } };
    const doc = { addEventListener: (t, f) => listeners.push(f), getElementById: id => (id === 'movie_player' ? mp : null), querySelector: () => video };
    const fire = href => { let prevented = false; const a = { getAttribute: () => href };
      listeners.forEach(f => f({ target: { closest: () => a }, preventDefault() { prevented = true; }, stopImmediatePropagation() {} })); return prevented; };
    new Function('window', 'document', 'location', 'URL', tsSrc)({}, doc, { href: 'https://m.youtube.com/watch?v=dQw4w9WgXcQ' }, URL);
    check('timestamp jumps in place', fire('/watch?v=dQw4w9WgXcQ&t=135s') && seeks[0] === 135 && plays === 1);
    check('h/m/s timestamps', fire('https://www.youtube.com/watch?v=dQw4w9WgXcQ&t=1h2m3s') && seeks[1] === 3723);
    check('other videos are not intercepted', !fire('/watch?v=aaaaaaaaaaa&t=10') && seeks.length === 2);
  }
  { // 22. Chapters: description timestamps of this video (not comments) are sent once, sorted, with titles
    const listeners = []; const posted = []; let observer = null; const timers = [];
    const mk = (tag, cls, parent) => ({ nodeType: 1, tagName: tag, className: cls, parentElement: parent, nextSibling: null, getAttribute: () => null });
    const desc = mk('DIV', 'expandable-video-description-body', null);
    const comments = mk('DIV', 'comment-renderer', null);
    const link = (href, after, parent) => { const a = mk('A', '', parent); a.getAttribute = k => (k === 'href' ? href : null);
      a.nextSibling = { nodeType: 3, textContent: after, nextSibling: null }; return a; };
    const anchors = [link('/watch?v=dQw4w9WgXcQ&t=45s', ' — Hits on video\nmore', desc), link('/watch?v=dQw4w9WgXcQ&t=0s', ' — Intro', desc),
      link('/watch?v=dQw4w9WgXcQ&t=20s', ' — Night attack', desc), link('/watch?v=dQw4w9WgXcQ&t=99s', ' lol', comments), link('/watch?v=other111111&t=5', ' x', desc)];
    const doc = { addEventListener: (t, f) => listeners.push(f), getElementById: () => null, querySelector: () => null, documentElement: {},
      querySelectorAll: () => anchors };
    const win = { streamShellSponsor: { postMessage: m => posted.push(m) }, MutationObserver: function (cb) { observer = cb; this.observe = () => {}; } };
    const realTimeout = globalThis.setTimeout; globalThis.MutationObserver = win.MutationObserver; globalThis.setTimeout = f => { timers.push(f); return 1; };
    new Function('window', 'document', 'location', 'URL', tsSrc)(win, doc, { href: 'https://m.youtube.com/watch?v=dQw4w9WgXcQ', pathname: '/watch' }, URL);
    observer && observer([]); timers.splice(0).forEach(f => f());
    const msg = posted.find(m => m.startsWith('chapters|['));
    const list = msg ? JSON.parse(msg.slice(9)) : [];
    check('chapters from the description, sorted', list.map(c => c.t).join(',') === '0,20,45');
    check('titles taken from the text after the time', list[1] && list[1].l === 'Night attack' && list[2].l === 'Hits on video');
    observer && observer([]); timers.splice(0).forEach(f => f());
    check('sent only when it changes', posted.filter(m => m.startsWith('chapters|[')).length === 1);
    delete globalThis.MutationObserver; globalThis.setTimeout = realTimeout;
  }
  { // 23. Live stream: reported to the app; on phones the chat card goes right after the description block
    const posted = []; let tick = null;
    const mkEl = (tag, cls) => { const o = { tagName: tag, className: cls, children: [], parentElement: null, nextSibling: null, style: {}, attrs: {},
      setAttribute(k, v) { o.attrs[k] = v; }, getAttribute: k => o.attrs[k], appendChild(c) { o.children.push(c); c.parentElement = o; return c; },
      insertBefore(c, ref) { const i = ref ? o.children.indexOf(ref) : -1; if (i < 0) o.children.push(c); else o.children.splice(i, 0, c); c.parentElement = o; return c; },
      removeChild(c) { o.children.splice(o.children.indexOf(c), 1); c.parentElement = null; }, addEventListener() {}, querySelector: () => null,
      get isConnected() { return true; } }; return o; };
    const body = mkEl('BODY', ''); const watch = mkEl('YTM-WATCH', ''); body.appendChild(watch);
    const list = mkEl('DIV', 'results'); watch.appendChild(list);
    const meta = mkEl('DIV', 'slim-video-metadata-section'); list.appendChild(meta);
    const recs = mkEl('DIV', 'related'); list.appendChild(recs); recs.querySelector = q => (q.includes('ytm-video-with-context-renderer') ? {} : null);
    list.querySelector = q => (q.includes('ytm-video-with-context-renderer') ? {} : null); watch.querySelector = list.querySelector;
    meta.nextSibling = recs;
    const desc = mkEl('DIV', 'description'); meta.appendChild(desc);
    const mp = { getVideoData: () => ({ isLive: true }) };
    const doc = { body, getElementById: id => (id === 'movie_player' ? mp : null), createElement: t => mkEl(t.toUpperCase(), ''),
      querySelector: q => (q.includes('description') ? desc : null), addEventListener() {} };
    const win = { streamShellSponsor: { postMessage: m => posted.push(m) } };
    const realInterval = globalThis.setInterval; globalThis.setInterval = f => { if (!tick) tick = f; return 1; };
    new Function('window', 'document', 'location', 'URL', 'setInterval', liveSrc)(win, doc, { href: 'https://m.youtube.com/watch?v=dQw4w9WgXcQ', hostname: 'm.youtube.com' }, URL, globalThis.setInterval);
    globalThis.setInterval = realInterval;
    check('live reported to the app', posted.includes('live|1'));
    const idx = list.children.findIndex(c => c.attrs && c.attrs['data-v'] === 'dQw4w9WgXcQ');
    check('chat card right after the description block', idx === 1 && list.children[2] === recs);
    const frame = list.children[1].children[1];
    check('official live chat in it', frame.src === 'https://www.youtube.com/live_chat?v=dQw4w9WgXcQ&embed_domain=m.youtube.com');
    win.__ssChatInline = false; tick();
    check('no inline chat on wide screens', list.children.length === 2);
  }
  { // 26. No description card of the app's own (YouTube shows its description); the chat card follows the information
    let tick = null;
    const mkEl = (tag) => { const o = { tagName: tag, className: '', children: [], parentElement: null, style: {}, attrs: {}, listeners: {},
      setAttribute(k, v) { o.attrs[k] = v; }, getAttribute: k => o.attrs[k], appendChild(c) { o.children.push(c); c.parentElement = o; return c; },
      insertBefore(c, ref) { if (c.parentElement) c.parentElement.removeChild(c); const i = ref ? o.children.indexOf(ref) : -1; if (i < 0) o.children.push(c); else o.children.splice(i, 0, c); c.parentElement = o; return c; },
      removeChild(c) { o.children.splice(o.children.indexOf(c), 1); c.parentElement = null; }, addEventListener(t, f) { o.listeners[t] = f; }, querySelector: () => null,
      get nextSibling() { const p = o.parentElement; return p ? p.children[p.children.indexOf(o) + 1] || null : null; },
      get isConnected() { return !!o.parentElement; } }; return o; };
    const body = mkEl('BODY'); const watch = mkEl('YTM-WATCH'); body.appendChild(watch);
    const list = mkEl('DIV'); watch.appendChild(list);
    const meta = mkEl('DIV'); list.appendChild(meta);
    const recs = mkEl('DIV'); list.appendChild(recs);
    list.querySelector = q => (q.includes('ytm-video-with-context-renderer') ? {} : null); watch.querySelector = list.querySelector;
    const d = mkEl('DIV'); meta.appendChild(d);
    const mp = { getVideoData: () => ({ isLive: true }), getPlayerResponse: () => ({ videoDetails: { videoId: 'dQw4w9WgXcQ', shortDescription: 'Intro\n1:05 Part one' } }) };
    const doc = { body, getElementById: id => (id === 'movie_player' ? mp : null), createElement: t => mkEl(t.toUpperCase()),
      createTextNode: t => ({ nodeType: 3, text: t }), querySelector: q => (q.includes('description') ? d : null), addEventListener() {} };
    const realInterval = globalThis.setInterval; globalThis.setInterval = f => { if (!tick) tick = f; return 1; };
    new Function('window', 'document', 'location', 'URL', 'setInterval', liveSrc)({ streamShellSponsor: { postMessage() {} } }, doc, { href: 'https://m.youtube.com/watch?v=dQw4w9WgXcQ', hostname: 'm.youtube.com' }, URL, globalThis.setInterval);
    globalThis.setInterval = realInterval;
    tick();
    check('no duplicate description card', !list.children.some(c => c.id === 'streamshell-description'));
    check('live chat card right after the video information', list.children[1] && list.children[1].id === 'streamshell-live-chat' && list.children[2] === recs);
  }
  { // 27. Sound on: an opened video plays with sound (once per video, not during an ad; your own mute afterwards stays)
    const listeners = {}; const intervals = [];
    let muted = true, ad = true, unmuteCalls = 0;
    const mp = { classList: { contains: c => ad && c === 'ad-showing' }, isMuted: () => muted, unMute() { muted = false; unmuteCalls++; }, getVideoData: () => ({ isLive: false }) };
    const video = { tagName: 'VIDEO', muted: true, paused: false, closest: () => mp };
    const doc = { body: {}, getElementById: id => (id === 'movie_player' ? mp : null), querySelector: q => (q.includes('video') ? video : null),
      addEventListener: (t, f) => { listeners[t] = f; }, createElement: () => ({}) };
    const realInterval = globalThis.setInterval, realTimeout = globalThis.setTimeout; const timeouts = [];
    globalThis.setInterval = f => { intervals.push(f); return 1; }; globalThis.setTimeout = f => { timeouts.push(f); return 1; };
    const loc = { href: 'https://m.youtube.com/watch?v=dQw4w9WgXcQ', hostname: 'm.youtube.com' };
    new Function('window', 'document', 'location', 'URL', 'setInterval', liveSrc)({}, doc, loc, URL, globalThis.setInterval);
    globalThis.setInterval = realInterval;
    listeners.playing({ target: video });
    check('no sound forced during an ad', unmuteCalls === 0 && video.muted);
    ad = false; listeners.playing({ target: video });
    check('video starts with sound (YouTube player and element)', !muted && !video.muted);
    timeouts.splice(0).forEach(f => f()); globalThis.setTimeout = realTimeout;
    muted = true; video.muted = true; const before = unmuteCalls; listeners.playing({ target: video }); intervals.forEach(f => f());
    check('your own mute later is respected', muted && video.muted && unmuteCalls === before);
    loc.href = 'https://m.youtube.com/watch?v=aaaaaaaaaaa'; intervals.forEach(f => f());
    check('next video: sound again (via the regular check)', !muted && !video.muted);
  }
  { // 28. Fullscreen on/off does not start the video over: back to the last position, still playing
    const listeners = {}; const intervals = [];
    const video = { currentTime: 120, paused: false, seeking: false, readyState: 4, duration: 600, play() { this.paused = false; } };
    const seeks = [];
    const mp = { seekTo(t) { seeks.push(t); video.currentTime = t; }, playVideo() { video.paused = false; } };
    const player = { classList: { contains: () => false } };
    const doc = { addEventListener: (t, f) => { (listeners[t] = listeners[t] || []).push(f); }, documentElement: {},
      querySelector: q => (q.includes('video') ? video : q === '.html5-video-player' ? player : null), querySelectorAll: () => [], getElementById: () => mp };
    const realInterval = globalThis.setInterval; globalThis.setInterval = f => { intervals.push(f); return 1; };
    new Function('window', 'document', 'location', 'URL', tsSrc)({}, doc, { href: 'https://m.youtube.com/watch?v=dQw4w9WgXcQ', pathname: '/watch' }, URL);
    globalThis.setInterval = realInterval;
    const tickAll = () => intervals.forEach(f => f());
    tickAll();                                   // position 120 s remembered
    (listeners.fullscreenchange || []).forEach(f => f());
    video.currentTime = 0.4; video.paused = true; video.readyState = 0; video.duration = NaN;  // YouTube re-loads its player
    tickAll();
    check('no jump while YouTube is still re-loading the media', seeks.length === 0);
    video.readyState = 4; video.duration = 600;   // media ready again, at the start
    tickAll();
    check('back to where it was after the fullscreen switch', seeks[0] === 120 && video.currentTime === 120);
    check('and playing', !video.paused);
    video.currentTime = 10; tickAll(); tickAll();  // you seek back yourself later: second guard at most, then never
    check('the guard tries only once', seeks.length === 1);
    let n = seeks.length; video.currentTime = 120; tickAll();
    (listeners.fullscreenchange || []).forEach(f => f()); video.currentTime = 110; tickAll(); tickAll();
    check('your own 10 s back jump right after fullscreen is kept', seeks.length === n && video.currentTime === 110);
    (listeners.fullscreenchange || []).forEach(f => f()); (listeners.seeking || []).forEach(f => f()); video.currentTime = 0.5; tickAll();
    check('your own jump to the start right after fullscreen is kept', seeks.length === n && video.currentTime === 0.5);
  }
  { // 28b. Fullscreen early in a video (0.4.3: at 2.8 s it started over at 0); never "corrects" a spot really near the start
    const run = (pos, after) => {
      const listeners = {}; const intervals = [];
      const video = { currentTime: pos, paused: false, seeking: false, readyState: 4, duration: 600, play() { this.paused = false; } };
      const seeks = [];
      const mp = { seekTo(t) { seeks.push(t); video.currentTime = t; }, playVideo() { video.paused = false; } };
      const player = { classList: { contains: () => false } };
      const doc = { addEventListener: (t, f) => { (listeners[t] = listeners[t] || []).push(f); }, documentElement: {},
        querySelector: q => (q.includes('video') ? video : q === '.html5-video-player' ? player : null), querySelectorAll: () => [], getElementById: () => mp };
      const realInterval = globalThis.setInterval; globalThis.setInterval = f => { intervals.push(f); return 1; };
      new Function('window', 'document', 'location', 'URL', tsSrc)({}, doc, { href: 'https://m.youtube.com/watch?v=dQw4w9WgXcQ', pathname: '/watch' }, URL);
      globalThis.setInterval = realInterval;
      intervals.forEach(f => f());
      (listeners.fullscreenchange || []).forEach(f => f());
      video.currentTime = after; intervals.forEach(f => f());
      return seeks;
    };
    const a = run(2.8, 0);
    check('fullscreen at 2.8 s: back to 2.8 instead of 0', a.length === 1 && a[0] === 2.8);
    check('fullscreen at 1.2 s: left alone (too close to the start)', run(1.2, 0).length === 0);
    check('fullscreen at 1.8 s, now at 1.0 s: left alone (less than 1 s apart)', run(1.8, 1.0).length === 0);
    check('fullscreen at 120 s still guarded as before', run(120, 0.3)[0] === 120);
  }
  { // 28c. (0.4.5) Fullscreen right at the start, YouTube rebuilds its player and leaves it standing: played once again;
    // never when you paused yourself
    const make = () => {
      const listeners = {}; const intervals = []; let now = 1000000;
      const video = { tagName: 'VIDEO', currentTime: 0.5, paused: false, ended: false, seeking: false, readyState: 4, duration: 2318, plays: 0,
        play() { this.plays++; this.paused = false; return Promise.resolve(); } };
      const mp = { plays: 0, seekTo() {}, playVideo() { this.plays++; video.paused = false; } };
      const player = { classList: { contains: () => false } };
      const doc = { addEventListener: (t, f) => { (listeners[t] = listeners[t] || []).push(f); }, documentElement: {},
        querySelector: q => (q.includes('video') ? video : q === '.html5-video-player' ? player : null), querySelectorAll: () => [], getElementById: () => mp };
      const realInterval = globalThis.setInterval; const realNow = Date.now;
      globalThis.setInterval = f => { intervals.push(f); return 1; }; Date.now = () => now;
      new Function('window', 'document', 'location', 'URL', tsSrc)({}, doc, { href: 'https://m.youtube.com/watch?v=dQw4w9WgXcQ', pathname: '/watch' }, URL);
      globalThis.setInterval = realInterval;
      const fire = (t, target) => (listeners[t] || []).forEach(f => f({ target }));
      return { video, mp, fire, tick: ms => { now += ms; intervals.forEach(f => f()); }, restore: () => { Date.now = realNow; }, at: () => now, set: n => { now = n; } };
    };
    let e = make();
    e.fire('playing', e.video); e.tick(250);
    e.fire('touchend', {}); e.tick(100); e.fire('fullscreenchange', {});   // tap on the fullscreen button
    e.video.paused = true; e.fire('pause', e.video); e.video.currentTime = 0; // YouTube rebuilds and stops
    e.tick(500);
    check('keep playing: not before 1.5 s after the switch', e.mp.plays === 0);
    e.tick(1200);
    check('keep playing: standing video started once again', e.mp.plays === 1 && !e.video.paused);
    e.video.paused = true; e.tick(1000); e.tick(1000);
    check('keep playing: only once', e.mp.plays === 1);
    e.restore();
    e = make();
    e.fire('playing', e.video); e.tick(250);
    e.fire('touchend', {}); e.tick(100); e.fire('fullscreenchange', {});
    e.tick(400); e.fire('touchend', {}); e.video.paused = true; e.fire('pause', e.video); // your own pause after the switch
    e.tick(2000); e.tick(1000);
    check('keep playing: your own pause is respected', e.mp.plays === 0 && e.video.paused);
    e.restore();
    e = make();
    e.fire('touchend', {}); e.video.paused = true; e.fire('pause', e.video);  // paused before, by you
    e.tick(3000); e.fire('fullscreenchange', {}); e.tick(2000); e.tick(1000);
    check('keep playing: a video paused before the switch stays paused', e.mp.plays === 0);
    e.restore();
  }
  { // 29. Chapters from the description text (mobile page has no description in the DOM); tap on the chapter title
    // next to the time opens the strip in fullscreen, a tap on the timeline never does
    const listeners = {}; const intervals = []; const posted = [];
    const mp = { getPlayerResponse: () => ({ videoDetails: { videoId: 'dQw4w9WgXcQ', lengthSeconds: '214', shortDescription: 'Battle report\n0:00 Intro\n0:41 Initial deployment\n1:30 – Push to B\nsee 2:00 in text' },
      storyboards: { playerStoryboardSpecRenderer: { spec: 'https://i.ytimg.com/sb/dQw4w9WgXcQ/storyboard3_L$L/$N.jpg|160#90#107#5#5#2000#M$M#rs$X' } } }) };
    const doc = { addEventListener: (t, f) => { (listeners[t] = listeners[t] || []).push(f); }, documentElement: {}, fullscreenElement: null,
      querySelector: () => null, querySelectorAll: () => [], getElementById: id => (id === 'movie_player' ? mp : null) };
    const win = { streamShellSponsor: { postMessage: m => posted.push(m) } };
    const realInterval = globalThis.setInterval; globalThis.setInterval = f => { intervals.push(f); return 1; };
    new Function('window', 'document', 'location', 'URL', tsSrc)(win, doc, { href: 'https://m.youtube.com/watch?v=dQw4w9WgXcQ', pathname: '/watch' }, URL);
    globalThis.setInterval = realInterval;
    intervals.forEach(f => f());
    const list = posted.filter(m => m.startsWith('chapters|[')).map(m => JSON.parse(m.slice(9))).pop() || [];
    check('timeline preview pattern reported once', posted.filter(m => m.startsWith('storyboard|')).length === 1 && posted.find(m => m.startsWith('storyboard|')) === 'storyboard|dQw4w9WgXcQ|214|https://i.ytimg.com/sb/dQw4w9WgXcQ/storyboard3_L$L/$N.jpg|160#90#107#5#5#2000#M$M#rs$X');
    check('chapters taken from the description lines', list.length === 3 && list[1].t === 41 && list[1].l === 'Initial deployment' && list[2].l === 'Push to B');
    doc.fullscreenElement = {};
    const mkTarget = (cls, text, parent) => ({ className: cls, textContent: text, parentElement: parent || null, getAttribute: () => null });
    let prevented = false; const ev = t => ({ target: t, preventDefault() { prevented = true; }, stopImmediatePropagation() {} });
    const bar = mkTarget('ytp-progress-bar ytp-chapters-container', '', null);
    const tap = (t) => { (listeners.touchstart || []).forEach(f => f(ev(t))); (listeners.touchend || []).forEach(f => f(ev(t))); };
    tap(mkTarget('ytp-chapter-hover-container', '', bar));
    check('a tap on the timeline is left to YouTube', !prevented && !posted.includes('chapters|open'));
    const faded = mkTarget('x-title', 'Initial deployment', { className: 'controls', textContent: '', parentElement: null, getAttribute: () => null, _opacity: '0' });
    tap(faded);
    check('controls faded out: the tap is YouTube\'s (it shows them)', !prevented && !posted.includes('chapters|open'));
    tap(mkTarget('x-title', 'Initial deployment ›'));
    check('a tap on the chapter title opens the strip (decided at finger-down)', prevented && posted.includes('chapters|open'));
  }
  { // 30. Flat sheet under the player, also one that slides over the player's bottom edge (and its ::before layer)
    const m = compactSrc.match(/var flatAt=0;[\s\S]*?e\.classList\.add\('ss-flat'\);\}\}/);
    check('flatAll found in compact script', !!m);
    if (m) {
      const mk = (w, top, rad, before) => { const cl = new Set(); return { tagName: 'DIV', cl, classList: { contains: c => cl.has(c), add: c => cl.add(c) },
        getBoundingClientRect: () => ({ width: w, top }), rad, before, contains: () => false }; };
      const player = { contains: () => false };
      const sheet = mk(400, 228, '16px'), beforeOnly = mk(400, 300, '0px', '16px'), card = mk(180, 260, '12px'), far = mk(400, 900, '16px');
      const root = { getElementsByTagName: () => [sheet, beforeOnly, card, far] };
      const gcs = (e, pseudo) => pseudo ? { content: e.before ? '""' : 'none', borderTopLeftRadius: e.before || '0px' } : { borderTopLeftRadius: e.rad, borderTopRightRadius: e.rad };
      const flatAll = new Function('window', 'document', 'getComputedStyle', 'Date', m[0] + '; return flatAll;')({ innerWidth: 400 }, { querySelector: () => root, body: root }, gcs, { now: () => 5000 });
      flatAll(player, { bottom: 240 });
      check('sheet overlapping the player made flat', sheet.cl.has('ss-flat'));
      check('rounded ::before layer made flat', beforeOnly.cl.has('ss-flat'));
      check('narrow cards and far-away surfaces keep their rounding', !card.cl.has('ss-flat') && !far.cl.has('ss-flat'));
    }
  }
  { // 31. Chapters from YouTube's own watch data (also automatic chapters, with YouTube's pictures); its chapter button
    const listeners = {}; const intervals = []; const posted = [];
    const chapter = (ms, title, url) => ({ chapterRenderer: { title: { simpleText: title }, timeRangeStartMillis: ms, thumbnail: { thumbnails: [{ url: url + '?s=1', width: 168 }, { url: url, width: 336 }, { url: url + '?big', width: 1280 }] } } });
    let nextVid = 'OTHERvideo1';
    const next = () => ({ currentVideoEndpoint: { watchEndpoint: { videoId: nextVid } },
      playerOverlays: { playerOverlayRenderer: { decoratedPlayerBarRenderer: { decoratedPlayerBarRenderer: { playerBar: { multiMarkersPlayerBarRenderer: { markersMap: [
        { key: 'HEATSEEKER', value: {} },
        { key: 'AUTO_CHAPTERS', value: { chapters: [chapter(0, 'Intro', 'https://i.ytimg.com/vi/dQw4w9WgXcQ/hqdefault_0.jpg'), chapter(41000, 'Initial deployment', 'https://i.ytimg.com/vi/dQw4w9WgXcQ/hqdefault_41000.jpg'), chapter(95500, 'Push', 'https://evil.example/x.jpg')] } },
      ] } } } } } } });
    const mp = { getWatchNextResponse: next, getPlayerResponse: () => ({ videoDetails: { videoId: 'dQw4w9WgXcQ', shortDescription: 'no time codes here' } }) };
    const doc = { addEventListener: (t, f) => { (listeners[t] = listeners[t] || []).push(f); }, documentElement: {}, fullscreenElement: null,
      querySelector: () => null, querySelectorAll: () => [], getElementById: id => (id === 'movie_player' ? mp : null) };
    const realInterval = globalThis.setInterval; globalThis.setInterval = f => { intervals.push(f); return 1; };
    new Function('window', 'document', 'location', 'URL', tsSrc)({ streamShellSponsor: { postMessage: m => posted.push(m) } }, doc, { href: 'https://m.youtube.com/watch?v=dQw4w9WgXcQ', pathname: '/watch' }, URL);
    globalThis.setInterval = realInterval;
    intervals.forEach(f => f());
    check('watch data of another video is ignored', !posted.some(m => m.startsWith('chapters|[{')));
    nextVid = 'dQw4w9WgXcQ'; intervals.forEach(f => f());
    const list = posted.filter(m => m.startsWith('chapters|[{')).map(m => JSON.parse(m.slice(9))).pop() || [];
    check('automatic chapters from YouTube\'s watch data', list.length === 3 && list[1].t === 41 && list[1].l === 'Initial deployment' && list[2].t === 95);
    check('YouTube\'s own picture per chapter (i.ytimg.com only)', list[1].i === 'https://i.ytimg.com/vi/dQw4w9WgXcQ/hqdefault_41000.jpg' && list[2].i === '');
    doc.fullscreenElement = {}; let prevented = false;
    const label = { className: '', textContent: 'Initial deployment', parentElement: null, getAttribute: () => null };
    const button = { className: 'ytwPlayerTimeDisplayPill ytwPlayerTimeDisplayPlayerBarButton', textContent: 'Initial deployment', parentElement: null, getAttribute: () => null };
    label.parentElement = button;
    const before = posted.filter(m => m === 'chapters|open').length;
    const e31 = { target: label, preventDefault() { prevented = true; }, stopImmediatePropagation() {} };
    (listeners.pointerdown || []).forEach(f => f(e31)); (listeners.click || []).forEach(f => f(e31));
    check('tap on YouTube\'s chapter button opens the strip', prevented && posted.filter(m => m === 'chapters|open').length === before + 1);
  }
  { // 32. The style covers YouTube's own 48 px top-bar offsets from its mobile CSS (no white band while a video opens)
    const css = compactSrc;
    check('player at the top while the video opens (.player-container absolute top:48px)', css.includes('.player-container{top:0!important}'));
    check('feed chip bar, panel and scroll padding without the top bar', css.includes('ytm-feed-filter-chip-bar-renderer{top:0!important}') && css.includes('ytm-engagement-panel{top:56.25vw!important}') && css.includes('scroll-padding-top:0!important'));
    check('no black page behind the white sheet (page-black-background)', css.includes('.page-container.page-black-background{background:transparent!important}'));
    check('guard also moves an absolutely positioned player up', /position==='absolute'\)&&parseFloat\(cs\.top\)>0/.test(css));
  }
  { // 33. Long title under the player: cut-off text becomes a moving, swipeable line
    const m = compactSrc.match(/var mq=null,mqAt=0;[\s\S]*?requestAnimationFrame\(step\);\}\)\(\);\}/);
    check('marquee found in compact script', !!m);
    if (m) {
      const mk = (top, w, sw, ellipsis, text) => { const cl = new Set(); const l = {}; return { tagName: 'H2', cl, classList: { add: c => cl.add(c) }, textContent: text, scrollLeft: 0, scrollWidth: sw, clientWidth: w, scrollHeight: 20, clientHeight: 20,
        isConnected: true, getBoundingClientRect: () => ({ top, height: 20 }), addEventListener: (t, f) => { l[t] = f; }, l, ellipsis, contains: () => false }; };
      const shortLine = mk(250, 380, 380, true, 'Short'), title = mk(262, 380, 900, true, 'FORCED CAPITULATION: Trump DEMANDS a very long title'), meta = mk(300, 380, 600, false, '@channel');
      const root = { getElementsByTagName: () => [shortLine, title, meta] };
      let clock = 10000; const frames = [];
      const api = new Function('document', 'getComputedStyle', 'Date', 'requestAnimationFrame', m[0] + '; return marquee;')(
        { querySelector: () => root, body: root }, e => ({ textOverflow: e.ellipsis ? 'ellipsis' : 'clip', webkitLineClamp: 'none', getPropertyValue: () => '' }), { now: () => clock }, f => frames.push(f));
      const player = { contains: () => false };
      api(player, { bottom: 240 });
      check('the cut-off title is chosen (not short or uncut lines)', title.cl.has('ss-marquee') && !shortLine.cl.has('ss-marquee') && !meta.cl.has('ss-marquee'));
      const run = (ms) => { const end = clock + ms; while (clock < end && frames.length) { clock += 30; frames.shift()(); } };
      run(1500); check('waits a moment before moving', title.scrollLeft === 0);
      run(3000); check('then slides along the title', title.scrollLeft > 20);
      title.l.touchstart(); const at = title.scrollLeft; run(2000);
      check('a touch pauses it', title.scrollLeft === at);
    }
  }
  { // 34. Foldable (wider than tall): YouTube places the description panel absolutely with its landscape rule; it is moved
    // to start right at the player's bottom edge (no white row in between)
    const start = compactSrc.indexOf('var docked=[],panelOpen=false;'), end = compactSrc.indexOf('var trail=0;');
    check('dock found in compact script', start >= 0 && end > start);
    if (start >= 0 && end > start) {
      const panel = { tagName: 'YTM-ENGAGEMENT-PANEL', isConnected: true, parentElement: null, contains: () => false, top: 444,
        style: { setProperty(k, v) { if (k === 'top') panel.top = parseFloat(v); } }, getBoundingClientRect: () => ({ top: panel.top + 12, height: 500, bottom: panel.top + 512 }) };
      const doc = { body: {}, elementsFromPoint: () => [], querySelectorAll: () => [panel] };
      const dock = new Function('document', 'getComputedStyle', 'window', 'H', compactSrc.slice(start, end) + '; return dock;')(
        doc, () => ({ position: 'absolute', top: panel.top + 'px', display: 'block', visibility: 'visible', opacity: '1', height: '500px', bottom: 'auto' }), { innerHeight: 900 }, 48);
      dock({ contains: () => false }, 396, 300);   // player bottom at 396, the panel showed at 456 (60 px too low)
      check('panel starts right at the player bottom', panel.getBoundingClientRect().top === 396);
      dock({ contains: () => false }, 396, 300);
      check('and stays there', panel.getBoundingClientRect().top === 396);
    }
  }
  { // 35. Wide foldable page (the user's screenshot): a short title that does not reach the middle of the page, the meta
    // line below it that does, and an empty wrapper on top of them. The TITLE must end up 12 px under the player, never
    // pushed under it.
    const m = compactSrc.match(/var pulled=null[\s\S]*?function closeGap\(p\)\{[\s\S]*?'important'\);\}\}/);
    const ip = compactSrc.match(/function inPanel\(el\)\{[\s\S]*?return false;\}/);
    if (m && ip) {
      const mk = (tag, props) => Object.assign({ tagName: tag, dataset: {}, firstChild: null, parentElement: null,
        cs: { position: 'static', backgroundColor: 'rgba(0, 0, 0, 0)', marginTop: '0px' },
        style: { v: {}, setProperty(k, val, pr) { this.v[k] = [val, pr]; }, removeProperty(k) { delete this.v[k]; }, getPropertyValue(k) { return this.v[k] ? this.v[k][0] : ''; }, getPropertyPriority(k) { return this.v[k] ? this.v[k][1] : ''; } },
        contains(o) { for (let x = o; x; x = x.parentElement) if (x === this) return true; return false; } }, props);
      const html = mk('HTML'); const body = mk('BODY', { parentElement: html }); body.cs.backgroundColor = 'rgb(255, 255, 255)';
      const watch = mk('YTM-WATCH', { parentElement: body });
      const player = mk('DIV', { parentElement: watch }); player.cs.position = 'fixed';
      const results = mk('DIV', { parentElement: watch });
      const wrap = mk('DIV', { parentElement: results });
      const title = mk('H2', { parentElement: wrap }); title.firstChild = { nodeType: 3, nodeValue: 'BRACA HILL STALI NA NOGE', nextSibling: null };
      const meta = mk('SPAN', { parentElement: wrap }); meta.firstChild = { nodeType: 3, nodeValue: '@Nugato 3,3 tis. lajkova upravo gleda više', nextSibling: null };
      const shift = () => { const v = results.style.v['margin-top']; return v ? parseFloat(v[0]) : 0; };
      player.getBoundingClientRect = () => ({ top: 0, bottom: 300, height: 300 });
      results.getBoundingClientRect = () => ({ top: 260 + shift(), bottom: 2000 });
      wrap.getBoundingClientRect = () => ({ top: 262 + shift(), bottom: 400 + shift() });
      title.getBoundingClientRect = () => ({ top: 270 + shift(), bottom: 300 + shift(), left: 30, right: 245 });
      meta.getBoundingClientRect = () => ({ top: 330 + shift(), bottom: 350 + shift(), left: 30, right: 354 });
      const hitIn = (e, x, y) => { const b = e.getBoundingClientRect(); return y >= b.top && y < b.bottom && x >= b.left && x < b.right; };
      const doc = { body, documentElement: html,
        elementsFromPoint(x, y) { const pre = y < 300 ? [player] : []; const stack = [];
          if (hitIn(title, x, y)) stack.push(title); if (hitIn(meta, x, y)) stack.push(meta);
          const wb = wrap.getBoundingClientRect(); if (y >= wb.top && y < wb.bottom) stack.push(wrap);
          return pre.concat(stack, [results, watch, body, html]); } };
      const gcs = (el) => Object.assign({}, el.cs, { marginTop: el === results && shift() ? shift() + 'px' : el.cs.marginTop });
      const clock = { t: 1000, now() { return this.t; } };
      const api = new Function('window', 'document', 'getComputedStyle', 'Date', 'setTimeout', 'playerEl',
        'var panelOpen=false;' + ip[0] + m[0] + '; return {settle:settle};')({ scrollY: 0, innerWidth: 704, innerHeight: 640 }, doc, gcs, clock, () => 0, () => player);
      for (let i = 0; i < 5; i++) { clock.t += 500; api.settle(player); }
      check('wide page: the short title is found (not the longer line below it)', title.getBoundingClientRect().top === 312);
      check('title fully below the player, meta line below the title', title.getBoundingClientRect().top >= 300 && meta.getBoundingClientRect().top > title.getBoundingClientRect().bottom);
    }
  }
  { // 36. Switching videos: the address shows the new video while the player still plays the old one at 10:00 — that
    // position must not be remembered for the new video (an ad's reload would start the new video in the middle)
    const e = makeEnv(); e.enableReload(); e.classes.delete('ad-showing');
    let playing = 'OLDvideo000'; e.mp.getVideoData = () => ({ video_id: playing });
    e.content('blob:old', 1200); e.video._t = 600; e.video.paused = false;
    e.run(adsSrc); e.advance(1000);                        // URL already dQw4w9WgXcQ, player still the old video
    playing = 'dQw4w9WgXcQ'; e.content('blob:ad2', 700); e.classes.add('ad-showing'); e.advance(4600);
    check('ad reload of the new video starts at its beginning, not at the old video\'s 10:00', e.mp.loads.length === 1 && e.mp.loads[0][1] === 0);
  }
  { // 37. Same video: its own position is kept for the ad reload
    const e = makeEnv(); e.enableReload(); e.classes.delete('ad-showing');
    e.mp.getVideoData = () => ({ video_id: 'dQw4w9WgXcQ' });
    e.content('blob:main', 1200); e.video._t = 300; e.video.paused = false;
    e.run(adsSrc); e.advance(1000);
    e.content('blob:ad3', 700); e.classes.add('ad-showing'); e.advance(4600);
    check('mid-roll ad: reload continues the same video where it was', e.mp.loads.length === 1 && e.mp.loads[0][1] >= 300 && e.mp.loads[0][1] <= 302);
  }
  { // 21. An ad when a video is opened at a timestamp: the reload keeps the timestamp
    const e = makeEnv(); e.enableReload(); e.content('blob:longad', 700); e.video.paused = false; e.location.href = 'https://m.youtube.com/watch?v=dQw4w9WgXcQ&t=2m5s';
    e.run(adsSrc); e.advance(4600);
    check('reload starts at the timestamp', e.mp.loads.length === 1 && e.mp.loads[0][1] === 125);
  }
  { // 10. Router: watch links go to the app only when enabled; others untouched
    const listeners = []; const posted = [];
    const doc = { addEventListener: (t, f, c) => listeners.push({ t, f, c }) };
    const win = { streamShellNav: { postMessage: m => posted.push(m) } };
    const fire = href => { let prevented = false, stopped = false;
      const a = { href, closest: () => a, getAttribute: () => href };
      const ev = { target: { closest: () => a }, button: 0, defaultPrevented: false, preventDefault() { prevented = true; }, stopImmediatePropagation() { stopped = true; }, stopPropagation() {} };
      listeners.filter(l => l.t === 'click').forEach(l => l.f(ev)); return { prevented, stopped }; };
    new Function('window', 'document', 'location', 'URL', routerSrc)(win, doc, { href: 'https://m.youtube.com/' }, URL);
    const off = fire('https://m.youtube.com/watch?v=dQw4w9WgXcQ');
    check('router idle without flag', !off.prevented && posted.length === 0);
    win.__streamshellRouteVideos = true;
    const on = fire('https://m.youtube.com/watch?v=dQw4w9WgXcQ');
    check('watch link routed to player tab', on.prevented && posted[0] === 'https://m.youtube.com/watch?v=dQw4w9WgXcQ');
    const other = fire('https://m.youtube.com/feed/subscriptions');
    check('other links stay in the instance', !other.prevented && posted.length === 1);
    const evil = fire('https://evil.example/watch?v=dQw4w9WgXcQ');
    check('foreign hosts never routed', !evil.prevented && posted.length === 1);
  }
  {
    // 24/25: the content starts directly under the player — a white band is closed, a title hidden under the player is
    // brought out — stably (repeated passes change nothing) and never while scrolled.
    const m = compactSrc.match(/var pulled=null[\s\S]*?function closeGap\(p\)\{[\s\S]*?'important'\);\}\}/);
    const ip = compactSrc.match(/function inPanel\(el\)\{[\s\S]*?return false;\}/);
    check('closeGap found in compact script', !!(m && ip));
    const world = (titleAt) => {
      const mk = (tag, props) => Object.assign({ tagName: tag, dataset: {}, firstChild: null, parentElement: null,
        cs: { position: 'static', backgroundColor: 'rgba(0, 0, 0, 0)', marginTop: '0px' },
        style: { v: {}, setProperty(k, val, pr) { this.v[k] = [val, pr]; }, removeProperty(k) { delete this.v[k]; }, getPropertyValue(k) { return this.v[k] ? this.v[k][0] : ''; }, getPropertyPriority(k) { return this.v[k] ? this.v[k][1] : ''; } },
        contains(o) { for (let x = o; x; x = x.parentElement) if (x === this) return true; return false; } }, props);
      const html = mk('HTML'); const body = mk('BODY', { parentElement: html }); body.cs.backgroundColor = 'rgb(255, 255, 255)';
      const watch = mk('YTM-WATCH', { parentElement: body });
      const player = mk('DIV', { parentElement: watch }); player.cs.position = 'fixed';
      const results = mk('DIV', { parentElement: watch });
      const title = mk('H2', { parentElement: results }); title.firstChild = { nodeType: 3, nodeValue: 'Video title', nextSibling: null };
      const shift = () => { const v = results.style.v['margin-top']; return v ? parseFloat(v[0]) : 0; };
      player.getBoundingClientRect = () => ({ top: 0, bottom: 240, height: 240 });
      results.getBoundingClientRect = () => ({ top: 150 + shift(), bottom: 2000 });
      title.getBoundingClientRect = () => ({ top: titleAt + shift(), bottom: titleAt + 40 + shift() });
      const doc = { body, documentElement: html,
        elementsFromPoint(x, y) { const t = title.getBoundingClientRect(); const pre = y < 240 ? [player] : [];
          if (y >= t.top && y < t.bottom) return pre.concat([title, results, watch, body, html]); return pre.concat([results, watch, body, html]); } };
      const gcs = (el) => Object.assign({}, el.cs, { marginTop: el === results && shift() ? shift() + 'px' : el.cs.marginTop });
      const win = { scrollY: 0, innerWidth: 400, innerHeight: 900 };
      const clock = { t: 1000, now() { return this.t; } };
      const api = new Function('window', 'document', 'getComputedStyle', 'Date', 'setTimeout', 'playerEl',
        'var panelOpen=false;' + ip[0] + m[0] + '; return {settle:settle};')(win, doc, gcs, clock, () => 0, () => player);
      return { run() { clock.t += 500; api.settle(player); }, top: () => title.getBoundingClientRect().top, win };
    };
    if (m && ip) {
      const gap = world(340); gap.run();
      check('white band closed: title 12 px under the player (was 100 px)', gap.top() === 252);
      for (let i = 0; i < 8; i++) gap.run();
      check('repeated passes stay flush (no stacking)', gap.top() === 252);
      gap.win.scrollY = 300; gap.run();
      check('not touched while scrolled', gap.top() === 252);
      const hid = world(200); hid.run();
      check('title hidden under the player brought out, 12 px below it', hid.top() === 252);
      for (let i = 0; i < 8; i++) hid.run();
      check('and stays there', hid.top() === 252);
    }
  }
  process.exit(failures ? 1 : 0);
})();
