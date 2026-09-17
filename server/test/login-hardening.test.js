'use strict';
/**
 * سنجه‌های سخت‌سازیِ ورود.
 *
 * هر سنجه یکی از حالت‌هایی است که صاحب مخزن خواسته بود بررسی شود:
 * رمزِ غلط، ایمیلِ ناموجود، کادرِ خالی، خروج، ورودِ دوباره، عوض کردنِ
 * حساب، درخواستِ دستکاری‌شده، تلاشِ زیاد، پاسخِ خراب، و چند درخواستِ
 * هم‌زمان.
 *
 * ⚠️ چیزی که اینجا **سنجیده نمی‌شود** و نباید هم بشود: خودِ قفلِ حساب.
 * `helpers.js` عمداً `LOGIN_LOCKOUT_TRIES` را بسیار بالا می‌گذارد تا
 * دویست سنجهٔ دیگر همدیگر را قفل نکنند. پس قفل و محدودیتِ نرخ در
 * پایینِ همین پرونده، مستقیم روی خودِ ابزارشان سنجیده می‌شوند.
 */
const test = require('node:test');
const assert = require('node:assert/strict');
const h = require('./helpers');

test.before(async () => { await h.start(); });
test.after(async () => { await h.stop(); });

/* ============================ ورودِ درست ============================ */

test('ورود موفق: نشست می‌دهد و هیچ ردی از رمز در پاسخ نیست', async () => {
  const user = await h.newUser('ورودِ درست');
  const r = await h.post('/api/auth/login', {
    identifier: user.email, password: user.password,
  });

  assert.equal(r.status, 200);
  assert.ok(r.body.accessToken, 'توکنِ دسترسی');
  assert.ok(r.body.refreshToken, 'توکنِ تازه‌سازی');
  assert.equal(r.body.user.email, user.email);

  /*
   *  خواستهٔ صاحب مخزن: «Password را داخل Response API برنگردان».
   *  یک نگاه به کلیدها کافی نیست — رمز ممکن است هر جای درخت باشد، پس
   *  کلِ پاسخ به متن تبدیل و جست‌وجو می‌شود.
   */
  const flat = JSON.stringify(r.body);
  assert.ok(!flat.includes(user.password), 'رمزِ خام در پاسخ نیست');
  assert.ok(!flat.includes('password_hash'), 'هشِ رمز در پاسخ نیست');
  assert.ok(!flat.includes('scrypt$'), 'هیچ هشی در پاسخ نیست');
});

test('نشست واقعاً کار می‌کند و توکن در پایگاه داده فقط به شکل هش می‌ماند', async () => {
  const user = await h.newUser('نشستِ سالم');
  const me = await h.get('/api/me', { token: user.accessToken });
  assert.equal(me.status, 200);

  //  توکنِ خام هیچ‌جای جدولِ توکن‌ها نیست — فقط SHA-256ِ آن
  const rows = await h.query('SELECT token_hash FROM tokens WHERE token_hash = $1', [user.accessToken]);
  assert.equal(rows.rowCount, 0, 'توکنِ خام ذخیره نشده');
});

/* ============================ ورودِ نادرست ============================ */

test('رمز اشتباه رد می‌شود', async () => {
  const user = await h.newUser('رمزِ غلط');
  const r = await h.post('/api/auth/login', {
    identifier: user.email, password: 'Definitely-Wrong-1',
  });
  assert.equal(r.status, 401);
  assert.equal(r.body.error.code, 'bad_credentials');
});

test('ایمیلِ ناموجود دقیقاً همان پاسخِ رمزِ غلط را می‌گیرد', async () => {
  const user = await h.newUser('برای مقایسه');

  const wrongPass = await h.post('/api/auth/login', {
    identifier: user.email, password: 'Definitely-Wrong-1',
  });
  const noSuchUser = await h.post('/api/auth/login', {
    identifier: 'nobody-at-all@test.local', password: 'Definitely-Wrong-1',
  });

  /*
   *  اگر این دو از هم فرق کنند — حتی در یک کلمه — هر کسی با یک فهرستِ
   *  ایمیل می‌فهمد کدام‌یک روی این سرور حساب دارد.
   */
  assert.equal(noSuchUser.status, wrongPass.status);
  assert.deepEqual(noSuchUser.body, wrongPass.body);
});

