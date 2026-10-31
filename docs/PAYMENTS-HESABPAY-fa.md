# پرداخت با حساب‌پی (HesabPay) — قرارداد، پیکربندی و کارهای صاحب محصول

شناسه‌ها: PAY-21 (درگاهِ حساب‌پی)، PAY-08 (تأییدِ ارائه‌دهنده)، سناریوی S35 (وب‌هوکِ دیر یا تکراری).
شاخهٔ سرور: `claude/payments-hesabpay-1` (مهاجرتِ ۱۳۹). قاعده‌های کوتاه: بخشِ «پرداختِ حساب‌پی» در `docs/RULES-fa.md`.

حساب‌پی درگاهِ میزبانی‌شده است: سرورِ ما با کلیدِ API یک «جلسهٔ پرداخت» می‌سازد و نشانیِ پرداخت می‌گیرد؛
مشتری با کیف‌پولِ حساب‌پی یا کارتِ بانکیِ AfPay می‌پردازد؛ حساب‌پی وب‌هوک می‌فرستد. سه راهِ استفاده:

1. **ویترین** (سفارشِ آنلاین): «پرداخت با حساب‌پی» ⇒ رفتن به صفحهٔ حساب‌پی ⇒ برگشت ⇒ نتیجه فقط از وضعیتِ سرور.
2. **صندوق با QR**: صندوقدار QR ِ مبلغِ دقیق را نشان می‌دهد، مشتری با برنامهٔ حساب‌پی اسکن می‌کند و می‌پردازد،
   فروش خودکار بسته می‌شود.
3. **NFC**: همان نشانی (`qrPayload`) به‌صورت رکوردِ NDEF ِ URI از دستگاهِ دکان؛ گوشیِ مشتری با لمس بازش می‌کند.

---

## ۱) قراردادِ API برای برنامهٔ دکان (اندروید/کامپیوتر)

پیشوند همان پیشوندِ برنامهٔ دکان است: **`/api/v1/shop/payments`** (همان مسیرها زیرِ `/api/shop/payments` هم هست).
توکن: همان `Authorization: Bearer …` ِ کاربرِ دکان؛ دکان فقط از نشست (دکانِ فعال) می‌آید و `shopId` ِ بدنه خوانده نمی‌شود.
اجازه: `data.write` (صاحب، مدیر، شاگرد — همان کسی که فروش ثبت می‌کند).

⛔ **مبلغ همیشه عددِ صحیح به «پول» (یک‌صدمِ افغانی)** — همان `amount` ِ ردیف‌های پرداختِ pay-core:
۱۴۵۰ افغانی ⇒ `145000`. عددِ اعشاری، رشته، صفر یا منفی ⇒ ۴۰۰ `bad_amount`. ارز فقط `"AFN"`.

### `GET /config`
همیشه جواب می‌دهد (حتی خاموش):
```json
{ "hesabpay": { "enabled": true } }
```
`enabled` = کلیدهای حساب‌پی روی سرور داده شده **و** پرچمِ `pay.hesabpay` برای همین دکان روشن است.
برنامه دکمهٔ «پرداخت با حساب‌پی» را فقط با `enabled: true` نشان می‌دهد.

### `POST /intents`
بدنه:
```json
{ "kind": "pos_sale", "refId": "<شناسهٔ فروشِ دفترِ دستگاه>", "amount": 145000, "currency": "AFN", "idempotencyKey": "<۸ تا ۱۲۸ نویسه>" }
```
- `kind`: `pos_sale` (صندوق) یا `online_order` (`refId` = شناسهٔ سفارشِ آنلاین).
- `refId`: `[A-Za-z0-9_.:-]{1,128}`. برای صندوق **همان شناسهٔ فروشی** که پس از پرداخت ثبت می‌شود.
- `idempotencyKey`: در بدنه یا سرآیندِ `Idempotency-Key` (اگر هر دو، باید یکی باشند).
- `description` (اختیاری، تا ۱۲۰ نویسه) فقط برای صفحهٔ حساب‌پی.

پاسخ‌ها:

