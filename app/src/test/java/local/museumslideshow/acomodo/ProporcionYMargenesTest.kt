package local.museumslideshow.acomodo

import local.museumslideshow.ajustes.CartelObra
import local.museumslideshow.ajustes.ExplicacionEnPantalla
import local.museumslideshow.ajustes.MarcoElegido
import local.museumslideshow.ajustes.Modo
import local.museumslideshow.ajustes.NombreSala
import local.museumslideshow.ajustes.Pared
import local.museumslideshow.ajustes.Preferencias
import local.museumslideshow.ajustes.Tamano
import local.museumslideshow.ajustes.Tema
import local.museumslideshow.catalogo.Forma
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/** Criterios del SPEC-0: ninguna obra se deforma (+-1%) y el texto nunca pisa la obra ni sale del margen. */
class ProporcionYMargenesTest {
    private val med = MedidorFalso()
    private val S = Constantes.SAFE

    private fun efectivamenteCompleta(p: PedidoEscena) =
        (p.prefs.tamano == Tamano.PANTALLA_COMPLETA) != p.pantallaCompleta

    private fun verificar(p: PedidoEscena, d: Disposicion, etiqueta: String) {
        // proporción
        for (c in d.obras) {
            val err = if (c.forma == Forma.REDONDA) abs(c.interior.ancho - c.interior.alto) / c.interior.ancho
            else abs((c.interior.ancho / c.interior.alto) / (c.obra.ancho.toFloat() / c.obra.alto) - 1f)
            assertTrue("$etiqueta: proporción de ${c.obra.driveId} fuera de 1% ($err)", err <= 0.01f)
        }
        if (efectivamenteCompleta(p)) {
            assertTrue("$etiqueta: pantalla completa sin textos", d.textos.isEmpty())
            for (c in d.obras) assertTrue(
                "$etiqueta: dentro de la pantalla",
                c.exterior.x >= -0.5f && c.exterior.y >= -0.5f && c.exterior.derecha <= 1920.5f && c.exterior.abajo <= 1080.5f,
            )
            return
        }
        for (c in d.obras) assertTrue(
            "$etiqueta: obra dentro del margen ${c.exterior}",
            c.exterior.x >= S.x - 0.5f && c.exterior.y >= S.y - 0.5f &&
                c.exterior.derecha <= S.derecha + 0.5f && c.exterior.abajo <= S.abajo + 0.5f,
        )
        for (t in d.textos) {
            assertTrue(
                "$etiqueta: texto dentro del margen $t",
                t.rect.x >= S.x - 0.5f && t.rect.y >= S.y - 0.5f && t.rect.derecha <= S.derecha + 0.5f && t.rect.abajo <= S.abajo + 0.5f,
            )
            if (t is Texto.Chip) {
                assertTrue("$etiqueta: el cuadrito está sobre la obra", d.obras.any {
                    t.rect.x >= it.exterior.x - 0.5f && t.rect.derecha <= it.exterior.derecha + 0.5f &&
                        t.rect.y >= it.exterior.y - 0.5f && t.rect.abajo <= it.exterior.abajo + 0.5f
                })
            } else {
                for (c in d.obras) assertTrue("$etiqueta: $t pisa la obra", !t.rect.pisa(c.exterior))
            }
        }
        val fijos = d.textos.filter { it !is Texto.Chip }
        for (i in fijos.indices) for (j in i + 1 until fijos.size) {
            assertTrue("$etiqueta: ${fijos[i]} pisa ${fijos[j]}", !fijos[i].rect.pisa(fijos[j].rect))
        }
        for (i in d.obras.indices) for (j in i + 1 until d.obras.size) {
            assertTrue("$etiqueta: dos obras se pisan", !d.obras[i].exterior.pisa(d.obras[j].exterior))
        }
        if (p.tema.esLibro) {
            val l = d.libro!!
            for (c in d.obras) assertTrue(
                "$etiqueta: obra dentro de su página",
                c.exterior.x >= l.obraArea.x - 0.5f && c.exterior.derecha <= l.obraArea.derecha + 0.5f &&
                    c.exterior.y >= l.obraArea.y - 0.5f && c.exterior.abajo <= l.obraArea.abajo + 0.5f,
            )
        }
    }

    @Test
    fun todosLosTemasTamanosYParedesRespetanProporcionMargenYTextos() {
        var casos = 0
        for (tema in Tema.values()) for (modo in listOf(Modo.CLARO, Modo.OSCURO)) for (tam in Tamano.values()) for (pared in Pared.values()) {
            for (marco in listOf(MarcoElegido.DEL_TEMA, MarcoElegido.FIJO("madera"), MarcoElegido.AL_AZAR)) {
                val prefs = Preferencias(tema = tema, tamano = tam, pared = pared, marco = marco)
                val td = Datos.tema(tema)
                val escenas = ArmadoDeEscenas.armar(Datos.variadas + Datos.variadas.reversed(), pared, td)
                for (obras in escenas) for (ok in listOf(false, true)) for (forzar in listOf(false, true)) {
                    val p = PedidoEscena(
                        obras, obras.size - 1, td, modo, prefs, ok, forzar, "Retratos y paisajes · 5 / 40", "5 / 40",
                    )
                    val d = Acomodador.acomodar(p, med)
                    verificar(p, d, "$tema/$modo/$tam/$pared/$marco/ok=$ok/forzar=$forzar/${obras.map { it.driveId }}")
                    casos++
                }
            }
        }
        assertTrue("se probaron casos ($casos)", casos > 1000)
    }

    @Test
    fun variantesDeAjustesDeTextoTambienRespetanMargenes() {
        val ajustes = listOf(
            Preferencias(cartelObra = CartelObra.NUNCA),
            Preferencias(explicacionEnPantalla = ExplicacionEnPantalla.NUNCA),
            Preferencias(nombreSala = NombreSala.SOLO_CONTADOR),
            Preferencias(nombreSala = NombreSala.NUNCA),
            Preferencias(pantallaCompletaConMarco = true, tamano = Tamano.PANTALLA_COMPLETA),
        )
        for (a in ajustes) for (tema in Tema.values()) for (tam in listOf(Tamano.NORMAL, Tamano.GRANDE, Tamano.MAXIMO)) {
            val td = Datos.tema(tema)
            for (obras in ArmadoDeEscenas.armar(Datos.variadas, a.pared, td)) {
                for (sala in listOf("Impresionismo · 3 / 24", null)) {
                    val p = PedidoEscena(obras, 0, td, Modo.OSCURO, a.copy(tema = tema, tamano = if (a.tamano == Tamano.PANTALLA_COMPLETA) a.tamano else tam), false, true, sala, "3 / 24")
                    verificar(p, Acomodador.acomodar(p, med), "$a/$tema/$tam/$sala")
                }
            }
        }
    }

    @Test
    fun obrasTipicasEnGrandeNoUsanElCuadritoSiHayLugar() {
        // En Grande hay lugar al costado o abajo para cualquier proporción razonable.
        for (o in listOf(Datos.monet, Datos.gioconda, Datos.venus, Datos.panorama, Datos.armino)) {
            val d = Acomodador.acomodar(Datos.pedido(o), med)
            assertTrue("${o.driveId} lleva cartel", d.textos.any { it is Texto.Cartel })
            assertEquals(0, d.textos.count { it is Texto.Chip })
        }
    }
}
