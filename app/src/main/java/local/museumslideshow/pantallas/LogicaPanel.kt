package local.museumslideshow.pantallas

import local.museumslideshow.ajustes.Tema
import local.museumslideshow.catalogo.Galeria

// La Barra lateral como lógica pura (sin Android ni Compose): el estado del panel, sus teclas, la lista de galerías
// y el cierre automático. Lo dibuja PanelLateral.kt y lo maneja GaleriaEnPantalla.

/** Los renglones del menú del panel, en el orden en que se ven. */
enum class Renglon { GALERIA, TEMA, PANTALLA_COMPLETA, EXPLICACION, AJUSTES, SINCRONIZAR }

/** Dónde está el panel: en el menú o adentro de una de las dos sublistas. */
enum class NivelPanel { MENU, GALERIAS, TEMAS }

/** Un renglón de la sublista Galería. Los encabezados de grupo se ven pero el foco los salta. */
sealed interface ItemGalerias {
    val elegible: Boolean get() = true

    data class Todas(val obras: Int) : ItemGalerias
    data class Encabezado(val titulo: String) : ItemGalerias {
        override val elegible: Boolean get() = false
    }
    data class Una(val id: String, val titulo: String, val obras: Int) : ItemGalerias
}

/** Estado del panel. `foco` = renglón del menú, o posición en la sublista (en `items` o en los temas). */
data class EstadoPanel(val nivel: NivelPanel = NivelPanel.MENU, val foco: Int = 0) {
    /** El tema que se está probando en vivo: el que tiene el foco en la sublista Tema (null fuera de ella). */
    val temaEnPrueba: Tema? get() = if (nivel == NivelPanel.TEMAS) Tema.values().getOrNull(foco) else null
}

/** Lo que el panel necesita saber de afuera para mover el foco. `elegida` = posición de la galería puesta en `items`. */
class ContextoPanel(val items: List<ItemGalerias>, val elegida: Int, val temaGuardado: Tema)

sealed interface EventoPanel {
    /** Atrás desde el menú: cierra todo (el tema en prueba se descarta). */
    object Cerrar : EventoPanel

    /** OK sobre una galería (`null` = Todas): cierra todo y la galería cambia. */
    data class ElegirGaleria(val id: String?) : EventoPanel

    /** OK sobre un tema: lo guarda y cierra todo. */
    data class ElegirTema(val tema: Tema) : EventoPanel

    object AlternarPantallaCompleta : EventoPanel
    object AlternarExplicacion : EventoPanel
    object AbrirAjustes : EventoPanel
    object Sincronizar : EventoPanel
}

data class PasoPanel(val estado: EstadoPanel, val evento: EventoPanel? = null)

object NavegacionPanel {
    private val RENGLONES = Renglon.values()

    /**
     * Una tecla sobre el panel. En el menú Arriba y Abajo dan la vuelta (son solo seis renglones); en las sublistas frenan
     * en las puntas (la de galerías puede ser larga: un salto de punta a punta desorienta). Derecha entra en un renglón
     * con ▸, Izquierda en una sublista vuelve al menú. Atrás: de una sublista al menú, del menú cierra.
     */
    fun paso(e0: EstadoPanel, tecla: Tecla, ctx: ContextoPanel): PasoPanel {
        val e = normalizar(e0, ctx)
        return when (e.nivel) {
            NivelPanel.MENU -> pasoMenu(e, tecla, ctx)
            NivelPanel.GALERIAS -> pasoGalerias(e, tecla, ctx)
            NivelPanel.TEMAS -> pasoTemas(e, tecla, ctx)
        }
    }

    /** Deja el foco en algo válido (el catálogo puede cambiar con el panel abierto). */
    fun normalizar(e: EstadoPanel, ctx: ContextoPanel): EstadoPanel = when (e.nivel) {
        NivelPanel.MENU -> e.copy(foco = e.foco.coerceIn(0, RENGLONES.lastIndex))
        NivelPanel.TEMAS -> e.copy(foco = e.foco.coerceIn(0, Tema.values().lastIndex))
        NivelPanel.GALERIAS -> {
            val items = ctx.items
            val f = e.foco.coerceIn(0, (items.size - 1).coerceAtLeast(0))
            val ok = if (items.isEmpty() || items[f].elegible) f
            else (siguienteElegible(items, f, 1) ?: siguienteElegible(items, f, -1) ?: 0)
            e.copy(foco = ok)
        }
    }