| وضعیت | کی | بدنه |
|---|---|---|
| `201` | قصدِ تازه ساخته شد | نمای قصد (پایین) |
| `200` | همان کلید (سرآیندِ `Idempotent-Replayed: true`)، یا کلیدِ دیگر برای **همان فروش با همان مبلغ** وقتی قصدِ زنده‌ای هست | همان قصد |
| `400` | `bad_kind`، `bad_ref`، `bad_amount`، `bad_currency`، `idempotency_key_required`، `bad_idempotency_key` | خطا |
| `403` | `hesabpay_disabled` (پرچم خاموش یا کلید نیست)؛ `online_payment_unavailable` (سفارشِ آنلاین بی اشتراکِ فعال) | خطا |
| `404` | `order_not_found` | خطا |
| `409` | `idempotency_key_reused` (همان کلید با بدنهٔ دیگر)؛ `already_paid` (+`intentId`)؛ `intent_active` (قصدِ زندهٔ همان فروش با مبلغِ دیگر؛ +`intentId` — اول لغوش کنید)؛ `amount_mismatch` (+`totalMinor`؛ سفارش)؛ `order_not_payable` | خطا |
| `429` | `rate_limited` (۳۰ ساخت/لغو در دقیقه برای هر کاربرِ هر دکان) | خطا |
| `502` | `provider_unavailable` (+`intentId`؛ قصد `failed` شد) — با **کلیدِ تازه** دوباره | خطا |

نمای قصد (پاسخِ ساختن و خواندن):
```json
{
  "id": "pi_…", "kind": "pos_sale", "refId": "sale-…", "status": "pending",
  "amount": 145000, "amountMinor": 145000, "currency": "AFN",
  "checkoutUrl": "https://…", "qrPayload": "https://…",
  "expiresAt": 1767000000000, "paidAt": null, "providerRef": "",
  "needsReview": false, "createdAt": 1766999100000
}
```
- `qrPayload` = `checkoutUrl` — همین را QR کنید (و برای NFC همین نشانی را رکوردِ NDEF ِ URI کنید).
- زمان‌ها میلی‌ثانیهٔ یونیکس، زمانِ سرور.
- وقتی `status: "paid"` و `kind: "pos_sale"`، فیلدِ `payment` هم می‌آید — **دقیقاً** ردیفی که برنامه به فروش می‌افزاید:
  ```json
  { "method": "hesabpay", "kind": "wallet", "amount": 145000, "ref": "<providerRef>", "status": "confirmed", "verification": "gateway", "intentId": "pi_…" }
  ```
  (`method: "hesabpay"` همان روشِ PAY-06 در `license/pay-core.js` است؛ این ردیف بر خلافِ ثبتِ دستی `confirmed` است چون ارائه‌دهنده تأیید کرده.)

### `GET /intents/:id` و `GET /intents/:id?wait=25`
```json
{ "id": "pi_…", "status": "paid", "amount": 145000, "currency": "AFN", "paidAt": 1767000000000, "providerRef": "TX-9", "expiresAt": 1767000000000, … }
```
(همان نمای بالا.) با `wait` (ثانیه، حداکثر ۲۵) اگر وضعیت `created`/`pending` است تا تغییرِ وضعیت یا پایانِ مهلت
صبر می‌کند؛ وضعیتِ پایانی فوری برمی‌گردد. برنامه حلقهٔ `GET ?wait=25` می‌زند، نه کوبیدنِ پشتِ سرِ هم.
سقف: ۲۴۰ خواندن در دقیقه برای هر کاربرِ هر دکان (۴۲۹). قصدِ دکانِ دیگر ⇒ ۴۰۴ `payment_not_found`.
قصدی که زمانش گذشته با همین خواندن `expired` می‌شود.

### `POST /intents/:id/cancel`
فقط از `created`/`pending` ⇒ `200` با `status: "cancelled"`؛ تکرارِ لغو همان ۲۰۰. وضعیتِ دیگر ⇒ ۴۰۹ `not_cancellable` (+`status`).
⚠️ لغو جلسهٔ حساب‌پی را نمی‌بندد (APIِ لغو معلوم نیست): اگر مشتری درست همان دم پرداخت کرد، پول با «تأییدِ ارائه‌دهنده»
ثبت می‌شود و قصد `paid` + `needsReview: true` می‌شود (بررسی/بازپرداخت).

### وضعیت‌ها
```
created → pending → paid | failed | expired | cancelled
paid → refunded            (فقط مدیر؛ درِ پنل در PR ِ بعد)
expired | cancelled | failed → paid   فقط وقتی خودِ حساب‌پی پرداخت را تأیید کند (S35) ⇒ needsReview + هشدار
```
پایانی‌ها برنمی‌گردند؛ `paid` هرگز با رویدادِ بعدی پایین نمی‌آید.

