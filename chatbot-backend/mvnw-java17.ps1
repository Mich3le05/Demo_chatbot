# Script per eseguire Maven con Java 17
$env:JAVA_HOME = "C:\Users\mmandanici\AppData\Local\Programs\Eclipse Adoptium\jdk-17.0.18.8-hotspot"
$env:PATH = "$env:JAVA_HOME\bin;$env:PATH"

Write-Host "=== Using Java 17 ===" -ForegroundColor Green
Write-Host "JAVA_HOME: $env:JAVA_HOME" -ForegroundColor Cyan

& "$PSScriptRoot\mvnw.cmd" $args
