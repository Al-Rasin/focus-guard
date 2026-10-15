// Pause page: wait, then open (spends one), extend (+3 before 7 PM, twice a day) or go back.
const params = new URLSearchParams(location.search);
const cat = params.get("cat");
const target = params.get("url");
const $ = id => document.getElementById(id);
const send = msg => chrome.runtime.sendMessage(msg);

function leave(home) {
  location.replace(home || "about:blank");
}

async function render() {
  if (params.get("mode") === "adult") {
    $("title").textContent = "Blocked";
    $("breathe").textContent = "Adult content is blocked.";
    $("count").hidden = $("go").hidden = true;
    $("back").onclick = () => leave("https://www.google.com/");
    return;
  }

  const s = await send({ type: "state", cat });
  if (s.unlocked) return leave(target);

  $("title").textContent = s.label;
  $("back").onclick = () => leave(s.home);
  const go = $("go");
  go.disabled = true;

  if (s.left > 0) {
    $("info").textContent = `${s.left} of ${s.total} opens left today`;
    go.textContent = "Open (15 min)";
    go.onclick = async () => {
      await send({ type: "open", cat });
      leave(target);
    };
  } else if (s.canExtend) {
    $("info").textContent = `Daily limit reached. Extensions left today: ${s.maxExtends - s.ext}`;
    go.textContent = `Extend +${s.extendOpens} opens`;
    go.onclick = async () => {
      await send({ type: "extend", cat });
      render(); // new opens still go through a fresh pause
    };
  } else {
    $("breathe").textContent = "Daily limit reached. Come back tomorrow.";
    $("count").hidden = go.hidden = true;
    return;
  }

  let n = s.pauseSeconds;
  $("count").textContent = n;
  const timer = setInterval(() => {
    if (--n > 0) return void ($("count").textContent = n);
    clearInterval(timer);
    $("count").textContent = "✓";
    go.disabled = false;
  }, 1000);
}

render();
