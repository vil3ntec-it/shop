# Owner request (1405/08/11): each shop's own HesabPay account — money goes to that shop
Owner: "Is it such that everyone enters their own HesabPay in the app and it goes to their own account?"
Current state (verified): server/src/lib/payment-hesabpay.js reads ONE platform key from env
(HESABPAY_API_KEY, HESABPAY_WEBHOOK_SECRET, HESABPAY_MERCHANT_ID) ⇒ all shops would pay into one account.
Feature is behind flag `pay.hesabpay`, never tested with a real/sandbox key (PAY-21 note).
Build:
1. Per-shop merchant credentials: owner-only write (`settings.write`/owner role), stored encrypted at rest (reuse the
   repo's existing secret-encryption helper if any — grep; else AES-GCM with a server key from env), write-only API
   (GET shows enabled + last 4 only). New migration (next free number; check ls server/migrations).
2. Intents/settle/refund/reconcile use the shop's own credentials; webhook verifies with that shop's secret (resolve
   shop via intent reference or a per-shop webhook path); constant-time compare; idempotent.
3. No own credentials ⇒ HesabPay disabled for that shop (GET /config enabled:false; till hides the button). NEVER fall
   back to the platform env key for a shop (keep env key only for an explicit platform-owned test shop if needed).
4. App: owner settings page «حساب‌پیِ دکان» (class C/B per the settings rule; Farsi text) to enter/replace/remove;
   status «وصل است / وصل نیست»; the till button appears only when the server says enabled.
5. Security tests: other shop cannot read/use credentials; staff cannot set; secrets never logged or returned;
   webhook signed with another shop's secret rejected; SUB-10 categories, route-authz-inventory, shop-route-inventory,
   CodeQL IP limiter before auth.
6. STATUS PAY-21/PAY-06 text; RULES note «پولِ حساب‌پی مستقیم به حسابِ همان دکان؛ کلیدِ سکو هرگز جایگزین نیست».

## Owner follow-up (same day) — decisions
A. Two modes per shop:
   - «ساده»: owner enters the shop's HesabPay number OR scans its own HesabPay QR once (parse/validate; store normalized).
     At checkout the app shows that QR/number + the invoice amount + a unique invoice reference (if HesabPay's QR format
     supports amount/reference — verify; otherwise show the reference for the payer to type in the note). Money goes
     directly to the shop. Confirmation is by the cashier (one tap «پول رسید»); NEVER auto-confirm.
     Optional hint (Android only, owner opt-in): NotificationListener reads the HesabPay app's notification and SUGGESTS
     «۵۰۰ افغانی رسید — فاکتورِ X؟»; the cashier taps to confirm. No READ_SMS (Play policy). Amount-only matching is never
     automatic (spoofed SMS/screenshots, same-amount collisions).
   - «خودکار»: shop's own merchant credentials (API key/secret) ⇒ server-side intent with unique reference + signed
     webhook ⇒ invoice confirmed automatically only when reference AND amount match (existing PAY-21 flow, per-shop keys).
B. Separate HesabPay PIN (distinct from the app/login PIN): required to open the «حساب‌پیِ دکان» section and to
   change/remove the account; owner-only; PBKDF2 like other PINs (reuse ApproverVault/AppLock patterns); lockout after
   N wrong tries; every change audited (auditLog/hash chain) and pushed as a notification to the owner's devices
   («حسابِ حساب‌پی عوض شد — اگر شما نبودید…»). Server keeps the account per shop; a change from a device without the PIN
   is rejected server-side too (not only UI).
C. Each shop configures its own account/scanner; nothing global.
