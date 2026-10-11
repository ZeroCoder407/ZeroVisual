# Generates the ZeroVisual logo.
#   make_logo.ps1 -OutPath icon.png -Size 256            -> star on a black square (mod icon / README)
#   make_logo.ps1 -OutPath star.png -Size 64 -StarOnly   -> star with glow on a transparent background
#   add -White for a white star that can be tinted in code
param([string]$OutPath, [int]$Size = 512, [switch]$StarOnly, [switch]$White)

Add-Type -AssemblyName System.Drawing

$bmp = New-Object System.Drawing.Bitmap($Size, $Size, [System.Drawing.Imaging.PixelFormat]::Format32bppArgb)
$g = [System.Drawing.Graphics]::FromImage($bmp)
$g.SmoothingMode = [System.Drawing.Drawing2D.SmoothingMode]::AntiAlias
$g.InterpolationMode = [System.Drawing.Drawing2D.InterpolationMode]::HighQualityBicubic
$g.PixelOffsetMode = [System.Drawing.Drawing2D.PixelOffsetMode]::HighQuality
$g.Clear([System.Drawing.Color]::Transparent)

function StarPath([float]$cx, [float]$cy, [float]$outer, [float]$inner) {
    $pts = New-Object 'System.Drawing.PointF[]' 10
    for ($i = 0; $i -lt 10; $i++) {
        $r = if ($i % 2 -eq 0) { $outer } else { $inner }
        $a = -[Math]::PI / 2 + $i * [Math]::PI / 5
        $pts[$i] = New-Object System.Drawing.PointF(($cx + $r * [Math]::Cos($a)), ($cy + $r * [Math]::Sin($a)))
    }
    $p = New-Object System.Drawing.Drawing2D.GraphicsPath
    $p.AddPolygon($pts)
    return $p
}

$s = [float]$Size
$cx = $s / 2
$cy = $s / 2 + $s * 0.025
$outer = if ($StarOnly) { $s * 0.40 } else { $s * 0.34 }
$inner = $outer * 0.40

if (-not $StarOnly) {
    # Background: near-black square
    $bgBrush = New-Object System.Drawing.SolidBrush([System.Drawing.Color]::FromArgb(255, 8, 8, 12))
    $g.FillRectangle($bgBrush, 0, 0, $s, $s)

    # Soft glow behind the star
    $glowPath = New-Object System.Drawing.Drawing2D.GraphicsPath
    $gr = $s * 0.46
    $glowPath.AddEllipse(($cx - $gr), ($cy - $gr), ($gr * 2), ($gr * 2))
    $glow = New-Object System.Drawing.Drawing2D.PathGradientBrush($glowPath)
    $glow.CenterColor = [System.Drawing.Color]::FromArgb(150, 110, 80, 255)
    $glow.SurroundColors = @([System.Drawing.Color]::FromArgb(0, 8, 8, 12))
    $g.FillPath($glow, $glowPath)
}

# Blurred halo: the star stroked with wide translucent pens
$haloSteps = if ($StarOnly) { 4 } else { 6 }
for ($i = $haloSteps; $i -ge 1; $i--) {
    $halo = StarPath $cx $cy $outer $inner
    $haloColor = if ($White) { [System.Drawing.Color]::FromArgb(22, 255, 255, 255) } else { [System.Drawing.Color]::FromArgb(18, 120, 90, 255) }
    $pen = New-Object System.Drawing.Pen($haloColor, ($s * 0.018 * $i))
    $pen.LineJoin = [System.Drawing.Drawing2D.LineJoin]::Round
    $g.DrawPath($pen, $halo)
    $pen.Dispose()
}

# Star body: horizontal gradient, blue (left) to purple (right)
$star = StarPath $cx $cy $outer $inner
$rect = New-Object System.Drawing.RectangleF(($cx - $outer), ($cy - $outer), ($outer * 2), ($outer * 2))
$fill = New-Object System.Drawing.Drawing2D.LinearGradientBrush($rect,
    [System.Drawing.Color]::FromArgb(255, 56, 132, 255),
    [System.Drawing.Color]::FromArgb(255, 168, 85, 247), 0.0)
$blend = New-Object System.Drawing.Drawing2D.ColorBlend(3)
$blend.Colors = @(
    [System.Drawing.Color]::FromArgb(255, 56, 140, 255),
    [System.Drawing.Color]::FromArgb(255, 110, 100, 250),
    [System.Drawing.Color]::FromArgb(255, 176, 80, 245))
$blend.Positions = @(0.0, 0.5, 1.0)
$fill.InterpolationColors = $blend
if ($White) {
    $fill = New-Object System.Drawing.SolidBrush([System.Drawing.Color]::White)
}
$g.FillPath($fill, $star)

if (-not $StarOnly) {
    # Thin light rim on the background edge
    $rimPen = New-Object System.Drawing.Pen([System.Drawing.Color]::FromArgb(40, 140, 120, 255), ($s * 0.008))
    $g.DrawRectangle($rimPen, 1, 1, ($s - 2), ($s - 2))
}

$g.Dispose()
$bmp.Save($OutPath, [System.Drawing.Imaging.ImageFormat]::Png)
$bmp.Dispose()
"saved $OutPath"

