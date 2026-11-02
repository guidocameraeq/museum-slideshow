---
name: inicio
description: Ritual de inicio de sesión de Museum Slideshow. Usar cuando el usuario dice "inicio", "retomemos", "arranquemos", "¿en qué quedamos?" o al empezar una sesión de trabajo nueva.
---

# /inicio — arrancar una sesión de trabajo

El hook SessionStart ya inyectó `SESSION_HANDOFF.md` + cabecera del TODO + estado git al abrir este chat. **NO los releas** (es costo puro de contexto). Releé el archivo solo ante señal de drift: fecha vieja, contradicción con git, o si el hook no aparece en el contexto.

**Si lo inyectado dice `CORTADO`** (el handoff pasó el tope, o el total quedó al borde de los 10.000 caracteres que corta la plataforma): leé con Read lo que falta **antes** de confirmar nada, y anotá para el `/cierre` que hay que achicar el handoff o el TODO. La última línea, `inyectado: N caracteres de 10000`, es la huella: si no aparece, la salida se cortó.

## Pasos

1. Cruzar handoff vs realidad: ¿el "último commit" del handoff coincide con `git log`? ¿Hay working tree sucio que el handoff no menciona? Detectar también: SPECs implementadas sin marcar `COMPLETO`, TODO que contradice al handoff, drift repo↔TV anotado.
2. Confirmar al usuario en 3 líneas: **Estado** / **Próxima acción** (del handoff) / **Bloqueos**. Si no dio la misión, preguntarla.
3. **NO tocar código ni docs hasta el OK explícito del usuario.**
4. Con la misión confirmada, y SOLO si la misión toca la TV: correr `adb connect 192.168.0.219:5555` y `adb -s 192.168.0.219:5555 shell settings get secure screensaver_components` (anotar qué protector tiene Guido ANTES de tocar nada) — no-bloqueante (si falla, reportarlo y seguir; el inicio nunca se traba por esto).

## Reglas

- **Una sesión = una misión.** Si a mitad de sesión aparece una misión distinta de verdad, proponer `/cierre` y chat nuevo.
- Si el handoff tiene más de 7 días, avisar que puede estar viejo y verificar contra git antes de confiar en él.
