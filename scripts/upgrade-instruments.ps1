param([switch]$Apply)
$ErrorActionPreference = 'Stop'
$instrumentRoot = Split-Path -Parent $PSScriptRoot
Push-Location (Join-Path $instrumentRoot 'backend')
try {
    $reportPath = 'target/surefire-reports/TEST-com.shopflow.backend.BackendApplicationTests.xml'
    if (!(Test-Path $reportPath)) { throw 'Build/test the backend with .\mvnw.cmd test first.' }
    [xml]$instrumentReport = Get-Content $reportPath
    $instrumentClasspath = (($instrumentReport.testsuite.properties.property | Where-Object { $_.name -eq 'java.class.path' }).value -split ';' | Where-Object { $_ -notmatch '[\\/]target[\\/]test-classes$' }) -join ';'
    $instrumentMode = if ($Apply) { '--apply' } else { '--dry-run' }
    & java '-Dfile.encoding=UTF-8' '-Dstdout.encoding=UTF-8' '-Dstderr.encoding=UTF-8' -cp $instrumentClasspath tools/UpgradeInstrumentCatalog.java $instrumentMode
    if ($LASTEXITCODE -ne 0) { throw 'Instrument upgrade failed; see the error above.' }
} finally { Pop-Location }
