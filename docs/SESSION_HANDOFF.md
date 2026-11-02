# SESSION_HANDOFF — Museum Slideshow

> Save game del proyecto. `/cierre` lo SOBREESCRIBE ENTERO en cada sesión — acá nunca se apila historia (eso vive en CHANGELOG). El hook SessionStart lo inyecta en cada chat nuevo: **tope de 5.500 bytes** (unas 70 líneas); si se pasa, el hook lo corta y avisa "CORTADO".

**Cierre**: 2026-11-02 · **Último commit**: ver `git log -1` (cierre del diseño visual)

## Qué se hizo
- **Diseño visual cerrado y aprobado por Guido**, mirando los borradores en la propia tele: `docs/diseno/` (01 mapa y estilos, 02 temas y modos, 03 versión final) y `docs/diseno/decisiones.md`, que es la lista de lo aprobado.
- **4K real probado en la TV**: las apps dibujan a 1080 (`max_graphics` 1920×1080), pero una superficie de 3840×2160 sale a 4K (lo hace el visor del chip `com.droidlogic.imageplayer`, composición `DEVICE`). Guido vio la grilla de 1 px y eligió a ciegas la mitad 4K de un Bruegel.
- **SPEC-0 enmendado** (sin re-sellar): 4K, 4 temas (Clásico salió), modos Claro/Oscuro/Automático, colores de pared, marcos reales, tamaños Normal/Grande/Máximo/Pantalla completa, acomodador inteligente, botones (OK = pantalla completa, mantener OK = explicación, también en el protector).
- `docs/formato-galeria.md`: campos nuevos opcionales `vida_autor`, `movimiento`, `marco`; explicación ideal de 80–150 palabras.
- La tele quedó limpia: se borraron las imágenes de prueba y el protector sigue siendo el de Google.

## Estado
- Repo: docs, diseño y sistema; **todavía no hay código ni proyecto Gradle**.
- TV: chip Amlogic T963D4, panel 3840×2160 a 60 Hz, decodificadores de video hasta 4K, codificador hasta 1080. Protector = `com.google.android.apps.tv.dreamx/.service.Backdrop`, sin cambios.
- **Esta PC (la de `C:\Users\guido`) NO tiene JDK 17, ni Android SDK, ni la copia de `../volumen-maestro`.** adb solo existe en `%APPDATA%\SideQuest\platform-tools\adb.exe` (ya autorizado en la tele).

## En curso
- Nada.

## Próximo paso CONCRETO
1. **Re-sellar el SPEC-0** (sesión en Opus, red team breve): integrar `docs/diseno/decisiones.md`, revisar coherencia de las enmiendas del 02/11 y renovar el sello.
2. **Preparar esta PC** para compilar: JDK 17 (Temurin), Android SDK con platform 34 y platform-tools, y clonar Volumen Maestro al lado.
3. **Sesión 0** del SPEC-0, construida con subagentes Sonnet (Opus coordina y revisa).

## Bloqueos
- Falta la clave de Drive y la carpeta "Galerías-prueba" con sus 2 galerías (las arma Guido; ver `docs/TODO.md`).
- Faltan las herramientas de compilación en esta PC (paso 2).

## Contexto que no está en otro doc
- **Forma de trabajo pedida por Guido**: los mockups y la construcción van con **subagentes Sonnet**; Opus escribe specs, coordina y revisa. Red team corto, para no gastar tokens.
- Para que un subagente no se trabe: escribir archivos grandes **por partes** (uno se colgó 10 min generando un HTML entero).
- Para mostrarle cosas en la tele a 4K: sacar capturas 4K (Edge headless; el borrador 02/03 tiene "modo TV" con `?tv=1&...`), pasarlas con `adb push` y abrirlas con `am start -S -n com.droidlogic.imageplayer/.FullImageActivity -a android.intent.action.VIEW -d file:///sdcard/... -t image/jpeg`. Puede pedir permiso de almacenamiento en pantalla. Borrarlas al terminar.
- Guido quiere temas y opciones para todo, y todo modular: cambiar un tema, marco o color no debe obligar a rearmar el resto.
- Las capturas del inicio de la TV muestran la foto de perfil de Guido: borrarlas al terminar.
