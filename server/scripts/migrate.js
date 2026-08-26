#!/usr/bin/env node
'use strict';
const { run } = require('../src/migrate');
const { closeDb } = require('../src/db');

run({ log: (m) => console.log(m) })
  .then(async (done) => {
    console.log(done.length ? `✔ ${done.length} Migration اجرا شد` : '✔ دیتابیس به‌روز است');
    await closeDb();
  })
  .catch(async (err) => {
    console.error('✖', err.message);
    await closeDb();
    process.exit(1);
  });
