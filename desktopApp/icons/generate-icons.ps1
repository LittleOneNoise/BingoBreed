<#
    Régénère les icônes de distribution à partir du master `source.png` (PNG carré, 1024+ px).

        pwsh -File desktopApp/icons/generate-icons.ps1

    Produit, à côté de ce script :
      - bingobreed.ico   Windows (jpackage/MSI + icône de l'exe) — 16/24/32/48/64/128 en DIB, 256 en PNG
      - bingobreed.icns  macOS (jpackage/DMG) — entrées PNG ic07..ic14
      - bingobreed.png   Linux (jpackage/DEB), 512 px
      - ../src/main/resources/icon.png   icône de fenêtre chargée au runtime, 256 px

    N'utilise que System.Drawing (fourni avec Windows) : les conteneurs ICO et ICNS sont
    écrits à la main, ImageMagick n'est pas nécessaire.
#>

$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.Drawing

$here   = Split-Path -Parent $MyInvocation.MyCommand.Path
$source = Join-Path $here 'source.png'
if (-not (Test-Path $source)) { throw "Master introuvable: $source" }

$master = [System.Drawing.Bitmap]::FromFile($source)
Write-Host "Master: $($master.Width)x$($master.Height)"

# --- Rendu d'une taille -------------------------------------------------------

function New-Scaled([int]$size) {
    $bmp = New-Object System.Drawing.Bitmap $size, $size, ([System.Drawing.Imaging.PixelFormat]::Format32bppArgb)
    $g = [System.Drawing.Graphics]::FromImage($bmp)
    $g.CompositingMode    = [System.Drawing.Drawing2D.CompositingMode]::SourceCopy
    $g.InterpolationMode  = [System.Drawing.Drawing2D.InterpolationMode]::HighQualityBicubic
    $g.PixelOffsetMode    = [System.Drawing.Drawing2D.PixelOffsetMode]::HighQuality
    $g.SmoothingMode      = [System.Drawing.Drawing2D.SmoothingMode]::HighQuality
    $g.DrawImage($master, (New-Object System.Drawing.Rectangle 0, 0, $size, $size))
    $g.Dispose()
    $bmp
}

function Get-PngBytes([System.Drawing.Bitmap]$bmp) {
    $ms = New-Object System.IO.MemoryStream
    $bmp.Save($ms, [System.Drawing.Imaging.ImageFormat]::Png)
    $bytes = $ms.ToArray()
    $ms.Dispose()
    , $bytes   # virgule = renvoie le tableau tel quel (PowerShell le déroulerait sinon)
}

# Entrée ICO « classique » : BITMAPINFOHEADER + XOR (BGRA bas-en-haut) + masque AND vide.
function Get-DibBytes([System.Drawing.Bitmap]$bmp) {
    $w = $bmp.Width; $h = $bmp.Height
    $rect = New-Object System.Drawing.Rectangle 0, 0, $w, $h
    $data = $bmp.LockBits($rect, [System.Drawing.Imaging.ImageLockMode]::ReadOnly,
                          [System.Drawing.Imaging.PixelFormat]::Format32bppArgb)
    $raw = New-Object byte[] ($data.Stride * $h)
    [System.Runtime.InteropServices.Marshal]::Copy($data.Scan0, $raw, 0, $raw.Length)
    $bmp.UnlockBits($data)

    $ms = New-Object System.IO.MemoryStream
    $bw = New-Object System.IO.BinaryWriter $ms
    # BITMAPINFOHEADER — hauteur doublée : l'image contient XOR puis AND.
    $bw.Write([int]40); $bw.Write([int]$w); $bw.Write([int]($h * 2))
    $bw.Write([int16]1); $bw.Write([int16]32); $bw.Write([int]0)
    $bw.Write([int]($w * $h * 4)); $bw.Write([int]0); $bw.Write([int]0); $bw.Write([int]0); $bw.Write([int]0)
    # XOR : lignes inversées (DIB = bas-en-haut)
    for ($y = $h - 1; $y -ge 0; $y--) { $bw.Write($raw, $y * $data.Stride, $w * 4) }
    # AND : 1 bpp, lignes alignées sur 4 octets, tout à zéro (opacité portée par le canal alpha)
    $maskRow = [math]::Ceiling($w / 32.0) * 4
    $bw.Write((New-Object byte[] ($maskRow * $h)))
    $bw.Flush()
    $bytes = $ms.ToArray()
    $bw.Dispose()
    , $bytes
}

# --- ICO ----------------------------------------------------------------------

$icoSizes = @(16, 24, 32, 48, 64, 128, 256)
$entries = foreach ($s in $icoSizes) {
    $bmp = New-Scaled $s
    # 256 en PNG (format ICO « Vista »), les tailles courantes en DIB pour la compat maximale.
    $payload = if ($s -eq 256) { Get-PngBytes $bmp } else { Get-DibBytes $bmp }
    $bmp.Dispose()
    [pscustomobject]@{ Size = $s; Data = $payload }
}

$icoPath = Join-Path $here 'bingobreed.ico'
$fs = [System.IO.File]::Create($icoPath)
$bw = New-Object System.IO.BinaryWriter $fs
$bw.Write([int16]0); $bw.Write([int16]1); $bw.Write([int16]$entries.Count)   # ICONDIR
$offset = 6 + 16 * $entries.Count
foreach ($e in $entries) {
    $dim = if ($e.Size -eq 256) { 0 } else { $e.Size }                        # 0 == 256
    $bw.Write([byte]$dim); $bw.Write([byte]$dim); $bw.Write([byte]0); $bw.Write([byte]0)
    $bw.Write([int16]1); $bw.Write([int16]32)
    $bw.Write([int]$e.Data.Length); $bw.Write([int]$offset)
    $offset += $e.Data.Length
}
foreach ($e in $entries) { $bw.Write([byte[]]$e.Data) }
$bw.Dispose(); $fs.Dispose()
Write-Host "ICO  -> $icoPath ($((Get-Item $icoPath).Length) o, $($entries.Count) tailles)"

# --- ICNS ---------------------------------------------------------------------

# Types PNG reconnus par macOS 10.7+ : <code> = <côté en pixels>
$icnsTypes = [ordered]@{ ic11 = 32; ic12 = 64; ic07 = 128; ic13 = 256; ic08 = 256
                         ic14 = 512; ic09 = 512; ic10 = 1024 }
$pngCache = @{}
$icnsEntries = foreach ($type in $icnsTypes.Keys) {
    $s = $icnsTypes[$type]
    if (-not $pngCache.ContainsKey($s)) {
        $bmp = New-Scaled $s
        $pngCache[$s] = Get-PngBytes $bmp
        $bmp.Dispose()
    }
    [pscustomobject]@{ Type = $type; Data = $pngCache[$s] }
}

# ICNS est big-endian, contrairement à ICO.
function Write-BE([System.IO.BinaryWriter]$w, [int]$v) {
    $b = [BitConverter]::GetBytes($v); [array]::Reverse($b); $w.Write($b)
}

$icnsPath = Join-Path $here 'bingobreed.icns'
$total = 8 + ($icnsEntries | ForEach-Object { 8 + $_.Data.Length } | Measure-Object -Sum).Sum
$fs = [System.IO.File]::Create($icnsPath)
$bw = New-Object System.IO.BinaryWriter $fs
$bw.Write([System.Text.Encoding]::ASCII.GetBytes('icns')); Write-BE $bw $total
foreach ($e in $icnsEntries) {
    $bw.Write([System.Text.Encoding]::ASCII.GetBytes($e.Type))
    Write-BE $bw (8 + $e.Data.Length)
    $bw.Write([byte[]]$e.Data)
}
$bw.Dispose(); $fs.Dispose()
Write-Host "ICNS -> $icnsPath ($((Get-Item $icnsPath).Length) o, $($icnsEntries.Count) entrées)"

# --- PNG (Linux + icône de fenêtre) -------------------------------------------

$linuxPath = Join-Path $here 'bingobreed.png'
[System.IO.File]::WriteAllBytes($linuxPath, $pngCache[512])
Write-Host "PNG  -> $linuxPath ($((Get-Item $linuxPath).Length) o, 512 px)"

$resDir = Join-Path (Split-Path -Parent $here) 'src/main/resources'
New-Item -ItemType Directory -Force -Path $resDir | Out-Null
$windowPath = Join-Path $resDir 'icon.png'
[System.IO.File]::WriteAllBytes($windowPath, $pngCache[256])
Write-Host "PNG  -> $windowPath ($((Get-Item $windowPath).Length) o, 256 px)"

$master.Dispose()
