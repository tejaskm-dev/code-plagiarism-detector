"use strict";

/**
 * The plain-language layer.
 *
 * Everything a person reads on screen is written here, so the voice stays consistent
 * and can be reviewed in one place. Three rules:
 *
 *   1. Write like a colleague explaining, not like documentation. Short sentences.
 *      No jargon that a person teaching an intro course would have to look up.
 *   2. Never turn a statistic into an accusation. The tool knows two files are
 *      unusually alike for this class. It does not know that anyone copied.
 *   3. Never hide the number. Plain words sit in front of the statistic, never in
 *      place of it — there is a "show the numbers" disclosure on every screen.
 *
 * Vocabulary decisions, applied everywhere: "class" not cohort, "shared code" not
 * similarity score, "matching passages" not fingerprints, "needs a look" not flagged.
 * The words tier, MAD, z-score and containment appear only inside disclosures.
 */

/* ------------------------------------------------------------------ verdicts */

function verdictFor(score, flagged) {
  if (score >= 0.9) {
    return {
      tone: "flagged", short: "Nearly identical",
      label: "These are nearly the same program",
      meaning: "Set aside the names, spacing and comments and almost nothing is left that " +
               "differs. Two people working separately do not land here.",
    };
  }
  if (score >= 0.7) {
    return {
      tone: "flagged", short: "Very close",
      label: "These two share most of their structure",
      meaning: "Far more is common between these files than two people usually produce on " +
               "their own. Worth reading side by side.",
    };
  }
  if (score >= 0.45) {
    return {
      tone: "warn", short: "Some overlap",
      label: "There is a real amount of shared work here",
      meaning: "This could be a copy someone partly rewrote, or two students who followed " +
               "the same tutorial. The files will tell you which.",
    };
  }
  if (flagged) {
    return {
      tone: "warn", short: "Stands out here",
      label: "Not much overlap, but more than anyone else in this class",
      meaning: "The shared part is small. It only stands out because everyone else in this " +
               "class overlaps even less. On a tightly grouped assignment that happens to " +
               "pairs who did nothing wrong.",
    };
  }
  return {
    tone: "ok", short: "Normal",
    label: "Normal for this assignment",
    meaning: "Students solving the same problem share this much routinely. Nothing here " +
             "asks for your attention.",
  };
}

/* ------------------------------------------------------- the opening sentence */

/**
 * The whole result as one short paragraph, before any number appears.
 *
 * This is the first thing on the screen because it is the only thing most readers
 * need. Everything below it is evidence for this paragraph.
 */
function summaryParagraph(cohort, pairs) {
  const typical = Math.round(cohort.median * 100);
  const top = pairs.length ? Math.max(...pairs.map((p) => p.score)) : 0;
  const flagged = cohort.flaggedCount;

  const opening = `${cohort.submissionCount} submissions, compared against each other in ` +
                  `${cohort.pairCount} combinations.`;

  if (flagged === 0) {
    return `${opening} Nothing stood out. The closest two students share ` +
           `${Math.round(top * 100)}% of their structure, which is in step with everyone ` +
           `else. Worth remembering this only compares your students with each other — if ` +
           `the whole class had worked from the same outside source, that would look normal here.`;
  }

  const closest = top >= 0.9
    ? " The closest two are practically the same program."
    : top >= 0.7
      ? " The closest two share most of their structure."
      : "";

  return `${opening} Most students overlap by about ${typical}%, which is the ordinary ` +
         `level for this assignment. ${flagged} pair${flagged === 1 ? "" : "s"} ` +
         `share${flagged === 1 ? "s" : ""} noticeably more than that.${closest}`;
}

/** One line placing a single score next to the class it was judged in. */
function scoreInContext(score, cohort) {
  const times = cohort.median > 0 ? score / cohort.median : null;
  if (times === null) {
    return `They share ${Math.round(score * 100)}% of their structure. Everyone else in this ` +
           `class shares essentially nothing.`;
  }
  if (times >= 1.15) {
    return `They share ${Math.round(score * 100)}% of their structure — roughly ` +
           `${times.toFixed(1)} times what a typical pair in this class shares ` +
           `(${Math.round(cohort.median * 100)}%).`;
  }
  if (times <= 0.85) {
    return `They share ${Math.round(score * 100)}%, which is less than the typical pair ` +
           `in this class (${Math.round(cohort.median * 100)}%).`;
  }
  return `They share ${Math.round(score * 100)}%, about the same as any other pair in this ` +
         `class (${Math.round(cohort.median * 100)}%).`;
}

