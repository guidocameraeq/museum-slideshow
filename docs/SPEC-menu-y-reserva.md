# SPEC: Menú de inicio, elegir galerías y reserva de obras — Museum Slideshow
La app abierta a mano es una app con menú; las galerías se eligen recorriendo las carpetas de Drive; la tele guarda solo una reserva de las próximas obras.
- Estado: READY
- Fecha: 2026-11-02
- Sello del Arquitecto: `arquitecto: 2026-11-02 · kit 2.4.3 · chequeos: tamaño ✅ · entorno — (sin ficha del kit) · tope ✅ · hash 30d31a437577`

## Pedido original, textual
> "al abrir la app manualmente, debería la app no debería abrir directamente en la galería de arte. Porque lo voy a usar más que nada cuando se ponga solo. Por el timeout. Cuando se abre, se debería abrir como una app normal con opciones. También después, por ejemplo, se me ocurre que quiero que tenga una cosa para navegar las carpetas del Drive y elegir cuáles sí y cuáles no. O sea, por eso tiene que ser, tiene que tener una UI usable la app. Bien armada."
> "Tiene que ser online y tiene que ir descargando de a varias obras las que le sigan. Ya no sé, las próximas 50 obras, una cosa así. O 20 obras, como para siempre tenerlas ya cacheadas. Pero no puede ni andar bajando obras una por uno porque va a andar muy mal. Ni tampoco puede lo otro, o sea, tenerlas todas descargadas."
> "antes de armar toda la UI de la app, me la vas a tener que mostrar, me vas a tener que hacer un mockup y mostrármelo en un HTML."

## Por qué (el dolor)
Hoy la app abre directo en las obras y los ajustes quedan escondidos detrás de Abajo → Barra → Ajustes. No hay forma de elegir qué galerías se usan. Y la app baja y guarda todo: la biblioteca real (1.382 obras, ~4 GB en copias 4K) no entra en la tele (4,3 GB libres, tope de 1 GB).

## Contexto del código
- `MainActivity.kt:73` arma la pantalla; `MainActivity.kt:193` muestra `GaleriaEnPantalla` apenas hay obras.
- `sync/Sincronizador.kt:39` `sincronizar(carpetaId)` lista la raíz, toma cada subcarpeta como galería (`:67`), ignora las anidadas (`:93`) y baja TODAS las imágenes (`sincronizarImagen`, `:186`).
- `catalogo/Catalogo.kt:57` `cargar(dir)` arma galerías desde lo bajado; `galeria/Pase.kt:24` arma el orden de escenas; `galeria/CargadorObras.kt` abre la copia guardada de cada obra.
- `ajustes/Preferencias.kt:69` `espacioMaximo`; la pantalla de Ajustes es una lista única (`pantallas/PantallaAjustes.kt`, `ajustes/ListaAjustes.kt`).

## AGREGA
- **Pantalla Inicio** (al abrir la app desde Google TV): botones grandes "Ver la galería", "Galerías", "Apariencia", "Pase", "Conexión y espacio", "Protector" y "Estado". Atrás desde la galería vuelve a Inicio; Atrás en Inicio sale de la app.
- **Ajustes agrupados**: Apariencia (tema, modo, horario, colores de pared, marco, tamaño, pantalla completa con marco, explicación, nombre de la sala, cartel), Pase (tiempo por obra, orden, transición y su duración, Pared, calidad), Conexión y espacio (carpeta de Drive, sincronizar cada, reserva, espacio máximo, sincronizar ahora). Mismos ajustes de hoy, misma forma de cambiarlos.
- **Pantalla Galerías**: recorre la carpeta pública de Drive como un árbol (una carpeta con imágenes = galería; una carpeta con subcarpetas = grupo, por ejemplo «Renacimiento/Rafael»). Cada galería y grupo tiene su tilde y muestra cuántas obras trae. Tildar un grupo tilda todo lo de adentro. Guardado como ajuste.
- **Ajuste "Galerías nuevas"**: Tildadas / Destildadas (default: Tildadas), para lo que el curador suba después.
- **Reserva de obras**: ajuste "Reserva" (20 / 50 / 100 / 200 obras; default 50). La tele guarda las próximas N obras del pase más las últimas 10 vistas (para volver con Izquierda). Cuando quedan menos de N/2 por delante, baja una tanda hasta completar N y borra las vistas que sobran.
- **Medidas antes de bajar**: el listado de Drive pide ancho y alto de cada imagen (`imageMediaMetadata`), así el pase se arma con todas las obras sin bajarlas.

## MODIFICA
- `MainActivity`: abre en Inicio, no en la galería — el protector (sesión 4) sigue arrancando directo en las obras.
- `Sincronizador`: sincroniza el **catálogo** (árbol de carpetas, `galeria.json`, nombres y medidas) de lo tildado; ya no baja todas las imágenes. Las imágenes las baja la reserva — cuidar que siga borrando lo que se borra en Drive y lo destildado.
- `Pase`: se arma con las obras del catálogo (bajadas o no); una obra todavía sin bajar se saltea en ese momento sin pantalla negra — el orden y el contador siguen siendo los de la galería.
- La Barra: "Galería" sigue eligiendo entre las tildadas (o Todas las tildadas).
- `Espacio máximo` queda como tope de seguridad de la reserva — si la reserva no entra, baja menos obras y Estado lo dice.
- Estado suma: obras en la reserva, cuántas por delante y el último error de bajada.

