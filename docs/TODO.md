# TODO — Museum Slideshow

> ÚNICA fuente de pendientes del proyecto. Completado → SE BORRA (la historia vive en CHANGELOG y git). Header de 1 línea, sin narrativa de sesión.

2026-11-02 — ver SESSION_HANDOFF.md

## 🔴 Crítico
- [ ] Bug: cambiar a "Calidad 1080" con la app abierta (`--es calidad 1080` por intent) deja la obra chica en el rincón de arriba a la izquierda y el resto negro. Las superficies pasan a 1920×1080 pero el dibujo no se reacomoda. Revisar `SuperficieObra.fijarCalidad` y el redibujo tras `surfaceChanged`.

## 🟠 Importante
- [ ] Sesión 3: ajustes persistentes (DataStore), Barra (Abajo) y pantalla de Estado. Incluye: el `ERROR_RED` al listar la carpeta raíz hoy no se guarda en el índice (solo va al registro) → tiene que llegar a Estado; "Sincronizar cada" y "Espacio máximo" pasan de constantes (`sync/Sincronizacion.kt`) a ajustes.
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
