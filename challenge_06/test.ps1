# Compile main + test sources and run all tests. Exit code 0 = all passed.
$ErrorActionPreference = 'Stop'
Set-Location $PSScriptRoot
if (Test-Path build/test-classes) { Remove-Item -Recurse -Force build/test-classes }
$sources = Get-ChildItem -Recurse -Filter *.java src/main/java, src/test/java | ForEach-Object { $_.FullName }
javac -encoding UTF-8 -d build/test-classes $sources
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
java -cp build/test-classes benefits.TestRunner
exit $LASTEXITCODE
