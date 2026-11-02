# SESSION_HANDOFF — Museum Slideshow

> Save game del proyecto. `/cierre` lo SOBREESCRIBE ENTERO en cada sesión — acá nunca se apila historia (eso vive en CHANGELOG). El hook SessionStart lo inyecta en cada chat nuevo: **tope de 5.500 bytes** (unas 70 líneas); si se pasa, el hook lo corta y avisa "CORTADO".

**Cierre**: 2026-11-02 (16:20) · **Último commit**: ver `git log -1` (cierre de la sesión 1)

## Qué se hizo
- **Proyecto partido en dos** (Guido): esta app + el **agente curador** (sesión "Agente Galerias de arte", repo `../galerias-de-arte`), que arma galerías, JSON e imágenes y exporta con `tools/export-drive.mjs`. Se habla con él por SendMessage.
- **Formato v1 acordado y fijo** (`docs/formato-galeria.md`): + `licencia`, `fuente`, `id` opcionales; piso de imagen 1000 px; explicación corta aceptada; nombres `autor-titulo-anio.jpg`. Galerías grandes van **enteras** (Guido poda después).
- **Clave de Drive creada** en la cuenta **guido.camera12@gmail.com** (proyecto `museum-slideshow-513518`, clave "Museum Slideshow TV" limitada a Drive API). Ya está en `local.properties` con `drive.carpeta` = "Galerías-prueba". Un proyecto creado por error en `guido.camera.eq` quedó dado de baja.
- **Supuesto ALTO verificado**: Drive v3 lista y baja la carpeta pública solo con la clave, sin login (`curl` y la app en la TV).
- **Sesión 1 construida** (Sonnet, revisada por Opus): `catalogo/` (lector estricto con kotlinx-serialization) + `sync/` (Drive HTTP, copia 4K por franjas + copia chica de 64 px, borra lo que se borró en Drive, tope de espacio, Worker cada 6 h) + pantalla provisoria con resumen. 31/31 tests.
- Opus corrigió: tope de espacio 1 GB (el SPEC) y espacio usado medido una vez (antes recorría todo en cada bajada).
- **Evidencia en la TV**: sincronizó 12 archivos en 25 s; la segunda pasada no bajó nada; captura "3 galerías · 9 obras" + "2 problemas" (JSON roto y archivo faltante, los esperados); 0 `key=` en el registro; protector sin cambios.

## Estado
- TV: Museum Slideshow 0.1 con sesión 1. Protector = `com.google.android.apps.tv.dreamx/.service.Backdrop` (sin tocar).
- PC: `local.properties` completo (clave + carpeta de prueba). Chrome: la extensión "Claude in Chrome" tiene dos navegadores; **"Browser 1" es el de esta PC** (cuenta guido.camera12), "Browser 2" es de otra PC.

## En curso
- Nada.

## Próximo paso CONCRETO
1. **Sesión 2**: acomodador + los 4 temas + marcos + galería en pantalla con la superficie 4K (SPEC-0 piezas 3, 4 y 5). Antes: armar la obra "grilla" (rayas de 1 px a 3840×2160) y pedirle al curador que la sume a "Sana".
2. Revisar en la sesión 2 que la copia 4K sale nítida y sin costuras entre franjas (NO VERIFICADO).

## Bloqueos
- Ninguno para la sesión 2. Faltan las fotos de marcos reales (se arranca con "sin marco" + negro fino provisorio, SPEC-0 supuesto BAJO).
- La biblioteca real del curador espera las imágenes de la otra PC de Guido (pendrive).

## Contexto que no está en otro doc
- **Forma de trabajo de Guido**: Opus coordina, escribe specs y revisa; **toda construcción con subagentes `model: sonnet`**, con brief claro y archivos grandes por partes. Red team corto, sin revisiones dobles.
- La clave nunca se mira ni se pega en el chat: se pasó del portapapeles a `local.properties` con PowerShell, mostrando solo `drive.apiKey=***`. Chrome solo copia al portapapeles si su ventana está en esta PC.
- El Bloc de notas de Windows 11 "guarda" al cerrar sin escribir el archivo: verificar siempre el contenido real.
- Compilar: `$env:JAVA_HOME='C:\Program Files\Eclipse Adoptium\jdk-17.0.20.101-hotspot'; .\gradlew.bat assembleRelease testDebugUnitTest`.
- Si la tele está en el protector, `screencap` muestra el protector: despertarla con `service call dreams 2`.
- Capturas 4K para Guido: `adb push` + `am start -S -n com.droidlogic.imageplayer/.FullImageActivity -a android.intent.action.VIEW -d file:///sdcard/... -t image/jpeg`. Borrarlas al terminar.
