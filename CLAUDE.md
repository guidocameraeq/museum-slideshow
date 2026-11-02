# CLAUDE.md — Reglas para Claude Code en Museum Slideshow

> Solo reglas de comportamiento y cómo llegar a la TV. Diseño, decisiones e historia → archivos dedicados (ver mapa de docs).
> Test por línea antes de agregar nada: **si borro esta línea, ¿Claude se equivoca? Si no, sobra.**

## Qué es esto

**Museum Slideshow**: app Android TV que es una galería de arte navegable y, a la vez, el protector de pantalla de la tele. Muestra las obras de Guido como cuadros (marco, cartel, explicación) con temas elegibles, y las lee de una carpeta pública de Google Drive. Stack: Kotlin + Jetpack Compose for TV, Gradle, sin librerías de terceros (solo AndroidX/Jetpack y kotlinx). La usa Guido en **su** TCL Google TV (la única donde se prueba). El diseño completo está en `SPEC-0.md`.

Repo hermano: `../volumen-maestro` (Volumen Maestro) es **la casa de la tele**: su historia, apps quitadas, respaldos y todo lo que se le hizo viven en `../volumen-maestro/docs/tv-guido-tcl/`. Acá solo va lo de esta app.

**Comandos clave** (desde la raíz, en PowerShell; existen desde la sesión 0 del SPEC-0):
- `.\gradlew.bat assembleRelease testDebugUnitTest` — compila el APK y corre las pruebas (necesita `JAVA_HOME` del JDK 17).
- `/instalar` — compila, instala en la TV y verifica que la app abre.
- `adb -s 192.168.0.219:5555 logcat -s MuseumSlideshow` — primer paso ante cualquier "no funciona".

## Conexión a la TV

| Qué | Valor |
|---|---|
| TV | TCL "Smart TV Pro" (G09), Google TV, Android TV 12, 2 GB RAM, chip Amlogic T963D4. Las apps dibujan a 1920×1080; una superficie de 3840×2160 sale a 4K real (probado 02/11) |
| IP / puerto ADB | `192.168.0.219:5555`, Wi-Fi "Yulius". Si no conecta, mirar que la PC esté en esa red (a veces queda en "Domotics3") |
| adb | El primero que exista: `%LOCALAPPDATA%\Android\Sdk\platform-tools\adb.exe` · `%LOCALAPPDATA%\Microsoft\WinGet\Packages\Google.PlatformTools_Microsoft.Winget.Source_8wekyb3d8bbwe\platform-tools\adb.exe` · `%APPDATA%\SideQuest\platform-tools\adb.exe` |
| JDK / SDK | Temurin 17 en `C:\Program Files\Eclipse Adoptium\jdk-17*` (no deja `JAVA_HOME` puesto: setearlo en cada comando) · SDK en `%LOCALAPPDATA%\Android\Sdk` (platform 34, build-tools 34.0.0). En el `sdkmanager.bat` el `;` de los paquetes se rompe: pasarlos con `--package_file=` |
| Firma | `keystore.jks` en la raíz, **versionado a propósito** (repo privado; Guido no guarda nada en la PC). Se crea en la sesión 0 |

Pasos: `adb connect 192.168.0.219:5555` → `adb devices` debe decir `device`. Si dice `unauthorized`, Guido acepta el cartel en la TV.

## El ciclo de trabajo

1. **Chat nuevo por misión.** El hook SessionStart inyecta el handoff; `/inicio` arranca el ritual. **No toco nada hasta el OK del usuario.**
2. Trabajar con las reglas de abajo operando solas.
3. `/cierre` al terminar → docs al día, commit, push → **el chat se descarta**. Compactar es emergencia (`cierre parcial`), no forma de vida.

Los rituales viven en `.claude/skills/`: **`/inicio` · `/cierre` · `/instalar`**. Ahí está el detalle; no lo duplico acá.

## 🚨 Restricciones duras

1. **El protector de Guido es suyo**: la app solo escribe `screensaver_components`, desde UNA función con esa clave fija. Antes de cambiarlo guarda el anterior (si no es el nuestro); toda prueba termina con el protector de Guido restaurado (`com.google.android.apps.tv.dreamx/.service.Backdrop` si no hay otro guardado). **Nunca** tocar el Ambient de Google, sus fondos, ni ningún otro ajuste del sistema.
2. **Nada de Volumen Maestro se toca desde acá**: ni su app, ni su motor, ni su configuración. Las teclas de volumen nunca se consumen en esta app.
3. **Esta app no trae cliente ADB** ni ejecuta comandos de sistema. No copiar `AdbClient.java` ni `engine/Shell.kt` de Volumen Maestro.
4. **Identidad fija**: nunca cambiar `keystore.jks`, el `applicationId` (`local.museumslideshow`) ni el nombre de la clase del `DreamService`: obliga a reinstalar, dar el permiso y elegir el protector de nuevo.
5. **Secretos**: la clave de Drive vive solo en `local.properties` (no versionado). Ninguna URL con `key=` va al registro, a Estado, a un doc ni al chat. Los `logcat` de la TV pueden traer tokens de otras apps: no se guardan en archivos persistentes.
6. **Nada irreversible en la TV sin OK** (desinstalar apps, borrar datos de apps que no sean esta). `pm clear local.museumslideshow` está permitido solo después de "Volver al anterior".
7. **Textos de la interfaz siempre en `res/values/strings.xml`** (i18n preparada): un texto de pantalla escrito en el código es un bug.
8. **Todo lo que pueda ser opción es un ajuste** con su valor por defecto (regla de Guido); nada fijo "porque sí".

