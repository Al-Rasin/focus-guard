// Focus Guard for Chrome/Brave: same rules as the Android app (see app/.../Limits.java).
importScripts("guard.js");
guard("llekdneeeimgoojkcjlfbajlkkcklnal"); // Focus Guard Keeper; each turns the other back on

// Per-category settings: chrome.storage.local "config" = {cat: {...}}, same JSON as the Android app
// (Config.java). Missing fields fall back to these defaults (the original fixed rules).
const CATS = ["shorts", "reels", "facebook"];
const DEFAULTS = {
  enabled: true, pause_s: 5, pause_step_s: 0, opens: 6, open_min: 15, daily_cap_min: 0, cooldown_min: 0,
  max_videos: 0, extend: { opens: 3, max: 2, cutoff: 19 }, ask_why: false, timer: true, mode: "part", schedule: [],
};
const STRICT_DELAY_MS = 24 * 3600 * 1000; // looser settings wait this long (always on here)

const SHORTS_URL = /youtube\.com\/shorts/;
const REELS_URL = /facebook\.com\/(reel|reels|watch\/reels)\b|fb\.watch\/|facebook\.com\/share\/r\//;
// facebook.com outside Reels is its own category, except Messages and what logging in/opening links needs.
const FACEBOOK_SITE = /^(https?:\/\/)?([a-z0-9-]+\.)*facebook\.com(?![a-z0-9.-])/;
const FACEBOOK_OK = /^(https?:\/\/)?((l|lm)\.facebook\.com|([a-z0-9-]+\.)*facebook\.com\/(messages|messenger_media|login|checkpoint|recover|two_step_verification|two_factor|groupcall|videocall)(?![a-z0-9_-]))/;
const LABELS = { shorts: "YouTube Shorts", reels: "Facebook Reels", facebook: "Facebook" };
const HOME = { shorts: "https://www.youtube.com/", reels: "https://www.facebook.com/messages/",
               facebook: "https://www.facebook.com/messages/" };

// ---- limits (chrome.storage.local, keys like 20260928_shorts) ----

function today() {
  const d = new Date();
  return `${d.getFullYear()}${String(d.getMonth() + 1).padStart(2, "0")}${String(d.getDate()).padStart(2, "0")}`;
}

// ---- settings ----

function withDefaults(c) {
  c = c || {};
  const ext = { ...DEFAULTS.extend, ...(c.extend || {}) };
  const out = { ...DEFAULTS, ...c, extend: ext };
  const int = (v, lo, hi, d) => Number.isFinite(+v) ? Math.min(hi, Math.max(lo, Math.round(+v))) : d;
  for (const k of ["pause_s", "pause_step_s", "opens", "open_min", "daily_cap_min", "cooldown_min", "max_videos"])
    out[k] = int(out[k], 0, 1440, DEFAULTS[k]);
  out.pause_s = Math.max(1, out.pause_s);
  out.open_min = Math.max(1, out.open_min);
  for (const k of ["opens", "max", "cutoff"]) ext[k] = int(ext[k], 0, 24, DEFAULTS.extend[k]);
  out.enabled = out.enabled !== false;
  out.timer = out.timer !== false;
  out.ask_why = out.ask_why === true;
  out.mode = out.mode === "whole" ? "whole" : "part";
  out.schedule = (Array.isArray(out.schedule) ? out.schedule : []).filter(w => parseWindow(w));
  return out;
}

// "22:00-07:00" -> [startMin, endMin], or null.
function parseWindow(w) {
  const m = /^\s*(\d{1,2})(?::(\d{2}))?\s*-\s*(\d{1,2})(?::(\d{2}))?\s*$/.exec(String(w));
  if (!m) return null;
  const a = +m[1] * 60 + +(m[2] || 0), b = +m[3] * 60 + +(m[4] || 0);
  return +m[1] > 24 || +m[3] > 24 || +(m[2] || 0) > 59 || +(m[4] || 0) > 59 ? null : [a, b];
}

function inSchedule(c, d = new Date()) {
  const now = d.getHours() * 60 + d.getMinutes();
  return c.schedule.some(w => {
    const [a, b] = parseWindow(w);
    return a !== b && (a < b ? now >= a && now < b : now >= a || now < b);
  });
}

