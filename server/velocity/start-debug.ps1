param()

$serverDir = $PSScriptRoot
$jar = Join-Path $serverDir "velocity.jar"
if (-not (Test-Path $jar)) {
    Write-Host "velocity.jar not found in $serverDir. Run download-jar.ps1 or place the jar manually." -ForegroundColor Red
    exit 1
}

Write-Host "Starting Velocity with JDWP on port 5006..."
java -agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=*:5006 -jar $jar
