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
const store = {
  get(k, d){ try{ const v = localStorage.getItem(k); return v == null ? d : JSON.parse(v); }catch(e){ return d; } },
  set(k, v){ try{ localStorage.setItem(k, JSON.stringify(v)); }catch(e){} },
};
let CFG = {};
try{ CFG = JSON.parse(Live ? Live.config() : '{}'); }catch(e){}

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
function raw(q){ return new Promise((res, rej) => { if(paused()) return rej(new Error(pauseMsg())); if(!Live) return rej(new Error('خارج التطبيق')); const id = ++SEQ; PEND[id] = { res, rej }; Live.call(id, q); }); }
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
function rawGet(url){ return new Promise((res, rej) => { if(paused()) return rej(new Error(pauseMsg())); if(!Live) return rej(new Error('خارج التطبيق')); const id = ++SEQ; PEND[id] = { res, rej }; Live.get(id, url); }); }
// the long lists come trimmed by the app (only what the screens use)
function rawSlim(url, fields){ return new Promise((res, rej) => { if(paused()) return rej(new Error(pauseMsg())); if(!Live || !Live.getSlim) return rawGet(url).then(res, rej); const id = ++SEQ; PEND[id] = { res, rej }; Live.getSlim(id, url, fields); }); }
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
const unix = t => { const n = +t; return n ? new Date(n * 1000).toISOString().replace('T', ' ').slice(0, 19) : String(t || ''); };
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
  ALL = st.map((x, i) => ({ id: String(x.stream_id), n: x.name, num: +x.num || i + 1, cmd: xurl('live', x.stream_id, 'ts'), logo: x.stream_icon || '', g: String(x.category_id) }))
    .sort((a, b) => a.num - b.num);
  CATS = cats.map(c => ({ id: String(c.category_id), name: c.category_name, ch: ALL.filter(x => x.g === String(c.category_id)) })).filter(c => c.ch.length);
  ALL.forEach(c => { c.ci = CATS.findIndex(x => x.id === c.g); });
}
let XVOD = [], XSER = [];
function xItem(x, kind){
  const r = parseFloat(x.rating); 
  return { kind, id: String(kind === 'series' ? x.series_id : x.stream_id), name: x.name || '', img: x.stream_icon || x.cover || '',
    r: r > 0 && r <= 10 ? r : null, year: String(x.year || x.releaseDate || x.release_date || '').slice(0, 4),
    desc: x.plot || '', time: 0, added: unix(kind === 'series' ? x.last_modified : x.added), genres: x.genre || '',
    cmd: kind === 'series' ? '' : xurl('movie', x.stream_id, x.container_extension || 'mp4'), cat: String(x.category_id) };
}
async function xLoadVod(){
  const [vc, sc, v, se] = await Promise.all([xcats('&action=get_vod_categories'), xcats('&action=get_series_categories'),
    xlist('&action=get_vod_streams', 'stream_id,name,stream_icon,rating,year,added,category_id,container_extension'),
    xlist('&action=get_series', 'series_id,name,cover,rating,year,releaseDate,last_modified,category_id')]);
  XVOD = (v || []).map(x => xItem(x, 'vod')); XSER = (se || []).map(x => xItem(x, 'series'));
  const fill = (kind, cats, items) => {
    VOD_CATS[kind] = (cats || []).map(c => ({ id: String(c.category_id), name: c.category_name })).filter(c => items.some(i => i.cat === c.id));
    const byNew = (a, b) => String(b.added).localeCompare(String(a.added));
    PAGES[kind + ':*'] = { items: items.slice().sort(byNew), page: 1, total: items.length, busy: false };
    for(const c of VOD_CATS[kind]){ const it = items.filter(i => i.cat === c.id).sort(byNew); PAGES[kind + ':' + c.id] = { items: it, page: 1, total: it.length, busy: false }; }
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
  const groups = list => { const g = []; for(const x of list) if(!g.includes(x.group)) g.push(x.group); return g; };
  ALL = ch.map((x, i) => ({ id: 'm' + i, n: x.name, num: i + 1, cmd: x.url, logo: x.logo, g: x.group }));
  CATS = groups(ALL.map(c => ({ group: c.g }))).map(g => ({ id: g, name: g, ch: ALL.filter(c => c.g === g) }));
  ALL.forEach(c => { c.ci = CATS.findIndex(x => x.id === c.g); });
  const fill = (kind, list, k) => {
    const items = list.map((x, i) => ({ kind: 'vod', id: k + i, name: x.name, img: x.logo, r: null, year: '', desc: '', time: 0, added: '', genres: '', cmd: x.url, cat: x.group }));
    VOD_CATS[kind] = groups(list).map(g => ({ id: g, name: g }));
    PAGES[kind + ':*'] = { items, page: 1, total: items.length };
    for(const c of VOD_CATS[kind]){ const it = items.filter(i => i.cat === c.id); PAGES[kind + ':' + c.id] = { items: it, page: 1, total: it.length }; }
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
  ALL = ch.map(x => ({ id: String(x.id), n: x.name, num: +x.number || 0, cmd: x.cmd, logo: abs(x.logo), g: String(x.tv_genre_id) }))
    .sort((a, b) => a.num - b.num);
  ALL.forEach((c, i) => { if(!c.num) c.num = i + 1; });
  CATS = g.map(x => ({ id: String(x.id), name: x.title, ch: ALL.filter(c => c.g === String(x.id)) })).filter(c => c.ch.length);
  ALL.forEach(c => { c.ci = CATS.findIndex(x => x.id === c.g); });
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
    const byNew = (a, b) => String(b.added).localeCompare(String(a.added));
    RECENT = XVOD.slice().sort(byNew).slice(0, 15).concat(XSER.slice().sort(byNew).slice(0, 15)).sort(byNew).slice(0, 10);
    HI_SERIES = XSER.slice().sort(byNew).filter(x => x.r >= 8).slice(0, 20);
    HI_MOVIES = XVOD.slice().sort(byNew).filter(x => x.r >= 8).slice(0, 20);
    return;
  }
  const mv = await page('أفلام', '*'), se = await page('مسلسلات', '*');
  await page('أفلام', '*'); await page('مسلسلات', '*');   // two pages each: enough highly rated ones
  const byAdded = (a, b) => String(b.added).localeCompare(String(a.added));
  RECENT = mv.items.slice(0, 15).concat(se.items.slice(0, 15)).sort(byAdded).slice(0, 10);
  HI_SERIES = se.items.filter(x => x.r >= 8).slice(0, 20);
  HI_MOVIES = mv.items.filter(x => x.r >= 8).slice(0, 20);
}
// the home screen's pictures fetched at once (the WebView keeps them in its cache for the next time)
function preload(){
  for(const it of RECENT.concat(HI_SERIES, HI_MOVIES).slice(0, 60)) if(it.img){ const im = new Image(); im.src = it.img; }
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
const RES = () => store.get('resume', {});
function keepResume(key, v){ const r = RES(); if(v) r[key] = v; else delete r[key]; store.set('resume', r); }
const FAVS = () => store.get('favs', []);
const isFav = (kind, id) => FAVS().some(f => f.kind === kind && f.id === id);
function toggleFav(it){
  let f = FAVS(); const at = f.findIndex(x => x.kind === it.kind && x.id === it.id);
  if(at >= 0) f.splice(at, 1); else f.unshift({ kind: it.kind, id: it.id, name: it.name, img: it.img || it.logo || '', cmd: it.cmd || '', r: it.r || null, year: it.year || '' });
  store.set('favs', f);
  toast(at >= 0 ? 'أُزيل من المفضلة' : 'أُضيف إلى المفضلة');
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
const badge = it => it.r ? `<div class="badge">★ ${ar(it.r.toFixed(1)).replace('.', '٫')}</div>` : '';
function continueItems(){
  return Object.entries(RES()).map(([k, v]) => Object.assign({ key: k }, v))
    .filter(v => v.pos > 60000 && (!v.dur || v.pos < v.dur - 90000)).sort((a, b) => b.at - a.at).slice(0, 12);
}
function rowsData(){
  const R = [];
  const cont = continueItems();
  if(cont.length) R.push({ h: 'تابع المشاهدة', kind: 'wide', items: cont.map((v, i) => ({
    html: `<div class="art" style="${cardArt(v, i + 3)}"></div><div class="cap">${esc(v.name)}${v.ep ? ` — الحلقة ${ar(v.ep)}` : ''}<small>من ${ar(mmss(v.pos))}</small>`
      + `<div class="bar"><i style="width:${v.dur ? Math.round(v.pos / v.dur * 100) : 30}%"></i></div></div>`,
    go: () => resumeItem(v) })) });
  const poster = (list, seed) => list.map((it, i) => ({
    html: `<div class="art" style="${cardArt(it, i + seed)}"></div>${badge(it)}`
      + (seed === 7 ? `<div class="kind">${it.kind === 'series' ? 'مسلسل' : 'فيلم'}</div>` : '')
      + `<div class="cap">${esc(it.name)}<small>${seed === 7 ? addedText(it.added) : ar(it.year)}</small></div>`,
    go: () => openMovie(it) }));
  if(RECENT.length) R.push({ h: 'أُضيف حديثاً', kind: 'poster', items: poster(RECENT, 7) });
  if(HI_SERIES.length) R.push({ h: 'مسلسلات جديدة بتقييمات عالية', kind: 'poster', items: poster(HI_SERIES, 1) });
  if(HI_MOVIES.length) R.push({ h: 'أفلام جديدة بتقييمات عالية', kind: 'poster', items: poster(HI_MOVIES, 5) });
  return R;
}
function addedText(a){
  const d = new Date(String(a).replace(' ', 'T')); if(isNaN(d)) return '';
  const days = Math.floor((Date.now() - d) / 864e5);
  return days <= 0 ? 'اليوم' : days === 1 ? 'أمس' : days === 2 ? 'منذ يومين' : days <= 10 ? 'منذ ' + ar(days) + ' أيام' : 'منذ ' + ar(days) + ' يوماً';
}
let VSHIFT = 0;
function setV(v){ VSHIFT = Math.max(0, v); const r = document.querySelector('#rows .rin'); if(r) r.style.transform = `translateY(${-VSHIFT}px)`; }
const SHIFT = {};
function drawHome(){
  $('prof').classList.toggle('focus', row === -2);
  $('tabs').innerHTML = TABS.map((t, i) => `<div class="tab glass${i === tab ? ' sel' : ''}${row === -1 && i === tab ? ' focus' : ''}">${t}</div>`).join('');
  const R = rowsData();
  if(!R.length){ $('rows').innerHTML = '<div class="empty">لا توجد عناوين بعد.</div>'; if(row >= 0) row = -1; return drawTabsOnly(); }
  if(row >= R.length) row = R.length - 1;
  if(row >= 0) col = Math.min(col, R[row].items.length - 1);
  $('rows').innerHTML = `<div class="rin" style="transform:translateY(${-VSHIFT}px)">` + R.map((r, ri) => `<div class="row"><h3>${r.h}</h3><div class="cards" style="transform:translateX(${SHIFT[ri] || 0}px)">${
    r.items.map((it, ci) => `<div class="card ${r.kind} glass${ri === row && ci === col ? ' focus' : ''}">${it.html}</div>`).join('')}</div></div>`).join('') + '</div>';
  if(row < 0){ setV(0); return; }
  const rowsEl = $('rows'), cards = rowsEl.querySelectorAll('.cards')[row], card = cards && cards.children[col];
  if(!card) return;
  { const rb = rowsEl.getBoundingClientRect(), fr = cards.parentElement.getBoundingClientRect();
    if(fr.bottom > rb.bottom - rb.height * .03) setV(VSHIFT + fr.bottom - rb.bottom + rb.height * .06);
    else if(fr.top < rb.top) setV(VSHIFT - (rb.top - fr.top)); }
  const box = rowsEl.getBoundingClientRect(), c = card.getBoundingClientRect(), pad = box.width * .04;
  let shift = SHIFT[row] || 0;
  if(c.left < box.left + pad) shift += (box.left + pad) - c.left;
  else if(c.right > box.right - pad) shift -= c.right - (box.right - pad);
  if(shift < 0) shift = 0;
  if(shift !== (SHIFT[row] || 0)){ SHIFT[row] = shift; cards.style.transform = `translateX(${shift}px)`; }
}
function drawTabsOnly(){ $('prof').classList.toggle('focus', row === -2); $('tabs').innerHTML = TABS.map((t, i) => `<div class="tab glass${i === tab ? ' sel' : ''}${row === -1 && i === tab ? ' focus' : ''}">${t}</div>`).join(''); }

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
  $('inum').textContent = ar(c.num);
  $('inm').textContent = c.n;
  const info = $('info');
  if(flash){ info.classList.remove('hide'); clearTimeout(infoT); infoT = setTimeout(() => info.classList.add('hide'), 3500); }
  if(listOpen){ clearTimeout(infoT); info.classList.add('hide'); }
  const list = $('list');
  list.classList.toggle('hide', !listOpen);
  if(listOpen){
    const cat = CATS[lcat] || { name: '', ch: [] }, chans = cat.ch;
    $('lcat').textContent = cat.name;
    list.classList.toggle('wide', catsOpen);
    $('lcats').innerHTML = CATS.map((c2, i) =>
      `<div class="c${i === csel && catsOpen ? ' sel' : ''}${i === lcat ? ' cur' : ''}"><span>${esc(c2.name)}</span><small>${ar(c2.ch.length)}</small></div>`).join('');
    // a window of the list around the chosen one (sections may hold hundreds)
    const from = Math.max(0, Math.min(lsel - 4, chans.length - 10));
    $('items').innerHTML = chans.slice(from, from + 10).map((x, k) => { const i = from + k;
      return `<div class="it${i === lsel && !catsOpen ? ' sel' : ''}${ALL.indexOf(x) === cur ? ' cur' : ''}"><span class="n">${ar(x.num)}</span>`
        + (x.logo ? `<img class="chanlogo" src="${esc(x.logo)}" onerror="this.remove()">` : '')
        + `<span>${esc(x.n)}${isFav('live', x.id) ? ' <small style="display:inline;color:var(--gold-lt)">★</small>' : ''}</span></div>`; }).join('');
    const cs = $('lcats'), sel = cs.querySelector('.sel, .cur');
    if(sel) sel.scrollIntoView({ block: 'nearest' });
  }
}

// ---- movies / series
const COLS = 5;
function openBrowse(kind){ screen = 'browse'; bKind = kind; bcat = 0; bfocus = 'rail'; bi = 0; bScroll = 0; show(); loadCat(); }
function curItems(){ const c = VOD_CATS[bKind][bcat]; const P = c && PAGES[bKind + ':' + c.id]; return P ? P.items : []; }
async function loadCat(){
  const c = VOD_CATS[bKind][bcat]; if(!c) return drawBrowse();
  const key = bKind + ':' + c.id;
  if(!PAGES[key]) { drawBrowse(); await page(bKind, c.id); }
  if(screen === 'browse') drawBrowse();
}
function drawRail(){
  $('bkind').textContent = bKind;
  $('sbtn').classList.toggle('focus', bfocus === 'search');
  const cats = VOD_CATS[bKind];
  const from = Math.max(0, Math.min(bcat - 6, cats.length - 12));
  $('rail').innerHTML = cats.slice(from, from + 12).map((c, k) => { const i = from + k;
    const P = PAGES[bKind + ':' + c.id];
    return `<div class="c${i === bcat ? ' cur' : ''}${i === bcat && bfocus === 'rail' ? ' sel' : ''}"><span>${esc(c.name)}</span><small>${P && isFinite(P.total) ? ar(P.total) : ''}</small></div>`; }).join('');
}
let BWIN = '', CATT = 0;
/** ▲▼ in the sections: the rail moves now, the section's posters only when the key rests (a held key runs on) */
function moveCat(d){
  const cats = VOD_CATS[bKind];
  const n = Math.max(0, Math.min(cats.length - 1, bcat + d));
  if(n === bcat) return;
  bcat = n; bScroll = 0; bi = 0; BWIN = '';
  drawRail();
  clearTimeout(CATT); CATT = setTimeout(loadCat, 280);
}   // the rows drawn now (section, first row, count): within them only the focus moves
function drawBrowse(){
  const cats = VOD_CATS[bKind];
  if(bfocus === 'grid'){
    const its = curItems(), first = Math.max(0, Math.floor(bi / COLS) - 3) * COLS;
    if(BWIN === bKind + ':' + bcat + ':' + first + ':' + its.length){
      const g = $('grid'), cards = g.querySelectorAll('.card');
      cards.forEach((c, k) => c.classList.toggle('focus', first + k === bi));
      $('sbtn').classList.remove('focus');
      const rail = $('rail').querySelector('.sel'); if(rail) rail.classList.remove('sel');
      scrollGrid();
      return;
    }
  }
  drawRail();
  const items = curItems(), g = $('grid');
  const name = cats[bcat] ? cats[bcat].name : '';
  // only the rows near the focus are drawn: a section may hold thousands
  const firstRow = Math.max(0, Math.floor(bi / COLS) - 3), start = firstRow * COLS, end = Math.min(items.length, start + COLS * 8);
  g.innerHTML = `<div class="in" style="transform:translateY(${-bScroll}px)"><h3>${esc(name)}</h3>${start ? `<div style="grid-column:1/-1;height:0"></div>` : ''}${
    items.slice(start, end).map((it, k) => { const i = start + k;
      return `<div class="card poster glass${bfocus === 'grid' && i === bi ? ' focus' : ''}"><div class="art" style="${cardArt(it, i)}"></div>${badge(it)}`
        + `<div class="cap">${esc(it.name)}<small>${ar(it.year)}</small></div></div>`; }).join('')
    || '<div class="empty" style="grid-column:1/-1;color:var(--dim)">جارٍ التحميل…</div>'}</div>`;
  BWIN = bKind + ':' + bcat + ':' + start + ':' + items.length;
  if(bfocus !== 'grid') { bScroll = 0; g.firstChild.style.transform = 'none'; BWIN = ''; return; }
  scrollGrid();
}
function scrollGrid(){
  const g = $('grid'), cats = VOD_CATS[bKind], items = curItems();
  const card = g.querySelector('.card.focus'); if(!card) return;
  const gb = g.getBoundingClientRect(), cb = card.getBoundingClientRect();
  let s2 = bScroll;
  if(cb.bottom > gb.bottom - gb.height * .04) s2 += cb.bottom - gb.bottom + gb.height * .06;
  else if(cb.top < gb.top + gb.height * .02) s2 -= gb.top - cb.top + gb.height * .04;
  if(s2 !== bScroll){ bScroll = Math.max(0, s2); g.firstChild.style.transform = `translateY(${-bScroll}px)`; }
  // near the end: the next page
  const c = cats[bcat], P = c && PAGES[bKind + ':' + c.id];
  if(P && !P.busy && P.items.length < P.total && bi >= items.length - COLS * 2)
    page(bKind, c.id).then(() => { if(screen === 'browse') drawBrowse(); });
}

// ---- a film or a series
async function openMovie(it){
  screen = 'movie'; MOV = { item: it, seasons: null, sIdx: 0, eIdx: 0, focus: 'btns' }; mbtn = 0; show();
  if(M3U) return;
  if(XT){
    const seasons = await xDetails(it).catch(() => []);
    if(it.kind === 'series') MOV.seasons = seasons || [];
    if(screen === 'movie' && MOV.item === it) drawMovie();
    return;
  }
  if(it.kind === 'series'){
    const j = await call(`type=series&action=get_ordered_list&movie_id=${enc(it.id)}&season_id=0&episode_id=0&p=1`).catch(() => null);
    MOV.seasons = listOf(j).map(s => ({ id: s.id, name: s.name, cmd: s.cmd, eps: (s.series || []).map(Number) })).filter(s => s.eps.length);
    if(screen === 'movie') drawMovie();
  }
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
  $('mbtns').innerHTML = B.map((b, i) => `<div class="btn glass${i === 0 ? ' main' : ''}${MOV.focus === 'btns' && i === mbtn ? ' focus' : ''}">${b}</div>`).join('');
  let eps = '';
  if(it.kind === 'series'){
    if(!MOV.seasons) eps = '<div class="lineh">جارٍ تحميل الحلقات…</div>';
    else if(!MOV.seasons.length) eps = '<div class="lineh">لا توجد حلقات</div>';
    else {
      const S = MOV.seasons[MOV.sIdx];
      eps = '<div class="lineh">المواسم</div><div class="pills">' + MOV.seasons.map((s, i) =>
        `<div class="pill glass${i === MOV.sIdx ? ' cur' : ''}${MOV.focus === 'seasons' && i === MOV.sIdx ? ' focus' : ''}">${esc(s.name) || 'الموسم ' + ar(i + 1)}</div>`).join('') + '</div>'
        + '<div class="lineh">الحلقات</div><div class="pills">' + S.eps.map((e, i) => {
          const r = RES()[`series:${it.id}`], m = EPS(it.id)[S.id + ':' + e] || {};
          const last = r && r.season === S.id && r.ep === e;
          const pct = m.dur && m.pos && !m.done ? Math.round(m.pos / m.dur * 100) : 0;
          return `<div class="pill glass${last ? ' cur' : ''}${m.done ? ' seen' : ''}${MOV.focus === 'eps' && i === MOV.eIdx ? ' focus' : ''}">${ar(e)}${pct ? `<i class="pg" style="width:${pct}%"></i>` : ''}</div>`; }).join('') + '</div>';
      const r = RES()[`series:${it.id}`];
      if(r && r.ep){
        const sn = (MOV.seasons.find(x => x.id === r.season) || {}).name || '';
        eps += `<div class="last">آخر ما شاهدته: ${esc(sn)} · الحلقة ${ar(r.ep)}${r.pos ? ' · من ' + ar(mmss(r.pos)) : ''}</div>`;
      }
    }
  }
  $('meps').innerHTML = eps;
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
/** a series' buttons in the player: the previous and the next episode */
function drawPbtns(){
  const box = $('pbtns'); if(!box) return;
  if(!PLAY || PLAY.it.kind !== 'series' || !PLAY.seasons){ box.innerHTML = ''; if(PLAY) PLAY.btns = []; $('phint').textContent = 'OK إيقاف/تشغيل · ◀▶ تقديم وترجيع (اضغط مطولاً للأسرع)'; return; }
  const n = nextEpisode(PLAY.seasons, PLAY.season, PLAY.ep), p = prevEpisode(PLAY.seasons, PLAY.season, PLAY.ep);
  PLAY.btns = [n && Object.assign({ label: 'الحلقة التالية ⏭' }, n), p && Object.assign({ label: '⏮ الحلقة السابقة' }, p)].filter(Boolean);
  box.innerHTML = PLAY.btns.map((b, i) => `<div class="b${PFOCUS === 'btns' && i === PBTN ? ' focus' : ''}">${b.label}</div>`).join('');
  $('phint').textContent = 'OK إيقاف/تشغيل · ◀▶ تقديم وترجيع · ▼ الحلقات';
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
    PFOCUS = 'bar'; PBTN = 0; drawPbtns();
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
        pos: 0, dur: 0, at: Date.now(), ep: n.ep, season: n.season.id, seasonObj: n.season });
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
  if(s.buffering) showPbar();
  saveProgress(false);
}
function showPbar(){ const b = $('pbar'); b.classList.remove('hide'); clearTimeout(pbarT); pbarT = setTimeout(() => b.classList.add('hide'), 4000); }
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
      + `<p>لإضافة فيلم أو مسلسل اختر «☆ للمفضلة» من صفحته، ولإضافة قناة اضغط مطولاً على OK فوقها في قائمة القنوات.</p></div>`;
    return;
  }
  body.innerHTML = `<div class="sres">${f.map((x, i) => `<div class="card poster glass${i === FSEL ? ' focus' : ''}"><div class="art" style="${cardArt(x, i)}"></div>`
    + `${x.kind === 'live' ? '<div class="kind">قناة</div>' : ''}<div class="cap">${esc(x.name)}</div></div>`).join('')}</div>`;
}
let FSEL = 0, SSEL = -1, SRES = [], searchT = 0, LAST_IDS = '';
function drawSearch(){
  const q = $('q').value.trim();
  if(q.length < 2){ SRES = []; $('sres').innerHTML = '<div class="none">اكتب حرفين أو أكثر، أو استخدم البحث الصوتي من جهاز التحكم</div>'; return; }
  clearTimeout(searchT);
  searchT = setTimeout(async () => {
    const chans = ALL.filter(c => c.n.toLowerCase().includes(q.toLowerCase())).slice(0, 6).map(c => ({ kind: 'live', id: c.id, name: c.n, img: c.logo, ch: c }));
    let found;
    if(M3U){
      const lq = q.toLowerCase(), all = (PAGES['أفلام:*'] || { items: [] }).items.concat((PAGES['مسلسلات:*'] || { items: [] }).items);
      found = all.filter(x => x.name.toLowerCase().includes(lq)).slice(0, 24);
    } else if(XT){
      const lq = q.toLowerCase();
      found = XVOD.filter(x => x.name.toLowerCase().includes(lq)).slice(0, 12).concat(XSER.filter(x => x.name.toLowerCase().includes(lq)).slice(0, 12));
    } else {
      const [v, s] = await Promise.all(['vod', 'series'].map(t => call(`type=${t}&action=get_ordered_list&search=${enc(q)}&category=*&genre=*&p=1&sortby=added`).catch(() => null)));
      found = listOf(v).map(x => vItem(x, 'vod')).concat(listOf(s).map(x => vItem(x, 'series')));
    }
    if($('q').value.trim() !== q) return;
    const next = chans.concat(found).slice(0, 30), ids = next.map(x => x.kind + x.id).join();
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
    : SRES.map((x, i) => `<div class="card poster glass${i === SSEL ? ' focus' : ''}"><div class="art" style="${cardArt(x, i + 3)}"></div>${badge(x)}`
      + `<div class="cap">${esc(x.name)}<small>${x.kind === 'live' ? 'قناة' : x.kind === 'series' ? 'مسلسل' : 'فيلم'}</small></div></div>`).join('');
}
function openResult(x){ if(x.kind === 'live') openLive(ALL.indexOf(x.ch || ALL.find(c => c.id === x.id))); else openMovie(x); }

// ---- the screens' switch
function show(){
  ['setup', 'home', 'live', 'movie', 'browse', 'favs', 'search', 'player'].forEach(s => $(s).classList.toggle('on', s === screen));
  $('tv').classList.toggle('video', screen === 'live' || screen === 'player');
  if(screen !== 'live' && screen !== 'player' && Live) Live.stop();
  if(screen === 'home') drawHome();
  if(screen === 'browse') drawBrowse();
  if(screen === 'favs'){ FSEL = 0; drawFavs(); }
  if(screen === 'movie') drawMovie();
  if(screen === 'search'){ const q = $('q'); q.value = ''; drawSearch(); setTimeout(() => q.focus(), 50); }
  if(screen === 'live') drawLive(false);
  if(screen !== 'search' && screen !== 'setup') $('tv').focus();
}

// ======================================================================== the remote
function key(k, repeat){
  if(screen === 'home'){
    const R = rowsData();
    if(row === -2 && (k === 'ArrowLeft' || k === 'ArrowRight')) return drawHome();   // the profile stands alone
    if(k === 'ArrowDown'){ if(row < R.length - 1){ row++; if(row >= 0) col = Math.min(col, R[row].items.length - 1); } }
    else if(k === 'ArrowUp'){ if(row > -2){ row--; if(row >= 0) col = Math.min(col, R[row].items.length - 1); } }   // above the tabs: the profile
    else if(k === 'ArrowLeft'){ if(row === -1) tab = Math.min(TABS.length - 1, tab + 1); else col = Math.min(R[row].items.length - 1, col + 1); }
    else if(k === 'ArrowRight'){ if(row === -1) tab = Math.max(0, tab - 1); else col = Math.max(0, col - 1); }
    else if(k === 'Enter' && row >= 0) return R[row].items[col].go();
    else if(k === 'Enter' && row === -2) return openSetup();
    else if(k === 'Enter' && row === -1){
      if(repeat && TABS[tab] === 'الرئيسية'){ openSetup(); return; }   // a long OK on «الرئيسية»: the activation screen
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
      if(catsOpen){
        if(k === 'ArrowDown') csel = Math.min(CATS.length - 1, csel + 1);
        else if(k === 'ArrowUp') csel = Math.max(0, csel - 1);
        else if(k === 'Enter' || k === 'ArrowLeft'){ lcat = csel; lsel = 0; catsOpen = false; }
        else if(k === 'Escape') catsOpen = false;
      }
      else if(k === 'ArrowDown') lsel = Math.min(chans.length - 1, lsel + 1);
      else if(k === 'ArrowUp') lsel = Math.max(0, lsel - 1);
      else if(k === 'ArrowRight'){ catsOpen = true; csel = lcat; }
      else if(k === 'Enter' && repeat){ const c = chans[lsel]; if(c && !LONG.done){ LONG.done = true; toggleFav({ kind: 'live', id: c.id, name: c.n, img: c.logo }); } }
      else if(k === 'Enter'){
        cur = ALL.indexOf(chans[lsel]); listOpen = false;
        return zap();
      }
      else if(k === 'Escape'){ listOpen = false; }
      drawLive(false);
    }
  } else if(screen === 'browse'){
    const cats = VOD_CATS[bKind], items = curItems();
    if(bfocus === 'search'){
      if(k === 'Enter'){ screen = 'search'; return show(); }
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
      else if(k === 'ArrowDown') bi = Math.min(items.length - 1, bi + COLS);
      else if(k === 'ArrowUp'){ if(bi < COLS){ bfocus = 'search'; } else bi -= COLS; }
      else if(k === 'Enter' && items[bi]) return openMovie(items[bi]);
      else if(k === 'Escape') bfocus = 'rail';
    }
    drawBrowse();
  } else if(screen === 'favs'){
    const f = FAVS();
    if(k === 'Escape'){ screen = 'home'; return show(); }
    if(k === 'ArrowLeft') FSEL = Math.min(f.length - 1, FSEL + 1);
    else if(k === 'ArrowRight') FSEL = Math.max(0, FSEL - 1);
    else if(k === 'ArrowDown') FSEL = Math.min(f.length - 1, FSEL + 6);
    else if(k === 'ArrowUp') FSEL = Math.max(0, FSEL - 6);
    else if(k === 'Enter' && f[FSEL]){
      const x = f[FSEL];
      if(x.kind === 'live'){ const i = ALL.findIndex(c => c.id === x.id); if(i >= 0) return openLive(i); return toast('القناة لم تعد موجودة'); }
      return openMovie(Object.assign({ desc: '', genres: '' }, x));
    }
    moveFocus($('favbody'), FSEL);
  } else if(screen === 'search'){
    if(k === 'Escape'){ screen = 'home'; return show(); }
    if(SSEL >= 0){
      if(k === 'ArrowLeft') SSEL = Math.min(SRES.length - 1, SSEL + 1);
      else if(k === 'ArrowRight') SSEL = Math.max(0, SSEL - 1);
      else if(k === 'ArrowDown') SSEL = Math.min(SRES.length - 1, SSEL + 6);
      else if(k === 'ArrowUp'){ if(SSEL < 6){ SSEL = -1; moveFocus($('sres'), -1); $('q').focus(); return; } else SSEL -= 6; }
      else if(k === 'Enter' && SRES[SSEL]) return openResult(SRES[SSEL]);
      if(SSEL >= 0) moveFocus($('sres'), SSEL);
    }
  } else if(screen === 'movie'){
    const it = MOV.item, S = MOV.seasons && MOV.seasons[MOV.sIdx];
    if(k === 'Escape'){ screen = 'home'; return show(); }
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
      else if(k === 'ArrowDown' || k === 'Enter') MOV.focus = 'eps';
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
    if(k === 'Escape'){ const it = PLAY && PLAY.it; stopVod(); if(it){ openMovie(it); } else { screen = 'home'; show(); } return; }
    const btns = PLAY && PLAY.btns || [];
    if(PFOCUS === 'btns'){   // an episode's buttons: ◀▶ choose, OK goes, ▲ back to the timeline
      if(k === 'ArrowLeft') PBTN = Math.min(btns.length - 1, PBTN + 1);
      else if(k === 'ArrowRight') PBTN = Math.max(0, PBTN - 1);
      else if(k === 'ArrowUp') PFOCUS = 'bar';
      else if(k === 'Enter' && btns[PBTN]){ const b = btns[PBTN]; saveProgress(true); return playVod(PLAY.it, { season: b.season, ep: b.ep, seasons: PLAY.seasons }); }
      drawPbtns(); showPbar(); return;
    }
    if(k === 'Enter' || k === 'PlayPause'){ s.playing ? Live.pause() : Live.resume(); }
    else if(k === 'ArrowDown' && btns.length){ PFOCUS = 'btns'; PBTN = 0; drawPbtns(); }
    else if(k === 'ArrowRight' || k === 'Forward') seek(1, repeat, s);    // the bar runs left to right
    else if(k === 'ArrowLeft' || k === 'Rewind') seek(-1, repeat, s);
    showPbar();
  }
}
function openList(){ listOpen = true; catsOpen = false; const c = ALL[cur]; lcat = Math.max(0, c ? c.ci : 0); lsel = Math.max(0, (CATS[lcat] || { ch: [] }).ch.indexOf(c)); drawLive(false); }

// a long OK (the key held): favourites for a channel, the activation screen from «الرئيسية»
const LONG = { done: false };
document.addEventListener('keydown', e => {
  const t = e.target;
  if(screen === 'setup') return setupKey(e);
  if(t && (t.id === 'q' || t.id === 'mic' || t.id === 'lang')){
    if(t.id === 'q' && e.key === 'ArrowDown' && SRES.length){ e.preventDefault(); t.blur(); $('tv').focus(); SSEL = 0; moveFocus($('sres'), 0); }
    if(e.key === 'Escape'){ e.preventDefault(); screen = 'home'; show(); }
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
  const t = e.target; if(t && (t.id === 'q' || t.id === 'mic' || t.id === 'lang')) return;
  clearTimeout(LONG.t);
  if(!LONG.armed) key('Enter', false);
  LONG.armed = false;
}, true);
window.remote = (name, rep) => {
  if(name === 'LongOK'){ clearTimeout(LONG.t); if(!LONG.armed){ LONG.armed = true; key('Enter', true); } return; }
  key(name, !!rep);
};
window.onBack = () => {
  if(screen === 'home' && !$('setup').classList.contains('on')){ return false; }   // leave the app
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
    LOADED_AT = Date.now();
    if(screen === 'home') drawHome();
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

// ---- start
function tick(){ const d = new Date(), h = d.getHours() % 12 || 12; $('clk').textContent = `${ar(String(h).padStart(2, '0'))}:${ar(String(d.getMinutes()).padStart(2, '0'))} ${d.getHours() < 12 ? 'ص' : 'م'}`; }
async function start(){
  if(!CFG.portal){ return openSetup(); }
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
    screen = 'home'; tab = 0; row = rowsData().length ? 0 : -1; show(); preload();
  }catch(e){
    openSetup();
    $('sst').textContent = e.message || 'تعذّر الاتصال';
  }
  $('loading').style.display = 'none';
}
(function(){
  const bgs = ['9hWJs7iblh8', 'LAj90eAXOZA', 'XDtzu1D2P8c', 'f5ZB0LHwi0s'];
  $('tv').style.setProperty('--bg', `url(backgrounds/${bgs[Math.floor(Math.random() * bgs.length)]}.jpg)`);
  tick(); setInterval(tick, 10000);
  $('smac').textContent = CFG.mac || '';
  start();
})();
