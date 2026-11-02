# Owner request (1405/08/11): Devices hub + scans while app closed/phone off
Owner words (translated): "I have several devices; I want to put all of them in the app — with one Bluetooth?" and
"I want the app such that if it's not open or the phone is off, if someone has a scanner and scans, it still works."

Build (behind sensible defaults per the new settings rule: essential setup page = visible; no money changes):
1. «دستگاه‌ها» page (v3 settings, class C; legacy + desktop entry): one «جست‌وجو» finds all nearby/paired BT devices;
   auto-classify (printer / scanner / scale / customer display) from BT class, BLE services and name patterns;
   connect several at once; auto-reconnect; green/red status per device; assign roles; test print/scan/weigh.
   Build on existing: EasySetup (HW-12, `tohid-ui:easy_setup`), hw/PrinterFinderAndroid.kt, print/ThermalPrinter.kt,
   HidScan (HW-05), scale protocols (HW-09, core), customer display (HW-10). Cash drawer = via printer (ESC p), not BT.
2. Background scan capture (Android): foreground service (persistent notification, required by Android) that keeps
   SPP/BLE scanners connected and appends scans to a durable local queue while the app is closed. Detect HID-keyboard
   mode and show the scanner's mode-switch guidance (cannot capture HID in background).
3. Offline scanner memory import: on reconnect, pull the scanner's stored batch (many BT scanners have
   inventory/storage mode; data arrives as a burst) into the same queue, de-duplicated by (device, time, code).
4. On app open: «N اسکن در انتظار» ⇒ choose target: انبارگردانی / ورودِ کالا / فاکتورِ فروش. Sales require explicit
   confirmation (never silently create a sale). Unknown barcodes ⇒ existing «ثبت کالا» flow.
5. iPhone: keep logic shared (core/commonMain); platform code only in *Android.kt; document iOS limits (BLE only).
6. Tests: classification vectors, queue durability/dedupe, burst import, "no silent sale" rule.
7. STATUS: HW-12/HW-05/HW-09/HW-10 text updates; RULES note. Text: clear standard Farsi (a Dari overlay exists separately).
8. Desktop (Windows) too — owner asked "or on the computer as well?": tray/background part that starts with Windows
   (opt-in from the «دستگاه‌ها» page; default per settings rule), keeps BT-SPP (virtual COM) and USB-COM/CDC scanners
   open via the existing jSerialComm path (same as HW-09 scale), appends scans to the same durable queue while the main
   window is closed; HID-keyboard mode detection + mode-switch guidance; scanner-memory burst import on reconnect;
   same «N اسکن در انتظار» prompt and "no silent sale" rule. Desktop «دستگاه‌ها» page lists COM/BT/USB devices with status.
9. Payment after background scans — owner asked "how is the payment recorded after scanning?":
   a) DEFAULT (safe): queued sale-scans become a HELD invoice («فاکتورِ در انتظار»; same barcode N times ⇒ qty N, reuse
      the existing hold/park mechanism POS-12). On app open: show it with the total; one tap «پرداخت» ⇒ normal pay panel
      (cash / credit / HesabPay) ⇒ sale recorded by the normal SalesEngine path. Nothing is recorded without that tap.
   b) OPTIONAL «کارتِ فرمان» (class B — owner turns it on; default OFF): the app prints a command card with control
      barcodes «فاکتورِ تازه»، «نقد پرداخت شد»، «حذفِ آخرین کالا» (signed/namespaced payloads so they can never collide with
      product barcodes). With it ON: items + «نقد پرداخت شد» ⇒ the background part records a CASH sale for the exact total
      via the normal SalesEngine.record path (same rounding/rules as the till, PAY-09 aware), prints the receipt if a printer
      is connected, and marks it «ثبت از اسکنر» (visible in invoices and the shift/X-Z report). Cash only; credit/customer
      sales always go through (a). Same sequence from scanner memory (phone/PC was off) ⇒ invoices rebuilt in order on
      import, marked «ثبت از اسکنر — از حافظه», time = import time with the note. Staff permissions: the background
      user is the device's signed-in till user; if the till/shift is closed or the user lacks sale permission ⇒ fall back to (a).
   Tests: held-invoice building, command-sequence parser (incl. missing «نقد», double «نقد», unknown codes), no recording
   without (a)'s tap when (b) is off, (b) cash-only, rounding applied, idempotent import (no double sales on re-import).
