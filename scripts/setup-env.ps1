$ErrorActionPreference = 'Stop'
$projectPath = Split-Path -Parent $PSScriptRoot
$envPath = Join-Path $projectPath '.env'
$content = if (Test-Path -LiteralPath $envPath) {
    [System.IO.File]::ReadAllText($envPath)
} else {
    [System.IO.File]::ReadAllText((Join-Path $projectPath '.env.example'))
}

function New-RandomSecret {
    param([switch]$Base64)
    $bytes = New-Object byte[] 32
    $rng = [System.Security.Cryptography.RandomNumberGenerator]::Create()
    try { $rng.GetBytes($bytes) } finally { $rng.Dispose() }
    if ($Base64) { return [Convert]::ToBase64String($bytes) }
    return [BitConverter]::ToString($bytes).Replace('-', '').ToLowerInvariant()
}

function Set-SecretIfMissing {
    param([string]$Name, [switch]$Base64)
    $pattern = '(?m)^' + [regex]::Escape($Name) + '=(.*)\r?$'
    $match = [regex]::Match($script:content, $pattern)
    if ($match.Success -and -not [string]::IsNullOrWhiteSpace($match.Groups[1].Value)) { return }
    $line = $Name + '=' + (New-RandomSecret -Base64:$Base64)
    if ($match.Success) {
        $script:content = [regex]::Replace($script:content, $pattern, $line)
    } else {
        $script:content = $script:content.TrimEnd() + [Environment]::NewLine + $line + [Environment]::NewLine
    }
}

Set-SecretIfMissing -Name 'DB_PASSWORD'
Set-SecretIfMissing -Name 'JWT_SECRET' -Base64
[System.IO.File]::WriteAllText($envPath, $content, (New-Object System.Text.UTF8Encoding($false)))
Write-Host '.env siap. Rahasia yang sudah terisi dipertahankan; nilai tidak ditampilkan.'
