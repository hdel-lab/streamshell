// Simulates YouTubeExtras in jsdom: feed filter, Return YouTube Dislike, DeArrow titles and SponsorBlock marks.
const fs = require('fs');
const { JSDOM } = require('jsdom');
const [src, cfg, answers] = [2, 3, 4].map(i => fs.readFileSync(process.argv[i], 'utf8'));
let failures = 0;
const check = (n, c) => { console.log((c ? 'PASS ' : 'FAIL ') + n); if (!c) failures++; };

const html = `<html><head></head><body><ytm-app><ytm-watch>
<div class="html5-video-player" id="movie_player" style="width:400px"><video></video><div class="ytm-progress-bar" id="bar"></div></div>
<ytm-slim-video-information-renderer><h2 class="slim-video-information-title"><span>Original watch title</span></h2></ytm-slim-video-information-renderer>
<ytm-segmented-like-dislike-button-renderer><button id="like">L</button><button id="dislike"><span class="icon"></span></button></ytm-segmented-like-dislike-button-renderer>
<ytm-video-with-context-renderer id="item"><a href="/watch?v=aaaaaaaaaaa"><h3 class="media-item-headline"><span id="t1">YOU WON'T BELIEVE THIS</span></h3></a></ytm-video-with-context-renderer>
<ytm-reel-shelf-renderer id="shorts">shorts</ytm-reel-shelf-renderer>
</ytm-watch></ytm-app></body></html>`;
const dom = new JSDOM(html, { url: 'https://m.youtube.com/watch?v=dQw4w9WgXcQ', runScripts: 'outside-only', pretendToBeVisual: true });
const w = dom.window; const d = w.document;
const posted = [];
w.streamShellSponsor = { postMessage: m => posted.push(m) };
// Layout: jsdom has none; give the player, bar and video the sizes a phone would have.
const rect = (el, r) => { el.getBoundingClientRect = () => Object.assign({ left: 0, top: 0, right: r.width, bottom: r.height, x: 0, y: 0 }, r); };
rect(d.getElementById('movie_player'), { width: 400, height: 225 });
rect(d.getElementById('bar'), { width: 380, height: 4, top: 220, bottom: 224 });
Object.defineProperty(d.querySelector('video'), 'duration', { get: () => 600 });
w.__streamshellSB = { map: { dQw4w9WgXcQ: [[60, 120, 'sponsor'], [500, 540, 'outro']] } };
w.eval(cfg); w.eval(src);

check('feed filter style installed (Shorts shelf hidden)', /ytm-reel-shelf-renderer/.test(d.getElementById('ss-feed-filter').textContent) && w.getComputedStyle(d.getElementById('shorts')).display === 'none');
check('dislike count asked for the open video', posted.includes('rytd|dQw4w9WgXcQ'));
check('DeArrow asked for the videos on the page', posted.some(m => m.startsWith('dearrow|') && m.includes('aaaaaaaaaaa') && m.includes('dQw4w9WgXcQ')));
const marks = d.getElementById('ss-sb-marks');
check('SponsorBlock marks drawn on the timeline', !!marks && marks.parentElement.id === 'bar' && marks.children.length === 2 && marks.children[0].style.left === '10%');
w.eval(answers);
const s = d.querySelector('#dislike .ss-dislikes');
check('dislike count shown in the dislike button', !!s && /12/.test(s.textContent));
check('like button untouched', !d.querySelector('#like .ss-dislikes'));
check('feed title replaced, original kept as tooltip', d.getElementById('t1').textContent === 'Clear title' && d.getElementById('t1').title === "YOU WON'T BELIEVE THIS");
check('watch page title replaced', d.querySelector('.slim-video-information-title span').textContent === 'Watch title');
w.__ssExtrasApply(); w.__ssExtrasApply();
check('repeated passes do not stack', d.querySelectorAll('.ss-dislikes').length === 1 && d.querySelectorAll('#ss-sb-marks').length === 1);
const before = posted.length; w.__ssExtrasApply();
check('ids are not asked twice', posted.length === before);
// Switching everything off: originals back, count and marks gone, style empty.
w.eval('window.__ssExtras={feedCss:"",dislikes:false,deArrow:false,markers:false};window.__ssExtrasApply();');
check('off: original titles back', d.getElementById('t1').textContent === "YOU WON'T BELIEVE THIS" && !d.getElementById('t1').title);
check('off: count, marks and style removed', !d.querySelector('.ss-dislikes') && !d.getElementById('ss-sb-marks') && d.getElementById('ss-feed-filter').textContent === '');
// In a frame the script does nothing.
const f = d.createElement('iframe'); d.body.appendChild(f); const frame = f.contentWindow;
check('inside a frame: nothing installed', frame.top !== frame && frame.eval(src) === 'frame' && !frame.__ssExtrasApply);
w.close();
process.exit(failures ? 1 : 0);
