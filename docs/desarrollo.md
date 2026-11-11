# Desarrollo — entorno, clave de Drive y permiso del protector

## Entorno de la PC
Lo mismo que Volumen Maestro (ver la tabla de conexión en `CLAUDE.md`): JDK 17 (Temurin), Android SDK platform 34 y build-tools 34.0.0, adb de Platform-Tools. Compilar: `.\gradlew.bat assembleRelease testDebugUnitTest` con `JAVA_HOME` apuntando al JDK 17.

## `local.properties` (no se versiona)
```
sdk.dir=C\:\\Users\\<usuario>\\AppData\\Local\\Android\\Sdk
drive.apiKey=<la clave>
drive.carpeta=<ID de la carpeta "Galerías">
```
El ID de la carpeta es lo que va después de `/folders/` en el link de Drive.

## Crear la clave de Drive (5 minutos, la hace Guido)
1. Entrar a https://console.cloud.google.com con la cuenta de Google y crear un proyecto nuevo ("Museum Slideshow"). **No** activar facturación ni cargar tarjeta.
2. En "APIs y servicios" → "Biblioteca", buscar **Google Drive API** y activarla.
3. En "APIs y servicios" → "Credenciales" → "Crear credenciales" → "Clave de API".
4. Editar la clave: en "Restricciones de API" elegir **Restringir clave** y marcar solo **Google Drive API**. Guardar.
5. Pasar la clave a Claude **por archivo** (pegarla en `local.properties`), no por el chat.

Si se pierde la PC o la clave, se borra esa clave en la consola y se crea otra con los mismos pasos.

## La carpeta de Drive
- Carpeta "Galerías" con **Compartir → Cualquier usuario con el enlace → Lector**. Nunca "Editor".
- Una subcarpeta por galería; adentro, las imágenes y `galeria.json` (ver `docs/formato-galeria.md`).
- Chequeo desde la PC (reemplazar los dos valores; **no** pegar el resultado con la clave en ningún doc):
  `curl "https://www.googleapis.com/drive/v3/files?q='<ID>'+in+parents&fields=files(id,name,mimeType)&key=<clave>"`

## Permiso del protector
La app necesita `WRITE_SECURE_SETTINGS` para ponerse como protector. Se da una vez por instalación limpia:
`adb -s 192.168.0.219:5555 shell pm grant local.museumslideshow android.permission.WRITE_SECURE_SETTINGS`

## Protector a mano (para probar o para arreglar)
- Ver cuál está: `adb shell settings get secure screensaver_components`
- Volver al Ambient de Google: `adb shell settings put secure screensaver_components com.google.android.apps.tv.dreamx/.service.Backdrop`
- Arrancarlo ya: `adb shell am start -n com.android.systemui/.Somnambulator` · despertarlo: `adb shell service call dreams 2`
- En TCL quizá haga falta el permiso "Inicio automático" para que arranque solo (SIN CONTRASTAR; se ve en la sesión 4).

## Publicar una versión (APK en GitHub Releases)
1. Subir el número en `app/build.gradle.kts` (`val versionApp = "0.3"`). El código interno sale solo y siempre crece.
2. Anotar la entrada en `docs/CHANGELOG.md`, commit y push a `main`.
3. Etiqueta: `git tag v0.3` y `git push origin v0.3`. El workflow `publicar.yml` compila el APK **sin clave**, revisa que no traiga ninguna (`AIza`) y lo publica como `museum-slideshow-0.3.apk`. Si la etiqueta no coincide con `versionApp`, falla a propósito.
- Cada push a cualquier rama corre `compilar.yml` (compila y prueba). El APK queda como "artifact" del run, para probar sin publicar.
- El APK de GitHub no trae clave: la tele usa la que tiene cargada en Ajustes. Una tele que tenía un APK compilado con clave la copió a los ajustes al arrancar, así que sigue andando.
- Compilar en la PC sigue igual. Ojo: una versión de la PC con número menor que la instalada no se puede instalar encima (`INSTALL_FAILED_VERSION_DOWNGRADE`).
