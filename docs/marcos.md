# Fotos de marcos

Las fotos de los marcos son de dominio público (CC0), tomadas de Wikimedia Commons. Quedan en `app/src/main/res/drawable-nodpi/marco_<id>.png`.

| Marco de la app | Archivo original | Museo | Fuente | Licencia |
|---|---|---|---|---|
| `dorado` | dorado.jpg | The Metropolitan Museum of Art (Nueva York), marco estilo Luis XIV | https://commons.wikimedia.org/wiki/File:Louis_XIV-style_frame_MET_86RR_254r2m.jpg | CC0 |
| `dorado-redondo` | dorado-redondo.jpg | The Metropolitan Museum of Art, tondo italiano | https://commons.wikimedia.org/wiki/File:Tondo_MET_86A_027R2m.jpg | CC0 |
| `madera` | madera.jpg | The Metropolitan Museum of Art, cassetta italiano de madera oscura | https://commons.wikimedia.org/wiki/File:Cassetta_frame_MET_86C_030r2m.jpg | CC0 |
| `filete` | negro-filete.jpg | The Metropolitan Museum of Art, cassetta con banda oscura entre molduras doradas (aproximado) | https://commons.wikimedia.org/wiki/File:Cassetta_frame_MET_86K_088r3.jpg | CC0 |

`fino` se dibuja en código (no tiene foto). Descartado: `dorado-alt.jpg` (Rijksmuseum; trae una plaquita y perspectiva).

## Cómo se preparan

Los originales (JPG sobre fondo liso) están en `E:\Guido\Fotos & Videos\Banco_Galerias_De_Arte\_marcos\` con su `marcos.json`. Desde la raíz del repo:

```
& 'C:\Program Files\Eclipse Adoptium\jdk-17.0.20.101-hotspot\bin\java.exe' herramientas\marcos\PrepararMarcos.java "E:\Guido\Fotos & Videos\Banco_Galerias_De_Arte\_marcos" 800
```

(el 800 es el lado largo en px; sin ese número usa 1000). La herramienta vuelve transparentes el fondo y el hueco, recorta, achica y deja los PNG. Imprime el grosor de cada lado (izquierda, arriba, derecha, abajo) ya escalado: esos números van en `Marcos.BORDES_DE_FOTO` (`galeria/DibujanteMarco.kt`). El redondo se dibuja entero y no usa bordes.

`res/raw/keep.xml` evita que el release borre las fotos (se buscan por nombre).
