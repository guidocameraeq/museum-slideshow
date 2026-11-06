# CHANGELOG — Museum Slideshow

> Historia permanente. `/cierre` agrega una entrada AL TOPE en cada sesión. Orden descendente estricto, sin excepciones. Nada de versiones duplicadas en otros docs.

## 2026-11-05 — Misión del 05/11: fichas, reserva, Barra lateral y marcos reales

### Added
- **Barra lateral** (borrador `07-barra-lateral.html`, aprobado): Abajo abre un panel vertical a la izquierda con Galería ▸ (lista con tapas y grupos), Tema ▸ (miniaturas y vista en vivo), Pantalla completa, Explicación, Ajustes ▸ y Sincronizar. Ajuste nuevo "Cerrar la Barra sola" (5/10/20 s/Nunca, 10 s por defecto; adb `--es barra`). También en el protector (Ajustes ▸ cierra el protector y abre Apariencia).
- **Marcos con fotos reales** CC0 del Met (madera, dorado, dorado redondo, filete), preparadas con `herramientas/marcos/PrepararMarcos.java` (fondo y hueco transparentes, grosor por lado). Créditos en `docs/marcos.md`.

### Changed
- La reserva ya no tira lo guardado al salir de la ventana: solo saca lo más viejo de afuera cuando falta espacio, y recién con la obra nueva lista. Obras en el orden del pase.
- La copia 4K baja sin la pausa del "Ritmo de bajada" (queda solo para los originales).
- El listado de Drive tiene su propia pausa: el bloqueo de los originales ya no frena la sincronización.
- En una pared, Abajo después de la última obra abre la Barra.

### Fixed
- Las fichas (`galeria.json`) no se actualizaban cuando Google bloqueaba los originales: ahora una ficha cambiada espera con la vieja y las demás siguen (las de igual md5 nunca se bajan).
- La vista previa de Apariencia mostraba siempre un borde negro: ahora dibuja el marco elegido.
- Elegir galería en la Barra "se iba al costado"; en el protector la Barra no abría. 444 tests. Todo probado en la TV.

## 2026-11-03 (noche) — Portada del repo, curaduría y crédito oculto

### Added
- Portada: `README.md` nuevo con banner (`docs/assets/banner.svg`, templo griego) y capturas de los diseños aprobados.
- `docs/curaduria.md`: quién hace qué con el agente curador, cómo pedirle y las reglas acordadas (originales en Drive, copia 4K, ids estables, curar borrando).
- Ajuste "Mostrar de dónde sale la imagen" (Sí / No, por defecto No): el crédito "Dominio público, vía Wikimedia Commons" ya no se ve. 403 tests. Probado en la TV.

## 2026-11-03 — Obras por la copia 4K de Drive

### Changed
- Las obras y las tapas se bajan primero por la copia de Drive (`thumbnailLink` pedido fresco, `=s3840` / `=s1920` / `=s480`, servidor `lh3`), que Google no bloquea; el original (`alt=media`) queda de respaldo. Ajuste nuevo "Bajar las obras de" (copia primero, por defecto / original primero).
- La pausa ante el bloqueo de Google es por camino (original / copia): si se traba el original, las obras siguen por copia y Estado lo dice sin alarmar. 401 tests. Probado en la TV: la reserva bajó obras de Gótico "por copia s3840", ~3 s cada una, sin cierres.

## 2026-11-02 (23:40) — Sesión 4: protector e interruptor; biblioteca real; pausa ante el bloqueo de Google

### Added
- Protector de pantalla `ProtectorMuseo` (DreamService): la misma galería, 4K, sin Barra; Atrás cierra lo abierto antes de salir; el volumen nunca se consume. Sincroniza al arrancar solo si toca.
- Interruptor "Usar como protector / Volver al anterior" (guarda y restaura el anterior; una sola función escribe `screensaver_components`) y pantalla Protector según el borrador `05-protector.html` (con "Probarlo ahora"). Extra de prueba `--es protector usar|volver`.
- Pausa ante "Google pidió una pausa" (429 / 403 de consultas automáticas o cuota): 30 min→6 h, respetada por todo, con reintento solo y mensaje en Estado. Ajuste "Ritmo de bajada" (Tranquilo / Normal). 374 tests.

### Changed
- `MotorApp`: catálogo, ajustes, sincronización y una sola reserva por proceso, compartidos por la app y el protector. La reserva baja de a una.
- La app lee la biblioteca real: carpeta "Galerías" (23 colecciones del curador).

### Fixed
- La app se cerraba al leer un 403 (`InputStream.readNBytes` no existe en Android 12).

## 2026-11-02 (21:10) — Cierre formal (cambio de chat)

### Changed
- Handoff: el próximo paso arranca por la biblioteca real (si el curador ya la subió) y sigue con la sesión 4 (protector).

## 2026-11-02 (20:40) — Menú de inicio, elegir galerías y reserva de obras

### Added
- `docs/SPEC-menu-y-reserva.md` (SPEC delta, COMPLETO) y borrador `docs/diseno/04-menu-y-galerias.html`.
- Pantalla Inicio (al abrir la app a mano) y ajustes agrupados: Apariencia (con vista previa), Pase, Conexión y espacio, Protector.
- Pantalla Galerías: tarjetas con tapa y tilde, grupos con mosaico, vista Lista (ajuste), "Galerías nuevas".
- Catálogo en árbol (grupos de carpetas), tapas de cada galería y medidas desde Drive.
- Reserva de obras: la tele guarda las próximas N + 10 vistas y baja de a tandas (ajuste 20/50/100/200).

