# مشخصاتِ جامع نسخهٔ ۳ — نمایه (docs/spec)

> این فایل را `node docs/spec/tools/spec.mjs split` می‌سازد؛ دستی ویرایش نکن. منبع فقط `MASTER-SPEC.md` (نسخهٔ ۳) است.

**قاعده:** برای هر کار اول همین فایل، بعد **فقط** تکه‌هایی را بخوان که جدول‌های زیر می‌گویند. `MASTER-SPEC.md` را
کامل نخوان؛ تکه‌ها بایت‌به‌بایت همان متن‌اند.

- منبعِ حقیقت: `MASTER-SPEC.md` (SHA-256 `9e4a59f3e95d073e25c4d3bb5465b330893d8dfb9b9333d3a22551890c850797`، ۶۸۱۸۲۹ بایت). فهرستِ تکه‌ها و SHA-256ِ هر کدام در
  `manifest.json` است؛ `check` می‌سنجد که چسباندنِ تکه‌ها به ترتیبِ آن دقیقاً همان SHA-256ِ اصل است (§۲۸.۲). این
  نمایه و `manifest.json` بیرون از چسباندن‌اند.
- نقطهٔ شروع (پیامِ شروعِ رسمی): `00-start.md`، سپس `15-corrections.md`، `26-completion.md`، `28-safe-execution.md`، `39-disambiguation.md`. قراردادِ رفعِ ابهام و
  تحویل: بخش ۳۹.
- وضعیتِ هر شناسه در `STATUS.md`، تصمیم‌های باز و موانع در `BLOCKERS.md`. یافتنِ یک شناسه:
  `grep -n 'POS-14' docs/spec/STATUS.md` و `grep -ln 'POS-14' docs/spec/[0-9]*.md`.
- شمارِ واقعیِ این نسخه (استخراج از متن، نه عددِ تاریخیِ ۴۰۸ یا S01–S144): ۷۶۴ شناسهٔ یکتا در
  ۵۷ خانواده، ۷۶۴ ردیفِ STATUS؛ ۴۰۸ قابلیتِ بخش ۶، ۱۴۶ سناریو،
  ۲۶ دروازهٔ تصمیم. ۲۴ شناسه چندجا تعریف شده (BLOCKERS.md). ۲۷۳ ردیف در خودِ سند فاز ندارد؛
  ۲۸۰ قابلیتِ بخش ۶ در هیچ سناریو/آزمونی نام برده نشده است.

## کدام تکه برای کدام فاز

