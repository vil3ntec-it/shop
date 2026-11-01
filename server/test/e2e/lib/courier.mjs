/**
 * برنامهٔ پیک در سطحِ پروتکل — همان صف و همان فرستادنِ گوشیِ پیک، سطر به سطرِ کاتلین:
 *
 *   `core/…/courier/CourierQueue.kt`   CourierFlow، CourierQueue.enqueue/batch/applyResults/
 *                                      projectedStatus و MissionView.forDisplay
 *   `data/CourierStore.kt`             CourierSync.flush: صف بسته به بسته ⇐ applyResults ⇐ «پاسخِ
 *                                      ناقص ⇒ بقیه بعداً» ⇐ کارت‌های active و done
 *   `ui/courier/CourierHome.kt`        record(): شناسهٔ `cev-<ULID>` و `from` = وضعیتی که روی کارت دیده شد
 *   `data/repo/CourierWire.kt`         بدنهٔ `POST /api/courier/events` (کلیدِ خالی فرستاده نمی‌شود)
 *
 * فهرست‌های دلیل، گذارها و سقفِ بسته از **خودِ** CourierQueue.kt خوانده می‌شوند (`KT`)، نه رونوشت؛
 * پس جابه‌جاییِ کاتلین این‌جا هم دیده می‌شود.
 */
import fs from 'node:fs';
import path from 'node:path';
import { core } from './native.mjs';

const ROOT = path.join(import.meta.dirname, '..', '..', '..', '..');
const SRC = fs.readFileSync(path.join(ROOT, 'core', 'src', 'commonMain', 'kotlin', 'ir', 'vil3ntec', 'tohid', 'core', 'courier', 'CourierQueue.kt'), 'utf8');
const quoted = (s) => [...s.matchAll(/"([^"]*)"/g)].map((m) => m[1]);
function strings(name) {
  const m = new RegExp(`val ${name}: [^=]+= (?:listOf|setOf)\\(([^)]*)\\)`).exec(SRC);
  if (!m) throw new Error(`CourierQueue.kt: ${name} پیدا نشد`);
  return quoted(m[1]);
}
function transitions() {
  const block = /val TRANSITIONS: [^=]+= mapOf\(([\s\S]*?)\n {2}\)/.exec(SRC);
  if (!block) throw new Error('CourierQueue.kt: TRANSITIONS پیدا نشد');
  const out = {};
  for (const m of block[1].matchAll(/"(\w+)" to (?:setOf\(([^)]*)\)|emptySet\(\))/g)) out[m[1]] = new Set(m[2] ? quoted(m[2]) : []);
  return out;
}
const pickupOnly = /val PICKUP_ONLY: [^=]+= setOf\(([^)]*)\)/.exec(SRC);

/** آن‌چه از کاتلین خوانده شد — برای سنجیدن با `delivery-states.js` ِ سرور */
export const KT = Object.freeze({
  REJECT_REASONS: strings('REJECT_REASONS'),
  FAIL_REASONS: strings('FAIL_REASONS'),
  CASH_REASONS: strings('CASH_REASONS'),
  COURIER_TARGETS: strings('COURIER_TARGETS'),
  LIVE: strings('LIVE'),
  STATUSES: strings('STATUSES'),
  TRANSITIONS: transitions(),
  PICKUP_ONLY: pickupOnly ? [...pickupOnly[1].matchAll(/"(\w+)" to "(\w+)"/g)].map((m) => `${m[1]}>${m[2]}`) : [],
  MAX_BATCH: Number(/const val MAX_BATCH = (\d+)/.exec(SRC)[1]),
  MAX_NOTE: Number(/const val MAX_NOTE = (\d+)/.exec(SRC)[1]),
});

/* ════════════════════════ CourierFlow ════════════════════════ */

export const isLive = (s) => KT.LIVE.includes(s);
export function canSend(from, to) {
  return KT.COURIER_TARGETS.includes(to) && (KT.TRANSITIONS[from] || new Set()).has(to) && !KT.PICKUP_ONLY.includes(`${from}>${to}`);
}
const reasonsFor = (type) => (type === 'rejected' ? KT.REJECT_REASONS : type === 'failed' ? KT.FAIL_REASONS : []);

