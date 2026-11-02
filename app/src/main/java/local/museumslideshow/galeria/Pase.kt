package local.museumslideshow.galeria

import kotlin.random.Random
import local.museumslideshow.acomodo.ArmadoDeEscenas
import local.museumslideshow.ajustes.Orden
import local.museumslideshow.ajustes.Preferencias
import local.museumslideshow.catalogo.Galeria
import local.museumslideshow.catalogo.Obra
import local.museumslideshow.temas.TemaDef

/** Una escena del pase: una obra sola o una Pared de 2-3, con su lugar dentro de la galería (`posicion` desde 1). */
data class EscenaDef(
    val galeriaId: String,
    val galeriaTitulo: String,
    val obras: List<Obra>,
    val posicion: Int,
    val total: Int,
)

/**
 * El orden del pase: qué escenas hay, cuál toca y si está en pausa. Lógica pura (sin Android ni tiempo):
 * el reloj lo maneja quien lo usa con [debeAvanzar].
 */
class Pase private constructor(val escenas: List<EscenaDef>, indiceInicial: Int) {
    var indice: Int = indiceInicial.coerceIn(0, escenas.size - 1)
        private set
    var pausado: Boolean = false
        private set

    val actual: EscenaDef get() = escenas[indice]

    /** El índice que daría moverse `delta` escenas (con vuelta), sin moverse. */
    fun indiceRelativo(delta: Int): Int = Math.floorMod(indice + delta, escenas.size)

    fun mover(delta: Int): Int {
        indice = indiceRelativo(delta)
        return indice
    }

    fun irA(i: Int) {
        indice = i.coerceIn(0, escenas.size - 1)
    }

    fun pausar() { pausado = true }
    fun seguir() { pausado = false }
    fun alternarPausa(): Boolean {
        pausado = !pausado
        return pausado
    }

    companion object {
        /** La galería elegida (por id de carpeta o por título); si no existe o es null, todas, en el orden del catálogo. */
        fun galeriasElegidas(galerias: List<Galeria>, eleccion: String?): List<Galeria> {
            if (eleccion.isNullOrBlank()) return galerias
            val porId = galerias.filter { it.driveId == eleccion }
            val una = porId.ifEmpty { galerias.filter { it.titulo.equals(eleccion, ignoreCase = true) } }
            return una.ifEmpty { galerias }
        }

        /**
         * Arma el pase. `semilla` fija la mezcla (mismo valor = mismo orden dentro de la sesión).
         * Si se da `indiceSolicitado` arranca ahí; si no, en la escena que tenga `obraActual`; si no, en la primera.
         * Devuelve null si no hay ninguna obra.
         */
        fun armar(
            galerias: List<Galeria>,
            prefs: Preferencias,
            tema: TemaDef,
            semilla: Long,
            obraActual: String? = null,
            indiceSolicitado: Int? = null,
        ): Pase? {
            val escenas = ArrayList<EscenaDef>()
            for (g in galeriasElegidas(galerias, prefs.galeria)) {
                if (g.obras.isEmpty()) continue
                val obras = if (prefs.orden == Orden.MEZCLAR) g.obras.shuffled(Random(semilla xor g.driveId.hashCode().toLong()))
                else g.obras
                val grupos = ArmadoDeEscenas.armar(obras, prefs.pared, tema)
                grupos.forEachIndexed { i, o -> escenas += EscenaDef(g.driveId, g.titulo, o, i + 1, grupos.size) }
            }
            if (escenas.isEmpty()) return null
            val porObra = obraActual?.let { id -> escenas.indexOfFirst { e -> e.obras.any { it.driveId == id } } }?.takeIf { it >= 0 }
            return Pase(escenas, indiceSolicitado ?: porObra ?: 0)
        }

        /** ¿Ya toca pasar a la escena siguiente? (nunca en pausa). */
        fun debeAvanzar(msEnEscena: Long, tiempoMs: Long, pausado: Boolean): Boolean = !pausado && msEnEscena >= tiempoMs
    }
}
