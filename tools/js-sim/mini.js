// Mini player and feed leftovers in a real DOM (jsdom): the compact script and the mini switch.
const fs = require('fs');
const { JSDOM } = require('jsdom');
const [compact, on, off] = [2, 3, 4].map(i => fs.readFileSync(process.argv[i], 'utf8'));
let failures = 0;
const check = (n, c) => { console.log((c ? 'PASS ' : 'FAIL ') + n); if (!c) failures++; };
// A video opened from the feed in the same page: the feed with its sticky chip row is still in the DOM.
const html = `<html><head></head><body><ytm-app><div class="page-container">
<ytm-browse><div class="rich-grid-sticky-header"><ytm-feed-filter-chip-bar-renderer id="chips">Sve · Videoigre · Uživo</ytm-feed-filter-chip-bar-renderer></div>
<ytm-rich-item-renderer id="feeditem">item</ytm-rich-item-renderer></ytm-browse>
<ytm-watch><div class="player-container" id="pc"><ytm-custom-control id="cc"><div class="player-controls-middle-core-buttons" id="mid"><button id="nextb">Next</button></div><span class="ytm-time-display" id="time">0:13 / 6:18</span></ytm-custom-control><div id="movie_player" class="html5-video-player"><video id="v"></video><div id="ctl">controls</div><div class="player-controls-content" id="pcc"><button id="pp">Pause</button></div><div class="ytp-ad-player-overlay" id="adov"><button class="ytp-skip-ad-button" id="skip">Skip</button></div></div></div>
<div id="title">Title under the player</div></ytm-watch></div></ytm-app></body></html>`;
const dom = new JSDOM(html, { url: 'https://m.youtube.com/watch?v=dQw4w9WgXcQ', runScripts: 'outside-only', pretendToBeVisual: true });
const w = dom.window, d = w.document;
w.eval(compact);
w.document.dispatchEvent(new w.Event('DOMContentLoaded'));
d.body.appendChild(d.createElement('div')); // a mutation: the script looks at the page again
setTimeout(() => {
  const cs = id => w.getComputedStyle(d.getElementById(id));
  check('video page marked (html.ss-watch)', d.documentElement.classList.contains('ss-watch'));
  check('feed leftovers (sticky chip row) hidden on the video page', cs('chips').display === 'none' || w.getComputedStyle(d.querySelector('ytm-browse')).display === 'none');
  check('player still shown', cs('pc').display !== 'none');
  w.eval(on);
  check('mini: page text invisible', cs('title').visibility === 'hidden');
  check('mini: player, video and its controls visible', cs('v').visibility === 'visible' && cs('ctl').visibility === 'visible');
  check('0.6.3 mini: own player buttons (pause, skip, ad overlay) hidden', ['pcc', 'pp', 'adov', 'skip'].every(id => cs(id).visibility === 'hidden'));
  check('0.6.4 mini: controls next to the player (previous/play/next, time) hidden', ['cc', 'mid', 'nextb', 'time'].every(id => cs(id).visibility === 'hidden'));
  check('mini: black page', /0, 0, 0/.test(w.getComputedStyle(d.body).backgroundColor));
  w.eval(on);
  check('mini twice: one style', d.querySelectorAll('#ss-mini-style').length === 1);
  w.eval(off);
  check('back to normal: page visible again', cs('title').visibility === 'visible');
  check('back to normal: player buttons visible again', cs('pp').visibility === 'visible');
  // On a feed page the mark goes away (the feed is not hidden there).
  w.history.pushState({}, '', '/'); d.body.appendChild(d.createElement('div'));
  setTimeout(() => {
    check('feed page: not marked, feed shown', !d.documentElement.classList.contains('ss-watch') && w.getComputedStyle(d.querySelector('ytm-browse')).display !== 'none');
    w.close(); process.exit(failures ? 1 : 0);
  }, 50);
}, 50);
