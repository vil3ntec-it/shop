# ۳۵. دروازهٔ پذیرش برای قابلیت‌های جدید

- **GATE-NEW-01:** PRES-01 تا PRES-10 روی API و رابط‌های هدف اجرا شوند؛ تفاوت «ساعت کاری»، «حضور اعلام‌شده»، «heartbeat» و «امکان سفارش» در UI و داده روشن باشد.
- **GATE-NEW-02:** INV-01 تا INV-16 با سناریوهای هم‌زمانی، آفلاین، رزرو و تطبیق دفتر گردش اجرا شوند؛ موجودی پس از هر سناریو با محاسبهٔ مستقل تطبیق داده شود.
- **GATE-NEW-03:** CHAT-01 تا CHAT-15 روی دستگاه‌ها/شبکه‌های هدف اجرا شوند؛ مصرف داده، ترتیب پیام، بازیابی، حذف و دسترسی رسانه با شواهد ثبت شود.
- **GATE-NEW-04:** IMG-01 تا IMG-15 با مجموعهٔ تصویری واقعی و مجاز، قبل/بعد و بررسی انسانی اجرا شوند؛ حفظ اصل و rollback اثبات شود.
- **GATE-NEW-05:** برای هر تست شناسه، build/commit، محیط، دستگاه/نسخه، دادهٔ آزمون غیرحساس، گام‌های بازتولید، خروجی واقعی، log/screenshot مناسب و نتیجه ثبت شود. mock یا تست واحد به‌تنهایی اثبات end-to-end نیست.
- **GATE-NEW-06:** هر مورد تا وقتی روی پیاده‌سازی واقعی پاس نشده `NOT-TESTED` یا `BLOCKED-EXTERNAL` است. اضافه‌کردن مشخصات به این سند به معنی پیاده‌سازی یا قبولی قابلیت نیست.

## منابع فنی پیشنهادی برای تطبیق پیاده‌سازی
- Android connectivity / offline-first: https://developer.android.com/topic/architecture/data-layer/offline-first
- PostgreSQL transactions: https://www.postgresql.org/docs/current/tutorial-transactions.html
- PostgreSQL explicit locking: https://www.postgresql.org/docs/current/explicit-locking.html
- WebSocket API: https://developer.mozilla.org/en-US/docs/Web/API/WebSockets_API
- OWASP File Upload Cheat Sheet: https://cheatsheetseries.owasp.org/cheatsheets/File_Upload_Cheat_Sheet.html
- Web.dev responsive images: https://web.dev/learn/images
- Web.dev image performance: https://web.dev/learn/performance/image-performance

---

