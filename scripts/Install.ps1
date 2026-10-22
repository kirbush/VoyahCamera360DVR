param([string]$Adb='adb',[string]$Serial,[string]$Apk=(Join-Path $PSScriptRoot '../artifacts/Voyah-Camera-0.3.apk'),[switch]$Launch)
$ErrorActionPreference='Stop'
$deviceArguments=@();if($Serial){$deviceArguments=@('-s',$Serial)}
if(-not(Test-Path -LiteralPath $Apk)){throw "APK not found: $Apk"}
& $Adb @deviceArguments install -r $Apk
if($LASTEXITCODE -ne 0){throw 'APK installation failed; no data was cleared'}
if($Launch){& $Adb @deviceArguments shell am start -n local.codex.voyahcameralab/.MainActivity;if($LASTEXITCODE -ne 0){throw 'Launch failed'}}
