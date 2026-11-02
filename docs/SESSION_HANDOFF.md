# SESSION_HANDOFF — Museum Slideshow

> Save game del proyecto. `/cierre` lo SOBREESCRIBE ENTERO en cada sesión — acá nunca se apila historia (eso vive en CHANGELOG). El hook SessionStart lo inyecta en cada chat nuevo: **tope de 5.500 bytes** (unas 70 líneas); si se pasa, el hook lo corta y avisa "CORTADO".

**Cierre**: 2026-11-01 · **Último commit**: ver `git log -1` (montaje del proyecto)

## Qué se hizo
- Diseño hecho con Guido en un chat de Volumen Maestro (01/11) y sellado por el Arquitecto: `SPEC-0.md` en **READY** (con red team integrado).
- Probado en la TV el 01/11: acepta otro protector. Se puso el de colores de Android con `settings put secure screensaver_components`, arrancó (`dumpsys dreams` lo mostró, captura vista) y se volvió al Ambient (`...dreamx/.service.Backdrop`).
- Montado el sistema de trabajo: `CLAUDE.md`, rituales `/inicio` `/cierre` `/instalar`, hook de arranque, docs.
- `docs/formato-galeria.md`: el formato del JSON para el agente de arte de Guido.

## Estado
- Repo: solo docs y sistema; **todavía no hay código ni proyecto Gradle** (es la sesión 0 del SPEC-0).
- TV: protector de Guido = Ambient de Google (`com.google.android.apps.tv.dreamx/.service.Backdrop`), sin cambios. 4,3 GB libres. Ya hay 3 protectores instalados: el de Google, el de colores de Android y el de Jellyfin.

## En curso
- Nada.

## Próximo paso CONCRETO
Ejecutar la sesión 0 del SPEC-0: armar el proyecto Gradle (solo el andamiaje de `../volumen-maestro`, sin su cliente ADB), crear la firma propia y verificar con `curl` que la clave de Drive lista una carpeta pública. Para eso Guido tiene que crear la clave de Drive (pasos en `docs/desarrollo.md`).

## Bloqueos
- Falta la clave de Drive y la carpeta "Galerías-prueba" con sus 2 galerías (las arma Guido; ver `docs/TODO.md`).

## Contexto que no está en otro doc
- Nombre e ícono elegidos por Guido: "Museum Slideshow", ícono de templo griego (tipo Partenón).
- Guido quiere temas y opciones para todo: si algo puede ser ajuste, es ajuste.
- Las capturas del inicio de la TV muestran la foto de perfil de Guido: borrarlas al terminar.
