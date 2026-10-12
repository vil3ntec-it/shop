; نصابِ ویندوزِ «فروشگاه من» — Inno Setup 6.5+، دو گونه از یک فایل:
;
;   Tohid-Windows-Setup.exe    (پیش‌فرض، ~۳ مگابایت) — «نصابِ آنلاین»
;   Tohid-Windows-Offline.exe  (/DOffline، ~۹۰ مگابایت) — همه‌چیز داخلِ خودش
;
; ── چرا آنلاین؟ (سناریوهای خطا، از گزارشِ صاحب مخزن) ─────────────────
; دو نصابِ کاملاً متفاوت (MSIِ WiX و Inno) روی کامپیوترِ او هر دو گفتند
; «فایل خراب است» («Data.cab is corrupt» و «The source file is corrupted»)،
; در حالی که فایلِ منتشرشده سالم بود و اندازهٔ فایلِ دانلودشده هم درست بود.
; یعنی بایت‌ها در راهِ دانلود خراب شده‌اند (دانلودمنیجری که تکه‌ها را بد
; وصل می‌کند، پروکسی/فیلترشکن، قطع و وصلِ شبکه) — نصابِ ۹۰ مگابایتی هر
; بار همان شانسِ خرابی را دارد. پس:
;   ۱. فایلِ دانلودیِ کاربر کوچک است (شانسِ خرابی تقریباً صفر)
;   ۲. خودِ نصاب برنامه را دانلود می‌کند و SHA-256 را می‌سنجد؛ خراب ⇐
;      پاک و دوباره، از سرورِ خودمان و بعد GitHub (هر کدام دو بار)
;   ۳. اگر Tohid-Windows-App-<نسخه>.7z کنارِ نصاب باشد (بی‌اینترنت)،
;      همان سنجیده و استفاده می‌شود
;   ۴. هیچ‌کدام نشد ⇐ پیامِ روشن و راهِ بعدی، نه خطای گنگ
;   ۵. برنامهٔ باز ⇐ بسته می‌شود؛ MSIِ قدیمی ⇐ بی‌صدا برداشته می‌شود
;   ۶. ویندوزِ ۳۲ بیتی یا قدیمی‌تر از ۱۰ ⇐ پیامِ روشن پیش از هر کار
; به‌روزرسانیِ درون‌برنامه نسخهٔ Offline را می‌گیرد (خودش SHA256SUMSِ
; امضاشده را می‌سنجد) و با ‎/SILENT‎ اجرا می‌کند.
;
; ساخت: tohid-desktop.yml — ‎iscc /DAppVersion=… /DPayloadSha=… /DPayloadSize=… [/DOffline]‎

#ifndef AppVersion
  #define AppVersion "0.0.0"
#endif
#ifndef SourceDir
  #define SourceDir "..\build\compose\binaries\main\app\Tohid"
#endif
#ifndef PayloadSha
  #define PayloadSha ""
#endif
#ifndef PayloadSize
  #define PayloadSize "0"
#endif
#define PayloadName "Tohid-Windows-App-" + AppVersion + ".7z"

