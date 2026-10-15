// Focus Guard for Chrome/Brave: same rules as the Android app (see app/.../Limits.java).
importScripts("guard.js");
guard("llekdneeeimgoojkcjlfbajlkkcklnal"); // Focus Guard Keeper; each turns the other back on

const PAUSE_SECONDS = 5;
const DAILY_OPENS = 6;
const UNLOCK_MS = 15 * 60 * 1000;
const EXTEND_OPENS = 3;
const MAX_EXTENDS = 2;
const EXTEND_CUTOFF_HOUR = 19; // extensions only while at least 5 h of the day remain

const SHORTS_URL = /youtube\.com\/shorts/;
const REELS_URL = /facebook\.com\/(reel|reels|watch\/reels)\b|fb\.watch\/|facebook\.com\/share\/r\//;
const LABELS = { shorts: "YouTube Shorts", reels: "Facebook Reels" };
const HOME = { shorts: "https://www.youtube.com/", reels: "https://www.facebook.com/" };

// ---- limits (chrome.storage.local, keys like 20260928_shorts) ----

function today() {
  const d = new Date();
  return `${d.getFullYear()}${String(d.getMonth() + 1).padStart(2, "0")}${String(d.getDate()).padStart(2, "0")}`;
}

async function state(cat) {
  const k = today();
  const s = await chrome.storage.local.get([`${k}_${cat}`, `${k}_ext_${cat}`, `until_${cat}`]);
  const used = s[`${k}_${cat}`] || 0;
  const ext = s[`${k}_ext_${cat}`] || 0;
  const total = DAILY_OPENS + EXTEND_OPENS * ext;
  const left = Math.max(0, total - used);
  const canExtend = left === 0 && ext < MAX_EXTENDS && new Date().getHours() < EXTEND_CUTOFF_HOUR;
  return { cat, label: LABELS[cat], used, ext, total, left, canExtend,
           unlocked: Date.now() < (s[`until_${cat}`] || 0), pauseSeconds: PAUSE_SECONDS,
           extendOpens: EXTEND_OPENS, maxExtends: MAX_EXTENDS, home: HOME[cat] };
}

async function open(cat) {
  const s = await state(cat);
  if (s.left === 0) return s;
  await chrome.storage.local.set({ [`${today()}_${cat}`]: s.used + 1, [`until_${cat}`]: Date.now() + UNLOCK_MS });
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
  const a = await state("shorts"), b = await state("reels");
  return `Shorts ${a.left}/${a.total} left  ·  Reels ${b.left}/${b.total} left`;
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
  else {
    let target = lower;
    try { target = decodeURIComponent(lower); } catch (e) {}
    if (await adultMatch(target.replace(/[./_\-?=&+%]/g, " "))) {
      chrome.tabs.update(tabId, { url: chrome.runtime.getURL("pause.html?mode=adult") });
    }
    return;
  }
  const s = await state(cat);
  if (s.unlocked) return;
  const q = new URLSearchParams({ cat, url });
  chrome.tabs.update(tabId, { url: chrome.runtime.getURL(`pause.html?${q}`) });
}

chrome.tabs.onUpdated.addListener((tabId, info) => {
  if (info.url) check(tabId, info.url);
});

chrome.runtime.onMessage.addListener((msg, sender, reply) => {
  const run = { state: () => state(msg.cat), open: () => open(msg.cat), extend: () => extend(msg.cat) }[msg.type];
  if (run) run().then(reply);
  return true;
});