// Port of Config.stricterOrEqual: tightening applies at once, anything else waits.
function stricterOrEqual(n, o) {
  const cap = v => v <= 0 ? Infinity : v;
  if (!n.enabled && o.enabled) return false;
  if (n.pause_s < o.pause_s || n.pause_step_s < o.pause_step_s) return false;
  if (n.opens > o.opens || n.open_min > o.open_min) return false;
  if (cap(n.daily_cap_min) > cap(o.daily_cap_min) || n.cooldown_min < o.cooldown_min) return false;
  if (cap(n.max_videos) > cap(o.max_videos)) return false;
  if (n.extend.opens * n.extend.max > o.extend.opens * o.extend.max || n.extend.cutoff > o.extend.cutoff) return false;
  if (!n.ask_why && o.ask_why) return false;
  if (n.mode === "part" && o.mode === "whole") return false;
  return o.schedule.every(w => n.schedule.includes(w));
}

// Current settings for all categories; pending looser changes whose time has come are applied.
async function settings() {
  const s = await chrome.storage.local.get(["config", "pending"]);
  const config = s.config || {}, pending = s.pending || {};
  let dirty = false;
  for (const cat of Object.keys(pending)) {
    if (Date.now() < pending[cat].at) continue;
    config[cat] = pending[cat].cfg;
    delete pending[cat];
    dirty = true;
  }
  if (dirty) await chrome.storage.local.set({ config, pending });
  const out = {};
  for (const cat of CATS) out[cat] = withDefaults(config[cat]);
  return { config: out, pending };
}

async function saveSettings(targets) {
  const { config, pending } = await settings();
  const result = {};
  for (const cat of CATS) {
    if (!targets || typeof targets[cat] !== "object") continue;
    const n = withDefaults({ ...config[cat], ...targets[cat] });
    if (stricterOrEqual(n, config[cat])) {
      config[cat] = n;
      delete pending[cat];
      result[cat] = "saved";
    } else {
      pending[cat] = { cfg: n, at: Date.now() + STRICT_DELAY_MS };
      result[cat] = "waiting";
    }
  }
  await chrome.storage.local.set({ config, pending });
  return result;
}

async function cfg(cat) {
  return (await settings()).config[cat];
}

// ---- limits (chrome.storage.local, keys like 20260928_shorts) ----

function today() {
  const d = new Date();
  return `${d.getFullYear()}${String(d.getMonth() + 1).padStart(2, "0")}${String(d.getDate()).padStart(2, "0")}`;
}

async function state(cat) {
  const c = await cfg(cat);
  const k = today();
  const s = await chrome.storage.local.get([`${k}_${cat}`, `${k}_ext_${cat}`, `until_${cat}`, `last_${cat}`, "swaps"]);
  const used = s[`${k}_${cat}`] || 0;
  const ext = s[`${k}_ext_${cat}`] || 0;
  const total = c.opens + c.extend.opens * ext;
  const scheduled = inSchedule(c);
  const left = scheduled ? 0 : Math.max(0, total - used);
  const canExtend = !scheduled && left === 0 && c.extend.opens > 0 && ext < c.extend.max
      && new Date().getHours() < c.extend.cutoff;
  const waitMin = Math.max(0, Math.ceil(((s[`last_${cat}`] || 0) + c.cooldown_min * 60000 - Date.now()) / 60000));
  const ideas = String(s.swaps || "").split("\n").map(l => l.trim()).filter(Boolean);
  return { cat, label: LABELS[cat], enabled: c.enabled, used, ext, total, left, canExtend, scheduled, waitMin,
           unlocked: Date.now() < (s[`until_${cat}`] || 0), pauseSeconds: Math.min(600, c.pause_s + c.pause_step_s * used),
           openMin: c.open_min, extendOpens: c.extend.opens, maxExtends: c.extend.max, home: HOME[cat],
           idea: ideas.length ? ideas[Math.floor(Math.random() * ideas.length)] : "" };
}

async function open(cat) {
  const s = await state(cat);
  if (s.left === 0 || s.waitMin > 0) return s;
  await chrome.storage.local.set({ [`${today()}_${cat}`]: s.used + 1, [`until_${cat}`]: Date.now() + s.openMin * 60000,
                                   [`last_${cat}`]: Date.now() });
  const after = await state(cat);
  chrome.notifications.create(`fg-${cat}`, {
    type: "basic", iconUrl: "icon.png",
    title: `${after.label}: ${after.left} of ${after.total} opens left today`,
    message: await summary(),
  });
  setTimeout(() => chrome.notifications.clear(`fg-${cat}`), 10000);
  return after;
}