test('ایمیلِ ناموجود همان‌قدر وقت می‌گیرد — ساعتِ پاسخ حساب را لو نمی‌دهد', async () => {
  const user = await h.newUser('سنجشِ زمان');

  //  یک بار گرم‌کردن: اولین `burnTime` هشِ ساختگی را هم می‌سازد
  await h.post('/api/auth/login', { identifier: 'warmup@test.local', password: 'x'.repeat(12) });

  async function median(identifier) {
    const runs = [];
    for (let i = 0; i < 5; i += 1) {
      const t = process.hrtime.bigint();
      await h.post('/api/auth/login', { identifier, password: 'Definitely-Wrong-1' });
      runs.push(Number(process.hrtime.bigint() - t) / 1e6);
    }
    return runs.sort((a, b) => a - b)[2];
  }

  const known = await median(user.email);
  const unknown = await median('nobody-at-all-2@test.local');

  /*
   *  پیش از این وصله، «ناموجود» هیچ scrypt‌ای اجرا نمی‌کرد و نسبتش به
   *  «موجود» چند صدم بود. نسبتِ ۰٫۴ عمداً دست‌ودل‌باز گرفته شده: این
   *  سنجه باید تفاوتِ **مرتبه‌ای** را بگیرد، نه نوسانِ طبیعیِ یک
   *  ماشینِ شلوغ را.
   */
  assert.ok(
    unknown > known * 0.4,
    `ایمیلِ ناموجود خیلی زودتر جواب داد (${unknown.toFixed(1)}ms در برابر ${known.toFixed(1)}ms)`
  );
});

test('کادرهای خالی پیامِ روشن می‌گیرند، نه «رمز غلط»', async () => {
  const noIdentifier = await h.post('/api/auth/login', { password: 'Passw0rd!test' });
  assert.equal(noIdentifier.status, 400);
  assert.equal(noIdentifier.body.error.code, 'identifier_required');

  const noPassword = await h.post('/api/auth/login', { identifier: 'someone@test.local' });
  assert.equal(noPassword.status, 400);
  assert.equal(noPassword.body.error.code, 'password_required');

  const emptyPassword = await h.post('/api/auth/login', {
    identifier: 'someone@test.local', password: '',
  });
  assert.equal(emptyPassword.status, 400);
  assert.equal(emptyPassword.body.error.code, 'password_required');
});

test('ایمیلِ بدقالب پیشِ سرور هم رد می‌شود — نه فقط در برنامه', async () => {
  const r = await h.post('/api/auth/login', { identifier: 'not-an-email@', password: 'Passw0rd!test' });
  assert.equal(r.status, 400);
  //  پیام فارسی است، نه خطای خامِ دیتابیس
  assert.match(r.body.error.message, /[؀-ۿ]/);
});

test('حسابِ غیرفعال حتی با رمزِ درست وارد نمی‌شود', async () => {
  const user = await h.newUser('غیرفعال');
  await h.query('UPDATE users SET status=$2 WHERE id=$1', [user.user.id, 'disabled']);

  const r = await h.post('/api/auth/login', { identifier: user.email, password: user.password });
  assert.equal(r.status, 403);
  assert.equal(r.body.error.code, 'account_disabled');
});

test('ورودِ درست، ردِ تلاش‌های ناموفق را پاک می‌کند', async () => {
  const user = await h.newUser('پاک‌سازیِ شمارنده');

  for (let i = 0; i < 3; i += 1) {
    await h.post('/api/auth/login', { identifier: user.email, password: `Wrong-${i}-aaaa` });
  }
  const before = await h.query(
    'SELECT COUNT(*)::int AS n FROM login_attempts WHERE scope=$1 AND identifier=$2 AND ok=false',
    ['user', user.email]
  );
  assert.equal(before.rows[0].n, 3, 'سه تلاشِ ناموفق ثبت شده');

  const ok = await h.post('/api/auth/login', { identifier: user.email, password: user.password });
  assert.equal(ok.status, 200);

  const after = await h.query(
    'SELECT COUNT(*)::int AS n FROM login_attempts WHERE scope=$1 AND identifier=$2 AND ok=false',
    ['user', user.email]
  );
  assert.equal(after.rows[0].n, 0, 'بعد از ورودِ درست، شمارنده صفر است');
});

