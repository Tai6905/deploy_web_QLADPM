param([switch]$Apply)
$ErrorActionPreference = 'Stop'
$seedBackendDirectory = Join-Path (Split-Path -Parent $PSScriptRoot) 'backend'
Push-Location -LiteralPath $seedBackendDirectory
try {
    $seedReportPath = 'target/surefire-reports/TEST-com.shopflow.backend.BackendApplicationTests.xml'
    if (-not (Test-Path -LiteralPath $seedReportPath)) {
        throw 'Build the backend with tests first: cd backend; .\mvnw.cmd package'
    }
    [xml]$seedReport = Get-Content -LiteralPath $seedReportPath
    $seedRawClasspath = ($seedReport.testsuite.properties.property | Where-Object { $_.name -eq 'java.class.path' }).value
    if (-not $seedRawClasspath) { throw 'Backend test report does not contain java.class.path.' }
    $seedClasspath = ($seedRawClasspath -split ';' | Where-Object { $_ -notmatch '[\\/]target[\\/]test-classes$' }) -join ';'
    $seedMode = if ($Apply) { '--apply' } else { '--dry-run' }
    & java '-Dfile.encoding=UTF-8' '-Dstdout.encoding=UTF-8' '-Dstderr.encoding=UTF-8' -cp $seedClasspath tools/SeedMocStudioCatalog.java $seedMode
    if ($LASTEXITCODE -ne 0) { throw 'Catalog seed failed. Inspect the error above and final transaction status. Existing SKUs are skipped on retry.' }
} finally {
    Pop-Location
}
