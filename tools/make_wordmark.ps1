# Generates the ZeroVisual wordmark (star + "ZeroVisual" in Inter):
#   title_logo.png  1024x256  - title screen logo
#   splash.png       512x512  - loading screen logo in the vanilla split layout
#                               (left half of the wordmark on top, right half below)
param([string]$OutDir)

Add-Type -AssemblyName System.Drawing

$root = Split-Path -Parent $PSScriptRoot
$fontFile = Join-Path $root "src\main\resources\assets\zerovisual\font\inter_latin.ttf"
$fonts = New-Object System.Drawing.Text.PrivateFontCollection
$fonts.AddFontFile($fontFile)
$family = $fonts.Families[0]

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

function Gradient($rect) {
    $b = New-Object System.Drawing.Drawing2D.LinearGradientBrush($rect,
        [System.Drawing.Color]::FromArgb(255, 56, 140, 255), [System.Drawing.Color]::FromArgb(255, 176, 80, 245), 0.0)
    $blend = New-Object System.Drawing.Drawing2D.ColorBlend(3)
    $blend.Colors = @([System.Drawing.Color]::FromArgb(255, 56, 140, 255), [System.Drawing.Color]::FromArgb(255, 110, 100, 250), [System.Drawing.Color]::FromArgb(255, 176, 80, 245))
    $blend.Positions = @(0.0, 0.5, 1.0)
    $b.InterpolationColors = $blend
    return $b
}

# ---- wordmark 1024x256 ----
$W = 1024; $H = 256
$bmp = New-Object System.Drawing.Bitmap($W, $H, [System.Drawing.Imaging.PixelFormat]::Format32bppArgb)
$g = [System.Drawing.Graphics]::FromImage($bmp)
$g.SmoothingMode = [System.Drawing.Drawing2D.SmoothingMode]::AntiAlias
$g.TextRenderingHint = [System.Drawing.Text.TextRenderingHint]::AntiAliasGridFit
$g.Clear([System.Drawing.Color]::Transparent)

$font = New-Object System.Drawing.Font($family, 136, [System.Drawing.FontStyle]::Regular, [System.Drawing.GraphicsUnit]::Pixel)
$format = [System.Drawing.StringFormat]::GenericTypographic
$zeroSize = $g.MeasureString("Zero", $font, 10000, $format)
$visualSize = $g.MeasureString("Visual", $font, 10000, $format)

$starSize = 168
$gap = 36
$total = $starSize + $gap + $zeroSize.Width + $visualSize.Width
$x0 = ($W - $total) / 2
$cy = $H / 2

# star with a soft halo
$scx = $x0 + $starSize / 2
for ($i = 5; $i -ge 1; $i--) {
    $halo = StarPath $scx $cy ($starSize / 2) ($starSize / 2 * 0.40)
    $pen = New-Object System.Drawing.Pen([System.Drawing.Color]::FromArgb(22, 120, 90, 255), (6 * $i))
    $pen.LineJoin = [System.Drawing.Drawing2D.LineJoin]::Round
    $g.DrawPath($pen, $halo)
}
$star = StarPath $scx $cy ($starSize / 2) ($starSize / 2 * 0.40)
$starRect = New-Object System.Drawing.RectangleF(($scx - $starSize / 2), ($cy - $starSize / 2), $starSize, $starSize)
$g.FillPath((Gradient $starRect), $star)

# text: "Zero" in the gradient, "Visual" in white
$textY = $cy - $zeroSize.Height / 2
$tx = $x0 + $starSize + $gap
$zeroPath = New-Object System.Drawing.Drawing2D.GraphicsPath([System.Drawing.Drawing2D.FillMode]::Winding)
$zeroPath.AddString("Zero", $family, [int][System.Drawing.FontStyle]::Regular, 136, (New-Object System.Drawing.PointF($tx, $textY)), $format)
$zeroRect = New-Object System.Drawing.RectangleF($tx, $textY, $zeroSize.Width, $zeroSize.Height)
$g.FillPath((Gradient $zeroRect), $zeroPath)
$visualPath = New-Object System.Drawing.Drawing2D.GraphicsPath([System.Drawing.Drawing2D.FillMode]::Winding)
$visualPath.AddString("Visual", $family, [int][System.Drawing.FontStyle]::Regular, 136, (New-Object System.Drawing.PointF(($tx + $zeroSize.Width), $textY)), $format)
$g.FillPath((New-Object System.Drawing.SolidBrush([System.Drawing.Color]::FromArgb(255, 240, 240, 248))), $visualPath)
$g.Dispose()

$bmp.Save((Join-Path $OutDir "title_logo.png"), [System.Drawing.Imaging.ImageFormat]::Png)

# ---- splash 512x512: left half -> top, right half -> bottom ----
$splash = New-Object System.Drawing.Bitmap(512, 512, [System.Drawing.Imaging.PixelFormat]::Format32bppArgb)
$sg = [System.Drawing.Graphics]::FromImage($splash)
$sg.InterpolationMode = [System.Drawing.Drawing2D.InterpolationMode]::HighQualityBicubic
$sg.Clear([System.Drawing.Color]::Transparent)
$sg.DrawImage($bmp, (New-Object System.Drawing.Rectangle(0, 0, 512, 256)), (New-Object System.Drawing.Rectangle(0, 0, 512, 256)), [System.Drawing.GraphicsUnit]::Pixel)
$sg.DrawImage($bmp, (New-Object System.Drawing.Rectangle(0, 256, 512, 256)), (New-Object System.Drawing.Rectangle(512, 0, 512, 256)), [System.Drawing.GraphicsUnit]::Pixel)
$sg.Dispose()
$splash.Save((Join-Path $OutDir "splash.png"), [System.Drawing.Imaging.ImageFormat]::Png)

$bmp.Dispose()
$splash.Dispose()
"saved title_logo.png and splash.png to $OutDir"
