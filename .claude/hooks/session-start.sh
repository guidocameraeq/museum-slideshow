#!/bin/bash
# Hook SessionStart: inyecta el estado del proyecto en cada chat nuevo
# (y también al retomar tras /clear o /compact). Su stdout entra como contexto.
# $CLAUDE_PROJECT_DIR SIEMPRE entre comillas (paths con espacios rompen todo lo demás).
#
# TOPE (kit v2.4.3): la plataforma corta lo que inyecta un hook en 10.000 caracteres SIN avisar.
# Si el handoff es largo, la sesión ve solo el principio y cree que vio todo (medido el 2026-10-22:
# dos proyectos inyectaban 15.500 y 18.000). Por eso:
#   - el handoff tiene tope; si se pasa, se corta y se avisa "CORTADO" (/inicio lee el resto);
#   - TODO, commits y estado de git van recortados;
#   - al final, la HUELLA: cuántos caracteres se inyectaron, para que se vea si crece.
# Los números siguen al hook del Hub (2026-10-22), que ya lo probó. El hook completo, en node y
# con presupuesto por bloque, llega con el kit v3 (docs/kit-v3/SPEC-0-kit-v3.md, P4.1).
TOPE_HANDOFF=5500
TOPE_TOTAL=10000

cd "$CLAUDE_PROJECT_DIR" 2>/dev/null || exit 0

armar() {
  local HANDOFF=docs/SESSION_HANDOFF.md
  local BYTES
  BYTES=$( (wc -c < "$HANDOFF") 2>/dev/null || echo 0)
  if [ "$BYTES" -le "$TOPE_HANDOFF" ]; then
    echo "=== SESSION_HANDOFF.md (inyectado por hook — NO hace falta releer el archivo) ==="
    cat "$HANDOFF" 2>/dev/null
  else
    echo "=== SESSION_HANDOFF.md — CORTADO: tiene $BYTES bytes y el tope es $TOPE_HANDOFF ==="
    # head -c puede partir una letra con tilde: se descarta el último renglón, que puede quedar a medias.
    head -c "$TOPE_HANDOFF" "$HANDOFF" | sed '$d'
    echo ""
    echo "⚠️ El handoff está CORTADO: leé el resto con Read en $HANDOFF ANTES de confiar en lo de arriba, y achicalo en el /cierre."
  fi
  echo ""
  echo "=== TODO.md — primeras 30 líneas ==="
  head -30 docs/TODO.md 2>/dev/null
  echo ""
  echo "=== git — últimos 5 commits (título cortado a 100) + estado del working tree (hasta 15 renglones) ==="
  git log -5 --format='%h %<(100,trunc)%s' 2>/dev/null | sed 's/ *$//'
  git status --short 2>/dev/null | head -15
  echo ""
  echo "=== Recordatorio: decí '/inicio' para el ritual de arranque; no toco nada sin OK del usuario ==="
}

SALIDA=$(armar)
# Cuenta caracteres, no bytes (lo que corta la plataforma). Si el sistema no tiene el locale C.UTF-8,
# wc cuenta bytes: da de más, o sea que avisa antes de tiempo, nunca tarde.
N=$(printf '%s\n' "$SALIDA" | LC_ALL=C.UTF-8 wc -m | tr -d ' ')
if [ "$N" -gt $((TOPE_TOTAL - 300)) ]; then
  # Arriba también: si se pasa del corte, la línea del final es justo la que no llega.
  echo "⚠️ CORTADO o al borde: esto inyecta $N caracteres y la plataforma corta en $TOPE_TOTAL. Lo del final (git) puede faltar: miralo a mano y achicá el handoff o el TODO en el /cierre."
  echo ""
fi
printf '%s\n' "$SALIDA"
echo "inyectado: $N caracteres de $TOPE_TOTAL"
