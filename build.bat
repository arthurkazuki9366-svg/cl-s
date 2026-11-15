@echo off
cd /d "%~dp0"
call mvn -B clean package
if errorlevel 1 (
  echo Falha ao compilar. Verifique se o JDK 25+ e o Maven estao instalados.
  exit /b 1
)
echo.
echo Pronto! O arquivo esta em a pasta target\ (SharkClans-1.1.0.jar). Copie para a pasta plugins do servidor.
