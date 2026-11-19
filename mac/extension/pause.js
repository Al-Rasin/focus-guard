// Pause page, same flow as the Android PauseActivity: question, guide orb counting down the wait,
// then the choices (why, open length, extend) under a white "I don't want to" button.
// Closed state (schedule, cooldown, opens used): lock over a resting orb and a countdown to reopen.
const params = new URLSearchParams(location.search);
const cat = params.get("cat");
const target = params.get("url");
const send = msg => chrome.runtime.sendMessage(msg);
const main = document.getElementById("main");
const wash = document.getElementById("wash");

const QUESTIONS = [
  "Take a breath. Do you really need this now?",
  "What were you about to do before this?",
  "How do you want to feel in 15 minutes?",
  "Is this a choice, or a habit?",
  "Are you bored, tired or avoiding something?",
  "Will you remember this video tomorrow?",
  "What would make the next hour good?",
];
const QUICK_WHY = ["Bored", "Habit", "A message", "Looking for something", "Taking a break"];
const ICON_PAUSE = `<svg viewBox="0 0 24 24" fill="#64D2FF"><rect x="6" y="5" width="4" height="14" rx="1.5"/>
  <rect x="14" y="5" width="4" height="14" rx="1.5"/></svg>`;
const ICON_LOCK = `<svg viewBox="0 0 24 24" fill="none" stroke="#fff" stroke-width="2" stroke-linecap="round">
  <rect x="5" y="11" width="14" height="10" rx="2.5" fill="rgba(255,255,255,.15)"/><path d="M8 11V8a4 4 0 0 1 8 0v3"/></svg>`;

let guide = null, washRaf = 0, timers = [];

function el(tag, cls, text) {
  const e = document.createElement(tag);
  if (cls) e.className = cls;
  if (text != null) e.textContent = text;
  return e;
}

function button(cls, text, onclick) {
  const b = el("button", cls, text);
  b.onclick = onclick;
  return b;
}

function leave(url) {
  location.replace(url || "about:blank");
}

function clear() {
  guide?.stop();
  guide = null;
  cancelAnimationFrame(washRaf);
  timers.forEach(clearInterval);
  timers = [];
  wash.style.opacity = 0;
  main.textContent = "";
}

function orb(seq, ring) {
  const box = el("div");
  box.id = "orbBox";
  const c = el("canvas");
  c.id = "orb";
  const px = Math.round(Math.min(260, window.innerHeight * 0.3, window.innerWidth - 80));
  c.style.width = c.style.height = px + "px";
  box.append(c);
  main.append(box);
  return { box, canvas: c, seq, ring };
}

function startGuide(o, onPhase) {
  guide = Guide.create(o.canvas, o.seq, { ring: o.ring, onPhase });
  guide.start();
  const tick = () => {
    wash.style.opacity = (0.03 + 0.09 * guide.breath()).toFixed(3);
    washRaf = requestAnimationFrame(tick);
  };
  washRaf = requestAnimationFrame(tick);
}

function pill(s, text) {
  const p = el("div", "pill");
  p.innerHTML = ICON_PAUSE;
  p.append(el("b", null, s.label), document.createTextNode(text));
  main.append(p);
}

function swapCard(idea) {
  if (!idea) return null;
  const box = el("div", "swap");
  box.append(el("div", "overline", "Instead, you could"), el("div", null, idea));
  return box;
}

function fmtLeft(ms) {
  const t = Math.max(0, Math.ceil(ms / 1000));
  const h = Math.floor(t / 3600), m = Math.floor((t % 3600) / 60), sec = t % 60;
  return h ? `${h}:${String(m).padStart(2, "0")}:${String(sec).padStart(2, "0")}`
           : `${m}:${String(sec).padStart(2, "0")}`;
}

async function uiPrefs() {
  const r = await chrome.storage.local.get(["ui", "question"]);
  const ui = Object.assign({ guide: 0, pace: 0, guide_q: true }, r.ui);
  const qi = (r.question || 0) % QUESTIONS.length;
  chrome.storage.local.set({ question: qi + 1 });
  return { ui, question: QUESTIONS[qi] };
}

function adult() {
  clear();
  main.append(el("div", "grow"));
  const o = orb(Guide.rest(), false);
  const lock = el("div");
  lock.id = "lock";
  lock.innerHTML = ICON_LOCK;
  o.box.append(lock);
  startGuide(o);
  main.append(el("div", "closedTitle", "Blocked"), el("div", "reason", "Adult content is blocked on this device."),
              el("div", "grow"));
  const bottom = el("div", "bottom");
  bottom.append(button("btn primary", "Go back", () => leave("https://www.google.com/")));
  main.append(bottom);
}

