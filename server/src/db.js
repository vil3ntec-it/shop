'use strict';
const fs = require('fs');
const path = require('path');
const Database = require('better-sqlite3');
const config = require('./config');

let db = null;

function getDb() {
  if (db) return db;
  fs.mkdirSync(path.dirname(config.dbPath), { recursive: true });
  db = new Database(config.dbPath);
  db.pragma('journal_mode = WAL');
  db.pragma('foreign_keys = ON');
  db.exec(fs.readFileSync(path.join(__dirname, 'schema.sql'), 'utf8'));
  return db;
}

function closeDb() { if (db) { db.close(); db = null; } }

/** شناسه‌ی یکتا با پیشوند خوانا. */
function newId(prefix) {
  const { randomBytes } = require('crypto');
  return `${prefix}_${randomBytes(12).toString('hex')}`;
}

function now() { return Date.now(); }

/** حذف توکن‌های منقضی — در زمان راه‌اندازی و به‌صورت دوره‌ای صدا زده می‌شود. */
function pruneExpired() {
  const d = getDb();
  const cutoff = now();
  const t = d.prepare('DELETE FROM tokens WHERE expires_at < ?').run(cutoff);
  const a = d.prepare('DELETE FROM login_attempts WHERE created_at < ?')
    .run(cutoff - 24 * 60 * 60 * 1000);
  return { tokens: t.changes, attempts: a.changes };
}

module.exports = { getDb, closeDb, newId, now, pruneExpired };
