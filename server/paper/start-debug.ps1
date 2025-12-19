param()

$serverDir = $PSScriptRoot
$jar = Join-Path $serverDir "paper.jar"
if (-not (Test-Path $jar)) {
    Write-Host "paper.jar not found in $serverDir. Run download-jar.ps1 or place the jar manually." -ForegroundColor Red
    exit 1
}

$eula = Join-Path $serverDir "eula.txt"
if (-not (Test-Path $eula)) {
    "eula=true" | Out-File -FilePath $eula -Encoding ASCII
    Write-Host "Created eula.txt (eula=true)"
}

Write-Host "Starting Paper with JDWP on port 5005..."
java -agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=*:5005 -jar $jar nogui