/* ============================ خروج ============================ */

test('خروج هر دو توکن را روی سرور باطل می‌کند — نه فقط روی گوشی', async () => {
  const user = await h.newUser('خروجِ واقعی');

  const out = await h.post(
    '/api/auth/logout',
    { refreshToken: user.refreshToken },
    { token: user.accessToken }
  );
  assert.equal(out.status, 200);

  //  توکنِ دسترسی دیگر در را باز نمی‌کند
  const me = await h.get('/api/me', { token: user.accessToken });
  assert.equal(me.status, 401);

  /*
   *  و مهم‌تر: توکنِ تازه‌سازی هم نمی‌تواند نشست را برگرداند. اگر این
   *  یکی زنده می‌ماند، «خروج» فقط یک پاک کردنِ ظاهری بود و هر کسی که
   *  آن توکن را از حافظهٔ گوشی برمی‌داشت، دوباره وارد می‌شد.
   */
  const revived = await h.post('/api/auth/refresh', { refreshToken: user.refreshToken });
  assert.equal(revived.status, 401);
  assert.equal(revived.body.error.code, 'invalid_token');
});

test('بعد از خروج، ورودِ دوباره کار می‌کند و نشستِ تازه با قبلی فرق دارد', async () => {
  const user = await h.newUser('ورودِ دوباره');
  await h.post('/api/auth/logout', { refreshToken: user.refreshToken }, { token: user.accessToken });

  const again = await h.post('/api/auth/login', { identifier: user.email, password: user.password });
  assert.equal(again.status, 200);
  assert.notEqual(again.body.accessToken, user.accessToken, 'توکنِ تازه، نه همان قبلی');
  assert.notEqual(again.body.refreshToken, user.refreshToken);

  const me = await h.get('/api/me', { token: again.body.accessToken });
  assert.equal(me.status, 200);

  //  و توکنِ نشستِ قبلی هنوز مرده است
  const old = await h.get('/api/me', { token: user.accessToken });
  assert.equal(old.status, 401);
});

test('«خروج از همهٔ دستگاه‌ها» هر نشستِ باز را می‌بندد', async () => {
  const user = await h.newUser('چند دستگاه');
  const second = await h.post('/api/auth/login', {
    identifier: user.email, password: user.password,
    device: { deviceId: 'second-phone', name: 'گوشی دوم', platform: 'test' },
  });
  assert.equal(second.status, 200);

  await h.post('/api/auth/logout-all', {}, { token: user.accessToken });

  assert.equal((await h.get('/api/me', { token: user.accessToken })).status, 401);
  assert.equal((await h.get('/api/me', { token: second.body.accessToken })).status, 401);
});

/* ============================ عوض کردنِ حساب ============================ */

test('عوض کردنِ حساب: نشستِ نفرِ قبلی به نفرِ تازه راه نمی‌دهد', async () => {
  const first = await h.newUser('نفرِ اول');
  const second = await h.newUser('نفرِ دوم');

  //  نفرِ اول خارج می‌شود
  await h.post('/api/auth/logout', { refreshToken: first.refreshToken }, { token: first.accessToken });

  //  نفرِ دوم وارد می‌شود — و هر کدام فقط خودش را می‌بیند
  const meSecond = await h.get('/api/me', { token: second.accessToken });
  assert.equal(meSecond.status, 200);
  assert.equal(meSecond.body.user.email, second.email);
  assert.notEqual(meSecond.body.user.id, first.user.id);

  //  توکنِ نفرِ اول مرده مانده، حتی حالا که کسِ دیگری وارد است
  assert.equal((await h.get('/api/me', { token: first.accessToken })).status, 401);
});

/* ============================ توکن و دستکاری ============================ */

test('توکنِ دستکاری‌شده، ساختگی و خالی همه رد می‌شوند', async () => {
  const user = await h.newUser('دستکاری');

  const tampered = `${user.accessToken.slice(0, -3)}AAA`;
  assert.equal((await h.get('/api/me', { token: tampered })).status, 401);
  assert.equal((await h.get('/api/me', { token: 'x'.repeat(40) })).status, 401);
  assert.equal((await h.get('/api/me')).status, 401);
});

