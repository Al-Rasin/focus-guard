// Time-left pill over Shorts/Reels while an open is running (same as Android's SessionTimer).
// When the window runs out on screen, ask the background to gate this tab again.
const PAGES = { shorts: /youtube\.com\/shorts/, reels: /facebook\.com\/(reel|reels|watch\/reels)\b|facebook\.com\/share\/r\// };
let pill = null;

function hide() {
  if (pill) pill.remove();
  pill = null;
}

function show(text, red) {
  if (!pill) {
    pill = document.createElement("div");
    pill.style.cssText = "position:fixed;top:12px;left:50%;transform:translateX(-50%);z-index:2147483647;" +
      "padding:4px 12px;border-radius:14px;background:rgba(0,0,0,.7);font:13px system-ui,sans-serif;" +
      "pointer-events:none";
    document.documentElement.appendChild(pill);
  }
  pill.textContent = text;
  pill.style.color = red ? "#ff6b6b" : "#fff";
}

async function tick() {
  const cat = Object.keys(PAGES).find(c => PAGES[c].test(location.href.toLowerCase()));
  if (!cat) return hide();
  const key = `until_${cat}`;
  const left = ((await chrome.storage.local.get(key))[key] || 0) - Date.now();
  if (left <= 0) {
    if (pill) chrome.runtime.sendMessage({ type: "recheck" });
    return hide();
  }
  const s = Math.ceil(left / 1000);
  show(`⏱ ${Math.floor(s / 60)}:${String(s % 60).padStart(2, "0")} left`, s <= 60);
}

setInterval(() => tick().catch(hide), 1000);
