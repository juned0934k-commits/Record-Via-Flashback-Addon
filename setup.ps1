# One-time setup for the Record Via Flashback (RVF) project.
# Run from this project folder:
#   powershell -ExecutionPolicy Bypass -File .\setup.ps1
#
# What it does:
#  1. Copies the Gradle wrapper (gradlew, gradlew.bat, gradle\wrapper) from your Flashback project.
#  2. Makes libs\flashback-slim.jar: a small compile-only stub of Flashback, cut from a Flashback jar.
#     This jar is only used to compile against. It is never shipped and must never be committed.

param(
    [string]$FlashbackProject = "D:\Flashback Mod ReCoded",
    [string]$FlashbackJar = ""
)

$ErrorActionPreference = "Stop"
$here = $PSScriptRoot

if ($FlashbackJar -eq "") {
    $FlashbackJar = "D:\RVF 26.2\Flashback-0.43.6-for-MC26.2.jar"
}

# ---- 1. Gradle wrapper
foreach ($f in @("gradlew", "gradlew.bat")) {
    $srcFile = Join-Path $FlashbackProject $f
    if (-not (Test-Path $srcFile)) {
        Write-Host "[FAIL] Not found: $srcFile"
        Write-Host "       Pass the Flashback project folder with -FlashbackProject"
        exit 1
    }
    Copy-Item -Force $srcFile (Join-Path $here $f)
}
$wrapperSrc = Join-Path $FlashbackProject "gradle\wrapper"
if (-not (Test-Path $wrapperSrc)) {
    Write-Host "[FAIL] Not found: $wrapperSrc"
    exit 1
}
New-Item -ItemType Directory -Force -Path (Join-Path $here "gradle\wrapper") | Out-Null
# only the wrapper jar: this project keeps its own gradle-wrapper.properties (newer Gradle for Minecraft 26.2)
Copy-Item -Force (Join-Path $wrapperSrc "gradle-wrapper.jar") (Join-Path $here "gradle\wrapper")
Write-Host "[ok] copied the Gradle wrapper jar"

# ---- 2. Slim Flashback jar
if (-not (Test-Path $FlashbackJar)) {
    Write-Host "[FAIL] Flashback jar not found: $FlashbackJar"
    Write-Host "       Pass it with -FlashbackJar `"path\to\flashback.jar`""
    exit 1
}

Add-Type -AssemblyName System.IO.Compression
Add-Type -AssemblyName System.IO.Compression.FileSystem

$libs = Join-Path $here "libs"
New-Item -ItemType Directory -Force -Path $libs | Out-Null
$slim = Join-Path $libs "flashback-slim.jar"
if (Test-Path $slim) { Remove-Item -Force $slim }

$inZip = [System.IO.Compression.ZipFile]::OpenRead($FlashbackJar)
$outZip = [System.IO.Compression.ZipFile]::Open($slim, [System.IO.Compression.ZipArchiveMode]::Create)
$count = 0
try {
    foreach ($entry in $inZip.Entries) {
        $name = $entry.FullName
        if ($name.EndsWith("/")) { continue }
        $keep = $name.StartsWith("com/moulberry/flashback/") -or $name.StartsWith("imgui/moulberry90/") -or $name.StartsWith("com/moulberry/lattice/")
        if (-not $keep) { continue }

        $newEntry = $outZip.CreateEntry($name, [System.IO.Compression.CompressionLevel]::Optimal)
        $inStream = $entry.Open()
        $outStream = $newEntry.Open()
        try { $inStream.CopyTo($outStream) } finally { $outStream.Dispose(); $inStream.Dispose() }
        $count++
    }

    # Minimal mod metadata so Gradle treats the stub as the mod "flashback"
    $meta = $outZip.CreateEntry("fabric.mod.json")
    $writer = New-Object System.IO.StreamWriter($meta.Open())
    try { $writer.Write('{"schemaVersion":1,"id":"flashback","version":"0.39.5","name":"Flashback"}') } finally { $writer.Dispose() }
} finally {
    $outZip.Dispose()
    $inZip.Dispose()
}

$sizeMb = [math]::Round((Get-Item $slim).Length / 1MB, 1)
Write-Host "[ok] created libs\flashback-slim.jar ($count classes/files, $sizeMb MB)"
Write-Host ""
Write-Host "Done. Now run:  .\gradlew.bat build"
