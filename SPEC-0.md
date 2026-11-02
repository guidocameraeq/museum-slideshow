# SPEC-0: Museum Slideshow
Galería de arte para la tele de Guido: una app navegable que también es el protector de pantalla, con las obras enmarcadas como cuadros y su ficha para aprender.
- Estado: READY
- Fecha: 2026-11-01
- Sello del Arquitecto: `arquitecto: 2026-11-01 · kit 2.4.3 · chequeos: tamaño ✅ · entorno — (sin ficha del kit) · tope ✅ · hash b277ed91c561`

## Por qué (el dolor)
Guido arma colecciones de obras de arte (un pintor, una época) y las usa de protector en su TCL Google TV, que está siempre prendida. El Ambient de Google las muestra mal: recorta, achica, pone tres verticales juntas con bordes feos y nunca dice qué obra es, de quién ni de qué año. Cuando esto funcione, la tele es una galería de museo de la que se aprende: cada obra colgada como un cuadro de verdad, con su cartel y su explicación, y navegable con el control.

## Entidades del dominio
- **Galería** — una subcarpeta de la carpeta "Galerías" de Drive. Tiene título, descripción opcional y muchas Obras en un orden.
- **Obra** — una imagen de una Galería; puede tener Ficha. Su forma (vertical, horizontal, cuadrada, redonda) decide cómo se cuelga.
- **Ficha** — los datos de una Obra (título, autor, año, técnica, medidas, dónde está, explicación). Vienen del `galeria.json` de la Galería; todos opcionales.
- **Tema** — una manera de dibujar la Obra en pantalla (Museo, Inmersivo, Libro de arte). Se elige en Ajustes.
- **Pared** — 2 o 3 Obras verticales colgadas juntas, cada una con su marco y su cartel (solo en los temas que la admiten).
- **Ajustes** — todas las opciones de la app, cada una con su valor por defecto.

## Nivel y stack, y por qué
Nivel: `estandar`.
Kotlin + Jetpack Compose for TV + Gradle, igual que Volumen Maestro (repo hermano `../volumen-maestro`): es lo que ya anda fluido en esta tele (Amlogic, 2 GB de RAM). Sin librerías de terceros: solo AndroidX/Jetpack y kotlinx; la red con `HttpURLConnection`, las imágenes con `BitmapFactory`, los ajustes con DataStore.

### Las piezas (cada una con una sola responsabilidad, sin conocer las tripas de las otras)
1. **Sincronizador** (`sync/`): lista la carpeta pública de Drive (Drive API v3 con una clave de API, sin login), baja solo lo nuevo o cambiado (por `md5Checksum`/`modifiedTime`) y borra de la tele lo que se borró de Drive. Corre al abrir la app, a mano desde la Barra y, con la app cerrada, con WorkManager (Jetpack) según el ajuste "Sincronizar cada". Sin internet, se usa lo guardado.
   - **Imágenes seguras**: solo archivos `image/*` de hasta 50 MB; se leen primero solo las medidas (`inJustDecodeBounds`) y se decodifican ya achicadas (`inSampleSize`) para entrar en 1920×1080 (la tele dibuja a 1920×1080 sobre panel 4K); nunca se abre una imagen entera en memoria. Cada archivo se guarda en el almacenamiento interno con su **ID de Drive** como nombre (nunca con el nombre que trae, para que un `../` no escriba fuera de la carpeta). `galeria.json` con tope de 1 MB. Lo salteado va a Estado con su motivo.
   - **Espacio máximo**: al llegar al tope, deja de bajar y Estado lo avisa (no borra obras para hacer lugar).
   - **La clave no se filtra**: ninguna URL que lleve `key=` se escribe en el registro, en Estado ni en un mensaje de error; se registran solo el ID de la carpeta o archivo y el código HTTP.
