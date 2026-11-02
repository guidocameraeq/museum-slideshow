# CHANGELOG — Museum Slideshow

> Historia permanente. `/cierre` agrega una entrada AL TOPE en cada sesión. Orden descendente estricto, sin excepciones. Nada de versiones duplicadas en otros docs.

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
