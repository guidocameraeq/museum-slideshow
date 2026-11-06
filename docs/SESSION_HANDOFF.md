# SESSION_HANDOFF — Museum Slideshow

> Save game del proyecto. `/cierre` lo SOBREESCRIBE ENTERO en cada sesión — acá nunca se apila historia (eso vive en CHANGELOG). El hook SessionStart lo inyecta en cada chat nuevo: **tope de 5.500 bytes** (unas 70 líneas); si se pasa, el hook lo corta y avisa "CORTADO".

**Cierre**: 2026-11-05 (noche) · **Último commit**: ver `git log -1`

## Qué se hizo (misión del 05/11, completa; detalle en CHANGELOG)
- **Fichas**: el md5 ya evitaba bajar fichas iguales; el problema real era que el listado compartía la pausa de los originales y la sync entera se salteaba. Camino nuevo `CaminoDrive.LISTADO` con pausa propia; una ficha cambiada que recibe el bloqueo espera con la vieja (`fichasEsperando`) y se baja en el reintento.
- **Reserva**: ya no borra al salir de la ventana; saca lo más viejo de afuera solo si falta espacio y después de tener la obra nueva. La copia 4K baja sin el respiro del ritmo (solo los originales lo tienen).
- **Barra lateral** (borrador `docs/diseno/07-barra-lateral.html`, aprobado, decisión en `decisiones.md`): `pantallas/PanelLateral.kt` + lógica pura `LogicaPanel.kt`; vista en vivo del tema; ajuste "Cerrar la Barra sola" (10 s); también en el protector. En una pared, Abajo pasada la última obra abre la Barra (antes no se podía).
- **Marcos reales**: 4 PNG en `res/drawable-nodpi/marco_*.png` (de las fotos del curador, con `herramientas/marcos/PrepararMarcos.java`), `res/raw/keep.xml` para que `shrinkResources` no los borre; vista previa de Apariencia con el marco real (grosor mínimo 16 dp).
- Todo construido por subagentes Sonnet, revisado, probado en la TV y subido en commits separados. 444 tests.

## Estado (verificado en la TV al cerrar)
- TV con la última versión. Protector = Museum Slideshow (el anterior guardado es el Ambient). Ajustes de Guido intactos: galería `1PH7nTu…` (Doré, La Divina Comedia), Galería moderna, marco madera, tamaño Máximo, cierre de la Barra 10 s.
- Volumen: el cartel de Volumen Maestro aparece con la galería en pantalla. Registro sin `key=`.
- La reserva ahora guarda más que N+10 obras mientras haya lugar (1 GB): es lo esperado.

## En curso
- Nada.

## Próximo paso CONCRETO
1. Que Guido use la Barra lateral y los marcos y diga qué ajustar (ver 🟠 del TODO: alineación de valores, tiempo de cierre, filete).
2. Verificar en la TV el caso "Google bloquea los originales" (🔴 del TODO).
3. Sesión 5: prueba de 2 horas y criterios que faltan del SPEC-0.

## Bloqueos
- Ninguno.

## Contexto que no está en otro doc
- **Forma de trabajo de Guido**: Opus coordina y revisa; construcción con subagentes `model: sonnet`; borrador HTML + OK antes de UI; subir a GitHub cada parte que anda.
- **Probar la Barra por adb**: se cierra sola a los 10 s; si la prueba es lenta (captura + mirarla) las teclas siguientes caen en la galería o el inicio. Poner `--es barra nunca` mientras se prueba y volver a `--es barra 10`. Mandar las teclas encadenadas en un solo `adb shell "...; sleep 1; ..."`.
- Probar por adb: `--es pantalla <inicio|galeria|galerias|apariencia|pase|conexion|protector|estado>`, `--es protector usar|volver`, `--es galeria <id|todas>`, `--es barra 5|10|20|nunca`, ajustes como extras (claves en `ajustes/PreferenciasDesdeTexto.kt`). Los extras SE GUARDAN: anotar el valor de Guido antes y restaurarlo.
- Con el protector corriendo, `am start` no llega a la app: despertar con `service call dreams 2` (abre el inicio de Google TV encima, con la foto de Guido: no capturar) y volver a abrir la app.
- `run-as` no anda (APK release): el estado se mira por `logcat -s MuseumSlideshow` (renglones `Reserva:`, `Sincronización:`, `Escena`, `Barra:`).
- Lint da 5 errores viejos `NewApi` (API 29 en `PintorSuperficie`, minSdk 28): inofensivos en la TV (API 31).
- Compilar: `$env:JAVA_HOME='C:\Program Files\Eclipse Adoptium\jdk-17.0.20.101-hotspot'; .\gradlew.bat assembleRelease testDebugUnitTest` desde la raíz.
- La clave nunca se mira ni va al chat. Cuenta Google del proyecto: guido.camera12.
