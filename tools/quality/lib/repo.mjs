/**
 * Repository access for the quality gate: tracked files, the files this
 * change touched, and a file's text before the change. Read-only; never
 * writes to the working tree.
 *
 * "This change" = HEAD^1 → working tree. In CI that is the PR merge commit
 * against its base (or the squash commit on main); locally it is the last
 * commit plus anything not yet committed.
 */
import { execFileSync } from 'node:child_process';
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

export const ROOT = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..', '..', '..');

function git(args) {
  try {
    return execFileSync('git', args, { cwd: ROOT, encoding: 'utf8', maxBuffer: 256 << 20, stdio: ['ignore', 'pipe', 'ignore'] });
  } catch {
    return null;
  }
}

export function trackedFiles() {
  const out = git(['ls-files', '-z']);
  if (out == null) throw new Error('git ls-files failed: run from inside the repository');
  return out.split('\0').filter(Boolean).filter((f) => fs.existsSync(path.join(ROOT, f)));
}

/**
 * Files changed by this change, as Map(path now → path at base, or null when added).
 * Returns null when there is no parent commit to compare with.
 */
export function changedFiles() {
  const out = git(['diff', '--name-status', '-z', '-M', 'HEAD^1']);
  if (out == null) return null;
  const parts = out.split('\0');
  const changed = new Map();
  for (let i = 0; i < parts.length && parts[i];) {
    const status = parts[i];
    if (status.startsWith('R') || status.startsWith('C')) {
      changed.set(parts[i + 2], parts[i + 1]);
      i += 3;
    } else {
      changed.set(parts[i + 1], status === 'A' ? null : parts[i + 1]);
      i += 2;
    }
  }
  return changed;
}

export function read(file) {
  try {
    return fs.readFileSync(path.join(ROOT, file), 'utf8');
  } catch {
    return null;
  }
}

/** The file's text before this change (null when it did not exist). */
export function readAtBase(file) {
  return file ? git(['show', `HEAD^1:${file}`]) : null;
}
