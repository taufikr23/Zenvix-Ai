$ErrorActionPreference = 'Stop'
Set-Location -LiteralPath (Split-Path -Parent $PSScriptRoot)

function Invoke-Compose {
    param([string[]]$ComposeArguments)
    $result = & docker compose @ComposeArguments
    if ($LASTEXITCODE -ne 0) { throw "Docker Compose gagal: $ComposeArguments" }
    return $result
}

function Assert-Health {
    $response = Invoke-RestMethod -Uri $healthUrl -TimeoutSec 10
    if ($response.status -ne 'UP') { throw 'Health belum UP.' }
    if ($response.PSObject.Properties.Name -contains 'components') {
        throw 'Detail internal database terekspos.'
    }
}

if (-not (Test-Path -LiteralPath '.env')) { throw 'Jalankan scripts/setup-env.ps1 dahulu.' }
$portLine = Get-Content -LiteralPath '.env' | Where-Object { $_ -match '^BACKEND_PORT=' } | Select-Object -First 1
$port = if ($portLine) { ($portLine -split '=', 2)[1].Trim() } else { '8080' }
$healthUrl = "http://localhost:$port/actuator/health"
Invoke-Compose -ComposeArguments @('-f', 'compose.yaml', 'up', '-d', '--build', '--wait', '--wait-timeout', '240')
Assert-Health

# Hanya baris sementara milik pengujian ini yang ditambah dan dihapus.
$markerEmail = 'persist-' + [Guid]::NewGuid().ToString('N') + '@example.test'
$sql = "INSERT INTO users(email,password_hash) VALUES ('$markerEmail','test-only-hash');"
$psqlCommand = 'psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" -v ON_ERROR_STOP=1 -At'
try {
    $sql | & docker compose exec -T db sh -c $psqlCommand
    if ($LASTEXITCODE -ne 0) { throw 'Gagal menulis data uji.' }
    Invoke-Compose -ComposeArguments @('restart', 'db', 'backend')
    Invoke-Compose -ComposeArguments @('up', '-d', '--wait', '--wait-timeout', '180')
    Assert-Health
    $result = "SELECT count(*) FROM users WHERE email='$markerEmail';" |
        & docker compose exec -T db sh -c $psqlCommand
    if ($LASTEXITCODE -ne 0 -or ($result -join '').Trim() -ne '1') {
        throw 'Pemeriksaan persistensi gagal.'
    }
    Write-Host 'LULUS: health UP dan data tetap tersedia setelah restart.'
} finally {
    "DELETE FROM users WHERE email='$markerEmail';" |
        & docker compose exec -T db sh -c $psqlCommand
    if ($LASTEXITCODE -ne 0) { Write-Warning 'Baris uji belum berhasil dibersihkan.' }
}