    /** El estado al entrar en una sublista: el foco cae en lo que está puesto. */
    fun entrar(nivel: NivelPanel, ctx: ContextoPanel): EstadoPanel = normalizar(
        when (nivel) {
            NivelPanel.MENU -> EstadoPanel()
            NivelPanel.GALERIAS -> EstadoPanel(nivel, ctx.elegida)
            NivelPanel.TEMAS -> EstadoPanel(nivel, ctx.temaGuardado.ordinal)
        },
        ctx,
    )

    private fun alMenu(nivel: NivelPanel): EstadoPanel =
        EstadoPanel(NivelPanel.MENU, if (nivel == NivelPanel.GALERIAS) Renglon.GALERIA.ordinal else Renglon.TEMA.ordinal)

    private fun pasoMenu(e: EstadoPanel, tecla: Tecla, ctx: ContextoPanel): PasoPanel {
        val r = RENGLONES[e.foco]
        return when (tecla) {
            Tecla.ATRAS -> PasoPanel(EstadoPanel(), EventoPanel.Cerrar)
            Tecla.ARRIBA -> PasoPanel(e.copy(foco = Math.floorMod(e.foco - 1, RENGLONES.size)))
            Tecla.ABAJO -> PasoPanel(e.copy(foco = Math.floorMod(e.foco + 1, RENGLONES.size)))
            Tecla.IZQUIERDA -> PasoPanel(e)
            Tecla.DERECHA -> when (r) {
                Renglon.GALERIA -> PasoPanel(entrar(NivelPanel.GALERIAS, ctx))
                Renglon.TEMA -> PasoPanel(entrar(NivelPanel.TEMAS, ctx))
                Renglon.AJUSTES -> PasoPanel(e, EventoPanel.AbrirAjustes)
                else -> PasoPanel(e)
            }
            Tecla.OK -> when (r) {
                Renglon.GALERIA -> PasoPanel(entrar(NivelPanel.GALERIAS, ctx))
                Renglon.TEMA -> PasoPanel(entrar(NivelPanel.TEMAS, ctx))
                Renglon.PANTALLA_COMPLETA -> PasoPanel(e, EventoPanel.AlternarPantallaCompleta)
                Renglon.EXPLICACION -> PasoPanel(e, EventoPanel.AlternarExplicacion)
                Renglon.AJUSTES -> PasoPanel(e, EventoPanel.AbrirAjustes)
                Renglon.SINCRONIZAR -> PasoPanel(e, EventoPanel.Sincronizar)
            }
        }
    }

    private fun pasoGalerias(e: EstadoPanel, tecla: Tecla, ctx: ContextoPanel): PasoPanel = when (tecla) {
        Tecla.ATRAS, Tecla.IZQUIERDA -> PasoPanel(alMenu(e.nivel))
        Tecla.ARRIBA -> PasoPanel(e.copy(foco = siguienteElegible(ctx.items, e.foco, -1) ?: e.foco))
        Tecla.ABAJO -> PasoPanel(e.copy(foco = siguienteElegible(ctx.items, e.foco, 1) ?: e.foco))
        Tecla.DERECHA -> PasoPanel(e)
        Tecla.OK -> when (val item = ctx.items.getOrNull(e.foco)) {
            is ItemGalerias.Todas -> PasoPanel(EstadoPanel(), EventoPanel.ElegirGaleria(null))
            is ItemGalerias.Una -> PasoPanel(EstadoPanel(), EventoPanel.ElegirGaleria(item.id))
            else -> PasoPanel(e)
        }
    }