async function extend(cat) {
  const s = await state(cat);
  if (s.canExtend) await chrome.storage.local.set({ [`${today()}_ext_${cat}`]: s.ext + 1 });
  return state(cat);
}

async function summary() {
  const a = await state("shorts"), b = await state("reels"), c = await state("facebook");
  return `Shorts ${a.left}/${a.total} left  ·  Reels ${b.left}/${b.total} left  ·  Facebook ${c.left}/${c.total} left`;
}

// ---- adult keywords (keywords.json + keywords.local.json, same matcher as Keywords.java) ----

let words = null;
const normalize = s => s.toLowerCase().replace(/[​-‏⁠﻿]/g, "").replace(/\s+/g, " ").trim();
const compact = s => s.replace(/[^a-z0-9]/g, "");

async function loadWords() {
  if (words) return words;
  // keywords.local.json (optional, not committed) adds personal entries.
  const read = f => fetch(chrome.runtime.getURL(f)).then(r => r.json()).catch(() => ({}));
  const [json, local] = await Promise.all([read("keywords.json"), read("keywords.local.json")]);
  const list = k => [...(json[k] || []), ...(local[k] || [])].map(normalize).filter(Boolean);
  words = { strong: list("strong"), weak: list("weak"), safe: list("safe_exceptions") };
  return words;
}

async function adultMatch(raw) {
  const w = await loadWords();
  let text = normalize(raw);
  for (const s of w.safe) text = text.split(s).join(" ");
  const packed = compact(text);
  for (const k of w.strong) {
    if (text.includes(k)) return k;
    const ck = compact(k);
    if (ck.length >= 5 && ck === k.replace(/ /g, "") && packed.includes(ck)) return k;
  }
  let first = null;
  for (const k of w.weak) {
    if (!text.includes(k)) continue;
    if (first === null) first = k;
    else if (!first.includes(k) && !k.includes(first)) return `${first} + ${k}`;
  }
  return null;
}

// ---- gate every URL a tab lands on (tabs.onUpdated also sees YouTube's in-page navigation) ----

async function check(tabId, url) {
  if (!/^https?:/.test(url)) return;
  const lower = url.toLowerCase();
  let cat = null;
  if (SHORTS_URL.test(lower)) cat = "shorts";
  else if (REELS_URL.test(lower)) cat = "reels";
  else if (FACEBOOK_SITE.test(lower) && !FACEBOOK_OK.test(lower)) cat = "facebook";
  else {
    let target = lower;
    try { target = decodeURIComponent(lower); } catch (e) {}
    if (await adultMatch(target.replace(/[./_\-?=&+%]/g, " "))) {
      chrome.tabs.update(tabId, { url: chrome.runtime.getURL("pause.html?mode=adult") });
    }
    return;
  }
  const s = await state(cat);
  if (s.unlocked || !s.enabled) return;
  const q = new URLSearchParams({ cat, url });
  chrome.tabs.update(tabId, { url: chrome.runtime.getURL(`pause.html?${q}`) });
}

chrome.tabs.onUpdated.addListener((tabId, info) => {
  if (info.url) check(tabId, info.url);
});

// Tabs already open when the extension (re)loads never navigate, so gate them once now.
async function checkAll() {
  for (const t of await chrome.tabs.query({})) if (t.url) check(t.id, t.url);
}
chrome.runtime.onStartup.addListener(checkAll);
chrome.runtime.onInstalled.addListener(checkAll);

chrome.runtime.onMessage.addListener((msg, sender, reply) => {
  if (msg.type === "recheck" && sender.tab) return void check(sender.tab.id, sender.tab.url);
  // Settings changes only from the extension's own pages, never from content scripts.
  const own = sender.id === chrome.runtime.id && !sender.tab?.url?.startsWith("http");
  const run = { state: () => state(msg.cat), open: () => open(msg.cat), extend: () => extend(msg.cat),
                settings: () => settings(),
                save: own ? () => saveSettings(msg.targets) : null,
                swaps: own ? () => chrome.storage.local.set({ swaps: String(msg.text || "").trim() }) : null,
              }[msg.type];
  if (run) run().then(reply);
  return true;
});
