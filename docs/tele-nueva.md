# Instalar Museum Slideshow en una tele nueva

> Guía paso a paso para dejar la app andando en otra Google TV / Android TV, desde una PC con Windows.
> Hoy el APK se compila en la PC. Cuando esté hecho lo de `docs/SPEC-actualizaciones.md`, los pasos 2 y 3 se reemplazan por "bajar el APK de Releases".

## Lo que necesitás tener a mano
- **La clave de Drive.** Está en el `local.properties` de la PC vieja (renglón `drive.apiKey=`). Si no tenés esa PC: entrá a https://console.cloud.google.com con la cuenta del proyecto → proyecto "Museum Slideshow" → **APIs y servicios → Credenciales** → en la clave, **Mostrar clave** y copiala. Nunca la pegues en un chat ni en un doc.
- **El link de la carpeta "Galerías"** de Drive (la que está compartida como "Cualquier usuario con el enlace → Lector"). El ID es lo que viene después de `/folders/`.

## 1. Preparar la tele (una sola vez)
1. **Activar el modo desarrollador**: Ajustes → Sistema → Información → tocar 7 veces **Compilación del SO de Android TV** hasta que diga "Ya sos desarrollador".
2. **Activar la depuración**: Ajustes → Sistema → Opciones para desarrolladores → **Depuración por USB** (en las TCL también habilita la conexión por red).
3. **Anotar la IP de la tele**: Ajustes → Red e Internet → tocar la red Wi-Fi → **Dirección IP** (algo como `192.168.0.xxx`). La PC tiene que estar en la misma red.

## 2. Preparar la PC (una sola vez)
1. Instalar **JDK 17** (Temurin, de adoptium.net) y **Android SDK** con platform 34, build-tools 34.0.0 y platform-tools (lo más fácil: instalar Android Studio y dejar que baje todo). Detalle en `docs/desarrollo.md`.
2. Bajar el repo: `git clone https://github.com/guidocameraeq/museum-slideshow`
3. Crear el archivo **`local.properties`** en la raíz del repo (no se sube a GitHub, nunca):
   ```
   sdk.dir=C\:\\Users\\<tu usuario>\\AppData\\Local\\Android\\Sdk
   drive.apiKey=<la clave>
   drive.carpeta=<ID de la carpeta "Galerías">
   ```
   Sin este archivo la app compila igual pero sale **sin clave ni carpeta** y no encuentra ninguna galería.

## 3. Compilar
Desde la raíz del repo, en PowerShell:
```powershell
$env:JAVA_HOME = (Get-ChildItem 'C:\Program Files\Eclipse Adoptium' -Directory | Where-Object Name -like 'jdk-17*' | Select-Object -First 1).FullName
.\gradlew.bat assembleRelease
```
El APK queda en `app\build\outputs\apk\release\app-release.apk`. Ese archivo sirve para todas tus teles: si ya lo compilaste una vez con la clave, podés reusarlo sin compilar de nuevo.

## 4. Instalar en la tele
Reemplazá `<IP>` por la IP del paso 1:
```powershell
adb connect <IP>:5555
adb devices                      # tiene que decir "device"; si dice "unauthorized", aceptá el cartel en la tele
adb -s <IP>:5555 install -r app\build\outputs\apk\release\app-release.apk
adb -s <IP>:5555 shell pm grant local.museumslideshow android.permission.WRITE_SECURE_SETTINGS
```
El último comando es **el permiso del protector**: se da una vez por tele (y otra vez solo si se desinstala la app).

## 5. Dejarla andando
1. Abrir **Museum Slideshow** desde el inicio de Google TV.
2. Esperar la primera sincronización (Estado muestra cómo va) y elegir las galerías en **Galerías**.
3. **Protector** → **Usar Museum Slideshow como protector**. La app guarda el protector que tenía la tele, y **Volver al anterior** lo deja como estaba.
4. Probarlo con **Probarlo ahora**.

## Si algo no anda
- **No encuentra galerías / "falta la clave"**: el APK se compiló sin `local.properties`. Volver al paso 2.3 y compilar de nuevo.
- **`INSTALL_FAILED_UPDATE_INCOMPATIBLE`**: hay instalada una versión con otra firma. No desinstalar a ciegas: primero "Volver al anterior" en Protector.
- **No conecta por adb**: mirar que la PC y la tele estén en la misma red y que la IP no haya cambiado.
- **Ver qué está pasando**: `adb -s <IP>:5555 logcat -s MuseumSlideshow`
