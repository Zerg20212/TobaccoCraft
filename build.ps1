param([switch]$SkipTests)
$ErrorActionPreference = 'Stop'
Set-Location -LiteralPath $PSScriptRoot
$ErrorActionPreference = 'Continue'
$javaSettings = & java -XshowSettings:properties -version 2>&1
$ErrorActionPreference = 'Stop'
if ($LASTEXITCODE -ne 0) { throw 'Установите JDK 21 и добавьте java в PATH.' }
$javaHomeLine = $javaSettings | Select-String 'java.home ='
$env:JAVA_HOME = ($javaHomeLine.ToString() -split '=', 2)[1].Trim()
if (-not (Test-Path -LiteralPath (Join-Path $env:JAVA_HOME 'bin\javac.exe'))) { throw 'Нужен JDK с компилятором javac.' }
$mavenCommand = Get-Command mvn.cmd -ErrorAction SilentlyContinue
if ($mavenCommand) {
    $maven = $mavenCommand.Source
} else {
    $toolsDirectory = Join-Path $PSScriptRoot '.tools'
    $maven = Join-Path $toolsDirectory 'apache-maven-3.9.9\bin\mvn.cmd'
    if (-not (Test-Path -LiteralPath $maven)) {
        New-Item -ItemType Directory -Path $toolsDirectory -Force | Out-Null
        $archive = Join-Path $toolsDirectory 'apache-maven-3.9.9-bin.zip'
        $base = 'https://repo.maven.apache.org/maven2/org/apache/maven/apache-maven/3.9.9/apache-maven-3.9.9-bin.zip'
        Invoke-WebRequest -UseBasicParsing -Uri $base -OutFile $archive
        $expected = (Invoke-WebRequest -UseBasicParsing -Uri ($base + '.sha512')).Content.Trim().Split(' ')[0]
        if ((Get-FileHash -LiteralPath $archive -Algorithm SHA512).Hash -ne $expected) { throw 'Контрольная сумма Maven не совпала.' }
        Expand-Archive -LiteralPath $archive -DestinationPath $toolsDirectory -Force
    }
}
$arguments = @('-B', '-ntp', '-Dmaven.repo.local=.m2', 'clean', 'verify')
if ($SkipTests) { $arguments += '-DskipTests' }
& $maven @arguments
if ($LASTEXITCODE -ne 0) { throw 'Сборка TobaccoCraft завершилась с ошибкой.' }
Write-Host 'Готово: target\TobaccoCraft-1.2.0.jar'
