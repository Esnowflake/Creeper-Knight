param(
    [switch]$Offline,
    [switch]$WindowsLoopbackWorkaround,
    [string]$JavaHome = 'D:\Program Files\JDK-17'
)
$ErrorActionPreference = 'Stop'
$env:JAVA_HOME = $JavaHome
$env:GRADLE_USER_HOME = Join-Path $PSScriptRoot '.gradle-home'
if ($WindowsLoopbackWorkaround) {
    $toolDirectory = Join-Path $PSScriptRoot 'tools'
    New-Item -ItemType Directory -Force "$toolDirectory\tmp" | Out-Null
    & "$JavaHome\bin\javac.exe" -d $toolDirectory "$PSScriptRoot\devtools\LoopbackAgent.java"
    if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
    Set-Content -LiteralPath "$toolDirectory\agent-manifest.txt" -Value 'Premain-Class: LoopbackAgent' -Encoding ascii
    & "$JavaHome\bin\jar.exe" cfm "$toolDirectory\loopback-agent.jar" "$toolDirectory\agent-manifest.txt" -C $toolDirectory LoopbackAgent.class
    if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
    $env:JAVA_TOOL_OPTIONS = '"-javaagent:' + "$toolDirectory\loopback-agent.jar" + '" "-Djava.io.tmpdir=' + "$toolDirectory\tmp" + '"'
}
$buildArgs = @('--no-daemon', 'clean', 'build', 'collectJars')
if ($Offline) { $buildArgs += '--offline' }
& "$PSScriptRoot\gradlew.bat" @buildArgs
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
