# Owner request (1405/08/11): very high security for HesabPay / bank / card accounts in the app
Owner: "Security of the HesabPay section or any bank/card entered in the app must be very high — nobody can steal
anything, or come and put their own card/account or their own QR into the app."
Threat: an employee or a thief with the phone swaps the shop's payee account/QR for their own; credential theft.

Already in main (#833): owner-only (server-enforced), separate HesabPay PIN (PBKDF2, lockout after 5), audit + push to owner
on change, AES-256-GCM write-only credentials (last 4 only), PIN reset wipes account + 24h hold, no platform-key fallback,
S117 guard: no card number/CVV anywhere.

Add (server-authoritative; tests for each):
1. Delayed activation: a new/changed payee account (simple number/QR or auto credentials) becomes ACTIVE only after 24h;
   during the hold the OLD account stays active (or none if first time is immediate? — first setup immediate, changes
   delayed); every owner device gets a push + in-app banner with «این من نبودم — لغو کن» (one tap, re-auth) that cancels
   the change and locks the HesabPay section for 24h. Audit every step.
2. Trusted device + re-auth: changes only from a device the owner has used ≥ N days / marked trusted (reuse device
   records `devices` + existing reauth `ReauthGate`/server reauth) AND HesabPay PIN AND account password/biometric reauth.
3. QR only from stored data: the till renders the QR from the server-stored account (no uploaded QR images accepted as
   payment targets; scanned QR at setup is parsed into account data, never stored as an image). Next to the QR show
   «پول به حسابِ <name> — <last4>» from the stored account.
4. Payee fingerprint on every payment row/intent (hash of the active account id/number); X/Z & owner report show which
   account received money; alert the owner if any payment references a non-active fingerprint.
5. «خودکار» mode: confirm only when the provider webhook says the money reached THIS shop's merchant (merchant id/
   account match + reference + amount); otherwise needs-review.
6. Rate limits, CodeQL pattern (IP limiter before auth; router.use(requireUser, requireShop, limit, authz) in one use),
   secrets never logged/returned; SUB-10/route-authz/shop-route-inventory gates; doc-roles for any new doc kinds.
7. Simplicity: no new settings toggles for these protections — they are always on (class A); only the owner sees the
   «حساب‌پیِ دکان» page (class B under «قابلیت‌ها»/«فروش و پرداخت»).
