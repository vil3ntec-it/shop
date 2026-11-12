#!/usr/bin/env python3
"""پشتهٔ رشته‌های گیرکرده از خروجیِ `sample` ِ macOS (`ios-e2e/shots/hang-sample-*.txt`) — فقط آن‌چه برای یافتنِ بن‌بست لازم است.

چرا: کارِ «برنامهٔ آیفون — چارچوب، Xcode و شبیه‌ساز» (`.github/workflows/ios-spike.yml`) پشتهٔ کامل را پیش از سطرهای THUMB ِ
عکس‌ها چاپ می‌کند و از ۵۰۰۰ خطِ آخرِ لاگ (سقفِ API ِ GitHub) بیرون می‌افتد. این ابزار در گامِ پس از THUMB فقط این‌ها را می‌دهد:
  • رشتهٔ اصلی (`com.apple.main-thread`)؛
  • هر رشته‌ای که روی `NSRecursiveLock` (قفلِ سراسریِ `synchronized` ِ ‎ios/jvm/Jvm.kt‎)، `semaphore_wait`
    (HTTP ِ ‎java/net/Http.kt‎، `Future.get` ِ ‎java/util/concurrent‎) یا `dispatch_sync` (`IosLocation.lastNow`) ایستاده است؛
هر رشته ≤ ۸۰ خط: سرِ پشته (ریشه) و تهِ آن (جایی که ایستاده) می‌مانند و میانه با یک سطرِ «⋯» کوتاه می‌شود؛ نشانیِ حافظه حذف.

استفاده: python3 ios/e2e/hang-threads.py <hang-sample-*.txt>…   ·   آزمونِ خودش: python3 ios/e2e/hang-threads.py --self-test
"""
import re
import sys

MAX_LINES = 80          # برای هر رشته، با سطرِ سرِ رشته
HEAD_LINES = 12         # از ریشهٔ پشته (کدام کار/آزمون)
MAX_THREADS = 24        # سقفِ رشته‌های چاپی در هر پرونده (لاگ کوتاه بماند)
WAITS = re.compile(r"NSRecursiveLock|semaphore_wait|dispatch_sync|__DISPATCH_WAIT_FOR_QUEUE__")
THREAD = re.compile(r"^\s*\d+\s+Thread_\S+")
ADDR = re.compile(r"\s+\[0x[0-9a-fA-F]+(?:,0x[0-9a-fA-F]+)*\]")


def threads(text):
    """بلوک‌های «Call graph:» — هر کدام [سرِ رشته، سطرهای پشته…]."""
    lines = text.splitlines()
    try:
        start = next(i for i, l in enumerate(lines) if l.strip() == "Call graph:") + 1
    except StopIteration:
        return None
    out, cur = [], None
    for line in lines[start:]:
        if line.strip() == "":
            continue
        if not line[:1].isspace():
            break  # «Total number in stack…» / «Binary Images:» ⇐ پایانِ گراف
        if THREAD.match(line):
            cur = [line.rstrip()]
            out.append(cur)
        elif cur is not None:
            cur.append(ADDR.sub("", line.rstrip()))
    return out


def pick(block):
    """رشتهٔ اصلی، یا رشتهٔ ایستاده روی قفل/انتظار ⇐ برچسب؛ وگرنه None."""
    if "com.apple.main-thread" in block[0]:
        return "رشتهٔ اصلی"
    hit = sorted({m.group(0) for l in block[1:] for m in WAITS.finditer(l)})
    return "ایستاده روی " + "، ".join(hit) if hit else None


def clip(block):
    if len(block) <= MAX_LINES:
        return block
    tail = MAX_LINES - 2 - HEAD_LINES
    hidden = len(block) - 1 - HEAD_LINES - tail
    return block[: 1 + HEAD_LINES] + [f"    ⋯ ({hidden} سطرِ میانیِ پشته)"] + block[-tail:]


def report(name, text):
    out = [f"===== {name}"]
    blocks = threads(text)
    if blocks is None:
        out.append("«Call graph:» نیست (sample کامل نشد؟) — ۴۰ سطرِ نخست:")
        out += text.splitlines()[:40]
        return out
    chosen = [(why, b) for b in blocks for why in [pick(b)] if why]
    out.append(f"{len(blocks)} رشته؛ {len(chosen)} رشتهٔ اصلی/ایستاده" +
               (f" (فقط {MAX_THREADS} ِ نخست)" if len(chosen) > MAX_THREADS else ""))
    for why, b in chosen[:MAX_THREADS]:
        out.append(f"--- {why}")
        out += clip(b)
    return out