## Reglas de evidencia (contra los "fixes fantasma")

1. **Un bug se declara resuelto SOLO tras reproducirlo en la TV** y verlo funcionar (`screencap`, registro, `dumpsys dreams`, `settings get secure screensaver_components`). Lo que depende de los ojos de Guido se marca **NO VERIFICADO** hasta que lo confirme.
2. Todo resultado se reporta con **evidencia real** o como **NO VERIFICADO**. Nunca "listo" a secas.
3. **En tareas aprobadas no freno a pedir permiso intermedio.** Trabajos >2 min → `run_in_background`.
4. **Post-compactación asumo que NO leí ningún archivo**: Read antes de cualquier Edit.
5. Ante errores: diagnosticar root cause, no aplicar parches encima.
6. **Toda spec se escribe en un formato del anexo** `~/.claude/skills/arquitecto/anexos/formato-spec.md`: **SPEC-0** si es nuevo, **SPEC delta** (con su NO SE TOCA) si toca algo que ya anda. Implementada: su línea de Estado pasa a `COMPLETO <fecha>` y el spec se queda donde está.
7. **Si la charla diseñó una feature no-trivial y todavía no hay spec escrito, OFREZCO cristalizarla en spec antes de construir.**

## Reglas operativas del stack

1. **Capturas**: `screencap` sale negro sobre apps con DRM y no muestra lo que hay debajo de otro protector. Las capturas del inicio muestran la foto de perfil de Guido: borrarlas al terminar.
2. **Navegar la TV con flechas desde la PC es frágil**: leer el foco con `uiautomator dump` o mirar una captura antes de apretar OK. `input keyevent` sí sirve para probar las flechas dentro de esta app; para el volumen, `cmd media_session volume --stream 3 --adj raise|lower`.
3. **Probar el protector sin esperar**: `am start -n com.android.systemui/.Somnambulator` lo arranca ya; `service call dreams 2` lo despierta.
4. **Temas sin estado**: cada tema dibuja todo desde el estado que recibe, sin allocations por cuadro (mismo contrato que `../volumen-maestro/docs/temas.md`).
5. **Imágenes**: nunca decodificar una imagen entera; primero las medidas (`inJustDecodeBounds`), después `inSampleSize`. La tele tiene 2 GB de RAM.
6. **Mensajes multi-tema del usuario** (dicta por voz): confirmar el orden de prioridad antes de ejecutar si no dijo "primero X".

## Mapa de documentación

| Archivo | Rol | Quién lo escribe / cuándo |
|---|---|---|
| `SPEC-0.md` | El diseño de la app (READY) | Arquitecto; pasa a `COMPLETO <fecha>` al terminar |
| `docs/SESSION_HANDOFF.md` | **Save game** — foto única de dónde quedamos | `/cierre` lo sobreescribe entero |
| `docs/TODO.md` | ÚNICA fuente de pendientes | `/cierre`: completadas afuera, nuevas adentro |
| `docs/CHANGELOG.md` | Historia de cambios | `/cierre`: entrada por sesión |
| `docs/diseno/` | Diseño visual aprobado: borradores HTML + `decisiones.md` (lo aprobado por Guido) | al cambiar el diseño |
| `docs/formato-galeria.md` | El formato de `galeria.json`, para el agente de arte de Guido | al cambiar el formato (subir `formato`) |
| `docs/desarrollo.md` | Entorno, clave de Drive, permiso del protector, compilar e instalar | al cambiar herramientas |

**Reglas del sistema**: una sola narrativa por sesión (HANDOFF + CHANGELOG). Un número vive en UN archivo. Lo derivable del código o de git NO se escribe en un doc. Docs nuevos (arquitectura, decisiones, temas) recién cuando duela no tenerlos.

## Comunicación

- Español rioplatense (vos), **en criollo: corto, sin jerga técnica** — Guido no es programador. Aplica a TODO.
- Decisiones: opciones con pros/contras + UNA recomendación justificada.
- Explicar QUÉ cambié, no narrar el proceso.
