#!/bin/bash
# Script para ejecutar ManualTester en Linux/Mac
# Hospital Santa Clotilde - SIH.SALUS Team

echo "╔════════════════════════════════════════════════════════════════╗"
echo "║   EJECUTANDO PRUEBA MANUAL - MÓDULO DE INTEROPERABILIDAD      ║"
echo "╚════════════════════════════════════════════════════════════════╝"
echo

cd "$(dirname "$0")"
mvn clean test-compile exec:java


