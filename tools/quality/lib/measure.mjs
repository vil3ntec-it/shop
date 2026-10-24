/**
 * Pure measurements on file text (no I/O), shared by the checks and tests.
 */

/** Line count as `wc -l` reports it. */
export function lineCount(text) {
  if (!text) return 0;
  let n = 0;
  for (let i = text.indexOf('\n'); i !== -1; i = text.indexOf('\n', i + 1)) n++;
  return n;
}

/** Comment-only lines blanked (line numbers kept), as HardcodedColorsTest does. */
export function codeOnly(text) {
  return text
    .split('\n')
    .map((line) => (/^\s*(\/\/|\/\*|\*)/.test(line) ? '' : line))
    .join('\n');
}

/** Matches of a global regex in code (comments ignored), optionally confirmed by `confirm(code, index)`. */
export function findAll(text, pattern, confirm) {
  if (!text) return [];
  const code = codeOnly(text);
  const hits = [];
  for (const m of code.matchAll(pattern)) {
    if (confirm && !confirm(code, m.index)) continue;
    hits.push({ line: lineCount(code.slice(0, m.index)) + 1, text: m[0] });
  }
  return hits;
}

/** Duplicate migration numbers: [{number, files}] for every number used more than once. */
export function duplicateMigrations(files, nameRe) {
  const byNumber = new Map();
  for (const f of files) {
    const m = nameRe.exec(f);
    if (!m) continue;
    const n = Number(m[1]);
    byNumber.set(n, [...(byNumber.get(n) || []), f]);
  }
  return [...byNumber]
    .filter(([, list]) => list.length > 1)
    .map(([number, list]) => ({ number, files: list.sort() }))
    .sort((a, b) => a.number - b.number);
}
