<#
Gera o index.json a partir de uma pasta de APKs.

Uso:
  .\tools\gerar-index.ps1 -ApkDir "D:\apks" -Usuario "meuuser" -Repo "minha-loja"

Os metadados vem do proprio APK via aapt2 (parte do build-tools que ja instalamos),
entao nao ha risco de o catalogo divergir do binario: package name, versionCode e
minSdk sao lidos do manifesto real, nao digitados a mao.
#>
[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)] [string] $ApkDir,
    [Parameter(Mandatory = $true)] [string] $Usuario,
    [Parameter(Mandatory = $true)] [string] $Repo,
    [string] $Tag = "apps",
    [string] $Saida = "$PSScriptRoot\..\repo\index.json",
    [string] $Aapt2 = "D:\Android\Sdk\build-tools\34.0.0\aapt2.exe",
    [string] $NomeLoja = "Loja Geely"
)

$ErrorActionPreference = 'Stop'

if (-not (Test-Path $Aapt2))  { throw "aapt2 nao encontrado em $Aapt2" }
if (-not (Test-Path $ApkDir)) { throw "pasta de APKs nao encontrada: $ApkDir" }

# Preserva descricoes/categorias ja escritas a mao: o aapt2 sabe o versionCode,
# mas nao sabe em que categoria voce quer o app nem o texto que voce escreveu.
$anterior = @{}
if (Test-Path $Saida) {
    $json = Get-Content $Saida -Raw | ConvertFrom-Json
    foreach ($a in $json.apps) { $anterior[$a.id] = $a }
    Write-Host "Reaproveitando metadados de $($anterior.Count) app(s) do index anterior."
}

function Get-ApkInfo([string] $Path) {
    $badging = & $Aapt2 dump badging $Path
    if ($LASTEXITCODE -ne 0) { throw "aapt2 falhou em $Path" }
    $texto = $badging -join "`n"

    $info = @{}
    if ($texto -match "package: name='([^']+)'")   { $info.id          = $Matches[1] }
    if ($texto -match "versionCode='([^']*)'")     { $info.versionCode = [int]($Matches[1]) }
    if ($texto -match "versionName='([^']*)'")     { $info.versionName = $Matches[1] }
    if ($texto -match "sdkVersion:'([^']+)'")      { $info.minSdk      = [int]($Matches[1]) }
    if ($texto -match "application-label:'([^']*)'") { $info.name      = $Matches[1] }

    $abis = @()
    if ($texto -match "native-code: (.+)") {
        $abis = ($Matches[1] -split '\s+') | ForEach-Object { $_.Trim("'") } | Where-Object { $_ }
    }
    $info.abis = $abis
    return $info
}

$apps = @()
$arquivos = Get-ChildItem -Path $ApkDir -Filter *.apk -File | Sort-Object Name

if ($arquivos.Count -eq 0) { throw "nenhum .apk encontrado em $ApkDir" }

foreach ($f in $arquivos) {
    Write-Host "Lendo $($f.Name)..." -NoNewline
    $info = Get-ApkInfo $f.FullName
    $hash = (Get-FileHash -Path $f.FullName -Algorithm SHA256).Hash.ToLower()
    $velho = $anterior[$info.id]

    $apps += [ordered]@{
        id          = $info.id
        name        = if ($velho -and $velho.name -and $velho.name -ne $info.name) { $velho.name } else { $info.name }
        summary     = if ($velho) { $velho.summary }     else { "" }
        description = if ($velho) { $velho.description } else { "" }
        category    = if ($velho) { $velho.category }    else { "Outros" }
        author      = if ($velho) { $velho.author }      else { "" }
        icon        = "https://raw.githubusercontent.com/$Usuario/$Repo/main/icons/$($info.id).png"
        versionName = $info.versionName
        versionCode = $info.versionCode
        minSdk      = $info.minSdk
        size        = $f.Length
        sha256      = $hash
        abis        = @($info.abis)
        url         = "https://github.com/$Usuario/$Repo/releases/download/$Tag/$($f.Name)"
        added       = if ($velho -and $velho.added) { $velho.added } else { (Get-Date -Format 'yyyy-MM-dd') }
    }
    Write-Host " $($info.id) v$($info.versionName) (code $($info.versionCode)), minSdk $($info.minSdk)"
}

$index = [ordered]@{
    schema = 1
    repo   = [ordered]@{
        name        = $NomeLoja
        description = "Apps para a multimidia Flyme Auto"
        updated     = (Get-Date).ToUniversalTime().ToString("yyyy-MM-ddTHH:mm:ssZ")
    }
    apps   = $apps
}

New-Item -ItemType Directory -Force -Path (Split-Path $Saida) | Out-Null
$index | ConvertTo-Json -Depth 6 | Out-File -FilePath $Saida -Encoding utf8

Write-Host ""
Write-Host "index.json gerado: $Saida  ($($apps.Count) apps)"

$semTexto = $apps | Where-Object { -not $_.summary }
if ($semTexto) {
    Write-Host ""
    Write-Host "Falta preencher summary/description/category para:" -ForegroundColor Yellow
    $semTexto | ForEach-Object { Write-Host "  - $($_.id)" -ForegroundColor Yellow }
    Write-Host "Edite o index.json a mao; a proxima execucao preserva o que voce escrever." -ForegroundColor Yellow
}