## NO SE TOCA
- La galería en pantalla: superficies 4K, fundido por el compositor, acomodador, temas, marcos y el control (OK, mantener OK, flechas, Pared, vista Explicación).
- El formato `galeria.json` v1 (`docs/formato-galeria.md`) y la carpeta pública con clave sin login.
- Los ajustes guardados hoy conservan su clave y su significado.
- Reglas duras de `CLAUDE.md`: el protector de Guido, las teclas de volumen, la clave fuera del registro.

## Criterios de aceptación
- Todo lo de NO SE TOCA sigue igual: los 4 temas, el fundido ≥24 cuadros/s y la capa 4K (`dumpsys SurfaceFlinger`) como en la sesión 2.
- CUANDO se abre la app desde el inicio de Google TV, DEBE mostrar Inicio (captura); "Ver la galería" arranca el pase y Atrás vuelve a Inicio.
- CUANDO se destilda una galería en Galerías, en la siguiente sincronización sus obras DEBEN dejar de pasar y sus copias DEBEN borrarse de la tele (Estado muestra menos espacio usado).
- CUANDO la Reserva es N y la galería tiene más de N obras, la tele NO DEBE guardar más de N + 10 copias (registro `Reserva: …`), y con internet ninguna obra DEBE esperar a bajarse cuando le toca (registro).
- SI no hay internet, ENTONCES el pase DEBE seguir con las obras de la reserva, sin pantalla negra, y Estado DEBE decirlo.
- CUANDO una galería trae subcarpetas, Galerías DEBE mostrarlas como grupo con sus galerías adentro, y tildar el grupo tilda todas.

## Ejecución
Tamaño: módulo solo. Construyen subagentes Sonnet; Opus coordina y revisa.
1. **Borrador HTML primero** (pedido de Guido): Inicio, Galerías (árbol con tildes) y una de las pantallas de ajustes agrupados, con el estilo de la app, en modo TV (como `docs/diseno/03-temas-v3.html`). Guido lo mira en la tele y lo aprueba; lo aprobado va a `docs/diseno/decisiones.md`. **Nada de UI se construye antes.**
2. Inicio + ajustes agrupados.
3. Catálogo con árbol de carpetas y Galerías.
4. Reserva de obras.
Cada parte: compilar en verde, instalar, probar en la tele, subir a GitHub.

## Entorno de prueba
- `.\gradlew.bat assembleRelease testDebugUnitTest` y `/instalar`.
- "Galerías-prueba" en Drive (3 galerías, 13 archivos) + una subcarpeta de grupo que arma Opus para probar el árbol. Para la reserva, la clave de prueba `--es reserva 5` (solo por adb) y una galería con más de 5 obras.
- Sin internet: `adb shell svc wifi disable` no sirve (corta adb); se prueba con la carpeta de Drive apuntando a un ID inexistente o desconectando el router con Guido presente.

## Supuestos
- [ALTO] [SIN CONTRASTAR] Drive v3 devuelve `imageMediaMetadata.width/height` para JPEG/PNG/WebP públicos con la clave. Si no, el pase usa medidas provisorias hasta bajar la obra.
- [ALTO] La tele tiene internet casi siempre; un corte de horas se cubre con la reserva, uno de días repite las obras de la reserva.
- [BAJO] Las últimas 10 vistas se guardan para Izquierda; más atrás, la obra se vuelve a bajar.
- [BAJO] Una carpeta con imágenes y subcarpetas a la vez es galería y grupo.

## Riesgos y decisiones ⚠️
- ⚠️ **Reserva en vez de guardar todo** (Guido, 02/11). Consecuencia: sin internet por días, se repiten las obras de la reserva. Además gasta conexión: la tele baja el **original** de cada obra (hasta 50 MB). A una obra por minuto, con originales de ~5 MB, son ~7 GB por día. Si molesta, el curador puede subir copias ya achicadas a 4K (~3 MB).
- ⚠️ **Destildar borra de la tele**. Consecuencia: volver a tildar obliga a bajar de nuevo (es la reserva, no todo).
- ⚠️ Solo se recorre la carpeta pública de "Galerías". Consecuencia: no se ve el resto del Drive; recorrerlo obligaría a iniciar sesión con Google en la tele (FUERA).

### Alternativas evaluadas y descartadas
- Guardar todo (lo de la sesión 1): la biblioteca real no entra en la tele.
- Online puro (bajar cada obra cuando le toca): cada obra tarda en aparecer, el pase se cae sin internet y gasta más conexión.

## FUERA de alcance
- Recorrer carpetas fuera de "Galerías" o iniciar sesión con Google.
- Subir, renombrar o borrar en Drive desde la tele.
- Elegir obras sueltas dentro de una galería (se elige por galería o grupo).
- El protector (sesión 4).
