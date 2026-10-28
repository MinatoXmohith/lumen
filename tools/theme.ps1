# Windows version of theme.sh: push a theme folder (config.json and optionally wallpaper.jpg) to the
# launcher on the TV and restart Home. See themes/README.md for the keys.
#
# Usage: tools\theme.ps1 -TvHost 192.168.1.50 themes\aurora
#        tools\theme.ps1 -TvHost 192.168.1.50 -Reset
param(
    [Parameter(Position = 0)][string]$Theme,
    [switch]$Reset,
    [string]$TvHost = $env:TV_HOST
)
$ErrorActionPreference = 'Stop'
$OutputEncoding = New-Object System.Text.UTF8Encoding $false
if (-not $TvHost) { throw 'pass -TvHost <tv ip> or set $env:TV_HOST' }
$env:TV_HOST = $TvHost
# Resolve the theme folder against the caller's directory before moving into tools\.
if ($Theme) { $Theme = (Resolve-Path $Theme).Path }
Set-Location $PSScriptRoot
$py = 'venv\Scripts\python.exe'
if (-not (Test-Path $py)) { throw 'run: python -m venv tools\venv; tools\venv\Scripts\python.exe -m pip install -r tools\requirements.txt' }
$pkg = 'com.mkdziner.tvhome'
$dir = "/sdcard/Android/data/$pkg/files"
$revision = [guid]::NewGuid().ToString('N')
# Restart Home so it rereads the theme. Android drops a force-stopped app's accessibility services from the
# enabled list (asynchronously, so wait a second), which would switch off InputKeyService/HomeKeyService;
# put the list back if it had ours. Writing the same list back is not enough: the force-stop also turns
# accessibility_enabled off, and the service only binds again once the list changes. So restart first, then
# clear the list, write it back and switch accessibility on again.
$restart = "a=`$(settings get secure enabled_accessibility_services); am force-stop $pkg; am start -n $pkg/.MainActivity >/dev/null; case `"`$a`" in *$pkg*) sleep 1; settings put secure enabled_accessibility_services ''; settings put secure enabled_accessibility_services `"`$a`"; settings put secure accessibility_enabled 1 ;; esac"

if ($Reset) {
    & $py -I tv.py "mkdir -p $dir; rm -f $dir/config.json $dir/wallpaper.jpg; printf '%s' '$revision' > $dir/theme_revision; $restart; echo reset"
    exit 0
}
if (-not $Theme) { throw 'usage: theme.ps1 <theme folder> | -Reset' }
$config = Join-Path $Theme 'config.json'
if (-not (Test-Path $config)) { throw "no config.json in $Theme" }
try { Get-Content $config -Raw | ConvertFrom-Json | Out-Null } catch { throw "$config is not valid JSON" }

& $py -I tv.py "mkdir -p $dir" | Out-Null
& $py -I tv.py --push $config "$dir/config.json"
$wall = Join-Path $Theme 'wallpaper.jpg'
if (Test-Path $wall) { & $py -I b64push.py $wall "$dir/wallpaper.jpg" } else { & $py -I tv.py "rm -f $dir/wallpaper.jpg" | Out-Null }
& $py -I tv.py "printf '%s' '$revision' > $dir/theme_revision" | Out-Null
& $py -I tv.py "$restart; echo applied"
Write-Host "Theme $(Split-Path $Theme -Leaf) is on the TV. Undo with: tools\theme.ps1 -TvHost $TvHost -Reset"
