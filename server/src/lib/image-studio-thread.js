'use strict';
/**
 * رشتهٔ جدای پردازشِ یک عکس (‎lib/image-studio-jobs.js‎ ‎isolate‎): رمزگشایی و پردازشِ سنگین بیرون از حلقهٔ
 * رویدادِ سرور، با سقفِ حافظه (`resourceLimits`) و مهلتِ کارگر. فقط محاسبه — نه پایگاه، نه شبکه، نه فایل.
 */
const { parentPort, workerData } = require('worker_threads');
const studio = require('./image-studio');

try {
  const { buf, info, request, maxBytes } = workerData;
  const r = studio.render(Buffer.from(buf), info, request, maxBytes);
  parentPort.postMessage({ ok: true, buf: r.buf, width: r.width, height: r.height, derivatives: r.derivatives, report: r.report });
} catch (e) {
  //  ApiError ِ ۴xx (نوعِ پردازش‌نشدنی، خروجیِ بزرگ) همیشگی است؛ بقیه گذرا و تلاشِ دوباره دارند
  parentPort.postMessage({ ok: false, code: e.code || 'studio_failed', status: Number(e.status) || 0, message: String(e.message || '').slice(0, 200) });
}
