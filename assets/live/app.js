// تلفزيونك المباشر: the screens of the approved preview, on a provider's MAG (Stalker) portal. Live.* is the app
// (MainActivity.Bridge): the portal's calls, the players under the page, the microphone. All texts in فصحى.
'use strict';
const Live = window.Live || null;
const ar = n => String(n).replace(/\d/g, d => '٠١٢٣٤٥٦٧٨٩'[d]);
const esc = s => String(s == null ? '' : s).replace(/[&<>"]/g, c => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;' })[c]);
const HUES = [200, 25, 340, 150, 270, 45, 190, 0, 110, 300];
const art = (i, a = 135) => `linear-gradient(${a}deg,hsl(${HUES[i % 10]} 45% 38%),hsl(${(HUES[i % 10] + 40) % 360} 50% 16%))`;
const hash = s => { let h = 0; for(const c of String(s)) h = (h * 31 + c.charCodeAt(0)) | 0; return Math.abs(h); };
const $ = id => document.getElementById(id);
// what is kept belongs to the account in use (favourites, where films stopped…), except the accounts themselves:
// the first account keeps the plain names (what was kept before there were accounts), the others a prefix
let NS = '';
const SHARED = new Set(['accounts', 'activeAcct', 'voiceLang']);
const store = {
  key: k => SHARED.has(k) ? k : NS + k,
  get(k, d){ try{ const v = localStorage.getItem(store.key(k)); return v == null ? d : JSON.parse(v); }catch(e){ return d; } },
  set(k, v){
    const t = JSON.stringify(v);
    try{ localStorage.setItem(store.key(k), t); }
    catch(e){   // the storage is full: the kept details of films go (they are asked again), then once more
      try{ Object.keys(localStorage).filter(x => x.includes('det:')).forEach(x => localStorage.removeItem(x)); localStorage.setItem(store.key(k), t); }catch(e2){}
    }
  },
};
let CFG = {};
try{ CFG = JSON.parse(Live ? Live.config() : '{}'); }catch(e){}
// ---- the accounts: each a name and its link («urtv 1», «urtv 2»…); the app itself holds the link in use
let ACCTS = store.get('accounts', []), ACTIVE = store.get('activeAcct', 0);
if(!ACCTS.length && CFG.portal){ ACCTS = [{ id: 0, name: 'urtv 1', url: CFG.portal }]; ACTIVE = 0; store.set('accounts', ACCTS); store.set('activeAcct', 0); }
const curAcct = () => ACCTS.find(a => a.id === ACTIVE) || null;
if(curAcct() && CFG.portal && curAcct().url !== CFG.portal){ curAcct().url = CFG.portal; store.set('accounts', ACCTS); }
NS = ACTIVE ? 'a' + ACTIVE + ':' : '';

// ---- the portal: Live.call(id, query) answers window.__done(id, ok, text)
let SEQ = 0; const PEND = {};
// a refusal (461, 429, 403, 401): the server is left alone for 10 minutes, so a short pause does not grow longer
let PAUSE_UNTIL = store.get('pauseUntil', 0);
const paused = () => Date.now() < PAUSE_UNTIL;
const pauseMsg = () => 'رفض السيرفر الطلبات، والتطبيق متوقف عن الطلب حتى ' + new Date(PAUSE_UNTIL).toTimeString().slice(0, 5).replace(/\d/g, d => '٠١٢٣٤٥٦٧٨٩'[d]) + ' حتى لا يطول الإيقاف';
window.__done = (id, ok, text) => {
  const p = PEND[id]; if(!p) return; delete PEND[id];
  if(!ok && /\((461|429|403|401|458)\)/.test(text)){ PAUSE_UNTIL = Date.now() + 10 * 60e3; store.set('pauseUntil', PAUSE_UNTIL); }
  ok ? p.res(text) : p.rej(new Error(text));
};
// a quiet counter of the requests (never shown): many in a short time and the next ones wait a little longer,
// so the server never sees a burst that it would answer by cutting the connection
const HITS = [];
function gap(){
  const now = Date.now(); while(HITS.length && now - HITS[0] > 10000) HITS.shift();
  const n = HITS.length;
  return n < 15 ? 0 : n < 30 ? 150 : n < 50 ? 400 : n < 80 ? 900 : 1800;
}
let GATE = Promise.resolve();
function gate(){ const p = GATE.then(() => { const w = gap(); return w ? new Promise(r => setTimeout(r, w)) : null; }).then(() => { HITS.push(Date.now()); }); GATE = p; return p; }
function raw(q){ return gate().then(() => new Promise((res, rej) => { if(paused()) return rej(new Error(pauseMsg())); if(!Live) return rej(new Error('خارج التطبيق')); const id = ++SEQ; PEND[id] = { res, rej }; Live.call(id, q); })); }
const call = q => raw(q).then(t => { try{ return JSON.parse(t); }catch(e){ return null; } });
const enc = encodeURIComponent;
const abs = u => !u ? '' : /^https?:/.test(u) ? u : u.startsWith('/') ? CFG.root + u : CFG.root + '/stalker_portal/misc/logos/320/' + u;
const streamUrl = cmd => String(cmd || '').replace(/^(ffmpeg|auto|ffrt\d?)\s+/i, '').trim();
async function link(type, cmd, extra = ''){
  const j = await call(`type=${type}&action=create_link&cmd=${enc(cmd)}${extra}&forced_storage=undefined&disable_ad=0&download=0`);
  const u = streamUrl(j && (j.cmd || j.url));
  if(!u) throw new Error('لا رابط للبث');
  return u;
}

// ---- Xtream (a get.php or player_api.php link from the provider): the same screens, another source.
// The link's address, user and password stay on this TV (the app's own storage); nothing is shown.
let XT = null;   // { base, u, p } when the link is an Xtream one
function parseXtream(link){
  try{
    const url = new URL(/^https?:/.test(link) ? link : 'http://' + link);
    const u = url.searchParams.get('username'), pw = url.searchParams.get('password') || url.searchParams.get('pasword');
    if(!u || !pw) return null;
    return { base: url.origin, u, p: pw };
  }catch(e){ return null; }
}
function rawGet(url){ return gate().then(() => new Promise((res, rej) => { if(paused()) return rej(new Error(pauseMsg())); if(!Live) return rej(new Error('خارج التطبيق')); const id = ++SEQ; PEND[id] = { res, rej }; Live.get(id, url); })); }
// the long lists come trimmed by the app (only what the screens use)
function rawSlim(url, fields){ if(!Live || !Live.getSlim) return rawGet(url);
  return gate().then(() => new Promise((res, rej) => { if(paused()) return rej(new Error(pauseMsg())); const id = ++SEQ; PEND[id] = { res, rej }; Live.getSlim(id, url, fields); })); }
// FRESH: ask the server (a refresh); otherwise the copy kept on the TV first, the server only without one
let FRESH = false, OLDEST = 0;
function kept(url){
  if(FRESH || !Live || !Live.cacheGet) return null;
  const t = Live.cacheGet(url); if(!t) return null;
  OLDEST = Math.max(OLDEST, +Live.cacheAge(url) || 0);
  try{ return JSON.parse(t); }catch(e){ return null; }
}
const xapi = q => `${XT.base}/player_api.php?username=${enc(XT.u)}&password=${enc(XT.p)}${q}`;
const xlist = (q, fields) => { const k = kept(xapi(q)); return k ? Promise.resolve(k) : rawSlim(xapi(q), fields).then(t => { try{ return JSON.parse(t); }catch(e){ return []; } }); };
const xcats = q => { const k = kept(xapi(q)); return k ? Promise.resolve(k) : rawGet(xapi(q)).then(t => { try{ return JSON.parse(t); }catch(e){ return []; } }); };
const xget = q => rawGet(`${XT.base}/player_api.php?username=${enc(XT.u)}&password=${enc(XT.p)}${q}`).then(t => { try{ return JSON.parse(t); }catch(e){ return null; } });
const xurl = (kind, id, ext) => `${XT.base}/${kind}/${enc(XT.u)}/${enc(XT.p)}/${id}.${ext || 'ts'}`;
const b64 = t => { try{ return decodeURIComponent(escape(atob(t))); }catch(e){ return t || ''; } };
let XACCOUNT = null;
async function xLogin(force){
  const k = store.get('acct:' + XT.base + XT.u, null);
  if(!force && k && Date.now() - k.at < 24 * 3600e3){ XACCOUNT = k.ui; return; }
  const j = await xget('');
  if(!j || !j.user_info) throw new Error('تعذّر الاتصال بالسيرفر، تحقق من الرابط');
  const ui = j.user_info;
  if(String(ui.auth) !== '1') throw new Error('اسم المستخدم أو كلمة المرور غير صحيحة');
  if(ui.status && ui.status !== 'Active') throw new Error('الاشتراك غير فعّال (' + ui.status + ')');
  XACCOUNT = ui;
  store.set('acct:' + XT.base + XT.u, { at: Date.now(), ui });
}
async function xLoadLive(){
  const cats = await xcats('&action=get_live_categories') || [], st = await xlist('&action=get_live_streams', 'num,name,stream_id,stream_icon,category_id') || [];
  setLive(st.map((x, i) => ({ id: String(x.stream_id), n: x.name || '', num: +x.num || i + 1, cmd: xurl('live', x.stream_id, 'ts'), logo: x.stream_icon || '', g: String(x.category_id) }))
    .sort((a, b) => a.num - b.num), cats.map(c => ({ id: String(c.category_id), name: c.category_name })));
}
/** the channels and their sections, grouped in one pass (a list may hold tens of thousands) */
function setLive(ch, cats){
  ALL = ch;
  const by = new Map();
  for(const c of ALL){ let l = by.get(c.g); if(!l) by.set(c.g, l = []); l.push(c); }
  CATS = [{ id: '⌕', name: '⌕ بحث عن قناة', find: true, ch: [] }, { id: '★', name: '★ المفضلة', fav: true, ch: [] }]
    .concat(cats.map(c => ({ id: c.id, name: c.name, ch: by.get(c.id) || [] })).filter(c => c.ch.length));
  const at = new Map(CATS.map((c, i) => [c.id, i]));
  ALL.forEach((c, i) => { c.i = i; c.ci = at.has(c.g) ? at.get(c.g) : -1; });
  favLive();
}
/** the favourite channels' section, made again when one is added or taken off */
function favLive(){
  const f = CATS.find(c => c.fav); if(!f) return;
  f.ch = ALL.filter(c => isFav('live', c.id));
  LCATS_OF = null; LWIN = '';
}
/** items by their section, in the order given */
function groupBy(items){
  const by = new Map();
  for(const it of items){ let l = by.get(it.cat); if(!l) by.set(it.cat, l = []); l.push(it); }
  return by;
}
let XVOD = [], XSER = [];
function xItem(x, kind){
  const r = parseFloat(x.rating); 
  return { kind, id: String(kind === 'series' ? x.series_id : x.stream_id), name: x.name || '', img: x.stream_icon || x.cover || '',
    r: r > 0 && r <= 10 ? r : null, year: String(x.year || x.releaseDate || x.release_date || '').slice(0, 4),
    desc: x.plot || '', time: 0, added: +(kind === 'series' ? x.last_modified : x.added) || 0, genres: x.genre || '',
    cmd: kind === 'series' ? '' : xurl('movie', x.stream_id, x.container_extension || 'mp4'), cat: String(x.category_id) };
}
async function xLoadVod(){
  const [vc, sc, v, se] = await Promise.all([xcats('&action=get_vod_categories'), xcats('&action=get_series_categories'),
    xlist('&action=get_vod_streams', 'stream_id,name,stream_icon,rating,year,added,category_id,container_extension'),
    xlist('&action=get_series', 'series_id,name,cover,rating,year,releaseDate,last_modified,category_id')]);
  // sorted once, newest first (the dates are numbers); the sections keep that order
  const byNew = (a, b) => b.added - a.added;
  XVOD = (v || []).map(x => xItem(x, 'vod')).sort(byNew); XSER = (se || []).map(x => xItem(x, 'series')).sort(byNew);
  const fill = (kind, cats, items) => {
    const by = groupBy(items);
    VOD_CATS[kind] = (cats || []).map(c => ({ id: String(c.category_id), name: c.category_name })).filter(c => by.has(c.id));
    PAGES[kind + ':*'] = { items, page: 1, total: items.length, busy: false };
    for(const c of VOD_CATS[kind]){ const it = by.get(c.id); PAGES[kind + ':' + c.id] = { items: it, page: 1, total: it.length, busy: false }; }
  };
  fill('أفلام', vc, XVOD); fill('مسلسلات', sc, XSER);
}
function keptDetails(it){
  const d = store.get('det:' + it.kind + ':' + it.id, null);
  if(!d || Date.now() - d.at > 30 * 864e5) return null;
  Object.assign(it, d.fields);
  return d;
}
async function xDetails(it){   // the plot, the genre, the length: asked when a film or series opens, kept a week
  const k = keptDetails(it);
  if(k) return k.seasons;
  const seasons = await xDetailsNow(it);
  store.set('det:' + it.kind + ':' + it.id, { at: Date.now(), seasons, fields: { desc: it.desc, genres: it.genres, time: it.time, year: it.year } });
  return seasons;
}
async function xDetailsNow(it){
  if(it.kind === 'series'){
    const j = await xget('&action=get_series_info&series_id=' + enc(it.id));
    if(!j) return [];
    const info = j.info || {};
    if(info.plot) it.desc = info.plot; if(info.genre) it.genres = info.genre;
    if(!it.year && info.releaseDate) it.year = String(info.releaseDate).slice(0, 4);
    const eps = j.episodes || {};
    return Object.keys(eps).sort((a, b) => +a - +b).map(n => ({ id: n, name: 'الموسم ' + ar(n),
      list: eps[n].map(e => ({ num: +e.episode_num, id: e.id, ext: e.container_extension || 'mp4' })).sort((a, b) => a.num - b.num) }))
      .map(s => Object.assign(s, { eps: s.list.map(e => e.num) }));
  }
  const j = await xget('&action=get_vod_info&vod_id=' + enc(it.id));
  const info = j && j.info || {};
  if(info.plot) it.desc = info.plot; if(info.genre) it.genres = info.genre;
  if(info.duration_secs) it.time = Math.round(info.duration_secs / 60);
  if(!it.year && info.releasedate) it.year = String(info.releasedate).slice(0, 4);
  if(info.backdrop_path && info.backdrop_path[0]) it.backdrop = info.backdrop_path[0];
  return null;
}

// ---- a plain M3U list (iptv-org's, or a get.php that answers only as a list): channels by their group;
// films (…/movie/…) and episodes (…/series/…) in it go to «أفلام» and «مسلسلات» by their group too
let M3U = false;
function parseM3u(text){
  const lines = String(text).split(/\r?\n/), ch = [], vod = [], ser = [];
  let meta = null;
  for(const l of lines){
    const t = l.trim();
    if(t.startsWith('#EXTINF')){
      const attr = k => { const m = t.match(new RegExp(k + '="([^"]*)"')); return m ? m[1] : ''; };
      meta = { name: t.slice(t.lastIndexOf(',') + 1).trim(), logo: attr('tvg-logo'), group: attr('group-title') || 'عام' };
    } else if(t && !t.startsWith('#') && meta){
      const it = Object.assign({ url: t }, meta);
      (/\/movie\//.test(t) ? vod : /\/series\//.test(t) ? ser : ch).push(it);
      meta = null;
    }
  }
  const groups = list => Array.from(new Set(list.map(x => x.group)));
  setLive(ch.map((x, i) => ({ id: 'm' + i, n: x.name, num: i + 1, cmd: x.url, logo: x.logo, g: x.group })), groups(ch).map(g => ({ id: g, name: g })));
  const fill = (kind, list, k) => {
    const items = list.map((x, i) => ({ kind: 'vod', id: k + i, name: x.name, img: x.logo, r: null, year: '', desc: '', time: 0, added: '', genres: '', cmd: x.url, cat: x.group }));
    const by = groupBy(items);
    VOD_CATS[kind] = groups(list).map(g => ({ id: g, name: g }));
    PAGES[kind + ':*'] = { items, page: 1, total: items.length };
    for(const c of VOD_CATS[kind]){ const it = by.get(c.id) || []; PAGES[kind + ':' + c.id] = { items: it, page: 1, total: it.length }; }
  };
  fill('أفلام', vod, 'mv'); fill('مسلسلات', ser, 'ms');
  if(!ALL.length && !vod.length && !ser.length) throw new Error('القائمة فارغة أو الرابط لا يعطي قائمة قنوات');
}
async function loadM3u(url){
  let text = !FRESH && Live && Live.cacheGet ? Live.cacheGet(url) : '';
  if(text) OLDEST = Math.max(OLDEST, +Live.cacheAge(url) || 0);
  try{ if(!text) text = await rawGet(url); }
  catch(e){ throw new Error('تعذّر تنزيل القائمة: ' + (e.message || '')); }
  if(!/#EXTM3U|#EXTINF/.test(text)) throw new Error('الرابط لا يعطي قائمة M3U');
  M3U = true; XT = null;
  parseM3u(text);
}

// ---- data from the portal
let CATS = [], ALL = [];                     // live sections and channels (by number)
const VOD_CATS = { 'أفلام': [], 'مسلسلات': [] };
const VTYPE = { 'أفلام': 'vod', 'مسلسلات': 'series' };
const PAGES = {};                            // 'vod:catId' -> { items, page, total, busy }
let RECENT = [], HI_SERIES = [], HI_MOVIES = [];
/** the first n of a list that pass (the list is not walked to its end) */
function firstN(list, ok, n){ const out = []; for(const x of list){ if(ok(x)){ out.push(x); if(out.length >= n) break; } } return out; }
const listOf = j => !j ? [] : Array.isArray(j) ? j : Array.isArray(j.data) ? j.data : [];
const rating = x => { const r = parseFloat(x.rating_imdb || x.rating_kinopoisk || x.rating || ''); return r > 0 && r <= 10 ? r : null; };
function vItem(x, kind){
  return { kind, id: String(x.id), name: x.name || x.o_name || '', img: abs(x.screenshot_uri || x.cover_big || x.logo || ''),
    r: rating(x), year: String(x.year || '').slice(0, 4), desc: x.description || '', time: +x.time || 0,
    added: x.added || '', cmd: x.cmd || '', genres: x.genres_str || '' };
}
async function loadLive(){
  const g = listOf(await call('type=itv&action=get_genres')).filter(x => x.id !== '*');
  let ch = listOf(await call('type=itv&action=get_all_channels').catch(() => null));
  if(!ch.length){   // some portals answer per section only
    for(const gg of g){
      for(let p = 1; p < 40; p++){
        const j = await call(`type=itv&action=get_ordered_list&genre=${enc(gg.id)}&p=${p}&fav=0&sortby=number`).catch(() => null);
        const d = listOf(j); ch = ch.concat(d);
        if(!j || !d.length || p * (+j.max_page_items || 14) >= (+j.total_items || 0)) break;
      }
    }
  }
  const list = ch.map(x => ({ id: String(x.id), n: x.name || '', num: +x.number || 0, cmd: x.cmd, logo: abs(x.logo), g: String(x.tv_genre_id) }))
    .sort((a, b) => a.num - b.num);
  list.forEach((c, i) => { if(!c.num) c.num = i + 1; });
  setLive(list, g.map(x => ({ id: String(x.id), name: x.title })));
}
async function loadVodCats(){
  for(const kind of ['أفلام', 'مسلسلات']){
    const j = await call(`type=${VTYPE[kind]}&action=get_categories`).catch(() => null);
    VOD_CATS[kind] = listOf(j).filter(x => x.id !== '*').map(x => ({ id: String(x.id), name: x.title }));
  }
}
async function page(kind, catId){
  if(XT || M3U) return PAGES[kind + ':' + catId] || { items: [], page: 1, total: 0 };
  const key = kind + ':' + catId, P = PAGES[key] || (PAGES[key] = { items: [], page: 0, total: Infinity, busy: false });
  if(P.busy || P.items.length >= P.total) return P;
  P.busy = true;
  try{
    const j = await call(`type=${VTYPE[kind]}&action=get_ordered_list&category=${enc(catId)}&genre=*&p=${P.page + 1}&sortby=added`);
    const d = listOf(j);
    P.page++; P.items = P.items.concat(d.map(x => vItem(x, VTYPE[kind])));
    P.total = d.length ? (+(j && j.total_items) || P.items.length) : P.items.length;
  }catch(e){ P.total = P.items.length; }
  P.busy = false;
  return P;
}
async function loadHome(){
  if(M3U){ RECENT = []; HI_SERIES = []; HI_MOVIES = []; return; }   // a plain list has no dates or ratings
  if(XT){
    // both lists are already newest first: only their heads are looked at
    RECENT = XVOD.slice(0, 15).concat(XSER.slice(0, 15)).sort((a, b) => b.added - a.added).slice(0, 10);
    HI_SERIES = firstN(XSER, x => x.r >= 8, 20);
    HI_MOVIES = firstN(XVOD, x => x.r >= 8, 20);
    return;
  }
  const mv = await page('أفلام', '*'), se = await page('مسلسلات', '*');
  await page('أفلام', '*'); await page('مسلسلات', '*');   // two pages each: enough highly rated ones
  const byAdded = (a, b) => a.added < b.added ? 1 : a.added > b.added ? -1 : 0;
  RECENT = mv.items.slice(0, 15).concat(se.items.slice(0, 15)).sort(byAdded).slice(0, 10);
  HI_SERIES = se.items.filter(x => x.r >= 8).slice(0, 20);
  HI_MOVIES = mv.items.filter(x => x.r >= 8).slice(0, 20);
}
async function loadAccount(){
  if(M3U){ $('acct').textContent = 'قائمة قنوات'; return; }
  if(XT){
    const e = XACCOUNT && +XACCOUNT.exp_date;
    $('acct').textContent = e ? 'اشتراكك · فعّال حتى ' + ar(new Date(e * 1000).toISOString().slice(0, 10)) : 'اشتراكك · فعّال';
    return;
  }
  const j = await call('type=account_info&action=get_main_info').catch(() => null);
  const end = j && (j.end_date || j.expire_billing_date || j.phone);
  $('acct').textContent = end ? 'اشتراكك · فعّال حتى ' + ar(String(end).slice(0, 10)) : 'اشتراكك · فعّال';
}

// ---- kept on this TV: where films stopped, favourites, the last channel
let RESM = null, FAVL = null, FAVK = null;
const RES = () => RESM || (RESM = store.get('resume', {}));
function keepResume(key, v){ const r = RES(); if(v) r[key] = v; else delete r[key]; store.set('resume', r); HOME = null; }
const FAVS = () => FAVL || (FAVL = store.get('favs', []));
const favKeys = () => FAVK || (FAVK = new Set(FAVS().map(f => f.kind + ':' + f.id)));
const isFav = (kind, id) => favKeys().has(kind + ':' + id);
function toggleFav(it){
  if(!it || !it.kind) return;
  const f = FAVS(), at = f.findIndex(x => x.kind === it.kind && x.id === it.id);
  if(at >= 0) f.splice(at, 1); else f.unshift({ kind: it.kind, id: it.id, name: it.name, img: it.img || it.logo || '', cmd: it.cmd || '', r: it.r || null, year: it.year || '' });
  store.set('favs', f); FAVK = null; HOME = null;
  if(it.kind === 'live') favLive();
  toast(at >= 0 ? 'أُزيل من المفضلة' : 'أُضيف إلى المفضلة');
}
const favMark = it => isFav(it.kind, it.id) ? '<b class="fv">★</b> ' : '';
/** the favourite films (or series) of a kind: the first section of its screen */
function favItems(kind){
  return FAVS().filter(f => f.kind === VTYPE[kind] ? !M3U || String(f.id).startsWith(kind === 'أفلام' ? 'mv' : 'ms')
    : M3U && f.kind === 'vod' && kind === 'مسلسلات' && String(f.id).startsWith('ms'));
}
const mmss = ms => { const s = Math.max(0, Math.round(ms / 1000)), h = Math.floor(s / 3600), m = Math.floor(s % 3600 / 60), x = s % 60;
  return (h ? h + ':' + String(m).padStart(2, '0') : m) + ':' + String(x).padStart(2, '0'); };

let toastT = 0;
function toast(t){ const el = $('toast'); el.textContent = t; el.classList.add('on'); clearTimeout(toastT); toastT = setTimeout(() => el.classList.remove('on'), 2600); }

// ======================================================================== the screens (as in the preview)
const TABS = ['الرئيسية', 'مباشر', 'أفلام', 'مسلسلات', 'المفضلة'];
let screen = 'home', tab = 0, row = 0, col = 0, cur = 0, listOpen = false, lcat = 0, lsel = 0, infoT = 0, mbtn = 0;
let catsOpen = false, csel = 0;
let bKind = 'أفلام', bcat = 0, bfocus = 'rail', bi = 0, bScroll = 0;
let MOV = null;   // the film or series open: { item, seasons, sIdx, eIdx, focus }

function cardArt(it, i){ return it.img ? `background:url('${esc(it.img)}') center/cover,${art(i, 200)}` : `background:${art(i, 200)}`; }
// ---- the posters of a section: only the cards on the screen are fetched, four at a time; until a picture
// arrives its card shows the skeleton (the cards come in one after another, a light runs over them)
const SEEN = new Set();   // fetched once: the WebView keeps them, so they show at once next time
let IMQ = [], IMGEN = 0, IMBUSY = 0;
function artTag(it, i){
  if(!it.img || SEEN.has(it.img)) return `<div class="art" style="${cardArt(it, i)}"></div>`;
  return `<div class="art" data-img="${esc(it.img)}" style="background:${art(i, 200)}"></div>`;
}
const skelCls = it => it.img && !SEEN.has(it.img) ? ' skel' : '';
const FLY = new Set();   // pictures on their way: a redrawn card waits for the same one
function wantArt(el){
  if(el.dataset.st) return;
  el.dataset.st = 'q';
  if(FLY.has(el.dataset.img)) return;
  IMQ.push({ el, u: el.dataset.img, gen: IMGEN }); pumpArt();
}
function pumpArt(){
  const store = Live && Live.hasImg;   // the app keeps every picture, made small: then more at once
  while(IMBUSY < (store ? 8 : 4) && IMQ.length){
    const j = IMQ.shift();
    if(j.gen !== IMGEN || !j.el.isConnected){ delete j.el.dataset.st; continue; }
    if(FLY.has(j.u) || SEEN.has(j.u)) continue;
    IMBUSY++; FLY.add(j.u);
    // a picture already on the TV asks no server: it does not wait in the requests' line
    (store && Live.hasImg(j.u) ? Promise.resolve() : gate()).then(() => {
      const im = new Image();
      const fin = ok => {
        IMBUSY--; SEEN.add(j.u); FLY.delete(j.u);
        const els = new Set([j.el]);
        document.querySelectorAll('.art[data-img]').forEach(a => { if(a.dataset.img === j.u) els.add(a); });
        els.forEach(a => {
          if(ok) a.style.background = `url('${j.u.replace(/'/g, '%27')}') center/cover`;
          a.removeAttribute('data-img');
          const c = a.parentNode; if(c) c.classList.remove('skel');
        });
        pumpArt();
      };
      im.onload = () => fin(true); im.onerror = () => fin(false); im.src = j.u;
    });
  }
}
/** the cards of a grid inside its window (after its scroll), top first */
function artVisible(g, scroll){
  const h = g.clientHeight;
  for(const j of IMQ) delete j.el.dataset.st;   // what waited is asked again only if still in sight
  IMQ = [];
  g.querySelectorAll('.art[data-img]').forEach(a => {
    const c = a.parentNode, top = c.offsetTop - scroll;
    if(top + c.offsetHeight > 0 && top < h) wantArt(a);
  });
}
function artVisibleHome(){
  const box = $('rows').getBoundingClientRect();
  $('rows').querySelectorAll('.art[data-img]:not([data-st])').forEach(a => {
    const c = a.parentNode.getBoundingClientRect();
    if(c.bottom > box.top && c.top < box.bottom && c.right > box.left && c.left < box.right) wantArt(a);
  });
}
let HOMEV = 0, HOME_CAS = true;
let HOME = null, HOME_DRAWN = null;   // the rows as made, and the ones on the screen now
const badge = it => it.r ? `<div class="badge">★ ${ar(it.r.toFixed(1)).replace('.', '٫')}</div>` : '';
function continueItems(){
  return Object.entries(RES()).map(([k, v]) => Object.assign({ key: k }, v))
    .filter(v => v.next || (v.pos > 60000 && (!v.dur || v.pos < v.dur - 90000)))   // a finished episode: the next one, from its start
    .sort((a, b) => b.at - a.at).slice(0, 12);
}
function rowsData(){
  const R = [];
  const cont = continueItems();
  if(cont.length) R.push({ h: 'تابع المشاهدة', kind: 'wide', items: cont.map((v, i) => ({
    sk: skelCls(v), html: `${artTag(v, i + 3)}<div class="cap">${favMark(v)}${esc(v.name)}${v.ep ? ` — الحلقة ${ar(v.ep)}` : ''}<small>${v.next ? 'الحلقة التالية · من البداية' : 'من ' + ar(mmss(v.pos))}</small>`
      + `<div class="bar"><i style="width:${v.next ? 0 : v.dur ? Math.round(v.pos / v.dur * 100) : 30}%"></i></div></div>`,
    it: v, go: () => resumeItem(v) })) });
  const poster = (list, seed) => list.map((it, i) => ({
    sk: skelCls(it), html: `${artTag(it, i + seed)}${badge(it)}`
      + (seed === 7 ? `<div class="kind">${it.kind === 'series' ? 'مسلسل' : 'فيلم'}</div>` : '')
      + `<div class="cap">${favMark(it)}${esc(it.name)}<small>${seed === 7 ? addedText(it.added) : ar(it.year)}</small></div>`,
    it, go: () => openMovie(it) }));
  if(RECENT.length) R.push({ h: 'أُضيف حديثاً', kind: 'poster', items: poster(RECENT, 7) });
  if(HI_SERIES.length) R.push({ h: 'مسلسلات جديدة بتقييمات عالية', kind: 'poster', items: poster(HI_SERIES, 1) });
  if(HI_MOVIES.length) R.push({ h: 'أفلام جديدة بتقييمات عالية', kind: 'poster', items: poster(HI_MOVIES, 5) });
  return R;
}
const homeRows = () => HOME || (HOME = rowsData());
function addedText(a){
  if(!a) return '';
  const d = typeof a === 'number' ? new Date(a * 1000) : new Date(String(a).replace(' ', 'T')); if(isNaN(d)) return '';
  const days = Math.floor((Date.now() - d) / 864e5);
  return days <= 0 ? 'اليوم' : days === 1 ? 'أمس' : days === 2 ? 'منذ يومين' : days <= 10 ? 'منذ ' + ar(days) + ' أيام' : 'منذ ' + ar(days) + ' يوماً';
}
let VSHIFT = 0;
function setV(v){ v = Math.max(0, v); if(v === VSHIFT) return; VSHIFT = v; const r = document.querySelector('#rows .rin'); if(r) r.style.transform = `translateY(${-VSHIFT}px)`; }
const SHIFT = {};   // each row's slide, by its title
let TABSIG = '';
function drawTabsOnly(){
  const sig = tab + ':' + Math.min(row, 0);
  if(sig === TABSIG) return;
  TABSIG = sig;
  $('prof').classList.toggle('focus', row === -2);
  // the filled tab is the screen shown («الرئيسية»); another tab is marked only while the cursor is on it
  $('tabs').innerHTML = TABS.map((t, i) => `<div class="tab glass${i === 0 ? ' sel' : ''}${row === -1 && i === tab ? ' focus' : ''}">${t}</div>`).join('');
}
/** the rows are built once; a key only moves the gold edge and slides the rows (places from the layout, not
 *  from the screen: a held key no longer measures cards in the middle of their slide) */
function drawHome(){
  const R = homeRows(), rowsEl = $('rows');
  if(!R.length){
    if(HOME_DRAWN !== R){ HOME_DRAWN = R; VSHIFT = 0; rowsEl.innerHTML = '<div class="empty">لا توجد عناوين بعد.</div>'; }
    if(row >= 0) row = -1;
    return drawTabsOnly();
  }
  if(row >= R.length) row = R.length - 1;
  if(row >= 0) col = Math.max(0, Math.min(col, R[row].items.length - 1));
  drawTabsOnly();
  if(HOME_DRAWN !== R){
    HOME_DRAWN = R;
    rowsEl.innerHTML = `<div class="rin" style="transform:translateY(${-VSHIFT}px)">` + R.map((r, ri) => `<div class="row"><h3>${r.h}</h3><div class="cards" style="transform:translateX(${SHIFT[r.h] || 0}px)">${
      r.items.map((it, ci) => `<div class="card ${r.kind}${it.sk || ''}${HOME_CAS && it.sk ? ' cas' : ''}" style="--d:${Math.min(ri * 3 + ci, 14) * 45}ms">${it.html}</div>`).join('')}</div></div>`).join('') + '</div>';
    HOME_CAS = false;   // the cascade once; later keys only move
  }
  const rin = rowsEl.firstChild, old = rin.querySelector('.card.focus');
  const rowEl = row >= 0 ? rin.children[row] : null, cards = rowEl && rowEl.lastChild, card = cards && cards.children[col];
  if(old && old !== card) old.classList.remove('focus');
  if(card){
    card.classList.add('focus');
    const H = rowsEl.clientHeight, top = rowEl.offsetTop, bot = top + rowEl.offsetHeight;
    if(row === 0) setV(0);
    else if(bot - VSHIFT > H * .97) setV(bot - H * .94);
    else if(top < VSHIFT) setV(top);
    const W = rowsEl.clientWidth, pad = W * .04, k = R[row].h, left = card.offsetLeft;
    let shift = SHIFT[k] || 0;
    if(left + shift < pad) shift = pad - left;
    else if(left + card.offsetWidth + shift > W - pad) shift = W - pad - left - card.offsetWidth;
    if(shift < 0) shift = 0;
    if(shift !== (SHIFT[k] || 0)){ SHIFT[k] = shift; cards.style.transform = `translateX(${shift}px)`; }
  } else setV(0);
  // the pictures of the cards in sight, once the rows have slid (a held key asks for none on its way)
  clearTimeout(HOMEV); HOMEV = setTimeout(() => { if(screen === 'home') artVisibleHome(); }, card && old ? 380 : 30);
}

// ---- live
function lastChannel(){ const id = store.get('lastChannel', null); const i = ALL.findIndex(c => c.id === id); return i >= 0 ? i : 0; }
function keepChannel(){ if(ALL[cur]) store.set('lastChannel', ALL[cur].id); }
let zapT = 0, playSeq = 0;
async function playTile(t, chIdx){
  const ch = ALL[chIdx]; if(!ch) return;
  const my = ++playSeq;
  try{ const u = XT || M3U ? ch.cmd : await link('itv', ch.cmd); if(my !== playSeq) return; Live && Live.play(t, u); }
  catch(e){ toast('تعذّر تشغيل «' + ch.n + '»'); }
}
function zap(){
  drawLive(true);
  clearTimeout(zapT);
  zapT = setTimeout(() => { playTile(0, cur); keepChannel(); }, 600);   // the stream opens when the key rests
}
function openLive(i){
  if(!ALL.length){ toast('لا توجد قنوات في اشتراكك'); return; }
  screen = 'live'; cur = i; listOpen = false; catsOpen = false;
  show(); zap();
}
function drawLive(flash){
  const c = ALL[cur]; if(!c) return;
  if(INFO_OF !== c){ INFO_OF = c; $('inum').textContent = ar(c.num); $('inm').textContent = c.n; }
  const info = $('info');
  if(flash){ info.classList.remove('hide'); clearTimeout(infoT); infoT = setTimeout(() => { info.classList.add('hide'); frostSoon(); }, 3500); }
  if(listOpen){ clearTimeout(infoT); info.classList.add('hide'); }
  $('list').classList.toggle('hide', !listOpen);
  if(listOpen) drawList();
  // the glass is placed again only when a panel came, went or changed its width: not at every key in the list
  const fk = listOpen + ':' + catsOpen + ':' + info.classList.contains('hide');
  if(fk !== FKEY){ FKEY = fk; frostSoon(); }
}
let INFO_OF = null, FKEY = '';
/** one class on one child of a box */
function mark(box, cls, el){
  const o = box.querySelector(':scope > .' + cls);
  if(o && o !== el) o.classList.remove(cls);
  if(el) el.classList.add(cls);
}
/** a row brought into its box's sight (the box alone scrolls, never the page) */
function reveal(box, el){
  if(!el) return;
  const top = el.offsetTop, bot = top + el.offsetHeight, pad = el.offsetHeight * .5;
  if(top - pad < box.scrollTop) box.scrollTop = Math.max(0, top - pad);
  else if(bot + pad > box.scrollTop + box.clientHeight) box.scrollTop = bot + pad - box.clientHeight;
}
/** how many rows of a box fit in it (measured from its first row) */
function fitRows(box, now){
  const f = box.firstElementChild; if(!f || !f.offsetHeight || !box.clientHeight) return now;
  const cs = getComputedStyle(box), gap = parseFloat(cs.rowGap) || 0;
  const inner = box.clientHeight - (parseFloat(cs.paddingTop) || 0) - (parseFloat(cs.paddingBottom) || 0);
  return Math.max(3, Math.floor((inner + gap) / (f.offsetHeight + gap)));
}
let LROWS = 8, LFROM = 0, LWIN = '', LNEAR = '', LWFROM = 0, LCATS_OF = null, LCT = 0;
/** ▲▼ in the sections: the edge moves now; the section's channels show beside it when the key rests */
function moveCats(){
  const cs = $('lcats');
  mark(cs, 'sel', cs.children[csel]); reveal(cs, cs.children[csel]);
  clearTimeout(LCT);
  LCT = setTimeout(() => { if(!listOpen || !catsOpen || lcat === csel || CATS[csel].find) return; lcat = csel; lsel = 0; LFROM = 0; drawList(); }, 130);
}
/** the channels' list: the sections are built once, the channels only when their window moves; a key between
 *  them moves two classes */
function drawList(){
  const cat = CATS[lcat] || { name: '', ch: [] }, chans = cat.ch;
  $('lcat').textContent = cat.name;
  $('list').classList.toggle('wide', catsOpen);
  const cs = $('lcats');
  if(LCATS_OF !== CATS){
    LCATS_OF = CATS;
    cs.innerHTML = CATS.map(c2 => `<div class="c"><span>${esc(c2.name)}</span><small>${c2.find ? '' : ar(c2.ch.length)}</small></div>`).join('');
  }
  mark(cs, 'cur', cs.children[lcat]);
  mark(cs, 'sel', catsOpen ? cs.children[csel] : null);
  reveal(cs, cs.children[catsOpen ? csel : lcat]);   // the section under the key, wherever it is in the list
  // a window of the list around the chosen one (sections may hold hundreds): as many as fit
  const box = $('items');
  if(lsel < LFROM) LFROM = lsel; else if(lsel > LFROM + LROWS - 1) LFROM = lsel - LROWS + 1;
  const from = LFROM = Math.max(0, Math.min(LFROM, chans.length - LROWS));
  const win = lcat + ':' + from + ':' + LROWS + ':' + chans.length;
  const rowOf = x => `<div class="it"><span class="n">${ar(x.num)}</span>`
    + (x.logo ? `<img class="chanlogo" src="${esc(x.logo)}" onerror="this.remove()">` : '')
    + `<span class="nm">${esc(x.n)}${isFav('live', x.id) ? ' <small style="display:inline;color:var(--gold-lt)">★</small>' : ''}</span></div>`;
  const near = lcat + ':' + LROWS + ':' + chans.length;
  if(win !== LWIN && near === LNEAR && LWIN && Math.abs(from - LWFROM) === 1 && box.children.length === LROWS){
    // the list moved by one row: one row leaves, one comes (the others, and their logos, stay as they are)
    if(from > LWFROM){ box.firstElementChild.remove(); box.insertAdjacentHTML('beforeend', rowOf(chans[from + LROWS - 1])); }
    else { box.lastElementChild.remove(); box.insertAdjacentHTML('afterbegin', rowOf(chans[from])); }
    LWIN = win; LWFROM = from;
  }
  else if(win !== LWIN){
    LWIN = win; LNEAR = near; LWFROM = from;
    box.innerHTML = chans.slice(from, from + LROWS).map(rowOf).join('');
    const n = fitRows(box, LROWS);
    if(n !== LROWS){ LROWS = n; return drawList(); }
  }
  const on = ALL[cur], ci = on ? chans.indexOf(on) : -1;
  mark(box, 'cur', ci >= from ? box.children[ci - from] : null);
  mark(box, 'sel', catsOpen ? null : box.children[lsel - from]);
}
// ---- the glass over the video: the picture under a panel, blurred by the app (the page cannot see the video)
let frostT = 0, FROST = '';
function syncFrost(){
  if(!Live || !Live.frost) return;
  const W = innerWidth, H = innerHeight, out = [];
  const add = el => { if(el.classList.contains('hide') || !el.offsetWidth) return;
    out.push([el.offsetLeft / W, el.offsetTop / H, el.offsetWidth / W, el.offsetHeight / H, (parseFloat(getComputedStyle(el).borderTopLeftRadius) || 0) / W].map(n => n.toFixed(4)).join()); };
  if(screen === 'live'){ add($('list')); add($('info')); }
  else if(screen === 'player') add($('pbar'));
  const s = out.join(';');
  if(s !== FROST){ FROST = s; Live.frost(s); }
}
function frostSoon(){ syncFrost(); clearTimeout(frostT); frostT = setTimeout(syncFrost, 420); }   // again when the panel has its width

// ---- movies / series
const COLS = 5, WROWS = 10;
const FAVCAT = { id: '★', name: '★ المفضلة', fav: true };   // the first section: this kind's favourites
const bCats = () => [FAVCAT].concat(VOD_CATS[bKind]);
const pageOf = c => !c ? null : c.fav ? { items: favItems(bKind), total: favItems(bKind).length, busy: false } : PAGES[bKind + ':' + c.id];
function openBrowse(kind){
  IMGEN++; screen = 'browse'; bKind = kind; bfocus = 'rail'; bi = 0; bScroll = 0; GW = ''; RFROM = 0;
  bcat = favItems(kind).length || !VOD_CATS[kind].length ? 0 : 1;   // the favourites first when there are some
  show(); loadCat();
}
function curItems(){ const P = pageOf(bCats()[bcat]); return P ? P.items : []; }
async function loadCat(){
  const c = bCats()[bcat]; if(!c) return drawBrowse();
  if(!c.fav && !PAGES[bKind + ':' + c.id]){ drawBrowse(); await page(bKind, c.id); }
  if(screen === 'browse') drawBrowse();
}
let RROWS = 10, RFROM = 0;
function drawRail(){
  $('bkind').textContent = bKind;
  $('sbtn').classList.toggle('focus', bfocus === 'search');
  const cats = bCats(), box = $('rail');
  // as many rows as the rail holds; the cursor walks to its edge and only then the sections move under it
  if(bcat < RFROM) RFROM = bcat; else if(bcat > RFROM + RROWS - 1) RFROM = bcat - RROWS + 1;
  const from = RFROM = Math.max(0, Math.min(RFROM, cats.length - RROWS));
  box.innerHTML = cats.slice(from, from + RROWS).map((c, k) => { const i = from + k;
    const P = pageOf(c);
    return `<div class="c${i === bcat ? ' cur' : ''}${i === bcat && bfocus === 'rail' ? ' sel' : ''}"><span>${esc(c.name)}</span><small>${P && isFinite(P.total) ? ar(P.total) : ''}</small></div>`; }).join('');
  const n = fitRows(box, RROWS);
  if(n !== RROWS){ RROWS = n; drawRail(); }
}
let GW = '', GW0 = 0, GROW = 0, GGAP = 0, CATT = 0, ARTT = 0;
/** ▲▼ in the sections: the rail moves now, the section's posters only when the key rests (a held key runs on) */
function moveCat(d){
  const cats = bCats();
  const n = Math.max(0, Math.min(cats.length - 1, bcat + d));
  if(n === bcat) return;
  bcat = n; bScroll = 0; bi = 0; GW = ''; IMGEN++; IMQ = [];
  drawRail();
  const c = cats[n], P = pageOf(c);
  if(!P || !P.items.length || XT || M3U) drawSkeleton(c ? c.name : '');   // the skeleton at once, the section when the key rests
  clearTimeout(CATT); CATT = setTimeout(loadCat, 280);
}
function drawSkeleton(name){
  GW = '';
  $('grid').innerHTML = `<div class="in"><h3>${esc(name)}</h3>${Array.from({ length: 10 }, (_, k) =>
    `<div class="card poster skel cas" style="--d:${k * 55}ms"><div class="cap sk"><i></i><i></i></div></div>`).join('')}</div>`;
}
/** ten rows of a section from row w0; the rows above are one empty block of their height, so every card keeps
 *  its place whichever window is drawn (a section may hold thousands) */
function paintGrid(items, name, w0, cas){
  const g = $('grid'), start = w0 * COLS, end = Math.min(items.length, start + WROWS * COLS);
  const empty = bCats()[bcat] && bCats()[bcat].fav ? 'لا مفضلة بعد: اضغط مطولاً على OK فوق أي عنوان لإضافته هنا' : 'لا يوجد شيء في هذا القسم';
  let h = '';
  for(let i = start; i < end; i++){ const it = items[i], sk = skelCls(it);
    h += `<div class="card poster${sk}${sk && cas ? ' cas' : ''}${bfocus === 'grid' && i === bi ? ' focus' : ''}" style="--d:${Math.min(i - start, 12) * 45}ms">${artTag(it, i)}${badge(it)}`
      + `<div class="cap">${favMark(it)}${esc(it.name)}<small>${ar(it.year || '')}</small></div></div>`; }
  g.innerHTML = `<div class="in" style="transform:translateY(${-bScroll}px)"><h3>${esc(name)}</h3>${
    w0 ? `<div style="grid-column:1/-1;height:${Math.max(0, w0 * GROW - GGAP)}px"></div>` : ''}${
    h || `<div class="empty" style="grid-column:1/-1;color:var(--dim)">${empty}</div>`}</div>`;
  GW0 = w0;
  if(!GROW){ const c = g.querySelector('.card');
    if(c && c.offsetHeight){ GGAP = parseFloat(getComputedStyle(c.parentNode).rowGap) || 0; GROW = c.offsetHeight + GGAP; } }
}
function drawBrowse(){
  const cats = bCats(), c0 = cats[bcat], items = curItems(), g = $('grid');
  const name = c0 ? c0.name : '', r = Math.floor(bi / COLS), sig = bKind + ':' + bcat + ':' + items.length;
  drawRail();
  // inside the rows already drawn only the gold edge moves
  if(bfocus === 'grid' && GW === sig && (GW0 === 0 || r - 2 >= GW0) && (r + 2 < GW0 + WROWS || (GW0 + WROWS) * COLS >= items.length)){
    const old = g.querySelector('.card.focus'), card = g.querySelectorAll('.card')[bi - GW0 * COLS];
    if(old && old !== card) old.classList.remove('focus');
    if(card) card.classList.add('focus');
    return scrollGrid();
  }
  const P0 = pageOf(c0);
  if(!items.length && c0 && !c0.fav && !XT && !M3U && (!P0 || P0.busy || P0.total === Infinity)) return drawSkeleton(name);
  const fresh = GW !== sig;
  let w0 = bfocus === 'grid' ? Math.max(0, r - 4) : 0;
  if(bfocus !== 'grid') bScroll = 0;
  if(w0 && !GROW){ paintGrid(items, name, 0, false); if(!GROW) w0 = 0; }   // a row's height is measured first
  paintGrid(items, name, w0, fresh);
  GW = sig;
  if(bfocus !== 'grid') return artVisible(g, 0);
  scrollGrid();
}
function scrollGrid(){
  const g = $('grid'), items = curItems();
  const card = g.querySelector('.card.focus'); if(!card) return;
  const H = g.clientHeight, top = card.offsetTop, bot = top + card.offsetHeight;
  let s2 = bScroll;
  if(bi < COLS) s2 = 0;
  else if(bot - s2 > H * .96) s2 = bot - H * .94;
  else if(top - s2 < H * .02) s2 = top - H * .04;
  s2 = Math.max(0, s2);
  if(s2 !== bScroll){ bScroll = s2; g.firstChild.style.transform = `translateY(${-bScroll}px)`; }
  // the pictures when the key rests: a held key runs past rows without asking for theirs
  clearTimeout(ARTT); ARTT = setTimeout(() => { if(screen === 'browse') artVisible(g, bScroll); }, 110);
  // near the end: the next page
  const c = bCats()[bcat], P = c && !c.fav && PAGES[bKind + ':' + c.id];
  if(P && !P.busy && P.items.length < P.total && bi >= items.length - COLS * 4)
    page(bKind, c.id).then(() => { if(screen === 'browse') drawBrowse(); });
}

// ---- a film or a series
async function openMovie(it){
  // Back returns to the screen it was opened from (the section, the search, the favourites), at the same card
  const back = screen === 'movie' || screen === 'player' ? (MOV && MOV.item && MOV.item.id === it.id && MOV.back) || 'home' : screen;
  const M = MOV = { item: it, seasons: null, sIdx: 0, eIdx: 0, focus: 'btns', back };
  screen = 'movie'; mbtn = 0; show();
  if(M3U) return;
  if(XT){
    const seasons = await xDetails(it).catch(() => []);
    if(it.kind === 'series') M.seasons = seasons || [];
  } else if(it.kind === 'series'){
    const j = await call(`type=series&action=get_ordered_list&movie_id=${enc(it.id)}&season_id=0&episode_id=0&p=1`).catch(() => null);
    M.seasons = listOf(j).map(s => ({ id: s.id, name: s.name, cmd: s.cmd, eps: (s.series || []).map(Number) })).filter(s => s.eps.length);
  } else return;
  lastSeen(M);
  if(screen === 'movie' && MOV === M) drawMovie();
}
/** a series opens on the season of the last episode watched, that episode chosen */
function lastSeen(M){
  const r = resumeOf(M.item); if(!r || !M.seasons) return;
  const si = M.seasons.findIndex(x => String(x.id) === String(r.season)); if(si < 0) return;
  M.sIdx = si; M.eIdx = Math.max(0, M.seasons[si].eps.indexOf(r.ep));
}
function resumeOf(it){ return RES()[it.kind + ':' + it.id]; }
function drawMovie(){
  const it = MOV.item, rs = resumeOf(it);
  $('mposter').style.cssText = cardArt(it, hash(it.id));
  $('mtitle').textContent = it.name;
  $('msub').innerHTML = (it.r ? `<span class="rate">★ ${ar(it.r.toFixed(1)).replace('.', '٫')}<small>/١٠</small></span> ` : '')
    + [it.year && ar(it.year), it.time ? ar(it.time) + ' دقيقة' : '', it.kind === 'series' ? 'مسلسل' : '', esc(it.genres)].filter(Boolean).join(' · ');
  $('mtext').textContent = it.desc || '';
  const B = it.kind === 'series'
    ? [rs ? `▶ تابع<small>الحلقة ${ar(rs.ep)} · من ${ar(mmss(rs.pos))}</small>` : '▶ تشغيل الحلقة الأولى', isFav(it.kind, it.id) ? '★ بالمفضلة' : '☆ للمفضلة']
    : [rs ? `▶ تابع<small>من ${ar(mmss(rs.pos))}</small>` : '▶ تشغيل', '↺ من البداية', isFav(it.kind, it.id) ? '★ بالمفضلة' : '☆ للمفضلة'];
  if(!rs && it.kind !== 'series') B.splice(1, 1);
  MOV.btns = B.length;
  $('mbtns').innerHTML = B.map((b, i) => `<div class="btn${i === 0 ? ' main' : ''}${MOV.focus === 'btns' && i === mbtn ? ' focus' : ''}">${b}</div>`).join('');
  let eps = '';
  if(it.kind === 'series'){
    if(!MOV.seasons) eps = '<div class="lineh">جارٍ تحميل الحلقات…</div>';
    else if(!MOV.seasons.length) eps = '<div class="lineh">لا توجد حلقات</div>';
    else {
      const S = MOV.seasons[MOV.sIdx], r0 = resumeOf(it), seen = EPS(it.id);
      eps = '<div class="lineh">المواسم</div><div class="pills">' + MOV.seasons.map((s, i) =>
        `<div class="pill${i === MOV.sIdx ? ' cur' : ''}${MOV.focus === 'seasons' && i === MOV.sIdx ? ' focus' : ''}">${esc(s.name) || 'الموسم ' + ar(i + 1)}</div>`).join('') + '</div>'
        + '<div class="lineh">الحلقات</div><div class="pills">' + S.eps.map((e, i) => {
          const m = seen[S.id + ':' + e] || {};
          const last = r0 && String(r0.season) === String(S.id) && r0.ep === e;
          // a part watched: a ring around the number, as far as it went
          const pct = m.dur && m.pos && !m.done ? Math.max(4, Math.min(96, Math.round(m.pos / m.dur * 100))) : 0;
          return `<div class="pill ep${pct ? ' part' : ''}${last ? ' cur' : ''}${m.done ? ' seen' : ''}${MOV.focus === 'eps' && i === MOV.eIdx ? ' focus' : ''}">`
            + (pct ? `<svg class="ring" viewBox="0 0 40 40"><circle cx="20" cy="20" r="18"/><circle class="on" cx="20" cy="20" r="18" pathLength="100" stroke-dasharray="${pct} 100" transform="rotate(-90 20 20)"/></svg>` : '')
            + `<span>${ar(e)}</span></div>`; }).join('') + '</div>';
      const r = RES()[`series:${it.id}`];
      if(r && r.ep){
        const sn = (MOV.seasons.find(x => x.id === r.season) || {}).name || '';
        eps += `<div class="last">آخر ما شاهدته: ${esc(sn)} · الحلقة ${ar(r.ep)}${r.pos ? ' · من ' + ar(mmss(r.pos)) : ''}</div>`;
      }
    }
  }
  $('meps').innerHTML = eps;
  // a long season: the chosen pill is brought into sight
  $('meps').querySelectorAll('.pills').forEach(b => reveal(b, b.querySelector('.focus') || b.querySelector('.cur')));
}

// ---- playing a film or an episode
let PLAY = null, pollT = 0, pbarT = 0, PFOCUS = 'bar', PBTN = 0;
// seeking: one press 10 seconds; held, the jumps grow (30 s, 1 min, 3 min, 5 min) and the target runs ahead of
// the player, which seeks once the key rests
const SEEK = { target: null, t0: 0, last: 0, tm: 0 };
function seek(dir, repeat, s){
  const now = Date.now();
  if(SEEK.target == null || now - SEEK.last > 900){ SEEK.target = s.pos || 0; SEEK.t0 = now; }
  const held = now - SEEK.t0;
  const step = !repeat ? 10000 : held < 1500 ? 30000 : held < 4000 ? 60000 : held < 8000 ? 180000 : 300000;
  SEEK.target = Math.max(0, Math.min((s.dur || Infinity) - 5000, SEEK.target + dir * step));
  SEEK.last = now;
  $('pcur').textContent = mmss(SEEK.target);
  if(s.dur) $('pfill').style.width = (SEEK.target / s.dur * 100) + '%';
  clearTimeout(SEEK.tm);
  SEEK.tm = setTimeout(() => { Live && Live.seekTo(SEEK.target); SEEK.target = null; }, repeat ? 450 : 250);
}
const ICON = {
  play: '<svg viewBox="0 0 24 24"><path d="M8 5.2v13.6a1 1 0 0 0 1.5.9l11-6.8a1 1 0 0 0 0-1.8l-11-6.8A1 1 0 0 0 8 5.2z"/></svg>',
  pause: '<svg viewBox="0 0 24 24"><rect x="6" y="4.5" width="4.4" height="15" rx="1.2"/><rect x="13.6" y="4.5" width="4.4" height="15" rx="1.2"/></svg>',
  next: '<svg viewBox="0 0 24 24"><path d="M5 5.6v12.8a1 1 0 0 0 1.6.8l9-6.4a1 1 0 0 0 0-1.6l-9-6.4A1 1 0 0 0 5 5.6z"/><rect x="17" y="4.5" width="2.8" height="15" rx="1.1"/></svg>',
  prev: '<svg viewBox="0 0 24 24"><path d="M19 5.6v12.8a1 1 0 0 1-1.6.8l-9-6.4a1 1 0 0 1 0-1.6l9-6.4a1 1 0 0 1 1.6.8z"/><rect x="4.2" y="4.5" width="2.8" height="15" rx="1.1"/></svg>',
};
let PPLAYING = null;
/** under the timeline: stop/play, and for a series the previous and the next episode beside it (signs only) */
function drawPbtns(){
  const box = $('pbtns'); if(!box || !PLAY) return;
  const series = PLAY.it.kind === 'series' && PLAY.seasons && PLAY.season;
  const n = series && nextEpisode(PLAY.seasons, PLAY.season, PLAY.ep), p = series && prevEpisode(PLAY.seasons, PLAY.season, PLAY.ep);
  PLAY.btns = [p && Object.assign({ act: 'prev' }, p), { act: 'pp' }, n && Object.assign({ act: 'next' }, n)].filter(Boolean);
  box.innerHTML = PLAY.btns.map((b, i) => `<div class="b ${b.act}${PFOCUS === 'btns' && i === PBTN ? ' focus' : ''}">${ICON[b.act === 'pp' ? (PPLAYING === false ? 'play' : 'pause') : b.act]}</div>`).join('');
  $('phint').textContent = series ? '' : 'OK إيقاف/تشغيل · ◀▶ تقديم وترجيع (اضغط مطولاً للأسرع)';
}
let PPAT = 0;
function playPause(s){
  if(!Live) return;
  const playing = PPLAYING != null ? PPLAYING : !!s.playing;
  playing ? Live.pause() : Live.resume();
  PPLAYING = !playing; PPAT = Date.now(); drawPbtns();
}
async function playVod(it, opts = {}){
  try{
    let u, key = it.kind + ':' + it.id, title = it.name;
    if(it.kind === 'series' && XT){
      const S = opts.season, e = (S.list || []).find(x => x.num === opts.ep);
      if(!e) throw new Error('');
      u = xurl('series', e.id, e.ext);
      title = `${it.name} — ${S.name || ''} · الحلقة ${ar(opts.ep)}`;
    } else if(XT || M3U) u = it.cmd;
    else if(it.kind === 'series'){
      const S = opts.season; u = await link('vod', S.cmd, '&series=' + opts.ep);
      title = `${it.name} — ${S.name || ''} · الحلقة ${ar(opts.ep)}`;
    } else u = await link('vod', it.cmd);
    PLAY = { it, key, title, season: opts.season || null, ep: opts.ep || null, lastSave: 0,
      seasons: opts.seasons || (MOV && MOV.item && MOV.item.id === it.id ? MOV.seasons : null) };
    PFOCUS = 'bar'; PBTN = 0; PPLAYING = null; PHIDE = false; drawPbtns();
    screen = 'player'; show();
    $('ptitle').textContent = title;
    if(Live) Live.playFrom(0, u, opts.from || 0);
    showPbar();
    clearInterval(pollT); pollT = setInterval(pollPlayer, 500);
  }catch(e){ toast('تعذّر تشغيل «' + it.name + '»'); }
}
async function resumeItem(v){
  const it = { kind: v.kind, id: v.id, name: v.name, img: v.img, cmd: v.cmd };
  if(v.kind === 'series'){
    // the seasons too (kept a month), so the player has its episode buttons and the next episode
    const seasons = XT ? await xDetails(it).catch(() => null) : null;
    const so = seasons && seasons.find(x => x.id === v.season) || v.seasonObj;
    return playVod(it, { season: so, ep: v.ep, from: v.pos, seasons });
  }
  else playVod(it, { from: v.pos });
}
const EPS = id => store.get('eps:' + id, {});   // a series' episodes: 'season:ep' -> { pos, dur, done, at }
function keepEpisode(id, season, ep, v){ const m = EPS(id); m[season + ':' + ep] = Object.assign(m[season + ':' + ep] || {}, v, { at: Date.now() }); store.set('eps:' + id, m); }
function nextEpisode(seasons, season, ep){
  const si = seasons.findIndex(x => x.id === season.id), S = seasons[si];
  const i = S ? S.eps.indexOf(ep) : -1;
  if(S && i >= 0 && i < S.eps.length - 1) return { season: S, ep: S.eps[i + 1] };
  const N = seasons[si + 1]; return N && N.eps.length ? { season: N, ep: N.eps[0] } : null;
}
function prevEpisode(seasons, season, ep){
  const si = seasons.findIndex(x => x.id === season.id), S = seasons[si];
  const i = S ? S.eps.indexOf(ep) : -1;
  if(S && i > 0) return { season: S, ep: S.eps[i - 1] };
  const P = seasons[si - 1]; return P && P.eps.length ? { season: P, ep: P.eps[P.eps.length - 1] } : null;
}
function saveProgress(force){
  if(!PLAY || !Live) return;
  const s = JSON.parse(Live.state() || '{}');
  if(!s.pos) return;
  if(!force && Date.now() - PLAY.lastSave < 10000) return;
  PLAY.lastSave = Date.now();
  const done = s.dur && s.pos > s.dur - 90000;
  if(PLAY.it.kind === 'series' && PLAY.season){
    keepEpisode(PLAY.it.id, PLAY.season.id, PLAY.ep, { pos: s.pos, dur: s.dur, done });
    if(done){   // finished: «تابع» points to the next episode, from its start
      const n = PLAY.seasons && nextEpisode(PLAY.seasons, PLAY.season, PLAY.ep);
      if(!n) return keepResume(PLAY.key, null);
      return keepResume(PLAY.key, { kind: 'series', id: PLAY.it.id, name: PLAY.it.name, img: PLAY.it.img || '', cmd: '',
        pos: 0, dur: 0, at: Date.now(), ep: n.ep, season: n.season.id, seasonObj: n.season, next: true });
    }
  } else if(done) return keepResume(PLAY.key, null);   // a finished film: off «تابع المشاهدة»
  keepResume(PLAY.key, { kind: PLAY.it.kind, id: PLAY.it.id, name: PLAY.it.name, img: PLAY.it.img || '', cmd: PLAY.it.cmd || '',
    pos: s.pos, dur: s.dur, at: Date.now(), ep: PLAY.ep, season: PLAY.season && PLAY.season.id, seasonObj: PLAY.season });
}
function pollPlayer(){
  if(screen !== 'player' || !Live) return;
  const s = JSON.parse(Live.state() || '{}');
  $('pdur').textContent = mmss(s.dur || 0);
  if(SEEK.target == null){   // while seeking the bar shows the target
    $('pcur').textContent = mmss(s.pos || 0);
    $('pfill').style.width = s.dur ? (s.pos / s.dur * 100) + '%' : '0';
  }
  const on = !!s.playing || !!s.buffering;   // the sign follows the player (a pause from the remote's own key too)
  if(PPLAYING !== on && s.pos != null && Date.now() - PPAT > 1500){ PPLAYING = on; drawPbtns(); }
  if((s.buffering || PPLAYING === false) && !PHIDE) showPbar();   // paused: the bar stays, unless Back sent it away
  saveProgress(false);
}
let PHIDE = false;
function showPbar(){
  const b = $('pbar'), was = b.classList.contains('hide');
  PHIDE = false;
  b.classList.remove('hide'); clearTimeout(pbarT);
  pbarT = setTimeout(() => { b.classList.add('hide'); if(PFOCUS === 'btns'){ PFOCUS = 'bar'; drawPbtns(); } frostSoon(); }, 4000);
  if(was) frostSoon();
}
function stopVod(){ saveProgress(true); clearInterval(pollT); Live && Live.stop(); PLAY = null; }
window.onEnded = tile => {
  if(screen !== 'player' || !PLAY) return;
  if(PLAY.it.kind !== 'series') keepResume(PLAY.key, null);
  // a series: the next episode
  if(PLAY.it.kind === 'series' && PLAY.season){
    keepEpisode(PLAY.it.id, PLAY.season.id, PLAY.ep, { done: true });
    const n = PLAY.seasons && nextEpisode(PLAY.seasons, PLAY.season, PLAY.ep);
    if(n) return playVod(PLAY.it, { season: n.season, ep: n.ep, seasons: PLAY.seasons });
  }
  stopVod(); screen = 'home'; show();
};
window.onPlayerError = async (tile, code) => {
  // a stream that fails: the account is checked again now (it may have ended or be paused)
  if(XT){ try{ await xLogin(true); }catch(e){ return toast(e.message); } }
  toast('تعذّر التشغيل، أعد المحاولة أو اختر غيره');
};

// ---- favourites and search
function drawFavs(){
  const f = FAVS(), body = $('favbody');
  if(!f.length){
    body.innerHTML = `<div class="notice glass"><div class="ic">☆</div><h2>لم تتم إضافة شيء إلى المفضلة حتى الآن</h2>`
      + `<p>اضغط مطولاً على OK فوق أي فيلم أو مسلسل أو قناة لإضافته إلى المفضلة، والضغط المطول مرة أخرى يزيله.</p></div>`;
    return;
  }
  body.innerHTML = `<div class="sres">${f.map((x, i) => `<div class="card poster${i === FSEL ? ' focus' : ''}"><div class="art" style="${cardArt(x, i)}"></div>`
    + `${x.kind === 'live' ? '<div class="kind">قناة</div>' : ''}<div class="cap">${esc(x.name)}</div></div>`).join('')}</div>`;
}
let FSEL = 0, SSEL = -1, SRES = [], searchT = 0, LAST_IDS = '';
let SCOPE = 'vod';   // what the search looks in: 'vod', 'series' or 'live', by where it was opened from
const SWORD = { vod: ['بحث في الأفلام', 'اسم فيلم…'], series: ['بحث في المسلسلات', 'اسم مسلسل…'], live: ['بحث عن قناة', 'اسم قناة أو رقمها…'] };
function openSearch(scope){ SCOPE = scope; screen = 'search'; show(); }
function leaveSearch(){
  if(SCOPE === 'live') return openLive(cur);
  screen = 'browse'; show(true);
}
function drawSearch(){
  const q = $('q').value.trim();
  $('stitle').textContent = SWORD[SCOPE][0]; $('q').placeholder = SWORD[SCOPE][1];
  clearTimeout(searchT);
  if(q.length < (SCOPE === 'live' ? 1 : 2)){ SRES = []; LAST_IDS = ''; $('sres').innerHTML = '<div class="none">' + (SCOPE === 'live' ? 'اكتب اسم القناة أو رقمها' : 'اكتب حرفين أو أكثر') + '، أو استخدم البحث الصوتي من جهاز التحكم</div>'; return; }
  searchT = setTimeout(async () => {
    const lq = q.toLowerCase(), has = x => x.name.toLowerCase().includes(lq), kind = SCOPE === 'vod' ? 'أفلام' : 'مسلسلات';
    let found;
    if(SCOPE === 'live') found = firstN(ALL, c => c.n.toLowerCase().includes(lq) || String(c.num) === q, 30).map(c => ({ kind: 'live', id: c.id, name: c.n, img: c.logo, ch: c }));
    else if(M3U) found = firstN((PAGES[kind + ':*'] || { items: [] }).items, has, 30);
    else if(XT) found = firstN(SCOPE === 'vod' ? XVOD : XSER, has, 30);
    else found = listOf(await call(`type=${SCOPE}&action=get_ordered_list&search=${enc(q)}&category=*&genre=*&p=1&sortby=added`).catch(() => null)).map(x => vItem(x, SCOPE));   // one request, of this kind only
    if($('q').value.trim() !== q || screen !== 'search') return;
    const next = found.slice(0, 30), ids = SCOPE + next.map(x => x.kind + x.id).join();
    if(ids === LAST_IDS) return;   // the same results: nothing drawn again
    LAST_IDS = ids; SRES = next;
    SSEL = -1; drawSearchRes(q);
  }, 700);   // asked when the typing pauses
}
/** the gold card moves; nothing is drawn again (a redraw fetched every picture anew) */
function moveFocus(box, i){
  const cards = box.querySelectorAll('.card');
  cards.forEach((c, k) => c.classList.toggle('focus', k === i));
  if(cards[i]) cards[i].scrollIntoView({ block: 'nearest', behavior: 'smooth' });
}
function drawSearchRes(q){
  $('sres').innerHTML = !SRES.length ? '<div class="none">لا توجد نتائج لـ «' + esc(q || $('q').value.trim()) + '»</div>'
    : SRES.map((x, i) => `<div class="card poster${i === SSEL ? ' focus' : ''}"><div class="art" style="${cardArt(x, i + 3)}"></div>${badge(x)}`
      + `<div class="cap">${esc(x.name)}<small>${x.kind === 'live' ? 'قناة' : x.kind === 'series' ? 'مسلسل' : 'فيلم'}</small></div></div>`).join('');
}
function openResult(x){ if(x.kind === 'live'){ const c = x.ch || ALL.find(c2 => c2.id === x.id); if(c) openLive(c.i); } else openMovie(x); }

// ---- the screens' switch
function show(keep){
  ['setup', 'home', 'live', 'movie', 'browse', 'favs', 'search', 'player'].forEach(s => $(s).classList.toggle('on', s === screen));
  $('tv').classList.toggle('video', screen === 'live' || screen === 'player');
  if(screen !== 'live' && screen !== 'player' && Live) Live.stop();
  if(screen === 'home'){ HOME = null; HOME_AT = Date.now(); drawHome(); myPhoto(); }
  if(screen === 'browse'){ if(keep) GW = ''; drawBrowse(); }
  if(screen === 'favs'){ if(!keep) FSEL = 0; FSEL = Math.max(0, Math.min(FSEL, FAVS().length - 1)); drawFavs(); }
  if(screen === 'movie') drawMovie();
  if(screen === 'search' && !keep){ const q = $('q'); q.value = ''; LAST_IDS = ''; drawSearch(); setTimeout(() => q.focus(), 50); }
  if(screen === 'search' && keep && SSEL < 0) setTimeout(() => $('q').focus(), 50);
  if(screen === 'live') drawLive(false);
  if((screen !== 'search' || (keep && SSEL >= 0)) && screen !== 'setup') $('tv').focus();
  frostSoon();
}

// ======================================================================== the remote
// ---- leaving: one question. The app looks for a newer version by itself (at the start, and again as the
// question opens): with one the question is «تحديث التطبيق والخروج؟», otherwise «متأكد أنك تريد الخروج من التطبيق؟»
const EXIT = { on: false, sel: 0, msg: '', busy: false, latest: 0, mine: 0 };
const newer = () => EXIT.latest > EXIT.mine && EXIT.mine > 0;
function lookForUpdate(){ if(Live && Live.checkUpdate) Live.checkUpdate(); }
/** a popup comes in (it grows a little as it clears) and goes out the same way, quicker */
function pop(el, on){
  clearTimeout(el._t);
  if(on){ el.classList.remove('out'); el.classList.add('on'); return; }
  if(!el.classList.contains('on')) return;
  el.classList.add('out');
  el._t = setTimeout(() => el.classList.remove('on', 'out'), 160);
}
function drawExit(){
  pop($('exit'), EXIT.on);
  if(!EXIT.on) return;
  $('xtitle').textContent = newer() ? 'تحديث التطبيق والخروج؟' : 'متأكد أنك تريد الخروج من التطبيق؟';
  $('xmsg').textContent = EXIT.msg || (newer() ? 'يوجد إصدار جديد ٣٫' + ar(EXIT.latest) + ' (الحالي ٣٫' + ar(EXIT.mine) + ')' : '');
  $('xbtns').innerHTML = ['نعم', 'لا'].map((t, i) => `<div class="btn${i === 0 ? ' main' : ''}${i === EXIT.sel && !EXIT.busy ? ' focus' : ''}">${t}</div>`).join('');
}
function openExit(){ Object.assign(EXIT, { on: true, sel: 0, msg: '', busy: false }); drawExit(); lookForUpdate(); }
function closeExit(){ EXIT.on = false; EXIT.busy = false; clearTimeout(EXIT.t); drawExit(); }
function exitKey(k){
  if(k === 'Escape') return closeExit();
  if(EXIT.busy) return;
  if(k === 'ArrowLeft') EXIT.sel = 1;
  else if(k === 'ArrowRight') EXIT.sel = 0;
  else if(k === 'Enter'){
    if(EXIT.sel === 1) return closeExit();
    if(!newer()){ closeExit(); if(Live && Live.exit) Live.exit(); return; }
    EXIT.busy = true; EXIT.msg = 'جارٍ تنزيل الإصدار الجديد… يُغلق التطبيق عند تثبيته';
    Live.update();
    clearTimeout(EXIT.t); EXIT.t = setTimeout(() => window.onUpdate('تعذّر التحديث: لا يوجد اتصال'), 120000);
  }
  drawExit();
}
window.onUpdateInfo = (latest, mine) => {
  EXIT.latest = latest; EXIT.mine = mine;
  if(EXIT.on && !EXIT.busy) drawExit();   // the question becomes the update's when a newer version is found
};
window.onUpdate = text => {
  if(!EXIT.on) return toast(text);
  clearTimeout(EXIT.t);
  EXIT.busy = false; EXIT.msg = text;
  if(/^جارٍ تثبيت/.test(text)){ EXIT.busy = true; setTimeout(() => { if(Live && Live.exit) Live.exit(); }, 2500); }   // closed: the new version installs
  else { EXIT.latest = 0; EXIT.sel = 0; }   // it could not update: the plain question, so leaving stays possible
  drawExit();
};

// ---- the accounts: a popup over the home screen. A card for each account and «+» for another link; a card
// opens, inside the same popup, this TV's MAC and the field for the link
const ACCT = { on: false, view: 'list', sel: 0, edit: null, focus: 'url', btn: 0, sure: false };
const USER = '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round"><circle cx="12" cy="8.5" r="3.6" fill="rgba(241,207,138,.22)"/><path d="M4.8 19.5c1.2-3.4 4-5.2 7.2-5.2s6 1.8 7.2 5.2"/></svg>';
function openAcct(edit, msg){
  Object.assign(ACCT, { on: true, view: 'list', sel: Math.max(0, ACCTS.findIndex(a => a.id === ACTIVE)) });
  pop($('apop'), true);
  if(edit) editAcct(edit, msg); else drawAcct();
}
function closeAcct(){ ACCT.on = false; pop($('apop'), false); $('tv').focus(); }
function newAcct(){
  const n = ACCTS.reduce((m, a) => Math.max(m, +(String(a.name).match(/(\d+)\s*$/) || [0, 0])[1]), 0) + 1;
  return { id: null, name: 'urtv ' + n, url: '' };
}
function drawAcct(){
  ACCT.view = 'list';
  $('atitle').textContent = 'الحسابات';
  const cards = ACCTS.concat([{ add: true }]);
  ACCT.sel = Math.max(0, Math.min(ACCT.sel, cards.length - 1));
  $('abody').innerHTML = '<div class="accts">' + cards.map((a, i) => a.add
    ? `<div class="acard add${i === ACCT.sel ? ' focus' : ''}"><div class="ava plus">+</div><b>إضافة حساب</b><small>برابط آخر</small></div>`
    : `<div class="acard${a.id === ACTIVE ? ' cur' : ''}${i === ACCT.sel ? ' focus' : ''}"><div class="ava">${USER}</div><b>${esc(a.name)}</b><small>${a.id === ACTIVE ? 'الحساب الحالي' : 'اضغط للتبديل إليه'}</small></div>`).join('') + '</div>';
}
function editAcct(a, msg){
  Object.assign(ACCT, { view: 'edit', edit: a, focus: 'url', btn: 0, sure: false });
  $('atitle').textContent = a.name;
  $('abody').innerHTML = `<div class="lbl">عنوان الماك (MAC) لهذا الجهاز</div><div class="mac">${esc(CFG.mac || '')}</div>`
    + `<div class="lbl">رابط البوابة أو رابط الاشتراك من المزوّد</div>`
    + `<input id="aurl" placeholder="http://portal.example:8080/c/" autocomplete="off" autocapitalize="off" spellcheck="false">`
    + `<div class="btns" id="abtns"></div><div class="st" id="ast"></div><div class="ver">الإصدار ${ar(CFG.version || '')}</div>`;
  $('aurl').value = a.url || '';
  $('ast').textContent = msg || '';
  acctBtns();
  setTimeout(() => { if(ACCT.on && ACCT.view === 'edit' && ACCT.focus === 'url') $('aurl').focus(); }, 80);
}
function acctBtns(){
  const B = ['اتصال'].concat(ACCT.edit.id != null ? [ACCT.sure ? 'تأكيد الحذف؟' : 'حذف الحساب'] : []);
  ACCT.btn = Math.min(ACCT.btn, B.length - 1);
  $('abtns').innerHTML = B.map((t, i) => `<div class="btn${i === 0 ? ' main' : ''}${ACCT.focus === 'btns' && i === ACCT.btn ? ' focus' : ''}">${t}</div>`).join('');
  return B.length;
}
/** the link is kept under its account and handed to the app, which starts again on it */
function connectAcct(){
  const a = ACCT.edit, url = $('aurl').value.trim();
  if(!url){ $('ast').textContent = 'أدخل رابط البوابة أو رابط الاشتراك أولاً'; return; }
  if(a.id == null){ a.id = ACCTS.reduce((m, x) => Math.max(m, x.id), -1) + 1; ACCTS.push({ id: a.id, name: a.name, url }); }
  else ACCTS.find(x => x.id === a.id).url = url;
  useAcct(a.id);
}
function useAcct(id){
  const a = ACCTS.find(x => x.id === id);
  store.set('accounts', ACCTS); store.set('activeAcct', a ? a.id : 0);
  if($('ast')) $('ast').textContent = 'جارٍ الاتصال…';
  if(Live) Live.setPortal(a ? a.url : '');
  setTimeout(() => location.reload(), 150);   // the app's screens start again, on that account
}
function dropAcct(){
  const id = ACCT.edit.id;
  ACCTS = ACCTS.filter(x => x.id !== id);
  const pre = id ? 'a' + id + ':' : null;   // what was kept for it goes too
  try{ if(pre) Object.keys(localStorage).filter(k => k.startsWith(pre)).forEach(k => localStorage.removeItem(k)); }catch(e){}
  store.set('accounts', ACCTS);
  if(id === ACTIVE) return useAcct(ACCTS.length ? ACCTS[0].id : -1);
  drawAcct();
}
function acctKey(k){
  if(ACCT.view === 'list'){
    const n = ACCTS.length + 1;
    if(k === 'Escape'){ if(READY) closeAcct(); return; }
    if(k === 'ArrowLeft') ACCT.sel = Math.min(n - 1, ACCT.sel + 1);
    else if(k === 'ArrowRight') ACCT.sel = Math.max(0, ACCT.sel - 1);
    else if(k === 'Enter') return editAcct(ACCT.sel < ACCTS.length ? Object.assign({}, ACCTS[ACCT.sel]) : newAcct());
    return drawAcct();
  }
  if(k === 'Escape') return drawAcct();
  if(ACCT.focus !== 'btns') return;
  const n = acctBtns();
  if(k === 'ArrowUp'){ ACCT.focus = 'url'; ACCT.sure = false; acctBtns(); return $('aurl').focus(); }
  if(k === 'ArrowLeft') ACCT.btn = Math.min(n - 1, ACCT.btn + 1);
  else if(k === 'ArrowRight') ACCT.btn = Math.max(0, ACCT.btn - 1);
  else if(k === 'Enter'){
    if(ACCT.btn === 0) return connectAcct();
    if(ACCT.sure) return dropAcct();
    ACCT.sure = true;   // asked once more before an account is taken off
  }
  if(k !== 'Enter') ACCT.sure = false;
  acctBtns();
}

function key(k, repeat){
  if(EXIT.on) return exitKey(k);
  if(ACCT.on) return acctKey(k);
  if(screen === 'home'){
    const R = homeRows();
    if(row === -2 && (k === 'ArrowLeft' || k === 'ArrowRight')) return drawHome();   // the profile stands alone
    if(k === 'ArrowDown'){ if(row < R.length - 1){ row++; col = 0; } }   // another row: from its first card
    else if(k === 'ArrowUp'){ if(row > -2){ row--; col = 0; } }   // above the tabs: the profile
    else if(k === 'ArrowLeft'){ if(row === -1) tab = Math.min(TABS.length - 1, tab + 1); else col = Math.min(R[row].items.length - 1, col + 1); }
    else if(k === 'ArrowRight'){ if(row === -1) tab = Math.max(0, tab - 1); else col = Math.max(0, col - 1); }
    else if(k === 'Enter' && row >= 0){   // a long OK: to the favourites (or off them)
      const x = R[row].items[col];
      if(!repeat) return x.go();
      toggleFav(x.it);
    }
    else if(k === 'Enter' && row === -2) return openAcct();
    else if(k === 'Enter' && row === -1){
      if(repeat && TABS[tab] === 'الرئيسية'){ openAcct(); return; }   // a long OK on «الرئيسية»: the activation screen
      if(TABS[tab] === 'أفلام' || TABS[tab] === 'مسلسلات') return openBrowse(TABS[tab]);
      if(TABS[tab] === 'المفضلة'){ screen = 'favs'; return show(); }
      if(TABS[tab] === 'مباشر') return openLive(lastChannel());
    }
    drawHome();
  } else if(screen === 'live'){
    if(!listOpen){
      if(k === 'ArrowUp' || k === 'ChannelUp'){ cur = (cur + 1) % ALL.length; zap(); }
      else if(k === 'ArrowDown' || k === 'ChannelDown'){ cur = (cur - 1 + ALL.length) % ALL.length; zap(); }
      else if(k === 'Enter') openList();
      else if(k === 'Escape'){ screen = 'home'; show(); }
    } else {
      const chans = (CATS[lcat] || { ch: [] }).ch;
      setTimeout(listIdle, 0);   // after this key has done its work (it may close the list itself)
      if(catsOpen){
        if(k === 'ArrowDown' || k === 'ArrowUp'){
          const n = Math.max(0, Math.min(CATS.length - 1, csel + (k === 'ArrowDown' ? 1 : -1)));
          if(n !== csel){ csel = n; moveCats(); }
          return;
        }
        clearTimeout(LCT);
        if((k === 'Enter' || k === 'ArrowLeft') && CATS[csel] && CATS[csel].find) return openSearch('live');
        if(k === 'Enter' || k === 'ArrowLeft'){ if(lcat !== csel){ lcat = csel; lsel = 0; LFROM = 0; } catsOpen = false; }
        else if(k === 'Escape') catsOpen = false;
      }
      else if(k === 'ArrowDown') lsel = Math.min(chans.length - 1, lsel + 1);
      else if(k === 'ArrowUp') lsel = Math.max(0, lsel - 1);
      else if(k === 'ArrowRight'){ catsOpen = true; csel = lcat; }
      else if(k === 'Enter' && repeat){ const c = chans[lsel]; if(c && !LONG.done){ LONG.done = true; toggleFav({ kind: 'live', id: c.id, name: c.n, img: c.logo }); lsel = Math.max(0, Math.min(lsel, (CATS[lcat] || { ch: [] }).ch.length - 1)); } }
      else if(k === 'Enter'){
        if(!chans[lsel]) return;
        cur = chans[lsel].i; listOpen = false;
        return zap();
      }
      else if(k === 'Escape'){ listOpen = false; }
      drawLive(false);
    }
  } else if(screen === 'browse'){
    const items = curItems();
    if(bfocus === 'search'){
      if(k === 'Enter') return openSearch(VTYPE[bKind]);
      if(k === 'ArrowDown') bfocus = 'rail';
      else if(k === 'Escape'){ screen = 'home'; return show(); }
    } else if(bfocus === 'rail'){
      if(k === 'ArrowDown'){ moveCat(1); return; }
      else if(k === 'ArrowUp'){ if(bcat === 0) bfocus = 'search'; else { moveCat(-1); return; } }
      else if((k === 'ArrowLeft' || k === 'Enter') && items.length){ bfocus = 'grid'; bi = 0; }
      else if(k === 'Escape'){ screen = 'home'; return show(); }
    } else {
      if(k === 'ArrowLeft'){ if(bi % COLS < COLS - 1 && bi < items.length - 1) bi++; }
      else if(k === 'ArrowRight'){ if(bi % COLS === 0) bfocus = 'rail'; else bi--; }
      // another row: the same column (only the home screen's rows start at their first card)
      else if(k === 'ArrowDown'){ if(Math.floor(bi / COLS) < Math.floor((items.length - 1) / COLS)) bi = Math.min(items.length - 1, bi + COLS); }
      else if(k === 'ArrowUp'){ if(bi < COLS){ bfocus = 'search'; } else bi -= COLS; }
      else if(k === 'Enter' && repeat && items[bi]){   // a long OK: to the favourites (or off them)
        toggleFav(items[bi]); GW = '';
        const left = curItems().length;
        if(!left) bfocus = 'rail'; else bi = Math.min(bi, left - 1);
      }
      else if(k === 'Enter' && items[bi]) return openMovie(items[bi]);
      else if(k === 'Escape') bfocus = 'rail';
    }
    drawBrowse();
  } else if(screen === 'favs'){
    const f = FAVS();
    if(k === 'Escape'){ screen = 'home'; return show(); }
    if(k === 'ArrowLeft') FSEL = Math.min(f.length - 1, FSEL + 1);
    else if(k === 'ArrowRight') FSEL = Math.max(0, FSEL - 1);
    else if(k === 'ArrowDown'){ if(Math.floor(FSEL / 6) < Math.floor((f.length - 1) / 6)) FSEL = Math.min(f.length - 1, FSEL + 6); }
    else if(k === 'ArrowUp'){ if(FSEL >= 6) FSEL -= 6; }
    else if(k === 'Enter' && repeat && f[FSEL]){ toggleFav(f[FSEL]); FSEL = Math.max(0, Math.min(FSEL, FAVS().length - 1)); return drawFavs(); }
    else if(k === 'Enter' && f[FSEL]){
      const x = f[FSEL];
      if(x.kind === 'live'){ const i = ALL.findIndex(c => c.id === x.id); if(i >= 0) return openLive(i); return toast('القناة لم تعد موجودة'); }
      return openMovie(Object.assign({ desc: '', genres: '' }, x));
    }
    moveFocus($('favbody'), FSEL);
  } else if(screen === 'search'){
    if(k === 'Escape') return leaveSearch();
    if(SSEL >= 0){
      if(k === 'ArrowLeft') SSEL = Math.min(SRES.length - 1, SSEL + 1);
      else if(k === 'ArrowRight') SSEL = Math.max(0, SSEL - 1);
      else if(k === 'ArrowDown'){ if(Math.floor(SSEL / 6) < Math.floor((SRES.length - 1) / 6)) SSEL = Math.min(SRES.length - 1, SSEL + 6); }
      else if(k === 'ArrowUp'){ if(SSEL < 6){ SSEL = -1; moveFocus($('sres'), -1); $('q').focus(); return; } else SSEL -= 6; }
      else if(k === 'Enter' && repeat && SRES[SSEL]){ const x = SRES[SSEL]; return toggleFav(x.kind === 'live' ? { kind: 'live', id: x.id, name: x.name, img: x.img } : x); }
      else if(k === 'Enter' && SRES[SSEL]) return openResult(SRES[SSEL]);
      if(SSEL >= 0) moveFocus($('sres'), SSEL);
    }
  } else if(screen === 'movie'){
    const it = MOV.item, S = MOV.seasons && MOV.seasons[MOV.sIdx];
    if(k === 'Escape'){ screen = MOV.back || 'home'; return show(true); }
    if(MOV.focus === 'btns'){
      if(k === 'ArrowLeft') mbtn = Math.min(MOV.btns - 1, mbtn + 1);
      else if(k === 'ArrowRight') mbtn = Math.max(0, mbtn - 1);
      else if(k === 'ArrowDown' && S) MOV.focus = 'seasons';
      else if(k === 'Enter'){
        const rs = resumeOf(it), last = mbtn === MOV.btns - 1;
        if(last){ toggleFav(it); return drawMovie(); }
        if(it.kind === 'series'){
          if(rs && rs.seasonObj){
            const so = (MOV.seasons || []).find(x => x.id === rs.season) || rs.seasonObj;
            return playVod(it, { season: so, ep: rs.ep, from: rs.pos, seasons: MOV.seasons });
          }
          if(S) return playVod(it, { season: MOV.seasons[0], ep: MOV.seasons[0].eps[0], seasons: MOV.seasons });
          return;
        }
        return playVod(it, { from: mbtn === 0 && rs ? rs.pos : 0 });
      }
    } else if(MOV.focus === 'seasons'){
      if(k === 'ArrowLeft') { MOV.sIdx = Math.min(MOV.seasons.length - 1, MOV.sIdx + 1); MOV.eIdx = 0; }
      else if(k === 'ArrowRight') { MOV.sIdx = Math.max(0, MOV.sIdx - 1); MOV.eIdx = 0; }
      else if(k === 'ArrowUp') MOV.focus = 'btns';
      else if(k === 'ArrowDown' || k === 'Enter'){   // into the episodes: on the last one watched when this is its season
        MOV.focus = 'eps';
        const m = { item: it, seasons: MOV.seasons, sIdx: -1, eIdx: 0 }; lastSeen(m);
        if(MOV.eIdx === 0 && m.sIdx === MOV.sIdx) MOV.eIdx = m.eIdx;
      }
    } else if(MOV.focus === 'eps'){
      if(k === 'ArrowLeft') MOV.eIdx = Math.min(S.eps.length - 1, MOV.eIdx + 1);
      else if(k === 'ArrowRight') MOV.eIdx = Math.max(0, MOV.eIdx - 1);
      else if(k === 'ArrowUp') MOV.focus = 'seasons';
      else if(k === 'Enter'){
        const e = S.eps[MOV.eIdx], rs = resumeOf(it);
        const m = EPS(it.id)[S.id + ':' + e];
        return playVod(it, { season: S, ep: e, from: m && !m.done ? m.pos || 0 : 0, seasons: MOV.seasons });
      }
    }
    drawMovie();
  } else if(screen === 'player'){
    const s = Live ? JSON.parse(Live.state() || '{}') : {};
    if(k === 'Escape'){
      const bar = $('pbar');
      if(!bar.classList.contains('hide')){ bar.classList.add('hide'); clearTimeout(pbarT); PHIDE = true; if(PFOCUS === 'btns'){ PFOCUS = 'bar'; drawPbtns(); } return frostSoon(); }
      const it = PLAY && PLAY.it; stopVod(); if(it){ openMovie(it); } else { screen = 'home'; show(); } return;
    }
    const btns = PLAY && PLAY.btns || [];
    if(k === 'PlayPause'){ playPause(s); return showPbar(); }
    if(PFOCUS === 'btns'){   // the buttons (they stand left to right, like the timeline): ◀▶ choose, OK goes, ▲ back to the timeline
      if(k === 'ArrowRight') PBTN = Math.min(btns.length - 1, PBTN + 1);
      else if(k === 'ArrowLeft') PBTN = Math.max(0, PBTN - 1);
      else if(k === 'ArrowUp') PFOCUS = 'bar';
      else if(k === 'Enter' && btns[PBTN]){
        const b = btns[PBTN];
        if(b.act === 'pp'){ playPause(s); return showPbar(); }
        saveProgress(true); return playVod(PLAY.it, { season: b.season, ep: b.ep, seasons: PLAY.seasons });
      }
      drawPbtns(); showPbar(); return;
    }
    if(k === 'Enter') playPause(s);
    else if(k === 'ArrowDown' && btns.length){ PFOCUS = 'btns'; PBTN = Math.max(0, btns.findIndex(b => b.act === 'pp')); drawPbtns(); }
    else if(k === 'ArrowRight' || k === 'Forward') seek(1, repeat, s);    // the bar runs left to right
    else if(k === 'ArrowLeft' || k === 'Rewind') seek(-1, repeat, s);
    showPbar();
  }
}
/** the channels' list closes by itself five seconds after the last key */
let LIDLE = 0;
function listIdle(){
  clearTimeout(LIDLE);
  if(listOpen) LIDLE = setTimeout(() => { if(screen !== 'live' || !listOpen) return; clearTimeout(LCT); listOpen = false; catsOpen = false; drawLive(false); }, 5000);
}
function openList(){ listOpen = true; listIdle(); catsOpen = false; const c = ALL[cur]; lcat = c && c.ci >= 0 ? c.ci : Math.min(2, CATS.length - 1); lsel = Math.max(0, (CATS[lcat] || { ch: [] }).ch.indexOf(c)); LFROM = lsel - (LROWS >> 1); drawLive(false); }

// a long OK (the key held): favourites for a channel, the activation screen from «الرئيسية»
const LONG = { done: false };
document.addEventListener('keydown', e => {
  const t = e.target;
  if(ACCT.on && t && t.id === 'aurl'){   // typing the link: ▼ or OK goes to the buttons under it
    if(e.key === 'ArrowDown' || e.key === 'Enter'){
      e.preventDefault(); if(e.key === 'Enter') LONG.skip = true;
      t.blur(); $('tv').focus(); ACCT.focus = 'btns'; acctBtns();
    }
    return;
  }
  if(screen === 'setup') return setupKey(e);
  if(t && (t.id === 'q' || t.id === 'mic' || t.id === 'lang')){
    if(t.id === 'q' && e.key === 'ArrowDown' && SRES.length){ e.preventDefault(); t.blur(); $('tv').focus(); SSEL = 0; moveFocus($('sres'), 0); }
    if(e.key === 'Escape'){ e.preventDefault(); if(screen === 'search') leaveSearch(); else { screen = 'home'; show(); } }
    return;
  }
  const map = { Backspace: 'Escape', GoBack: 'Escape' };
  const k = map[e.key] || e.key;
  if(['ArrowUp', 'ArrowDown', 'ArrowLeft', 'ArrowRight', 'Enter', 'Escape'].includes(k)){
    e.preventDefault();
    // OK: a long press is timed here (the TV may not repeat a held OK at all); a short one acts on release
    if(k === 'Enter'){
      if(e.repeat) return;
      LONG.armed = false; LONG.done = false;
      clearTimeout(LONG.t);
      LONG.t = setTimeout(() => { LONG.armed = true; key('Enter', true); }, 700);
      return;
    }
    key(k, e.repeat);
  }
}, true);
document.addEventListener('keyup', e => {
  if(screen === 'setup' || e.key !== 'Enter') return;
  if(LONG.skip){ LONG.skip = false; return; }   // the OK that left the link's field is not also a press on the button
  const t = e.target; if(t && (t.id === 'q' || t.id === 'mic' || t.id === 'lang')) return;
  clearTimeout(LONG.t);
  if(!LONG.armed) key('Enter', false);
  LONG.armed = false;
}, true);
window.remote = (name, rep) => {
  if(name === 'LongOK'){ clearTimeout(LONG.t); if(!LONG.armed){ LONG.armed = true; key('Enter', true); } return; }
  key(name, !!rep);
};
let HOME_AT = 0;   // when a screen last gave way to the home screen
window.onBack = () => {
  if(EXIT.on){ closeExit(); return true; }
  if(ACCT.on){ if(ACCT.view === 'list' && !READY) return false; acctKey('Escape'); return true; }
  if(screen === 'home' && !$('setup').classList.contains('on')){   // leaving is asked first
    if(Date.now() - HOME_AT > 900) openExit();   // not by the same Back that just came back to the home screen
    return true;
  }
  if(screen === 'setup'){ if(CFG.portal && READY){ screen = 'home'; show(); return true; } return false; }
  if(screen === 'search' && SSEL >= 0){ SSEL = -1; moveFocus($('sres'), -1); $('q').focus(); return true; }
  key('Escape', false);
  return true;
};
window.onLeave = () => { if(screen === 'player') saveProgress(true); };
// ---- fresh lists: films and series once a day, channels once a week. When the app is open at night (03:00–05:00)
// it does it then; otherwise on coming back or at the start, in the background, when a list is over its age.
let LOADED_AT = 0, REFRESHING = false;
const DAY = 24 * 3600e3;
function ageOf(q){ if(!XT || !Live || !Live.cacheAge) return 0; const a = +Live.cacheAge(xapi(q)); return a < 0 ? Infinity : a; }
function due(){
  if(XT) return { vod: ageOf('&action=get_vod_streams') > DAY, live: ageOf('&action=get_live_streams') > 7 * DAY };
  const old = Date.now() - LOADED_AT;
  return { vod: old > DAY, live: old > 7 * DAY };
}
async function refreshLists(night){
  if(REFRESHING || !READY || paused()) return;
  const d = due(); if(!d.vod && !d.live) return;
  REFRESHING = true; FRESH = true;
  try{
    if(XT){ if(d.live) await xLoadLive(); if(d.vod) await xLoadVod(); }
    else if(M3U) await loadM3u(CFG.portal.replace(/([?&])pasword=/, '$1password='));
    else { for(const k of Object.keys(PAGES)) delete PAGES[k]; if(d.live) await loadLive(); await loadVodCats(); }
    await loadHome();
    LOADED_AT = Date.now(); LCATS_OF = null; LWIN = ''; GW = '';
    if(screen === 'home'){ HOME = null; drawHome(); }
  }catch(e){}
  REFRESHING = false; FRESH = false;
}
setInterval(() => { const h = new Date().getHours(); if(h >= 3 && h < 5) refreshLists(true); }, 20 * 60e3);
window.onReturn = () => { refreshLists(false); if(screen === 'live') zap(); if(screen === 'player' && PLAY){ const it = PLAY.it; const rs = resumeOf(it); stopVod(); openMovie(it); } };

// ---- search: the field, the language, the microphone
const q = $('q'), mic = $('mic'), langBtn = $('lang');
let voiceLang = store.get('voiceLang', 'ar');
langBtn.textContent = voiceLang === 'ar' ? 'ع' : 'EN';
q.addEventListener('input', drawSearch);
q.addEventListener('keydown', e => { if(e.key === 'ArrowLeft' && q.selectionStart === q.value.length){ e.preventDefault(); langBtn.focus(); } });
langBtn.addEventListener('click', () => { voiceLang = voiceLang === 'ar' ? 'en' : 'ar'; store.set('voiceLang', voiceLang); langBtn.textContent = voiceLang === 'ar' ? 'ع' : 'EN'; });
langBtn.addEventListener('keydown', e => {
  if(e.key === 'ArrowRight'){ e.preventDefault(); q.focus(); }
  else if(e.key === 'ArrowLeft'){ e.preventDefault(); mic.focus(); }
});
mic.addEventListener('keydown', e => { if(e.key === 'ArrowRight'){ e.preventDefault(); langBtn.focus(); } });
mic.addEventListener('click', () => {
  mic.classList.add('on'); $('listen').textContent = voiceLang === 'ar' ? 'تحدّث الآن…' : 'Speak now…';
  if(Live) Live.voice(voiceLang); else window.onVoice('');
});
window.onVoice = text => { mic.classList.remove('on'); $('listen').textContent = ''; if(text){ q.value = text; drawSearch(); } };

// ---- activation
let READY = false;
function openSetup(){ screen = 'setup'; show(); $('smac').textContent = CFG.mac || ''; $('surl').value = CFG.portal || ''; $('sver').textContent = 'الإصدار ' + ar(CFG.version || ''); setTimeout(() => $('surl').focus(), 60); }
function setupKey(e){
  const t = e.target;
  if(e.key === 'ArrowDown' && t.id === 'surl'){ e.preventDefault(); $('sgo').focus(); }
  else if(e.key === 'ArrowUp' && t.id === 'sgo'){ e.preventDefault(); $('surl').focus(); }
  else if(e.key === 'Enter' && t.id === 'surl'){ e.preventDefault(); $('sgo').focus(); }
}
$('sgo').addEventListener('click', async () => {
  const u = $('surl').value.trim();
  if(!u){ $('sst').textContent = 'أدخل رابط البوابة أولاً'; return; }
  Live && Live.setPortal(u); CFG.portal = u;
  $('sst').textContent = 'جارٍ الاتصال…';
  await start();
});

/** the owner's photo from the home screen app, beside the clock: asked for until it comes (that app may be
 *  updated after this one, and only then lets it be read) */
let PHOTO = 0;   // 0 not yet, 1 asking, 2 shown
function myPhoto(){
  if(PHOTO || !Live || !Live.hasImg) return;
  PHOTO = 1;
  const im = new Image();
  im.onload = () => { PHOTO = 2; im.className = 'me'; $('prof').appendChild(im); };
  im.onerror = () => { PHOTO = 0; };
  im.src = 'https://urtv.local/avatar.png?' + Date.now();
}
// ---- start
function tick(){ const d = new Date(), h = d.getHours() % 12 || 12; $('clk').textContent = `${ar(String(h).padStart(2, '0'))}:${ar(String(d.getMinutes()).padStart(2, '0'))} ${d.getHours() < 12 ? 'ص' : 'م'}`; }
async function start(){
  if(!CFG.portal){ screen = 'home'; show(); return openAcct(curAcct() ? Object.assign({}, curAcct()) : newAcct()); }
  $('loading').style.display = 'flex';
  try{
    XT = parseXtream(CFG.portal); M3U = false;
    const listy = /get\.php|\.m3u8?(\?|$)|type=m3u/i.test(CFG.portal);
    if(XT){
      try{ await xLogin(); await xLoadLive(); await xLoadVod(); }
      catch(e){   // the server may answer only with the list itself
        if(!listy) throw e;
        try{ await loadM3u(CFG.portal.replace(/([?&])pasword=/, '$1password=')); }
        catch(e2){ throw e; }   // one reason is enough (the list asks the same server)
      }
    }
    else if(listy) await loadM3u(CFG.portal);
    else { await raw('login'); await loadLive(); await loadVodCats(); }
    READY = true;
    // the kept lists were shown at once; older than 6 hours: new ones are asked for in the background
    LOADED_AT = Date.now() - OLDEST;
    setTimeout(() => refreshLists(false), 3000);   // only what is over its age
    await loadHome().catch(() => {});   // the home screen appears whole: its rows are made first
    loadAccount();
    screen = 'home'; tab = 0; HOME = null; row = homeRows().length ? 0 : -1; show();
  }catch(e){
    screen = 'home'; show();
    openAcct(curAcct() ? Object.assign({}, curAcct()) : newAcct(), e.message || 'تعذّر الاتصال');
  }
  $('loading').style.display = 'none';
}
(function(){
  const bgs = ['9hWJs7iblh8', 'LAj90eAXOZA', 'XDtzu1D2P8c', 'f5ZB0LHwi0s'];
  $('tv').style.setProperty('--bg', `url(backgrounds/${bgs[Math.floor(Math.random() * bgs.length)]}.jpg)`);
  tick(); setInterval(tick, 10000);
  if(Live && Live.frost) $('tv').classList.add('frosty');   // the app puts the glass under the panels: they come at once
  setTimeout(lookForUpdate, 8000);   // known before the question at leaving is asked
  $('smac').textContent = CFG.mac || '';
  start();
})();
