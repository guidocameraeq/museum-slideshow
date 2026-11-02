package local.museumslideshow.pantallas

import local.museumslideshow.ajustes.VistaGalerias
import local.museumslideshow.catalogo.Arbol
import local.museumslideshow.catalogo.FilaLista

/** Dónde puede estar el foco en la pantalla Galerías. */
enum class ZonaGalerias {
    /** El selector Tarjetas | Lista (índice 0 o 1). */
    SELECTOR,

    /** El botón "Tildar todas / Destildar todas" (solo adentro de un grupo, vista Tarjetas). */
    TODAS,

    /** Una tarjeta (vista Tarjetas) o una fila (vista Lista): el índice dentro de lo que se ve. */
    ITEMS,

    /** La fila "Galerías nuevas" del pie. */
    NUEVAS,
}

data class FocoGalerias(val zona: ZonaGalerias, val indice: Int = 0)

/**
 * Todo lo que la pantalla Galerías recuerda mientras está abierta: los grupos por los que se entró (`pila`, de afuera hacia
 * adentro, para las migas), el foco, los grupos abiertos de la vista Lista y la primera fila que se ve (el scroll).
 */
data class EstadoGalerias(
    val pila: List<String> = emptyList(),
    val foco: FocoGalerias = FocoGalerias(ZonaGalerias.ITEMS, 0),
    val abiertos: Set<String> = emptySet(),
    val primeraFila: Int = 0,
)

/** Lo que una tecla pide hacer fuera del estado (guardar un ajuste o salir). */
sealed interface EfectoGalerias {
    /** Atrás desde el primer nivel: vuelve a Inicio. */
    object Salir : EfectoGalerias

    data class CambiarVista(val vista: VistaGalerias) : EfectoGalerias

    /** Tilda (o destilda, si ya estaba todo tildado) una galería o un grupo entero. */
    data class Alternar(val id: String) : EfectoGalerias

    /** "Galerías nuevas": `dir` -1 / +1, con `envolver` al apretar OK. */
    data class CambiarNuevas(val dir: Int, val envolver: Boolean) : EfectoGalerias
}

data class PasoGalerias(val estado: EstadoGalerias, val efecto: EfectoGalerias? = null)

/** La navegación de la pantalla Galerías como función pura (sin Android ni Compose). */
object NavegacionGalerias {
    const val COLUMNAS = 4

    /** Cuántas filas de tarjetas se ven a la vez y cuántas filas de la lista. */
    const val FILAS_TARJETAS = 2
    const val FILAS_LISTA = 11

    /** El grupo donde se está (null = el primer nivel). */
    fun grupoActual(e: EstadoGalerias): String? = e.pila.lastOrNull()

    /** Las tarjetas que se ven ahora: lo que hay adentro del grupo actual. */
    fun tarjetas(arbol: Arbol, e: EstadoGalerias) = arbol.hijos(grupoActual(e))

    /** La primera fila visible para que la fila `fila` se vea, moviendo lo mínimo desde `actual`. */
    fun primeraFilaVisible(actual: Int, fila: Int, visibles: Int, total: Int): Int {
        val maxPrimera = (total - visibles).coerceAtLeast(0)
        val f = when {
            fila < actual -> fila
            fila >= actual + visibles -> fila - visibles + 1
            else -> actual
        }
        return f.coerceIn(0, maxPrimera)
    }

    fun paso(arbol: Arbol, e0: EstadoGalerias, tecla: Tecla, vista: VistaGalerias, filas: List<FilaLista>): PasoGalerias {
        val e = normalizar(arbol, e0, vista, filas)
        val paso = when (vista) {
            VistaGalerias.TARJETAS -> pasoTarjetas(arbol, e, tecla)
            VistaGalerias.LISTA -> pasoLista(e, tecla, filas)
        }
        val (visibles, total, fila) = when (vista) {
            VistaGalerias.TARJETAS -> Triple(FILAS_TARJETAS, (tarjetas(arbol, paso.estado).size + COLUMNAS - 1) / COLUMNAS, paso.estado.foco.indice / COLUMNAS)
            VistaGalerias.LISTA -> Triple(FILAS_LISTA, filas.size, paso.estado.foco.indice)
        }
        val e2 = paso.estado
        val primera = if (e2.foco.zona == ZonaGalerias.ITEMS) primeraFilaVisible(e2.primeraFila, fila, visibles, total)
        else primeraFilaVisible(e2.primeraFila, e2.primeraFila, visibles, total)
        // Al cambiar de vista la lista o la grilla arrancan desde arriba.
        val cambiaVista = paso.efecto is EfectoGalerias.CambiarVista && paso.efecto.vista != vista
        return paso.copy(estado = e2.copy(primeraFila = if (cambiaVista) 0 else primera))
    }