2. **Lector de galerías** (`catalogo/`): lee cada `galeria.json` (formato en `docs/formato-galeria.md`) y arma Galerías, Obras y Fichas. Un error en el JSON no tumba nada: la Obra va sin Ficha y el problema queda anotado para la pantalla de Estado. Los campos desconocidos se ignoran (así el formato puede crecer).
3. **Acomodador** (`acomodo/`): mira la proporción de cada Obra (y el campo `forma` si viene) y decide la disposición: obra sola o Pared de 2-3 verticales. Solo decide lugares; no dibuja.
4. **Temas** (`temas/`): cada tema recibe "esta Obra (o Pared), con esta Ficha, en este lugar, en este momento de la transición" y la dibuja: fondo, marco, paspartú, cartel. Sin estado propio ni allocations por cuadro (mismo contrato que los temas de Volumen Maestro). Un tema nuevo = un archivo nuevo + una línea en el registro de temas. Cada tema declara si admite Pared.
5. **Galería en pantalla** (`galeria/`): pasa las Obras según orden y tiempo, maneja la transición y responde al control. **Es la misma pieza para la app y para el protector.**
6. **Ajustes** (`ajustes/`): los de la tabla de abajo, guardados con DataStore. Toda decisión nueva que pueda ser opción se suma como ajuste con su valor por defecto (regla de Guido).
7. **Interruptor del protector** (`protector/`): el `DreamService` que muestra la Galería en pantalla, y el botón "Usar como protector / Volver al anterior", que escribe `screensaver_components` (requiere `WRITE_SECURE_SETTINGS`, otorgado una vez por adb). **Una sola función** escribe ajustes del sistema, y la clave `screensaver_components` está fija en ella. Al activar, guarda el valor actual como "el anterior" **solo si no es el nuestro**; al volver, si no hay nada guardado, pone el Ambient de Google (`com.google.android.apps.tv.dreamx/.service.Backdrop`). La app **no trae cliente ADB** (a diferencia de Volumen Maestro): no ejecuta comandos de sistema.

### Pantallas de la app
- **Galería** (la principal y la del protector): la Obra a pantalla completa según el Tema.
- **Barra** (se abre con Abajo sobre la Galería): elegir Galería (o "Todas"), elegir Tema, ir a Ajustes, sincronizar ahora.
- **Ajustes**: la lista de opciones (abajo) y la pantalla de **Estado**: última sincronización, galerías y obras bajadas, espacio usado, problemas encontrados en los JSON, estado del permiso del protector (con el comando adb para darlo si falta).

### El control (en app y en protector, igual)
- Izquierda / Derecha: obra anterior / siguiente (reinicia el tiempo de la obra).
- OK: abre la explicación completa de la obra (vista tipo Libro de arte); OK o Atrás la cierra.
- Arriba / Abajo en una Pared: elegir cuál de las obras colgadas; Abajo fuera de una Pared: abre la Barra.
- Play/Pausa (si el control lo trae): pausa o sigue el pase.
- Atrás: cierra lo abierto; sin nada abierto, sale (en el protector, vuelve a la tele).
- **Las teclas de volumen nunca se consumen**: siguen yendo al sistema y a Volumen Maestro.

### Ajustes (todos opciones, con su valor por defecto)
| Ajuste | Valores | Por defecto |
|---|---|---|
| Tema | Museo / Inmersivo / Libro de arte (+ los que se agreguen) | Museo |
| Tiempo por obra | 10 s a 10 min | 60 s |
| Orden | El de la galería / Mezclar | El de la galería |
| Galería | Cualquiera de las bajadas / Todas | Todas |
| Pared de verticales | Apagada / Hasta 2 / Hasta 3 | Hasta 3 |
| Cartel de la obra | Siempre / Unos segundos al cambiar / Nunca | Depende del tema (Museo: siempre; Inmersivo: unos segundos) |
| Transición | Fundido / Ninguna | Fundido |
| Duración de la transición | 0,5 a 3 s | 1,5 s |
| Sincronizar cada | 1 h / 6 h / 24 h / Solo a mano | 6 h |
| Espacio máximo | 0,5 / 1 / 2 GB | 1 GB (la tele tiene 4,3 GB libres al 01/11) |
| Carpeta de Drive | ID o link de la carpeta "Galerías" | El de `local.properties` al compilar |
| Usar como protector | Sí / No (botón) | No |

