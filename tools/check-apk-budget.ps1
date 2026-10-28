<#
.SYNOPSIS
Checks the debug APK against the phase 2 size budget in tools/apk-budget.json.

.DESCRIPTION
Prints the numbers and fails (exit 1) when any limit is exceeded:
  * the whole APK may grow at most maxGrowthBytes over baseApkBytes (the debug APK of
    baseCommit, the main commit the phase started from, as built by CI);
  * the fonts, summed as the compressed size of the assets/composeResources/<package>/font*/
    entries, may take at most maxFontBytes;
  * fonts and icons together may take at most maxFontsAndIconsBytes (the spec's 1 MB).
    The icons do not exist yet, so this sum counts the fonts only. The task that adds the
    com.mooscles.designsystem.icons package (phase 2, Task 6) adds its dex size here.

.EXAMPLE
pwsh tools/check-apk-budget.ps1 -Apk app/build/outputs/apk/debug/app-debug.apk
#>
param(
    [Parameter(Mandatory = $true)][string]$Apk,
    [string]$Config = (Join-Path $PSScriptRoot 'apk-budget.json')
)
$ErrorActionPreference = 'Stop'

if (-not (Test-Path -LiteralPath $Apk -PathType Leaf)) { throw "APK not found: $Apk" }
if (-not (Test-Path -LiteralPath $Config -PathType Leaf)) { throw "Budget file not found: $Config" }
$budget = Get-Content -LiteralPath $Config -Raw | ConvertFrom-Json
foreach ($field in 'baseCommit', 'baseApkBytes', 'maxGrowthBytes', 'maxFontBytes', 'maxFontsAndIconsBytes') {
    if ($null -eq $budget.$field) { throw "$Config has no '$field'." }
}

Add-Type -AssemblyName System.IO.Compression.FileSystem
$apkPath = (Resolve-Path -LiteralPath $Apk).Path
$apkBytes = (Get-Item -LiteralPath $apkPath).Length
$fontPattern = '^assets/composeResources/[^/]+/font(-[^/]+)?/[^/]+$'
$fontBytes = [long]0
$fontEntries = @()
$zip = [System.IO.Compression.ZipFile]::OpenRead($apkPath)
try {
    foreach ($entry in $zip.Entries) {
        if ($entry.FullName -match $fontPattern) {
            $fontBytes += $entry.CompressedLength
            $fontEntries += "- ``$($entry.FullName)``: $($entry.CompressedLength) bytes compressed ($($entry.Length) uncompressed)"
        }
    }
}
finally { $zip.Dispose() }

# Icons: none yet (see the description). Task 6 measures the dex size of their package.
$iconBytes = [long]0
$fontsAndIcons = $fontBytes + $iconBytes
$baseApk = [long]$budget.baseApkBytes
$growth = $apkBytes - $baseApk

function Format-Check([string]$label, [long]$value, [long]$limit) {
    $state = if ($value -le $limit) { 'ok' } else { 'OVER' }
    "| $label | $value | $limit | $state |"
}

$lines = @(
    '### APK budget',
    "``$Apk``: $apkBytes bytes. Base: $baseApk bytes at $($budget.baseCommit).",
    '',
    '| Check | Bytes | Limit | Result |',
    '|---|---:|---:|---|',
    (Format-Check 'APK growth over the base' $growth ([long]$budget.maxGrowthBytes)),
    (Format-Check 'Fonts (compressed)' $fontBytes ([long]$budget.maxFontBytes)),
    (Format-Check 'Fonts and icons (icons not measured yet)' $fontsAndIcons ([long]$budget.maxFontsAndIconsBytes)),
    '',
    "Font entries: $($fontEntries.Count)."
) + $fontEntries
$lines | Write-Output
if ($env:GITHUB_STEP_SUMMARY) { $lines | Out-File -FilePath $env:GITHUB_STEP_SUMMARY -Append -Encoding utf8 }

$over = @()
if ($growth -gt [long]$budget.maxGrowthBytes) { $over += "the APK grew $growth bytes (limit $($budget.maxGrowthBytes))" }
if ($fontBytes -gt [long]$budget.maxFontBytes) { $over += "the fonts take $fontBytes bytes (limit $($budget.maxFontBytes))" }
if ($fontsAndIcons -gt [long]$budget.maxFontsAndIconsBytes) { $over += "fonts and icons take $fontsAndIcons bytes (limit $($budget.maxFontsAndIconsBytes))" }
if ($fontEntries.Count -eq 0) { $over += 'no font entry matched, so the font check cannot have measured anything' }
if ($over.Count -gt 0) {
    Write-Error ("APK budget exceeded: " + ($over -join '; ') + '.') -ErrorAction Continue
    exit 1
}
