/**
 * Ratchet logic (pure): compare measured values with baseline caps that may
 * only go down — the HardcodedColorsTest pattern, generalised.
 */

/**
 * Per-file caps. A file not in `caps` is allowed `defaultCap`.
 * @param {Record<string, number>} caps
 * @param {Map<string, number>} current  value per file now
 * @returns {{over: Array<{file:string, value:number, cap:number, listed:boolean}>, lowerable: Array<{file:string, value:number, cap:number}>}}
 */
export function checkPerFile(caps, current, defaultCap) {
  const over = [];
  const lowerable = [];
  for (const [file, value] of current) {
    const listed = Object.prototype.hasOwnProperty.call(caps, file);
    const cap = listed ? caps[file] : defaultCap;
    if (value > cap) over.push({ file, value, cap, listed });
  }
  for (const [file, cap] of Object.entries(caps)) {
    const value = current.get(file) ?? 0;
    if (value < cap) lowerable.push({ file, value, cap });
  }
  over.sort((a, b) => b.value - b.cap - (a.value - a.cap) || a.file.localeCompare(b.file));
  lowerable.sort((a, b) => a.file.localeCompare(b.file));
  return { over, lowerable };
}

/** Lower-only update of per-file caps: entries never rise, and drop once at or under `defaultCap`. */
export function lowerPerFile(caps, current, defaultCap) {
  const next = {};
  for (const [file, cap] of Object.entries(caps)) {
    const value = Math.min(cap, current.get(file) ?? 0);
    if (value > defaultCap) next[file] = value;
  }
  return sortKeys(next);
}

/** Full snapshot of per-file caps (only for the first baseline or an explained reset). */
export function snapshotPerFile(current, defaultCap) {
  const next = {};
  for (const [file, value] of current) if (value > defaultCap) next[file] = value;
  return sortKeys(next);
}

/** Lower-only update of named totals. */
export function lowerTotals(caps, totals) {
  const next = {};
  for (const [id, cap] of Object.entries(caps)) next[id] = Math.min(cap, totals[id] ?? 0);
  return next;
}

/**
 * Who caused a violation? `introduced` when this change touched the file and
 * made the value bigger than before (or added the file).
 */
export function attribute(file, value, changed, valueAtBase) {
  if (!changed) return 'unknown';
  if (!changed.has(file)) return 'pre-existing';
  const before = valueAtBase(changed.get(file));
  return before == null || value > before ? 'introduced' : 'pre-existing';
}

export function sortKeys(obj) {
  return Object.fromEntries(Object.entries(obj).sort(([a], [b]) => a.localeCompare(b)));
}
