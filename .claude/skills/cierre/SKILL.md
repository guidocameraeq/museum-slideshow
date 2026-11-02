---
name: cierre
description: Ritual de cierre de sesión de Museum Slideshow. Usar cuando el usuario dice "cierre", "cerrá la sesión", "voy a comprimir", "compact ya", "prepará todo para compactar" o "cierre parcial".
---

# /cierre — cerrar la sesión (después el chat se descarta)

Dos modos:

- **`cierre`** (default, cierre completo): docs al día → commit → push → reporte. Después el usuario descarta el chat y la próxima misión arranca con `/inicio` en un chat nuevo.
- **`cierre parcial`** (la emergencia): el contexto se llenó a mitad de misión. Ejecutar SOLO los pasos 1-2 + avisar "listo para `/compact`". Sin push obligatorio; el chat sigue. Tras el `/compact`, el hook SessionStart re-inyecta el handoff — asumir que NO leí ningún archivo.

## Pasos (modo completo)

1. **Verificar consistencia**: releer `docs/SESSION_HANDOFF.md` (el previo) + `git status` + `git log` de la sesión. Si se tocó un entorno externo (servidor, DB, servicio), verificar su estado real y comparar contra los docs — **la realidad gana**.
2. **`docs/SESSION_HANDOFF.md`** — sobreescribir ENTERO. Es la ÚNICA narrativa de la sesión:
   - Fecha/hora de cierre · último commit
   - Qué se hizo (5-15 bullets) · en qué estado quedó (branch / entorno / DB)
   - Lo que quedó en curso · **próximo paso CONCRETO** (nunca "seguir avanzando") · bloqueos
   - Archivos tocados · contexto importante que no esté en otros docs
   - **Tope: 5.500 bytes** (`wc -c docs/SESSION_HANDOFF.md`). Si se pasa, el hook lo corta: lo viejo va al CHANGELOG, no se aprieta la letra.
     Al terminar, correr `bash .claude/hooks/session-start.sh | tail -1` y mirar la huella: tiene que quedar bajo 9.700 caracteres.
3. **`docs/CHANGELOG.md`** — entrada nueva al tope (Added / Changed / Fixed / Removed según corresponda).
4. **`docs/TODO.md`** — tareas completadas AFUERA (la historia queda en CHANGELOG); nuevas con criticidad. El header es solo `YYYY-MM-DD — ver SESSION_HANDOFF.md`.
5. **Otros docs de estado** (si el proyecto los tiene): STATUS solo si cambió el estado de un bloque (tocar la fila, nunca re-contar la sesión); DECISIONS si hubo decisión técnica con alternativas (ADR-NNN + línea en el índice); REJECTED si se descartó algo por preferencia.
6. **Frescura de `docs-fyd/`** (SOLO si existe `docs-fyd/` en la raíz; si no, salteá este paso entero). Paso ≥3: **NO corre en `cierre parcial`**. Marca staleness, no regenera:
   - Corré tu **propio** relevamiento (no reutilices el del paso 1): `git diff --name-only` (working tree) + `git log --name-only <primer-commit-de-la-sesión>..HEAD` = los archivos que la sesión tocó.
   - Cruzalos contra la **watchlist**: migraciones/schema · manifiestos de dependencias · `.env`/config · archivos de instrucciones-IA (CLAUDE.md, etc.) · infra/deploy (Dockerfile, compose, k8s, terraform, CI).
   - **Si hay match**: editá SOLO la línea "Frescura" de `docs-fyd/ESTADO.md` → `PENDIENTE REGENERAR (YYYY-MM-DD) — categorías tocadas: <las que matchearon>`. Sin match: no tocás nada.
   - **Marca, no regenera**: NO corras `/docs-fyd`, NO reescribas ningún otro `.md` de `docs-fyd/`, NO toques campos `[completar]` ni `docs/`. La regeneración pesada la corre el humano con `/docs-fyd` (que limpia el flag).
6-bis. **¿Sirvió el relevamiento?** (SOLO si existe `docs/relevamiento/*/TABLERO.md`; si no, salteá este paso entero). Paso ≥3: **NO corre en `cierre parcial`**. Mirá el `chequeo:` de cada TABLERO:
   - **`chequeo:` con fecha ya cumplida y sin `5-sirvio.md` al lado** → imprimí **UNA línea**: *"«\<título\>» cumple sus semanas de uso — corré `relevamiento sirvió` cuando puedas."*
   - Cualquier otro caso (`-`, `PENDIENTE`, fecha futura, o ya existe `5-sirvio.md`) → **no imprimas nada.**
   - **No preguntes, no generes ningún archivo, no frenes el cierre.** Es un aviso de una línea y nada más.
7. **SPECs**: si `SPEC-0.md` (o un `docs/SPEC-*.md`) quedó implementado en esta sesión → su línea `- Estado:` pasa a `COMPLETO <fecha>` y el spec se queda donde está (no se archiva).
8. **Checklist de regresión** — verificar solo los que esta sesión pudo haber tocado: el protector de Guido quedó como estaba (`settings get secure screensaver_components` igual al del `/inicio`); con la galería en pantalla el volumen sigue andando (`cmd media_session volume --stream 3 --adj raise` → aparece el cartel de Volumen Maestro en la captura); `logcat -d -s MuseumSlideshow` no tiene ningún `key=`.
9. **Consistencia de números**: verificar que ningún dato quedó duplicado en dos docs — cada número vive solo en su fuente única.
10. **Memoria**: si cambió un hecho estructural (de cómo trabaja el usuario o del stack) → actualizar la memoria persistente de Claude.
11. **Commit + push**: `git add -A` → mostrar `git status` al usuario → commit `docs: cierre de sesión YYYY-MM-DD — <tema>` → push.
12. **Reporte final**: SHA · archivos actualizados · próximo paso al retomar · bloqueos · **"Chat listo para descartar — la próxima misión arranca con /inicio en un chat nuevo."**

## Edge cases

- Cambios sin commitear que el usuario NO quiere commitear → preguntar: stash / descartar / commit selectivo / dejar sin commitear y anotarlo en el HANDOFF.
- Drift entorno↔repo sin resolver → documentarlo en el HANDOFF como PRIMER paso de la próxima sesión.
- Push falla por conflicto remoto → pull + merge; si el conflicto no es trivial, parar y avisar antes de seguir.
