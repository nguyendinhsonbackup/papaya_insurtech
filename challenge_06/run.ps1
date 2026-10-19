# Build + run the pipeline. Usage: .\run.ps1 [calculate] [--policy file] [--expenses file] [--out-dir dir]
$ErrorActionPreference = 'Stop'
Set-Location $PSScriptRoot
if (Test-Path build/classes) { Remove-Item -Recurse -Force build/classes }
$sources = Get-ChildItem -Recurse -Filter *.java src/main/java | ForEach-Object { $_.FullName }
javac -encoding UTF-8 -d build/classes $sources
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
$cliArgs = @($args)
if ($cliArgs.Count -eq 0) { $cliArgs = @('calculate') }
java -cp build/classes benefits.Main @cliArgs
exit $LASTEXITCODE
