'use strict';
/**
 * تصویرهای ایمیل — همان SVGهای فایلِ صاحبِ سامانه، به PNG.
 *
 * چرا: جیمیل و بیشترِ برنامه‌های ایمیل SVGِ داخلِ HTML را نشان نمی‌دهند
 * (و data: را هم نه). پس هر شکلِ قالب یک بار با کرومیوم به PNGِ سه‌برابر
 * تبدیل و کنارِ قالب گذاشته می‌شود؛ ایمیل آن‌ها را با `cid:` پیوست می‌کند.
 *
 *   node scripts/build-mail-images.cjs <مسیرِ playwright-core> [مسیرِ کرومیوم]
 *
 * ⚠️ فقط سازنده است و سرور به آن نیازی ندارد؛ PNGها در مخزن‌اند.
 */
const fs = require('fs');
const path = require('path');
const { chromium } = require(process.argv[2] || 'playwright-core');

const DIR = path.join(__dirname, '..', 'src', 'lib', 'mail-templates');

//  نام به ترتیبِ SVGها در هر فایل (همان ترتیبِ خودِ فایل)
const NAMES = {
  pump: ['heart', 'mark', 'copy', 'clock', 'shield', 'bolt', 'layout', 'qr', 'star', 'chat', 'banner', 'phone', 'mail'],
  shop: ['logo', 'verify', 'hi', 'perk1', 'perk2', 'perk3', 'art', 'lock', 'copy', 'tile1', 'tile2', 'tile3',
    'feat1', 'feat2', 'feat3', 'footlogo', 'web', 'msg', 'tel', 'email', 'tel2', 'mail2', 'heart', 'mascot'],
};
//  رنگِ `currentColor` همان رنگی که نوشتهٔ پدر در قالب دارد
const WIDE = { banner: 340, art: 390, mascot: 104 };
const CURRENT = { pump: { copy: '#201503' }, shop: { copy: '#241805', web: '#F2D9A8', msg: '#F2D9A8', tel: '#F2D9A8', email: '#F2D9A8' } };

(async () => {
  const b = await chromium.launch(process.argv[3] ? { executablePath: process.argv[3] } : {});
  const p = await b.newPage({ deviceScaleFactor: 3 });
  for (const [app, names] of Object.entries(NAMES)) {
    const html = fs.readFileSync(path.join(DIR, `${app}-code.html`), 'utf8')
      .replace(/<script[^]*?<\/script>/, '');
    let svgs = [...html.matchAll(/<svg[^]*?<\/svg>/g)].map((m) => m[0]);
    //  SVGِ بی‌اندازه‌ای که فقط <defs>ِ مشترک دارد، به هر شکل ضمیمه می‌شود
    const defs = svgs.filter((x) => /^<svg width="0" height="0"/.test(x)).join('');
    svgs = svgs.filter((x) => !/^<svg width="0" height="0"/.test(x));
    if (svgs.length !== names.length) throw new Error(`${app}: ${svgs.length} svg, ${names.length} names`);
    const out = path.join(DIR, `${app}-img`);
    fs.mkdirSync(out, { recursive: true });
    for (let i = 0; i < svgs.length; i++) {
      let svg = svgs[i].replace(/currentColor/g, (CURRENT[app] || {})[names[i]] || '#201503');
      //  شکل‌های بی‌اندازه (بنر، صحنه، نقش) پهنای خودشان در قالب را می‌گیرند؛ بلندی از viewBox
      if (!/\swidth="/.test(svg.slice(0, svg.indexOf('>')))) {
        const w = WIDE[names[i]] || 340;
        const vb = /viewBox="0 0 ([\d.]+) ([\d.]+)"/.exec(svg);
        const hgt = vb ? Math.round(w * Number(vb[2]) / Number(vb[1])) : w;
        svg = svg.replace('<svg', `<svg width="${w}" height="${hgt}"`);
      }
      await p.setContent(`<html><body style="margin:0;background:transparent">${defs}${svg}</body></html>`);
      const el = (await p.$$('svg')).pop();
      await el.screenshot({ path: path.join(out, `${names[i]}.png`), omitBackground: true });
    }
    console.log(app, svgs.length, 'تصویر');
  }
  await b.close();
})().catch((e) => { console.error(e); process.exit(1); });
