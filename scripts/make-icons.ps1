Add-Type -AssemblyName System.Drawing
$src = "C:\Users\Riddim\.qoder\vibe_images\image_1785218007.png"
$res = "d:\Code\Ms Phone Agent\app\src\main\res"
$img = [System.Drawing.Image]::FromFile($src)
# 每个密度：图例尺寸(legacy) / 前景层尺寸(108dp 自适应层)
$map = @{ "mdpi" = @(48, 108); "hdpi" = @(72, 162); "xhdpi" = @(96, 216); "xxhdpi" = @(144, 324); "xxxhdpi" = @(192, 432) }
foreach ($k in $map.Keys) {
    $dir = Join-Path $res ("mipmap-" + $k)
    New-Item -ItemType Directory -Force -Path $dir | Out-Null
    $jobs = @(
        @("ic_launcher.png", $map[$k][0]),
        @("ic_launcher_round.png", $map[$k][0]),
        @("ic_launcher_foreground.png", $map[$k][1])
    )
    foreach ($job in $jobs) {
        $size = $job[1]
        $bmp = New-Object System.Drawing.Bitmap($size, $size)
        $g = [System.Drawing.Graphics]::FromImage($bmp)
        $g.InterpolationMode = [System.Drawing.Drawing2D.InterpolationMode]::HighQualityBicubic
        $g.SmoothingMode = [System.Drawing.Drawing2D.SmoothingMode]::HighQuality
        $g.DrawImage($img, 0, 0, $size, $size)
        $g.Dispose()
        $bmp.Save((Join-Path $dir $job[0]), [System.Drawing.Imaging.ImageFormat]::Png)
        $bmp.Dispose()
    }
}
$img.Dispose()
Write-Output "ICON RESIZE DONE"
Get-ChildItem $res -Recurse -Filter *.png | Select-Object FullName, Length