export class CourierInputError extends Error { constructor(code, message) { super(message); this.code = code; } }

/* ════════════════════════ CourierQueue ════════════════════════ */

const EVENT_ID = /^[A-Za-z0-9_.:-]{8,128}$/;
const REASON = /^[a-z_]{1,40}$/;
export const latinDigits = (raw) => [...String(raw)].map((ch) => {
  if (ch >= '0' && ch <= '9') return ch;
  const fa = '۰۱۲۳۴۵۶۷۸۹'.indexOf(ch); if (fa >= 0) return String(fa);
  const ar = '٠١٢٣٤٥٦٧٨٩'.indexOf(ch); if (ar >= 0) return String(ar);
  return '';
}).join('');

/** `CourierQueue.enqueue` ⇒ [state, event] */
export function enqueue(state, eventId, assignmentId, type, deviceTime, { from = '', reason = '', note = '', cashCollectedMinor = null, cashReason = '', code = '' } = {}) {
  if (!EVENT_ID.test(eventId)) throw new CourierInputError('bad_event_id', 'شناسهٔ رویداد نامعتبر است');
  if (!assignmentId) throw new CourierInputError('assignment_not_found', 'مأموریت پیدا نشد');
  if (!KT.COURIER_TARGETS.includes(type)) throw new CourierInputError('bad_event_type', 'این کار با پیک نیست');
  if (from && !canSend(from, type)) throw new CourierInputError('invalid_transition', 'این تغییرِ وضعیت مجاز نیست');
  const r = reason.trim();
  const n = note.trim().slice(0, KT.MAX_NOTE);
  if (type === 'rejected' || type === 'failed') {
    if (!r) throw new CourierInputError('reason_required', 'دلیل لازم است');
    if (!reasonsFor(type).includes(r)) throw new CourierInputError('bad_reason', 'دلیل معتبر نیست');
    if (r === 'other' && !n) throw new CourierInputError('note_required', 'توضیح لازم است');
  } else if (r && !REASON.test(r)) throw new CourierInputError('bad_reason', 'دلیل معتبر نیست');
  const cr = cashReason.trim();
  if (cr && !KT.CASH_REASONS.includes(cr)) throw new CourierInputError('bad_reason', 'دلیلِ اختلافِ نقد معتبر نیست');
  if (state.pending.some((e) => e.eventId === eventId)) throw new CourierInputError('event_key_reused', 'این شناسه در صف هست');
  const ev = {
    eventId, assignmentId, type, deviceTime: Math.max(0, deviceTime), deviceSeq: state.nextSeq, from, reason: r, note: n,
    cashCollectedMinor: type === 'delivered' ? cashCollectedMinor : null,
    cashReason: type === 'delivered' ? cr : '',
    code: type === 'delivered' ? latinDigits(code).slice(0, 12) : '',
  };
  return [{ ...state, nextSeq: state.nextSeq + 1, pending: [...state.pending, ev] }, ev];
}

export const batch = (state, max = KT.MAX_BATCH) => [...state.pending].sort((a, b) => a.deviceSeq - b.deviceSeq).slice(0, Math.min(Math.max(1, max), KT.MAX_BATCH));

/** `CourierQueue.applyResults` */
export function applyResults(state, sent, results) {
  const byId = new Map(results.filter((r) => r.eventId).map((r) => [r.eventId, r]));
  const done = new Set();
  const rejected = [];
  sent.forEach((ev, i) => {
    const pos = results[i];
    const r = byId.get(ev.eventId) || (pos && (!pos.eventId || pos.eventId === ev.eventId) ? pos : null);
    if (!r) return;
    if (['applied', 'duplicate', 'unchanged'].includes(r.status)) done.add(ev.eventId);
    else if (r.status === 'rejected') { done.add(ev.eventId); rejected.push({ event: { ...ev, code: '' }, code: r.code || 'rejected', message: r.message || '' }); }
  });
  if (!done.size) return state;
  return { ...state, pending: state.pending.filter((e) => !done.has(e.eventId)), rejected: [...state.rejected, ...rejected].slice(-50) };
}

