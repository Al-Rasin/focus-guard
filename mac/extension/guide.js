// Pause-screen guide, a port of the Android app's Guide.java: an orb whose motion matches the word
// under it (Breathe in grows, Hold glows, Breathe out shrinks, Look outside ripples, Blink closes,
// Drop your shoulders sinks). The ring around it fills as the wait runs out.
const Guide = (() => {
  const IN = 0, HOLD = 1, OUT = 2, LOOK = 3, BLINK = 4, RELAX = 5;
  const P = (kind, s, word, sub) => ({ kind, ms: s * 1000, word, sub });

  // style 0 breathe, 1 look away, 2 mix; pace 0 calm 4-4, 1 box 4-4-4-4, 2 deep 4-7-8.
  function sequence(style, pace) {
    const breath = [P(IN, 4, "Breathe in", "slowly, through your nose")];
    if (pace === 1) breath.push(P(HOLD, 4, "Hold", "stay still"));
    if (pace === 2) breath.push(P(HOLD, 7, "Hold", "stay still"));
    breath.push(P(OUT, pace === 2 ? 8 : 4, "Breathe out", "let it all go"));
    if (pace === 1) breath.push(P(HOLD, 4, "Hold", "empty and calm"));
    const look = P(LOOK, 6, "Look outside", "find something far away"),
          blink = P(BLINK, 3, "Blink slowly", "rest your eyes"),
          relax = P(RELAX, 4, "Drop your shoulders", "unclench your jaw");
    if (style === 1) return [look, blink];
    if (style === 2) return [...breath, look, ...breath, relax];
    return breath;
  }

  const rest = () => [P(IN, 5, "", ""), P(OUT, 6, "", "")];
  const ease = t => 0.5 - 0.5 * Math.cos(Math.PI * Math.max(0, Math.min(1, t)));
  const rgba = (hex, a) => {
    const n = parseInt(hex.slice(1), 16);
    return `rgba(${n >> 16},${(n >> 8) & 255},${n & 255},${a})`;
  };

  function create(canvas, seq, { ring = true, onPhase } = {}) {
    const ctx = canvas.getContext("2d");
    const BLOBS = ["#0A84FF", "#5E5CE6", "#40C8E0"];
    let index = -1, phaseStart = 0, b = 0, bFrom = 0, t = 0, eye = 1, dim = 1, drop = 0, shimmer = 0;
    let progress = 0, count = "", raf = 0, size = 0, dpr = 1;

    function fit() {
      dpr = window.devicePixelRatio || 1;
      size = canvas.clientWidth;
      canvas.width = canvas.height = Math.round(size * dpr);
    }

    function tick(now) {
      if (index < 0 || now - phaseStart >= seq[index].ms) {
        phaseStart = index < 0 ? now : phaseStart + seq[index].ms;
        if (now - phaseStart > 2000) phaseStart = now; // tab was hidden: don't race through phases
        index = (index + 1) % seq.length;
        bFrom = b;
        onPhase?.(seq[index], index);
      }
      const p = seq[index];
      t = (now - phaseStart) / p.ms;
      const e = ease(t), settle = ease(Math.min(1, t * 3));
      if (p.kind === IN) b = bFrom + (1 - bFrom) * e;
      else if (p.kind === OUT) b = bFrom * (1 - e);
      else if (p.kind !== HOLD) b = bFrom + (0.35 - bFrom) * settle;
      shimmer = p.kind === HOLD ? 0.5 - 0.5 * Math.cos(2 * Math.PI * t * Math.max(1, p.ms / 2000)) : 0;
      dim += ((p.kind === LOOK ? 0.45 : 1) - dim) * 0.06;
      if (p.kind === BLINK) {
        const k = (t * 2) % 1;
        eye = k < 0.5 ? 1 - 0.92 * ease(k * 2) : 0.08 + 0.92 * ease((k - 0.5) * 2);
      } else eye += (1 - eye) * 0.2;
      drop = p.kind === RELAX ? (t < 0.6 ? ease(t / 0.6) : 1 - ease((t - 0.6) / 0.4)) : drop * 0.85;
      draw(now / 1000, p);
    }

    function radial(x, y, r, stops) {
      const g = ctx.createRadialGradient(x, y, 0, x, y, r);
      for (const [o, c] of stops) g.addColorStop(o, c);
      return g;
    }

    function circle(x, y, r) {
      ctx.beginPath();
      ctx.arc(x, y, Math.max(0, r), 0, Math.PI * 2);
    }

    function draw(sec, p) {
      if (canvas.clientWidth !== size) fit();
      ctx.setTransform(dpr, 0, 0, dpr, 0, 0);
      ctx.clearRect(0, 0, size, size);
      const c = size / 2, R = size / 2 - 6;
      const r = R * (ring ? 0.74 : 0.86) * (0.55 + 0.45 * b);
      const oy = c + drop * R * 0.12;

      if (ring) {
        ctx.lineWidth = 1.5;
        ctx.strokeStyle = "rgba(255,255,255,.12)";
        circle(c, c, R);
        ctx.stroke();
        if (progress > 0.001) {
          const g = ctx.createConicGradient(-Math.PI / 2, c, c);
          [[0, "#64D2FF"], [0.3, "#0A84FF"], [0.6, "#5E5CE6"], [0.85, "#BF5AF2"], [1, "#64D2FF"]]
            .forEach(([o, col]) => g.addColorStop(o, col));
          ctx.strokeStyle = g;
          ctx.lineWidth = 3;
          ctx.lineCap = "round";
          const end = -Math.PI / 2 + Math.max(0.01, Math.PI * 2 * progress);
          ctx.beginPath();
          ctx.arc(c, c, R, -Math.PI / 2, end);
          ctx.stroke();
          const hx = c + R * Math.cos(end), hy = c + R * Math.sin(end);
          ctx.fillStyle = radial(hx, hy, 12, [[0, "rgba(100,210,255,.8)"], [1, "rgba(100,210,255,0)"]]);
          circle(hx, hy, 12);
          ctx.fill();
          ctx.fillStyle = "#fff";
          circle(hx, hy, 2.6);
          ctx.fill();
        }
      }

      if (p.kind === LOOK) {
        ctx.lineWidth = 1.2;
        for (let i = 0; i < 3; i++) {
          const k = (t * 2 + i / 3) % 1;
          ctx.strokeStyle = rgba("#64D2FF", (150 / 255) * (1 - k) * Math.min(1, t * 4));
          circle(c, oy, r + (R * 0.98 - r) * k);
          ctx.stroke();
        }
      }

      const halo = r * (1.45 + 0.1 * shimmer);
      ctx.globalAlpha = dim * (0.7 + 0.3 * shimmer);
      ctx.fillStyle = radial(c, oy, halo, [[0.45, "rgba(47,123,255,.55)"], [0.7, "rgba(47,123,255,.2)"],
                                            [1, "rgba(47,123,255,0)"]]);
      circle(c, oy, halo);
      ctx.fill();
      ctx.globalAlpha = 1;

      ctx.save();
      ctx.translate(0, oy);
      ctx.scale(1, eye);
      ctx.translate(0, -oy);
      circle(c, oy, r);
      ctx.clip();
      ctx.globalAlpha = Math.max(0.55, dim);
      ctx.fillStyle = "#0B1440";
      circle(c, oy, r);
      ctx.fill();
      ctx.globalAlpha = (235 / 255) * dim;
      BLOBS.forEach((col, i) => {
        const ang = sec * (0.35 + 0.12 * i) * (i === 1 ? -1 : 1) + i * 2.1;
        const off = r * (0.32 + 0.08 * Math.sin(sec * 0.7 + i));
        const bx = c + off * Math.cos(ang), by = oy + off * Math.sin(ang);
        const br = r * (0.95 + 0.1 * Math.sin(sec * 0.9 + i * 1.7));
        ctx.fillStyle = radial(bx, by, br, [[0, col], [0.45, rgba(col, 0.6)], [1, rgba(col, 0)]]);
        circle(bx, by, br);
        ctx.fill();
      });
      const gx = c - r * 0.32, gy = oy - r * 0.42;
      ctx.globalAlpha = dim;
      ctx.fillStyle = radial(gx, gy, r * 0.75, [[0, "rgba(255,255,255,.55)"], [0.4, "rgba(255,255,255,.15)"],
                                                [1, "rgba(255,255,255,0)"]]);
      circle(gx, gy, r * 0.75);
      ctx.fill();
      ctx.restore();

      ctx.save();
      ctx.translate(0, oy);
      ctx.scale(1, eye);
      ctx.translate(0, -oy);
      ctx.globalAlpha = (70 / 255) * dim;
      ctx.strokeStyle = "#fff";
      ctx.lineWidth = 1;
      circle(c, oy, r - 0.5);
      ctx.stroke();
      ctx.restore();
      ctx.globalAlpha = 1;

      if (count) {
        ctx.font = `200 ${size * 0.2}px -apple-system, BlinkMacSystemFont, sans-serif`;
        ctx.fillStyle = "#fff";
        ctx.textAlign = "center";
        ctx.textBaseline = "middle";
        ctx.shadowColor = "rgba(0,0,0,.4)";
        ctx.shadowBlur = 8;
        ctx.fillText(count, c, oy);
        ctx.shadowBlur = 0;
      }
    }

    function loop(now) {
      tick(now);
      raf = requestAnimationFrame(loop);
    }

    fit();
    return {
      start() { if (!raf) raf = requestAnimationFrame(loop); },
      stop() { cancelAnimationFrame(raf); raf = 0; },
      wait(f, text) { progress = f; count = text; },
      breath: () => b,
    };
  }

  return { create, sequence, rest, STYLES: ["Breathe", "Look away", "Mix"], PACES: ["Calm", "Box", "Deep"],
           PACE_INFO: ["In 4 s, out 4 s", "In 4, hold 4, out 4, hold 4", "In 4, hold 7, out 8"] };
})();
