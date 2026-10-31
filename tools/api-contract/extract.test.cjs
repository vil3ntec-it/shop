'use strict';
/**
 * آزمونِ `extract.cjs` (پارسرِ تقریبیِ قراردادِ API).   node --test tools/api-contract/extract.test.cjs
 * خودِ مولد سرور را بالا می‌آورد و node_modules می‌خواهد؛ این آزمون بی وابستگی است.
 */
const test = require('node:test');
const assert = require('node:assert/strict');
const X = require('./extract.cjs');

test('splitArgs: regex با پرانتز، قالب با ${}، رشته با ویرگول', () => {
  const src = "router.get('/a,b', requirePermission('x.y'), wrap(async (req, res) => { const s = `${req.body.a}, (`; const r = /\\(/; res.json({ ok: s.match(r) }); }))";
  const args = X.splitArgs(src, src.indexOf('('));
  assert.equal(args.length, 3);
  assert.equal(args[0], "'/a,b'");
  assert.equal(args[1], "requirePermission('x.y')");
  assert.match(args[2], /^wrap\(async/);
});

test('callArgsAt: از خط و ستونِ stack frame به آرگومان‌ها', () => {
  const src = "const x = 1;\n  router.post('/p', h);\n";
  assert.deepEqual(X.callArgsAt(src, 2, 3), ["'/p'", 'h']);
  assert.deepEqual(X.callArgsAt(src, 2, 10), ["'/p'", 'h']);
});

test('fieldsOf: مستقیم، destructure، نامِ مستعار و کلیدِ رشته‌ای', () => {
  const t = "const { name, price: p = 0, ...rest } = req.body || {}; const b = req.body || {}; if (b.qty) {} "
    + "x(req.body?.note, req.body['bar-code']); const { q } = req.query; req.query.page";
  assert.deepEqual(X.fieldsOf(t, 'body'), ['bar-code', 'name', 'note', 'price', 'qty']);
  assert.deepEqual(X.fieldsOf(t, 'query'), ['page', 'q']);
});

test('shapeOf: کلیدهای پاسخ، وضعیت‌ها و کدهای خطا', () => {
  const t = "async (req, res) => { if (!req.body.id) throw badRequest('نه، این نه', 'bad_id'); "
    + "if (x) throw notFound(); if (y) throw new ApiError(409, 'dup', 'm'); "
    + 'res.status(201).json({ id, ok: true, ...rest, nested: { a: 1 } }); }';
  const s = X.shapeOf(t);
  assert.deepEqual(s.body, ['id']);
  assert.deepEqual(s.responseKeys, ['...', 'id', 'nested', 'ok']);
  assert.deepEqual(s.statuses, [201]);
  assert.deepEqual(s.errors, ['400 bad_id', '404 not_found', '409 dup']);
  assert.ok(s.found > 0);
});

test('namedFunctionText: handlerِ تعریف‌شده جدا در همان فایل', () => {
  const src = "async function listIt(req, res) { res.json({ items: [] }); }\nrouter.get('/', wrap(listIt));";
  assert.match(X.namedFunctionText(src, 'listIt'), /res\.json\(\{ items/);
  assert.equal(X.namedFunctionText(src, 'nope'), '');
});
