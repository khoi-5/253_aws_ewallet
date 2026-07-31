$root = Split-Path -Parent $MyInvocation.MyCommand.Path

Write-Host "Starting Cloud E-wallet development environment..." -ForegroundColor Cyan

Write-Host "Starting Docker MySQL..." -ForegroundColor Yellow
Set-Location $root
docker compose up -d

Write-Host "Waiting for MySQL container..." -ForegroundColor Yellow
Start-Sleep -Seconds 5

Write-Host "Starting Spring Boot backend..." -ForegroundColor Green
Start-Process powershell -ArgumentList @(
    "-NoExit",
    "-Command",
    "cd '$root\backend'; cmd /c mvnw.cmd spring-boot:run"
)

Write-Host "Waiting for backend..." -ForegroundColor Yellow
Start-Sleep -Seconds 5

Write-Host "Starting React frontend..." -ForegroundColor Green
Start-Process powershell -ArgumentList @(
    "-NoExit",
    "-Command",
    "cd '$root\frontend'; npm run dev -- --open"
)

Write-Host "Done." -ForegroundColor Cyan
Write-Host "Backend:  http://localhost:8080"
Write-Host "Frontend: http://localhost:5173"