## Concerns activados
- **Multi-tenant ⚠️: NO** — decidido por Guido: es solo suya. Ver Riesgos.
- **i18n ⚠️: SÍ, preparada** — menús en español, pero todo texto de la interfaz va en `res/values/strings.xml` desde el día 1 (cero textos de la interfaz escritos en el código); sumar inglés es agregar `values-en/`. Los textos de las obras vienen del JSON en el idioma que use el agente.
- **Errores estándar: activado** — la sincronización reintenta con espera creciente; nunca falla en silencio: los problemas (red, JSON inválido, imagen que no baja o no se decodifica) van a la pantalla de Estado y al registro (`logcat` con etiqueta `MuseumSlideshow`); la galería sigue mostrando lo que tiene.
- **Logging: activado (sin Sentry)** — registro con etiqueta `MuseumSlideshow` + pantalla de Estado. Sin Sentry: app personal sin servidor; el diagnóstico es por adb.
- **Settings: activado** — DataStore Preferences; cada ajuste con default (tabla de arriba).
- **Listas configurables: activado (adaptado)** — los temas viven en un registro (una línea por tema); las galerías las define la carpeta de Drive, no el código.
- **Roles / permisos: no aplica** — un solo usuario, sin login.
- **Auditoría: no aplica** — no toca plata ni datos de terceros.
- **Dashboards: no aplica.** **Notificaciones: no aplica.**
- **Feature flags: no aplica** — lo que se prende o apaga es un ajuste.

## Alcance v1 (qué entra)
- Leer la carpeta pública "Galerías" de Drive (subcarpeta = galería, `galeria.json` + imágenes), bajar y guardar para usar sin internet.
- Mostrar las obras sin recortar ni deformar, con marco según el tema, y la Pared de verticales como opción.
- Los 3 temas: Museo, Inmersivo, Libro de arte.
- Ficha y explicación de cada obra; obras sin ficha se muestran igual.
- Navegación con el control (tabla de arriba), Barra, Ajustes y Estado.
- La misma galería como app (ícono en el inicio, con banner de templo griego) y como protector de pantalla (`DreamService` interactivo).
- Botón para ponerse como protector y para volver exactamente al anterior.
- `docs/formato-galeria.md`: el formato del JSON con un ejemplo completo, escrito para que lo use el agente de arte de Guido sin ver el código.

## FUERA de alcance (qué NO entra)
- Subir, editar o borrar obras o JSON desde la app (eso lo hacen Guido y su agente en Drive).
- Google Fotos (cerrado a apps de afuera desde 2025) y otras fuentes: pendrive, carpeta de la PC, Immich, WebDAV.
- Generar o traducir textos con IA dentro de la app.
- Música o sonido.
- Login, cuentas o varios usuarios.
- Otras teles o Play Store: solo se prueba en la TCL de Guido y se instala por adb.
- Integración con Volumen Maestro más allá de convivir (que el volumen siga andando).
- Más temas que los 3, zoom o paneo sobre la obra (efecto Ken Burns), búsqueda de obras.
- Tocar el Ambient de Google o sus fondos: el interruptor solo cambia cuál protector arranca.

## Criterios de aceptación (verificables en la TV)
- CUANDO la carpeta de Drive de prueba tiene una galería con `galeria.json` válido, la app DEBE bajarla y mostrar cada obra con título, autor y año (captura con `screencap`).
- SI no hay internet al abrir la app, ENTONCES DEBE mostrar las obras ya bajadas y la pantalla de Estado DEBE indicar la hora de la última sincronización buena.
- SI no hay obras bajadas y no hay internet, ENTONCES la app DEBE mostrar un mensaje que lo explique, nunca una pantalla negra.
- SI un `galeria.json` tiene un error o nombra una imagen que no existe, ENTONCES la app DEBE mostrar igual las obras de esa galería (las afectadas sin ficha) y la pantalla de Estado DEBE nombrar el archivo y el problema.
- SI la galería trae una imagen de 15.000×15.000 px, ENTONCES la app NO DEBE cerrarse y la obra DEBE verse achicada (o figurar en Estado como salteada).
- CUANDO se borra una obra de la carpeta de Drive, DEBE desaparecer de la tele en la siguiente sincronización.
- SI se llega al espacio máximo, ENTONCES la app DEBE dejar de bajar y Estado DEBE decirlo.
- Ninguna obra se recorta ni se deforma: en la captura, la proporción de la obra dibujada coincide con la del archivo (±1%), en los 3 temas y en la Pared.
- CUANDO "Usar como protector" está en Sí y la tele queda quieta el tiempo configurado en Android, DEBE arrancar sola la galería (`dumpsys dreams` muestra el componente de Museum Slideshow) y Izquierda/Derecha DEBEN pasar obras sin cerrarla.
- CUANDO se toca "Volver al anterior", `settings get secure screensaver_components` DEBE devolver exactamente el valor que había antes de activarlo (hoy `com.google.android.apps.tv.dreamx/.service.Backdrop`), también si antes se tocó "Usar como protector" dos veces seguidas; SI falta el permiso, la app DEBE decirlo y mostrar el comando adb en lugar de fallar.
- CUANDO se cambia el Tiempo por obra, la obra siguiente DEBE durar el tiempo nuevo (medido en el registro), sin reinstalar.
- CUANDO se cambia el Tema, la Galería o la Pared, la pantalla DEBE cambiar en ese momento (captura antes y después), y el cambio DEBE seguir después de reiniciar la tele.
- Con la galería en pantalla (app o protector), los botones de volumen DEBEN seguir manejando el equipo de audio: el cartel de Volumen Maestro aparece (captura) y el volumen cambia (`service call media.audio_flinger 9`).
- Después de 2 horas pasando obras cada 10 s, la memoria del proceso (`dumpsys meminfo`, PSS total) DEBE crecer menos de 15 MB entre la hora 1 y la hora 2.
- Ningún renglón del registro (`logcat -d -s MuseumSlideshow`) DEBE contener `key=`.

