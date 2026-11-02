package local.museumslideshow.acomodo

import local.museumslideshow.ajustes.Pared
import local.museumslideshow.catalogo.Forma
import local.museumslideshow.catalogo.Obra
import local.museumslideshow.temas.TemaDef

/** Agrupa obras en escenas: solas o Paredes de 2-3 verticales consecutivas (SPEC-0, supuestos BAJO). */
object ArmadoDeEscenas {
    const val LIMITE_VERTICAL = 0.8f

    /** Vertical = ancho/alto menor a 0,8; las redondas no cuentan. */
    fun esVertical(o: Obra): Boolean =
        o.ficha?.forma != Forma.REDONDA && o.alto > 0 && o.ancho.toFloat() / o.alto < LIMITE_VERTICAL

    fun armar(obras: List<Obra>, pared: Pared, tema: TemaDef): List<List<Obra>> {
        val maximo = pared.maximo
        if (!tema.admitePared || tema.esLibro || maximo < 2) return obras.map { listOf(it) }
        val escenas = mutableListOf<List<Obra>>()
        var i = 0
        while (i < obras.size) {
            if (!esVertical(obras[i])) {
                escenas += listOf(obras[i]); i++; continue
            }
            var j = i
            while (j < obras.size && j - i < maximo && esVertical(obras[j])) j++
            escenas += obras.subList(i, j).toList()
            i = j
        }
        return escenas
    }
}
