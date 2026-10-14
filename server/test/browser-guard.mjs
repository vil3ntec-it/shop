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
