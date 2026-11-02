---
name: instalar
description: Compila Museum Slideshow, lo instala en la TV de Guido y verifica que abre. Usar cuando el usuario dice "instalá", "subilo a la tele", "probalo en la TV", o después de cambiar código de la app.
---

# /instalar — compilar, instalar en la TV y verificar

Variables (PowerShell):

```powershell
$env:JAVA_HOME = (Get-ChildItem 'C:\Program Files\Eclipse Adoptium' -Directory | Where-Object Name -like 'jdk-17*' | Select-Object -First 1).FullName
$adb = @("$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe",
         "$env:LOCALAPPDATA\Microsoft\WinGet\Packages\Google.PlatformTools_Microsoft.Winget.Source_8wekyb3d8bbwe\platform-tools\adb.exe",
         "$env:APPDATA\SideQuest\platform-tools\adb.exe") | Where-Object { Test-Path $_ } | Select-Object -First 1
$tv  = "192.168.0.219:5555"
```

## Pasos

1. **Compilar y probar**: `.\gradlew.bat assembleRelease testDebugUnitTest --console=plain`. Si falla, mostrar las líneas `e:` y frenar. Si Gradle dice "being used by another process", esperar unos segundos y reintentar una vez.
2. **Conectar**: `& $adb connect $tv` → `& $adb devices` tiene que decir `device`. `unauthorized` → pedirle a Guido que acepte el cartel en la TV. Sin respuesta → la TV está apagada o cambió de IP (ver `CLAUDE.md`).
3. **Anotar el protector actual**: `& $adb -s $tv shell settings get secure screensaver_components` (para comparar al final).
4. **Instalar como actualización**: `& $adb -s $tv install -r app\build\outputs\apk\release\app-release.apk`. Si dice `INSTALL_FAILED_UPDATE_INCOMPATIBLE`, la firma no coincide: **frenar y avisar** (no desinstalar sin OK).
5. **Permiso del protector** (solo la primera vez o tras desinstalar): `& $adb -s $tv shell pm grant local.museumslideshow android.permission.WRITE_SECURE_SETTINGS`.
6. **Verificar con evidencia** (a los ~5 s):
   - `& $adb -s $tv shell monkey -p local.museumslideshow -c android.intent.category.LEANBACK_LAUNCHER 1` abre la app y `pidof local.museumslideshow` devuelve un número.
   - `& $adb -s $tv logcat -d -s MuseumSlideshow` sin errores, y sin ningún `key=`.
   - Si el cambio es visual: `screencap -p /sdcard/x.png` + `pull` para mirarla; borrar la captura de la TV y de la PC después.
   - El protector quedó igual que en el paso 3 (salvo que la misión fuera cambiarlo, y entonces se restaura al terminar la prueba).
7. **Reportar**: qué se instaló, la evidencia de cada punto y lo que queda NO VERIFICADO (lo que depende de los ojos de Guido).

## Reglas

- Nunca `adb uninstall local.museumslideshow` ni `pm clear` sin antes "Volver al anterior": se perdería el protector guardado.
- Nunca tocar `local.volumenmaestro` desde acá.
- La TV la está usando Guido: no navegar menús con flechas sin mirar una captura antes de apretar OK.