### جریانِ صندوق (برای برنامه)
1. صندوقدار «حساب‌پی» را می‌زند ⇒ برنامه شناسهٔ فروش و `idempotencyKey` را **پیش از** درخواست می‌سازد و نگه می‌دارد
   (قطعِ شبکه و تکرار ⇒ همان قصد).
2. `POST /intents` ⇒ QR از `qrPayload` (و NFC).
3. حلقهٔ `GET /intents/:id?wait=25` تا `paid` / `failed` / `expired` / `cancelled`.
4. `paid` ⇒ فروش را با همان `refId` ببندید و ردیفِ `payment` ِ پاسخ را بیفزایید. بستنِ دوباره همان فروش با همان شناسه
   فروشِ دوم نمی‌سازد؛ یک قصد هرگز فروشِ دیگری را نمی‌پردازد (`refId` عوض نمی‌شود) و فروشِ پرداخت‌شده قصدِ تازه نمی‌گیرد
   (`already_paid`).
5. انصراف ⇒ `POST /intents/:id/cancel`. تغییرِ سبد پس از نمایشِ QR ⇒ اول لغو، بعد قصدِ تازه (وگرنه `intent_active`).

---

## ۲) وب‌هوک

`POST /api/v1/payments/webhook/hesabpay` (همان `/api/payments/…`)، بی توکنِ کاربر — عمداً بیرون از `/shop` که همه‌اش درِ کاربرِ دکان است.
همین نشانی را در داشبوردِ حساب‌پی ثبت کنید: `https://api.vill3n.top/api/v1/payments/webhook/hesabpay`.
⚠️ `/payments` پیشوندِ تازه است ⇒ باید در `ACCOUNT_PREFIXES` ِ درگاهِ پنلِ خانگی (ریپوی `server`) افزوده شود، وگرنه از تونل «not found» می‌گیرد.

- بدنهٔ خام (تا ۶۴ کیلوبایت) با HMAC-SHA256 و `HESABPAY_WEBHOOK_SECRET`، مقایسهٔ زمان‌ثابت. امضای بد ⇒ ۴۰۱.
- تکرار با شناسهٔ رویداد (`payment_events.provider_event_id` یکتا) ⇒ ۲۰۰ و بی‌اثر.
- قصدِ ناشناخته ⇒ ۲۰۰ و لاگ (بی طوفانِ تکرار).
- مبلغ **و** ارز باید با قصد یکی باشند؛ نه ⇒ «paid» نمی‌شود، `needsReview` و سابقهٔ `payment.review`.
- «paid» فقط پس از **تأییدِ دوم** از خودِ حساب‌پی (`fetchStatus`)؛ خاموش کردنش: `PAYMENTS_CONFIRM_WEBHOOK=0`
  (پیشنهاد نمی‌شود). پرداختِ دیر (پس از انقضا/لغو) همیشه تأییدِ دوم می‌خواهد.
- تأییدِ دوم نرسید (شبکه) **یا درِ وضعیت هنوز `pending`/`unknown` می‌گوید** (هم‌گامیِ دیرِ حساب‌پی) ⇒ ۵۰۳ `retry_later`
  تا حساب‌پی دوباره بفرستد؛ رویداد `deferred` می‌ماند و همان رویداد آن بار اعمال می‌شود. فقط «نه» ِ قطعی
  (`failed`/`expired`/`cancelled`) ⇒ ۲۰۰ و `unconfirmed` ِ نهایی، با `needsReview` و هشدار (امضا می‌گوید paid، درِ وضعیت نه).
- «paid» ِ `deferred` روی قصدی که بسته یا منقضی شد (یا از پیش بسته بود) ⇒ `needsReview` و سابقهٔ `payment.review`
  (`paid_unconfirmed`)؛ هرگز خودکار «پرداخت‌شده» نمی‌شود — تأییدِ بعدیِ حساب‌پی همان مسیرِ پرداختِ دیر است.
- همان رویداد هنوز در پردازش ⇒ ۵۰۳ (نه «تکراری»)؛ پردازشی که ۲ دقیقه بی پایان ماند (فرآیندِ مرده) دوباره گرفته می‌شود.
- برگشتِ مرورگر از صفحهٔ حساب‌پی هرگز پرداخت را «موفق» نمی‌کند.

