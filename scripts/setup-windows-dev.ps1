<#
Setup script for Windows (no Chocolatey) to get Maven and configure environment for building.

What it does:
- Checks for `java` and `mvn` availability.
- If `mvn` missing, downloads Apache Maven, extracts to `%USERPROFILE%\tools\maven`, and adds its `bin` to the user PATH.
- If `java` is missing, opens the Adoptium Temurin 17 download page and prompts you to install JDK 17 manually (no admin MSI automated install).

Usage (PowerShell):
  # allow the script to run in this session
  Set-ExecutionPolicy -Scope Process -ExecutionPolicy Bypass -Force
  # run the script
  .\scripts\setup-windows-dev.ps1
#>

function Test-CommandExists {
    param([string]$name)
    $null -ne (Get-Command $name -ErrorAction SilentlyContinue)
}

if (Test-CommandExists -name 'mvn') {
    Write-Host "Maven detected (mvn). No action needed." -ForegroundColor Green
    mvn -v
    return
}

if (-not (Test-CommandExists -name 'java')) {
    Write-Host "Java (JDK) not found on PATH." -ForegroundColor Yellow
    Write-Host "This script can continue to install Maven, but you still need a JDK 17 installed for builds." -ForegroundColor Yellow
    Write-Host "Opening the Adoptium Temurin 17 download page in your browser..." -ForegroundColor Cyan
    Start-Process "https://adoptium.net/temurin/releases/?version=17"
    Write-Host "Please download and install JDK 17 (Windows x64 MSI recommended). Press Enter when installation is complete to continue." -ForegroundColor Yellow
    Read-Host
}

$mvnVersion = '3.9.6'
$mvnUrl = "https://dlcdn.apache.org/maven/maven-3/3.9.12/binaries/apache-maven-3.9.12-bin.zip"
$tmpZip = Join-Path $env:TEMP "apache-maven-$mvnVersion-bin.zip"
$installParent = Join-Path $env:USERPROFILE 'tools\maven'

if (-not (Test-Path $installParent)) { New-Item -ItemType Directory -Path $installParent -Force | Out-Null }

Write-Host "Downloading Maven $mvnVersion..." -ForegroundColor Cyan
try {
    Invoke-WebRequest -Uri $mvnUrl -OutFile $tmpZip -UseBasicParsing -ErrorAction Stop
}
catch {
    Write-Host "Failed to download Maven from $mvnUrl. Please check your network or download manually." -ForegroundColor Red
    throw $_
}

Write-Host "Extracting Maven..." -ForegroundColor Cyan
try {
    Expand-Archive -Path $tmpZip -DestinationPath $installParent -Force
}
catch {
    Write-Host "Failed to extract archive. Ensure you have permissions and try again." -ForegroundColor Red
    throw $_
}

$extractedDir = Join-Path $installParent "apache-maven-$mvnVersion"
$mavenBin = Join-Path $extractedDir 'bin'

Write-Host "Configuring user PATH to include Maven bin: $mavenBin" -ForegroundColor Cyan
$userPath = [Environment]::GetEnvironmentVariable('Path', 'User')
if ($userPath -notlike "*$mavenBin*") {
    $newUserPath = if ([string]::IsNullOrEmpty($userPath)) { $mavenBin } else { "$userPath;$mavenBin" }
    [Environment]::SetEnvironmentVariable('Path', $newUserPath, 'User')
    Write-Host "Updated user PATH. New entries will be available in new terminals." -ForegroundColor Green
}
else {
    Write-Host "Maven bin already in user PATH." -ForegroundColor Green
}

# Attempt to set JAVA_HOME if java is present and JAVA_HOME not set
if ([string]::IsNullOrEmpty([Environment]::GetEnvironmentVariable('JAVA_HOME', 'User'))) {
    $javaCmd = Get-Command java -ErrorAction SilentlyContinue
    if ($javaCmd) {
        $javaExe = $javaCmd.Source
        $javaRoot = Split-Path -Parent (Split-Path -Parent $javaExe)
        if ($javaRoot) {
            Write-Host "Setting JAVA_HOME to $javaRoot (per-user)" -ForegroundColor Cyan
            [Environment]::SetEnvironmentVariable('JAVA_HOME', $javaRoot, 'User')
        }
    }
}

Write-Host "Applying environment to current session..." -ForegroundColor Cyan
if (Test-CommandExists -name 'java') {
    $javaCmd = Get-Command java -ErrorAction SilentlyContinue
    $javaExe = $javaCmd.Source
    $javaRoot = Split-Path -Parent (Split-Path -Parent $javaExe)
    $env:JAVA_HOME = $javaRoot
}
$env:PATH = "$mavenBin;$env:PATH"

Write-Host "Verification:" -ForegroundColor Cyan
try { mvn -v } catch { Write-Host "mvn not found in current session. Close and reopen the terminal to pick up the new PATH, or run:`n`$env:PATH = '$mavenBin;`$env:PATH'`nThen run ``mvn -v``" -ForegroundColor Yellow }
try { java -version } catch { Write-Host "java not available in current session." -ForegroundColor Yellow }

Write-Host "Setup complete. Please restart VS Code or open a new terminal to use Maven and the configured JAVA_HOME." -ForegroundColor Green