test('توکنِ منقضی رد می‌شود، حتی اگر باطل نشده باشد', async () => {
  const user = await h.newUser('منقضی');

  //  ساعتِ انقضا را به گذشته می‌بریم؛ همان کاری که گذشتِ زمان می‌کند
  await h.query(
    `UPDATE tokens SET expires_at = $2
      WHERE subject_id = $1 AND kind = 'access'`,
    [user.user.id, Date.now() - 1000]
  );

  const me = await h.get('/api/me', { token: user.accessToken });
  assert.equal(me.status, 401);
  assert.equal(me.body.error.code, 'invalid_token');
});

test('توکنِ تازه‌سازی، توکنِ دسترسی می‌سازد ولی خودش کلیدِ در نیست', async () => {
  const user = await h.newUser('تازه‌سازی');

  //  توکنِ تازه‌سازی روی مسیرهای معمولی پذیرفته نمی‌شود
  assert.equal((await h.get('/api/me', { token: user.refreshToken })).status, 401);

  const fresh = await h.post('/api/auth/refresh', { refreshToken: user.refreshToken });
  assert.equal(fresh.status, 200);
  assert.ok(fresh.body.accessToken);
  //  و در پاسخِ تازه‌سازی هیچ توکنِ تازه‌سازیِ تازه‌ای نمی‌آید
  assert.equal(fresh.body.refreshToken, undefined);
  assert.equal((await h.get('/api/me', { token: fresh.body.accessToken })).status, 200);
});

test('شناسه از توکن خوانده می‌شود، نه از بدنه — درخواستِ دستکاری‌شده بی‌اثر است', async () => {
  const victim = await h.newUser('قربانی');
  const attacker = await h.newUser('مهاجم');

  /*
   *  مهاجم شناسهٔ قربانی را در بدنه می‌گذارد. اگر سرور یک لحظه به
   *  بدنه اعتماد کند، این درخواست حسابِ کسِ دیگری را برمی‌گرداند.
   */
  const r = await h.api('GET', '/api/me', { token: attacker.accessToken });
  assert.equal(r.status, 200);
  assert.equal(r.body.user.id, attacker.user.id);

  const posted = await h.post(
    '/api/me',
    { userId: victim.user.id, id: victim.user.id, name: 'عوض‌شده' },
    { token: attacker.accessToken }
  );
  //  هر جوابی بدهد، نباید حسابِ قربانی را عوض کرده باشد
  const victimNow = await h.get('/api/me', { token: victim.accessToken });
  assert.equal(victimNow.status, 200);
  assert.notEqual(victimNow.body.user.name, 'عوض‌شده');
  assert.ok(posted.status < 500, 'سرور نباید بترکد');
});

/* ============================ هم‌زمانی ============================ */

test('چند ورودِ هم‌زمان با یک حساب: همه سالم، هر کدام نشستِ خودش', async () => {
  const user = await h.newUser('هم‌زمان');

  const all = await Promise.all(
    Array.from({ length: 5 }, () =>
      h.post('/api/auth/login', { identifier: user.email, password: user.password })
    )
  );

  for (const r of all) assert.equal(r.status, 200);
  const tokens = all.map(r => r.body.accessToken);
  assert.equal(new Set(tokens).size, tokens.length, 'هیچ دو نشستی یک توکن ندارند');

  //  و هر پنج تا واقعاً کار می‌کنند
  for (const t of tokens) {
    assert.equal((await h.get('/api/me', { token: t })).status, 200);
  }
});

test('چند تازه‌سازیِ هم‌زمان توکنِ همدیگر را باطل نمی‌کنند', async () => {
  const user = await h.newUser('تازه‌سازیِ هم‌زمان');

  const all = await Promise.all(
    Array.from({ length: 4 }, () => h.post('/api/auth/refresh', { refreshToken: user.refreshToken }))
  );
  for (const r of all) assert.equal(r.status, 200);

  //  توکنِ تازه‌سازی بعد از چند بار استفاده هنوز سالم است
  const after = await h.post('/api/auth/refresh', { refreshToken: user.refreshToken });
  assert.equal(after.status, 200);
});

/* ============================ پاسخ و خطا ============================ */

