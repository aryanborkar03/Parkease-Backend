@echo off
setlocal enabledelayedexpansion

REM ==== CONFIG ====
REM Root folder of your backend
set ROOT=C:\Users\Hp\Desktop\Parkease-Backend

REM Your SonarQube token (ParkEase)
set SONAR_TOKEN=squ_c38a17264a95cd5da299cf600214c584cb936c9b

REM List of services to process
set SERVICES=auth-service booking-service payment-service parkinglot-service parkingspot-service analytics-service notification-service vehicle-service

echo.
echo ============================================
echo   ParkEase - Run tests + Sonar for all services
echo   ROOT: %ROOT%
echo ============================================
echo.

for %%S in (%SERVICES%) do (
    echo --------------------------------------------
    echo Processing service: %%S
    echo --------------------------------------------
    cd /d "%ROOT%\%%S"
    if errorlevel 1 (
        echo [ERROR] Could not change directory to %ROOT%\%%S
        echo.
        goto :continue
    )

    echo [STEP] Running mvnw clean verify for %%S ...
    call .\mvnw.cmd clean verify
    if errorlevel 1 (
        echo [ERROR] Tests/build failed for %%S (clean verify)
        echo.
        goto :continue
    )

    echo [STEP] Running mvnw sonar:sonar for %%S ...
    call .\mvnw.cmd sonar:sonar -Dsonar.token=%SONAR_TOKEN%
    if errorlevel 1 (
        echo [ERROR] Sonar analysis failed for %%S (sonar:sonar)
        echo.
        goto :continue
    )

    echo [OK] Finished %%S successfully.
    echo.

    :continue
)

echo ============================================
echo   All services processed. Check above for any [ERROR].
echo ============================================
echo.
pause
endlocal