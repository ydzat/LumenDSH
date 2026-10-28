#!/data/data/com.termux/files/usr/bin/bash
# Syntax-check the companion host without starting it.
set -euo pipefail
node --check integrations/companion/host.mjs
echo "host syntax ok"
