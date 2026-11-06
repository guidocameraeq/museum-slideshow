# TODO — Museum Slideshow

> ÚNICA fuente de pendientes del proyecto. Completado → SE BORRA (la historia vive en CHANGELOG y git). Header de 1 línea, sin narrativa de sesión.

2026-11-05 — ver SESSION_HANDOFF.md

## 🔴 Crítico
- [ ] Verificar en la TV el caso "Google bloquea los originales": las obras siguen por copia, las fichas cambiadas esperan con la vieja y se bajan en el reintento, y Estado muestra el renglón tranquilo (probado solo en tests). Ver que aparezcan las 23 galerías.

## 🟠 Importante
- [ ] Guido mira a ojo la Barra lateral (tamaño de letra, valores de la derecha no alineados entre renglones, si 10 s alcanzan, Abajo varias veces en una pared), las pantallas de ajustes y Estado.
- [ ] Guido mira los marcos a ojo: "filete" es una foto aproximada (banda oscura entre molduras doradas) y además suma 4 px de filete del catálogo; si queda feo, filete 0 o pedir otra foto. El redondo no se vio nunca (no hay obras redondas).
- [ ] Pasar los ids de marco (`fino`, `filete`, `madera`, `dorado`, `dorado-redondo`, `sin`) al campo `marco` de `docs/formato-galeria.md` y avisarle al curador.
- [ ] Probar en la TV lo que falta: sin internet (el pase sigue con la reserva), "Sincronizar cada: Solo a mano" (desaparece de `dumpsys jobscheduler`), cambiar Tiempo por obra, reinicio real de la tele, pantalla Protector sin permiso.
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
