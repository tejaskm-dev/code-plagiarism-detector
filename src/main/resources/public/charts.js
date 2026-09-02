/*
 * Charts, drawn with Chart.js rather than by hand.
 *
 * The earlier versions of these were hand-authored SVG string builders. They worked,
 * but every axis, tooltip and hit-target was bespoke code that had to be maintained
 * and none of them were interactive. Chart.js is vendored locally under vendor/ --
 * no CDN, no network at runtime, identical rendering offline, which is the same
 * constraint the fonts are held to.
 *
 * Charts cannot be built from an HTML string the way the rest of the UI is, because
 * they need a live <canvas> in the document. The pattern here is: markup functions
 * emit a placeholder and queue a builder; whoever wrote the HTML calls flushCharts()
 * once the nodes are actually in the DOM.
 */

const chartQueue = [];
const liveCharts = new Map();

/** Reads a CSS custom property so charts follow the theme rather than hard-coding it. */
function token(name, fallback) {
  const v = getComputedStyle(document.documentElement).getPropertyValue(name).trim();
  return v || fallback;
}

/** Emits a canvas and queues its configuration for the next flushCharts(). */
function chartCanvas(id, height, build) {
  chartQueue.push({ id, build });
  return `<div class="chart-box" style="height:${height}px"><canvas id="${id}"></canvas></div>`;
}

/** Builds every queued chart. Safe to call when the queue is empty. */
function flushCharts() {
  while (chartQueue.length) {
    const { id, build } = chartQueue.shift();
    const el = document.getElementById(id);
    if (!el) continue;
    const existing = liveCharts.get(id);
    if (existing) existing.destroy();
    try {
      liveCharts.set(id, new Chart(el.getContext("2d"), build()));
    } catch (e) {
      el.closest(".chart-box").innerHTML =
        `<p class="t-body muted">This chart could not be drawn.</p>`;
    }
  }
}

/** Shared defaults so every chart in the app reads as one family. */
function baseOptions() {
  return {
    responsive: true,
    maintainAspectRatio: false,
    animation: { duration: 520, easing: "easeOutQuart" },
    plugins: {
      legend: { display: false },
      tooltip: {
        backgroundColor: token("--surface-inverse", "#1c1b1f"),
        titleColor: token("--on-surface-inverse", "#f5f5f5"),
        bodyColor: token("--on-surface-inverse", "#f5f5f5"),
        padding: 10,
        cornerRadius: 8,
        displayColors: false,
        titleFont: { family: "Manrope, sans-serif", size: 12, weight: "600" },
        bodyFont: { family: "Manrope, sans-serif", size: 12 },
      },
    },
  };
}

function gridColor() { return token("--outline-variant", "#e0e0e0"); }
function tickFont() { return { family: "Manrope, sans-serif", size: 11 }; }

/**
 * Distribution of every pair score, with the cohort median and the flag threshold
 * drawn as vertical rules.
 *
 * A bare histogram would show shape but not judgement. The two rules are what let a
 * reader see *why* a given score was or was not flagged.
 */
function histogramChart(pairs, c) {
  const BINS = 25;
  const counts = new Array(BINS).fill(0);
  pairs.forEach((p) => {
    counts[Math.min(BINS - 1, Math.max(0, Math.floor(p.score * BINS)))]++;
  });

  const id = "chart-histogram";
  const markup = chartCanvas(id, 240, () => {
    const primary = token("--primary", "#4a5ab9");
    const error = token("--error", "#b3261e");
    const secondary = token("--secondary", "#5b6b8c");

    return {
      type: "bar",
      data: {
        labels: counts.map((_, i) => (i / BINS)),
        datasets: [{
          data: counts,
          backgroundColor: counts.map((_, i) =>
            (i / BINS) >= c.reviewThreshold ? error : primary),
          borderRadius: 3,
          categoryPercentage: 1,
          barPercentage: 0.92,
        }],
      },
      options: {
        ...baseOptions(),
        scales: {
          x: {
            grid: { display: false },
            border: { color: gridColor() },
            ticks: {
              font: tickFont(), color: token("--on-surface-variant", "#5f5f5f"),
              // Chart.js rotates labels the moment it thinks they may collide. With
              // 25 bins it always thinks so, and diagonal axis labels are harder to
              // read than fewer horizontal ones -- so only every fifth bin is named
              // and rotation is pinned off.
              maxRotation: 0, minRotation: 0, autoSkip: false,
              callback: (v, i) => (i % 5 === 0 ? `${Math.round((i / BINS) * 100)}%` : ""),
            },
            title: {
              display: true, text: "similarity between a pair of submissions",
              font: tickFont(), color: token("--on-surface-variant", "#5f5f5f"),
            },
          },
          y: {
            beginAtZero: true,
            grid: { color: gridColor() },
            border: { display: false },
            ticks: { font: tickFont(), color: token("--on-surface-variant", "#5f5f5f"), precision: 0 },
            title: {
              display: true, text: "number of pairs",
              font: tickFont(), color: token("--on-surface-variant", "#5f5f5f"),
            },
          },
        },
        plugins: {
          ...baseOptions().plugins,
          tooltip: {
            ...baseOptions().plugins.tooltip,
            callbacks: {
              title: (items) => {
                const i = items[0].dataIndex;
                return `${Math.round((i / BINS) * 100)}–${Math.round(((i + 1) / BINS) * 100)}% similar`;
              },
              label: (item) => `${item.raw} pair${item.raw === 1 ? "" : "s"}`,
            },
          },
          rules: {
            marks: [
              { at: c.median, color: secondary, label: `median ${c.median.toFixed(3)}` },
              { at: c.reviewThreshold, color: error, label: `flag above ${c.reviewThreshold.toFixed(3)}` },
            ],
            bins: BINS,
          },
        },
      },
      plugins: [verticalRules],
    };
  });

  return `${markup}
    <div class="chart-legend">
      <span class="key"><span class="swatch" style="background:var(--primary)"></span>below the line</span>
      <span class="key"><span class="swatch" style="background:var(--error)"></span>flagged for review</span>
      <span class="key"><span class="swatch swatch-rule" style="background:var(--secondary)"></span>cohort median</span>
    </div>`;
}

