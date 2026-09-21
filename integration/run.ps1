param([Parameter(Mandatory = $true)][string]$PaperDirectory)
$ErrorActionPreference = 'Stop'
$ErrorActionPreference = 'Continue'
$javaSettings = & java -XshowSettings:properties -version 2>&1
$ErrorActionPreference = 'Stop'
$jdk = (($javaSettings | Select-String 'java.home =').ToString() -split '=', 2)[1].Trim()
$project = Split-Path -Parent $PSScriptRoot
$source = (Resolve-Path -LiteralPath $PaperDirectory).Path
$runtime = Join-Path $project 'target\paper-test'
$jar = Join-Path $project 'target\TobaccoCraft-1.2.0.jar'
if (-not (Test-Path -LiteralPath $jar)) { throw 'Сначала соберите проект: .\build.ps1' }
if (-not (Test-Path -LiteralPath (Join-Path $source 'server.jar'))) { throw 'В исходном каталоге нет server.jar.' }
$eula = Join-Path $source 'eula.txt'
if (-not (Test-Path -LiteralPath $eula) -or -not ([IO.File]::ReadAllText($eula) -match '(?m)^eula=true\s*$')) {
    throw 'Нужен локальный Paper 1.21.1 с уже принятой EULA.'
}
New-Item -ItemType Directory -Path (Join-Path $runtime 'plugins') -Force | Out-Null
foreach ($name in @('server.jar', 'libraries', 'versions', 'cache', 'eula.txt')) {
    $from = Join-Path $source $name
    if (Test-Path -LiteralPath $from) { Copy-Item -LiteralPath $from -Destination $runtime -Recurse -Force }
}
Copy-Item -LiteralPath $jar -Destination (Join-Path $runtime 'plugins') -Force
$properties = @'
server-ip=127.0.0.1
server-port=25586
online-mode=true
enable-status=false
enable-query=false
enable-rcon=false
max-players=1
view-distance=2
simulation-distance=2
level-name=test-world
level-type=minecraft:flat
generator-settings={"layers":[{"block":"minecraft:bedrock","height":1},{"block":"minecraft:dirt","height":2},{"block":"minecraft:grass_block","height":1}],"biome":"minecraft:plains"}
generate-structures=false
spawn-protection=0
allow-nether=false
max-tick-time=60000
sync-chunk-writes=false
'@
[IO.File]::WriteAllText((Join-Path $runtime 'server.properties'), $properties, [Text.UTF8Encoding]::new($false))
$probeClasses = Join-Path $project 'target\probe-classes'
New-Item -ItemType Directory -Path $probeClasses -Force | Out-Null
$dependencies = Get-ChildItem -LiteralPath (Join-Path $project '.m2') -Recurse -Filter '*.jar' | ForEach-Object { $_.FullName }
$classpath = (@($jar) + $dependencies) -join ';'
$compilerArgs = @('-encoding', 'UTF-8', '--release', '21', '-proc:none', '-classpath',
    ('"' + $classpath.Replace('\', '/') + '"'), '-d', ('"' + $probeClasses.Replace('\', '/') + '"'),
    ('"' + (Join-Path $PSScriptRoot 'SmokeProbe.java').Replace('\', '/') + '"'))
$compilerArgs += Get-ChildItem -LiteralPath $PSScriptRoot -Filter '*.java' | Where-Object Name -ne 'SmokeProbe.java' | ForEach-Object { '"' + $_.FullName.Replace('\', '/') + '"' }
$argsFile = Join-Path $project 'target\probe-javac.args'
[IO.File]::WriteAllLines($argsFile, $compilerArgs, [Text.UTF8Encoding]::new($false))
$ErrorActionPreference = 'Continue'
& (Join-Path $jdk 'bin\javac.exe') ('@' + $argsFile)
if ($LASTEXITCODE -ne 0) { throw 'Не удалось скомпилировать проверочный плагин.' }
$ErrorActionPreference = 'Stop'
& (Join-Path $jdk 'bin\jar.exe') --create --file (Join-Path $runtime 'plugins\TobaccoSmokeProbe.jar') -C $probeClasses . -C $PSScriptRoot plugin.yml
if ($LASTEXITCODE -ne 0) { throw 'Не удалось упаковать проверочный плагин.' }
$result = Join-Path $runtime 'plugins\smoke-result.txt'
if (Test-Path -LiteralPath $result) { Remove-Item -LiteralPath $result }
Push-Location -LiteralPath $runtime
try {
    $ErrorActionPreference = 'Continue'
    & java '-Xms256M' '-Xmx768M' '-XX:ActiveProcessorCount=2' '-Dfile.encoding=UTF-8' -jar server.jar --nogui
    if ($LASTEXITCODE -ne 0) { throw 'Тестовый сервер завершился с ошибкой.' }
    if (-not (Test-Path -LiteralPath $result) -or [IO.File]::ReadAllText($result) -ne 'OK') {
        throw 'Проверка не пройдена. Подробности: target\paper-test\logs\latest.log'
    }
    Write-Host 'Проверка Paper 1.21.1 пройдена.'
} finally { Pop-Location }
