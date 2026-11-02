# Formato de galería — instrucciones para el agente de arte

> Este documento es para la IA que arma las galerías de Guido. Se puede leer solo, sin conocer la app.
> Versión del formato: **1**.

## Qué es una galería

Museum Slideshow es la galería de arte de la tele de Guido. Lee una carpeta de Google Drive llamada **"Galerías"**. Cada **subcarpeta** de esa carpeta es **una galería**: un pintor, una época, un tema. Adentro de cada subcarpeta van:

- **Las imágenes de las obras**: JPG, PNG o WebP, de hasta 50 MB cada una. Conviene que el lado largo tenga 2000 px o más; la app las achica sola.
- **Un archivo `galeria.json`**, con los datos de la galería y de cada obra.

```
Galerías/
├── Impresionismo/
│   ├── galeria.json
│   ├── monet-impresion-sol-naciente.jpg
│   └── renoir-baile-moulin-galette.jpg
└── Caravaggio/
    ├── galeria.json
    └── ...
```

## El archivo `galeria.json`

Reglas del archivo:
- JSON válido, en UTF-8. Sin comentarios y sin comas sobrantes al final de una lista.
- Hasta 1 MB.
- Se llama exactamente `galeria.json`, en minúsculas.

```json
{
  "formato": 1,
  "titulo": "Impresionismo",
  "descripcion": "París, 1860-1890: pintar la luz y el momento, al aire libre.",
  "obras": [
    {
      "archivo": "monet-impresion-sol-naciente.jpg",
      "titulo": "Impresión, sol naciente",
      "autor": "Claude Monet",
      "vida_autor": "1840–1926",
      "anio": "1872",
      "movimiento": "Impresionismo",
      "tecnica": "Óleo sobre lienzo",
      "medidas": "48 × 63 cm",
      "ubicacion": "Musée Marmottan Monet, París",
      "forma": "rectangular",
      "explicacion": "El puerto de Le Havre al amanecer, pintado en pocas horas.\n\nUn crítico usó el título de este cuadro para burlarse del grupo, y así nació la palabra «impresionismo».",
      "credito": "Dominio público, vía Wikimedia Commons"
    },
    {
      "archivo": "botticelli-madonna-granada.jpg",
      "titulo": "Virgen de la Granada",
      "autor": "Sandro Botticelli",
      "anio": "c. 1487",
      "forma": "redonda"
    }
  ]
}
```

### Datos de la galería

| Campo | ¿Obligatorio? | Qué es |
|---|---|---|
| `formato` | sí | Siempre el número `1` (sin comillas). La versión de este formato. |
| `titulo` | sí | El nombre de la galería, como se va a ver en la tele. |
| `descripcion` | no | Una o dos frases que presentan la galería. |
| `obras` | sí | La lista de obras. **El orden de la lista es el orden en que se muestran** (salvo que Guido elija "mezclar"). Por ejemplo, cronológico para ver cómo evoluciona un pintor. |

### Datos de cada obra

Todos son texto entre comillas.

| Campo | ¿Obligatorio? | Qué es |
|---|---|---|
| `archivo` | sí | El nombre **exacto** de la imagen en la misma subcarpeta. Respeta mayúsculas, minúsculas y la extensión. |
| `titulo` | no | El título de la obra. |
| `autor` | no | Quién la hizo. |
| `vida_autor` | no | Los años del autor, por ejemplo `"1840–1926"` o `"c. 1390–1441"`. |
| `anio` | no | Texto, no número, para admitir cosas como `"c. 1503-1519"` o `"siglo XV"`. |
| `movimiento` | no | El movimiento o la época, por ejemplo `"Impresionismo"` o `"Renacimiento"`. |
| `tecnica` | no | Por ejemplo, `"Óleo sobre tabla"`. |
| `medidas` | no | Por ejemplo, `"77 × 53 cm"`. |
| `ubicacion` | no | Dónde está hoy: museo y ciudad. |
| `forma` | no | `"rectangular"` (el valor por defecto), `"redonda"` u `"ovalada"`. Ponela **siempre** en las obras redondas u ovaladas (tondos, medallones): la app no las puede adivinar por los píxeles, y con este dato les pone un marco redondo. |
| `explicacion` | no | El texto tipo cartel de museo: qué muestra la obra, por qué importa, qué mirar. Entre 80 y 150 palabras es lo ideal: así entra al lado de la obra en la mayoría de los casos (si no entra, la app la muestra aparte con el botón OK). Los párrafos se separan con `\n\n`. Texto plano, sin Markdown ni HTML. |
| `marco` | no | El marco que sugiere el agente para esta obra. La app lo usa solo si Guido elige "el que diga la galería". El catálogo de nombres se define cuando estén los marcos reales; un nombre desconocido se ignora. |
| `credito` | no | De dónde sale la imagen o su licencia. Se muestra chico. |

## Qué hace la app si algo no está perfecto

Nada se rompe: la galería se muestra igual y cada problema queda anotado en la pantalla de Estado de la app.

| Caso | Qué hace la app |
|---|---|
| Una imagen de la carpeta que no figura en `obras` | La muestra al final, sin ficha. |
| Una obra cuyo `archivo` no existe | La saltea y la anota en Estado. |
| `galeria.json` roto o sin `formato` | Muestra todas las imágenes sin ficha, ordenadas por nombre de archivo, y lo anota en Estado. |
| Subcarpeta sin `galeria.json` | Usa el nombre de la carpeta como título y muestra las imágenes sin ficha. |
| Un campo que la app no conoce | Lo ignora. El formato puede crecer sin romper lo anterior. |
| Una imagen de más de 50 MB, o un archivo que no es imagen | Lo saltea y lo anota en Estado. |

## Antes de entregar una galería (chequeo para el agente)
1. `galeria.json` pasa un validador de JSON y pesa menos de 1 MB.
2. Cada `archivo` coincide letra por letra con una imagen de la subcarpeta, y no hay dos obras con el mismo `archivo`.
3. Las obras redondas u ovaladas tienen su `forma`.
4. `obras` está en el orden en que conviene verlas.
5. Los textos están en el idioma que eligió Guido, sin Markdown.