/** How far above ordinary a pair sits, as a plain multiple. */
function unusualness(score, cohort) {
  if (!cohort.median) return { text: "—", title: "The typical pair here shares nothing." };
  const times = score / cohort.median;
  return {
    text: `${times.toFixed(1)}×`,
    title: `${times.toFixed(1)} times what a typical pair in this class shares.`,
  };
}

/** Why the bar sits where it does, without naming a z-score. */
function thresholdSentence(cohort) {
  return `In this class, two students have to share about ` +
         `${Math.round(cohort.reviewThreshold * 100)}% before it counts as unusual. That ` +
         `figure comes from this class's own results, not a fixed rule — so an easy ` +
         `assignment where everyone's code looks alike does not put the whole room under suspicion.`;
}

const NOT_AN_ACCUSATION =
  "Being listed here means <strong>unusual next to the rest of this class</strong>. " +
  "It is a reason to open the files and decide for yourself. It is not a finding that " +
  "anyone copied.";

/* ------------------------------------------------------------ health of results */

function tierExplained(cohort) {
  if (cohort.tier === 1) {
    return {
      tone: "ok",
      heading: "These results can be trusted",
      plain: "Your students' work varies enough to compare meaningfully. Each pair was " +
             "measured against how alike a typical pair in this class is, so the bar moves " +
             "with the assignment instead of being a fixed percentage.",
      technical: `Tier 1 — ${cohort.tierLabel}. Median ${cohort.median.toFixed(4)}, ` +
                 `MAD ${cohort.mad.toFixed(4)}, flagged at |z| ≥ ${cohort.outlierThreshold}.`,
    };
  }
  if (cohort.tier === 2) {
    return {
      tone: "warn",
      heading: "Read this list carefully",
      plain: "More than half the pairs in this class scored exactly the same, which leaves " +
             "almost nothing to measure against. The fallback reacts to much smaller " +
             "differences, so a pair can end up on this list over a gap that would mean " +
             "nothing in a normal class. The top of the list is still worth your time; " +
             "treat the bottom of it as weak.",
      technical: `Tier 2 — ${cohort.tierLabel}. MAD collapsed to 0; MAE ${cohort.mae.toFixed(4)} used.`,
    };
  }
  return {
    tone: "warn",
    heading: "This analysis may not tell you anything",
    plain: "Every pair in this class came out identical, so there is nothing to compare " +
           "anyone against. That happens when an assignment has only one sensible answer — " +
           "and it also happens when everyone worked from the same source. This tool cannot " +
           "tell those two apart, so the ranking below carries no information. You will have " +
           "to read the work yourself.",
    technical: `Tier 3 — ${cohort.tierLabel}. No dispersion; absolute bar ${cohort.reviewThreshold.toFixed(2)}.`,
  };
}

/* ------------------------------------------------------------------ the pair */

function containmentSentence(pair) {
  const forward = pair.containmentLeftInRight;
  const backward = pair.containmentRightInLeft;
  if (Math.abs(forward - backward) < 0.12) {
    return "Each file covers about as much of the other, which is what you see when two " +
           "files are similar in size and content.";
  }
  const [inside, around, amount] = forward > backward
    ? [pair.leftLabel, pair.rightLabel, forward]
    : [pair.rightLabel, pair.leftLabel, backward];
  return `${Math.round(amount * 100)}% of ${inside}'s work turns up inside ${around}'s file, ` +
         `but not the other way round. That is the shape you get when a shorter piece of ` +
         `code has been dropped into a longer one.`;
}

const MATCH_EXPLANATION =
  "The highlighted lines are the parts these two files have in common. The comparison " +
  "ignores variable names, numbers, text and comments entirely — which is why renaming " +
  "things, reformatting, or shuffling functions around does not hide a match. It was never " +
  "looking at those.";

const COMPARE_INSTRUCTIONS =
  "Read the two files side by side. Use the arrows at the top right, or press " +
  "<kbd>n</kbd> and <kbd>p</kbd>, to jump between the parts they share.";

