param(
    [string]$Url
)

if (-not $Url) {
    Write-Host "No URL provided. Provide -Url to download the Velocity jar or place jar manually in this folder. Example:`n    .\\download-jar.ps1 -Url '<direct-jar-url>'" -ForegroundColor Yellow
    exit 1
}

$dest = Join-Path $PSScriptRoot "velocity.jar"
Write-Host "Downloading $Url to $dest..."
Invoke-WebRequest -Uri $Url -OutFile $dest -UseBasicParsing
Write-Host "Downloaded to $dest"
