param([Parameter(Mandatory)][ValidateSet('Start','Stop','Mark','Pull')][string]$Mode,[string]$Adb='adb',[string]$Serial,[ValidatePattern('^[A-Za-z0-9_-]{1,80}$')][string]$Text='MARK',[switch]$IncludeVideos)
$ErrorActionPreference='Stop'
$diagnosticRoot=Join-Path $PSScriptRoot '../diagnostics';New-Item -ItemType Directory -Force -Path $diagnosticRoot | Out-Null
$sessionFile=Join-Path $diagnosticRoot 'active-session.json'
if($Mode -eq 'Start'){
  if(Test-Path -LiteralPath $sessionFile){$old=Get-Content -LiteralPath $sessionFile -Raw | ConvertFrom-Json;if(Get-Process -Id $old.pid -ErrorAction SilentlyContinue){throw 'A collector is already recorded; stop it first'}}
  $resolvedAdb=(Get-Command $Adb -ErrorAction Stop).Source
  if(-not $Serial){$deviceLines=& $resolvedAdb devices;$connected=@($deviceLines | Where-Object {$_ -match '^\S+\s+device$'});if($connected.Count -ne 1){throw 'Specify -Serial when exactly one device is not connected'};$Serial=($connected[0] -split '\s+')[0]}
  $folder=Join-Path $diagnosticRoot (Get-Date -Format 'yyyyMMdd-HHmmss');New-Item -ItemType Directory -Path $folder | Out-Null;$folder=(Resolve-Path -LiteralPath $folder).Path
  $argsForAdb=@('-s',$Serial,'logcat','-b','main','-b','system','-v','threadtime','-T','1','VoyahCameraLab:V','V4L2CameraHAL:V','ais_v4l2_proxy:V','EVENT_MANAGER:V','CameraService:I','Camera3-Device:V','CanBusService:D','*:S')
  $collector=Start-Process -FilePath $resolvedAdb -ArgumentList $argsForAdb -WindowStyle Hidden -PassThru -RedirectStandardOutput (Join-Path $folder 'logcat.txt') -RedirectStandardError (Join-Path $folder 'logcat-error.txt')
  @{pid=$collector.Id;serial=$Serial;adb=$resolvedAdb;folder=$folder;started=(Get-Date -Format o)} | ConvertTo-Json | Set-Content -LiteralPath $sessionFile -Encoding utf8
  Write-Output "Recording: $folder";exit
}
if(-not(Test-Path -LiteralPath $sessionFile)){throw 'No diagnostic session. Run -Mode Start first.'}
$session=Get-Content -LiteralPath $sessionFile -Raw | ConvertFrom-Json
if(-not $Serial){$Serial=$session.serial};$deviceArguments=@('-s',$Serial)
if($Mode -eq 'Stop'){
  $process=Get-CimInstance Win32_Process -Filter "ProcessId = $($session.pid)"
  if($process){if($process.Name -ne 'adb.exe' -or $process.CommandLine -notmatch 'logcat' -or $process.CommandLine -notmatch [regex]::Escape($session.serial)){throw 'PID no longer matches this collector'};Stop-Process -Id $session.pid}
  $session | Add-Member -NotePropertyName stopped -NotePropertyValue (Get-Date -Format o) -Force;$session | ConvertTo-Json | Set-Content -LiteralPath $sessionFile -Encoding utf8;Write-Output 'Collector stopped';exit
}
if($Mode -eq 'Mark'){& $Adb @deviceArguments shell log -t VoyahCameraLab -p i "TEST_MARK_$Text";"$(Get-Date -Format o) $Text" | Add-Content -LiteralPath (Join-Path $session.folder 'marks.txt');exit}
& $Adb @deviceArguments pull /sdcard/Android/data/local.codex.voyahcameralab/files/ (Join-Path $session.folder 'app-files')
& $Adb @deviceArguments shell dumpsys media.camera | Set-Content -LiteralPath (Join-Path $session.folder 'media-camera.txt') -Encoding utf8
& $Adb @deviceArguments shell dumpsys dls_vehicle_signal_service | Set-Content -LiteralPath (Join-Path $session.folder 'native360.txt') -Encoding utf8
if($IncludeVideos){& $Adb @deviceArguments pull /sdcard/Movies/VoyahCamera360DVR/ (Join-Path $session.folder 'videos')}
Write-Output "Saved: $($session.folder)"
