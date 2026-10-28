param(
    [string]$Adb = 'adb',
    [string]$Saida = 'philcolite-diagnostico.txt'
)
$ErrorActionPreference = 'Stop'
"=== PHILCO LITE ===" | Set-Content -Path $Saida -Encoding UTF8
"=== HOME ===" | Add-Content $Saida
& $Adb shell cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME | Add-Content $Saida
"=== MEMORIA DISPONIVEL ===" | Add-Content $Saida
& $Adb shell "grep -E 'MemAvailable|SwapFree' /proc/meminfo" | Add-Content $Saida
"=== MEMORIA PHILCO LITE ===" | Add-Content $Saida
& $Adb shell dumpsys meminfo br.com.philcolite | Add-Content $Saida
"=== MEMORIA PROJECTIVY ===" | Add-Content $Saida
& $Adb shell dumpsys meminfo com.spocky.projengmenu | Add-Content $Saida
Write-Host "Relatorio salvo: $Saida"
