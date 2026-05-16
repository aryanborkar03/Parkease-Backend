@echo off
setlocal

echo ============================================
echo   ParkEase - tests + Sonar for all services
echo ============================================
echo.

REM AUTH-SERVICE
echo -------- auth-service --------
cd /d "C:\Users\Hp\Desktop\Parkease-Backend\auth-service"
call .\mvnw.cmd clean verify sonar:sonar
echo.

REM BOOKING-SERVICE
echo -------- booking-service --------
cd /d "C:\Users\Hp\Desktop\Parkease-Backend\booking-service"
call .\mvnw.cmd clean verify sonar:sonar
echo.

REM PAYMENT-SERVICE
echo -------- payment-service --------
cd /d "C:\Users\Hp\Desktop\Parkease-Backend\payment-service"
call .\mvnw.cmd clean verify sonar:sonar
echo.

REM PARKINGLOT-SERVICE
echo -------- parkinglot-service --------
cd /d "C:\Users\Hp\Desktop\Parkease-Backend\parkinglot-service"
call .\mvnw.cmd clean verify sonar:sonar
echo.

REM PARKINGSPOT-SERVICE
echo -------- parkingspot-service --------
cd /d "C:\Users\Hp\Desktop\Parkease-Backend\parkingspot-service"
call .\mvnw.cmd clean verify sonar:sonar
echo.

REM ANALYTICS-SERVICE
echo -------- analytics-service --------
cd /d "C:\Users\Hp\Desktop\Parkease-Backend\analytics-service"
call .\mvnw.cmd clean verify sonar:sonar
echo.

REM NOTIFICATION-SERVICE
echo -------- notification-service --------
cd /d "C:\Users\Hp\Desktop\Parkease-Backend\notification-service"
call .\mvnw.cmd clean verify sonar:sonar
echo.

REM VEHICLE-SERVICE
echo -------- vehicle-service --------
cd /d "C:\Users\Hp\Desktop\Parkease-Backend\vehicle-service"
call .\mvnw.cmd clean verify sonar:sonar
echo.

echo ============================================
echo   All services processed.
echo ============================================
echo.
pause
endlocal