[Setup]
; ⛔ هرگز عوض نشود — ویندوز با همین شناسه نسخهٔ تازه را روی همان برنامه می‌نشاند
AppId={{8C2E4F1A-5B7D-4E3C-9A61-2F0D7B3E5C14}
AppName=فروشگاه من
AppVerName=فروشگاه من {#AppVersion}
AppVersion={#AppVersion}
VersionInfoVersion={#AppVersion}
AppPublisher=VIL3NTEC
AppPublisherURL=https://vil3ntec-it.github.io/shop/
AppSupportURL=https://vil3ntec-it.github.io/shop/download.html
AppCopyright=(c) VIL3NTEC
DefaultDirName={localappdata}\Tohid
DefaultGroupName=فروشگاه من
DisableProgramGroupPage=yes
DisableDirPage=auto
DisableWelcomePage=no
UsePreviousAppDir=yes
PrivilegesRequired=lowest
ArchitecturesAllowed=x64compatible
ArchitecturesInstallIn64BitMode=x64compatible
MinVersion=10.0
WizardStyle=modern
WizardSizePercent=110
WizardImageFile=wizard-164.bmp,wizard-205.bmp,wizard-246.bmp
WizardSmallImageFile=wizard-small-55.bmp,wizard-small-69.bmp,wizard-small-83.bmp
WizardImageStretch=no
SetupIconFile=..\icons\tohid.ico
UninstallDisplayIcon={app}\Tohid.exe
UninstallDisplayName=فروشگاه من
CloseApplications=force
RestartApplications=no
SetupLogging=yes
ShowLanguageDialog=no
OutputDir=..\build\installer
#ifdef Offline
OutputBaseFilename=Tohid-Windows-Offline
Compression=lzma2/ultra64
SolidCompression=yes
LZMAUseSeparateProcess=yes
#else
OutputBaseFilename=Tohid-Windows-Setup
Compression=lzma2/max
ArchiveExtraction=enhanced/nopassword
#endif

[Messages]
WelcomeLabel2=This will install [name/ver] on your computer.%n%nYour shop ledger, on the same account as your phone - every device stays in sync.%n%nIf an older version is installed it is updated in place; your data and settings are kept.

[Tasks]
Name: "desktopicon"; Description: "{cm:CreateDesktopIcon}"; GroupDescription: "{cm:AdditionalIcons}"

[InstallDelete]
; پوشه‌های برنامه با هر نسخه کامل عوض می‌شوند — فایلِ کهنه از نسخهٔ قبل نمی‌ماند
Type: filesandordirs; Name: "{app}\app"
Type: filesandordirs; Name: "{app}\runtime"

[Files]
#ifdef Offline
Source: "{#SourceDir}\*"; DestDir: "{app}"; Flags: ignoreversion recursesubdirs createallsubdirs
#else
Source: "{tmp}\{#PayloadName}"; DestDir: "{app}"; ExternalSize: {#PayloadSize}; Flags: external extractarchive recursesubdirs createallsubdirs ignoreversion
#endif

[UninstallDelete]
Type: filesandordirs; Name: "{app}\app"
Type: filesandordirs; Name: "{app}\runtime"

[Icons]
Name: "{autoprograms}\فروشگاه من"; Filename: "{app}\Tohid.exe"
Name: "{autodesktop}\فروشگاه من"; Filename: "{app}\Tohid.exe"; Tasks: desktopicon

[Run]
; نصبِ دستی: تیکِ «Launch»؛ به‌روزرسانیِ بی‌صدا: خودکار باز می‌شود (آزمونِ CI با ‎/NOLAUNCH‎ نه)
Filename: "{app}\Tohid.exe"; Description: "{cm:LaunchProgram,فروشگاه من}"; Flags: nowait postinstall; Check: MayLaunch

[Code]
const
  // نسخهٔ قدیمی (MSIِ jpackage، همان upgradeUuidِ build.gradle.kts)
  OldUpgradeCode = '{6F1E0C52-7A3B-4D8E-9C1F-3B2A5D7E9F10}';

function MsiEnumRelatedProducts(lpUpgradeCode: String; dwReserved: Cardinal; iProductIndex: Cardinal; lpProductBuf: String): Cardinal;
  external 'MsiEnumRelatedProductsW@msi.dll stdcall';

function MayLaunch: Boolean;
var
  I: Integer;
begin
  Result := True;
  for I := 1 to ParamCount do
    if CompareText(ParamStr(I), '/NOLAUNCH') = 0 then Result := False;
end;

#ifndef Offline
const
  PayloadName = '{#PayloadName}';
  PayloadSha = '{#PayloadSha}';

var
  DownloadPage: TDownloadWizardPage;

procedure InitializeWizard;
begin
  DownloadPage := CreateDownloadPage('Downloading فروشگاه من', 'Setup is downloading and verifying the program files...', nil);
  DownloadPage.ShowBaseNameInsteadOfUrl := True;
end;

// ۳: فایلِ برنامه کنارِ نصاب (نصبِ بی‌اینترنت) — فقط اگر SHA-256اش درست باشد
function UseLocalPayload: Boolean;
var
  Local: String;
begin
  Result := False;
  Local := ExpandConstant('{src}\') + PayloadName;
  if not FileExists(Local) then Exit;
  if CompareText(GetSHA256OfFile(Local), PayloadSha) <> 0 then begin
    Log('Local payload has a wrong SHA-256, ignoring: ' + Local);
    Exit;
  end;
  Result := CopyFile(Local, ExpandConstant('{tmp}\') + PayloadName, False);
  Log('Using local payload: ' + Local);
end;

// ۲: سرورِ خودمان، بعد GitHub؛ هر کدام دو بار. DownloadTemporaryFile فایلِ
// با hashِ نادرست را رد می‌کند (استثنا) و دورِ بعد از نو می‌گیرد.
function DownloadPayload(var LastError: String): Boolean;
var
  Urls: TArrayOfString;
  I, Try_: Integer;
  Custom: String;
begin
  Result := False;
  Custom := ExpandConstant('{param:PAYLOADURL|}');
  if Custom <> '' then begin
    SetArrayLength(Urls, 1);
    Urls[0] := Custom;
  end else begin
    SetArrayLength(Urls, 2);
    Urls[0] := 'https://api.vill3n.top/api/releases/tohid-desktop/file/' + PayloadName;
    Urls[1] := 'https://github.com/vil3ntec-it/shop/releases/download/tohid-desktop/' + PayloadName;
  end;
  for I := 0 to GetArrayLength(Urls) - 1 do
    for Try_ := 1 to 2 do begin
      DownloadPage.Clear;
      DownloadPage.Add(Urls[I], PayloadName, PayloadSha);
      DownloadPage.Show;
      try
        try
          DownloadPage.Download;
          Result := True;
          Exit;
        except
          if DownloadPage.AbortedByUser then begin
            LastError := 'Download cancelled.';
            Exit;
          end;
          LastError := GetExceptionMessage;
          Log(Format('Download failed (%s, try %d): %s', [Urls[I], Try_, LastError]));
          DeleteFile(ExpandConstant('{tmp}\') + PayloadName);
        end;
      finally
        DownloadPage.Hide;
      end;
    end;
end;

function NextButtonClick(CurPageID: Integer): Boolean;
var
  Err: String;
begin
  Result := True;
  if CurPageID <> wpReady then Exit;
  if UseLocalPayload then Exit;
  if not DownloadPayload(Err) then begin
    SuppressibleMsgBox(
      'فروشگاه من could not be downloaded.' + #13#10#13#10 +
      'Reason: ' + Err + #13#10#13#10 +
      'Please check your internet connection (or turn your VPN/proxy off or on) and click Install again.' + #13#10#13#10 +
      'Without internet: download MyShop-Windows-Offline.exe from' + #13#10 +
      'https://vil3ntec-it.github.io/shop/download.html',
      mbCriticalError, MB_OK, IDOK);
    Result := False;
  end;
end;
#endif

// ۵: نسخهٔ MSIِ قدیمی را بی‌صدا برمی‌دارد تا دو «Tohid» در فهرستِ
// برنامه‌ها نماند. فقط فایل‌های برنامه — دفتر و تنظیمات دست نمی‌خورند.
function PrepareToInstall(var NeedsRestart: Boolean): String;
var
  Code: String;
  Rc, Guard: Integer;
begin
  Result := '';
  Guard := 0;
  repeat
    Code := StringOfChar(' ', 39);
    if MsiEnumRelatedProducts(OldUpgradeCode, 0, 0, Code) <> 0 then Break;
    Code := Copy(Code, 1, 38);
    Log('Removing old MSI version: ' + Code);
    if Guard = 0 then
      Exec(ExpandConstant('{sys}\taskkill.exe'), '/F /IM Tohid.exe', '', SW_HIDE, ewWaitUntilTerminated, Rc);
    Exec(ExpandConstant('{sys}\msiexec.exe'), '/x ' + Code + ' /qn /norestart', '', SW_HIDE, ewWaitUntilTerminated, Rc);
    Guard := Guard + 1;
  until Guard >= 3;
end;
