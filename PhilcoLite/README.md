# Philco Lite 0.1 — Android TV Launcher

Launcher **experimental**, nativo em Kotlin, feito para TVs com pouca RAM. Base de testes: Philco Android TV 14 / MediaTek MT5867 / `armeabi-v7a` / ~914 MiB de RAM.

## O que já existe

- Home declarada no Android (mas **não substitui a atual ao instalar**).
- Grade de apps instalados, priorizando aplicativos próprios de Android TV.
- Ícones e nomes reais obtidos do Package Manager; sem lista fixa que dependa da Philco.
- Controle remoto com D-pad/OK, foco destacado, cinco colunas e rolagem.
- Relógio/data por broadcast do Android (sem timer em background).
- Favoritos locais: manter OK pressionado no card. Preferências gravadas no próprio dispositivo.
- Acesso rápido às Configurações, recarregar a grade e **voltar ao Projectivy**.
- Sem WebView, internet, analytics, permissões invasivas ou serviços residentes.
- Manifesto com `<queries>` específico para apps de TV e apps convencionais; não utiliza `QUERY_ALL_PACKAGES`.

## Abrir e compilar no Windows

**Pré-requisitos:** Android Studio recente, JDK 17 e Android SDK Platform 35. A build usa AGP 8.9.2 / Kotlin 2.0.21 / Gradle 8.11.1.

O projeto fornece o arquivo `gradle-wrapper.properties` e os scripts para chamar o Wrapper, mas o **JAR oficial não acompanha o ZIP**. No PowerShell, a partir da pasta `PhilcoLite`:

```powershell
.\scripts\preparar-wrapper.ps1
.\gradlew.bat :app:assembleDebug
```

O primeiro comando baixa `gradle-wrapper.jar` do repositório oficial da Gradle no GitHub e verifica seu hash SHA-256 publicado, antes de permitir a execução. Se o PowerShell bloquear scripts, abra o arquivo para inspeção e execute-o em sessão com política apropriada; não precisa alterar as configurações permanentes de segurança do Windows.

A primeira build baixa as dependências oficiais pelo Gradle. Alternativamente, abra a pasta `PhilcoLite` em **Android Studio → Open**, faça o Gradle Sync e use **Build → Build APK(s)** depois de preparar o Wrapper.

O APK de debug será gerado em:

`app/build/outputs/apk/debug/app-debug.apk`

## Alternativa: gerar APK no GitHub Actions

O projeto inclui `.github/workflows/build-apk.yml`. Crie um repositório GitHub com este conteúdo, abra **Actions → Compilar Philco Lite APK → Run workflow** e, se a compilação passar, baixe `philco-lite-debug-apk` nos artefatos do workflow. O APK não foi pré-compilado aqui.

## Instalação segura na Philco (ADB já pareado)

**NÃO DESATIVE O PROJECTIVY!** Não execute comandos `pm disable-user` ou `set-home-activity` nesta primeira etapa. O Projectivy permanece como Home funcional, com o Google Launcher ainda desativado conforme sua configuração atual.

Execute a partir da pasta de `platform-tools` do Windows:

```powershell
.\adb.exe devices
.\adb.exe install -r "C:\CAMINHO\PhilcoLite\app\build\outputs\apk\debug\app-debug.apk"
.\adb.exe shell am start -n br.com.philcolite/.MainActivity
```

Agora teste navegação, abertura de Spotify, Netflix, YouTube, seleção por D-pad, favoritos e o botão **Projectivy**. Mesmo que o app mostre a Home, o Android ainda deve reconhecer o Projectivy como Home padrão.

## Experimento futuro: trocar a Home somente depois de validar

Faça backup do estado atual:

```powershell
.\adb.exe shell pm list packages -d > antes-home-philcolite.txt
.\adb.exe shell cmd role get-role-holders --user 0 android.app.role.HOME
```

Apenas depois dos testes e com ADB persistente, se quiser investigar a substituição, o candidato é:

```powershell
.\adb.exe shell cmd package set-home-activity --user 0 br.com.philcolite/.MainActivity
.\adb.exe shell cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME
```

**Não force a desativação do Projectivy**. Se a Philco continuar voltando para ele, mantenha a configuração estável até termos uma recuperação comprovada.

**Rollback (restaurar Projectivy):**

```powershell
.\adb.exe shell cmd package set-home-activity --user 0 com.spocky.projengmenu/.ui.home.MainActivity
.\adb.exe shell am start -n com.spocky.projengmenu/.ui.home.MainActivity
```

Se o Philco Lite estiver com falhas, remova-o depois de retornar ao Projectivy:

```powershell
.\adb.exe uninstall br.com.philcolite
```

## Comparação de consumo

No mesmo estado de uso da TV (aguarde estabilizar após o boot), capture:

```powershell
.\adb.exe shell dumpsys meminfo br.com.philcolite
.\adb.exe shell dumpsys meminfo com.spocky.projengmenu
.\adb.exe shell "grep -E 'MemAvailable|SwapFree' /proc/meminfo"
```

Compare `TOTAL PSS`, `GL mtrack`, disponibilidade global e, sobretudo, o tempo e a fluidez da Home. Não confunda RSS com PSS; capturas únicas não são benchmarks rigorosos.

Ou execute `scripts/diagnostico.ps1 -Adb "C:\CAMINHO\platform-tools\adb.exe"`.

## Restrições conhecidas da 0.1

- **Projeto-fonte, não APK compilado**: o ambiente em que este projeto foi gerado não tinha Android SDK nem acesso para baixar dependências; a compilação e os testes reais ainda precisam ser feitos no Android Studio/TV.
- A tela de Configurações usa a Intent pública `android.settings.SETTINGS`; algum firmware pode não ter uma Activity correspondente. O atalho Projectivy permanece como fallback.
- Alguns aplicativos da fabricante podem não anunciar `LEANBACK_LAUNCHER`/`LAUNCHER` e, por isso, não aparecer na grade. Nunca removemos componentes para contornar isso.
- Alguns controles não enviam `long-press` como esperado. Nesse caso, os favoritos ainda serão um recurso para melhorar na próxima versão.
- Gradle debug inclui runtime do Kotlin; o tamanho e consumo reais precisam ser medidos na TV. A versão release pode ser reduzida com R8.

## Arquitetura

`app/src/main/java/br/com/philcolite/MainActivity.kt` — Activity única com layout Android Views, montagem de grade, favoritos, Intent de apps e relógio.

`app/src/main/AndroidManifest.xml` — filtros `HOME`, `DEFAULT`, `LEANBACK_LAUNCHER` e visibilidade restrita de apps.

Sem bibliotecas adicionais, Compose, rede ou serviços.

## Próximas versões

- v0.2: cache e carregamento incremental de ícones; seleção de favoritos via menu.
- v0.3: benchmark controlado de inicialização e otimizações de foco/scroll.
- v1.0: teste como Home padrão com procedimento de recuperação comprovado.

Feito para experimentar com segurança em hardware real. Antes de instalar como Home: **validar sempre o rollback**.
