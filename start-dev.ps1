$root = Split-Path -Parent $MyInvocation.MyCommand.Path

Write-Host "Starting Cloud E-Wallet development environment..." -ForegroundColor Cyan

$envFile = Join-Path $root ".env.local"
if (-not (Test-Path -LiteralPath $envFile)) {
    Write-Error "Missing .env.local file at: $envFile"
    Write-Host "Copy .env.example to .env.local and replace JWT_SECRET." -ForegroundColor Yellow
    exit 1
}

Get-Content -LiteralPath $envFile | ForEach-Object {
    $line = $_.Trim()
    if ($line -and -not $line.StartsWith("#")) {
        $parts = $line -split "=", 2
        if ($parts.Count -eq 2) {
            [Environment]::SetEnvironmentVariable(
                $parts[0].Trim(),
                $parts[1].Trim(),
                "Process"
            )
        }
    }
}

if (-not $env:JWT_SECRET) {
    Write-Error "JWT_SECRET is missing in .env.local"
    exit 1
}

if (-not $env:JWT_EXPIRATION) {
    $env:JWT_EXPIRATION = "3600"
}

Write-Host "Local environment variables loaded (values hidden)." -ForegroundColor Green

Write-Host "Starting Docker MySQL..." -ForegroundColor Yellow
Set-Location $root
docker compose up -d
if ($LASTEXITCODE -ne 0) {
    Write-Error "Failed to start Docker services. Ensure Docker Desktop is running."
    exit 1
}

Write-Host "Waiting for MySQL container..." -ForegroundColor Yellow
Start-Sleep -Seconds 5

Write-Host "Starting Spring Boot backend..." -ForegroundColor Green
$backendCommand = @"
`$env:SPRING_PROFILES_ACTIVE='local'
Set-Location '$root\backend'
cmd /c mvnw.cmd spring-boot:run
"@
Start-Process powershell -ArgumentList @("-NoExit", "-Command", $backendCommand)

Write-Host "Waiting for backend..." -ForegroundColor Yellow
Start-Sleep -Seconds 5

Write-Host "Starting React frontend..." -ForegroundColor Green
Start-Process powershell -ArgumentList @(
    "-NoExit",
    "-Command",
    "Set-Location '$root\frontend'; npm run dev -- --open"
)

Write-Host "Done." -ForegroundColor Cyan
Write-Host "Backend:  http://localhost:8080"
Write-Host "Frontend: http://localhost:5173"
