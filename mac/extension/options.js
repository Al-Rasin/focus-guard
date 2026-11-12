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

function render(config, pending) {
  current = config;
  const box = $("cats");
  box.textContent = "";
  for (const cat of Object.keys(LABELS)) {
    const c = config[cat];
    const card = document.createElement("div");
    card.className = "card";
    card.dataset.cat = cat;
    const h = document.createElement("h3");
    h.textContent = LABELS[cat];
    const on = document.createElement("input");
    on.type = "checkbox";
    on.checked = c.enabled;
    on.dataset.k = "enabled";
    h.appendChild(on);
    card.appendChild(h);
    for (const [k, label, min, max] of FIELDS) {
      const row = document.createElement("label");
      row.textContent = label;
      const inp = document.createElement("input");
      Object.assign(inp, { type: "number", min, max, value: get(c, k) });
      inp.dataset.k = k;
      row.appendChild(inp);
      card.appendChild(row);
    }
    const t = document.createElement("label");
    t.textContent = "Time-left tab";
    const ti = document.createElement("input");
    Object.assign(ti, { type: "checkbox", checked: c.timer });
    ti.dataset.k = "timer";
    t.appendChild(ti);
    card.appendChild(t);
    const s = document.createElement("label");
    s.textContent = "Blocked times (e.g. 22:00-07:00, comma separated)";
    const si = document.createElement("input");
    Object.assign(si, { type: "text", value: c.schedule.join(", ") });
    si.dataset.k = "schedule";
    s.style.flexWrap = "wrap";
    s.appendChild(si);
    card.appendChild(s);
    if (pending[cat]) {
      const p = document.createElement("div");
      p.className = "pending";
      p.textContent = `Looser change waiting until ${new Date(pending[cat].at).toLocaleString()}`;
      card.appendChild(p);
    }
    box.appendChild(card);
  }
}

function read() {
  const out = {};
  for (const card of document.querySelectorAll(".card")) {
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