/** `CourierQueue.projectedStatus` */
export function projectedStatus(serverStatus, assignmentId, state) {
  let s = serverStatus;
  for (const ev of state.pending.filter((e) => e.assignmentId === assignmentId).sort((a, b) => a.deviceSeq - b.deviceSeq)) if (canSend(s, ev.type)) s = ev.type;
  return s;
}

/** `CourierWire.eventJson` */
export function eventJson(e) {
  const o = { eventId: e.eventId, assignmentId: e.assignmentId, type: e.type, deviceTime: e.deviceTime, deviceSeq: e.deviceSeq };
  if (e.from) o.from = e.from;
  if (e.reason) o.reason = e.reason;
  if (e.note) o.note = e.note;
  if (e.cashCollectedMinor !== null && e.cashCollectedMinor !== undefined) o.cashCollectedMinor = e.cashCollectedMinor;
  if (e.cashReason) o.cashReason = e.cashReason;
  if (e.code) o.code = e.code;
  return o;
}

/* ════════════════════════ برنامهٔ پیک ════════════════════════ */

export class CourierApp {
  /** @param {import('./native.mjs').NativeDevice} dev  دستگاهِ واردشده با حسابِ پیک */
  constructor(dev) {
    this.dev = dev;
    this.queue = { nextSeq: 1, pending: [], rejected: [] };
    this.cards = { active: [], done: [] };
    this.lastFailure = '';
    /** پاسخِ آخرین بستهٔ رسیده (برای دیدنِ «تکراری») */
    this.lastResults = [];
  }

  /** `MissionView.forDisplay`: وضعیتِ سرور + صف؛ پایان‌یافته ⇒ بی نشانی و مشتری */
  view(id) {
    const c = [...this.cards.active, ...this.cards.done].find((x) => x.id === id);
    if (!c) return null;
    const status = projectedStatus(c.status, c.id, this.queue);
    const shown = { ...c, status };
    return isLive(status) ? shown : { ...shown, address: null, customer: null };
  }

  /** `CourierHome.record`: اول صفِ گوشی (CAS با وضعیتِ کارت)؛ بعد اگر شد سرور */
  record(id, type, { reason = '', note = '', cash = null, cashReason = '', code = '' } = {}) {
    const card = this.view(id);
    if (!card) throw new CourierInputError('assignment_not_found', 'مأموریت پیدا نشد');
    const [next] = enqueue(this.queue, `cev-${core.ulid(Date.now())}`, card.id, type, Date.now(),
      { from: card.status, reason, note, cashCollectedMinor: cash, cashReason, code });
    this.queue = next;
    return this;
  }

  /**
   * `CourierSync.flush`. `loseResponse`: سرور بسته را می‌گیرد و اعمال می‌کند ولی پاسخ به گوشی
   * نمی‌رسد (قطعِ وسطِ راه) ⇒ صف دست نمی‌خورد.
   */
  async flush({ loseResponse = false } = {}) {
    let sent = 0;
    this.lastFailure = '';
    try {
      for (;;) {
        const b = batch(this.queue);
        if (!b.length) break;
        const r = await this.dev.api('POST', '/courier/events', { deviceId: this.dev.deviceId, events: b.map(eventJson) });
        if (loseResponse) { this.lastFailure = 'connection_reset'; return { ok: false, sent }; }
        if (r.status !== 200) { this.lastFailure = `${r.status} ${r.body?.error?.code || ''}`.trim(); return { ok: false, sent }; }
        const results = (r.body.results || []).map((x) => ({ eventId: x.eventId || '', status: x.status || '', code: x.code || '', message: x.message || '' }));
        this.lastResults = results;
        const next = applyResults(this.queue, b, results);
        this.queue = next;
        const left = b.filter((e) => next.pending.some((p) => p.eventId === e.eventId)).length;
        sent += b.length - left;
        if (left > 0) break;
      }
      for (const scope of ['active', 'done']) {
        const r = await this.dev.api('GET', `/courier/missions?scope=${scope}`);
        if (r.status !== 200) { this.lastFailure = `${r.status} ${r.body?.error?.code || ''}`.trim(); return { ok: false, sent }; }
        this.cards[scope] = r.body.items || [];
      }
      return { ok: true, sent };
    } catch (e) {
      this.lastFailure = e.code || e.message;
      return { ok: false, sent };
    }
  }
}
