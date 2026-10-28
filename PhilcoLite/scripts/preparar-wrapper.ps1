# Baixa apenas o Gradle Wrapper JAR oficial e valida o SHA-256 publicado pela Gradle.
# Nenhum comando ADB e executado por este script.
$ErrorActionPreference = 'Stop'
$raiz = Split-Path -Parent $PSScriptRoot
$destino = Join-Path $raiz 'gradle\wrapper\gradle-wrapper.jar'
$url = 'https://raw.githubusercontent.com/gradle/gradle/v8.11.1/gradle/wrapper/gradle-wrapper.jar'
$sha256 = '2db75c40782f5e8ba1fc278a5574bab070adccb2d21ca5a6e5ed840888448046'
Write-Host "Baixando Gradle Wrapper 8.11.1 da distribuicao oficial..."
Invoke-WebRequest -Uri $url -OutFile $destino -UseBasicParsing
$real = (Get-FileHash -Path $destino -Algorithm SHA256).Hash.ToLowerInvariant()
if ($real -ne $sha256) {
    Remove-Item -LiteralPath $destino -Force
    throw "Falha na verificacao SHA-256. Esperado: $sha256; obtido: $real"
}
Write-Host "SHA-256 verificado. Execute: .\gradlew.bat :app:assembleDebug" -ForegroundColor Green
