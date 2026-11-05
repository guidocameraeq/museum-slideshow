# TODO — Museum Slideshow

> ÚNICA fuente de pendientes del proyecto. Completado → SE BORRA (la historia vive en CHANGELOG y git). Header de 1 línea, sin narrativa de sesión.

2026-11-02 — ver SESSION_HANDOFF.md

## 🔴 Crítico
- [ ] **Misión 05/11 (pedido de Guido usándola), en este orden:**
  1. **Drive — fichas**: los `galeria.json` bajan por `alt=media` (el camino que Google bloquea); la copia 4K es solo para imágenes. Bajar una ficha solo si cambió (fecha/md5 del listado, que no se bloquea); revisar primero qué hace hoy.
  2. **Drive — "Preparando obras"**: al cambiar de galería la reserva tira TODO lo guardado (TV 05/11 16:00: 50 obras/134 MB → 0, y baja 50 de cero). No borrar al cambiar de galería (solo por espacio), primera obra ya, sin pausa de ritmo por el camino copia.
  3. **Barra (Abajo)**: en el protector no abre (Guido la quiere ahí, sobre todo para elegir galería); elegir galerías en la Barra navega mal (se va al costado; una o "Todas" está bien). Borrador HTML antes de construir.
  4. **Marcos**: la vista previa de Apariencia no muestra el marco elegido (hoy todos son el negro provisorio porque faltan las fotos). Fotos pedidas al curador el 05/11.
- [ ] Verificar en la TV el caso "originales bloqueados, copias siguen" (probado en la PC y en tests; el 03/11 en la TV los originales no estaban bloqueados) y que Estado muestre el renglón tranquilo. Ver que aparezcan las 23 galerías.

## 🟠 Importante
- [ ] La reserva borra lo guardado aunque no pueda bajar lo que lo reemplaza (pasó con Rococó antes de la pausa): no borrar una obra hasta tener lista la que entra.
- [ ] Probar en la TV lo que falta: sin internet (el pase sigue con la reserva), "Sincronizar cada: Solo a mano" (desaparece de `dumpsys jobscheduler`), cambiar Tiempo por obra, reinicio real de la tele, pantalla Protector sin permiso.
- [ ] Guido mira a ojo la Barra, las pantallas de ajustes y Estado.
- [ ] Conseguir fotos de marcos reales libres de derechos (negro fino, negro con filete dorado, madera con adornos, dorado, **dorado redondo**) y fijar sus nombres cortos para el campo `marco` de `docs/formato-galeria.md`. El código ya está listo (`MarcoImagen`, `res/drawable-nodpi/marco_<id>.png`); cuidar que `shrinkResources` no los borre.
- [ ] Tondos: ninguna obra de la biblioteca real trae `forma`; si Guido ve alguno redondo, avisarle al curador para marcarlo.
- [ ] Sesión 5: prueba de 2 horas y todos los criterios del SPEC-0 en la TV.

## 🟡 Cuando se pueda
- [ ] Lint: 5 errores `NewApi` viejos (API 29 en `PintorSuperficie`, minSdk 28). Inofensivos en la TV (API 31); subir `minSdk` a 29 o envolver.
- [ ] `pie_protector` en `strings.xml` quedó sin uso.
- [ ] Guido mira el fundido a ojo: a mitad del cruce, marcos y textos de una escena se ven un instante sobre la obra de la otra. Si molesta, ajustar.
- [ ] Ícono y banner definitivos: templo griego (tipo Partenón). Hoy hay uno provisorio.
- [ ] Rotación EXIF en `ProcesadorImagenAndroid` (hoy no se aplica).
- [ ] La grilla de 1 px se ve apenas: el sincronizador guarda JPEG 95, que suaviza rayas de 1 px.

## 🟢 Ideas / algún día
- [ ] Más temas; zoom o paneo lento sobre la obra (fuera de la v1).
