# bench — شروعِ سردِ برنامهٔ کامپیوتر روی کامپیوترِ مرجع (F0-26، بخشِ ۹.۱ و ۹.۲).
#
#   powershell -ExecutionPolicy Bypass -File bench\desktop-cold-start.ps1 [-Runs 10] [-Exe "C:\...\app.exe"]
#
# بی -Exe، میان‌برِ «فروشگاه من / My Shop / Tohid» در منوی Start پیدا می‌شود.
# هر دور: برنامه بسته می‌شود، دوباره باز می‌شود، و زمان تا وقتی شمرده
# می‌شود که پنجرهٔ اصلی ساخته شده و پاسخ‌گوست. این «کفِ» شروعِ سرد است —
# کشیدنِ محتوای نخستین صفحه کمی بعد است.
# شروعِ «واقعاً سرد» روی HDD فقط دورِ نخست پس از روشن‌کردنِ دوبارهٔ ویندوز
# است؛ اسکریپت آن را جدا چاپ می‌کند. هیچ دادهٔ دکانی خوانده نمی‌شود.
# پیام‌های خروجی انگلیسی‌اند تا کنسولِ قدیمیِ ویندوز درست نشانشان دهد؛
# فایل UTF-8 با BOM است تا PowerShell 5.1 همین توضیح‌ها را درست بخواند.
param([string]$Exe = "", [int]$Runs = 10)
$ErrorActionPreference = 'Stop'

if (-not $Exe) {
  $sh = New-Object -ComObject WScript.Shell
  $dirs = @("$env:APPDATA\Microsoft\Windows\Start Menu\Programs", "$env:ProgramData\Microsoft\Windows\Start Menu\Programs")
  $lnk = Get-ChildItem $dirs -Recurse -Filter *.lnk -ErrorAction SilentlyContinue |
    Where-Object { $_.BaseName -match 'My Shop|MyShop|Tohid|فروشگاه' } | Select-Object -First 1
  if ($lnk) { $Exe = $sh.CreateShortcut($lnk.FullName).TargetPath }
}
if (-not $Exe -or -not (Test-Path $Exe)) { Write-Host "App not found; pass the exe path with -Exe"; exit 2 }

$name = [IO.Path]::GetFileNameWithoutExtension($Exe)
$cpu = (Get-CimInstance Win32_Processor | Select-Object -First 1).Name
$ram = [math]::Round((Get-CimInstance Win32_ComputerSystem).TotalPhysicalMemory / 1GB, 1)
$disk = (Get-PhysicalDisk | Select-Object -First 1).MediaType
Write-Host "PC: $cpu | RAM $ram GB | disk: $disk"
Write-Host "App: $Exe"

$times = @()
$mem = 0
for ($i = 1; $i -le $Runs; $i++) {
  Get-Process -Name $name -ErrorAction SilentlyContinue | Stop-Process -Force
  Start-Sleep -Seconds 3
  $sw = [Diagnostics.Stopwatch]::StartNew()
  $null = Start-Process -FilePath $Exe -PassThru
  $ok = $false
  while ($sw.Elapsed.TotalSeconds -lt 180) {
    $w = Get-Process -Name $name -ErrorAction SilentlyContinue | Where-Object { $_.MainWindowHandle -ne 0 -and $_.Responding } | Select-Object -First 1
    if ($w) { $ok = $true; break }
    Start-Sleep -Milliseconds 20
  }
  $ms = $sw.ElapsedMilliseconds
  if (-not $ok) { Write-Host "run ${i}: no window within 180 s"; continue }
  Write-Host "run ${i}: $ms ms"
  $times += $ms
  Start-Sleep -Seconds 5
  $mem = [math]::Round(((Get-Process -Name $name -ErrorAction SilentlyContinue | Measure-Object WorkingSet64 -Sum).Sum) / 1MB)
}
Get-Process -Name $name -ErrorAction SilentlyContinue | Stop-Process -Force

if ($times.Count -eq 0) { exit 1 }
$s = $times | Sort-Object
$p50 = $s[[math]::Ceiling(0.5 * $s.Count) - 1]
$p95 = $s[[math]::Min($s.Count - 1, [math]::Ceiling(0.95 * $s.Count) - 1)]
Write-Host ("first run (true cold only right after a reboot): {0} ms" -f $times[0])
Write-Host ("cold start: p50 {0} ms  p95 {1} ms  max {2} ms  (n={3})" -f $p50, $p95, $s[-1], $s.Count)
Write-Host ("memory 5 s after start: {0} MB" -f $mem)