## Ejecución
Tamaño: módulo solo
- Sesiones: varias seguidas, en este orden: (0) armar el proyecto Gradle copiando de `../volumen-maestro` **solo** el andamiaje (wrapper, versiones, `build.gradle.kts` sin su código: ni `AdbClient.java` ni `engine/Shell.kt`), crear una firma propia nueva y verificar la lectura de Drive con la clave; (1) sincronizador + lector; (2) acomodador + temas + galería en pantalla; (3) ajustes, barra y estado; (4) protector e interruptor; (5) prueba de las 2 horas y los criterios en la TV.
- Para abrir: `inicio — ejecutá el SPEC-0 (SPEC-0.md, está READY)` en un chat nuevo en `C:\Users\Usuario\Desktop\Proyectos\museum-slideshow`.
- Cómo se prueba: ver "Entorno de prueba".

## Entorno de prueba
- Cómo se levanta: `.\gradlew.bat assembleRelease testDebugUnitTest` (JDK 17 en `JAVA_HOME`) y `/instalar` (compila, instala en la TV por adb y abre la app).
- Datos semilla: carpeta de Drive "Galerías-prueba" pública de solo lectura, con 2 galerías: una con 6 obras (horizontales, verticales y una redonda) y su `galeria.json` válido, y otra con un JSON roto a propósito y una imagen faltante. La arma Guido con su agente siguiendo `docs/formato-galeria.md` (**necesita a Guido de día**). Cómo se resetea: **primero** "Volver al anterior" en la app (o `settings put secure screensaver_components com.google.android.apps.tv.dreamx/.service.Backdrop`), y recién ahí borrar los datos de la app (`pm clear local.museumslideshow`: es nuestra app, no un dato de Guido), volver a dar el permiso y sincronizar de nuevo.
- Chequeo base (verde antes de tocar código): `.\gradlew.bat assembleRelease testDebugUnitTest` en verde y `adb -s 192.168.0.219:5555 shell pidof local.volumenmaestro:motor` devuelve un número (la TV está conectada y Volumen Maestro anda).
- Compartido: la única TV. El chequeo base es de solo lectura; el protector de Guido se restaura siempre al terminar cada prueba.
- Herramientas, cada una con su chequeo:
  - adb: `adb connect 192.168.0.219:5555` → `adb devices` dice `device`.
  - JDK 17: `java -version` dice 17.
  - Android SDK 34: existe `%LOCALAPPDATA%\Android\Sdk\platforms\android-34`.
  - Clave de API de Drive: `local.properties` tiene `drive.apiKey`; chequeo: `curl "https://www.googleapis.com/drive/v3/files?q='<id>'+in+parents&key=<clave>"` lista los archivos de la carpeta de prueba. Crear el proyecto de Google Cloud y la clave **necesita a Guido de día**.
  - Permiso del protector: `adb shell pm grant local.museumslideshow android.permission.WRITE_SECURE_SETTINGS` (una vez por instalación limpia).