| فاز | شناسه‌های غیرِ سناریو | سناریوهای این فاز | تکه‌هایی که باید خوانده شوند |
|---|---|---|---|
| ۰ | ۳۲ | — | `02-current.md`، `06-SEC.md`، `08-phases.md`، `09-testing.md`، `10-security.md`، `11-operations.md`، `12-report.md`، `13-decisions.md`، `15-corrections.md`، `28-safe-execution.md`، `39-disambiguation.md` |
| ۱ | ۳۳ | S10، S26، S27، S28، S28b، S47، S143 | `01-goals.md`، `03-architecture.md`، `04-afghanistan.md`، `06-POS.md`، `06-PRD.md`، `06-INV.md`، `06-CRM.md`، `06-STF.md`، `06-SYN.md`، `06-SEC.md`، `06-LOC.md`، `06-OPS.md`، `07-1.md`، `07-3.md`، `07-5.md`، `08-phases.md`، `09-testing.md`، `12-report.md`، `13-decisions.md`، `15-corrections.md`، `28-safe-execution.md`، `39-disambiguation.md` |
| ۲ | ۹۱ | S01، S02، S03، S05، S06، S07، S08، S09، S11، S12، S13، S14، S15، S16، S17، S18، S44، S45، S46، S50، S141، S144 | `01-goals.md`، `05-ux.md`، `06-POS.md`، `06-PAY.md`، `06-PRD.md`، `06-CRM.md`، `06-RET.md`، `06-STF.md`، `06-RPT.md`، `06-HW.md`، `06-SEC.md`، `06-OPS.md`، `07-1.md`، `07-5.md`، `08-phases.md`، `09-testing.md`، `12-report.md`، `13-decisions.md`، `15-corrections.md`، `16-acceptance.md`، `28-safe-execution.md`، `39-disambiguation.md` |
| ۳ | ۶۷ | S04، S19، S20، S21، S22، S23، S24، S25، S29، S48 | `03-architecture.md`، `06-POS.md`، `06-PAY.md`، `06-PRD.md`، `06-PRM.md`، `06-INV.md`، `06-PUR.md`، `06-CRM.md`، `06-STF.md`، `06-RPT.md`، `06-HW.md`، `06-SYN.md`، `06-AI.md`، `06-OPS.md`، `07-1.md`، `07-2.md`، `07-3.md`، `07-5.md`، `08-phases.md`، `09-testing.md`، `12-report.md`، `13-decisions.md`، `15-corrections.md`، `28-safe-execution.md`، `39-disambiguation.md` |
| ۴ | ۱۲۶ | S30، S31، S32، S33، S34، S35، S36، S37، S39، S40، S41، S42، S43، S49، S142 | `03-architecture.md`، `04-afghanistan.md`، `06-POS.md`، `06-PAY.md`، `06-PRD.md`، `06-INV.md`، `06-CRM.md`، `06-RPT.md`، `06-SYN.md`، `06-SEC.md`، `06-WEB.md`، `06-CHK.md`، `06-ORD.md`، `06-PCK.md`، `06-DLV.md`، `06-NTF.md`، `06-MKT.md`، `06-RSK.md`، `06-LOC.md`، `06-OPS.md`، `07-3.md`، `07-4.md`، `07-5.md`، `08-phases.md`، `09-testing.md`، `10-security.md`، `11-operations.md`، `12-report.md`، `13-decisions.md`، `15-corrections.md`، `28-safe-execution.md`، `39-disambiguation.md` |
| ۵ | ۸۱ | S38 | `03-architecture.md`، `06-POS.md`، `06-PAY.md`، `06-PRD.md`، `06-PRM.md`، `06-INV.md`، `06-PUR.md`، `06-CRM.md`، `06-RET.md`، `06-STF.md`، `06-RPT.md`، `06-HW.md`، `06-SEC.md`، `06-WEB.md`، `06-CHK.md`، `06-ORD.md`، `06-PCK.md`، `06-DLV.md`، `06-NTF.md`، `06-MKT.md`، `06-RSK.md`، `06-AI.md`، `06-OPS.md`، `07-4.md`، `08-phases.md`، `12-report.md`، `13-decisions.md`، `15-corrections.md`، `28-safe-execution.md`، `39-disambiguation.md` |
| ۶ | ۶ | — | `03-architecture.md`، `06-POS.md`، `06-PAY.md`، `06-INV.md`، `06-PUR.md`، `06-RPT.md`، `08-phases.md`، `12-report.md`، `13-decisions.md`، `15-corrections.md`، `28-safe-execution.md`، `39-disambiguation.md` |
| بی‌فاز | ۲۷۳ | — | جدولِ «کدام تکه برای کدام موضوع» |

## کدام تکه برای کدام موضوع (خانوادهٔ شناسه)