### Changed
- El sincronizador ya no baja todas las obras: baja el catálogo, los JSON y las tapas; las obras las baja la reserva.
- Atrás en la galería vuelve a Inicio. 335 tests.

## 2026-11-02 (19:00) — Sesión 3: ajustes, Barra y Estado

### Added
- Ajustes que se guardan (DataStore), con efecto inmediato; los extras de intent también se guardan (`reset`, `carpeta`, `sincronizar`, `espacio`).
- Barra (Abajo), pantalla de Ajustes (todos los del SPEC) y pantalla de Estado (sincronización, obras, espacio, problemas en criollo, permiso y protector activo). Avisos de sin obras y de sincronizando. 203 tests.
- "Sincronizar cada", "Espacio máximo" y "Carpeta de Drive" como ajustes; el error de red al listar la raíz llega a Estado.
- Permiso `WRITE_SECURE_SETTINGS` declarado en el manifest (se usa en la sesión 4; hoy solo se consulta).

### Fixed
- Cambiar la Calidad con la app abierta dejaba la obra en un rincón o al doble: ahora se recrean las dos superficies.

## 2026-11-02 (17:50) — Sesión 2: la galería en pantalla a 4K

### Added
- Acomodador (`acomodo/`), temas, colores de pared (con bordó) y marcos (`temas/`), preferencias con sus valores por defecto (`ajustes/`), fuentes EB Garamond e Inter (OFL).
- Galería en pantalla (`galeria/`): dos superficies 4K detrás de una capa de Compose, fundido hecho por el compositor (~58 cuadros/s), vista Explicación, control (OK, mantener OK, flechas, Play/Pausa, Atrás) y extras de intent para probar por adb. 147 tests.
- Imágenes de prueba en "Galerías-prueba/Sana": grilla de 1 px y la comparación 1080/4K.

### Changed
- Las obras se abren como Bitmap de memoria de video: la app usa la mitad de memoria.
- SPEC-0: 4K verificado (capa 3840×2160 `DEVICE` y ojos de Guido); supuestos de `screencap` y del fundido verificados.

## 2026-11-02 (16:20) — Sesión 1: sincronizador + lector, y la clave de Drive

### Added
- `catalogo/` (lector de galerías) y `sync/` (sincronizador de Drive, copia 4K por franjas, copia chica, tope de espacio, Worker cada 6 h). Pantalla provisoria con el resumen de galerías, obras y problemas. 31 tests.
- `Registro.limpiar()`: borra cualquier `key=` de lo que se loguea.
- Dependencia `kotlinx-serialization-json` 1.7.3 (JSON estricto: el de Android acepta JSON rotos).
- Clave de Drive en la cuenta guido.camera12 y carpeta "Galerías-prueba" del curador. Probado en la TV: 3 galerías, 9 obras, 2 problemas esperados.

### Changed
- `docs/formato-galeria.md`: campos opcionales `licencia`, `fuente` e `id`; imágenes desde 1000 px; explicación corta aceptada (acordado con el agente curador).
- SPEC-0, supuesto de la clave sin login: verificado.

## 2026-11-02 (tarde) — SPEC-0 re-sellado y sesión 0

### Added
- Proyecto Gradle (andamiaje de Volumen Maestro sin su cliente ADB), firma propia `keystore.jks`, pantalla mínima, ícono y banner provisorios. Instalada y probada en la TV.
- En el SPEC-0: presupuesto de memoria, copia 4K siempre (1080 = a la mitad), una medida para las dos capas, ajustes "Horario del Automático" y color de pared claro/oscuro, marco dorado redondo, criterios de 4K en el protector, fluidez y botones.

### Changed
- `SPEC-0.md` re-sellado y READY con las enmiendas del 02/11 integradas (red team breve).
- `CLAUDE.md`: la PC de `C:\Users\guido` ya tiene JDK 17 y Android SDK.

## 2026-11-02 — Diseño visual cerrado y 4K probado

### Added
- `docs/diseno/`: borradores 01 (mapa de pantallas y estilos), 02 (temas × modos) y 03 (versión final con acomodador inteligente), más `decisiones.md` con todo lo aprobado por Guido mirándolos en la tele.
- `docs/formato-galeria.md`: campos opcionales `vida_autor`, `movimiento` y `marco`.

### Changed
- `SPEC-0.md` enmendado (sin re-sellar): la obra se muestra a 4K real (superficie de 3840×2160, probado en la TV); 4 temas con modos Claro/Oscuro/Automático, colores de pared, marcos reales y 4 tamaños; acomodador que elige lugar y cantidad de información; nuevos botones (OK = pantalla completa, mantener OK = explicación) también en el protector.
- `CLAUDE.md` y `/instalar`: adb en la ruta real de esta PC.

### Removed
- Tema Clásico (lo probó Guido y no le gustó; su marco dorado queda como opción de marco).

## 2026-11-01 — Nace el proyecto

### Added
- `SPEC-0.md` en READY: galería de arte que es app y protector, galerías en Drive con `galeria.json`, 3 temas, pared de verticales, todo configurable, interruptor del protector.
- Sistema de trabajo: `CLAUDE.md`, `/inicio`, `/cierre`, `/instalar`, hook de arranque.
- `docs/formato-galeria.md` (para el agente de arte) y `docs/desarrollo.md`.