## Supuestos
- [ALTO] [SIN CONTRASTAR] Drive API v3 lista y baja archivos de una carpeta compartida "cualquiera con el link" usando solo una clave de API, sin login. (Si está mal, hace falta iniciar sesión con la cuenta de Google en la tele: cambia la pieza 1.) Se verifica en la sesión 0 con `curl`.
- [ALTO] [SIN CONTRASTAR] Un `DreamService` con Compose e interactivo funciona en esta TCL y la tele lo arranca sola al quedar quieta. (El de colores de Android arrancó a mano el 01/11; Jellyfin trae uno; Aerial Views avisa que en TCL puede hacer falta el permiso "Inicio automático".) Si no arranca solo, se busca ese permiso en los ajustes de TCL.
- [BAJO] El orden de la galería es el orden de las obras en el JSON; las imágenes que no figuran en el JSON van al final, por nombre de archivo.
- [BAJO] Una obra es "vertical" para la Pared si su ancho/alto es menor a 0,8; la redonda se toma del campo `forma` del JSON (detectarla por los píxeles no es confiable).
- [BAJO] La Pared se arma con verticales consecutivas en el orden actual; si no hay otra vertical a mano, la obra va sola.
- [BAJO] La carpeta de Drive se cambia desde Ajustes o con un `am start` por adb (tipear un link con el control es incómodo); el valor de arranque sale de `local.properties`.
- [BAJO] La clave de Drive queda en `local.properties` (no versionado) y viaja adentro del APK; se limita a la Drive API, en un proyecto de Google Cloud sin facturación, así que si alguien la saca del APK solo puede leer carpetas públicas. Si se pierde la PC, se crea otra en 5 minutos (paso en `docs/desarrollo.md`).
- [BAJO] [SIN CONTRASTAR] La carpeta "Galerías" se crea nueva, así que no necesita la `resourceKey` de los links viejos de Drive (anteriores a 2021); el `curl` de la sesión 0 lo confirma.
- [BAJO] Con la app cerrada, WorkManager puede sincronizar sin el permiso "Inicio automático" de TCL; si no, se sincroniza al abrir la app o al arrancar el protector.

## Riesgos y decisiones ⚠️
- ⚠️ Multi-tenant: NO (Guido, 01/11). Consecuencia: si un día otra persona la usa, pone su propia carpeta en Ajustes; separar usuarios dentro de una misma app sería rehacer la pieza de datos.
- ⚠️ i18n: preparada, solo español al arrancar (Guido, 01/11). Consecuencia: todo texto de la interfaz en `strings.xml` desde el día 1; un texto escrito en el código es un bug.
- ⚠️ Identidad de la app: `applicationId` `local.museumslideshow`, firma propia `keystore.jks` versionada en el repo (privado) y el nombre de la clase del `DreamService`. Consecuencia: cambiar cualquiera de los tres obliga a desinstalar, reinstalar, volver a dar el permiso y volver a elegir el protector.
- ⚠️ `WRITE_SECURE_SETTINGS`: permite cambiar ajustes protegidos de Android. Consecuencia: la app solo puede escribir `screensaver_components` (regla dura en el CLAUDE.md); guarda el valor previo antes de cambiarlo y "Volver al anterior" lo restaura exacto.
- ⚠️ Carpeta pública "cualquiera con el link" (Guido, 01/11: le da igual que la vean mientras no la editen). Consecuencia: quien tenga el link ve las obras; nunca se le da permiso de edición al link.
- ⚠️ Una actualización de Google TV podría devolver el protector al Ambient (le pasó a Chromecast en 2022). Consecuencia: el botón "Usar como protector" lo vuelve a poner; la pantalla de Estado muestra cuál protector está activo.

### Alternativas evaluadas y descartadas
- App "página web" adentro de la tele (temas como HTML) — descartada: en 2 GB de RAM, imágenes grandes con transiciones suelen ir trabadas, y el control queda torpe; justo lo que más importa.
- Partir de Aerial Views (protector de código abierto) — descartada: no está pensado para fichas, marcos ni temas, no lee Drive, y cada actualización suya obliga a reacomodar lo nuestro.
- Google Fotos como fuente — descartada: Google cerró la lectura de álbumes a apps de afuera en 2025.
- Copiar las obras a la tele desde la PC — descartada: cada galería nueva obliga a pasar por la PC y la tele tiene poco espacio.
- Carpeta compartida de la PC — descartada: solo anda con la PC prendida.
- Planilla de Google o una ficha por obra en vez de JSON — descartadas: los datos los genera una IA, y el JSON es el formato que la app puede validar con precisión.
- Login con la cuenta de Google en vez de link público — descartada por ahora: la carpeta pública de solo lectura alcanza y evita el login en la tele (queda como plan B si falla el supuesto de la clave).
