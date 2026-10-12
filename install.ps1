# Tohid — Windows installer bootstrap
#
#   irm https://vil3ntec-it.github.io/shop/install.ps1 | iex
#
# Downloads Tohid-Windows-Setup.exe (our server first, then GitHub), checks its
# SHA-256 against the published SHA256SUMS.txt and runs it. A file fetched this
# way is not marked "downloaded from the internet", so Windows does not show the
# blue SmartScreen "Windows protected your PC" box that browser downloads get
# (the installer is not code-signed yet). Nothing here needs Administrator.
$ErrorActionPreference = 'Stop'
$ProgressPreference = 'SilentlyContinue'
try { [Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12 } catch {}

if (-not [Environment]::Is64BitOperatingSystem) {
  Write-Host 'Tohid needs 64-bit Windows 10 or 11. On this PC use the web version: https://vil3ntec-it.github.io/shop/' -ForegroundColor Red
  return
}

$name = 'Tohid-Windows-Setup.exe'
$sources = @(
  'https://api.vill3n.top/api/releases/tohid-desktop/file',
  'https://github.com/vil3ntec-it/shop/releases/download/tohid-desktop'
)
# CI test hooks (tohid-desktop.yml): a local source and silent installer args
if ($env:TOHID_INSTALL_SRC) { $sources = @($env:TOHID_INSTALL_SRC) }
$dir = Join-Path $env:TEMP ('tohid-' + [guid]::NewGuid().ToString('N').Substring(0, 8))
New-Item -ItemType Directory -Force $dir | Out-Null
$exe = Join-Path $dir $name

$ok = $false
foreach ($src in $sources) {
  for ($try = 1; $try -le 2 -and -not $ok; $try++) {
    try {
      Write-Host "Downloading Tohid from $src ..."
      $sums = (Invoke-WebRequest -UseBasicParsing "$src/SHA256SUMS.txt").Content
      if ($sums -is [byte[]]) { $sums = [Text.Encoding]::UTF8.GetString($sums) }
      $line = ($sums -split "`n") | Where-Object { $_ -match "\s\*?$([regex]::Escape($name))\s*$" } | Select-Object -First 1
      if (-not $line) { throw 'checksum list has no entry for the installer' }
      $want = ($line.Trim() -split '\s+')[0].ToLower()
      Invoke-WebRequest -UseBasicParsing "$src/$name" -OutFile $exe
      $got = (Get-FileHash $exe -Algorithm SHA256).Hash.ToLower()
      if ($got -ne $want) { throw "file damaged in transit (SHA-256 mismatch)" }
      $ok = $true
    } catch {
      Write-Host "  failed: $($_.Exception.Message)" -ForegroundColor Yellow
      Remove-Item $exe -Force -ErrorAction SilentlyContinue
    }
  }
  if ($ok) { break }
}
if (-not $ok) {
  Write-Host 'Could not download Tohid. Check the internet connection (VPN/proxy on or off) and run the command again.' -ForegroundColor Red
  return
}

Write-Host 'Verified. Starting the installer...' -ForegroundColor Green
if ($env:TOHID_INSTALL_ARGS) { Start-Process -FilePath $exe -ArgumentList $env:TOHID_INSTALL_ARGS -Wait }
else { Start-Process -FilePath $exe -Wait }
Remove-Item $dir -Recurse -Force -ErrorAction SilentlyContinue
