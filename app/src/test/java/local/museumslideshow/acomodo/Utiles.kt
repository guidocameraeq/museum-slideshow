package local.museumslideshow.acomodo

import local.museumslideshow.ajustes.Modo
import local.museumslideshow.ajustes.Preferencias
import local.museumslideshow.ajustes.Tema
import local.museumslideshow.catalogo.Ficha
import local.museumslideshow.catalogo.Forma
import local.museumslideshow.catalogo.Obra
import local.museumslideshow.temas.RegistroTemas
import local.museumslideshow.temas.TemaDef
import kotlin.math.ceil
import kotlin.math.max

/** Medidor determinista: alto = líneas x interlineado, con líneas = caracteres x ancho medio por carácter / ancho. */
class MedidorFalso : MedidorTexto {
    private fun lineas(chars: Int, tamano: Float, ancho: Float): Int =
        max(1, ceil(chars * 0.5f * tamano / ancho).toInt())

    override fun medirCartel(ficha: Ficha, nivel: NivelInfo, conAvisoOk: Boolean, ancho: Float, tema: TemaDef, modo: Modo): MedidaTexto {
        if (ancho < 150f) return MedidaTexto(ancho, 0f, false)
        var h = lineas((ficha.titulo ?: "").length, 32f, ancho) * 38f + 31f
        if (nivel != NivelInfo.MINIMA) {
            h += 3 * 26f + 26f + 12f
            if (nivel == NivelInfo.COMPLETA) h += lineas((ficha.explicacion ?: "").length, 20f, ancho) * 29f + 16f
        }
        if (conAvisoOk) h += 48f
        return MedidaTexto(ancho, h)
    }

    override fun medirNombre(ficha: Ficha, ancho: Float, tema: TemaDef, modo: Modo): MedidaTexto =
        MedidaTexto(ancho, lineas((ficha.titulo ?: "").length, 27f, ancho) * 32f + 26f)

    override fun medirChip(ficha: Ficha, tema: TemaDef, modo: Modo) = MedidaTexto(300f, 80f)
    override fun medirSala(texto: String, tema: TemaDef, modo: Modo) = MedidaTexto(texto.length * 13f, 26f)
    override fun medirContador(texto: String, tema: TemaDef, modo: Modo) = MedidaTexto(texto.length * 10f, 24f)

    override fun medirPaginaLibro(
        ficha: Ficha, nombreSala: String?, conExplicacion: Boolean, tamanoExplicacionPx: Float,
        conAvisoOk: Boolean, ancho: Float, modo: Modo,
    ): MedidaTexto {
        var h = 330f + 40f
        if (conExplicacion) {
            val chars = (ficha.explicacion ?: "").length
            val l = max(1, ceil(chars * 0.45f * tamanoExplicacionPx / ancho).toInt())
            h += 29f + l * tamanoExplicacionPx * 1.42f
        }
        if (conAvisoOk) h += 50f
        return MedidaTexto(ancho, h)
    }
}

object Datos {
    const val EXPLICACION_CORTA = "Una explicación corta de la obra que entra en cualquier cartel con lugar, con unas pocas líneas de texto nada más."
    val EXPLICACION_LARGA = "Texto muy largo de explicación. ".repeat(100)

    fun ficha(
        titulo: String? = "Impresión, sol naciente",
        forma: Forma = Forma.RECTANGULAR,
        explicacion: String? = EXPLICACION_CORTA,
        marco: String? = null,
    ) = Ficha(
        titulo, "Claude Monet", "1840–1926", "1872", "Impresionismo", "Óleo sobre lienzo", "48 × 63 cm",
        "Museo Marmottan, París", forma, explicacion, marco, "Dominio público", null, null, "id",
    )

    fun obra(
        id: String, ancho: Int, alto: Int, ficha: Ficha? = ficha(), forma: Forma? = null,
    ) = Obra(
        driveId = id, archivo = "$id.jpg", ancho = ancho, alto = alto,
        ficha = if (forma != null && ficha != null) ficha.copy(forma = forma) else ficha,
    )

    val monet = obra("monet", 1600, 1245)
    val venus = obra("venus", 30000, 18840)
    val panorama = obra("panorama", 6000, 2000)
    val gioconda = obra("gioconda", 7479, 11146)
    val armino = obra("armino", 30894, 41545)
    val arnolfini = obra("arnolfini", 4386, 6000)
    val caminante = obra("caminante", 2327, 2980)
    val tondo = obra("tondo", 2016, 2024, forma = Forma.REDONDA)
    val ovalo = obra("ovalo", 1500, 2000, forma = Forma.OVALADA)
    val sinFicha = obra("sinficha", 3000, 2000, ficha = null)
    val ancha16x9 = obra("16x9", 3840, 2160)
    val tresPorDos = obra("3x2", 3000, 2000)

    val variadas = listOf(
        monet, venus, panorama, gioconda, armino, arnolfini, caminante, tondo, ovalo, sinFicha, ancha16x9, tresPorDos,
    )

    fun tema(t: Tema) = RegistroTemas.de(t)

    fun pedido(
        obras: List<Obra>,
        tema: Tema = Tema.GALERIA_MODERNA,
        modo: Modo = Modo.CLARO,
        prefs: Preferencias = Preferencias(),
        elegida: Int = 0,
        pantallaCompleta: Boolean = false,
        forzarExplicacion: Boolean = false,
        nombreSala: String? = "Impresionismo · 3 / 24",
        contador: String = "3 / 24",
    ) = PedidoEscena(
        obras, elegida, tema(tema), modo, prefs.copy(tema = tema), pantallaCompleta, forzarExplicacion, nombreSala, contador,
    )

    fun pedido(obra: Obra, tema: Tema = Tema.GALERIA_MODERNA, modo: Modo = Modo.CLARO, prefs: Preferencias = Preferencias(),
               pantallaCompleta: Boolean = false, forzarExplicacion: Boolean = false, nombreSala: String? = "Impresionismo · 3 / 24") =
        pedido(listOf(obra), tema, modo, prefs, 0, pantallaCompleta, forzarExplicacion, nombreSala)
}
