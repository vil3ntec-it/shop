/* ⚠️ تولیدشده از keymap.json با node android-native/app/src/main/java/ir/vil3ntec/tohid/core/keymap/gen-keymap.mjs — دستی ویرایش نشود.
 * نقشهٔ کلیدهای وب (ستونِ «وب»ِ جدولِ ۵.۲ و MASTER-SPEC ۳.۱۶). کلید با e.code خوانده می‌شود،
 * پس با چیدمانِ فارسی و پشتو هم کار می‌کند؛ Mod یعنی Ctrl یا Cmd.
 * هر کلید: [نام، Mod، Shift، Alt]. استفاده: TohidKeymap.matches(event, 'global.page1')
 */
(function (root) {
  'use strict';
  var CODES = {"A":["KeyA"],"B":["KeyB"],"C":["KeyC"],"D":["KeyD"],"E":["KeyE"],"F":["KeyF"],"G":["KeyG"],"H":["KeyH"],"I":["KeyI"],"J":["KeyJ"],"K":["KeyK"],"L":["KeyL"],"M":["KeyM"],"N":["KeyN"],"O":["KeyO"],"P":["KeyP"],"Q":["KeyQ"],"R":["KeyR"],"S":["KeyS"],"T":["KeyT"],"U":["KeyU"],"V":["KeyV"],"W":["KeyW"],"X":["KeyX"],"Y":["KeyY"],"Z":["KeyZ"],"D0":["Digit0","Numpad0"],"D1":["Digit1"],"D2":["Digit2"],"D3":["Digit3"],"D4":["Digit4"],"D5":["Digit5"],"D6":["Digit6"],"D7":["Digit7"],"D8":["Digit8"],"D9":["Digit9"],"F1":["F1"],"F2":["F2"],"F3":["F3"],"F4":["F4"],"F5":["F5"],"F6":["F6"],"F7":["F7"],"F8":["F8"],"F9":["F9"],"F10":["F10"],"F11":["F11"],"F12":["F12"],"Enter":["Enter","NumpadEnter"],"Escape":["Escape"],"Delete":["Delete"],"Tab":["Tab"],"Space":["Space"],"Home":["Home"],"End":["End"],"PageUp":["PageUp"],"PageDown":["PageDown"],"Up":["ArrowUp"],"Down":["ArrowDown"],"Left":["ArrowLeft"],"Right":["ArrowRight"],"Plus":["NumpadAdd"],"Minus":["Minus","NumpadSubtract"],"Equals":["Equal"],"Slash":["Slash"]};
  var LABELS = {"A":"A","B":"B","C":"C","D":"D","E":"E","F":"F","G":"G","H":"H","I":"I","J":"J","K":"K","L":"L","M":"M","N":"N","O":"O","P":"P","Q":"Q","R":"R","S":"S","T":"T","U":"U","V":"V","W":"W","X":"X","Y":"Y","Z":"Z","D0":"0","D1":"1","D2":"2","D3":"3","D4":"4","D5":"5","D6":"6","D7":"7","D8":"8","D9":"9","F1":"F1","F2":"F2","F3":"F3","F4":"F4","F5":"F5","F6":"F6","F7":"F7","F8":"F8","F9":"F9","F10":"F10","F11":"F11","F12":"F12","Enter":"Enter","Escape":"Esc","Delete":"Delete","Tab":"Tab","Space":"Space","Home":"Home","End":"End","PageUp":"PgUp","PageDown":"PgDn","Up":"↑","Down":"↓","Left":"←","Right":"→","Plus":"+","Minus":"−","Equals":"=","Slash":"/"};
  var SCOPES = { "global": { "kind": "global", "title": "همه‌جا", "parent": null }, "sale": { "kind": "screen", "title": "صندوقِ فروش", "parent": null }, "sale.payment": { "kind": "window", "title": "پنلِ پرداخت", "parent": "sale" }, "customers": { "kind": "screen", "title": "مشتری‌ها و قرض‌داران", "parent": null }, "supplier": { "kind": "screen", "title": "حسابِ تأمین‌کننده", "parent": null }, "purchase": { "kind": "screen", "title": "خرید", "parent": null }, "orders": { "kind": "screen", "title": "سفارش‌های اینترنتی", "parent": null }, "stocktake": { "kind": "screen", "title": "انبارگردانی", "parent": null }, "reports": { "kind": "screen", "title": "گزارش‌ها", "parent": null } };
  var ACTIONS = {
    "global.help": {"scope":"global","title":"راهنمای کلیدهای همین صفحه","status":"live","page":null,"web":[["Slash",false,false,true]],"overrides":[]},
    "global.palette": {"scope":"global","title":"جست‌وجوی فرمان و همه چیز","status":"live","page":null,"web":[["K",true,false,false]],"overrides":[]},
    "global.page1": {"scope":"global","title":"داشبورد","status":"live","page":"dashboard","web":[["D1",false,false,true]],"overrides":[]},
    "global.page2": {"scope":"global","title":"فروش","status":"live","page":"sale","web":[["D2",false,false,true]],"overrides":[]},
    "global.page3": {"scope":"global","title":"قرض‌داران","status":"live","page":"debtors","web":[["D3",false,false,true]],"overrides":[]},
    "global.page4": {"scope":"global","title":"انبار","status":"live","page":"warehouse","web":[["D4",false,false,true]],"overrides":[]},
    "global.page5": {"scope":"global","title":"محصولات","status":"live","page":"products","web":[["D5",false,false,true]],"overrides":[]},
    "global.page6": {"scope":"global","title":"تاریخچهٔ فروش","status":"live","page":"sales","web":[["D6",false,false,true]],"overrides":[]},
    "global.page7": {"scope":"global","title":"خرید","status":"live","page":"purchasing","web":[["D7",false,false,true]],"overrides":[]},
    "global.page8": {"scope":"global","title":"گزارش‌ها","status":"live","page":"reports","web":[["D8",false,false,true]],"overrides":[]},
    "global.page9": {"scope":"global","title":"مصارف","status":"live","page":"expenses","web":[["D9",false,false,true]],"overrides":[]},
    "global.orders": {"scope":"global","title":"سفارش‌های اینترنتی","status":"planned","page":null,"web":[["O",false,false,true]],"overrides":[]},
    "global.admin": {"scope":"global","title":"مرکزِ مدیریت","status":"live","page":null,"web":[["M",false,false,true]],"overrides":[]},
    "global.new": {"scope":"global","title":"موردِ تازه در همین صفحه","status":"live","page":null,"web":[["N",false,false,true]],"overrides":[]},
    "global.find": {"scope":"global","title":"جست‌وجو در همین صفحه","status":"partial","page":null,"web":[["Slash",false,false,false]],"overrides":[]},
    "global.save": {"scope":"global","title":"ذخیره یا تأیید","status":"partial","page":null,"web":[["S",true,false,false],["Enter",true,false,false]],"overrides":[]},
    "global.print": {"scope":"global","title":"چاپ","status":"partial","page":null,"web":[["P",true,false,false]],"overrides":[]},
    "global.export": {"scope":"global","title":"خروجی","status":"partial","page":null,"web":[["E",true,false,false]],"overrides":[]},
    "global.lock": {"scope":"global","title":"قفلِ صندوق و تعویضِ کاربر با PIN","status":"planned","page":null,"web":[["L",false,false,true]],"overrides":[]},
    "global.zoomIn": {"scope":"global","title":"بزرگ‌نمایی","status":"live","page":null,"web":[],"overrides":[]},
    "global.zoomOut": {"scope":"global","title":"کوچک‌نمایی","status":"live","page":null,"web":[],"overrides":[]},
    "global.zoomReset": {"scope":"global","title":"اندازهٔ عادیِ متن","status":"live","page":null,"web":[],"overrides":[]},
    "global.fullscreen": {"scope":"global","title":"تمام‌صفحه","status":"live","page":null,"web":[],"overrides":[]},
    "global.back": {"scope":"global","title":"برگشت یا بستن (هرگز حذف نمی‌کند)","status":"live","page":null,"web":[["Escape",false,false,false]],"overrides":[]},
    "sale.add": {"scope":"sale","title":"افزودن (بارکد یا نام)","status":"live","page":null,"web":[["Enter",false,false,false]],"overrides":[]},
    "sale.up": {"scope":"sale","title":"ردیف یا پیشنهادِ بالا","status":"live","page":null,"web":[["Up",false,false,false]],"overrides":[]},
    "sale.down": {"scope":"sale","title":"ردیف یا پیشنهادِ پایین","status":"live","page":null,"web":[["Down",false,false,false]],"overrides":[]},
    "sale.qtyUp": {"scope":"sale","title":"یکی بیشتر","status":"live","page":null,"web":[["Plus",false,false,false],["Equals",false,true,false]],"overrides":[]},
    "sale.qtyDown": {"scope":"sale","title":"یکی کمتر","status":"live","page":null,"web":[["Minus",false,false,false]],"overrides":[]},
    "sale.deleteRow": {"scope":"sale","title":"حذفِ ردیف","status":"live","page":null,"web":[["Delete",false,false,false]],"overrides":[]},
    "sale.undo": {"scope":"sale","title":"برگرداندن","status":"planned","page":null,"web":[["Z",true,false,false]],"overrides":[]},
    "sale.customer": {"scope":"sale","title":"انتخاب یا ساختِ مشتری","status":"live","page":null,"web":[["C",false,false,true]],"overrides":[]},
    "sale.editRow": {"scope":"sale","title":"ویرایشِ ردیف","status":"live","page":null,"web":[["J",false,false,true]],"overrides":[]},
    "sale.freeItem": {"scope":"sale","title":"قلمِ آزاد","status":"live","page":null,"web":[["Q",false,false,true]],"overrides":[]},
    "sale.discountRow": {"scope":"sale","title":"تخفیفِ ردیف","status":"planned","page":null,"web":[["X",false,false,true]],"overrides":[]},
    "sale.discountInvoice": {"scope":"sale","title":"تخفیفِ کلِ فاکتور","status":"planned","page":null,"web":[["X",false,true,true]],"overrides":[]},
    "sale.hold": {"scope":"sale","title":"نگه‌داشتنِ سبد","status":"partial","page":null,"web":[["G",false,false,true]],"overrides":[]},
    "sale.heldList": {"scope":"sale","title":"فهرستِ سبدهای نگه‌داشته","status":"partial","page":null,"web":[["G",false,true,true]],"overrides":[]},
    "sale.nextOpen": {"scope":"sale","title":"فاکتورِ بازِ بعدی","status":"partial","page":null,"web":[],"overrides":[]},
    "sale.prevOpen": {"scope":"sale","title":"فاکتورِ بازِ قبلی","status":"partial","page":null,"web":[],"overrides":[]},
    "sale.receive": {"scope":"sale","title":"دریافتِ وجه یا قسط از مشتری","status":"planned","page":null,"web":[["R",false,false,true]],"overrides":[]},
    "sale.reprint": {"scope":"sale","title":"چاپِ دوبارهٔ آخرین رسید","status":"live","page":null,"web":[["P",false,false,true]],"overrides":[]},
    "sale.drawer": {"scope":"sale","title":"باز کردنِ کشوی پول بدون فروش","status":"planned","page":null,"web":[],"overrides":[]},
    "sale.payPanel": {"scope":"sale","title":"پنلِ پرداخت (امروز F9 فروش را فوری می‌بندد)","status":"planned","page":null,"web":[["Enter",false,false,true]],"overrides":[]},
    "sale.returnMode": {"scope":"sale","title":"حالتِ برگشت","status":"planned","page":null,"web":[["Enter",false,true,true]],"overrides":[]},
    "sale.finishExact": {"scope":"sale","title":"پایانِ فوری با نقدِ دقیق","status":"live","page":null,"web":[["Enter",true,false,false]],"overrides":["global.save"]},
    "sale.priceCheck": {"scope":"sale","title":"قیمت‌پرسی بدون افزودن","status":"planned","page":null,"web":[["I",false,false,true]],"overrides":[]},
    "sale.unit": {"scope":"sale","title":"تغییرِ واحدِ ردیف","status":"planned","page":null,"web":[["U",false,false,true]],"overrides":[]},
    "sale.priceList": {"scope":"sale","title":"تغییرِ لیستِ قیمتِ فاکتور","status":"planned","page":null,"web":[["R",false,true,true]],"overrides":[]},
    "sale.clear": {"scope":"sale","title":"پاک کردنِ کادر؛ خالی کردنِ فاکتور فقط با تأیید","status":"live","page":null,"web":[["Escape",false,false,false]],"overrides":["global.back"]},
    "sale.payment.submit": {"scope":"sale.payment","title":"ثبت؛ مبلغِ خالی = نقدِ دقیق","status":"planned","page":null,"web":[["Enter",false,false,false]],"overrides":[]},
    "sale.payment.nextField": {"scope":"sale.payment","title":"فیلدِ بعدی","status":"planned","page":null,"web":[["Tab",false,false,false]],"overrides":[]},
    "sale.payment.prevField": {"scope":"sale.payment","title":"فیلدِ قبلی","status":"planned","page":null,"web":[["Tab",false,true,false]],"overrides":[]},
    "sale.payment.nextMethod": {"scope":"sale.payment","title":"روشِ پرداختِ بعدی","status":"planned","page":null,"web":[["PageDown",false,false,false]],"overrides":[]},
    "sale.payment.prevMethod": {"scope":"sale.payment","title":"روشِ پرداختِ قبلی","status":"planned","page":null,"web":[["PageUp",false,false,false]],"overrides":[]},
    "sale.payment.note10": {"scope":"sale.payment","title":"اسکناسِ ۱۰","status":"planned","page":null,"web":[["D1",false,true,false]],"overrides":[]},
    "sale.payment.note20": {"scope":"sale.payment","title":"اسکناسِ ۲۰","status":"planned","page":null,"web":[["D2",false,true,false]],"overrides":[]},
    "sale.payment.note50": {"scope":"sale.payment","title":"اسکناسِ ۵۰","status":"planned","page":null,"web":[["D3",false,true,false]],"overrides":[]},
    "sale.payment.note100": {"scope":"sale.payment","title":"اسکناسِ ۱۰۰","status":"planned","page":null,"web":[["D4",false,true,false]],"overrides":[]},
    "sale.payment.note500": {"scope":"sale.payment","title":"اسکناسِ ۵۰۰","status":"planned","page":null,"web":[["D5",false,true,false]],"overrides":[]},
    "sale.payment.note1000": {"scope":"sale.payment","title":"اسکناسِ ۱۰۰۰","status":"planned","page":null,"web":[["D6",false,true,false]],"overrides":[]},
    "sale.payment.togglePrint": {"scope":"sale.payment","title":"چاپ برای همین فاکتور: روشن یا خاموش","status":"planned","page":null,"web":[["P",false,false,true]],"overrides":[]},
    "sale.payment.customer": {"scope":"sale.payment","title":"مشتری (برای باقی‌ماندهٔ قرضی)","status":"planned","page":null,"web":[["C",false,false,true]],"overrides":[]},
    "sale.payment.back": {"scope":"sale.payment","title":"برگشت به سبد، بی ثبتِ هیچ چیز","status":"planned","page":null,"web":[["Escape",false,false,false]],"overrides":[]},
    "customers.lend": {"scope":"customers","title":"قرض دادن","status":"live","page":null,"web":[],"overrides":[]},
    "customers.receive": {"scope":"customers","title":"دریافت","status":"live","page":null,"web":[],"overrides":[]},
    "customers.open": {"scope":"customers","title":"باز کردنِ حساب","status":"live","page":null,"web":[],"overrides":[]},
    "supplier.pay": {"scope":"supplier","title":"پرداخت","status":"live","page":null,"web":[],"overrides":[]},
    "purchase.next": {"scope":"purchase","title":"فیلد یا ردیفِ بعدی","status":"live","page":null,"web":[],"overrides":[]},
    "purchase.save": {"scope":"purchase","title":"ذخیرهٔ فاکتور (Ctrl+S و Ctrl+Enter هم)","status":"live","page":null,"web":[],"overrides":[]},
    "purchase.createInline": {"scope":"purchase","title":"کالا یا تأمین‌کنندهٔ تازه، درجا","status":"planned","page":null,"web":[["N",false,true,true]],"overrides":[]},
    "purchase.duplicateRow": {"scope":"purchase","title":"تکرارِ ردیف","status":"planned","page":null,"web":[],"overrides":[]},
    "purchase.lastPrices": {"scope":"purchase","title":"آخرین قیمت‌های خریدِ این کالا","status":"planned","page":null,"web":[],"overrides":[]},
    "orders.open": {"scope":"orders","title":"باز کردن","status":"planned","page":null,"web":[],"overrides":[]},
    "orders.advance": {"scope":"orders","title":"قبول یا مرحلهٔ بعد","status":"planned","page":null,"web":[],"overrides":[]},
    "orders.reject": {"scope":"orders","title":"رد با دلیل","status":"planned","page":null,"web":[],"overrides":[]},
    "orders.printSlip": {"scope":"orders","title":"چاپِ برگه","status":"planned","page":null,"web":[],"overrides":[]},
    "orders.busy": {"scope":"orders","title":"حالتِ «شلوغم» (با مدت)","status":"planned","page":null,"web":[],"overrides":[]},
    "orders.pause": {"scope":"orders","title":"توقفِ موقتِ سفارش‌گیری","status":"planned","page":null,"web":[],"overrides":[]},
    "stocktake.confirm": {"scope":"stocktake","title":"تأیید","status":"planned","page":null,"web":[],"overrides":[]},
    "stocktake.finish": {"scope":"stocktake","title":"بازبینی و پایان","status":"planned","page":null,"web":[],"overrides":[]},
    "reports.drill": {"scope":"reports","title":"ریز شدن در ردیف","status":"planned","page":null,"web":[],"overrides":[]},
    "reports.nextPeriod": {"scope":"reports","title":"دورهٔ بعد","status":"planned","page":null,"web":[],"overrides":[]},
    "reports.prevPeriod": {"scope":"reports","title":"دورهٔ قبل","status":"planned","page":null,"web":[],"overrides":[]},
    "reports.compare": {"scope":"reports","title":"ستونِ مقایسه با دورهٔ قبل","status":"planned","page":null,"web":[],"overrides":[]}
  };
  function chordMatches(e, c) {
    return CODES[c[0]].indexOf(e.code) >= 0 &&
      !!(e.ctrlKey || e.metaKey) === c[1] && !!e.shiftKey === c[2] && !!e.altKey === c[3];
  }
  function action(id) {
    var a = ACTIONS[id];
    if (!a) throw new Error('کلیدِ ناشناخته در keymap: ' + id);
    return a;
  }
  /** آیا این رویدادِ keydown کارِ id است؟ */
  function matches(e, id) {
    var w = action(id).web;
    for (var i = 0; i < w.length; i++) if (chordMatches(e, w[i])) return true;
    return false;
  }
  /** برچسبِ نخستین کلیدِ وب: «Alt+1»، روی مک «Cmd+K» */
  function label(id, mac) {
    var c = action(id).web[0];
    if (!c) return '';
    var p = [];
    if (c[1]) p.push(mac ? 'Cmd' : 'Ctrl');
    if (c[3]) p.push(mac ? 'Option' : 'Alt');
    if (c[2]) p.push('Shift');
    p.push(LABELS[c[0]]);
    return p.join('+');
  }
  var api = { CODES: CODES, SCOPES: SCOPES, ACTIONS: ACTIONS, matches: matches, label: label };
  if (typeof module === 'object' && module.exports) module.exports = api;
  else root.TohidKeymap = api;
})(typeof window !== 'undefined' ? window : globalThis);
