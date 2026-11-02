# TODO — Museum Slideshow

> ÚNICA fuente de pendientes del proyecto. Completado → SE BORRA (la historia vive en CHANGELOG y git). Header de 1 línea, sin narrativa de sesión.

2026-11-02 — ver SESSION_HANDOFF.md

## 🔴 Crítico
- (nada)

## 🟠 Importante
- [ ] Probar en la TV lo que falta: sin internet (el pase sigue con la reserva y Estado dice "No se pudo conectar con Drive"; criterio del SPEC de menú y reserva), "Sincronizar cada: Solo a mano" (desaparece de `dumpsys jobscheduler`), cambiar Tiempo por obra (la obra siguiente dura lo nuevo), reinicio real de la tele.
- [ ] Guido mira a ojo la Barra, las pantallas de ajustes y Estado (Inicio y Galerías ya tienen diseño aprobado).
- [ ] Avisarle al curador: `Sana/prueba-1080-vs-4k.jpg` (fuera del JSON) y `Grupo prueba/{Uno,Dos}` en "Galerías-prueba" los puso Opus para probar.
- [ ] Reserva: la sincronización del Worker en segundo plano no dispara la reserva (se rearma al abrir la app o la galería); revisar en la sesión 4 que el protector la dispare.
- [ ] Conseguir fotos de marcos reales libres de derechos (negro fino, negro con filete dorado, madera con adornos, dorado, **dorado redondo**) y fijar sus nombres cortos para el campo `marco` de `docs/formato-galeria.md`. El código ya está listo (`MarcoImagen`, `res/drawable-nodpi/marco_<id>.png`); cuidar que `shrinkResources` no los borre.
- [ ] Sesión 4: protector (`DreamService`) e interruptor "Usar como protector / Volver al anterior". Reusar `GaleriaEnPantalla` y las dos superficies; verificar 4K y fundido también en el protector.
- [ ] Sesión 5: prueba de 2 horas y todos los criterios del SPEC-0 en la TV.
- [ ] Curador: exportar la biblioteca real a una carpeta "Galerías" nueva (espera las imágenes de la otra PC de Guido) y cambiar `drive.carpeta`.

## 🟡 Cuando se pueda
- [ ] Guido mira el fundido a ojo: a mitad del cruce, marcos y textos de una escena se ven un instante sobre la obra de la otra (las dos capas se cruzan por separado). Si molesta, ajustar.
- [ ] Ícono y banner definitivos: templo griego (tipo Partenón). Hoy hay uno provisorio.
- [ ] Rotación EXIF en `ProcesadorImagenAndroid` (hoy no se aplica; las obras casi nunca la traen).
- [ ] La grilla de 1 px se ve apenas: el sincronizador guarda JPEG 95, que suaviza rayas de 1 px. Para obras normales no se nota (la comparación 1080/4K dio bien).

## 🟢 Ideas / algún día
- [ ] Más temas; zoom o paneo lento sobre la obra (fuera de la v1).
