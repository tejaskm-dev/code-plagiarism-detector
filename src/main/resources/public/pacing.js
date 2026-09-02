"use strict";

/**
 * Deliberate pacing.
 *
 * Work that finishes instantly reads as work that was not done. A verdict on academic
 * integrity that appears in forty milliseconds looks like a guess, and a reader who
 * distrusts it will not use it. So the interface holds a result back until it has been
 * on screen long enough to have registered.
 *
 * Two rules keep this from becoming a lie:
 *
 *   1. Only ever a MINIMUM. Nothing is slowed down. If the real work takes longer than
 *      the floor, the floor is irrelevant and the user waits for the actual result.
 *   2. Anything shown during the wait must be true. The analysis stages below are the
 *      steps the engine really performs, in the order it performs them — not invented
 *      captions for a bar that is really just a timer.
 *
 * The floor scales with the weight of the outcome. A verdict earns a couple of seconds;
 * an action the user repeats forty times gets a couple of hundred milliseconds, because
 * making a rename feel weighty just makes it feel broken.
 */

const PACE = {
  /** A verdict over the whole class. The one moment worth waiting for. */
  analysis: 2600,
  /** Opening a comparison. Long enough to feel fetched, short enough to browse. */
  pair: 420,
  /** Upload feedback: the count needs a beat to be read. */
  upload: 700,
  /** Repeated edits. Just enough to confirm the click landed. */
  edit: 260,
};

const sleep = (ms) => new Promise((resolve) => setTimeout(resolve, ms));

/** Runs work, returning no sooner than `floor` milliseconds from now. */
async function atLeast(floor, work) {
  const started = performance.now();
  const result = await work;
  const remaining = floor - (performance.now() - started);
  if (remaining > 0) await sleep(remaining);
  return result;
}

/**
 * Steps a caption list forward while real work runs underneath.
 *
 * Deliberately does not pretend to know progress. There is no percentage, because the
 * engine does not report one and inventing a number that creeps to 90% and stalls is
 * the exact dishonesty this module exists to avoid. What it shows is which stage is
 * running and which are done.
 */
function runStages(host, stages, options) {
  const perStage = (options && options.perStage) || 380;
  let index = 0;
  let stopped = false;

  const paint = () => {
    host.innerHTML = `<ol class="stages">` + stages.map((stage, i) => {
      const status = i < index ? "done" : i === index ? "active" : "waiting";
      return `<li class="stage ${status}">
        <span class="marker">${i < index ? "&#10003;" : i === index ? "" : ""}</span>
        <span class="body">
          <span class="label">${stage.label}</span>
          <span class="detail">${stage.detail}</span>
        </span></li>`;
    }).join("") + `</ol>`;
  };

  paint();
  const timer = setInterval(() => {
    if (stopped) return;
    // Hold on the last stage rather than completing it: the work is not finished
    // until the response lands, and showing it finished would be a lie.
    if (index < stages.length - 1) { index++; paint(); }
  }, perStage);

  return {
    finish() {
      stopped = true;
      clearInterval(timer);
      index = stages.length;
      paint();
    },
    abort() {
      stopped = true;
      clearInterval(timer);
    },
  };
}

/**
 * Counts a number up to its final value.
 *
 * Used only on figures that are the point of the card. A number that animates draws
 * the eye; a screen where everything animates draws it nowhere.
 */
function countUp(element, target, options) {
  const decimals = (options && options.decimals) || 0;
  const suffix = (options && options.suffix) || "";
  const duration = (options && options.duration) || 700;

  if (window.matchMedia("(prefers-reduced-motion: reduce)").matches || target === 0) {
    element.textContent = target.toFixed(decimals) + suffix;
    return;
  }

  const started = performance.now();
  const tick = (now) => {
    const t = Math.min(1, (now - started) / duration);
    // Ease out: fast at first, settling at the end, so the final value is readable
    // for most of the animation rather than racing past.
    const eased = 1 - Math.pow(1 - t, 3);
    element.textContent = (target * eased).toFixed(decimals) + suffix;
    if (t < 1) requestAnimationFrame(tick);
    else element.textContent = target.toFixed(decimals) + suffix;
  };
  requestAnimationFrame(tick);
}
