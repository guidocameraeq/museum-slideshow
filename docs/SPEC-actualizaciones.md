# SPEC delta: APK en GitHub, actualizarse desde la app y clave cargada en Ajustes — Museum Slideshow
Cada versión queda publicada en GitHub como APK instalable; la app avisa y se actualiza sola desde Ajustes; la clave y la carpeta de Drive se cargan en la tele, no adentro del APK.
- Estado: BORRADOR — falta el OK de Guido
- Fecha: 2026-11-11

## Pedido original, textual
> "El repo no tiene una APK instalable. Y tampoco tiene un sistema de auto-update con GitHub. ¿Le podemos hacer eso? Y ya dejarlo subido si hago una instalación acá. Y el día que le haga un update desde la misma app, tiene una sección de ajustes, updatear y demás."
> (Sobre la clave) "me gusta la opción B […] o si no la opción A, y que se pueda conseguir las claves de una manera fácil […] Sí, dale, hacela. El A."

## Por qué (el dolor)
Instalar en otra tele obliga a armar una PC entera (JDK, SDK, compilar). Y para actualizar hay que repetirlo. El repo ahora es **público**: un APK con la clave adentro la dejaría a la vista (opción B descartada). Opción A: el APK sale sin clave y la clave se carga una vez por tele.

## Contexto del código
- `app/build.gradle.kts:25` `versionCode = 1`, `versionName = "0.1"`; `:28-29` mete `DRIVE_API_KEY` y `DRIVE_CARPETA` desde `local.properties`.
- `sync/Sincronizacion.kt:27` usa `BuildConfig.DRIVE_API_KEY`; `:18` `carpetaEfectiva` ya prefiere la carpeta de los ajustes sobre la de fábrica.
- `ajustes/ListaAjustes.kt:267` el ajuste Carpeta hoy es de solo lectura ("Se cambia desde la PC"); `ajustes/PreferenciasDesdeTexto.kt:127` ya acepta `--es carpeta <link o id>` por adb.
- `pantallas/TextosProblemas.kt:35` ya existe el problema `SIN_CLAVE` (falta clave / falta carpeta).
- Firma: `keystore.jks` versionado, misma firma para todas las versiones → las actualizaciones se instalan encima.

## AGREGA
- **Releases en GitHub** (`.github/workflows/release.yml`): al subir una etiqueta `v*` (ej. `v0.2`), GitHub compila el APK release firmado **sin `local.properties`** y lo publica en Releases como `museum-slideshow-<versión>.apk`, con las notas del CHANGELOG.
- **Ajuste "Clave de Drive"** (Conexión y espacio): se escribe en la tele. Se muestra tapada (`AIza…•••`), nunca entera. Por adb: `--es clave <la clave>` (y nunca va al registro: `Registro.limpiar` ya tapa `key=`; agregar que tape también la clave suelta).
- **Ajuste "Carpeta de Drive" editable**: se pega el link entero o el ID (usa `CarpetaDrive.extraerId`). Para escribir fácil: la app **Google TV del teléfono** tiene teclado y deja pegar lo copiado en el teléfono.
- **Primera vez sin clave o sin carpeta**: en vez de "falta la clave", la pantalla sin obras ofrece ir directo a cargarlas.
- **Pantalla "Actualizaciones"** (botón nuevo en Inicio o dentro de Estado): versión instalada, última en GitHub, botón **Buscar ahora** y, si hay una nueva, **Actualizar**. Baja el APK a la carpeta de la app y lo instala con `PackageInstaller` (Android muestra su cartel "¿Instalar?", que se acepta con el control). Si falta el permiso "instalar apps desconocidas", explica cómo darlo y abre esa pantalla de Ajustes de la tele.
- **Ajuste "Buscar actualizaciones"**: Una vez por día / Solo a mano (default: Una vez por día). Si hay una nueva, lo dice la Barra y la pantalla Inicio; **nunca se instala sola**.
- Permiso en el manifiesto: `REQUEST_INSTALL_PACKAGES`.
- Consulta a GitHub sin login: `https://api.github.com/repos/guidocameraeq/museum-slideshow/releases/latest` (pública, sin clave). El repo/dueño va como constante en un solo lugar.

## MODIFICA
- `Sincronizacion.crearApi`: la clave sale del ajuste si tiene valor; si no, la de fábrica (`BuildConfig`, vacía en el APK de GitHub). Igual que ya hace la carpeta.
- `versionCode` / `versionName`: se suben en cada versión (el `versionCode` sale del número de la etiqueta).
- `docs/tele-nueva.md`: los pasos de compilar pasan a "bajar el APK de Releases"; `docs/desarrollo.md`: cómo publicar una versión.

## NO SE TOCA
- `keystore.jks`, `applicationId` (`local.museumslideshow`) y la clase del `DreamService` (regla dura 4).
- La app sigue sin cliente ADB ni comandos de sistema (regla dura 3): instalar es con la API `PackageInstaller` de Android.
- El protector de Guido y las teclas de volumen (reglas duras 1 y 2).
- La galería en pantalla, los temas, la reserva y la sincronización (salvo de dónde sale la clave).
- Los ajustes guardados hoy: la tele de siempre sigue con su clave de fábrica sin cargar nada.

## Criterios de aceptación
- CUANDO se sube la etiqueta `v0.2`, Releases DEBE tener `museum-slideshow-0.2.apk`, y el APK NO DEBE contener la clave (buscar `AIza` en el APK da vacío).
- CUANDO se instala ese APK en una tele sin nada, la app DEBE pedir clave y carpeta; cargadas, DEBE sincronizar y mostrar las galerías.
- CUANDO hay una versión nueva en GitHub, Actualizaciones DEBE mostrarla y "Actualizar" DEBE terminar en el cartel de Android; aceptado, la app DEBE abrir con la versión nueva y **con todos los ajustes, la clave y el protector como estaban**.
- SI no hay internet o GitHub no responde, Actualizaciones DEBE decirlo sin trabarse, y la galería DEBE seguir igual.
- La clave NUNCA DEBE aparecer en `logcat -s MuseumSlideshow` ni en Estado.
- La tele de siempre, actualizada a la versión nueva por adb, DEBE seguir andando sin cargar nada (usa la clave de fábrica).

## Ejecución
Tamaño: módulo solo. Construyen subagentes Sonnet; Opus coordina y revisa.
1. **Clave y carpeta en Ajustes** (sin esto, el APK de GitHub no sirve).
2. **Workflow de Releases** + primera versión `v0.2`.
3. **Pantalla Actualizaciones** + aviso diario. Borrador HTML corto primero (pantalla nueva = OK de Guido antes de construir).
Cada parte: compilar en verde, instalar, probar en la tele, subir a GitHub.

## Entorno de prueba
- `.\gradlew.bat assembleRelease testDebugUnitTest` y `/instalar` desde la PC de Guido (una sesión en la nube no llega a la tele: ahí todo queda NO VERIFICADO).
- Para probar "Actualizar" hace falta publicar dos versiones seguidas (`v0.2` y `v0.3`).