    /** Deja el estado consistente con lo que hay (un foco fuera de rango, un grupo que ya no existe). */
    fun normalizar(arbol: Arbol, e: EstadoGalerias, vista: VistaGalerias, filas: List<FilaLista>): EstadoGalerias {
        val pila = e.pila.takeWhile { arbol.esGrupo(it) }
        val base = if (pila.size == e.pila.size) e else e.copy(pila = pila)
        val cantidad = if (vista == VistaGalerias.TARJETAS) tarjetas(arbol, base).size else filas.size
        val f = base.foco
        val foco = when (f.zona) {
            // Adentro de un grupo no hay selector (es del primer nivel): arriba de todo está "Tildar todas".
            ZonaGalerias.SELECTOR ->
                if (vista == VistaGalerias.TARJETAS && pila.isNotEmpty()) FocoGalerias(ZonaGalerias.TODAS)
                else f.copy(indice = f.indice.coerceIn(0, 1))
            ZonaGalerias.TODAS -> if (vista == VistaGalerias.TARJETAS && pila.isNotEmpty()) f else FocoGalerias(ZonaGalerias.SELECTOR, indiceVista(vista))
            ZonaGalerias.ITEMS -> if (cantidad == 0) FocoGalerias(ZonaGalerias.NUEVAS) else f.copy(indice = f.indice.coerceIn(0, cantidad - 1))
            ZonaGalerias.NUEVAS -> f
        }
        return base.copy(foco = foco)
    }

    private fun indiceVista(v: VistaGalerias) = if (v == VistaGalerias.TARJETAS) 0 else 1

    // ------------------------------------------------------------------------------------- Tarjetas

    private fun pasoTarjetas(arbol: Arbol, e: EstadoGalerias, tecla: Tecla): PasoGalerias {
        val hijos = tarjetas(arbol, e)
        val n = hijos.size
        val enGrupo = e.pila.isNotEmpty()
        val f = e.foco
        fun a(zona: ZonaGalerias, i: Int = 0) = PasoGalerias(e.copy(foco = FocoGalerias(zona, i)))
        val arriba = if (enGrupo) a(ZonaGalerias.TODAS) else a(ZonaGalerias.SELECTOR, 0)
        val primeroOPie = if (n > 0) a(ZonaGalerias.ITEMS, 0) else a(ZonaGalerias.NUEVAS)
        return when (f.zona) {
            ZonaGalerias.SELECTOR -> when (tecla) {
                Tecla.ATRAS -> atras(arbol, e)
                Tecla.IZQUIERDA -> a(ZonaGalerias.SELECTOR, 0)
                Tecla.DERECHA -> a(ZonaGalerias.SELECTOR, 1)
                Tecla.ABAJO -> if (enGrupo) a(ZonaGalerias.TODAS) else primeroOPie
                Tecla.ARRIBA -> PasoGalerias(e)
                Tecla.OK -> PasoGalerias(e, EfectoGalerias.CambiarVista(if (f.indice == 0) VistaGalerias.TARJETAS else VistaGalerias.LISTA))
            }
            ZonaGalerias.TODAS -> when (tecla) {
                Tecla.ATRAS -> atras(arbol, e)
                Tecla.ARRIBA, Tecla.IZQUIERDA, Tecla.DERECHA -> PasoGalerias(e)
                Tecla.ABAJO -> primeroOPie
                Tecla.OK -> PasoGalerias(e, EfectoGalerias.Alternar(grupoActual(e) ?: return PasoGalerias(e)))
            }
            ZonaGalerias.ITEMS -> {
                val i = f.indice
                when (tecla) {
                    Tecla.ATRAS -> atras(arbol, e)
                    Tecla.IZQUIERDA -> if (i % COLUMNAS > 0) a(ZonaGalerias.ITEMS, i - 1) else PasoGalerias(e)
                    Tecla.DERECHA -> if (i % COLUMNAS < COLUMNAS - 1 && i + 1 < n) a(ZonaGalerias.ITEMS, i + 1) else PasoGalerias(e)
                    Tecla.ARRIBA -> if (i >= COLUMNAS) a(ZonaGalerias.ITEMS, i - COLUMNAS) else arriba
                    Tecla.ABAJO -> when {
                        i + COLUMNAS < n -> a(ZonaGalerias.ITEMS, i + COLUMNAS)
                        // La fila de abajo es más corta y no llega a esta columna: cae en su última tarjeta.
                        i / COLUMNAS < (n - 1) / COLUMNAS -> a(ZonaGalerias.ITEMS, n - 1)
                        else -> a(ZonaGalerias.NUEVAS)
                    }
                    Tecla.OK -> {
                        val nodo = hijos[i]
                        if (nodo.esGrupo) {
                            PasoGalerias(e.copy(pila = e.pila + nodo.id, foco = FocoGalerias(ZonaGalerias.ITEMS, 0), primeraFila = 0))
                        } else {
                            PasoGalerias(e, EfectoGalerias.Alternar(nodo.id))
                        }
                    }
                }
            }
            ZonaGalerias.NUEVAS -> when (tecla) {
                Tecla.ATRAS -> atras(arbol, e)
                Tecla.ARRIBA -> if (n > 0) a(ZonaGalerias.ITEMS, n - 1) else arriba
                Tecla.ABAJO -> PasoGalerias(e)
                Tecla.IZQUIERDA -> PasoGalerias(e, EfectoGalerias.CambiarNuevas(-1, false))
                Tecla.DERECHA -> PasoGalerias(e, EfectoGalerias.CambiarNuevas(1, false))
                Tecla.OK -> PasoGalerias(e, EfectoGalerias.CambiarNuevas(1, true))
            }
        }
    }