/* --------------------------------------------------------- what the tool is doing */

/**
 * The stages of a real analysis, in the order the engine performs them.
 *
 * Shown while an analysis runs. These are not invented steps for a progress bar — each
 * one names work that genuinely happens, and seeing them is most people's only chance
 * to learn what the tool actually does with their students' code.
 */
const ANALYSIS_STAGES = [
  { label: "Reading the submissions",        detail: "Loading every file in the batch." },
  { label: "Stripping out names and comments", detail: "So that renaming things cannot hide a copy." },
  { label: "Building a fingerprint of each file", detail: "A compact summary of each program's structure." },
  { label: "Setting aside shared starter code", detail: "Work everyone was given does not count against anyone." },
  { label: "Comparing every pair",           detail: "Each student against each other student." },
  { label: "Working out what is unusual here", detail: "Judged against this class, not a fixed threshold." },
];


/* ==========================================================================
   Higher-level readings

   The engine works pair by pair because that is how similarity is defined.
   A marker does not think that way. They think about students, and about
   groups — and a ring of four who all copied from each other is precisely
   the shape a list of pairs hides, because it appears as six unrelated rows.
   ========================================================================== */

/**
 * Collapses flagged pairs into the groups they imply.
 *
 * Two students belong to the same group if there is any chain of flagged pairs
 * connecting them. A group of four shows up in a pair list as six separate rows;
 * here it shows up as one finding, which is how it should be handled.
 */
function findGroups(pairs) {
  const parent = new Map();
  const find = (x) => { while (parent.get(x) !== x) { parent.set(x, parent.get(parent.get(x))); x = parent.get(x); } return x; };
  const add = (x) => { if (!parent.has(x)) parent.set(x, x); };

  const flagged = pairs.filter((p) => p.flagged);
  flagged.forEach((p) => { add(p.leftLabel); add(p.rightLabel); });
  flagged.forEach((p) => {
    const a = find(p.leftLabel), b = find(p.rightLabel);
    if (a !== b) parent.set(a, b);
  });

  const groups = new Map();
  parent.forEach((_, student) => {
    const root = find(student);
    if (!groups.has(root)) groups.set(root, { members: [], links: [] });
    groups.get(root).members.push(student);
  });
  flagged.forEach((p) => groups.get(find(p.leftLabel)).links.push(p));

  return [...groups.values()]
    .map((g) => {
      const scores = g.links.map((l) => l.score);
      const possible = (g.members.length * (g.members.length - 1)) / 2;
      return {
        members: g.members.sort(),
        links: g.links.sort((a, b) => b.score - a.score),
        strongest: Math.max(...scores),
        weakest: Math.min(...scores),
        // A group where every possible pairing is flagged behaves very differently
        // from a chain, and the difference matters when deciding what happened.
        complete: g.links.length >= possible,
        density: g.links.length / possible,
      };
    })
    .sort((a, b) => b.members.length - a.members.length || b.strongest - a.strongest);
}

/** How a group should be read, given its size and how tightly connected it is. */
function describeGroup(group) {
  const n = group.members.length;
  if (n === 2) {
    return "Two students whose work overlaps far more than the rest of the class.";
  }
  if (group.complete) {
    return `All ${n} of these students' files match each other, every combination. ` +
           `Work that passed between all of them, or that all of them took from one ` +
           `outside source, both look like this.`;
  }
  return `${n} students linked in a chain — not everyone matches everyone. Often one ` +
         `person's work spread outward. The strongest links are listed first.`;
}

/** Per-student rollup: the unit a marker actually acts on. */
function perStudent(pairs, files) {
  const byStudent = new Map();
  (files || []).forEach((f) => byStudent.set(f.student,
    { student: f.student, filename: f.filename, unidentified: f.unidentified,
      flaggedWith: [], highest: 0, partners: [] }));

  pairs.forEach((p) => {
    [[p.leftLabel, p.rightLabel], [p.rightLabel, p.leftLabel]].forEach(([who, other]) => {
      const row = byStudent.get(who);
      if (!row) return;
      row.highest = Math.max(row.highest, p.score);
      if (p.flagged) { row.flaggedWith.push(other); row.partners.push(p); }
    });
  });

  return [...byStudent.values()]
    .sort((a, b) => b.flaggedWith.length - a.flaggedWith.length || b.highest - a.highest);
}

