$ErrorActionPreference = 'Stop'
$cancellationRoot = Split-Path -Parent $PSScriptRoot
Push-Location (Join-Path $cancellationRoot 'backend')
try {
    $cancellationReportPath = 'target/surefire-reports/TEST-com.shopflow.backend.BackendApplicationTests.xml'
    if (!(Test-Path $cancellationReportPath)) { throw 'Run .\mvnw.cmd test from backend first.' }
    [xml]$cancellationReport = Get-Content $cancellationReportPath
    $cancellationClasspath = (($cancellationReport.testsuite.properties.property | Where-Object { $_.name -eq 'java.class.path' }).value -split ';' | Where-Object { $_ -notmatch '[\\/]target[\\/]test-classes$' }) -join ';'
    & java '-Dfile.encoding=UTF-8' '-Dstdout.encoding=UTF-8' '-Dstderr.encoding=UTF-8' -cp $cancellationClasspath tools/OrderCancellationSmoke.java
    if ($LASTEXITCODE -ne 0) { throw 'Cancellation migration/smoke check failed.' }
} finally { Pop-Location }
