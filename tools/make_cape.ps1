# Generates the ZeroVisual cape (also used for the elytra).
#   make_cape.ps1 -OutPath cape.png
# Layout follows the vanilla 64x32 cape/elytra UVs, drawn at 4x (256x128) for a sharper look.
param([string]$OutPath, [int]$Scale = 4)

Add-Type -AssemblyName System.Drawing

$W = 64 * $Scale
$H = 32 * $Scale
$bmp = New-Object System.Drawing.Bitmap($W, $H, [System.Drawing.Imaging.PixelFormat]::Format32bppArgb)
$g = [System.Drawing.Graphics]::FromImage($bmp)
$g.SmoothingMode = [System.Drawing.Drawing2D.SmoothingMode]::AntiAlias
$g.PixelOffsetMode = [System.Drawing.Drawing2D.PixelOffsetMode]::HighQuality
$g.Clear([System.Drawing.Color]::Transparent)

$blue = [System.Drawing.Color]::FromArgb(255, 56, 140, 255)
$mid = [System.Drawing.Color]::FromArgb(255, 110, 100, 250)
$purple = [System.Drawing.Color]::FromArgb(255, 176, 80, 245)
$dark = [System.Drawing.Color]::FromArgb(255, 10, 10, 16)
$edge = [System.Drawing.Color]::FromArgb(255, 22, 18, 40)

function UvRect([float]$x, [float]$y, [float]$w, [float]$h) {
    return New-Object System.Drawing.RectangleF(($x * $Scale), ($y * $Scale), ($w * $Scale), ($h * $Scale))
}

function Gradient($rect) {
    $b = New-Object System.Drawing.Drawing2D.LinearGradientBrush($rect, $blue, $purple, 0.0)
    $blend = New-Object System.Drawing.Drawing2D.ColorBlend(3)
    $blend.Colors = @($blue, $mid, $purple)
    $blend.Positions = @(0.0, 0.5, 1.0)
    $b.InterpolationColors = $blend
    return $b
}

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

$darkBrush = New-Object System.Drawing.SolidBrush($dark)
$edgeBrush = New-Object System.Drawing.SolidBrush($edge)

# ---- Cape: 10x16x1 box at uv (0,0) ----
# top / bottom strips and sides
$g.FillRectangle($edgeBrush, (UvRect 1 0 10 1))
$g.FillRectangle($edgeBrush, (UvRect 11 0 10 1))
$g.FillRectangle($edgeBrush, (UvRect 0 1 1 16))
$g.FillRectangle($edgeBrush, (UvRect 11 1 1 16))

# outside face (seen from behind the player): dark base with a gradient frame and the star
$outside = UvRect 1 1 10 16
$g.FillRectangle($darkBrush, $outside)
$frame = Gradient $outside
$framePen = New-Object System.Drawing.Pen($frame, (0.75 * $Scale))
$g.DrawRectangle($framePen, ($outside.X + 0.375 * $Scale), ($outside.Y + 0.375 * $Scale), ($outside.Width - 0.75 * $Scale), ($outside.Height - 0.75 * $Scale))

$cx = $outside.X + $outside.Width / 2
$cy = $outside.Y + $outside.Height * 0.45
$outer = 3.6 * $Scale
$inner = $outer * 0.40

$g.SetClip([System.Drawing.RectangleF]$outside)
$glowPath = New-Object System.Drawing.Drawing2D.GraphicsPath
$gr = 6 * $Scale
$glowPath.AddEllipse(($cx - $gr), ($cy - $gr), ($gr * 2), ($gr * 2))
$glow = New-Object System.Drawing.Drawing2D.PathGradientBrush($glowPath)
$glow.CenterColor = [System.Drawing.Color]::FromArgb(140, 110, 80, 255)
$glow.SurroundColors = @([System.Drawing.Color]::FromArgb(0, 10, 10, 16))
$g.FillPath($glow, $glowPath)

$star = StarPath $cx $cy $outer $inner
$starRect = New-Object System.Drawing.RectangleF(($cx - $outer), ($cy - $outer), ($outer * 2), ($outer * 2))
$g.FillPath((Gradient $starRect), $star)

# small "ZV" style accent line under the star
$lineRect = New-Object System.Drawing.RectangleF(($cx - 2.5 * $Scale), ($outside.Y + 12.5 * $Scale), (5 * $Scale), (0.5 * $Scale))
$g.FillRectangle((Gradient $lineRect), $lineRect)
$g.ResetClip()

# inside face (towards the player's back): dark with a subtle gradient
$inside = UvRect 12 1 10 16
$g.FillRectangle($darkBrush, $inside)
$insideGlow = New-Object System.Drawing.Drawing2D.LinearGradientBrush($inside,
    [System.Drawing.Color]::FromArgb(60, 56, 140, 255), [System.Drawing.Color]::FromArgb(60, 176, 80, 245), 90.0)
$g.FillRectangle($insideGlow, $inside)

# ---- Elytra: 10x20x2 box at uv (22,0) ----
$g.FillRectangle($edgeBrush, (UvRect 24 0 20 2))
$g.FillRectangle($edgeBrush, (UvRect 22 2 2 20))
$g.FillRectangle($edgeBrush, (UvRect 34 2 2 20))
$wingOut = UvRect 24 2 10 20
$wingIn = UvRect 36 2 10 20
$g.FillRectangle((Gradient $wingOut), $wingOut)
$g.FillRectangle($darkBrush, $wingIn)
$g.FillRectangle($insideGlow, $wingIn)
foreach ($p in @(@(27, 6, 1.6), @(31, 11, 1.1), @(28, 16, 1.3))) {
    $s = StarPath (($p[0]) * $Scale) (($p[1]) * $Scale) (($p[2]) * $Scale) (($p[2]) * $Scale * 0.4)
    $g.FillPath((New-Object System.Drawing.SolidBrush([System.Drawing.Color]::FromArgb(200, 255, 255, 255))), $s)
}

$g.Dispose()
$bmp.Save($OutPath, [System.Drawing.Imaging.ImageFormat]::Png)
$bmp.Dispose()
"saved $OutPath"

