"use strict";

/* ==========================================================================
   State. Only the assignment id survives a reload; everything else is
   re-fetched, because the server is the authority on what is current.
   ========================================================================== */
const STORAGE_KEY = "integrity.assignmentId";

const state = {
  assignment: null,
  roster: null,
  results: null,
  filesById: new Map(),
  rosterSort: { key: "student", desc: false },
  pairSort: { key: "score", desc: true },
  rosterFilter: "",
  onlyFlagged: false,
};

const $ = (id) => document.getElementById(id);
const pct = (v) => (v * 100).toFixed(1) + "%";
const esc = (t) => String(t ?? "").replace(/&/g, "&amp;").replace(/</g, "&lt;")
  .replace(/>/g, "&gt;").replace(/"/g, "&quot;").replace(/'/g, "&#39;");

/** Ids contain slashes; each segment is encoded so the route still matches. */
const encodeId = (id) => String(id).split("/").map(encodeURIComponent).join("/");

const initials = (name) => {
  const clean = String(name || "?").replace(/^unidentified-/, "?");
  const parts = clean.split(/[^A-Za-z0-9]+/).filter(Boolean);
  if (!parts.length) return "?";
  return (parts.length === 1 ? parts[0].slice(0, 2) : parts[0][0] + parts[1][0]).toUpperCase();
};

async function api(path, options) {
  const response = await fetch(path, options);
  if (!response.ok) {
    let message = `request failed (HTTP ${response.status})`;
    try { const b = await response.json(); if (b && b.message) message = b.message; } catch (e) { /* */ }
    const error = new Error(message);
    error.status = response.status;
    throw error;
  }
  return response.json();
}

/* ---------------------------------------------------------------- fragments */
function notice(kind, title, body, action) {
  const glyph = { warn: "warning", error: "warning", ok: "check", info: "info" }[kind] || "info";
  return `<div class="notice notice-${kind}">
    <span class="glyph">${icon(glyph, 18)}</span>
    <div class="body"><div class="title">${esc(title)}</div><div class="t-body">${body}</div></div>
    ${action || ""}</div>`;
}

function emptyState(iconName, headline, detail, action) {
  return `<div class="state">
    <span class="glyph">${icon(iconName, 34)}</span>
    <div class="headline">${esc(headline)}</div>
    <div class="detail t-body">${esc(detail)}</div>
    ${action ? `<div class="actions">${action}</div>` : ""}</div>`;
}

/* ==========================================================================
   Navigation
   ========================================================================== */
const VIEW_ORDER = ["submissions", "overview", "pairs", "compare"];
let currentView = "overview";

function showView(name) {
  // Slide in from the side you came from, so moving back feels like moving back.
  const goingBack = VIEW_ORDER.indexOf(name) < VIEW_ORDER.indexOf(currentView);
  currentView = name;

  document.querySelectorAll(".view").forEach((v) => {
    v.classList.remove("active", "came-back");
  });
  const target = $("view-" + name);
  target.classList.add("active");
  if (goingBack) target.classList.add("came-back");
  document.querySelectorAll("#nav .nav-item").forEach((b) => {
    if (b.dataset.view === name) b.setAttribute("aria-current", "page");
    else b.removeAttribute("aria-current");
  });
  window.scrollTo({ top: 0 });
}
document.querySelectorAll("#nav .nav-item").forEach((b) =>
  b.addEventListener("click", () => { if (!b.disabled) showView(b.dataset.view); }));

const setNav = (name, enabled, title) => {
  const b = document.querySelector(`#nav .nav-item[data-view="${name}"]`);
  b.disabled = !enabled;
  b.title = title || "";
};
const setTally = (name, value) => {
  const t = $("tally-" + name);
  if (!t) return;
  t.hidden = value === null || value === undefined;
  if (!t.hidden) t.textContent = value;
};

function renderContext() {
  if (!state.assignment) {
    $("context-name").textContent = "No assignment open";
    $("context-meta").textContent = "Create one to begin";
    $("run-check").disabled = true;
    return;
  }
  const r = state.roster;
  const bits = [];
  if (r) bits.push(`${r.count} submission${r.count === 1 ? "" : "s"}`);
  if (r && r.unidentifiedCount > 0) bits.push(`${r.unidentifiedCount} unidentified`);
  bits.push(state.results ? "analysed" : "not analysed");
  $("context-name").textContent = state.assignment.name;
  $("context-meta").textContent = bits.join("  ·  ");
  $("run-check").disabled = !r || r.count < 2;
}

/* ==========================================================================
   OVERVIEW — verdict, tier explainer, distribution
   ========================================================================== */
function renderOverview() {
  const host = $("overview-content");

  if (!state.assignment) {
    host.innerHTML = `<div class="card"><div class="card-body">` +
      emptyState("inbox", "No assignment open",
        "Create an assignment and upload a batch to see an analysis here.",
        `<button class="btn btn-primary" onclick="showView('submissions')">Go to Submissions</button>`) +
      `</div></div>`;
    return;
  }
  if (!state.results) {
    const count = state.roster ? state.roster.count : 0;
    host.innerHTML = `<div class="card"><div class="card-body">` +
      emptyState("chart", "This batch has not been analysed",
        count < 2
          ? "At least two submissions are needed before anything can be compared."
          : `${count} submissions are ready. Nothing is compared until you run an analysis.`,
        count >= 2 ? `<button class="btn btn-primary" id="overview-analyse">Analyse batch</button>` : "") +
      `</div></div>`;
    const b = $("overview-analyse");
    if (b) b.addEventListener("click", runAnalysis);
    return;
  }

  const c = state.results.cohort;
  const pairs = state.results.pairs;
  const flagged = pairs.filter((p) => p.flagged);
  const top = flagged.slice().sort((a, b) => b.score - a.score).slice(0, 5);

  const verdict = c.flaggedCount === 0
    ? "Nothing here needs your attention."
    : c.flaggedCount === 1
      ? "One pair is worth a look."
      : `${c.flaggedCount} pairs are worth a look.`;

  const explain = summaryParagraph(c, pairs);

  host.innerHTML = `
    <div class="page-head">
      <h2 class="t-display" style="color:var(--primary)">${esc(state.assignment.name)}</h2>
      <p class="t-body-lg muted">Analysis run ${esc(new Date(state.results.generatedAt).toLocaleString())}.</p>
    </div>

    ${c.unidentifiedCount > 0 ? notice("warn",
      `${c.unidentifiedCount} submission${c.unidentifiedCount === 1 ? "" : "s"} could not be attributed to a student`,
      "Results below include them, but a finding you cannot attribute to a person cannot be acted on.",
      `<button class="btn btn-sm" onclick="showView('submissions')">Resolve</button>`) : ""}

    <div class="bento stagger">
      <!-- verdict -->
      <div class="card col-6" style="display:flex;flex-direction:column;justify-content:space-between;min-height:300px">
        <div class="card-body">
          <div class="row" style="margin-bottom:var(--md)">
            <span class="chip ${c.flaggedCount ? "chip-flagged" : "chip-ok"}">
              ${c.flaggedCount ? "review needed" : "nothing unusual"}</span>
            <span class="t-label muted">${c.pairCount} pairs compared</span>
          </div>
          <h3 class="t-headline" style="color:var(--primary);margin-bottom:var(--sm)">${esc(verdict)}</h3>
          <p class="t-body-lg muted" style="max-width:58ch">${explain}</p>
          <p class="t-body muted" style="max-width:58ch;margin-top:var(--sm)">${NOT_AN_ACCUSATION}</p>
        </div>
        ${c.flaggedCount ? `<div class="card-body" style="padding-top:0">
          <button class="btn btn-primary" onclick="showView('pairs')">
            Review flagged pairs ${icon("arrow", 16)}</button></div>` : ""}
      </div>

      <!-- counters -->
      <div class="col-3 stat-stack">
        <div class="card" style="flex:1">
          <div class="card-body stat">
            <div class="stat-head">
              <span class="t-label upper muted">Submissions</span>
              <span class="muted">${icon("description", 18)}</span>
            </div>
            <div class="value" data-count="${c.submissionCount}">0</div>
            <div class="t-label muted">${state.results.files.length} files carried for review</div>
          </div>
        </div>
        <div class="card" style="flex:1">
          <div class="card-body stat">
            <div class="stat-head">
              <span class="t-label upper muted">Flagged pairs</span>
              <span style="color:${c.flaggedCount ? "var(--error)" : "var(--outline)"}">${icon("flag", 18)}</span>
            </div>
            <div class="value ${c.flaggedCount ? "alert" : ""}" data-count="${c.flaggedCount}">0</div>
            <div class="t-label muted">of ${c.pairCount} compared</div>
          </div>
        </div>
      </div>

      <!-- distribution -->
      <div class="card col-3">
        <div class="card-body stat" style="height:100%">
          <div class="stat-head">
            <span class="t-label upper muted">Median similarity</span>
            <span class="muted">${icon("chart", 18)}</span>
          </div>
          <div class="value" data-count="${(c.median * 100).toFixed(1)}" data-decimals="1" data-suffix="%">0</div>
          <div class="t-label muted">Baseline overlap for this cohort.</div>
          <div style="margin-top:auto;padding-top:var(--md)">
            <p class="t-body muted">This is how much two students in this class typically
              have in common. Anything near this figure is ordinary.</p>
            <div class="row between t-label" style="margin-top:var(--xs)">
              <span class="muted">Unusual above</span>
              <span class="tabular strong">${Math.round(c.reviewThreshold * 100)}%</span></div>
          </div>
        </div>
      </div>

      <!-- tier explainer -->
      <div class="col-6">${tierCard(c)}</div>

      <!-- histogram -->
      <div class="card col-6">
        <div class="card-head"><h3 class="t-title-sm">Score distribution</h3>
          <span class="t-label muted">${c.pairCount} pairs</span></div>
        <div class="card-body">${histogram(pairs, c)}</div>
      </div>

      <!-- authorship -->
      <div class="card col-12">${authorshipCard()}</div>

      <!-- top flagged -->
      <div class="card col-12">
        <div class="card-head">
          <h3 class="t-title-sm">Highest-scoring pairs</h3>
          <button class="btn btn-ghost btn-sm" onclick="showView('pairs')">
            View all pairs ${icon("arrow", 14)}</button>
        </div>
        <div class="card-body flush">${topTable(top, c)}</div>
      </div>
    </div>`;
  paintIcons(host);
  flushCharts();
  host.querySelectorAll("[data-count]").forEach((el) => countUp(el,
    Number(el.dataset.count), { decimals: Number(el.dataset.decimals || 0),
                                suffix: el.dataset.suffix || "" }));
}

/**
 * Authorship across the cohort.
 *
 * This is the surface for the local classifier described in ai-detection-results.md.
 * It reports at three levels because one number per file is not usable on its own:
 * a sentence for the batch, a sorted bar per file, and the model's own reasoning
 * behind a disclosure for anyone who wants it.
 */
function authorshipCard() {
  const files = state.results.files || [];
  const ai = state.results.ai;
  if (ai && Number.isFinite(ai.band)) setAiBand(ai.band);

  const scored = files.filter((f) => typeof f.aiScore === "number");
  if (!scored.length) {
    return `<div class="card-head"><h3 class="t-title-sm">Authorship</h3></div>
      <div class="card-body"><p class="t-body muted">
      ${esc(aiCohortSummary(ai, files.length))}</p></div>`;
  }

  const rows = [...scored].sort((a, b) => b.aiScore - a.aiScore).map((f) => `
    <details class="disclosure">
      <summary>${esc(f.label || f.filename)} — ${esc(aiVerdictLabel(f.aiVerdict))}
        <span class="tabular muted">${Math.round(f.aiScore * 100)}%</span></summary>
      <div class="content"><p class="t-body">${esc(f.aiRationale)}</p>
        <p class="t-label muted" style="margin-top:var(--xs)">
          ${esc(f.filename)} · model ${esc(f.aiModel || "n/a")}</p></div>
    </details>`).join("");

  return `<div class="card-head">
      <h3 class="t-title-sm">Authorship</h3>
      <span class="t-label muted">${scored.length} of ${files.length} files assessed</span>
    </div>
    <div class="card-body">
      <p class="t-body" style="max-width:72ch">${esc(aiCohortSummary(ai, files.length))}</p>
      ${authorshipChart(files)}
      <div class="chart-legend">
        <span class="key"><span class="swatch" style="background:var(--error)"></span>reads as generated</span>
        <span class="key"><span class="swatch" style="background:var(--primary)"></span>reads as hand-written</span>
        <span class="key"><span class="swatch" style="background:var(--outline)"></span>undecided (shaded band)</span>
      </div>
      <div class="notice notice-info" style="margin-top:var(--md)">
        <div><p class="t-body">${esc(AI_NOT_PROOF)}</p></div>
      </div>
      <details class="disclosure" style="margin-top:var(--sm)">
        <summary>Why each file scored the way it did</summary>
        <div class="content">${rows}</div>
      </details>
    </div>`;
}

/**
 * Which statistical tier decided the flags, in plain language.
 *
 * Not decorative. Tiers 2 and 3 mean the normal measure of spread collapsed, and a
 * reader has no way to know that from the scores alone — this is what makes the
 * tier 2 / tier 3 disagreement visible without reading raw JSON.
 */
function tierCard(c) {
  const t = tierExplained(c);
  return `<div class="card" style="height:100%;${t.tone === "warn"
    ? "border-color:rgba(181,71,8,.35);background:var(--warn-container)" : ""}">
    <div class="card-body">
      <div class="row" style="margin-bottom:var(--xs)">
        <span style="color:${t.tone === "warn" ? "var(--warn)" : "var(--secondary)"}">
          ${icon(t.tone === "warn" ? "warning" : "check", 18)}</span>
        <h3 class="t-title-sm">${esc(t.heading)}</h3>
      </div>
      <p class="t-body" style="max-width:64ch">${esc(t.plain)}</p>
      <p class="t-body muted" style="margin-top:var(--xs);max-width:64ch">${thresholdSentence(c)}</p>
      <details class="disclosure"><summary>Show the underlying numbers</summary>
        <div class="content mono">${esc(t.technical)}</div></details>
    </div></div>`;
}

/**
 * Distribution of every pair score, with the cohort median and the flag threshold
 * drawn as labelled rules and each flagged pair plotted as a point.
 *
 * A bare histogram would show shape but not judgement. The two rules are what let a
 * reader see *why* a given score was or was not flagged.
 */
function histogram(pairs, c) {
  return histogramChart(pairs, c);
}

function topTable(rows, c) {
  if (!rows.length) {
    return emptyState("check", "No pair crossed the threshold",
      `The highest similarity in this cohort stayed below ${c.reviewThreshold.toFixed(3)}.`);
  }
  return `<table><thead><tr>
      <th>Student pair</th><th class="num">Similarity</th>
      <th class="num">Modified z</th><th class="num">Shared fingerprints</th><th class="num"></th>
    </tr></thead><tbody>` +
    rows.map((p) => `<tr class="row-flagged">
      <td>${pairCell(p)}<div class="t-label muted" style="margin-top:2px">${esc(verdictFor(p.score, p.flagged).short)}</div></td>
      <td class="num strong" style="color:var(--error)">${pct(p.score)}</td>
      <td class="num"><span class="tabular strong" title="${esc(unusualness(p.score, c).title)}"
        >${esc(unusualness(p.score, c).text)}</span></td>
      <td class="num tabular muted">${p.sharedFingerprints ?? "—"}</td>
      <td class="num"><button class="btn btn-sm" data-open-pair="${esc(p.left)}|${esc(p.right)}">Compare</button></td>
    </tr>`).join("") + `</tbody></table>`;
}

function pairCell(p) {
  const one = (label, unknown) => `<span class="avatar ${unknown ? "unknown" : ""}"
      title="${esc(label)}">${esc(initials(label))}</span>`;
  return `<div class="row" style="gap:var(--sm)">
    <div style="display:flex">${one(p.leftLabel, p.leftUnidentified)}
      <span style="margin-left:-10px">${one(p.rightLabel, p.rightUnidentified)}</span></div>
    <div class="who">
      <div class="name">${esc(p.leftLabel)} <span class="subtle">&amp;</span> ${esc(p.rightLabel)}</div>
      <div class="file">${esc(p.leftFilename)} · ${esc(p.rightFilename)}</div>
    </div></div>`;
}

/* ==========================================================================
   SUBMISSIONS
   ========================================================================== */
function renderIssues() {
  const out = [];
  const r = state.roster;
  if (r && r.count === 0) {
    out.push(notice("info", "No submissions yet", "Add files or a zip archive below to begin."));
  }
  if (r && r.unidentifiedCount > 0) {
    out.push(notice("warn",
      `${r.unidentifiedCount} submission${r.unidentifiedCount === 1 ? "" : "s"} need a student identifier`,
      "These files carried no student folder and no identifier in their filename. They are " +
      "compared normally and never merged together, but a result you cannot attribute to a " +
      "person cannot be acted on. Use <strong>Resolve</strong> in the table below.",
      `<button class="btn btn-sm" id="jump-unidentified">Show them</button>`));
  }
  if (r && r.count > 1 && !state.results) {
    out.push(notice("info", "This batch has not been analysed",
      "Nothing is compared until you run an analysis."));
  }
  $("submissions-issues").innerHTML = out.join("");
  paintIcons($("submissions-issues"));
  const jump = $("jump-unidentified");
  if (jump) jump.addEventListener("click", () => {
    state.rosterFilter = "unidentified-";
    $("roster-filter").value = "unidentified-";
    renderRoster();
    $("roster-card").scrollIntoView({ behavior: "smooth", block: "start" });
  });
}

function renderRoster() {
  const r = state.roster;
  $("roster-count").textContent = r ? `${r.count} total` : "0";
  const body = $("roster-body");
  const empty = $("roster-empty");
  $("analyze").disabled = !r || r.count < 2;
  $("analyze").title = (!r || r.count < 2) ? "At least two submissions are needed" : "";

  if (!r || r.count === 0) {
    body.innerHTML = "";
    empty.innerHTML = emptyState("inbox", "No submissions yet",
      "Drop files or a zip archive above. A zip with one folder per student attributes work automatically.");
    paintIcons(empty);
    return;
  }

  const filter = state.rosterFilter.toLowerCase();
  const rows = r.submissions
    .filter((s) => !filter || s.student.toLowerCase().includes(filter) ||
                   s.filename.toLowerCase().includes(filter))
    .sort((a, b) => {
      const { key, desc } = state.rosterSort;
      const x = a[key], y = b[key];
      const cmp = typeof x === "string" ? x.localeCompare(y, undefined, { numeric: true })
                                        : (x === y ? 0 : x < y ? -1 : 1);
      return desc ? -cmp : cmp;
    });

  body.innerHTML = rows.map((s) => `
    <tr class="${s.unidentified ? "row-attention" : ""}" data-sid="${esc(s.submissionId)}">
      <td>
        <div class="row" style="gap:var(--sm)">
          <span class="avatar ${s.unidentified ? "unknown" : ""}">${esc(initials(s.student))}</span>
          <div class="who"><div class="name" data-role="student">${esc(s.student)}</div></div>
        </div>
      </td>
      <td class="mono t-label muted">${esc(s.filename)}</td>
      <td><span class="chip chip-neutral">${esc(s.language)}</span></td>
      <td class="num tabular muted">${s.lineCount}</td>
      <td>${s.unidentified
        ? `<span class="chip chip-warn">${icon("warning", 12)} needs identifying</span>`
        : `<span class="chip chip-ok">${icon("check", 12)} identified</span>`}</td>
      <td class="num nowrap">
        <button class="btn btn-sm" data-action="rename">${s.unidentified ? "Resolve" : "Rename"}</button>
        <button class="btn btn-sm btn-danger" data-action="remove" aria-label="Remove">${icon("trash", 13)}</button>
      </td>
    </tr>`).join("");

  empty.innerHTML = rows.length ? "" :
    emptyState("search", "Nothing matches that filter", `No submission matches "${state.rosterFilter}".`);
  paintIcons(body);
  paintIcons(empty);

  body.querySelectorAll("button[data-action]").forEach((button) => {
    const row = button.closest("tr");
    const sid = row.dataset.sid;
    if (button.dataset.action === "rename") button.addEventListener("click", () => beginRename(row, sid));
    else button.addEventListener("click", () => removeSubmission(sid, row));
  });
}

/** Inline edit, so attributing a file never means leaving the table. */
function beginRename(row, submissionId) {
  const cell = row.querySelector('[data-role="student"]');
  if (!cell) return;
  const current = cell.textContent.trim();
  const editing = current.startsWith("unidentified-") ? "" : current;

  cell.innerHTML = `<input type="text" value="${esc(editing)}" style="width:190px"
    placeholder="e.g. 22CS101" aria-label="Student identifier">`;
  const input = cell.querySelector("input");
  input.focus();
  input.select();

  let done = false;
  const commit = async () => {
    if (done) return;
    done = true;
    const value = input.value.trim();
    if (!value || value === current) { renderRoster(); return; }
    try {
      const form = new FormData();
      form.append("student", value);
      await atLeast(PACE.edit,
        api(`/api/v1/assignments/${state.assignment.id}/submissions/${encodeId(submissionId)}`,
          { method: "PATCH", body: form }));
      await refreshRoster();
    } catch (e) {
      $("upload-feedback").innerHTML = notice("error", "Could not set the identifier", esc(e.message));
      paintIcons($("upload-feedback"));
      renderRoster();
    }
  };
  input.addEventListener("keydown", (e) => {
    if (e.key === "Enter") commit();
    if (e.key === "Escape") { done = true; renderRoster(); }
  });
  input.addEventListener("blur", commit);
}

async function removeSubmission(submissionId, row) {
  const who = row.querySelector('[data-role="student"]');
  if (!confirm(`Remove ${who ? who.textContent.trim() : "this submission"} from the batch?`)) return;
  try {
    await api(`/api/v1/assignments/${state.assignment.id}/submissions/${encodeId(submissionId)}`,
      { method: "DELETE" });
    await refreshRoster();
  } catch (e) {
    $("upload-feedback").innerHTML = notice("error", "Could not remove", esc(e.message));
    paintIcons($("upload-feedback"));
  }
}

$("roster-filter").addEventListener("input", (e) => { state.rosterFilter = e.target.value; renderRoster(); });
document.querySelectorAll("#roster-table th.sortable").forEach((th) =>
  th.addEventListener("click", () => {
    const key = th.dataset.sort;
    state.rosterSort = { key, desc: state.rosterSort.key === key ? !state.rosterSort.desc : false };
    document.querySelectorAll("#roster-table th").forEach((h) => h.removeAttribute("aria-sort"));
    th.setAttribute("aria-sort", state.rosterSort.desc ? "descending" : "ascending");
    renderRoster();
  }));

/* ==========================================================================
   Assignment lifecycle
   ========================================================================== */
async function openAssignment(id) {
  try {
    const roster = await api(`/api/v1/assignments/${id}/submissions`);
    state.assignment = { id: roster.assignmentId, name: roster.assignmentName, createdAt: roster.createdAt };
    state.roster = roster;
    localStorage.setItem(STORAGE_KEY, id);

    $("start-card").hidden = true;
    $("upload-card").hidden = false;
    $("roster-card").hidden = false;
    $("boilerplate-card").hidden = false;
    setTally("submissions", roster.count);

    if (roster.analysed) await loadResults(true);
    else clearResults();

    renderRoster();
    renderIssues();
    renderContext();
    renderOverview();
    return true;
  } catch (e) {
    if (e.status === 404) { localStorage.removeItem(STORAGE_KEY); return false; }
    throw e;
  }
}

function clearResults() {
  state.results = null;
  setNav("pairs", false, "Run an analysis first");
  setNav("compare", false, "Open a pair to compare");
  setTally("pairs", null);
}

async function loadResults(quiet) {
  try {
    state.results = await api(`/api/v1/assignments/${state.assignment.id}/results`);
    state.filesById = new Map(state.results.files.map((f) => [f.submissionId, f]));
    setNav("pairs", true);
    setTally("pairs", state.results.pairs.length);
    renderPairs();
  } catch (e) {
    clearResults();
    if (!quiet) throw e;
  }
}

async function refreshRoster() {
  state.roster = await api(`/api/v1/assignments/${state.assignment.id}/submissions`);
  setTally("submissions", state.roster.count);
  if (!state.roster.analysed) clearResults();
  renderRoster();
  renderIssues();
  renderContext();
  renderOverview();
}

$("create-assignment").addEventListener("click", async () => {
  const form = new FormData();
  form.append("name", $("assignment-name").value.trim() || "Untitled assignment");
  try {
    const created = await api("/api/v1/assignments", { method: "POST", body: form });
    await openAssignment(created.id);
    showView("submissions");
  } catch (e) {
    $("submissions-issues").innerHTML = notice("error", "Could not create the assignment", esc(e.message));
    paintIcons($("submissions-issues"));
  }
});
$("assignment-name").addEventListener("keydown", (e) => {
  if (e.key === "Enter") $("create-assignment").click();
});

$("new-analysis").addEventListener("click", () => {
  localStorage.removeItem(STORAGE_KEY);
  state.assignment = null; state.roster = null; clearResults();
  $("start-card").hidden = false;
  $("upload-card").hidden = true;
  $("roster-card").hidden = true;
  $("boilerplate-card").hidden = true;
  $("assignment-name").value = "";
  setTally("submissions", null);
  renderContext(); renderOverview(); renderRecent();
  showView("submissions");
});

$("switch-assignment").addEventListener("click", () => {
  $("start-card").hidden = false;
  renderRecent();
  showView("submissions");
  $("start-card").scrollIntoView({ behavior: "smooth", block: "start" });
});

async function renderRecent() {
  try {
    const all = await api("/api/v1/assignments");
    if (!all.length) return;
    $("recent-wrap").hidden = false;
    $("recent-list").innerHTML = all.slice(0, 8).map((a) => `
      <div class="row between" style="padding:var(--xs) 0;border-bottom:1px solid var(--surface-variant)">
        <div><div class="strong">${esc(a.name)}</div>
          <div class="t-label muted mono">${esc(a.id)}${a.analysed ? " · analysed" : ""}</div></div>
        <button class="btn btn-sm" data-open="${esc(a.id)}">Open</button></div>`).join("");
    $("recent-list").querySelectorAll("button[data-open]").forEach((b) =>
      b.addEventListener("click", () => openAssignment(b.dataset.open)));
  } catch (e) { /* the list is a convenience, never a blocker */ }
}

/* ==========================================================================
   Upload
   ========================================================================== */
const dropzone = $("dropzone");
["dragenter", "dragover"].forEach((t) =>
  dropzone.addEventListener(t, (e) => { e.preventDefault(); dropzone.classList.add("over"); }));
["dragleave", "drop"].forEach((t) =>
  dropzone.addEventListener(t, (e) => { e.preventDefault(); dropzone.classList.remove("over"); }));
dropzone.addEventListener("drop", (e) => upload(e.dataTransfer.files));
dropzone.addEventListener("click", (e) => { if (e.target.id !== "browse") $("file-input").click(); });
dropzone.addEventListener("keydown", (e) => {
  if (e.key === "Enter" || e.key === " ") { e.preventDefault(); $("file-input").click(); }
});
$("browse").addEventListener("click", (e) => { e.stopPropagation(); $("file-input").click(); });
$("file-input").addEventListener("change", (e) => { upload(e.target.files); e.target.value = ""; });

async function upload(list) {
  const files = Array.from(list || []);
  if (!files.length || !state.assignment) return;
  const form = new FormData();
  files.forEach((f) => form.append("files", f, f.name));
  $("upload-feedback").innerHTML =
    `<div class="row"><span class="spinner"></span><span class="t-body muted">Uploading ${files.length} file(s)…</span></div>`;

  try {
    const response = await atLeast(PACE.upload,
      fetch(`/api/v1/assignments/${state.assignment.id}/submissions`, { method: "POST", body: form }));
    const body = await response.json();
    if (!response.ok && !body.accepted) throw new Error(body.message || "upload failed");

    const bits = [`<strong>${body.acceptedCount}</strong> accepted`,
                  `${body.storedTotal} stored in total`];
    if (Number(body.unidentifiedCount) > 0) bits.push(`${body.unidentifiedCount} unidentified`);
    if (body.rejected.length) bits.push(`${body.rejected.length} rejected`);

    let html = notice(body.acceptedCount > 0 ? "ok" : "error",
      body.acceptedCount > 0 ? "Upload complete" : "Nothing could be accepted", bits.join(" · "));
    if (body.rejected.length) {
      html += `<div class="card"><div class="card-body flush"><table>
        <thead><tr><th>Rejected</th><th>Reason</th></tr></thead><tbody>` +
        body.rejected.map((r) => `<tr><td class="mono t-label">${esc(r.filename || r.path)}</td>
          <td class="muted">${esc(r.reason)}</td></tr>`).join("") + `</tbody></table></div></div>`;
    }
    $("upload-feedback").innerHTML = html;
    paintIcons($("upload-feedback"));
    await refreshRoster();
  } catch (e) {
    $("upload-feedback").innerHTML = notice("error", "Upload failed", esc(e.message));
    paintIcons($("upload-feedback"));
  }
}

$("boilerplate-browse").addEventListener("click", () => $("boilerplate-input").click());
$("boilerplate-input").addEventListener("change", async (e) => {
  const files = Array.from(e.target.files || []);
  e.target.value = "";
  if (!files.length || !state.assignment) return;
  const form = new FormData();
  files.forEach((f) => form.append("files", f, f.name));
  try {
    const body = await api(`/api/v1/assignments/${state.assignment.id}/boilerplate`,
      { method: "POST", body: form });
    $("boilerplate-feedback").innerHTML =
      notice("info", `${body.stored.length} reference file(s) stored`, esc(body.note));
  } catch (err) {
    $("boilerplate-feedback").innerHTML = notice("error", "Upload failed", esc(err.message));
  }
  paintIcons($("boilerplate-feedback"));
});

/* ==========================================================================
   Analysis
   ========================================================================== */
async function runAnalysis() {
  const host = $("overview-content");
  showView("overview");

  // The stages below are the steps the engine genuinely performs. The floor on the
  // whole operation is there because a verdict that lands instantly reads as a guess —
  // but nothing is slowed down: if the analysis takes longer, it simply takes longer.
  host.innerHTML = `<div class="card"><div class="working">
      <div class="working-head">
        <span class="spinner"></span>
        <span class="title">Working through ${state.roster.count} submissions</span>
      </div>
      <div id="stage-host"></div>
    </div></div>`;

  const stages = runStages($("stage-host"), ANALYSIS_STAGES, { perStage: 380 });
  [$("analyze"), $("run-check")].forEach((b) => { if (b) b.disabled = true; });

  try {
    await atLeast(PACE.analysis, (async () => {
      await api(`/api/v1/assignments/${state.assignment.id}/analyze`, { method: "POST" });
      await loadResults(false);
      await refreshRosterQuietly();
    })());
    stages.finish();
    // Let the final tick register before the result replaces it.
    await new Promise((r) => setTimeout(r, 260));
    renderOverview();
  } catch (e) {
    stages.abort();
    host.innerHTML = notice("error", "The analysis could not be completed", esc(e.message));
    paintIcons(host);
  } finally {
    [$("analyze"), $("run-check")].forEach((b) => { if (b) b.disabled = false; });
    renderContext();
  }
}

/** Refreshes the roster without repainting Overview mid-analysis. */
async function refreshRosterQuietly() {
  state.roster = await api(`/api/v1/assignments/${state.assignment.id}/submissions`);
  setTally("submissions", state.roster.count);
  renderRoster();
  renderIssues();
}

$("analyze").addEventListener("click", runAnalysis);
$("run-check").addEventListener("click", runAnalysis);

/* ==========================================================================
   Pairs and Compare — carried over, restyled in the next phase
   ========================================================================== */
state.lens = "groups";

function renderPairs() {
  if (!state.results) return;
  const c = state.results.cohort;
  const pairs = state.results.pairs;

  $("pairs-subtitle").textContent =
    `${c.pairCount} comparisons across ${c.submissionCount} submissions in ${state.assignment.name}.`;

  const tier = tierExplained(c);
  $("pairs-cohort").innerHTML = `
    <div class="notice notice-${tier.tone}">
      <span class="glyph">${icon(tier.tone === "ok" ? "check" : "warning", 18)}</span>
      <div class="body">
        <div class="title">${esc(tier.heading)}</div>
        <div class="t-body">${esc(tier.plain)}</div>
        <div class="t-body" style="margin-top:var(--xs)">${thresholdSentence(c)}</div>
        <details class="disclosure"><summary>Show the underlying numbers</summary>
          <div class="content mono">${esc(tier.technical)}</div></details>
      </div></div>`;
  paintIcons($("pairs-cohort"));

  state.groups = findGroups(pairs);
  state.students = perStudent(pairs, state.results.files);
  $("lens-count-groups").textContent = state.groups.length;
  $("lens-count-students").textContent = state.students.filter((r) => r.flaggedWith.length).length;
  $("lens-count-pairs").textContent = pairs.length;

  renderLens();
}

document.querySelectorAll(".lens-tab").forEach((tab) =>
  tab.addEventListener("click", () => {
    state.lens = tab.dataset.lens;
    document.querySelectorAll(".lens-tab").forEach((t) =>
      t.setAttribute("aria-selected", String(t.dataset.lens === state.lens)));
    renderLens();
  }));

function renderLens() {
  const host = $("lens-body");
  if (state.lens === "groups") host.innerHTML = groupsLens();
  else if (state.lens === "students") host.innerHTML = studentsLens();
  else host.innerHTML = pairsLens();
  paintIcons(host);
  host.classList.remove("stagger");
  void host.offsetWidth;
  host.classList.add("stagger");
}

/* ------------------------------------------------------------------ groups */
function groupsLens() {
  const groups = state.groups;
  if (!groups.length) {
    return `<div class="card"><div class="card-body">` + emptyState("check",
      "No groups formed", "No two students in this class overlap enough to link them together.") +
      `</div></div>`;
  }
  const c = state.results.cohort;

  return groups.map((g, gi) => `
    <div class="card group-card">
      <div class="card-head">
        <div class="row">
          <span class="chip ${g.strongest >= 0.9 ? "chip-flagged" : "chip-warn"}">
            ${icon("warning", 12)} Group ${gi + 1}</span>
          <h3 class="t-title-sm">${g.members.length} students</h3>
        </div>
        <span class="t-label muted">${g.links.length} link${g.links.length === 1 ? "" : "s"}
          &middot; strongest ${pct(g.strongest)}</span>
      </div>
      <div class="card-body">
        <p class="t-body">${esc(describeGroup(g))}</p>
        ${groupDiagram(g)}
        <div class="member-row">
          ${g.members.map((m) => `<span class="member">
            <span class="avatar ${m.startsWith("unidentified-") ? "unknown" : ""}">${esc(initials(m))}</span>
            ${esc(m)}</span>`).join("")}
        </div>
        <details class="disclosure" ${gi === 0 ? "open" : ""}>
          <summary>Show the ${g.links.length} link${g.links.length === 1 ? "" : "s"} in this group</summary>
          <div class="content" style="margin-top:var(--xs)">
            ${g.links.map((l) => `
              <div class="link-row">
                <span class="grow t-body">${esc(l.leftLabel)}
                  <span class="subtle">and</span> ${esc(l.rightLabel)}</span>
                <span class="link-strength"><span class="fill" style="width:${(l.score * 100).toFixed(0)}%"></span></span>
                <span class="t-label tabular strong" style="width:52px;text-align:right">${pct(l.score)}</span>
                <span class="t-label muted" style="width:96px;text-align:right">
                  ${esc(verdictFor(l.score, true).short)}</span>
                <button class="btn btn-sm" data-open-pair="${esc(l.left)}|${esc(l.right)}">Open</button>
              </div>`).join("")}
          </div>
        </details>
      </div>
    </div>`).join("");
}

/**
 * Draws a group as points on a circle with a line for every flagged link.
 *
 * The shape carries the finding. A ring where every point connects to every other
 * looks nothing like a star with one student at the centre, and those two situations
 * call for completely different conversations — but as a list of rows they are
 * indistinguishable. Line weight and opacity follow similarity, so the strong link in
 * a chain is visible immediately.
 */
function groupDiagram(group) {
  const n = group.members.length;
  if (n < 2) return "";

  const W = 460, H = Math.min(240, 96 + n * 26), R = Math.min(H, W) / 2 - 34;
  const cx = W / 2, cy = H / 2;
  const at = (i) => {
    // Start at the top and go clockwise, so the reading order matches the chip list.
    const angle = (i / n) * Math.PI * 2 - Math.PI / 2;
    return { x: cx + Math.cos(angle) * R, y: cy + Math.sin(angle) * R };
  };
  const index = new Map(group.members.map((m, i) => [m, i]));

  const edges = group.links.map((l) => {
    const a = at(index.get(l.leftLabel)), b = at(index.get(l.rightLabel));
    const strong = l.score >= 0.9;
    return `<line x1="${a.x.toFixed(1)}" y1="${a.y.toFixed(1)}"
        x2="${b.x.toFixed(1)}" y2="${b.y.toFixed(1)}"
        stroke="${strong ? "var(--error)" : "var(--secondary)"}"
        stroke-width="${(1 + l.score * 4).toFixed(1)}"
        stroke-opacity="${(0.25 + l.score * 0.6).toFixed(2)}" stroke-linecap="round">
        <title>${esc(l.leftLabel)} and ${esc(l.rightLabel)} — ${pct(l.score)}</title></line>`;
  }).join("");

  // Degree tells you who sits at the centre of a chain, so it sizes the node.
  const degree = new Map(group.members.map((m) => [m, 0]));
  group.links.forEach((l) => {
    degree.set(l.leftLabel, degree.get(l.leftLabel) + 1);
    degree.set(l.rightLabel, degree.get(l.rightLabel) + 1);
  });
  const maxDegree = Math.max(...degree.values());

  const nodes = group.members.map((m, i) => {
    const p = at(i);
    const hub = maxDegree > 1 && degree.get(m) === maxDegree;
    const r = 13 + (degree.get(m) / maxDegree) * 5;
    const labelOut = p.x > cx + 4 ? "start" : p.x < cx - 4 ? "end" : "middle";
    const lx = p.x + (labelOut === "start" ? r + 5 : labelOut === "end" ? -(r + 5) : 0);
    const ly = p.y + (labelOut === "middle" ? (p.y < cy ? -(r + 7) : r + 15) : 4);
    return `<g>
      <circle cx="${p.x.toFixed(1)}" cy="${p.y.toFixed(1)}" r="${r.toFixed(1)}"
        fill="${hub ? "var(--error-container)" : "var(--surface-container-lowest)"}"
        stroke="${hub ? "var(--error)" : "var(--outline-variant)"}" stroke-width="2"/>
      <text x="${p.x.toFixed(1)}" y="${(p.y + 3.5).toFixed(1)}" text-anchor="middle"
        font-size="9" font-weight="700" fill="var(--on-surface-variant)"
        font-family="JetBrains Mono, monospace">${esc(initials(m))}</text>
      <text x="${lx.toFixed(1)}" y="${ly.toFixed(1)}" text-anchor="${labelOut}"
        font-size="10" fill="var(--on-surface-variant)"
        font-family="JetBrains Mono, monospace">${esc(m)}</text>
    </g>`;
  }).join("");

  const hub = [...degree.entries()].find(([, d]) => maxDegree > 1 && d === maxDegree);
  const caption = group.complete
    ? "Every student here matches every other."
    : hub
      ? `${hub[0]} sits at the centre — the most links run through this submission.`
      : "";

  return `<div style="margin:var(--sm) 0">
    <svg viewBox="0 0 ${W} ${H}" class="chart" style="max-width:${W}px"
      role="img" aria-label="Diagram of which students in this group match which others">
      ${edges}${nodes}
    </svg>
    ${caption ? `<p class="t-label muted" style="margin-top:var(--base)">${esc(caption)}</p>` : ""}
  </div>`;
}

/* ---------------------------------------------------------------- students */
function studentsLens() {
  const involved = state.students.filter((r) => r.flaggedWith.length > 0);
  const clear = state.students.length - involved.length;

  if (!involved.length) {
    return `<div class="card"><div class="card-body">` + emptyState("check",
      "No student stands out", "Every submission sits in line with the rest of the class.") +
      `</div></div>`;
  }

  return `<div class="card"><div class="card-body flush">
    <div class="table-scroll"><table>
      <thead><tr>
        <th>Student</th><th class="num">Appears in</th><th>Reading</th>
        <th class="num">Closest match</th><th class="num"></th>
      </tr></thead>
      <tbody>${involved.map((r) => `
        <tr class="${r.flaggedWith.length > 1 ? "row-flagged" : ""}">
          <td><div class="row" style="gap:var(--sm)">
            <span class="avatar ${r.unidentified ? "unknown" : ""}">${esc(initials(r.student))}</span>
            <div class="who"><div class="name">${esc(r.student)}</div>
              <div class="file">${esc(r.filename)}</div></div></div></td>
          <td class="num tabular strong">${r.flaggedWith.length}</td>
          <td class="t-body muted" style="max-width:46ch">${esc(describeStudent(r))}</td>
          <td class="num tabular strong">${pct(r.highest)}</td>
          <td class="num">
            <button class="btn btn-sm" data-open-pair="${esc(r.partners[0].left)}|${esc(r.partners[0].right)}">
              Open closest</button></td>
        </tr>`).join("")}</tbody>
    </table></div>
    ${clear ? `<div class="card-body"><p class="t-body muted">${clear} other
      submission${clear === 1 ? "" : "s"} showed nothing unusual.</p></div>` : ""}
  </div></div>`;
}

/* ------------------------------------------------------------------- pairs */
function pairsLens() {
  const c = state.results.cohort;
  const rows = state.results.pairs
    .filter((p) => !state.onlyFlagged || p.flagged)
    .slice()
    .sort((a, b) => {
      const { key, desc } = state.pairSort;
      const x = a[key], y = b[key];
      const cmp = typeof x === "string" ? x.localeCompare(y) : (x === y ? 0 : x < y ? -1 : 1);
      return desc ? -cmp : cmp;
    });

  return `<div class="card"><div class="card-head">
      <h3 class="t-title-sm">Every comparison</h3>
      <label class="row t-body muted" style="gap:var(--xs)">
        <input type="checkbox" id="only-flagged" ${state.onlyFlagged ? "checked" : ""}>
        only the ones worth a look</label>
    </div>
    <div class="card-body flush"><div class="table-scroll"><table>
      <thead><tr>
        <th>Reading</th><th>Student pair</th><th class="num">Shared code</th>
        <th class="num">How much of one is in the other</th>
        <th class="num">vs. typical</th><th class="num"></th>
      </tr></thead>
      <tbody>${rows.map((p) => {
        const v = verdictFor(p.score, p.flagged);
        const forward = p.containmentLeftInRight >= p.containmentRightInLeft;
        const u = unusualness(p.score, c);
        return `<tr class="${p.flagged ? "row-flagged" : ""}">
          <td><span class="chip chip-${v.tone}">${icon(v.tone === "ok" ? "check" : "warning", 12)}
            ${esc(v.short)}</span></td>
          <td>${pairCell(p)}</td>
          <td class="num"><div class="strong tabular" style="${p.flagged ? "color:var(--error)" : ""}">${pct(p.score)}</div>
            <div class="score-bar"><div class="fill ${p.score >= c.reviewThreshold ? "hot" : ""}"
              style="width:${(Math.min(1, p.score) * 100).toFixed(1)}%"></div></div></td>
          <td class="num"><div class="dir">
              <span class="who">${esc(forward ? p.leftLabel : p.rightLabel)}</span>
              <span aria-hidden="true">&#8594;</span>
              <span class="who">${esc(forward ? p.rightLabel : p.leftLabel)}</span>
              <span class="tabular strong">${pct(Math.max(p.containmentLeftInRight, p.containmentRightInLeft))}</span>
            </div>
            <div class="dir muted" style="opacity:.7"><span aria-hidden="true">&#8592;</span>
              <span class="tabular">${pct(Math.min(p.containmentLeftInRight, p.containmentRightInLeft))}</span></div></td>
          <td class="num"><span class="tabular strong" title="${esc(u.title)}">${esc(u.text)}</span></td>
          <td class="num"><button class="btn btn-sm"
            data-open-pair="${esc(p.left)}|${esc(p.right)}">Compare</button></td>
        </tr>`; }).join("")}</tbody>
    </table></div></div></div>`;
}

document.addEventListener("change", (e) => {
  if (e.target && e.target.id === "only-flagged") {
    state.onlyFlagged = e.target.checked;
    renderLens();
  }
});

/* ==========================================================================
   COMPARE — the screen that has to convince a non-specialist

   Rebuilt around three things the previous version lacked: it opens for any
   pair rather than only flagged ones, it says in plain words what the reader
   is looking at, and it lets them step through the matches instead of hunting
   for yellow lines in a long file.
   ========================================================================== */
/*
 * Every "Compare" / "Open" button in the app carries data-open-pair="left|right".
 * Nothing was listening for it, so openDiff() below was unreachable and the buttons
 * did nothing at all. One delegated listener covers all of them, including rows
 * rendered after this file runs.
 */
document.addEventListener("click", (event) => {
  const trigger = event.target.closest("[data-open-pair]");
  if (!trigger) return;
  event.preventDefault();
  const [left, right] = trigger.dataset.openPair.split("|");
  if (left && right) openDiff(left, right);
});

const compare = { detail: null, matches: [], index: 0, syncing: false };

async function openDiff(leftId, rightId) {
  const host = $("compare-content");
  setNav("compare", true);
  showView("compare");
  host.innerHTML = `<div class="card"><div class="card-body">
    <div class="state"><span class="spinner"></span>
    <div class="headline" style="margin-top:var(--sm)">Lining up the two files…</div>
    <div class="detail">Finding every passage they have in common.</div></div></div></div>`;

  try {
    // A short floor: long enough that the comparison feels retrieved rather than
    // conjured, short enough that stepping through ten pairs is not a chore.
    const detail = await atLeast(PACE.pair,
      api(`/api/v1/assignments/${state.assignment.id}/pair` +
          `?left=${encodeURIComponent(leftId)}&right=${encodeURIComponent(rightId)}`));
    compare.detail = detail;
    // Matched regions are paired positionally: the nth region on the left is shown
    // beside the nth on the right, which is what makes stepping through them useful.
    compare.matches = detail.left.spans.map((span, i) => ({
      left: span, right: detail.right.spans[i] || detail.right.spans[detail.right.spans.length - 1],
    })).filter((m) => m.right);
    compare.index = 0;
    renderCompare();
  } catch (e) {
    host.innerHTML = notice("error", "Could not open this comparison", esc(e.message));
    paintIcons(host);
  }
}

function renderCompare() {
  const d = compare.detail;
  const cohort = {
    median: d.cohortMedian, reviewThreshold: d.reviewThreshold,
    mad: d.cohortMad,
  };
  const v = verdictFor(d.score, d.flagged);
  const total = compare.matches.length;

  $("compare-content").innerHTML = `
    <div class="compare-head" style="margin-bottom:var(--md)">
      <div>
        <button class="btn btn-ghost btn-sm" id="back-to-pairs" style="margin-bottom:var(--xs)">
          ${icon("arrow", 14)} Back to all pairs</button>
        <h2 class="t-headline-sm" style="color:var(--primary)">
          ${esc(d.left.student)} <span class="subtle">and</span> ${esc(d.right.student)}</h2>
      </div>
      <div class="match-nav" role="group" aria-label="Step through matches">
        <button id="match-prev" aria-label="Previous match" ${total < 2 ? "disabled" : ""}>&#9650;</button>
        <span class="counter" id="match-counter"></span>
        <button id="match-next" aria-label="Next match" ${total < 2 ? "disabled" : ""}>&#9660;</button>
      </div>
    </div>

    <div class="verdict-banner tone-${v.tone}">
      <span style="flex-shrink:0;margin-top:2px">${icon(v.tone === "ok" ? "check" : "warning", 22)}</span>
      <div class="grow">
        <div class="headline">${esc(v.label)}</div>
        <p class="t-body" style="margin-top:var(--base);max-width:70ch">${esc(v.meaning)}</p>
        <p class="t-body" style="margin-top:var(--xs);max-width:70ch">${scoreInContext(d.score, cohort)}</p>
        <p class="t-label" style="margin-top:var(--xs);opacity:.85">${NOT_AN_ACCUSATION}</p>
      </div>
    </div>

    <div class="card" style="margin-bottom:var(--md)">
      <div class="card-body">
        <div class="row wrap" style="gap:var(--lg)">
          <div><div class="t-label upper muted">Shared structure</div>
            <div class="t-title tabular">${pct(d.score)}</div></div>
          <div><div class="t-label upper muted">Typical here</div>
            <div class="t-title tabular muted">${pct(d.cohortMedian)}</div></div>
          <div><div class="t-label upper muted">Matching passages</div>
            <div class="t-title tabular">${total}</div></div>
          <div><div class="t-label upper muted">Lines involved</div>
            <div class="t-title tabular">${d.left.matchedLines} <span class="subtle">/</span> ${d.right.matchedLines}</div></div>
        </div>
        <p class="t-body muted" style="margin-top:var(--sm);max-width:78ch">
          ${containmentSentence({
            leftLabel: d.left.student, rightLabel: d.right.student,
            containmentLeftInRight: d.containmentLeftInRight,
            containmentRightInLeft: d.containmentRightInLeft })}
        </p>
        <details class="disclosure">
          <summary>Show the underlying numbers</summary>
          <div class="content mono">
            similarity ${d.score.toFixed(4)} ·
            modified z ${d.modifiedZ === null ? "—" : d.modifiedZ.toFixed(2)} ·
            shared fingerprints ${d.sharedFingerprints} ·
            containment ${d.containmentLeftInRight.toFixed(3)} / ${d.containmentRightInLeft.toFixed(3)} ·
            cohort median ${d.cohortMedian.toFixed(4)}, MAD ${d.cohortMad.toFixed(4)} ·
            flag threshold ${d.reviewThreshold.toFixed(3)}
          </div>
        </details>
      </div>
    </div>

    ${evidencePanel(d)}

    <div class="card" style="margin-bottom:var(--md)"><div class="card-body">
      <p class="t-body" style="margin-bottom:var(--sm)">${COMPARE_INSTRUCTIONS}</p>
      <div class="match-legend">
        <span><span class="sample"></span> shared passage</span>
        <span><span class="sample now"></span> the one you are on</span>
        <span class="grow"></span>
        <span>${MATCH_EXPLANATION}</span>
      </div>
    </div></div>

    <div class="diff-grid">
      ${pane("left", d.left)}
      ${pane("right", d.right)}
    </div>`;

  paintIcons($("compare-content"));
  $("back-to-pairs").addEventListener("click", () => showView("pairs"));
  $("match-prev").addEventListener("click", () => stepMatch(-1));
  $("match-next").addEventListener("click", () => stepMatch(1));

  wireSyncScroll();
  updateMatchState(true);
}

/**
 * Why this pair was flagged, in terms of the code itself.
 *
 * This is the answer to the question a marker actually has. A similarity score says
 * how much two files share; it does not say *what* they share. Naming the routines on
 * both sides and listing the identifiers that were swapped turns a number into a
 * finding someone can act on — or dismiss, having seen the basis for it.
 */
function evidencePanel(d) {
  const e = d.evidence;
  const routines = e.routines.filter((r) => r.sharedFragments >= 2);
  const leftName = d.left.student, rightName = d.right.student;

  if (!routines.length && !e.renames.length) {
    return `<div class="card" style="margin-bottom:var(--md)"><div class="card-body">
      <h3 class="t-title-sm" style="margin-bottom:var(--xs)">What these two share</h3>
      <p class="t-body muted">The overlap is scattered rather than concentrated in any one
        routine — often a sign of shared idiom rather than shared work.</p></div></div>`;
  }

  const routineRows = routines.slice(0, 8).map((r) => `
    <div class="routine-line ${r.leftName === r.rightName ? "same" : ""}">
      <span class="side">${esc(r.leftName)}<span class="ln"> · line ${r.leftStartLine}</span></span>
      <span class="joiner">${r.leftName === r.rightName ? "same name" : "&harr;"}</span>
      <span class="side right">${esc(r.rightName)}<span class="ln"> · line ${r.rightStartLine}</span></span>
    </div>`).join("");

  const renameChips = e.renames.slice(0, 12).map((r) => `
    <span class="rename" title="${esc(r.from)} in ${esc(leftName)}'s file is ${esc(r.to)} in ${esc(rightName)}'s, ${r.occurrences} time(s)">
      <span class="from">${esc(r.from)}</span>
      <span aria-hidden="true">&rarr;</span>
      <span class="to">${esc(r.to)}</span>
      ${r.occurrences > 1 ? `<span class="n">${r.occurrences}×</span>` : ""}
    </span>`).join("");

  return `<div class="card" style="margin-bottom:var(--md)">
    <div class="card-head"><h3 class="t-title-sm">Why this pair came up</h3>
      <span class="t-label muted">the evidence, in the code itself</span></div>
    <div class="card-body">
      <div class="evidence-grid">
        <div>
          <h4 class="t-label upper muted" style="margin-bottom:var(--xs)">Which routines match</h4>
          <p class="t-body" style="margin-bottom:var(--sm)">
            ${esc(routineSentence(routines, leftName, rightName))}</p>
          <div class="routine-map">${routineRows}</div>
          ${routines.length > 8 ? `<p class="t-label muted" style="margin-top:var(--xs)">
            and ${routines.length - 8} more</p>` : ""}
          <p class="t-label muted" style="margin-top:var(--sm);display:flex;gap:6px">
            <span style="flex-shrink:0">${icon("info", 13)}</span>
            <span>${esc(leftName)}'s routine on the left, ${esc(rightName)}'s on the right.</span></p>
        </div>
        <div>
          <h4 class="t-label upper muted" style="margin-bottom:var(--xs)">What was renamed</h4>
          <p class="t-body" style="margin-bottom:var(--sm)">${esc(renameSentence(e, leftName, rightName))}</p>
          ${e.renames.length ? `<div class="rename-list">${renameChips}</div>` : ""}
          ${e.renames.length > 12 ? `<p class="t-label muted" style="margin-top:var(--xs)">
            and ${e.renames.length - 12} more</p>` : ""}
          <p class="t-label muted" style="margin-top:var(--sm)">
            Read as <em>${esc(leftName)}'s name &rarr; ${esc(rightName)}'s name</em>.</p>
        </div>
      </div>
      <details class="disclosure"><summary>How this was worked out</summary>
        <div class="content">${esc(EVIDENCE_CAVEAT)}
          <span class="mono"> · ${e.renamedTokens} renamed / ${e.identicalTokens} identical
          identifier uses across ${d.sharedFingerprints} matching fragments.</span></div>
      </details>
    </div></div>`;
}

function pane(side, file) {
  return `<div class="card code-pane">
    <div class="pane-head">
      <div class="row" style="gap:var(--xs);min-width:0">
        <span class="muted" data-icon="person" data-size="16"></span>
        <span class="t-label strong nowrap">${esc(file.student)}</span>
        <span class="t-label muted mono" style="overflow:hidden;text-overflow:ellipsis">${esc(file.filename)}</span>
      </div>
      <span class="chip chip-neutral nowrap">${file.matchedLines} of ${file.lineCount} lines</span>
    </div>
    <div class="pane-body">
      <div class="minimap" id="minimap-${side}" title="Where the matches sit in this file"></div>
      <div class="code-scroll" id="code-${side}" data-side="${side}">
        ${renderSource(file.source, file.spans)}
      </div>
    </div>
  </div>`;
}

function renderSource(source, spans) {
  const index = new Map();
  spans.forEach((s, i) => { for (let l = s.startLine; l <= s.endLine; l++) index.set(l, i); });
  return source.split("\n").map((text, i) => {
    const no = i + 1;
    const match = index.has(no);
    return `<div class="code-line${match ? " highlight-match" : ""}"` +
           `${match ? ` data-match="${index.get(no)}"` : ""} id="ln-${no}">` +
           `<span class="line-num">${no}</span>` +
           `<span class="line-content">${esc(text) || " "}</span></div>`;
  }).join("");
}

/** Scrolling one pane moves the other proportionally, so matches stay level. */
function wireSyncScroll() {
  const panes = [$("code-left"), $("code-right")];
  panes.forEach((pane, i) => {
    pane.addEventListener("scroll", () => {
      if (compare.syncing) return;
      compare.syncing = true;
      const other = panes[1 - i];
      const range = pane.scrollHeight - pane.clientHeight;
      const otherRange = other.scrollHeight - other.clientHeight;
      if (range > 0 && otherRange > 0) {
        other.scrollTop = (pane.scrollTop / range) * otherRange;
      }
      drawMinimaps();
      requestAnimationFrame(() => { compare.syncing = false; });
    }, { passive: true });
  });
  drawMinimaps();
}

function stepMatch(delta) {
  const total = compare.matches.length;
  if (!total) return;
  compare.index = (compare.index + delta + total) % total;
  updateMatchState(false);
}

function updateMatchState(initial) {
  const total = compare.matches.length;
  $("match-counter").textContent = total ? `Match ${compare.index + 1} of ${total}` : "No matches";

  document.querySelectorAll("#compare-content .code-line.match-current")
    .forEach((el) => el.classList.remove("match-current"));
  if (!total) { drawMinimaps(); return; }

  const current = compare.matches[compare.index];
  [["left", current.left], ["right", current.right]].forEach(([side, span]) => {
    const pane = $("code-" + side);
    for (let l = span.startLine; l <= span.endLine; l++) {
      const line = pane.querySelector(`#ln-${l}`);
      if (line) line.classList.add("match-current");
    }
    if (!initial) {
      const first = pane.querySelector(`#ln-${span.startLine}`);
      if (first) {
        // Park the match a third down the pane rather than at the very top, so the
        // reader can see what leads into it.
        compare.syncing = true;
        pane.scrollTop = first.offsetTop - pane.clientHeight / 3;
        requestAnimationFrame(() => { compare.syncing = false; });
      }
    }
  });
  drawMinimaps();
}

/** A scaled bar per pane showing every match and where the viewport currently is. */
function drawMinimaps() {
  const d = compare.detail;
  if (!d) return;
  [["left", d.left], ["right", d.right]].forEach(([side, file]) => {
    const map = $("minimap-" + side);
    const pane = $("code-" + side);
    if (!map || !pane) return;
    const lines = Math.max(1, file.lineCount);
    const segs = file.spans.map((s, i) => {
      const top = ((s.startLine - 1) / lines) * 100;
      const height = Math.max(1.2, ((s.endLine - s.startLine + 1) / lines) * 100);
      return `<div class="seg${i === compare.index ? " current" : ""}" ` +
             `style="top:${top.toFixed(2)}%;height:${height.toFixed(2)}%"></div>`;
    }).join("");
    const frac = pane.scrollHeight > 0 ? pane.clientHeight / pane.scrollHeight : 1;
    const at = pane.scrollHeight > 0 ? pane.scrollTop / pane.scrollHeight : 0;
    map.innerHTML = segs +
      `<div class="viewport" style="top:${(at * 100).toFixed(2)}%;height:${(frac * 100).toFixed(2)}%"></div>`;
  });
}

/** n / p step through matches; Escape returns to the list. */
document.addEventListener("keydown", (e) => {
  if (!$("view-compare").classList.contains("active")) return;
  if (e.target.matches("input, textarea")) return;
  if (e.key === "n" || e.key === "ArrowDown") { e.preventDefault(); stepMatch(1); }
  if (e.key === "p" || e.key === "ArrowUp") { e.preventDefault(); stepMatch(-1); }
  if (e.key === "Escape") showView("pairs");
});

/* ==========================================================================
   Boot
   ========================================================================== */
(async function boot() {
  paintIcons(document);
  renderContext();
  const remembered = localStorage.getItem(STORAGE_KEY);
  if (remembered && await openAssignment(remembered).catch(() => false)) return;
  renderOverview();
  renderRecent();
})();
