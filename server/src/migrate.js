'use strict';
/**
 * مهاجرت‌های ساده و بی‌خطر.
 * هر مهاجرت فقط وقتی اجرا می‌شود که لازم باشد؛ داده‌ی موجود دست نمی‌خورد.
 */
function columnExists(db, table, column) {
  return db.prepare(`PRAGMA table_info(${table})`).all().some(c => c.name === column);
}

function addColumn(db, table, column, definition) {
  if (columnExists(db, table, column)) return false;
  db.exec(`ALTER TABLE ${table} ADD COLUMN ${column} ${definition}`);
  return true;
}

function run(db) {
  const applied = [];
  // دوره آزمایشی روی هر حساب
  if (addColumn(db, 'users', 'trial_started_at', 'INTEGER')) applied.push('users.trial_started_at');
  if (addColumn(db, 'users', 'trial_ends_at', 'INTEGER')) applied.push('users.trial_ends_at');
  if (addColumn(db, 'users', 'trial_used', 'INTEGER NOT NULL DEFAULT 0')) applied.push('users.trial_used');
  return applied;
}

module.exports = { run, columnExists, addColumn };
