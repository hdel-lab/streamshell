// 0.5.0 page scripts in jsdom: feed-tab visibility shield, tap guard, Shorts routing, hidden tiles, A–B repeat.
const fs = require('fs');
const { JSDOM } = require('jsdom');
const [router, extras, hideCfg, repeat] = [2, 3, 4, 5].map(i => fs.readFileSync(process.argv[i], 'utf8'));
let failures = 0;
const check = (n, c) => { console.log((c ? 'PASS ' : 'FAIL ') + n); if (!c) failures++; };

{ // Router: shield + guard + Shorts
  const dom = new JSDOM(`<html><body>
    <ytm-media-item id="tile"><a id="a" href="/watch?v=aaaaaaaaaaa"><h3>Video</h3></a></ytm-media-item>
    <a id="shorts" href="/shorts/bbbbbbbbbbb">Short</a>
    <a id="sub" href="/feed/subscriptions">Subs</a></body></html>`, { url: 'https://m.youtube.com/', runScripts: 'outside-only' });
  const w = dom.window; const d = w.document;
  const posted = [];
  w.streamShellNav = { postMessage: m => posted.push(m) };
  let pageSawHidden = 0;
  d.addEventListener('visibilitychange', () => { pageSawHidden++; });
  w.eval(router);
  check('feed tab always "visible"', d.visibilityState === 'visible' && d.hidden === false);
  d.dispatchEvent(new w.Event('visibilitychange'));
  check('visibilitychange never reaches the page', pageSawHidden === 0);
  const touch = (type, x, y, el) => { const e = new w.Event(type, { bubbles: true, cancelable: true }); e.touches = type === 'touchend' ? [] : [{ clientX: x, clientY: y }]; el.dispatchEvent(e); };
  const click = el => { const e = new w.MouseEvent('click', { bubbles: true, cancelable: true }); el.dispatchEvent(e); return e.defaultPrevented; };
  const a = d.getElementById('a');
  touch('touchstart', 10, 10, a); touch('touchend', 10, 10, a);
  check('plain tap opens the video in the player tab', click(a) && posted[0] === 'https://m.youtube.com/watch?v=aaaaaaaaaaa');
  touch('touchstart', 10, 10, a); touch('touchmove', 12, 60, a); touch('touchend', 12, 60, a);
  check('a touch that scrolled opens nothing', click(a) && posted.length === 1);
  w.dispatchEvent(new w.Event('scroll'));
  touch('touchstart', 10, 10, a); touch('touchend', 10, 10, a);
  check('a touch that stops a scroll opens nothing', click(a) && posted.length === 1);

  // Shorts as normal videos (flag set in this export)
  const sh = d.getElementById('shorts');
  setTimeout(() => {
    touch('touchstart', 5, 5, sh); touch('touchend', 5, 5, sh);
    check('Shorts link opens as a normal video', click(sh) && posted[1] === 'https://m.youtube.com/watch?v=bbbbbbbbbbb');
    const sub = d.getElementById('sub');
    touch('touchstart', 5, 5, sub); touch('touchmove', 5, 80, sub); touch('touchend', 5, 80, sub);
    check('other links are never blocked by the guard', !click(sub));
    extrasPart();
  }, 400);
}
var return_;

function extrasPart() {
  const dom = new JSDOM(`<html><head></head><body>
    <ytm-video-with-context-renderer id="watched"><a href="/watch?v=aaaaaaaaaaa"><h3 class="media-item-headline">Seen before</h3></a><div class="media-item-byline">Nice · 1M views</div></ytm-video-with-context-renderer>
    <ytm-video-with-context-renderer id="spam"><a href="/watch?v=bbbbbbbbbbb"><h3 class="media-item-headline">Hello</h3></a><div class="media-item-byline">Spam Channel · 2 days ago</div></ytm-video-with-context-renderer>
    <ytm-video-with-context-renderer id="word"><a href="/watch?v=ccccccccccc"><h3 class="media-item-headline">Official TRAILER 2</h3></a><div class="media-item-byline">Studio</div></ytm-video-with-context-renderer>
    <ytm-video-with-context-renderer id="keep"><a href="/watch?v=ddddddddddd"><h3 class="media-item-headline">About spam channel</h3></a><div class="media-item-byline">Good · 3 views</div></ytm-video-with-context-renderer>
    </body></html>`, { url: 'https://m.youtube.com/', runScripts: 'outside-only', pretendToBeVisual: true });
  const w = dom.window; const d = w.document;
  w.streamShellSponsor = { postMessage: () => {} };
  w.eval(hideCfg); w.eval(extras);
  const hidden = id => d.getElementById(id).style.display === 'none';
  check('watched video hidden', hidden('watched'));
  check('blocked channel hidden (by its byline)', hidden('spam'));
  check('blocked word hidden (without case)', hidden('word'));
  check('a title that only mentions the channel stays', !hidden('keep'));
  w.eval('window.__ssExtras={watched:[],chans:[],words:[]};window.__ssExtrasApply();');
  check('lists cleared: everything shows again', !hidden('watched') && !hidden('spam') && !hidden('word'));
  repeatPart();
}

function repeatPart() {
  const dom = new JSDOM('<html><body><div class="html5-video-player" id="movie_player"><video></video></div></body></html>', { url: 'https://m.youtube.com/watch?v=aaaaaaaaaaa', runScripts: 'outside-only' });
  const w = dom.window; const d = w.document;
  const seeks = [];
  d.getElementById('movie_player').seekTo = t => seeks.push(t);
  const v = d.querySelector('video'); let t = 0;
  Object.defineProperty(v, 'currentTime', { get: () => t, set: x => { t = x; } });
  w.eval(repeat);
  const tick = x => { t = x; v.dispatchEvent(new w.Event('timeupdate', { bubbles: true })); };
  tick(12); tick(19.5);
  check('A–B: plays on inside the part', seeks.length === 0);
  tick(20.1);
  check('A–B: back to the start at the end of the part', seeks[0] === 10);
  w.eval('window.__ssAB=null;'); tick(25);
  check('A–B cleared: no more jumps', seeks.length === 1);
  if (failures) console.log(failures + ' failure(s)');
  process.exit(failures ? 1 : 0);
}
