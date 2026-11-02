# SESSION_HANDOFF — Museum Slideshow

> Save game del proyecto. `/cierre` lo SOBREESCRIBE ENTERO en cada sesión — acá nunca se apila historia (eso vive en CHANGELOG). El hook SessionStart lo inyecta en cada chat nuevo: **tope de 5.500 bytes** (unas 70 líneas); si se pasa, el hook lo corta y avisa "CORTADO".

**Cierre**: 2026-11-02 (20:40) · **Último commit**: ver `git log -1` (menú y reserva)

## Qué se hizo (esta tanda)
- **Sesiones 1, 2 y 3 del SPEC-0 hechas y probadas en la TV** (sincronizador, galería 4K con fundido por el compositor a ~58 cuadros/s, ajustes, Barra, Estado). Detalle en CHANGELOG.
- **SPEC delta `docs/SPEC-menu-y-reserva.md` → COMPLETO**: pedido de Guido ("la app abierta a mano es una app normal con opciones", "elegir carpetas de Drive", "online, de a varias obras, ni de a una ni todas").
  - **Inicio** (borrador 4, Inicio A aprobado) + ajustes agrupados (Apariencia con vista previa, Pase, Conexión y espacio, Protector, Estado). Atrás desde la galería vuelve a Inicio.
  - **Catálogo en árbol** (grupos y galerías, prof. 4), medidas desde Drive (`imageMediaMetadata`, VERIFICADO), **tapas** de 480 px.
  - **Galerías**: tarjetas con tapa y tilde, grupos con mosaico (OK entra, Tildar todas), vista Lista (ajuste), "Galerías nuevas".
  - **Reserva de obras**: próximas N (20/50/100/200, default 50) + 10 vistas; tanda al quedar < N/2; borra lo que sobra y lo destildado. Probado con `--es reserva 5`: nunca más de 15 copias.
- Diseño: `docs/diseno/04-menu-y-galerias.html` + `decisiones.md` (Inicio A; Galerías en tarjetas).
- Drive "Galerías-prueba" tiene además `Grupo prueba/{Uno,Dos}` (lo armó Opus) y `Sana/prueba-1080-vs-4k.jpg` (fuera del JSON; avisarle al curador).

## Estado
- TV: versión del menú instalada; tema Libro de arte (lo eligió Guido), reserva 50, vista Tarjetas. Protector = `com.google.android.apps.tv.dreamx/.service.Backdrop` (sin tocar).
- Probar por adb: `am start -n local.museumslideshow/.MainActivity --es pantalla <inicio|galeria|galerias|apariencia|pase|conexion|protector|estado>`; ajustes como extras (`--es reserva 5`, `--es tildar "<carpeta>"`, `--es reset si`; claves en `ajustes/PreferenciasDesdeTexto.kt`).

## En curso
- Nada.

## Próximo paso CONCRETO
1. **Sesión 4**: protector (`DreamService`) + interruptor "Usar como protector / Volver al anterior" (pantalla Protector ya existe, deshabilitada). Debe arrancar directo en la galería, con la reserva; dar el permiso con `pm grant`; verificar 4K y fundido dentro del protector.
2. Pendientes de prueba en la TV listados en TODO (sin internet, Solo a mano, reinicio real).

## Bloqueos
- Ninguno. Marcos reales: faltan fotos (hoy negro fino provisorio; redondas sin marco).
- Biblioteca real del curador: espera las imágenes de la otra PC de Guido. ⚠️ La reserva baja el ORIGINAL de cada obra: ~7 GB/día a una obra por minuto con originales de ~5 MB; si molesta, el curador sube copias 4K achicadas.

## Contexto que no está en otro doc
- **Forma de trabajo de Guido**: Opus coordina, escribe specs y revisa; **toda construcción con subagentes `model: sonnet`**. Antes de construir UI: **borrador HTML** y OK de Guido. Subir a GitHub cada parte que compila y anda.
- Capturas de borradores a 4K: servir `docs/diseno` con un servidor local (node) y Edge headless `--force-device-scale-factor=2 --window-size=1920,1080`; con `file://` las imágenes no cargan. En HTML generado, `url('…')` con comilla simple dentro de `style="…"`.
- La clave nunca se mira ni va al chat. Cuenta Google del proyecto: guido.camera12; Chrome de esta PC (ver memoria).
- Despertar la tele con `input keyevent 224` hace que Google TV abra su inicio encima: volver a abrir la app. En Git Bash, `/sdcard/...` se rompe: usar PowerShell para `pull`.
- Compilar: `$env:JAVA_HOME='C:\Program Files\Eclipse Adoptium\jdk-17.0.20.101-hotspot'; .\gradlew.bat assembleRelease testDebugUnitTest` (335 tests).
- Las capturas del inicio de la TV muestran la foto de perfil de Guido: borrarlas al terminar.
