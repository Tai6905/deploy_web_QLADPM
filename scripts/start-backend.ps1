param([switch]$BootstrapOwner)
$ErrorActionPreference = 'Stop'
$backendDirectory = Join-Path (Split-Path -Parent $PSScriptRoot) 'backend'
$localConfig = Join-Path $backendDirectory 'application-local.properties'
$savedPassword = $env:DB_PASSWORD
$savedOwnerEmail = $env:BOOTSTRAP_OWNER_EMAIL
$savedOwnerPassword = $env:BOOTSTRAP_OWNER_PASSWORD
try {
    $passwordSetting = if (Test-Path -LiteralPath $localConfig) {
        Select-String -LiteralPath $localConfig -Pattern '^\s*spring\.datasource\.password\s*=\s*\S+'
    }
    $hasFilePassword = $passwordSetting -and ($passwordSetting.Line -notmatch '\$\{DB_PASSWORD')
    if (-not $env:DB_PASSWORD -and -not $hasFilePassword) {
        $secret = Read-Host 'Aiven PostgreSQL password (input hidden)' -AsSecureString
        $env:DB_PASSWORD = [System.Net.NetworkCredential]::new('', $secret).Password
        if (-not $env:DB_PASSWORD) { throw 'Database password is required.' }
    }
    if ($BootstrapOwner) {
        $env:BOOTSTRAP_OWNER_EMAIL = Read-Host 'Email for the first shop owner'
        $ownerSecret = Read-Host 'Shop owner password (8-72 UTF-8 bytes, input hidden)' -AsSecureString
        $env:BOOTSTRAP_OWNER_PASSWORD = [System.Net.NetworkCredential]::new('', $ownerSecret).Password
        if ($env:BOOTSTRAP_OWNER_PASSWORD.Length -lt 8 -or
            [System.Text.Encoding]::UTF8.GetByteCount($env:BOOTSTRAP_OWNER_PASSWORD) -gt 72) {
            throw 'Shop owner password must have at least 8 characters and at most 72 UTF-8 bytes.'
        }
    }
    Push-Location -LiteralPath $backendDirectory
    try { & .\mvnw.cmd spring-boot:run; if ($LASTEXITCODE -ne 0) { throw 'Backend startup failed. See the error above.' } }
    finally { Pop-Location }
} finally {
    $env:DB_PASSWORD = $savedPassword
    $env:BOOTSTRAP_OWNER_EMAIL = $savedOwnerEmail
    $env:BOOTSTRAP_OWNER_PASSWORD = $savedOwnerPassword
}
