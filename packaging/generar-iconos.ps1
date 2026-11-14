$ErrorActionPreference='Stop'
Add-Type -AssemblyName System.Drawing
$root=Split-Path $PSScriptRoot -Parent
$images=Join-Path $root 'frontend\images'
$source=Get-Content -LiteralPath (Join-Path $images 'tecnopc-icon.svg') -Raw
$pathData=[regex]::Match($source,'<path d="([^"]+)"').Groups[1].Value
$tokens=[regex]::Matches($pathData,'[MHVLZ]|-?\d+(?:\.\d+)?') | ForEach-Object {$_.Value}
$path=New-Object Drawing.Drawing2D.GraphicsPath
$x=0.0;$y=0.0;$startX=0.0;$startY=0.0
for($i=0;$i -lt $tokens.Count;){
    $command=$tokens[$i++]
    switch($command){
        'M' {$x=[float]$tokens[$i++];$y=[float]$tokens[$i++];$startX=$x;$startY=$y;$path.StartFigure()}
        'H' {$next=[float]$tokens[$i++];$path.AddLine([float]$x,[float]$y,$next,[float]$y);$x=$next}
        'V' {$next=[float]$tokens[$i++];$path.AddLine([float]$x,[float]$y,[float]$x,$next);$y=$next}
        'L' {$nextX=[float]$tokens[$i++];$nextY=[float]$tokens[$i++];$path.AddLine([float]$x,[float]$y,$nextX,$nextY);$x=$nextX;$y=$nextY}
        'Z' {$path.CloseFigure();$x=$startX;$y=$startY}
        default {throw "Comando SVG no soportado: $command"}
    }
}
$pngs=@{}
foreach($size in @(16,32,48,180,256)){
    $bitmap=New-Object Drawing.Bitmap($size,$size)
    $g=[Drawing.Graphics]::FromImage($bitmap)
    $g.Clear([Drawing.Color]::Transparent)
    $g.SmoothingMode=[Drawing.Drawing2D.SmoothingMode]::AntiAlias
    $g.ScaleTransform(($size/44.0),($size/44.0));$g.TranslateTransform(2,4)
    $pen=New-Object Drawing.Pen([Drawing.ColorTranslator]::FromHtml('#008cff'),2.8)
    $pen.LineJoin=[Drawing.Drawing2D.LineJoin]::Miter
    $pen.StartCap=[Drawing.Drawing2D.LineCap]::Square;$pen.EndCap=$pen.StartCap
    $g.DrawPath($pen,$path)
    $stream=New-Object IO.MemoryStream
    $bitmap.Save($stream,[Drawing.Imaging.ImageFormat]::Png)
    $pngs[$size]=$stream.ToArray()
    [IO.File]::WriteAllBytes((Join-Path $images "tecnopc-icon-$size.png"),$pngs[$size])
    $stream.Dispose();$pen.Dispose();$g.Dispose();$bitmap.Dispose()
}
$sizes=@(16,32,48,256)
$ico=New-Object IO.MemoryStream
$writer=New-Object IO.BinaryWriter($ico)
$writer.Write([uint16]0);$writer.Write([uint16]1);$writer.Write([uint16]$sizes.Count)
$offset=6+16*$sizes.Count
foreach($size in $sizes){
    $dimension=if($size -eq 256){0}else{$size}
    $writer.Write([byte]$dimension);$writer.Write([byte]$dimension);$writer.Write([byte]0);$writer.Write([byte]0)
    $writer.Write([uint16]1);$writer.Write([uint16]32);$writer.Write([uint32]$pngs[$size].Length);$writer.Write([uint32]$offset)
    $offset+=$pngs[$size].Length
}
foreach($size in $sizes){$writer.Write([byte[]]$pngs[$size])}
[IO.File]::WriteAllBytes((Join-Path $root 'frontend\favicon.ico'),$ico.ToArray())
$resource=Join-Path $root 'desktop-common\src\main\resources\com\tecnopc\ui\tecnopc-icon.png'
[IO.File]::WriteAllBytes($resource,$pngs[256])
$writer.Dispose();$ico.Dispose();$path.Dispose()
Write-Output 'Generados SVG, PNG e ICO de TecnoPC.'
