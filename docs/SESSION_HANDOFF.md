# SESSION_HANDOFF — Museum Slideshow

> Save game del proyecto. `/cierre` lo SOBREESCRIBE ENTERO en cada sesión — acá nunca se apila historia (eso vive en CHANGELOG). El hook SessionStart lo inyecta en cada chat nuevo: **tope de 5.500 bytes** (unas 70 líneas); si se pasa, el hook lo corta y avisa "CORTADO".

**Cierre**: 2026-11-02 (19:00) · **Último commit**: ver `git log -1` (sesión 3)

## Qué se hizo
- **Sesión 2 construida** (2 subagentes Sonnet, revisados por Opus): `acomodo/` (puerto del borrador 3 + decisiones de Guido), `temas/` (4 temas, colores con bordó, marcos), `ajustes/Preferencias` (defaults, sin persistir), `galeria/` (dos superficies 4K, capa Compose, fundido, control, vista Explicación). Fuentes EB Garamond e Inter (OFL) en `res/font`. 147 tests.
- **4K VERIFICADO**: capas 3840×2160 `composition type=DEVICE` + **Guido vio la diferencia** en `prueba-1080-vs-4k.jpg` ("la derecha se ve muchísimo mejor"). La grilla de 1 px se distingue apenas (JPEG del sincronizador).
- **Fundido**: redibujar con `lockHardwareCanvas` daba ~19 cuadros/s (también a 1080). Ahora lo hace el compositor: dos `SurfaceView`, alpha de la de arriba vía `SurfaceControl.Transaction` (`View.setAlpha` NO llega al compositor en esta tele) → **~58 cuadros/s**, duración exacta (la tele tiene `animator_duration_scale=0.5`; el reloj es propio).
- **Memoria**: obras como `Bitmap.Config.HARDWARE` → PSS de 220 a ~114 MB.
- **Probado en la TV con capturas**: los 4 temas, claro/oscuro, Pared de 2 con nombres, tondo en Inmersivo con contador, Libro de arte, OK = pantalla completa, mantener OK = vista Explicación, Arriba/Abajo en Pared, Volumen Maestro con la app abierta, protector intacto.
- Imágenes de prueba en Drive "Galerías-prueba/Sana": `grilla-4k.png` (la sumó el curador) y `prueba-1080-vs-4k.jpg` (la puso Opus, fuera del JSON; avisarle al curador, su sesión no respondía).

## Estado
- TV: versión de la sesión 3 instalada, con los ajustes de fábrica (`reset`). Protector = `com.google.android.apps.tv.dreamx/.service.Backdrop` (sin tocar).
- Probar por adb: `am start -n local.museumslideshow/.MainActivity --es tema sala-oscura --es tam maximo --es galeria "'Prueba sana'" --ei escena 2` (`--es reset si` = defaults; claves en `ajustes/PreferenciasDesdeTexto.kt`). Registro: escenas con `interiores=` y `Transición: N cuadros…`.

## En curso
- Nada.

## Próximo paso CONCRETO
1. **Sesión 3 HECHA** (ajustes persistentes, Barra, Ajustes, Estado; bug de calidad arreglado recreando las superficies). Falta probar en la TV lo listado en TODO.
2. **Sesión 4**: protector (`DreamService`) e interruptor "Usar como protector / Volver al anterior"; dar el permiso con `pm grant` y verificar 4K y fundido dentro del protector.

## Bloqueos
- Ninguno. Marcos reales: siguen faltando fotos (hoy negro fino provisorio; redondas sin marco).
- Biblioteca real del curador: espera las imágenes de la otra PC de Guido.

## Contexto que no está en otro doc
- **Forma de trabajo de Guido**: Opus coordina, escribe specs y revisa; **toda construcción con subagentes `model: sonnet`**. Subir a GitHub cada parte terminada que compila, no solo en el cierre.
- La clave nunca se mira ni va al chat (se pasó del portapapeles a `local.properties` mostrando `***`).
- Despertar la tele con `input keyevent 224` hace que Google TV abra su inicio encima de la app: volver a abrirla.
- En scripts por adb desde Git Bash, `/sdcard/...` se convierte en ruta de Windows: usar PowerShell para `pull`.
- Compilar: `$env:JAVA_HOME='C:\Program Files\Eclipse Adoptium\jdk-17.0.20.101-hotspot'; .\gradlew.bat assembleRelease testDebugUnitTest`. Si R8 dice "classes.dex en uso": `gradlew --stop` y borrar `app/build/intermediates/dex/release/minifyReleaseWithR8`.
- Las capturas del inicio de la TV muestran la foto de perfil de Guido: borrarlas al terminar.
