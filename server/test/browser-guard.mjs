/**
 * F0-17 — نبودِ ابزارِ مرورگر (پلی‌رایت، axe-core) «قبول» نیست.
 *
 * پیش از این، آزمونِ مرورگری که پلی‌رایت پیدا نمی‌کرد با کدِ ۰ بیرون
 * می‌رفت و CI «سبز» نشان می‌داد بی آن‌که مرورگری باز شده باشد. حالا:
 *   • پیش‌فرض: قرمز (کدِ ۱) با راهِ نصب.
 *   • SKIP_BROWSER_TESTS=1: ردِ آگاهانه با پیامِ بلند — فقط برای کامپیوترِ
 *     خودتان، هرگز در CI.
 */
export const SKIP = process.env.SKIP_BROWSER_TESTS === '1';

export function missingTool(name, install) {
  if (SKIP) {
    console.log(`⏭  ${name} پیدا نشد و SKIP_BROWSER_TESTS=1 است — این سنجه آگاهانه رد شد (اجرا نشد؛ سبز حساب نکنید)`);
    process.exit(0);
  }
  console.error(`❌  ${name} پیدا نشد — آزمونِ مرورگر بی آن اجرا نمی‌شود و «قبول» هم حساب نمی‌شود.
    نصب: ${install}
    ردِ آگاهانه فقط روی کامپیوترِ خودتان: SKIP_BROWSER_TESTS=1`);
  process.exit(1);
}

export const PLAYWRIGHT_INSTALL = 'npm i -g playwright@1.56.1 && npx playwright install chromium';

/*
 *  درخواستِ پس‌زمینه‌ای که از صفحه بیشتر عمر کند (شمارشِ دوره‌ای، pullِ زنده،
 *  تازه‌سازیِ نشست…) وقتی صفحه/زمینه بسته شد، در گرداننده‌ی `page.route`
 *  با «Target page, context or browser has been closed» یا «Fetch response
 *  has been disposed» می‌افتد. این استثنای بی‌صاحب کلِ فرآیندِ آزمون را
 *  می‌کُشت، حتی وقتی همه‌ی گام‌ها سبز بودند.
 *
 *  `closeSafeRoute` فقط **همین** خطا را نادیده می‌گیرد؛ هر خطای دیگرِ
 *  گرداننده (مثلاً «Route is already handled») مثلِ پیش بالا می‌رود.
 */
const CLOSED_TARGET = /Target page, context or browser has been closed|Target closed|Fetch response has been disposed/;

export function isClosedTargetError(err) {
  return CLOSED_TARGET.test(String((err && err.message) || err || ''));
}

export function closeSafeRoute(handler) {
  return async (route, request) => {
    try {
      return await handler(route, request);
    } catch (err) {
      if (isClosedTargetError(err)) return undefined;
      throw err;
    }
  };
}

/*
 *  axe بر همان حالتی که کاربر می‌بیند (CI ِ #881 و #1020): رنگِ نیمه‌راهِ انیمیشن یا گذار کنتراستِ ساختگی می‌دهد، و
 *  خوابِ ثابت (`waitForTimeout`) روی CI ِ پربار به پایانِ آن نمی‌رسد — انیمیشن با نخستین فریم آغاز می‌شود و آن فریم زیرِ
 *  بار دیر می‌رسد. این‌جا تا وقتی هیچ انیمیشن/گذارِ پایان‌دارِ در جریانی روی خودِ `selector`، درونش یا پدرانش (شفافیتِ
 *  پدر هم رنگ را می‌برد) نمانده صبر می‌شود؛ انیمیشنِ جای دیگرِ صفحه (تزئینی) و انیمیشنِ بی‌پایان شمرده نمی‌شوند.
 *  بازتولید: پخشِ ۷ تا ۲۰ برابر کندِ انیمیشن‌ها با CDP ِ `Animation.setPlaybackRate` (۰٫۱۵–۰٫۰۵) ⇒ همان خطای کنتراستِ CI
 *  روی کدِ پیشین؛ با این صبر سبز.
 */
export async function animationsSettled(page, selector, { timeout = 10_000 } = {}) {
  await page.waitForFunction((sel) => {
    const root = document.querySelector(sel);
    if (!root) return false;
    return document.getAnimations().every((a) => {
      const t = a.effect && a.effect.target;
      if (!t || !(t === root || root.contains(t) || t.contains(root))) return true;
      if (a.effect.getComputedTiming().endTime === Infinity) return true;
      return a.playState !== 'running' && !a.pending;
    });
  }, selector, { timeout });
}
