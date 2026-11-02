# CHANGELOG — Museum Slideshow

> Historia permanente. `/cierre` agrega una entrada AL TOPE en cada sesión. Orden descendente estricto, sin excepciones. Nada de versiones duplicadas en otros docs.

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