test('بدنهٔ خراب پیامِ تمیز می‌گیرد، نه درونِ کتابخانهٔ سرور', async () => {
  const res = await fetch(`${h.base()}/api/auth/login`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: '{"identifier": "a@b.com", "password": ',
  });
  const body = await res.json();

  assert.equal(res.status, 400);
  assert.equal(body.error.code, 'bad_json');
  assert.match(body.error.message, /[؀-ۿ]/, 'پیام فارسی است');
  //  هیچ اثری از پیامِ داخلیِ Node
  assert.ok(!/JSON at position|Unexpected token/i.test(JSON.stringify(body)));
});

test('بدنهٔ بیش از حد بزرگ رد می‌شود و سرور سرِ پا می‌ماند', async () => {
  const res = await fetch(`${h.base()}/api/auth/login`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ identifier: 'a@b.com', password: 'x'.repeat(3 * 1024 * 1024) }),
  });
  assert.equal(res.status, 413);
  const body = await res.json();
  assert.equal(body.error.code, 'body_too_large');

  //  و سرور هنوز جواب می‌دهد
  assert.equal((await h.get('/api/health')).status, 200);
});

test('خطای سرور جزئیاتِ داخلی را بیرون نمی‌دهد', async () => {
  //  یک ورودیِ بدقالب که تا لایهٔ اعتبارسنجی می‌رود
  const r = await h.post('/api/auth/login', { identifier: { $ne: null }, password: 'Passw0rd!test' });
  assert.ok(r.status >= 400 && r.status < 500, 'خطای کاربر است، نه خرابیِ سرور');
  const flat = JSON.stringify(r.body);
  assert.ok(!/at Object\.|node_modules|\/src\//.test(flat), 'هیچ مسیرِ فایل یا stack در پاسخ نیست');
});

/* ============================ بازیابیِ رمز ============================ */

test('کدِ بازیابی یک‌بارمصرف است — بارِ دوم کار نمی‌کند', async () => {
  const user = await h.newUser('بازیابیِ یک‌بارمصرف');

  const asked = await h.post('/api/auth/password/forgot', { email: user.email });
  assert.equal(asked.status, 200);
  const code = asked.body.devCode;
  assert.ok(code, 'کدِ آزمایشی');

  const first = await h.post('/api/auth/password/reset', {
    email: user.email, code, password: 'Brand-New-Pass-1',
  });
  assert.equal(first.status, 200);

  //  همان کد، بارِ دوم
  const second = await h.post('/api/auth/password/reset', {
    email: user.email, code, password: 'Another-New-Pass-2',
  });
  assert.notEqual(second.status, 200);

  //  و رمزِ دومی ننشسته
  const wrong = await h.post('/api/auth/login', {
    identifier: user.email, password: 'Another-New-Pass-2',
  });
  assert.equal(wrong.status, 401);
  const right = await h.post('/api/auth/login', {
    identifier: user.email, password: 'Brand-New-Pass-1',
  });
  assert.equal(right.status, 200);
});

test('رمزِ تازه هم هش می‌شود — هیچ رمزِ خامی در پایگاه داده نیست', async () => {
  const user = await h.newUser('هشِ رمز');
  const fresh = 'Yet-Another-Pass-3';

  const asked = await h.post('/api/auth/password/forgot', { email: user.email });
  await h.post('/api/auth/password/reset', { email: user.email, code: asked.body.devCode, password: fresh });

  const row = await h.query('SELECT password_hash FROM users WHERE id=$1', [user.user.id]);
  const stored = row.rows[0].password_hash;
  assert.ok(stored.startsWith('scrypt$'), 'قالبِ هشِ استاندارد');
  assert.ok(!stored.includes(fresh), 'رمزِ خام داخلِ هش نیست');

  //  و هیچ جای دیگری از جدولِ کاربران رمز نیفتاده
  const anywhere = await h.query('SELECT COUNT(*)::int AS n FROM users WHERE password_hash = $1', [fresh]);
  assert.equal(anywhere.rows[0].n, 0);
});

/* ============================ شناسه‌ی برنامه ============================ */

/*
 *  سرورِ مرکزی یکی است و چند برنامه از آن احراز هویت می‌گیرند. این سه
 *  سنجه ثابت می‌کنند که گفتنِ شناسه واقعاً اثر دارد — وگرنه فرستادنش
 *  از سمتِ برنامه فقط یک کلیدِ تزئینی در بدنه بود.
 */

test('ورود با شناسه‌ی برنامه، نشست را به همان بخش مهر می‌زند', async () => {
  const user = await h.newUser('شناسه‌دار');

  const r = await h.post('/api/auth/login', {
    identifier: user.email, password: user.password, app: 'shop',
  });
  assert.equal(r.status, 200);

  const row = await h.query(
    `SELECT app FROM tokens WHERE kind='access' AND subject_id=$1 ORDER BY issued_at DESC LIMIT 1`,
    [user.user.id]
  );
  assert.equal(row.rows[0].app, 'shop');
});

test('نگفتنِ شناسه همان «دکان» است — نسخه‌های قدیمی بیرون نمی‌افتند', async () => {
  const user = await h.newUser('بی‌شناسه');

  const withId = await h.post('/api/auth/login', {
    identifier: user.email, password: user.password, app: 'shop',
  });
  const without = await h.post('/api/auth/login', {
    identifier: user.email, password: user.password,
  });
  assert.equal(withId.status, 200);
  assert.equal(without.status, 200);

  //  هر دو نشست یک کار می‌کنند
  assert.equal((await h.get('/api/me', { token: withId.body.accessToken })).status, 200);
  assert.equal((await h.get('/api/me', { token: without.body.accessToken })).status, 200);
});

test('توکنِ یک برنامه در برنامه‌ی دیگر اصلاً پیدا نمی‌شود', async () => {
  const user = await h.newUser('دو برنامه');

  const shop = await h.post('/api/auth/login', {
    identifier: user.email, password: user.password, app: 'shop',
  });
  const pump = await h.post('/api/auth/login', {
    identifier: user.email, password: user.password, app: 'pump',
  });
  assert.equal(shop.status, 200);
  assert.equal(pump.status, 200);
  assert.notEqual(shop.body.accessToken, pump.body.accessToken);

  //  همان آدم، ولی هر نشست فقط درِ خودش را باز می‌کند
  assert.equal((await h.get('/api/me', { token: shop.body.accessToken })).status, 200);
  assert.equal(
    (await h.get('/api/me', { token: pump.body.accessToken })).status, 401,
    'نشستِ پمپ روی مسیرهای دکان پیدا نمی‌شود'
  );
});

/* ============================ قفلِ حساب ============================ */

/*
 *  `helpers.js` هر دو شمارنده‌ی قفل را عمداً بسیار بالا می‌گذارد، وگرنه
 *  دویست سنجه‌ی دیگر — که همه از ۱۲۷٫۰٫۰٫۱ می‌آیند — همدیگر را می‌بستند.
 *  پس اینجا خودِ تابع با آستانه‌های واقعی صدا زده می‌شود.
 */

test('قفل، حدس‌زننده را می‌بندد نه صاحبِ حساب', async () => {
  const auth = require('../src/routes/auth');
  const config = require('../src/config');
  const { query, now } = require('../src/db');

  const victim = `victim-${Date.now()}@test.local`;
  const attackerIp = '203.0.113.7';
  const ownerIp = '198.51.100.20';

  //  مهاجم از IP خودش، هشت بار رمزِ غلط می‌زند
  for (let i = 0; i < 8; i += 1) {
    await query(
      'INSERT INTO login_attempts (scope, identifier, ip, ok, created_at) VALUES ($1,$2,$3,$4,$5)',
      ['user', victim, attackerIp, false, now()]
    );
  }

  const real = { tries: config.rateLimit.lockoutTries, global: config.rateLimit.lockoutGlobalTries };
  config.rateLimit.lockoutTries = 8;
  config.rateLimit.lockoutGlobalTries = 40;
  try {
    //  مهاجم بسته است
    await assert.rejects(
      () => auth.assertNotLocked('user', victim, attackerIp),
      (e) => e.code === 'locked_out',
      'IP مهاجم باید بسته باشد'
    );

    /*
     *  و مهم‌ترین بخش: صاحبِ حساب از گوشیِ خودش دست‌نخورده وارد
     *  می‌شود. تا دیروز همین‌جا بسته می‌شد — یعنی هشت کلیکِ یک غریبه،
     *  حسابِ یک نفرِ دیگر را یک ربع می‌بست.
     */
    await auth.assertNotLocked('user', victim, ownerIp);
  } finally {
    config.rateLimit.lockoutTries = real.tries;
    config.rateLimit.lockoutGlobalTries = real.global;
  }
});

test('حمله‌ی پخش‌شده روی چند IP باز هم حساب را می‌بندد', async () => {
  const auth = require('../src/routes/auth');
  const config = require('../src/config');
  const { query, now } = require('../src/db');

  const victim = `spread-${Date.now()}@test.local`;

  //  چهل تلاشِ ناموفق از چهل IP جدا — هیچ‌کدام به تنهایی به هشت نمی‌رسد
  for (let i = 0; i < 40; i += 1) {
    await query(
      'INSERT INTO login_attempts (scope, identifier, ip, ok, created_at) VALUES ($1,$2,$3,$4,$5)',
      ['user', victim, `203.0.113.${i + 1}`, false, now()]
    );
  }

  const real = { tries: config.rateLimit.lockoutTries, global: config.rateLimit.lockoutGlobalTries };
  config.rateLimit.lockoutTries = 8;
  config.rateLimit.lockoutGlobalTries = 40;
  try {
    await assert.rejects(
      () => auth.assertNotLocked('user', victim, '198.51.100.99'),
      (e) => e.code === 'locked_out',
      'پشتوانه‌ی حمله‌ی پخش‌شده باید بگیرد'
    );
  } finally {
    config.rateLimit.lockoutTries = real.tries;
    config.rateLimit.lockoutGlobalTries = real.global;
  }
});

/* ============================ نسخه‌ی برنامه ============================ */

test('نسخه‌ی برنامه روی نشست ثبت می‌شود', async () => {
  const user = await h.newUser('نسخه‌دار');

  const r = await h.post('/api/auth/login', {
    identifier: user.email, password: user.password,
    app: 'shop', appVersion: '3.2.7',
  });
  assert.equal(r.status, 200);

  const row = await h.query(
    `SELECT app, app_version FROM tokens
      WHERE kind='access' AND subject_id=$1 ORDER BY issued_at DESC LIMIT 1`,
    [user.user.id]
  );
  assert.equal(row.rows[0].app, 'shop');
  assert.equal(row.rows[0].app_version, '3.2.7');
});

test('نگفتنِ نسخه خطا نیست — نسخه‌های امروزِ دستِ کاربر بیرون نمی‌افتند', async () => {
  const user = await h.newUser('بی‌نسخه');

  const r = await h.post('/api/auth/login', { identifier: user.email, password: user.password });
  assert.equal(r.status, 200);

  const row = await h.query(
    `SELECT app_version FROM tokens
      WHERE kind='access' AND subject_id=$1 ORDER BY issued_at DESC LIMIT 1`,
    [user.user.id]
  );
  assert.equal(row.rows[0].app_version, '');
});

test('نسخه‌ی دستکاری‌شده هیچ دری باز نمی‌کند و سرور را نمی‌ترکاند', async () => {
  const user = await h.newUser('نسخه‌ی بدخواه');

  //  رشته‌ی خیلی بلند، و چیزی که شبیهِ تزریق است
  const r = await h.post('/api/auth/login', {
    identifier: user.email, password: user.password,
    appVersion: "x'; DROP TABLE tokens; --" + 'y'.repeat(500),
  });
  assert.equal(r.status, 200, 'ورود سرِ جایش است');

  const row = await h.query(
    `SELECT app_version FROM tokens
      WHERE kind='access' AND subject_id=$1 ORDER BY issued_at DESC LIMIT 1`,
    [user.user.id]
  );
  assert.ok(row.rows[0].app_version.length <= 32, 'بریده شده');

  //  و جدول سرِ جایش است
  const alive = await h.query('SELECT COUNT(*)::int AS n FROM tokens');
  assert.ok(alive.rows[0].n > 0);
});

/* ============================ لاگ ============================ */

test('کدِ بازیابی در لاگِ سرورِ واقعی نوشته نمی‌شود', async () => {
  /*
   *  خواستهٔ صاحب مخزن: «Token بازیابی نباید در Log ذخیره شود».
   *
   *  فرستندهٔ `log` کدِ خام را می‌نوشت، بی هیچ شرطی — و پیش‌فرضِ
   *  `OTP_EMAIL_PROVIDER` هم همین است. یعنی روی سرورِ واقعی هر کدِ
   *  بازیابی صاف می‌رفت داخلِ لاگ.
   *
   *  اینجا خودِ فرستنده صدا زده می‌شود، چون رفتارش به `NODE_ENV` بند
   *  است و کلِ برنامهٔ سنجه در حالتِ `test` بالا آمده.
   */
  const config = require('../src/config');
  const { senders } = require('../src/lib/otp');

  const lines = [];
  const realLog = console.log, realWarn = console.warn;
  console.log = (...a) => lines.push(a.join(' '));
  console.warn = (...a) => lines.push(a.join(' '));

  const realEnv = config.env;
  try {
    config.env = 'production';
    await senders.log('ali@example.com', '424242');
  } finally {
    config.env = realEnv;
    console.log = realLog;
    console.warn = realWarn;
  }

  const written = lines.join('\n');
  assert.ok(!written.includes('424242'), 'کد در لاگ نیست');
  assert.ok(!written.includes('ali@example.com'), 'نشانیِ کامل هم در لاگ نیست');
  assert.ok(written.includes('a***@example.com'), 'ولی آن‌قدر هست که مدیر بفهمد کدام بود');
});

test('بیرون از production همان کد نوشته می‌شود — وگرنه سرورِ توسعه کار نمی‌کند', async () => {
  const { senders } = require('../src/lib/otp');
  const lines = [];
  const realLog = console.log;
  console.log = (...a) => lines.push(a.join(' '));
  try {
    await senders.log('dev@example.com', '131313');
  } finally { console.log = realLog; }

  assert.ok(lines.join('\n').includes('131313'));
});

/* ============================ ابزارِ محدودیت نرخ ============================ */

/*
 *  این دو سنجه مسیرها را صدا نمی‌زنند، خودِ ابزار را می‌سنجند.
 *
 *  چرا: `helpers.js` سقفِ همهٔ مسیرها را عمداً بسیار بالا می‌گذارد،
 *  وگرنه دویست سنجهٔ دیگر — که همه از یک IP می‌آیند — همدیگر را
 *  می‌بستند. پس اگر بخواهیم قفل را از راهِ مسیر بسنجیم، یا باید سقف را
 *  پایین بیاوریم (و بقیه را بترکانیم) یا چیزی نسنجیم. راهِ سوم همین
 *  است: ابزار را مستقیم صدا بزنیم.
 */

const { rateLimit, resetAll } = require('../src/middleware/ratelimit');

function fakeReq(ip = '10.0.0.1') {
  return { headers: {}, socket: { remoteAddress: ip }, body: {} };
}
function fakeRes() {
  const headers = {};
  return { set: (k, v) => { headers[k] = v; }, headers };
}

test('محدودیتِ نرخ بعد از سقف، درخواست را رد می‌کند', async () => {
  resetAll();
  const limiter = rateLimit({ max: 3, windowMs: 60_000, keyPrefix: 'sanity' });
  const res = fakeRes();
  const errors = [];
  const next = (err) => errors.push(err || null);

  for (let i = 0; i < 5; i += 1) limiter(fakeReq(), res, next);

  assert.equal(errors.filter(e => e === null).length, 3, 'سه تای اول رد می‌شوند');
  const blocked = errors.filter(Boolean);
  assert.equal(blocked.length, 2, 'دو تای بعدی بسته می‌شوند');
  assert.equal(blocked[0].status, 429);
  assert.ok(res.headers['Retry-After'], 'به کاربر می‌گوید چقدر صبر کند');
  resetAll();
});

test('محدودیتِ نرخ هر IP را جدا می‌شمارد — یک مهاجم بقیه را نمی‌بندد', () => {
  resetAll();
  const limiter = rateLimit({ max: 2, windowMs: 60_000, keyPrefix: 'perip' });
  const errors = [];
  const next = (err) => errors.push(err || null);

  for (let i = 0; i < 4; i += 1) limiter(fakeReq('10.0.0.9'), fakeRes(), next);
  //  مهاجم بسته شد؛ حالا یک کاربرِ دیگر از IP دیگر
  errors.length = 0;
  limiter(fakeReq('10.0.0.10'), fakeRes(), next);

  assert.deepEqual(errors, [null], 'کاربرِ دیگر آزاد است');
  resetAll();
});