| خانواده | شمار | تعریف در | اصلاحیه در |
|---|---|---|---|
| F0 | ۲۸ | `02-current.md` | `15-corrections.md`، `29-auth-security-annex.md` |
| POS | ۴۲ | `06-POS.md` | — |
| PAY | ۲۴ | `06-PAY.md` | — |
| PRD | ۲۷ | `06-PRD.md` | — |
| PRM | ۱۸ | `06-PRM.md` | — |
| INV | ۲۱ | `06-INV.md`، `32-catalog-stock.md`، `38-gap-closure.md` | — |
| PUR | ۱۴ | `06-PUR.md` | — |
| CRM | ۱۶ | `06-CRM.md` | — |
| RET | ۱۳ | `06-RET.md` | — |
| STF | ۱۱ | `06-STF.md` | — |
| RPT | ۱۸ | `06-RPT.md` | — |
| HW | ۱۳ | `06-HW.md` | — |
| SYN | ۱۴ | `06-SYN.md` | — |
| SEC | ۱۰ | `06-SEC.md`، `38-gap-closure.md` | — |
| WEB | ۲۵ | `06-WEB.md` | — |
| CHK | ۲۴ | `06-CHK.md` | — |
| ORD | ۲۱ | `06-ORD.md`، `38-gap-closure.md` | — |
| PCK | ۱۴ | `06-PCK.md` | — |
| DLV | ۲۰ | `06-DLV.md` | — |
| NTF | ۱۰ | `06-NTF.md` | — |
| MKT | ۱۲ | `06-MKT.md` | — |
| RSK | ۱۰ | `06-RSK.md` | — |
| AI | ۱۲ | `06-AI.md` | — |
| LOC | ۱۰ | `06-LOC.md` | — |
| OPS | ۹ | `06-OPS.md`، `29-auth-security-annex.md`، `38-gap-closure.md` | — |
| S | ۱۴۶ | `07-1.md`، `07-2.md`، `07-3.md`، `07-4.md`، `07-5.md`، `16-acceptance.md`، `19-final-requirements.md`، `20-product-decisions.md`، `21-accounts-support.md`، `23-marketplace-login.md`، `28-safe-execution.md` | — |
| E2E-MKT | ۲ | `24-evidence-audit.md` | — |
| E2E-ORD | ۲ | `24-evidence-audit.md` | — |
| E2E-STK | ۱ | `24-evidence-audit.md` | — |
| E2E-PCK | ۱ | `24-evidence-audit.md` | — |
| E2E-DLV | ۲ | `24-evidence-audit.md` | — |
| E2E-CASH | ۱ | `24-evidence-audit.md` | — |
| E2E-PAY | ۱ | `24-evidence-audit.md` | — |
| E2E-RET | ۱ | `24-evidence-audit.md` | — |
| E2E-SYNC | ۱ | `24-evidence-audit.md` | — |
| E2E-SEC | ۱ | `24-evidence-audit.md` | — |
| E2E-OPS | ۱ | `24-evidence-audit.md` | — |
| E2E-ADM | ۱ | `24-evidence-audit.md` | — |
| AUTH | ۱۰ | `29-auth-security-annex.md` | — |
| SUB | ۱۰ | `29-auth-security-annex.md` | — |
| TEN | ۴ | `29-auth-security-annex.md` | — |
| DATA | ۱۰ | `29-auth-security-annex.md` | — |
| PERF | ۶ | `29-auth-security-annex.md` | — |
| NOTIF | ۱۲ | `30-order-notify.md` | — |
| PRES | ۱۰ | `31-store-presence.md` | — |
| CHAT | ۱۵ | `33-chat.md` | — |
| IMG | ۱۵ | `34-product-images.md` | — |
| GATE-NEW | ۶ | `35-new-gate.md` | — |
| IMG-PRO | ۱۸ | `36-image-studio.md` | — |
| IMG-BANK | ۱۸ | `37-image-bank.md` | — |
| DEL | ۲ | `38-gap-closure.md` | — |
| FIN | ۱ | `38-gap-closure.md` | — |
| SRCH | ۱ | `38-gap-closure.md` | — |
| REC | ۱ | `38-gap-closure.md` | — |
| REL | ۱ | `38-gap-closure.md` | — |
| ABUSE | ۱ | `38-gap-closure.md` | — |
| D | ۲۶ | `13-decisions.md`، `15-corrections.md` | — |

## همهٔ تکه‌ها به ترتیب