    private fun pasoTemas(e: EstadoPanel, tecla: Tecla, ctx: ContextoPanel): PasoPanel = when (tecla) {
        Tecla.ATRAS, Tecla.IZQUIERDA -> PasoPanel(alMenu(e.nivel))   // el tema en prueba se descarta: sale de la sublista
        Tecla.ARRIBA -> PasoPanel(e.copy(foco = (e.foco - 1).coerceAtLeast(0)))
        Tecla.ABAJO -> PasoPanel(e.copy(foco = (e.foco + 1).coerceAtMost(Tema.values().lastIndex)))
        Tecla.DERECHA -> PasoPanel(e)
        Tecla.OK -> PasoPanel(EstadoPanel(), EventoPanel.ElegirTema(Tema.values()[e.foco]))
    }

    /** La próxima posición con foco posible desde `desde` en la dirección `dir` (±1), saltando encabezados; null si no hay. */
    fun siguienteElegible(items: List<ItemGalerias>, desde: Int, dir: Int): Int? {
        var i = desde + dir
        while (i in items.indices) {
            if (items[i].elegible) return i
            i += dir
        }
        return null
    }

    /**
     * Cuántas galerías quedan fuera de la vista (no cuentan los encabezados): `primero` y `ultimo` son los renglones de la
     * lista que se ven (los parciales cuentan como vistos).
     */
    fun fueraDeVista(items: List<ItemGalerias>, primero: Int, ultimo: Int): Pair<Int, Int> {
        val arriba = items.withIndex().count { (i, x) -> i < primero && x.elegible }
        val abajo = items.withIndex().count { (i, x) -> i > ultimo && x.elegible }
        return arriba to abajo
    }
}

/** Cómo se arma la lista de la sublista Galería. */
object ListaGaleriasPanel {
    /**
     * "Todas" primero; después las galerías `tildadas` (en el orden del catálogo). Las que cuelgan de un grupo van juntas
     * bajo un encabezado con el nombre de ese grupo (los grupos, en el orden en que aparecen); las sueltas, sin encabezado,
     * van enseguida de "Todas". `tituloGrupo(id)` da el nombre del grupo (null = no se sabe: la galería queda suelta).
     */
    fun armar(tildadas: List<Galeria>, tituloGrupo: (String) -> String?): List<ItemGalerias> {
        val salida = ArrayList<ItemGalerias>()
        salida += ItemGalerias.Todas(tildadas.sumOf { it.obras.size })
        val sueltas = ArrayList<Galeria>()
        val porGrupo = LinkedHashMap<String, MutableList<Galeria>>()
        for (g in tildadas) {
            val padre = g.padreId
            if (padre == null || tituloGrupo(padre) == null) sueltas += g else porGrupo.getOrPut(padre) { ArrayList() } += g
        }
        for (g in sueltas) salida += ItemGalerias.Una(g.driveId, g.titulo, g.obras.size)
        for ((padre, lista) in porGrupo) {
            salida += ItemGalerias.Encabezado(tituloGrupo(padre) ?: "")
            for (g in lista) salida += ItemGalerias.Una(g.driveId, g.titulo, g.obras.size)
        }
        return salida
    }

    /** La posición de la galería puesta (por id o por título, como acepta el pase). Sin galería puesta, o si no está: "Todas" (0). */
    fun posicionElegida(items: List<ItemGalerias>, eleccion: String?): Int {
        if (eleccion == null) return 0
        val i = items.indexOfFirst { it is ItemGalerias.Una && (it.id == eleccion || it.titulo.equals(eleccion, ignoreCase = true)) }
        return if (i >= 0) i else 0
    }
}

/**
 * El cierre automático: se cierra el panel si pasó `limiteMs` desde la última tecla. `reloj` da la hora en ms (se inyecta
 * para probar). `limiteMs` null = nunca.
 */
class CierreAutomatico(private val limiteMs: Long?, private val reloj: () -> Long) {
    private var desde = reloj()

    /** Cualquier tecla (menos el volumen) vuelve a empezar la cuenta. */
    fun reiniciar() {
        desde = reloj()
    }

    fun vencido(): Boolean = limiteMs != null && reloj() - desde >= limiteMs

    /** Cuánto falta, de 1 (recién empieza) a 0 (se cierra); null si nunca se cierra. */
    fun restante(): Float? {
        val l = limiteMs ?: return null
        return (1f - (reloj() - desde).toFloat() / l).coerceIn(0f, 1f)
    }
}