    /** Atrás: sale de un grupo (el foco vuelve a su tarjeta) o, en el primer nivel, de la pantalla. */
    private fun atras(arbol: Arbol, e: EstadoGalerias): PasoGalerias {
        val salida = e.pila.lastOrNull() ?: return PasoGalerias(e, EfectoGalerias.Salir)
        val nuevaPila = e.pila.dropLast(1)
        val hermanos = arbol.hijos(nuevaPila.lastOrNull())
        val i = hermanos.indexOfFirst { it.id == salida }.coerceAtLeast(0)
        val fila = i / COLUMNAS
        return PasoGalerias(
            e.copy(
                pila = nuevaPila,
                foco = FocoGalerias(ZonaGalerias.ITEMS, i),
                primeraFila = primeraFilaVisible(0, fila, FILAS_TARJETAS, (hermanos.size + COLUMNAS - 1) / COLUMNAS),
            ),
        )
    }

    // ------------------------------------------------------------------------------------- Lista

    private fun pasoLista(e: EstadoGalerias, tecla: Tecla, filas: List<FilaLista>): PasoGalerias {
        val n = filas.size
        val f = e.foco
        fun a(zona: ZonaGalerias, i: Int = 0) = PasoGalerias(e.copy(foco = FocoGalerias(zona, i)))
        return when (f.zona) {
            ZonaGalerias.SELECTOR, ZonaGalerias.TODAS -> when (tecla) {
                Tecla.ATRAS -> PasoGalerias(e, EfectoGalerias.Salir)
                Tecla.IZQUIERDA -> a(ZonaGalerias.SELECTOR, 0)
                Tecla.DERECHA -> a(ZonaGalerias.SELECTOR, 1)
                Tecla.ABAJO -> if (n > 0) a(ZonaGalerias.ITEMS, 0) else a(ZonaGalerias.NUEVAS)
                Tecla.ARRIBA -> PasoGalerias(e)
                Tecla.OK -> PasoGalerias(e, EfectoGalerias.CambiarVista(if (f.indice == 0) VistaGalerias.TARJETAS else VistaGalerias.LISTA))
            }
            ZonaGalerias.ITEMS -> {
                val i = f.indice
                val fila = filas[i]
                when (tecla) {
                    Tecla.ATRAS -> PasoGalerias(e, EfectoGalerias.Salir)
                    Tecla.ARRIBA -> if (i > 0) a(ZonaGalerias.ITEMS, i - 1) else a(ZonaGalerias.SELECTOR, 1)
                    Tecla.ABAJO -> if (i < n - 1) a(ZonaGalerias.ITEMS, i + 1) else a(ZonaGalerias.NUEVAS)
                    Tecla.DERECHA -> if (fila.esGrupo && !fila.abierto) PasoGalerias(e.copy(abiertos = e.abiertos + fila.id)) else PasoGalerias(e)
                    Tecla.IZQUIERDA -> when {
                        fila.esGrupo && fila.abierto -> PasoGalerias(e.copy(abiertos = e.abiertos - fila.id))
                        fila.nivel > 0 -> {
                            val padre = filas.indexOfFirst { it.id == fila.padreId }
                            if (padre >= 0) a(ZonaGalerias.ITEMS, padre) else PasoGalerias(e)
                        }
                        else -> PasoGalerias(e)
                    }
                    Tecla.OK -> PasoGalerias(e, EfectoGalerias.Alternar(fila.id))
                }
            }
            ZonaGalerias.NUEVAS -> when (tecla) {
                Tecla.ATRAS -> PasoGalerias(e, EfectoGalerias.Salir)
                Tecla.ARRIBA -> if (n > 0) a(ZonaGalerias.ITEMS, n - 1) else a(ZonaGalerias.SELECTOR, 1)
                Tecla.ABAJO -> PasoGalerias(e)
                Tecla.IZQUIERDA -> PasoGalerias(e, EfectoGalerias.CambiarNuevas(-1, false))
                Tecla.DERECHA -> PasoGalerias(e, EfectoGalerias.CambiarNuevas(1, false))
                Tecla.OK -> PasoGalerias(e, EfectoGalerias.CambiarNuevas(1, true))
            }
        }
    }
}
