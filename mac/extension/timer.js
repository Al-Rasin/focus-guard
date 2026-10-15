// Time-left tab on the screen edge over Shorts/Reels while an open is running (same as Android's
// SessionTimer): pause-screen colours, red and pulsing in the last minute. Drag it to move it:
// it snaps to the nearer side and the spot is remembered (timer_pos in storage).
// When the window runs out on screen, ask the background to gate this tab again.
const PAGES = { shorts: /youtube\.com\/shorts/, reels: /facebook\.com\/(reel|reels|watch\/reels)\b|facebook\.com\/share\/r\// };
let tab = null;
let pos = { right: false, y: 0.45 }; // y = top of the tab as a fraction of the window height

chrome.storage.local.get("timer_pos").then(s => { if (s.timer_pos) pos = s.timer_pos; });

function hide() {
  if (tab) tab.remove();
  tab = null;
}

/** Stick to the left or right edge; the rounded corners face the page. */
function dock() {
  const t = tab.style;
  t.left = pos.right ? "auto" : "0";
  t.right = pos.right ? "0" : "auto";
  t.top = `${pos.y * 100}%`;
  t.borderRadius = pos.right ? "16px 0 0 16px" : "0 16px 16px 0";
  t.borderLeftWidth = pos.right ? "2px" : "0";
  t.borderRightWidth = pos.right ? "0" : "2px";
  t.padding = pos.right ? "8px 12px 8px 14px" : "8px 14px 8px 12px";
}

function drag(e) {
  e.preventDefault();
  const r = tab.getBoundingClientRect();
  const dx = e.clientX - r.left, dy = e.clientY - r.top;
  const move = m => {
    Object.assign(tab.style, { left: `${m.clientX - dx}px`, right: "auto",
      top: `${Math.min(Math.max(m.clientY - dy, 0), innerHeight - r.height)}px` });
  };
  const up = u => {
    removeEventListener("pointermove", move);
    removeEventListener("pointerup", up);
    pos = { right: u.clientX > innerWidth / 2, y: tab.getBoundingClientRect().top / innerHeight };
    chrome.storage.local.set({ timer_pos: pos });
    dock();
  };
  addEventListener("pointermove", move);
  addEventListener("pointerup", up);
}

function show(time, warn) {
  if (!tab) {
    tab = document.createElement("div");
    tab.innerHTML = "<style>@keyframes fg-pulse{to{opacity:.55}}</style><b></b><span></span>";
    tab.style.cssText = "position:fixed;z-index:2147483647;border:2px solid;cursor:grab;user-select:none;" +
      "display:flex;flex-direction:column;align-items:center;font-family:system-ui,sans-serif";
    tab.querySelector("b").style.cssText = "font-size:22px;line-height:1.2";
    tab.querySelector("span").style.cssText = "font-size:11px";
    tab.addEventListener("pointerdown", drag);
    document.documentElement.appendChild(tab);
    dock();
  }
  tab.querySelector("b").textContent = time;
  tab.querySelector("span").textContent = warn ? "last min" : "left";
  Object.assign(tab.style, warn
    ? { background: "#F87171", borderColor: "#F87171", color: "#0E1116", animation: "fg-pulse .6s ease-in-out infinite alternate" }
    : { background: "rgba(14,17,22,.95)", borderColor: "#7DD3FC", color: "#7DD3FC", animation: "none" });
  tab.querySelector("span").style.color = warn ? "#0E1116" : "#AEB6C2";
}

async function tick() {
  const cat = Object.keys(PAGES).find(c => PAGES[c].test(location.href.toLowerCase()));
  if (!cat) return hide();
  const key = `until_${cat}`;
  const left = ((await chrome.storage.local.get(key))[key] || 0) - Date.now();
  if (left <= 0) {
    if (tab) chrome.runtime.sendMessage({ type: "recheck" });
    return hide();
  }
  const s = Math.ceil(left / 1000);
  show(`${Math.floor(s / 60)}:${String(s % 60).padStart(2, "0")}`, left <= 60000);
}

setInterval(() => tick().catch(hide), 1000);
