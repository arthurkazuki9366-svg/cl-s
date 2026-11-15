#!/usr/bin/env bash
# Compila o SharkClans. Requisitos: JDK 25+ e Maven 3.8+, com acesso a internet (baixa a API do Paper).
set -e
cd "$(dirname "$0")"
mvn -B clean package
echo
echo "Pronto! Copie para a pasta plugins do servidor:"
ls -1 target/SharkClans-*.jar
