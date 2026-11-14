$ErrorActionPreference='Stop'
Add-Type -AssemblyName System.Windows.Forms
Add-Type -AssemblyName System.Drawing
$root=Split-Path $PSScriptRoot -Parent
$file=Join-Path $root '.local-secrets\administrador.dpapi'
try {
    $secure=((Get-Content -LiteralPath $file -Raw).Trim()) | ConvertTo-SecureString
    $plain=[Net.NetworkCredential]::new('',$secure).Password
} catch {
    [void][Windows.Forms.MessageBox]::Show('No se pudo recuperar la clave. Abre este archivo con el usuario de Windows que la guardo.','TecnoPC')
    exit 1
}
$form=New-Object Windows.Forms.Form
$form.Text='TecnoPC - Clave guardada del administrador'
$form.Size=New-Object Drawing.Size(590,205)
$form.StartPosition='CenterScreen'
$form.TopMost=$true
$label=New-Object Windows.Forms.Label
$label.Text='Usuario: administrador. Esta es la clave inicial guardada; no restablece una cuenta ya creada.'
$label.Location=New-Object Drawing.Point(15,15)
$label.Size=New-Object Drawing.Size(550,40)
$form.Controls.Add($label)
$box=New-Object Windows.Forms.TextBox
$box.Location=New-Object Drawing.Point(15,60)
$box.Size=New-Object Drawing.Size(540,25)
$box.ReadOnly=$true
$box.UseSystemPasswordChar=$true
$box.Text=$plain
$form.Controls.Add($box)
$show=New-Object Windows.Forms.CheckBox
$show.Text='Mostrar clave'
$show.Location=New-Object Drawing.Point(15,105)
$show.Size=New-Object Drawing.Size(150,25)
$show.Add_CheckedChanged({$box.UseSystemPasswordChar=!$show.Checked})
$form.Controls.Add($show)
$copy=New-Object Windows.Forms.Button
$copy.Text='Copiar clave'
$copy.Location=New-Object Drawing.Point(390,103)
$copy.Size=New-Object Drawing.Size(160,30)
$copy.Add_Click({[Windows.Forms.Clipboard]::SetText($box.Text)})
$form.Controls.Add($copy)
[void]$form.ShowDialog()
$box.Clear();$plain=$null;$secure.Dispose();$form.Dispose()