/** What a student's involvement means, in a sentence. */
function describeStudent(row) {
  if (row.flaggedWith.length === 0) {
    return "Nothing about this submission stands out.";
  }
  if (row.flaggedWith.length === 1) {
    return `Overlaps unusually with ${row.flaggedWith[0]}.`;
  }
  return `Overlaps unusually with ${row.flaggedWith.length} other students: ` +
         `${row.flaggedWith.slice(0, 4).join(", ")}` +
         `${row.flaggedWith.length > 4 ? ", and others" : ""}. A submission that matches ` +
         `several people at once is usually either the source everyone worked from, or ` +
         `something everyone was given.`;
}

/* ------------------------------------------------------- reading the evidence */

/** Turns matching routines into a sentence a marker can act on. */
function routineSentence(routines, leftName, rightName) {
  const real = routines.filter((r) => r.sharedFragments >= 2);
  if (!real.length) return "";
  const sameName = real.filter((r) => r.leftName === r.rightName).length;
  const lead = real.length === 1
    ? `The shared code sits in one place:`
    : `The shared code is spread across ${real.length} routines:`;
  const tail = sameName === real.length && real.length > 1
    ? ` Every one of them carries the same name in both files.`
    : sameName === 0
      ? ` None of them share a name — the routines were renamed as well as their contents.`
      : "";
  return lead + tail;
}

/** The renaming evidence, stated the way a person would state it. */
function renameSentence(evidence, leftName, rightName) {
  const total = evidence.renamedTokens + evidence.identicalTokens;
  if (total === 0) return "";
  if (evidence.renames.length === 0) {
    return `Inside the matching code, every variable is spelled identically in both files. ` +
           `Nothing was renamed at all.`;
  }
  const share = Math.round((evidence.renamedTokens / total) * 100);
  return `Inside the matching code, ${share}% of the variable names differ while the code ` +
         `around them stays the same. That is the signature of a rename: the structure was ` +
         `kept and the labels were changed.`;
}

const EVIDENCE_CAVEAT =
  "Routine names are worked out by reading the code around each match, which is usually " +
  "right but can mislabel unusual formatting. The line numbers are exact.";

/*
 * Authorship, in words a reader can act on.
 *
 * The model returns a fraction of decision trees. That number means nothing to a
 * teacher on its own, and worse, it reads as a probability of guilt when it is not
 * one. Everything below exists to keep the reading honest: a high score describes
 * the *style* of the file, and regular style is exactly what a well-taught student
 * produces.
 */

const AI_NOT_PROOF =
  "This looks at writing habits, not at where the code came from. A careful student " +
  "who names things consistently and formats as they go produces the same pattern as " +
  "a generated file. Use this to decide what to ask about, never as the answer.";

function aiVerdictLabel(verdict) {
  return { generated: "Reads as generated", human: "Reads as hand-written",
           unsure: "Undecided", unmeasured: "Not assessed" }[verdict] || "Not assessed";
}

/** One sentence describing the whole batch, which is what a reader looks at first. */
function aiCohortSummary(ai, total) {
  if (!ai || !ai.measured) {
    return "No file in this batch could be assessed for authorship.";
  }
  const skipped = total - ai.measured;
  const tail = skipped > 0
    ? ` ${skipped} file${skipped === 1 ? " was" : "s were"} too short to assess.`
    : "";

  if (ai.generated === ai.measured) {
    return `Every one of the ${ai.measured} files assessed carries the regular, even ` +
      `style typical of generated code. A whole cohort landing here usually means the ` +
      `assignment was narrow enough that everyone converged — or that the batch really ` +
      `is machine-written.${tail}`;
  }
  if (ai.generated === 0) {
    return `None of the ${ai.measured} files assessed stand out as generated. They carry ` +
      `the irregularity of someone editing as they went.${tail}`;
  }
  return `${ai.generated} of ${ai.measured} files assessed read as generated, ` +
    `${ai.human} read as hand-written, and ${ai.unsure} sit too close to the middle to ` +
    `call either way.${tail}`;
}
