# Decisiones de diseño

> Lo que Guido aprobó o pidió mirando los borradores. Es la fuente para el borrador siguiente y para la enmienda del SPEC-0 al cerrar el diseño. Los borradores (`0N-*.html`) muestran cómo se ve; esta lista dice qué quedó decidido.

## Temas (v1)
- **Galería moderna**: marco negro fino con paspartú blanco y la letra del borrador 2 → aprobados.
- **Sala oscura**: letra, marco negro con filete dorado y el cartel con ficha completa → aprobados.
- **Inmersivo**: obra enorme sobre el fondo desenfocado de sí misma, cartel a los costados → aprobado. **Sin degradado** sobre la obra.
- **Libro de arte**: obra a la izquierda y texto a la derecha. Queda bien con una obra vertical. **No hace Paredes**: muestra las obras de a una.
- **Clásico**: sale de la v1 (02/11). El marco dorado sobrevive como opción de marco.

## Modos y colores
- **Claro** para el día (los oscuros se pierden de día) y **Oscuro** para la noche.
- **Automático**: claro de día, oscuro de noche, según la hora. Es el valor por defecto.
- **Color de pared**: cada modo tiene variantes, por ejemplo claros (blanco galería, gris cálido, piedra) y oscuros (carbón, negro, azul noche).

## Marcos
- Ajuste **Marco**: el del tema / uno fijo elegido / al azar por obra (cada obra conserva siempre el mismo) / el que diga la galería.
- Catálogo: negro fino, negro con filete dorado, madera, dorado, sin marco.

## Tamaño de la obra
- Normal / **Grande (por defecto)** / Máximo. Grande es el equilibrio entre obra grande e información.

## Carteles y texto: el acomodador es inteligente
- **El texto nunca pisa la obra si hay lugar libre.** El acomodador mide la obra (la app conoce sus píxeles; no hace falta ponerlos en el JSON) y el espacio que sobra. Ahí elige dónde va el cartel: costado, abajo o, si no queda nada (Inmersivo con la pantalla llena), un cuadrito flotante.
- **Cuánta información**, según el espacio libre: completa (ficha + explicación) → ficha → mínima (título, autor, año). Nunca se corta un texto a la mitad: si la explicación no entra entera, no se muestra.
- **Paredes**: cada obra con su nombre abajo, también en Máximo. Las obras se achican lo justo para dejar esa franja. La explicación casi nunca entra.
- Ajuste **Explicación en pantalla**: Cuando entra / Nunca. La tecla **OK** la muestra y la oculta en el momento. Si no entra al lado de la obra, OK la abre en una vista tipo Libro de arte.
- **Nombre de la sala** («Impresionismo · 3 / 24»): nunca tapa la obra. Ajuste: siempre / unos segundos / nunca.

## Más datos en la ficha
- Campos nuevos opcionales en `galeria.json`: **movimiento** («Impresionismo») y **años del autor** («1840–1926»). Hay que pasarlos a `docs/formato-galeria.md` al cerrar el diseño.

## Del borrador 3 en la tele (02/11)
- Le gustó: texto, información, layout. Inmersivo con Paredes de 2 y 3 «se siente más pulido».
- **Cartel al costado: centrado en altura con la obra** (ni arriba ni abajo de todo ni empezando en la mitad).
- **Nombre de la sala**: está bien que se esconda donde molesta, pero la información no se pierde: cuando no hay lugar, un **contador chico** («9 / 22») en un rincón libre.
- **Pantalla completa**: la obra sola, lo más grande posible y sin textos. Sirve sobre todo para las obras gigantes. Puede quedar fija si Guido quiere.
- **Botones**: OK = pantalla completa sí/no; y otro gesto para la explicación sí/no (propuesta en «Pendiente de ver»).

## Cierre del diseño (02/11)
- **Pantalla completa**: la obra entera (nunca recortada), con dos variantes: **sin marco** (solo la obra) y **con marco**.
- **Marcos de verdad**: los marcos dibujados con código parecen digitales. El de madera tiene que ser un marco real con adornos. Los marcos se arman con **imágenes de marcos reales** que se estiran por partes (las esquinas quedan intactas), no con líneas y degradados.
- **Colores de pared**: la paleta gustó. Se suma el **bordó** del viejo Clásico como color de pared.
- **Botones** (aprobados): OK = pantalla completa sí/no · mantener OK = explicación sí/no · Abajo = Barra, con esos dos interruptores también ahí.
- **En el protector, los mismos botones**: OK **no** lo cierra. Atrás y Home sí.
- **Todo modular y parametrizable**: cada tema, marco, color y nivel de información es una pieza aparte. El acomodador se adapta a lo que trae cada obra: sin explicación, el cartel se acomoda sin dejar huecos; con explicación, la usa si entra y si el ajuste lo permite.
- Guido da el diseño por bueno para construir: lo fino se ajusta usándola.

## Menú de inicio y Galerías (borrador 4, 02/11)
- **Inicio A** (la obra de la galería actual de fondo con velo a la izquierda, nombre grande, tres datos chicos, botón principal "Ver la galería" y seis botones) → aprobado. El B (tarjetas) queda descartado.
- **Galerías**: tarjetas con la **tapa** (primera obra de cada galería) y tilde; los grupos con mosaico de tapas, OK entra. La lista con tildes queda como **segunda vista** (ajuste Tarjetas / Lista, por defecto Tarjetas).
- Guido dio el borrador 4 por bueno para construir (02/11).

## Pantalla Protector (borrador 5, 02/11)
- Aprobado tal cual `05-protector.html`: un botón grande según el estado ("Usar Museum Slideshow como protector" / "Volver al anterior (<nombre>)"), "Probarlo ahora" cuando ya es el nuestro, franja verde "Listo" al activar, panel con el protector de hoy y el anterior, y sin permiso el botón apagado con el comando.