function closed(s) {
  clear();
  const reason = s.scheduled ? "Blocked by your schedule right now."
    : s.left > 0 && s.waitMin > 0 ? "A short break after your last open."
    : `You've used all ${s.total} opens today.`;
  pill(s, s.left > 0 ? ` · ${s.left} of ${s.total} left` : " · closed");
  main.append(el("div", "grow"));
  const o = orb(Guide.rest(), false);
  const lock = el("div");
  lock.id = "lock";
  lock.innerHTML = ICON_LOCK;
  o.box.append(lock);
  startGuide(o);
  main.append(el("div", "closedTitle", `${s.label} is closed`), el("div", "reason", reason));
  if (s.reopenAt > Date.now()) {
    const count = el("div", "count", fmtLeft(s.reopenAt - Date.now()));
    main.append(el("div", "overline", "Opens again in"), count);
    main.lastChild.previousSibling.style.marginTop = "18px";
    timers.push(setInterval(() => {
      const ms = s.reopenAt - Date.now();
      count.textContent = fmtLeft(ms);
      if (ms <= 0) render();
    }, 1000));
  }
  const tiles = el("div", "tiles");
  const tile = (n, label) => {
    const t = el("div", "tile");
    t.append(el("b", null, n), el("span", null, label));
    return t;
  };
  tiles.append(tile(String(s.weekSkips), "Went back this week"),
               tile(`${s.used} · ${s.minToday} min`, "Opens · time today"));
  main.append(tiles);
  const swap = swapCard(s.idea);
  if (swap) {
    swap.style.width = "100%";
    swap.style.boxSizing = "border-box";
    swap.style.marginTop = "10px";
    swap.style.textAlign = "left";
    main.append(swap);
  }
  main.append(el("div", "grow"));
  const bottom = el("div", "bottom");
  bottom.append(button("btn primary", "Go back", async () => {
    await send({ type: "skip", cat });
    leave(s.home);
  }));
  main.append(bottom);
}

function minuteChoices(openMin) {
  const below = [1, 5, 10, 15, 30, 60].filter(m => m < openMin).slice(-3);
  return [...below, openMin];
}

async function gate(s) {
  clear();
  const { ui, question } = await uiPrefs();
  const extending = s.left === 0;
  pill(s, extending ? " · limit reached" : ` · ${s.left} of ${s.total} left`);
  if (ui.guide_q) main.append(Object.assign(el("div", null, question), { id: "question" }));
  const o = orb(Guide.sequence(ui.guide, ui.pace), true);
  const word = Object.assign(el("div", null, ""), { id: "word" });
  const sub = Object.assign(el("div", null, ""), { id: "sub" });
  const hint = Object.assign(el("div", null, ""), { id: "hint" });
  main.append(word, sub, hint);
  startGuide(o, p => {
    word.style.opacity = sub.style.opacity = 0;
    setTimeout(() => {
      word.textContent = p.word;
      sub.textContent = p.sub;
      word.style.opacity = sub.style.opacity = 1;
    }, 300);
  });

  const panel = Object.assign(el("div"), { id: "panel" });
  if (s.weekSkips > 0) panel.append(el("div", "badge", `✓ You went back ${s.weekSkips}× this week`));
  const swap = swapCard(s.idea);
  if (swap) panel.append(swap);

  const choices = el("div");
  choices.style.cssText = "display:flex;flex-direction:column;gap:10px";
  if (extending) {
    choices.append(el("div", "overline", `Daily limit reached · ${s.maxExtends - s.ext} extensions left`));
    const ext = button("btn tinted", `Extend +${s.extendOpens} opens`, async () => {
      await send({ type: "extend", cat });
      render(); // new opens still go through a fresh pause
    });
    choices.append(ext);
  } else {
    const why = Object.assign(el("input"), { id: "why", placeholder: "Why are you opening it?", maxLength: 200 });
    const chips = el("div", "chips");
    QUICK_WHY.forEach(w => chips.append(button("chip", w, () => { why.value = w; sync(); })));
    choices.append(el("div", "overline", s.askWhy ? "Write why first" : "Why? (optional)"), why, chips);
    const mins = el("div", "chips");
    let busy = false;
    for (const m of minuteChoices(s.openMin)) {
      mins.append(button("chip big", `${m} min`, async () => {
        if (busy) return;
        busy = true;
        const after = await send({ type: "open", cat, min: m, why: why.value });
        if (after.unlocked) return leave(target);
        busy = false;
        render();
      }));
    }
    const label = el("div", "overline", "Or open it for");
    label.style.marginTop = "6px";
    choices.append(label, mins);
    const sync = () => mins.classList.toggle("gated", s.askWhy && why.value.trim().length < 3);
    why.oninput = sync;
    sync();
  }
  panel.append(choices);
  main.append(panel);
  main.append(el("div", "grow"));
  const bottom = el("div", "bottom");
  bottom.append(button("btn primary", `I don't want to open ${s.label}`, async () => {
    await send({ type: "skip", cat });
    leave(s.home);
  }));
  main.append(bottom);

  // Wait: ring fills, seconds in the orb, choices hidden until it runs out.
  const ms = s.pauseSeconds * 1000, t0 = performance.now();
  choices.classList.add("gated");
  const step = () => {
    const done = Math.min(1, (performance.now() - t0) / ms);
    const left = Math.ceil((ms - (performance.now() - t0)) / 1000);
    if (done < 1) {
      guide?.wait(done, String(left));
      hint.textContent = `Choices in ${left} s`;
      return;
    }
    clearInterval(timer);
    guide?.wait(1, "");
    hint.textContent = "";
    choices.classList.remove("gated");
    panel.classList.add("on");
  };
  const timer = setInterval(step, 100);
  timers.push(timer);
  step();
}

async function render() {
  if (params.get("mode") === "adult") return adult();
  const s = await send({ type: "state", cat });
  if (s.unlocked || !s.enabled) return leave(target);
  const closedNow = s.scheduled || (s.left > 0 && s.waitMin > 0) || (s.left === 0 && !s.canExtend);
  closedNow ? closed(s) : gate(s);
}

// Hidden tab: stop drawing (the wait keeps counting on the clock).
document.addEventListener("visibilitychange", () => {
  if (!guide) return;
  document.hidden ? guide.stop() : guide.start();
});

render();
