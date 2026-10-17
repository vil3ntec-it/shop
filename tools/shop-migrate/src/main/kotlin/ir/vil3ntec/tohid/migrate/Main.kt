package ir.vil3ntec.tohid.migrate

import java.io.File
import java.io.PrintStream
import kotlin.system.exitProcess

private const val USAGE = """
shop-migrate — ابزارِ مهاجرتِ دادهٔ فروشگاه (فاز ۱، قدمِ ۱: اجرای آزمایشی)

  dry-run <backup.zip|backup.json> [--all] [--names]
      پشتیبان را فقط می‌خواند، در SQLiteِ درون‌حافظه به مدلِ تازه می‌برد و
      گزارشِ تطبیق را چاپ می‌کند. هیچ فایلی نمی‌نویسد.
      --all    همهٔ کالاها/مشتری‌ها را چاپ کن، نه فقط ناهم‌خوان‌ها
      --names  نام‌ها را نشان بده (فقط برای خودِ صاحبِ دکان؛ این خروجی را نفرستید)
      خروجی: 0 = PASS، 2 = اختلافِ بی‌دلیل، 1 = خطا

  fixture --seed <n> [--size small|medium|large] [--anomalies] --out <file.json>
      یک پشتیبانِ ساختگی (بی دادهٔ واقعی) می‌سازد.

  golden <backup.json>
      عددهای «موتورِ قدیم» برای `legacy-golden.txt` را چاپ می‌کند.
"""

fun main(args: Array<String>) {
  val out = PrintStream(System.out, true, "UTF-8")
  val code = runCatching { cli(args.toList(), out) }.getOrElse {
    System.err.println("خطا: ${it.message ?: it::class.simpleName}")
    1
  }
  exitProcess(code)
}

fun cli(args: List<String>, out: PrintStream): Int {
  fun flag(name: String) = name in args
  fun value(name: String): String? = args.indexOf(name).takeIf { it >= 0 }?.let { args.getOrNull(it + 1) }
  return when (args.firstOrNull()) {
    "dry-run" -> {
      val path = args.getOrNull(1)?.takeUnless { it.startsWith("--") } ?: return usage(out)
      val opened = BackupReader.read(File(path))
      val result = DryRun.run(opened.data, opened.storeName)
      out.print(Report.render(result, opened, all = flag("--all"), names = if (flag("--names")) opened.data else null))
      if (result.ok) 0 else 2
    }
    "fixture" -> {
      val seed = value("--seed")?.toLongOrNull() ?: return usage(out)
      val target = value("--out") ?: return usage(out)
      val size = Fixtures.Size.valueOf((value("--size") ?: "small").uppercase())
      val file = File(target)
      require(!file.exists()) { "فایل از قبل هست؛ رونویسی نمی‌شود: $target" }
      file.writeText(Fixtures.backupJson(Fixtures.generate(seed, size, flag("--anomalies"))), Charsets.UTF_8)
      out.println("نوشته شد: $target")
      0
    }
    "golden" -> {
      val path = args.getOrNull(1) ?: return usage(out)
      LegacyFigures.golden(BackupReader.read(File(path)).data).forEach { out.println(it) }
      0
    }
    else -> usage(out)
  }
}

private fun usage(out: PrintStream): Int {
  out.println(USAGE.trimIndent())
  return 1
}
