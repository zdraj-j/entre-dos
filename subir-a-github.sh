#!/usr/bin/env sh
# Sube este repositorio (con su historial) a un repositorio vacío de GitHub.
# Uso: ./subir-a-github.sh https://github.com/TU_USUARIO/entre-dos.git
set -e
if [ -z "$1" ]; then echo "Uso: $0 <url del repositorio vacío en GitHub>"; exit 1; fi
git remote remove origin 2>/dev/null || true
git remote add origin "$1"
git push -u origin main
echo "Listo: $1"
