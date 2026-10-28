# Windows version of disable.sh: disable every package listed in a debloat file on the TV
# (one package per line, # comments allowed). Packages are disabled for user 0, never uninstalled.
# Undo with tools\restore.ps1 <same file>.
#
# Usage: tools\disable.ps1 -TvHost 192.168.1.50 examples\xiaomi-mi-tv-4a\debloat.txt
param(
    [Parameter(Mandatory = $true, Position = 0)][string]$List,
    [string]$TvHost = $env:TV_HOST
)
$ErrorActionPreference = 'Stop'
# Pipe plain UTF-8 to native commands; the default here prepends a BOM that corrupts the first command.
$OutputEncoding = New-Object System.Text.UTF8Encoding $false
$repo = Split-Path -Parent $PSScriptRoot
$List = (Resolve-Path $List).Path
$py = Join-Path $repo 'tools\venv\Scripts\python.exe'
if (-not (Test-Path $py)) { throw 'run: python -m venv tools\venv; tools\venv\Scripts\python.exe -m pip install -r tools\requirements.txt' }
if (-not $TvHost) { throw 'pass -TvHost <tv ip> or set $env:TV_HOST' }
$env:TV_HOST = $TvHost

$pkgs = Get-Content $List | ForEach-Object { ($_ -replace '#.*$', '').Trim() } | Where-Object { $_ } | ForEach-Object { ($_ -split '\s+')[0] }
# Lists come from contributors and each name is pasted into a shell command on the TV (#165).
$bad = @($pkgs | Where-Object { $_ -notmatch '^[A-Za-z_][A-Za-z0-9_]*(\.[A-Za-z0-9_]+)+$' })
if ($bad.Count -gt 0) {
    throw "refusing $List, not a package name: $($bad -join ', ')"
}
# A Wi-Fi network saved during the first-run wizard belongs to setupwraith, and Android deletes it when
# setupwraith is disabled. Skip it while it owns the saved network; `$env:FORCE_SETUPWRAITH=1` overrides.
$setup = 'com.google.android.tungsten.setupwraith'
if ($pkgs -contains $setup -and -not $env:FORCE_SETUPWRAITH) {
    $wifi = (& $py -I (Join-Path $repo 'tools\tv.py') 'dumpsys wifi') -join "`n"
    # Be safe: if we cannot read the owners (tv.py failed, timed out, or showed no network at all),
    # do not disable setupwraith, so the TV cannot lose the network it is on.
    if ($LASTEXITCODE -ne 0 -or $wifi -notmatch 'cname=') {
        Write-Warning "Skipping ${setup}: could not check which app owns the saved Wi-Fi networks (dumpsys wifi failed or showed none). Try again, or set `$env:FORCE_SETUPWRAITH=1 if the TV uses Ethernet."
        $pkgs = @($pkgs | Where-Object { $_ -ne $setup })
    } elseif ($wifi -match 'cname=[^ ]*setupwraith') {
        Write-Warning "Skipping ${setup}: the saved Wi-Fi network belongs to it, and disabling it would delete that network and take the TV off Wi-Fi. Forget the network and join it again in Settings > Network & Internet, then run this again (or set `$env:FORCE_SETUPWRAITH=1 on Ethernet)."
        $pkgs = @($pkgs | Where-Object { $_ -ne $setup })
    }
}
# Same as disable.sh: the state undo needs is recorded before the first command reaches the TV, and
# a failed save leaves the TV untouched (#239).
& $py -I (Join-Path $repo 'tools\recovery.py') --save $List
if ($LASTEXITCODE -ne 0) { throw 'no recovery record was written, so nothing was disabled' }

$cmds = @($pkgs | ForEach-Object { "pm disable-user --user 0 $_" }) + 'pm list packages -d'
$cmds | & $py -I (Join-Path $repo 'tools\tv.py') --batch
