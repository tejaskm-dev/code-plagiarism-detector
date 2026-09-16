"use strict";

/**
 * Inline SVG icons.
 *
 * The mockups use Material Symbols, which is a webfont. A font is a large binary
 * dependency for a dozen glyphs and needs either a CDN (ruled out) or ~300KB checked
 * into the repo. These are hand-picked equivalents drawn as paths instead: a few KB,
 * crisp at any size, no loading state, and nothing to fetch at runtime.
 */
const ICONS = {
  shield:      '<path d="M12 2 4 5v6c0 5 3.4 9.4 8 11 4.6-1.6 8-6 8-11V5l-8-3z"/>',
  plus:        '<path d="M12 5v14M5 12h14" stroke-width="2" stroke-linecap="round"/>',
  dashboard:   '<path d="M4 4h7v7H4zM13 4h7v4h-7zM13 10h7v10h-7zM4 13h7v7H4z"/>',
  description: '<path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8l-6-6z"/><path d="M14 2v6h6" fill="none" stroke-width="1.5"/>',
  compare:     '<path d="M17 3l4 4-4 4V8H9V6h8V3zM7 13v3h8v2H7v3l-4-4 4-4z"/>',
  code:        '<path d="m8 6-6 6 6 6M16 6l6 6-6 6" fill="none" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"/>',
  upload:      '<path d="M12 16V4m0 0L7 9m5-5 5 5" fill="none" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"/><path d="M4 17v2a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2v-2" fill="none" stroke-width="2" stroke-linecap="round"/>',
  play:        '<path d="M7 4.5v15l13-7.5z"/>',
  history:     '<path d="M12 4a8 8 0 1 0 8 8" fill="none" stroke-width="2" stroke-linecap="round"/><path d="M12 7v5l3 2" fill="none" stroke-width="2" stroke-linecap="round"/><path d="M4 4v5h5" fill="none" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"/>',
  flag:        '<path d="M5 21V4m0 0h11l-2 4 2 4H5" fill="none" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"/>',
  chart:       '<path d="M4 20V10M10 20V4M16 20v-7M22 20H2" fill="none" stroke-width="2" stroke-linecap="round"/>',
  warning:     '<path d="M12 3 2 20h20L12 3z" fill="none" stroke-width="2" stroke-linejoin="round"/><path d="M12 9v5M12 17.5v.5" fill="none" stroke-width="2" stroke-linecap="round"/>',
  check:       '<path d="m4 12 5 5L20 6" fill="none" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round"/>',
  info:        '<circle cx="12" cy="12" r="9" fill="none" stroke-width="2"/><path d="M12 11v5M12 7.5v.5" fill="none" stroke-width="2" stroke-linecap="round"/>',
  person:      '<circle cx="12" cy="8" r="4"/><path d="M4 21a8 8 0 0 1 16 0z"/>',
  arrow:       '<path d="M5 12h14m-6-6 6 6-6 6" fill="none" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"/>',
  edit:        '<path d="M4 20h4L20 8l-4-4L4 16v4z" fill="none" stroke-width="2" stroke-linejoin="round"/>',
  trash:       '<path d="M4 7h16M9 7V4h6v3M6 7l1 13h10l1-13" fill="none" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"/>',
  search:      '<circle cx="11" cy="11" r="7" fill="none" stroke-width="2"/><path d="m20 20-4.5-4.5" fill="none" stroke-width="2" stroke-linecap="round"/>',
  inbox:       '<path d="M3 13h5l1 3h6l1-3h5M4 5h16l1 8v6a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-6l1-8z" fill="none" stroke-width="1.8" stroke-linejoin="round"/>',
};

/** Renders an icon into every [data-icon] element under root. */
function paintIcons(root) {
  (root || document).querySelectorAll("[data-icon]").forEach((host) => {
    const name = host.dataset.icon;
    if (!ICONS[name] || host.dataset.painted === name) return;
    const size = host.dataset.size || 20;
    host.innerHTML =
      `<svg viewBox="0 0 24 24" width="${size}" height="${size}" fill="currentColor" ` +
      `stroke="currentColor" aria-hidden="true" focusable="false">${ICONS[name]}</svg>`;
    host.dataset.painted = name;
    host.style.display = "inline-flex";
    host.style.alignItems = "center";
  });
}

/** Icon markup for use inside template strings. */
function icon(name, size) {
  if (!ICONS[name]) return "";
  return `<svg viewBox="0 0 24 24" width="${size || 18}" height="${size || 18}" ` +
         `fill="currentColor" stroke="currentColor" aria-hidden="true" ` +
         `focusable="false" style="vertical-align:-.15em">${ICONS[name]}</svg>`;
}