/** Draws the median and threshold rules over a histogram, with labels. */
const verticalRules = {
  id: "rules",
  afterDatasetsDraw(chart, args, opts) {
    if (!opts || !opts.marks) return;
    const { ctx, chartArea, scales } = chart;
    opts.marks.forEach((mark) => {
      if (!Number.isFinite(mark.at) || mark.at < 0 || mark.at > 1) return;
      const x = scales.x.left + mark.at * (scales.x.right - scales.x.left);
      ctx.save();
      ctx.strokeStyle = mark.color;
      ctx.lineWidth = 1.5;
      ctx.setLineDash([4, 3]);
      ctx.beginPath();
      ctx.moveTo(x, chartArea.top - 2);
      ctx.lineTo(x, chartArea.bottom);
      ctx.stroke();
      ctx.setLineDash([]);
      ctx.fillStyle = mark.color;
      ctx.font = "600 10px Manrope, sans-serif";
      const flip = x > chartArea.right - 92;
      ctx.textAlign = flip ? "right" : "left";
      ctx.fillText(mark.label, flip ? x - 5 : x + 5, chartArea.top + 8);
      ctx.restore();
    });
  },
};

/**
 * Authorship across the cohort, one horizontal bar per submission.
 *
 * Sorted, so the shape of the batch is the first thing visible: a cohort where
 * everything sits high is a different situation from one where a single file does.
 * The undecided band is drawn as a shaded region rather than a line, because a score
 * inside it is explicitly not a verdict.
 */
function authorshipChart(files) {
  const scored = files.filter((f) => typeof f.aiScore === "number")
    .sort((a, b) => b.aiScore - a.aiScore);
  if (!scored.length) return "";

  const id = "chart-authorship";
  return chartCanvas(id, Math.max(160, 26 * scored.length + 46), () => {
    const error = token("--error", "#b3261e");
    const primary = token("--primary", "#4a5ab9");
    const muted = token("--outline", "#79747e");
    const colourFor = (v) => (v >= 0.6 ? error : v <= 0.4 ? primary : muted);

    return {
      type: "bar",
      data: {
        labels: scored.map((f) => f.label || f.filename),
        datasets: [{
          data: scored.map((f) => f.aiScore),
          backgroundColor: scored.map((f) => colourFor(f.aiScore)),
          borderRadius: 4,
          barPercentage: 0.72,
        }],
      },
      options: {
        ...baseOptions(),
        indexAxis: "y",
        scales: {
          x: {
            min: 0, max: 1,
            grid: { color: gridColor() },
            border: { display: false },
            ticks: {
              font: tickFont(), color: token("--on-surface-variant", "#5f5f5f"),
              callback: (v) => `${Math.round(v * 100)}%`,
            },
            title: {
              display: true, text: "share of decision trees reading the file as generated",
              font: tickFont(), color: token("--on-surface-variant", "#5f5f5f"),
            },
          },
          y: {
            grid: { display: false },
            border: { color: gridColor() },
            ticks: { font: tickFont(), color: token("--on-surface", "#1c1b1f") },
          },
        },
        plugins: {
          ...baseOptions().plugins,
          tooltip: {
            ...baseOptions().plugins.tooltip,
            callbacks: {
              title: (items) => scored[items[0].dataIndex].filename,
              label: (item) => {
                const f = scored[item.dataIndex];
                const v = Math.round(f.aiScore * 100);
                if (f.aiVerdict === "unsure") return `${v}% — undecided, not evidence`;
                return f.aiVerdict === "generated"
                  ? `${v}% — reads as generated`
                  : `${v}% — reads as hand-written`;
              },
            },
          },
          band: { from: 0.5 - AI_BAND, to: 0.5 + AI_BAND },
        },
      },
      plugins: [undecidedBand],
    };
  });
}

/** Shades the undecided band so a score inside it cannot be misread as a verdict. */
const undecidedBand = {
  id: "band",
  beforeDatasetsDraw(chart, args, opts) {
    if (!opts || !Number.isFinite(opts.from)) return;
    const { ctx, chartArea, scales } = chart;
    const x1 = scales.x.getPixelForValue(opts.from);
    const x2 = scales.x.getPixelForValue(opts.to);
    ctx.save();
    ctx.fillStyle = token("--surface-variant", "#eeeeee");
    ctx.globalAlpha = 0.75;
    ctx.fillRect(x1, chartArea.top, x2 - x1, chartArea.bottom - chartArea.top);
    ctx.restore();
  },
};

let AI_BAND = 0.10;
function setAiBand(v) { if (Number.isFinite(v)) AI_BAND = v; }
