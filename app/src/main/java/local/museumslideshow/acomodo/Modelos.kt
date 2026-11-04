package local.museumslideshow.acomodo

import local.museumslideshow.ajustes.Modo
import local.museumslideshow.ajustes.Preferencias
import local.museumslideshow.catalogo.Ficha
import local.museumslideshow.catalogo.Forma
import local.museumslideshow.catalogo.Obra
import local.museumslideshow.temas.MarcoDef
import local.museumslideshow.temas.TemaDef

/** Cuánta información lleva el cartel: completa (ficha + explicación) > ficha > mínima (título, autor, año). */
enum class NivelInfo { COMPLETA, FICHA, MINIMA }

data class MedidaTexto(val ancho: Float, val alto: Float, val entra: Boolean = true)

/** Lo implementa quien dibuja (con las fuentes de verdad); en las pruebas hay uno falso. Todo en px del escenario de 1920x1080. */
interface MedidorTexto {
    /** Alto en px del cartel de una obra con ese nivel y ese ancho; `entra=false` si alguna palabra no entra a lo ancho. */
    fun medirCartel(
        ficha: Ficha, nivel: NivelInfo, conAvisoOk: Boolean, ancho: Float, tema: TemaDef, modo: Modo, conCredito: Boolean,
    ): MedidaTexto

    /** Nombre bajo la obra en la Pared. */
    fun medirNombre(ficha: Ficha, ancho: Float, tema: TemaDef, modo: Modo): MedidaTexto

    /** Cuadrito flotante: ancho y alto (máx. 440 de ancho). */
    fun medirChip(ficha: Ficha, tema: TemaDef, modo: Modo): MedidaTexto
    fun medirSala(texto: String, tema: TemaDef, modo: Modo): MedidaTexto
    fun medirContador(texto: String, tema: TemaDef, modo: Modo): MedidaTexto

    /** Libro de arte: alto de la columna derecha completa con la explicación a ese tamaño de letra. */
    fun medirPaginaLibro(
        ficha: Ficha, nombreSala: String?, conExplicacion: Boolean, tamanoExplicacionPx: Float,
        conAvisoOk: Boolean, ancho: Float, modo: Modo, conCredito: Boolean,
    ): MedidaTexto
}

/** Una obra con el marco que le toca (ya resuelto con [local.museumslideshow.temas.CatalogoMarcos.resolver]). */
data class ObraEnEscena(val obra: Obra, val marco: MarcoDef)

data class PedidoEscena(
    /** 1 = sola; 2-3 = Pared. */
    val obras: List<Obra>,
    /** En una Pared, la obra elegida con Arriba/Abajo (para pantalla completa). */
    val elegida: Int,
    val tema: TemaDef,
    /** CLARO u OSCURO (usá `modoEfectivo`). */
    val modo: Modo,
    val prefs: Preferencias,
    /** OK la prende/apaga. Si el Tamaño ya es PANTALLA_COMPLETA, OK pasa a GRANDE. */
    val pantallaCompleta: Boolean,
    /** Mantener OK: pidió la explicación. */
    val forzarExplicacion: Boolean,
    val nombreSala: String?,
    /** Por ejemplo "3 / 24". */
    val contador: String,
)

data class Rect(val x: Float, val y: Float, val ancho: Float, val alto: Float) {
    val derecha: Float get() = x + ancho
    val abajo: Float get() = y + alto
    val centroX: Float get() = x + ancho / 2
    val centroY: Float get() = y + alto / 2

    /** ¿Se pisan (con más de medio px de por medio)? */
    fun pisa(o: Rect): Boolean =
        minOf(derecha, o.derecha) - maxOf(x, o.x) > 0.5f && minOf(abajo, o.abajo) - maxOf(y, o.y) > 0.5f

    fun agrandar(h: Float, v: Float) = Rect(x - h, y - v, ancho + 2 * h, alto + 2 * v)
}

/**
 * `interior` es la imagen; `exterior` incluye marco, filete y paspartú. Marco, filete y paspartú en px
 * (el marco es el aro de `grosorMarco`, después el `filete` dorado, después el `paspartu`, después la imagen).
 */
data class ObraColocada(
    val obra: Obra,
    val interior: Rect,
    val exterior: Rect,
    val marco: MarcoDef,
    val grosorMarco: Float,
    val filete: Float,
    val paspartu: Float,
    val forma: Forma,
)

sealed class Texto {
    abstract val rect: Rect

    data class Cartel(override val rect: Rect, val nivel: NivelInfo, val avisoOk: Boolean) : Texto()
    data class Nombre(override val rect: Rect, val obraIndice: Int) : Texto()
    data class Chip(override val rect: Rect) : Texto()
    data class Sala(override val rect: Rect) : Texto()
    data class Contador(override val rect: Rect) : Texto()
}

/**
 * Libro de arte. `obraArea` es la caja donde se centra la obra, `columna` la columna del texto.
 * `tamanoExplicacionPx` != null = la explicación entra entera a ese tamaño. `corrido` = el texto del renglón
 * de arriba de la página (nombre de la sala o contador, según el ajuste); null = vacío.
 */
data class PaginaLibro(
    val obraArea: Rect,
    val columna: Rect,
    val tamanoExplicacionPx: Float?,
    val avisoOk: Boolean,
    val corrido: String? = null,
)

data class Disposicion(
    val obras: List<ObraColocada>,
    val textos: List<Texto>,
    val libro: PaginaLibro?,
    /** Pidió la explicación (mantener OK) y no entra al lado de la obra: el que dibuja abre la vista Explicación. */
    val explicacionNoEntra: Boolean,
    val nivel: NivelInfo?,
)
