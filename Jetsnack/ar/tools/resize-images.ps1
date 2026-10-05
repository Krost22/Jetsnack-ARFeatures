# Downscales the snack photos in res/drawable-nodpi so their shortest side is at most $MaxShortSide px.
# They are shown at 300dp at most (~900px on xxhdpi), but the originals were up to 3750px wide,
# which made every decode slow and memory hungry. Usage (Windows PowerShell):
#   powershell -ExecutionPolicy Bypass -File Jetsnack/ar/tools/resize-images.ps1
param(
    [int]$MaxShortSide = 900,
    [long]$Quality = 85
)

Add-Type -AssemblyName System.Drawing
$dir = Join-Path $PSScriptRoot '..\..\app\src\main\res\drawable-nodpi'
$codec = [System.Drawing.Imaging.ImageCodecInfo]::GetImageEncoders() | Where-Object { $_.MimeType -eq 'image/jpeg' }
$params = New-Object System.Drawing.Imaging.EncoderParameters 1
$params.Param[0] = New-Object System.Drawing.Imaging.EncoderParameter ([System.Drawing.Imaging.Encoder]::Quality, $Quality)

Get-ChildItem $dir -Filter *.jpg | ForEach-Object {
    $path = $_.FullName
    $before = $_.Length
    $source = [System.Drawing.Image]::FromFile($path)
    try {
        # Bake the EXIF orientation into the pixels; the re-encoded file drops the tag.
        if ($source.PropertyIdList -contains 0x0112) {
            switch ($source.GetPropertyItem(0x0112).Value[0]) {
                3 { $source.RotateFlip([System.Drawing.RotateFlipType]::Rotate180FlipNone) }
                6 { $source.RotateFlip([System.Drawing.RotateFlipType]::Rotate90FlipNone) }
                8 { $source.RotateFlip([System.Drawing.RotateFlipType]::Rotate270FlipNone) }
            }
        }
        $short = [Math]::Min($source.Width, $source.Height)
        if ($short -le $MaxShortSide) { return }
        $scale = $MaxShortSide / $short
        $width = [int][Math]::Round($source.Width * $scale)
        $height = [int][Math]::Round($source.Height * $scale)
        $target = New-Object System.Drawing.Bitmap $width, $height
        $graphics = [System.Drawing.Graphics]::FromImage($target)
        $graphics.InterpolationMode = [System.Drawing.Drawing2D.InterpolationMode]::HighQualityBicubic
        $graphics.PixelOffsetMode = [System.Drawing.Drawing2D.PixelOffsetMode]::HighQuality
        $graphics.DrawImage($source, 0, 0, $width, $height)
        $graphics.Dispose()
    } finally {
        $source.Dispose()
    }
    if ($target) {
        $target.Save($path, $codec, $params)
        $target.Dispose()
        $target = $null
        $after = (Get-Item $path).Length
        '{0,-24} {1,7:N0} KB -> {2,6:N0} KB' -f $_.Name, ($before / 1KB), ($after / 1KB)
    }
}
