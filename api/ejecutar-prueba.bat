@echo off
REM Script para ejecutar ManualTester en Windows
REM Hospital Santa Clotilde - SIH.SALUS Team

echo ╔════════════════════════════════════════════════════════════════╗
echo ║   EJECUTANDO PRUEBA MANUAL - MÓDULO DE INTEROPERABILIDAD      ║
echo ╚════════════════════════════════════════════════════════════════╝
echo.

cd /d %~dp0
mvn clean test-compile exec:java

pause


