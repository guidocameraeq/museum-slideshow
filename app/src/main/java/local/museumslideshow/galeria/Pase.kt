package local.museumslideshow.galeria

import kotlin.random.Random
import local.museumslideshow.acomodo.ArmadoDeEscenas
import local.museumslideshow.ajustes.Orden
import local.museumslideshow.ajustes.Preferencias
import local.museumslideshow.ajustes.estaTildada
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

    /** Las obras de todo el pase, en orden (con las repetidas de una Pared en el orden en que cuelgan). */
    fun ordenDeObras(): List<Obra> = escenas.flatMap { it.obras }

    /** Dónde está, dentro de [ordenDeObras], la primera obra de la escena actual. */
    fun posicionDeObra(): Int = (0 until indice).sumOf { escenas[it].obras.size }

    /**
     * El índice que daría moverse `delta` escenas contando SOLO las que están `lista` (con vuelta). Una escena no lista se saltea
     * sin perder el contador (cada escena sigue siendo la n-ésima de su galería). Si no hay ninguna lista, no se mueve.
     */
    fun indiceSaltando(delta: Int, lista: (EscenaDef) -> Boolean): Int {
        if (delta == 0) return indice
        val paso = if (delta > 0) 1 else -1
        val n = escenas.size
        var i = indice
        repeat(Math.abs(delta)) {
            var intentos = 0
            do {
                i = Math.floorMod(i + paso, n)
                intentos++
            } while (!lista(escenas[i]) && intentos < n)
            if (!lista(escenas[i])) return indice
        }
        return i
    }

    /** Se mueve `delta` escenas saltando las que no están listas. Devuelve el índice nuevo. */
    fun moverSaltando(delta: Int, lista: (EscenaDef) -> Boolean): Int {
        indice = indiceSaltando(delta, lista)
        return indice
    }

    /** Si la escena actual no está lista pasa a la próxima que sí. false si no hay ninguna lista (no se mueve). */
    fun asegurarLista(lista: (EscenaDef) -> Boolean): Boolean {
        if (lista(actual)) return true
        val j = indiceSaltando(1, lista)
        if (j == indice) return false
        indice = j
        return true
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
        /** Una semilla para toda la sesión: la mezcla es la misma para el pase que se ve y para la reserva que lo prepara. */
        val SEMILLA_SESION: Long = System.nanoTime()

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
            for (g in galeriasElegidas(galerias.filter { prefs.estaTildada(it.driveId) }, prefs.galeria)) {
                if (g.obras.isEmpty()) continue
                val conMedidas = g.obras.filter { it.conMedidas }
                if (conMedidas.isEmpty()) continue
                val obras = if (prefs.orden == Orden.MEZCLAR) conMedidas.shuffled(Random(semilla xor g.driveId.hashCode().toLong()))
                else conMedidas
                val grupos = ArmadoDeEscenas.armar(obras, prefs.pared, tema)
                grupos.forEachIndexed { i, o -> escenas += EscenaDef(g.driveId, g.titulo, o, i + 1, grupos.size) }
            }
            if (escenas.isEmpty()) return null
            val porObra = obraActual?.let { id -> escenas.indexOfFirst { e -> e.obras.any { it.driveId == id } } }?.takeIf { it >= 0 }
            return Pase(escenas, indiceSolicitado ?: porObra ?: 0)
        }

        /** Las obras del pase que todavía no tienen medidas (Drive no las dio y no están bajadas): no entran hasta bajarse. */
        fun obrasSinMedidas(galerias: List<Galeria>, prefs: Preferencias): List<Obra> =
            galeriasElegidas(galerias.filter { prefs.estaTildada(it.driveId) }, prefs.galeria)
                .flatMap { g -> g.obras.filter { !it.conMedidas && !it.bajada } }

        /** ¿Ya toca pasar a la escena siguiente? (nunca en pausa). */
        fun debeAvanzar(msEnEscena: Long, tiempoMs: Long, pausado: Boolean): Boolean = !pausado && msEnEscena >= tiempoMs
    }
}
