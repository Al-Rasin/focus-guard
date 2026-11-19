// Settings page: per-category limits, "do this instead" ideas, backup file shared with the Android app.
const LABELS = { shorts: "YouTube Shorts", reels: "Facebook Reels", facebook: "Facebook" };
const $ = id => document.getElementById(id);
const send = msg => chrome.runtime.sendMessage(msg);

// [key, label, min, max]; "extend.x" = inside the extend block.
const FIELDS = [
  ["pause_s", "Pause (seconds)", 1, 600],
  ["pause_step_s", "Pause grows per open (seconds)", 0, 120],
  ["opens", "Opens per day", 0, 100],
  ["open_min", "Minutes per open", 1, 240],
  ["cooldown_min", "Minutes between opens (0 = off)", 0, 1440],
  ["extend.opens", "Extend adds opens (0 = no extend)", 0, 24],
  ["extend.max", "Extends per day", 0, 24],
  ["extend.cutoff", "No extending from hour", 0, 24],
];

let current = {};

const get = (c, k) => k.startsWith("extend.") ? c.extend[k.slice(7)] : c[k];

function el(tag, cls, text) {
  const e = document.createElement(tag);
  if (cls) e.className = cls;
  if (text != null) e.textContent = text;
  return e;
}

function row(label, input, cls) {
  const r = el("div", "row" + (cls ? " " + cls : ""));
  r.append(el("span", null, label), input);
  return r;
}

function render(config, pending) {
  current = config;
  const box = $("cats");
  box.textContent = "";
  for (const cat of Object.keys(LABELS)) {
    const c = config[cat];
    box.append(el("h2", null, LABELS[cat]));
    const card = el("div", "group card");
    card.dataset.cat = cat;
    const on = Object.assign(el("input", "switch"), { type: "checkbox", checked: c.enabled });
    on.dataset.k = "enabled";
    card.append(row("On", on, "head"));
    for (const [k, label, min, max] of FIELDS) {
      const inp = Object.assign(el("input"), { type: "number", min, max, value: get(c, k) });
      inp.dataset.k = k;
      card.append(row(label, inp));
    }
    const ti = Object.assign(el("input", "switch"), { type: "checkbox", checked: c.timer });
    ti.dataset.k = "timer";
    card.append(row("Time-left tab", ti));
    const si = Object.assign(el("input"), { type: "text", value: c.schedule.join(", "), placeholder: "22:00-07:00" });
    si.dataset.k = "schedule";
    card.append(row("Blocked times (comma separated)", si, "wrap"));
    box.append(card);
    if (pending[cat]) box.append(el("div", "note pending",
      `Looser change waiting until ${new Date(pending[cat].at).toLocaleString()}`));
  }
}

// Pause screen look, same choices as the Android app (storage "ui": guide, pace, guide_q).
async function renderUi() {
  const ui = Object.assign({ guide: 0, pace: 0, guide_q: true }, (await chrome.storage.local.get("ui")).ui);
  const save = () => chrome.storage.local.set({ ui });
  const seg = (id, names, key) => {
    const box = $(id);
    box.textContent = "";
    names.forEach((n, i) => {
      const b = el("button", i === ui[key] ? "on" : "", n);
      b.onclick = () => { ui[key] = i; save(); renderUi(); };
      box.append(b);
    });
  };
  seg("guide", Guide.STYLES, "guide");
  seg("pace", Guide.PACES, "pace");
  $("paceInfo").textContent = Guide.PACE_INFO[ui.pace];
  $("guide_q").checked = ui.guide_q;
  $("guide_q").onchange = () => { ui.guide_q = $("guide_q").checked; save(); };
}

function read() {
  const out = {};
  for (const card of document.querySelectorAll("#cats .card")) {
    const c = JSON.parse(JSON.stringify(current[card.dataset.cat]));
    for (const inp of card.querySelectorAll("input")) {
      const k = inp.dataset.k;
      if (inp.type === "checkbox") c[k] = inp.checked;
      else if (k === "schedule") c.schedule = inp.value.split(",").map(w => w.trim()).filter(Boolean);
      else if (k.startsWith("extend.")) c.extend[k.slice(7)] = +inp.value;
      else c[k] = +inp.value;
    }
    out[card.dataset.cat] = c;
  }
  return out;
}

function report(result) {
  const waiting = Object.keys(result).filter(c => result[c] === "waiting").map(c => LABELS[c]);
  return waiting.length ? `Saved. Looser changes wait 24 h: ${waiting.join(", ")}` : "Saved";
}

async function load() {
  const { config, pending } = await send({ type: "settings" });
  render(config, pending);
  $("swaps").value = (await chrome.storage.local.get("swaps")).swaps || "";
}

$("save").onclick = async () => {
  const result = await send({ type: "save", targets: read() });
  await send({ type: "swaps", text: $("swaps").value });
  await load();
  $("msg").textContent = report(result);
};

$("export").onclick = async () => {
  const { config } = await send({ type: "settings" });
  const swaps = (await chrome.storage.local.get("swaps")).swaps || "";
  const data = { focusguard: 1, targets: config, apps: [], swaps };
  const a = document.createElement("a");
  a.href = URL.createObjectURL(new Blob([JSON.stringify(data, null, 2)], { type: "application/json" }));
  a.download = "focus-guard-settings.json";
  a.click();
  setTimeout(() => URL.revokeObjectURL(a.href), 5000);
  $("msg").textContent = "Settings saved to the file";
};

$("import").onclick = () => $("file").click();
$("file").onchange = async () => {
  const f = $("file").files[0];
  $("file").value = "";
  if (!f) return;
  let j;
  try {
    if (f.size > 1000000) throw 0;
    j = JSON.parse(await f.text());
  } catch (e) {
    return void ($("msg").textContent = "Could not read the file");
  }
  if (!j || j.focusguard !== 1 || typeof j.targets !== "object" || !j.targets)
    return void ($("msg").textContent = "Not a Focus Guard settings file");
  const targets = {};
  for (const cat of Object.keys(LABELS)) if (j.targets[cat] && typeof j.targets[cat] === "object") targets[cat] = j.targets[cat];
  const result = await send({ type: "save", targets });
  if (typeof j.swaps === "string" && j.swaps.trim()) await send({ type: "swaps", text: j.swaps });
  await load();
  $("msg").textContent = `Loaded ${Object.keys(targets).length} targets. ` + report(result);
};

load();
renderUi();