## ۳) تطبیق (PAY-21)

`payment-intents.reconcile({ sinceMs })` قصدهای `created`/`pending`/`expired` ِ ۴۸ ساعتِ اخیر را از حساب‌پی می‌پرسد:
پرداختِ تأییدشده ⇒ `paid` (دیر ⇒ هشدار)، ناموفق ⇒ بستن، اختلاف ⇒ `needsReview` + سابقه. زنده پیش از منقضی و تازه
پیش از کهنه.

زمان‌بند (`startReconciler`، از `index.js`): هر ۵ دقیقه قصدهای ۲ ساعتِ اخیر (زنده و تازه‌منقضی؛ مهلتِ قصد ۱۵ دقیقه)،
و هر ۱۲ نوبت (≈ ساعتی) همان ۴۸ ساعت؛ هر نوبت حداکثر ۴ دقیقه و بقیه به نوبتِ بعد. فقط وقتی ارائه‌دهندهٔ فعال پیکربندی
شده (بی کلید هیچ پرسشی نمی‌رود). دو نمونهٔ سرور هم‌زمان اجرا نمی‌کنند: اجارهٔ `lease:payments.reconcile` در `app_config`
(نمونه‌ای که وسطِ کار بمیرد، پس از ۱۰ دقیقه آزاد می‌شود). فهرستِ پنلِ مدیر در PR ِ بعدی (ویترین + تطبیق + پنل) است.

## ۴) محیطِ سرور

| متغیر | لازم | پیش‌فرض | شرح |
|---|---|---|---|
| `HESABPAY_API_KEY` | ✔ | — | کلیدِ API ِ پذیرنده |
| `HESABPAY_WEBHOOK_SECRET` | ✔ | — | رازِ امضای وب‌هوک |
| `HESABPAY_BASE_URL` | | `https://api.hesab.com/api/v1` | نشانیِ API (sandbox اگر حساب‌پی داد)؛ فقط `https` — http ⇒ «پیکربندی نشده» |
| `HESABPAY_MERCHANT_ID` | | — | اگر حساب‌پی در بدنه خواست |
| `HESABPAY_CALLBACK_URL` | | `SERVER_URL` + مسیرِ وب‌هوک | نشانیِ وب‌هوک در درخواستِ جلسه |
| `HESABPAY_TIMEOUT_MS` | | ۱۰۰۰۰ | مهلتِ هر درخواست به حساب‌پی |
| `PAYMENTS_CONFIRM_WEBHOOK` | | `1` | تأییدِ دوم پیش از «paid» |
| `PAYMENTS_PROVIDER` | | — | `fake` فقط با `NODE_ENV` ِ صریحِ `test`/`development`؛ هر محیطِ دیگر ⇒ نادیده |
| `PAYMENTS_FAKE_SECRET` | | — | رازِ امضای ساختگی (فقط توسعه/آزمون)؛ نبود ⇒ ساختگی خاموش |
| `RATE_PAY_CREATE_MAX` / `RATE_PAY_POLL_MAX` | | ۳۰ / ۲۴۰ در دقیقه | سقف‌ها |

نبودِ کلید ⇒ «پیکربندی نشده»: `/config` همیشه `enabled: false` و ساختن ۴۰۳ `hesabpay_disabled`.
پرچم: `pay.hesabpay` در «تنظیماتِ زنده» (`PUT /api/admin/pump/live-config`، scope = شناسهٔ دکان یا `all`)، پیش‌فرض خاموش.
پرداختِ سفارشِ آنلاین افزون بر این **اشتراکِ فعال** می‌خواهد (دورهٔ آزمایشی نه).

**توسعه با ارائه‌دهندهٔ ساختگی:** سرور را با `NODE_ENV=development`، `PAYMENTS_PROVIDER=fake` و یک
`PAYMENTS_FAKE_SECRET` ِ دلخواه بالا بیاورید؛ `checkoutUrl` ساختگی است (`https://pay.invalid/fake/…`). پرداخت را با
`PAYMENTS_FAKE_SECRET=<همان> node server/scripts/fake-pay.js --url http://127.0.0.1:3000 --intent pi_… --amount 145000`
بزنید (`--status failed` هم).

