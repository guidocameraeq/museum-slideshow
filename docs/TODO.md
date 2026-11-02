# TODO — Museum Slideshow

> ÚNICA fuente de pendientes del proyecto. Completado → SE BORRA (la historia vive en CHANGELOG y git). Header de 1 línea, sin narrativa de sesión.

2026-11-02 — ver SESSION_HANDOFF.md

## 🔴 Crítico
- [ ] Guido: crear la clave de Drive (proyecto de Google Cloud sin facturación, Drive API, clave limitada a Drive API) y ponerla en `local.properties` junto con el ID de la carpeta. Pasos en `docs/desarrollo.md`.
- [ ] Guido + su agente de arte: carpeta "Galerías-prueba" pública de solo lectura con 2 galerías según `docs/formato-galeria.md` (una sana con horizontales, verticales y una redonda; otra con el JSON roto a propósito y una imagen faltante).
- [ ] Claude: armar la obra "grilla" (rayas de 1 px a 3840×2160) para la galería sana de prueba.
- [ ] Cerrar la sesión 0: `curl` a Drive con la clave (contrasta el supuesto ALTO de la clave sin login).

## 🟠 Importante
- [ ] Sesión 1: sincronizador + lector de galerías.
- [ ] Sesión 2: acomodador + los 4 temas + marcos + galería en pantalla con la superficie 4K.
- [ ] Conseguir fotos de marcos reales libres de derechos (negro fino, negro con filete dorado, madera con adornos, dorado, **dorado redondo**) y fijar sus nombres cortos para el campo `marco` de `docs/formato-galeria.md`.
- [ ] Sesión 3: ajustes, barra y pantalla de Estado.
- [ ] Sesión 4: protector (`DreamService`) e interruptor "Usar como protector / Volver al anterior".
- [ ] Sesión 5: prueba de 2 horas y todos los criterios del SPEC-0 en la TV.

## 🟡 Cuando se pueda
- [ ] Ícono y banner definitivos: templo griego (tipo Partenón). Hoy hay uno provisorio.

## 🟢 Ideas / algún día
- [ ] Más temas; zoom o paneo lento sobre la obra (fuera de la v1).
