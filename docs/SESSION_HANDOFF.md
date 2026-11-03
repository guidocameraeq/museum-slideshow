# SESSION_HANDOFF — Museum Slideshow

> Save game del proyecto. `/cierre` lo SOBREESCRIBE ENTERO en cada sesión — acá nunca se apila historia (eso vive en CHANGELOG). El hook SessionStart lo inyecta en cada chat nuevo: **tope de 5.500 bytes** (unas 70 líneas); si se pasa, el hook lo corta y avisa "CORTADO".

**Cierre**: 2026-11-02 (noche, Guido dormido; cierre autónomo) · **Último commit**: ver `git log -1`

## Qué se hizo
- **Sesión 4 del SPEC-0 hecha y probada en la TV**: protector `local.museumslideshow.protector.ProtectorMuseo` (nombre FIJO, identidad), `MotorApp` (una reserva por proceso, compartida app/protector), interruptor (una sola función escribe `screensaver_components`; "el anterior" en DataStore `protector`).
- Pantalla Protector (borrador `docs/diseno/05-protector.html`, aprobado): Usar / Volver al anterior (nombre) / Probarlo ahora; sin permiso, botón apagado + comando.
- VERIFICADO en la TV: arranca sola al quedar quieta, 4K (3840×2160) dentro del protector, Derecha/OK/Atrás (Atrás cierra lo abierto antes de salir), volumen (Guido vio el cartel), Usar×2 + Volver → Ambient exacto. Permiso dado con `pm grant`.
- **Biblioteca real conectada**: carpeta "Galerías" (ID `1crKbvETrz6wbF_XmJ0tkGor__uQih2oO`, compartida por Guido) puesta en la TV y en `local.properties` (`drive.carpeta`). Rococó se vio en la tele. El curador exportó las 23 colecciones (1382 obras, ~7,4 GB, ORIGINALES; Eakins reducido); Guido decidió publicar todo, también lo con copyright, y curar borrando.
- **Bloqueo de Google**: al sincronizar 23 galerías, Drive respondió 403 "automated queries" a TODA la red de la casa (también la PC). Arreglo: pausa persistente 30 min→6 h que respetan sincronización, protector, Worker y reserva; reintento por WorkManager; Estado lo explica; descargas de a una con ajuste nuevo **Ritmo de bajada** (Tranquilo 2 s, default / Normal). Fix aparte: `readNBytes` no existe en Android 12 y cerraba la app.

## Estado
- TV: última versión instalada. **Protector = Museum Slideshow** (Guido pidió dejarlo puesto; el anterior guardado es el Ambient). Tema galería moderna, pared gris cálido, 10 min por obra (ajustes de Guido).
- Drive en pausa (bloqueo de Google) al cerrar; la reserva quedó VACÍA (la versión vieja borró Rococó para hacer lugar a obras que no pudo bajar). Se llena sola cuando Google levante el bloqueo. Estado: 11 de 23 galerías catalogadas.
- La TV quedó apagada (pedido de Guido).

## En curso
- Drive para escritorio de la PC sube los 7 GB a la nube en segundo plano (puede tardar horas).

## Próximo paso CONCRETO
1. Prender la tele, abrir la app → Estado: ¿ya no hay pausa? ¿están las 23 galerías? ¿la reserva bajó obras? Si sigue la pausa, esperar (se reintenta sola); si el bloqueo dura días, pensar copias a 3840 px y bajar menos (preguntarle a Guido).
2. Pedirle a Guido: originales vs copias a 3840 px (sigue sin responder; recomendado: copias). Si sí, el curador regenera con `--max-side=3840`.
3. Sesión 5: prueba de 2 horas y criterios que faltan del SPEC-0 (ver TODO).

## Bloqueos
- Bloqueo anti-abuso de Google sobre la red de la casa (temporal, duración desconocida).

## Contexto que no está en otro doc
- **Forma de trabajo de Guido**: Opus coordina y revisa; construcción con subagentes `model: sonnet`; borrador HTML + OK antes de UI; subir a GitHub cada parte que anda.
- Probar por adb: `--es pantalla <inicio|galeria|galerias|apariencia|pase|conexion|protector|estado>`, `--es protector usar|volver`, `--es ritmo normal`, ajustes como extras (claves en `ajustes/PreferenciasDesdeTexto.kt`). Mostrar un PNG a 4K: `am start -S -n com.droidlogic.imageplayer/.FullImageActivity -a android.intent.action.VIEW -d file:///sdcard/... -t image/png`.
- Con el protector corriendo, `am start` no llega a la app: despertar con `service call dreams 2` (abre el inicio de Google TV encima, con la foto de Guido: no capturar) y volver a abrir la app.
- Lint da 5 errores viejos `NewApi` (API 29 en `PintorSuperficie`, minSdk 28): inofensivos en la TV (API 31). Antes de usar APIs de Java nuevas, mirar que existan en Android 12.
- Compilar: `$env:JAVA_HOME='C:\Program Files\Eclipse Adoptium\jdk-17.0.20.101-hotspot'; .\gradlew.bat assembleRelease testDebugUnitTest` (374 tests).
- La clave nunca se mira ni va al chat. Cuenta Google del proyecto: guido.camera12.