## ۵) آن‌چه صاحب محصول باید بدهد

1. **حسابِ پذیرندهٔ (Merchant) حساب‌پی** به نامِ کسب‌وکار.
2. **کلیدِ API** و **رازِ امضای وب‌هوک** از داشبوردِ توسعه‌دهندهٔ حساب‌پی (developers.hesab.com) ⇒ در `.env` ِ سرور
   (`HESABPAY_API_KEY`، `HESABPAY_WEBHOOK_SECRET`) — هرگز در کد یا پیام.
3. **ثبتِ نشانیِ وب‌هوک** در داشبوردِ حساب‌پی: `https://api.vill3n.top/api/v1/payments/webhook/hesabpay`، و افزودنِ پیشوندِ
   `/payments` به `ACCOUNT_PREFIXES` ِ پنلِ خانگی (ریپوی `server`).
4. اگر حساب‌پی **sandbox** دارد: نشانیِ sandbox در `HESABPAY_BASE_URL` و یک پرداختِ آزمایشی؛ بی آن، نخستین
   پرداختِ واقعیِ کوچک با پرچمِ یک دکانِ آزمایشی.
5. **سندِ رسمیِ API** (یا دسترسیِ این محیط به docs.hesab.com) تا موارد «تأییدنشده» پایین درست شوند.
6. تصمیمِ D-08 (پرداختِ آنلاین) در `docs/spec/BLOCKERS.md`.

## ۶) تأییدنشده‌ها (همه فقط در `server/src/lib/payment-hesabpay.js`)

سندِ رسمی از این محیط خوانده نشد (docs.hesab.com در دسترسِ شبکهٔ این سیزن نبود). آن‌چه از افزونهٔ WooCommerce ِ
جامعه آمده: `POST https://api.hesab.com/api/v1/payment/create-session` و کلید در سرآیند. بقیه حدس‌اند:

- شکلِ سرآیندِ کلید (`Authorization: API-KEY <key>`)؛
- بدنهٔ ساختنِ جلسه (`items[{id,name,price,quantity}]`، `amount`، `reference`، `callback_url`، `redirect_*_url`) و
  نامِ فیلدهای پاسخ (`url`/`payment_url`، `session_id`/`id`، `expires_at`)؛
- نامِ سرآیندِ امضا (`x-hesab-signature`) و این‌که امضا HMAC-SHA256 ِ بدنهٔ خام است — افزونه به‌جایش درِ
  `hesab/webhooks/verify-signature` ِ حساب‌پی را صدا می‌زند؛
- شکلِ بدنهٔ وب‌هوک و نامِ وضعیت‌ها؛ نیامدنِ ارز = AFN؛
- درِ «وضعیت» برای تأییدِ دوم و تطبیق (`GET /payment/status/:session`)؛
- مبلغ به افغانی با دو رقمِ اعشار (`"1450.00"`)؛ اگر حساب‌پی فقط افغانیِ صحیح بپذیرد باید مبلغِ پول‌دار رد شود (گرد نمی‌شود).

هر کدام که فرق کرد فقط همان فایل عوض می‌شود؛ آزمونِ `test/payment-intents.test.js` (سرورِ ساختگیِ حساب‌پی) هم.

## ۷) افزودنِ ارائه‌دهندهٔ دیگر (M-Paisa، My Money، e& money، ATOMA Pay)

1. فایلِ `server/src/lib/payment-<نام>.js` با همان قرارداد (`configured`، `createSession`، `verifyWebhook`، `fetchStatus`)
   — شرح در سرِ `lib/payment-providers.js`؛ همهٔ جزئیاتِ ارائه‌دهنده فقط همان‌جا.
2. نامش در `adapter()` ِ `payment-providers.js`، درِ `POST /webhook/<نام>` در `routes/payments-webhook.js`،
   پرچمِ `pay.<نام>` و کلیدِ `<نام>` در `GET /config`.
3. `method` ِ ردیفِ پرداخت همان شناسهٔ PAY-06 (`m_paisa`، `my_money`، `e_money`، `atoma_pay`)؛ سقفِ ATOMA Pay ۱۵٬۰۰۰ افغانی (§۴.۲).
4. آزمون با سرورِ ساختگیِ همان ارائه‌دهنده، مثلِ آزمونِ حساب‌پی.
