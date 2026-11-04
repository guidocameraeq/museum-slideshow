# Curaduría — cómo trabajamos con el agente de Galerías de arte

> Qué hace cada uno, cómo se habla con el curador y qué reglas valen entre los dos. El formato de los archivos está en [`formato-galeria.md`](formato-galeria.md) (manda ese doc; el curador tiene una copia en su repo, `docs/FORMATO_DRIVE.md`).

## Quién hace qué
| Quién | Hace | No hace |
|---|---|---|
| **Curador** (repo hermano `../galerias-de-arte`, sesión "Agente Galerias de arte") | Elige obras, consigue imágenes, escribe las fichas, exporta a Drive y valida cada `galeria.json` | Código de la app, diseño en pantalla, la tele |
| **Esta app** (este repo) | Lee Drive, baja, acomoda y muestra; define el formato | Curar ni bajar arte por su cuenta |
| **Guido** | Su Drive: comparte la carpeta "Galerías" (cualquiera con el link, Lector) y cura **borrando** imágenes en Drive | — |

## Cómo se le pide algo al curador
- Por mensaje entre sesiones a la sesión **"Agente Galerias de arte"** (o abriendo un chat en `../galerias-de-arte`). Sus avisos llegan solos acá.
- Pedidos típicos: una galería nueva, corregir una ficha, marcar un tondo (`forma`), cambiar el formato (subir `formato` en los dos docs a la vez).
- Si la app necesita un campo nuevo: primero se agrega a `formato-galeria.md` (opcional, con default), después se le avisa.

## Reglas acordadas
- **Carpeta**: "Galerías" en el Drive de Guido; una subcarpeta por galería con sus imágenes y `galeria.json` (formato v1). Su ID va en `local.properties` (`drive.carpeta`) y en el ajuste "Carpeta de Drive".
- **Imágenes**: en Drive van los **originales** (hasta 50 MB; el curador achica lo que pasa). La app baja la **copia 4K que arma Drive** y el original solo de respaldo: Google bloquea la descarga masiva de originales desde la casa.
- **ids de obra estables**: no cambian aunque se renumere o se renombre el archivo. La app identifica cada obra por su archivo en Drive, así que un renombre no la duplica.
- **Curar borrando**: si Guido borra una imagen de Drive, el curador no la vuelve a subir y la app la saltea (puede seguir listada en el JSON). Si a una galería le falta más del 30 %, el curador no la toca (Drive a medio sincronizar).
- **Explicaciones**: las que hay (hoy cortas, 25–60 palabras); la app las muestra si entran.
- **Crédito de la imagen** (`credito`): el curador lo completa; la app lo muestra solo si el ajuste "Mostrar de dónde sale la imagen" está en Sí (por defecto, No).
- **Licencias**: Guido decidió publicar todo por el link, también obras con copyright (uso personal).
- **Pruebas**: "Galerías-prueba" (Sana, Rota-json, Rota-archivo) sigue en Drive para pruebas; `Sana/prueba-1080-vs-4k.jpg` y `Grupo prueba/` los puso el agente de la app.
