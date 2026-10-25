param(
    [Parameter(Mandatory)][string]$Endpoint,
    [Parameter(Mandatory)][string]$Source,
    [Parameter(Mandatory)][string]$Destination,
    [string]$Adb = 'D:\Programs\MuMuPlayer\nx_main\adb.exe'
)
$ErrorActionPreference = 'Stop'
if ($Destination -notmatch '^/data/data/com\.termux/files/home/sliver-wolf-build/[a-zA-Z0-9/_.-]+$' -or $Destination.Contains('..')) {
    throw 'Destination must stay inside the dedicated phone build directory.'
}
$text = [IO.File]::ReadAllText((Resolve-Path $Source)).Replace("`r`n", "`n")
$encoded = [Convert]::ToBase64String([Text.Encoding]::UTF8.GetBytes($text))
$parent = $Destination.Substring(0, $Destination.LastIndexOf('/'))
$encoded | & $Adb -s $Endpoint shell "run-as com.termux /data/data/com.termux/files/usr/bin/bash -c 'mkdir -p $parent && /data/data/com.termux/files/usr/bin/base64 -d > $Destination'"
if ($LASTEXITCODE -ne 0) { throw 'Phone transfer failed.' }
