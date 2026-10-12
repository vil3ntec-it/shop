; نصابِ ویندوزِ توحید — Inno Setup 6، ظاهرِ «modern».
;
; چرا نه نصابِ jpackage (WiX)؟ آن نصاب پنجرهٔ قدیمیِ ویندوز XP را نشان
; می‌داد و نسخهٔ exeاش یک MSIِ ۱۱۰ مگابایتی را در پوشهٔ موقت باز می‌کرد —
; روی بعضی کامپیوترها همان‌جا «Data.cab is corrupt» می‌داد. این نصاب:
;   • فارسی و راست‌به‌چپ، با ظاهرِ امروزیِ ویندوز ۱۰ و ۱۱
;   • بی نیاز به Administrator (نصب برای همین کاربر، مثلِ قبل در ‎%LOCALAPPDATA%\Tohid‎)
;   • نسخهٔ قدیمیِ MSI را خودش بی‌صدا برمی‌دارد — دفتر و تنظیمات دست نمی‌خورند
;   • درستیِ فایل‌های خودش را پیش از نصب می‌سنجد و اگر دانلود ناقص باشد روشن می‌گوید
;   • به‌روزرسانیِ درون‌برنامه همین فایل را با ‎/SILENT‎ اجرا می‌کند و برنامه را دوباره باز می‌کند
;
; ساخت: ‎iscc /DAppVersion=1.2.3 /DSourceDir=...\app\Tohid Tohid.iss‎ (‎tohid-desktop.yml‎)

#ifndef AppVersion
  #define AppVersion "0.0.0"
#endif
#ifndef SourceDir
  #define SourceDir "..\build\compose\binaries\main\app\Tohid"
#endif

[Setup]
; ⛔ هرگز عوض نشود — ویندوز با همین شناسه نسخهٔ تازه را روی همان برنامه می‌نشاند
AppId={{8C2E4F1A-5B7D-4E3C-9A61-2F0D7B3E5C14}
AppName=توحید
AppVerName=توحید {#AppVersion}
AppVersion={#AppVersion}
VersionInfoVersion={#AppVersion}
AppPublisher=VIL3NTEC
AppPublisherURL=https://vil3ntec-it.github.io/shop/
AppSupportURL=https://vil3ntec-it.github.io/shop/
AppCopyright=© VIL3NTEC
DefaultDirName={localappdata}\Tohid
DefaultGroupName=توحید
DisableProgramGroupPage=yes
DisableDirPage=auto
DisableWelcomePage=no
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
UninstallDisplayName=توحید
Compression=lzma2/ultra64
SolidCompression=yes
LZMAUseSeparateProcess=yes
OutputDir=..\build\installer
OutputBaseFilename=Tohid-Windows-Setup
CloseApplications=force
RestartApplications=no
SetupLogging=yes
ShowLanguageDialog=no

[Languages]
Name: "fa"; MessagesFile: "compiler:Default.isl,Persian.isl"

[Tasks]
Name: "desktopicon"; Description: "{cm:CreateDesktopIcon}"; GroupDescription: "{cm:AdditionalIcons}"

[InstallDelete]
; پوشه‌های برنامه با هر نسخه کامل عوض می‌شوند — فایلِ کهنه از نسخهٔ قبل نمی‌ماند
Type: filesandordirs; Name: "{app}\app"
Type: filesandordirs; Name: "{app}\runtime"

[Files]
Source: "{#SourceDir}\*"; DestDir: "{app}"; Flags: ignoreversion recursesubdirs createallsubdirs

[Icons]
Name: "{autoprograms}\توحید"; Filename: "{app}\Tohid.exe"
Name: "{autodesktop}\توحید"; Filename: "{app}\Tohid.exe"; Tasks: desktopicon

[Run]
; نصبِ دستی: تیکِ «اجرای توحید»؛ به‌روزرسانیِ بی‌صدا: خودکار باز می‌شود
Filename: "{app}\Tohid.exe"; Description: "{cm:LaunchProgram,توحید}"; Flags: nowait postinstall

[Code]
// نسخهٔ قدیمی (MSIِ jpackage، همان upgradeUuidِ build.gradle.kts) را بی‌صدا برمی‌دارد
// تا دو «Tohid» در فهرستِ برنامه‌های ویندوز نماند. فقط فایل‌های برنامه —
// دفتر و تنظیمات در ‎%APPDATA%‎ و پوشهٔ کاربر است و دست نمی‌خورد.
const
  OldUpgradeCode = '{6F1E0C52-7A3B-4D8E-9C1F-3B2A5D7E9F10}';

function MsiEnumRelatedProducts(lpUpgradeCode: String; dwReserved: Cardinal; iProductIndex: Cardinal; lpProductBuf: String): Cardinal;
  external 'MsiEnumRelatedProductsW@msi.dll stdcall';

function PrepareToInstall(var NeedsRestart: Boolean): String;
var
  Code: String;
  Rc: Integer;
  Guard: Integer;
begin
  Result := '';
  Guard := 0;
  repeat
    Code := StringOfChar(' ', 39);
    if MsiEnumRelatedProducts(OldUpgradeCode, 0, 0, Code) <> 0 then Break;
    Code := Copy(Code, 1, 38);
    Log('برداشتنِ نسخهٔ قدیمی: ' + Code);
    // برنامهٔ باز فایل‌هایش را قفل می‌کند و برداشتن نیمه می‌ماند
    if Guard = 0 then
      Exec(ExpandConstant('{sys}\taskkill.exe'), '/F /IM Tohid.exe', '', SW_HIDE, ewWaitUntilTerminated, Rc);
    Exec(ExpandConstant('{sys}\msiexec.exe'), '/x ' + Code + ' /qn /norestart', '', SW_HIDE, ewWaitUntilTerminated, Rc);
    Guard := Guard + 1;
  until Guard >= 3;
end;