SELF = """Analysis of sampling test.kexe (pid 4242) every 1 millisecond
Process:         test.kexe [4242]
----

Call graph:
    2650 Thread_9001   DispatchQueue_1: com.apple.main-thread  (serial)
    + 2650 start  (in dyld) + 6076  [0x18a2b6b4c]
    +   2650 main  (in test.kexe) + 36  [0x1001c3a8c]
""" + "".join(f"    + {'  ' * i}2650 kfun:frame{i}  (in test.kexe) + 8  [0x1000{i:04x}]\n" for i in range(90)) + """    +   2650 -[NSRecursiveLock lock]  (in Foundation) + 28  [0x18b6ae5b0]
    +     2650 __psynch_mutexwait  (in libsystem_kernel.dylib) + 8  [0x18a5b75cc]
    2650 Thread_9002
    + 2650 start_wqthread  (in libsystem_pthread.dylib) + 8  [0x18a5ee0b8]
    +   2650 __workq_kernreturn  (in libsystem_kernel.dylib) + 8  [0x18a5b8b84]
    2650 Thread_9003: worker
    + 2650 thread_start  (in libsystem_pthread.dylib) + 8  [0x18a5ee0c4]
    +   2650 kfun:java.net.HttpURLConnection#execute  (in test.kexe) + 900  [0x100abc000]
    +     2650 _dispatch_semaphore_wait_slow  (in libdispatch.dylib) + 76  [0x18a42f0a4]
    +       2650 semaphore_wait_trap  (in libsystem_kernel.dylib) + 8  [0x18a5b5c08]
    2650 Thread_9004   DispatchQueue_7: com.apple.NSURLSession-delegate  (serial)
    + 2650 _dispatch_sync_f_slow  (in libdispatch.dylib) + 140  [0x18a43a1e0]
    +   2650 __DISPATCH_WAIT_FOR_QUEUE__  (in libdispatch.dylib) + 340  [0x18a43a8d4]

Total number in stack (recursive counted multiple, when >=5):
        5       _pthread_start  (in libsystem_pthread.dylib) + 136  [0x18a5f3f94]

Binary Images:
       0x100000000 -        0x101ffffff +test.kexe (0) <00000000-0000-0000-0000-000000000000> /tmp/test.kexe
"""


def self_test():
    out = report("self", SELF)
    text = "\n".join(out)
    assert "4 رشته؛ 3 رشتهٔ اصلی/ایستاده" in text, text[:400]
    assert "Thread_9002" not in text, "رشتهٔ بیکار چاپ نمی‌شود"
    sections = text.split("--- ")[1:]
    assert [s.split("\n")[0] for s in sections] == [
        "رشتهٔ اصلی", "ایستاده روی semaphore_wait", "ایستاده روی __DISPATCH_WAIT_FOR_QUEUE__، dispatch_sync"], sections
    main = sections[0].split("\n")[1:]
    assert len([l for l in main if l]) == MAX_LINES, len(main)
    assert "start  (in dyld)" in main[1] and "__psynch_mutexwait" in main[-2] and "-[NSRecursiveLock lock]" in main[-3], main[-3:]
    assert "سطرِ میانیِ پشته" in main[HEAD_LINES + 1]
    assert "[0x" not in text and "Binary Images" not in text and "Total number" not in text
    broken = "\n".join(report("broken", "sample: failed to attach\n"))
    assert "«Call graph:» نیست" in broken and "failed to attach" in broken
    print("hang-threads self-test: ok (7/7)")


if __name__ == "__main__":
    if sys.argv[1:] == ["--self-test"]:
        self_test()
        sys.exit(0)
    if not sys.argv[1:]:
        print(__doc__.strip().splitlines()[-1])
        sys.exit(2)
    for path in sys.argv[1:]:
        try:
            with open(path, encoding="utf-8", errors="replace") as f:
                body = f.read()
        except OSError as e:
            print(f"===== {path}: خوانده نشد ({e})")
            continue
        print("\n".join(report(path, body)))