| پرونده | بخش | خط‌ها در اصل | حجم |
|---|---|---|---|
| `00-intro.md` | مقدمه: این سند چیست و چطور استفاده شود | ۰–۳۲ | ۳.۸ KB |
| `00-start.md` | پیام شروع رسمی (تنها متن اجرایی معتبر) | ۳۴–۴۳ | ۳ KB |
| `01-goals.md` | ۱. هدف، معیار برتری و قوانین غیرقابل‌مذاکره | ۴۴–۱۲۴ | ۱۰.۷ KB |
| `02-current.md` | ۲. وضعیت فعلی: آنچه حفظ می‌شود و آنچه باید اصلاح شود | ۱۲۵–۲۳۹ | ۲۱.۷ KB |
| `03-architecture.md` | ۳. معماری هدف | ۲۴۰–۶۴۶ | ۴۴.۱ KB |
| `04-afghanistan.md` | ۴. شرایط افغانستان و الزامات ناشی از آن | ۶۴۷–۸۳۶ | ۱۵.۴ KB |
| `05-ux.md` | ۵. تجربه کاربری: کیبورد، لمس و سیستم طراحی | ۸۳۷–۱۰۲۹ | ۱۹.۲ KB |
| `06-intro.md` | ۶. فهرست کامل قابلیت‌ها — راهنما | ۱۰۳۰–۱۰۴۶ | ۰.۸ KB |
| `06-POS.md` | ۶.۱ صندوق فروش (POS) | ۱۰۴۷–۱۰۹۱ | ۷.۵ KB |
| `06-PAY.md` | ۶.۲ پرداخت و صندوق پول (PAY) | ۱۰۹۲–۱۱۱۸ | ۴ KB |
| `06-PRD.md` | ۶.۳ کالا، واحد و قیمت (PRD) | ۱۱۱۹–۱۱۴۸ | ۴ KB |
| `06-PRM.md` | ۶.۴ تخفیف، وفاداری و کارت هدیه (PRM) | ۱۱۴۹–۱۱۶۹ | ۲.۳ KB |
| `06-INV.md` | ۶.۵ انبار (INV) | ۱۱۷۰–۱۱۹۳ | ۳.۴ KB |
| `06-PUR.md` | ۶.۶ خرید و تأمین‌کننده (PUR) | ۱۱۹۴–۱۲۱۰ | ۲.۳ KB |
| `06-CRM.md` | ۶.۷ مشتری و حساب قرض (CRM) | ۱۲۱۱–۱۲۲۹ | ۲.۱ KB |
| `06-RET.md` | ۶.۸ برگشت و تعویض (RET) | ۱۲۳۰–۱۲۴۵ | ۱.۷ KB |
| `06-STF.md` | ۶.۹ کارمندان و مجوزها (STF) | ۱۲۴۶–۱۲۵۹ | ۱.۷ KB |
| `06-RPT.md` | ۶.۱۰ گزارش و تحلیل (RPT) | ۱۲۶۰–۱۲۸۱ | ۲.۳ KB |
| `06-HW.md` | ۶.۱۱ دستگاه‌ها (HW) | ۱۲۸۲–۱۳۰۶ | ۲.۶ KB |
| `06-SYN.md` | ۶.۱۲ آفلاین و همگام‌سازی (SYN) | ۱۳۰۷–۱۳۲۳ | ۲.۲ KB |
| `06-SEC.md` | ۶.۱۳ امنیت (SEC). جزئیات در بخش ۱۰. | ۱۳۲۴–۱۳۳۶ | ۱.۴ KB |
| `06-WEB.md` | ۶.۱۴ ویترین اینترنتی (WEB) | ۱۳۳۷–۱۳۶۹ | ۴ KB |
| `06-CHK.md` | ۶.۱۵ سبد و ثبت سفارش (CHK) | ۱۳۷۰–۱۳۹۶ | ۳.۵ KB |
| `06-ORD.md` | ۶.۱۶ دریافت سفارش در فروشگاه (ORD) | ۱۳۹۷–۱۴۲۰ | ۳.۶ KB |
| `06-PCK.md` | ۶.۱۷ جمع‌آوری و بسته‌بندی (PCK) | ۱۴۲۱–۱۴۳۷ | ۲.۱ KB |
| `06-DLV.md` | ۶.۱۸ تحویل و پیک (DLV) | ۱۴۳۸–۱۴۶۳ | ۳.۱ KB |
| `06-NTF.md` | ۶.۱۹ اطلاع‌رسانی و پشتیبانی (NTF) | ۱۴۶۴–۱۴۷۶ | ۱.۴ KB |
| `06-MKT.md` | ۶.۲۰ بازاریابی (MKT) | ۱۴۷۷–۱۴۹۱ | ۱.۴ KB |
| `06-RSK.md` | ۶.۲۱ تقلب و ریسک (RSK) | ۱۴۹۲–۱۵۰۴ | ۱.۵ KB |
| `06-AI.md` | ۶.۲۲ هوش مصنوعی (AI) | ۱۵۰۵–۱۵۲۱ | ۲.۳ KB |
| `06-LOC.md` | ۶.۲۳ بومی‌سازی (LOC) | ۱۵۲۲–۱۵۳۴ | ۱.۵ KB |
| `06-OPS.md` | ۶.۲۴ عملیات و پشتیبانی محصول (OPS) | ۱۵۳۵–۱۵۶۰ | ۲.۱ KB |
| `07-0.md` | ۷. سناریوهای کامل و معیار قبولی — قواعد | ۱۵۶۱–۱۵۷۰ | ۱.۲ KB |
| `07-1.md` | ۷.۱ صندوق | ۱۵۷۱–۱۷۵۰ | ۱۲.۶ KB |
| `07-2.md` | ۷.۲ انبار و خرید | ۱۷۵۱–۱۸۰۵ | ۴.۳ KB |
| `07-3.md` | ۷.۳ آفلاین و همگام‌سازی | ۱۸۰۶–۱۸۶۰ | ۴ KB |
| `07-4.md` | ۷.۴ فروش اینترنتی و تحویل | ۱۸۶۱–۱۹۵۰ | ۷.۲ KB |
| `07-5.md` | ۷.۵ کارمندان، اشتراک و گزارش | ۱۹۵۱–۱۹۸۷ | ۲.۸ KB |
| `07-6.md` | ۷.۶ سی کار روزانه برای سنجش «۹۰٪ بدون موس» | ۱۹۸۸–۲۰۲۸ | ۲.۵ KB |
| `08-phases.md` | ۸. فازبندی و ترتیب اجرا | ۲۰۲۹–۲۱۴۲ | ۸.۸ KB |
| `09-testing.md` | ۹. آزمایش، کارایی و پایداری | ۲۱۴۳–۲۲۳۳ | ۷.۲ KB |
| `10-security.md` | ۱۰. امنیت و حریم خصوصی | ۲۲۳۴–۲۳۶۰ | ۱۱.۵ KB |
| `11-operations.md` | ۱۱. عملیات، انتشار و پایش | ۲۳۶۱–۲۴۰۴ | ۳.۶ KB |
| `12-report.md` | ۱۲. پروتکل گزارش و تعریف «تمام‌شده» | ۲۴۰۵–۲۴۵۰ | ۳ KB |
| `13-decisions.md` | ۱۳. دروازه‌های تصمیم صاحب محصول | ۲۴۵۱–۲۴۸۳ | ۴.۹ KB |
| `14-sources.md` | ۱۴. منابع اصلی | ۲۴۸۴–۲۵۳۲ | ۴.۶ KB |
| `15-corrections.md` | ۱۵. اصلاحات الزام‌آور و رفع ابهام‌ها (نسخهٔ ۱۵ اکتبر ۲۰۲۶) | ۲۵۳۳–۲۶۹۲ | ۳۷.۷ KB |
| `16-acceptance.md` | ۱۶. آزمون‌های پذیرش جدید (مانع انتشار) | ۲۶۹۳–۲۷۱۳ | ۴.۶ KB |
| `17-release-checklist.md` | ۱۷. چک‌لیست انتشار عمومی | ۲۷۱۴–۲۷۳۱ | ۱.۹ KB |
| `18-changelog.md` | ۱۸. تاریخچهٔ اصلاحات این سند | ۲۷۳۲–۲۷۳۷ | ۰.۷ KB |
| `19-final-requirements.md` | ۱۹. الزامات تکمیلی نهایی: محصول ساده در ظاهر، دقیق در داخل | ۲۷۳۸–۲۹۶۶ | ۵۶.۱ KB |
| `20-product-decisions.md` | ۲۰. تصمیم‌های تکمیلی محصول، فناوری و تجربهٔ استفاده (بازبینی ۱۵ اکتبر ۲۰۲۶) | ۲۹۶۷–۳۱۲۷ | ۳۶.۱ KB |
| `21-accounts-support.md` | 21. حساب‌ها، پشتیبانی، اعتماد، پرداخت، پیام‌رسانی، شارژ سیم‌کارت، امنیت اشتراک… | ۳۱۲۸–۳۳۴۸ | ۵۵.۱ KB |
| `22-execution-order.md` | 22. ترتیب اجرای پروژه: اول صحت عملکرد، بعد بررسی عمیق امنیت | ۳۳۴۹–۳۴۱۳ | ۹.۹ KB |
| `23-marketplace-login.md` | 23. تجربهٔ ورود مشتری در بازارچه: مشاهده آزاد، ثبت سفارش با تأیید | ۳۴۱۴–۳۴۷۰ | ۸.۴ KB |
| `24-evidence-audit.md` | 24. ممیزی شواهدی نسخهٔ فعلی و نمره‌گذاری قابل‌تکرار (افزوده‌شده: ۱۵ اکتبر ۲۰۲۶) | ۳۴۷۱–۳۶۲۸ | ۲۶.۲ KB |
| `25-dev-instructions.md` | 25. دستور نهایی برای دستیار توسعه: اجرا، بازسنجی و اعلام نمره | ۳۶۲۹–۳۶۴۵ | ۳.۵ KB |
| `26-completion.md` | 26. فرمان تکمیل واقعی تا ۱۰ از ۱۰ — بدون نمره‌سازی و بدون حدس | ۳۶۴۶–۳۷۵۹ | ۱۷.۷ KB |
| `27-old-start.md` | ⚠️ **منسوخ — فقط تاریخچه** · ۲۷. پیام شروع قدیمی (منسوخ) | ۳۷۶۰–۳۷۶۴ | ۰.۴ KB |
| `28-safe-execution.md` | ۲۸. قرارداد نهایی اجرای ایمن و محدودیت‌های باز | ۳۷۶۵–۳۸۰۹ | ۸.۳ KB |
| `29-auth-security-annex.md` | ۲۹. ضمیمهٔ الزام‌آور نهایی: ورود، اشتراک، امنیت، حریم خصوصی، صحت داده و مقیاس | ۳۸۱۰–۳۹۵۵ | ۲۴ KB |
| `30-order-notify.md` | ۳۰. الزام قطعی اعلان سفارش حتی وقتی برنامهٔ گوشی بسته است | ۳۹۵۶–۴۰۱۲ | ۹.۱ KB |
| `31-store-presence.md` | ۳۱. وضعیت آنلاین/آفلاین و ساعت کاری فروشگاه | ۴۰۱۳–۴۰۵۰ | ۷.۴ KB |
| `32-catalog-stock.md` | ۳۲. کاتالوگ واحد و موجودی هوشمند فروشگاه، قفسه و انبار | ۴۰۵۱–۴۰۹۹ | ۱۰.۲ KB |
| `33-chat.md` | ۳۳. چت و پیام‌رسانی قابل اعتماد، باکیفیت و کم‌مصرف | ۴۱۰۰–۴۱۴۷ | ۹.۲ KB |
| `34-product-images.md` | ۳۴. مدیریت حرفه‌ای تصاویر محصولات: بهبود، ورود گروهی و کنترل کیفیت | ۴۱۴۸–۴۱۹۶ | ۹.۹ KB |
| `35-new-gate.md` | ۳۵. دروازهٔ پذیرش برای قابلیت‌های جدید | ۴۱۹۷–۴۲۱۶ | ۲.۳ KB |
| `36-image-studio.md` | ۳۶. سامانهٔ حرفه‌ای آماده‌سازی، ترمیم و کنترل کیفیت تصاویر محصول (Product Image… | ۴۲۱۷–۴۳۸۱ | ۲۴.۱ KB |
| `37-image-bank.md` | ۳۷. بانک مرکزی تصاویر محصولات + جست‌وجو و انتخاب هوشمند در استودیوی تصویر | ۴۳۸۲–۴۵۲۲ | ۱۹.۲ KB |
| `38-gap-closure.md` | ۳۸. تکمیل شکاف‌های محصول و عملیات بر اساس ممیزی قابلیت‌های حرفه‌ای | ۴۵۲۳–۴۶۱۶ | ۱۳.۷ KB |
| `39-disambiguation.md` | ۳۹. قرارداد نهایی رفع ابهام برای ایجنت توسعه (افزوده‌شده: ۱۷ اکتبر ۲۰۲۶) | ۴۶۱۷–۴۶۶۷ | ۹.۷ KB |
