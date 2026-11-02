# SESSION_HANDOFF — Museum Slideshow

> Save game del proyecto. `/cierre` lo SOBREESCRIBE ENTERO en cada sesión — acá nunca se apila historia (eso vive en CHANGELOG). El hook SessionStart lo inyecta en cada chat nuevo: **tope de 5.500 bytes** (unas 70 líneas); si se pasa, el hook lo corta y avisa "CORTADO".

**Cierre**: 2026-11-02 (tarde) · **Último commit**: ver `git log -1` (cierre de la sesión 0)

## Qué se hizo
- **SPEC-0 re-sellado y READY** (hash `41981de51d76`), con OK de Guido: integra el diseño de `docs/diseno/decisiones.md` y el 4K, más lo que encontró un red team breve (Sonnet): presupuesto fijo de memoria (2 pantallas 4K), siempre se guarda la copia 4K (1080 = abrirla a la mitad), una sola medida para las dos capas, fundido de las dos capas juntas, horario del Automático (ajuste, 7:00/19:00), color de pared claro y oscuro por separado, criterios nuevos (4K también en el protector, fluidez ≥24 cuadros/s, botones, prueba de 2 h con Paredes de 3).
- **Guido decidió**: las obras redondas llevan un **marco dorado redondo de foto real** (sin marco mientras no esté la foto).
- **PC lista**: Temurin 17 (winget), Android SDK (cmdline-tools, platform 34, build-tools 34.0.0, platform-tools) y `../volumen-maestro` clonado.
- **Sesión 0 hecha** (subagente Sonnet, revisada por Opus): proyecto Gradle con el andamiaje de Volumen Maestro (AGP 8.5.2, Kotlin 2.0.20, Gradle 8.7) sin su cliente ADB; `keystore.jks` propio (alias `museum`); `BuildConfig.DRIVE_API_KEY`/`DRIVE_CARPETA` desde `local.properties`; pantalla mínima "Todavía no hay obras"; ícono y banner provisorios (templo).
- **Evidencia en la TV**: build en verde (1/1 test), instalada, abre (captura + registro `Arranca la app (versión 0.1)`); con la app en pantalla el cartel de Volumen Maestro aparece al subir el volumen; protector sin cambios; ningún `key=` en el registro.

## Estado
- Repo: andamiaje compilando; ninguna pieza del SPEC-0 construida todavía.
- TV: Museum Slideshow 0.1 instalada. Protector = `com.google.android.apps.tv.dreamx/.service.Backdrop` (sin tocar).
- PC: `local.properties` tiene solo `sdk.dir` (sin clave de Drive).

## En curso
- Nada.

## Próximo paso CONCRETO
1. Si Guido ya puso `drive.apiKey` y `drive.carpeta` en `local.properties`: el `curl` de `docs/desarrollo.md` contra "Galerías-prueba" (sin pegar la URL con la clave en ningún lado). Eso cierra la sesión 0 y contrasta el supuesto ALTO de la clave.
2. **Sesión 1**: sincronizador + lector de galerías, con subagentes Sonnet (Opus coordina y revisa).

## Bloqueos
- Falta la clave de Drive y la carpeta "Galerías-prueba" (Guido). La sesión 1 se puede empezar con pruebas locales, pero no verificar en la TV sin eso.

## Contexto que no está en otro doc
- **Forma de trabajo de Guido**: Opus coordina, escribe specs y revisa; **toda construcción con subagentes `model: sonnet`**, con brief claro y archivos grandes por partes. Red team corto, sin revisiones dobles.
- Compilar: `$env:JAVA_HOME='C:\Program Files\Eclipse Adoptium\jdk-17.0.20.101-hotspot'; .\gradlew.bat assembleRelease testDebugUnitTest` (la primera vez ~4 min).
- Si la tele está en el protector, `screencap` muestra el protector y no la app: despertarla con `service call dreams 2` antes de capturar.
- Para mostrarle cosas a 4K: capturas 4K (Edge headless, "modo TV" del borrador 02/03 con `?tv=1&...`), `adb push` y `am start -S -n com.droidlogic.imageplayer/.FullImageActivity -a android.intent.action.VIEW -d file:///sdcard/... -t image/jpeg`. Borrarlas al terminar.
- Guido quiere todo modular y con opciones: cambiar un tema, marco o color no debe obligar a rearmar el resto.
- Las capturas del inicio de la TV muestran la foto de perfil de Guido: borrarlas al terminar.
