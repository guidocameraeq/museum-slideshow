package local.museumslideshow.ajustes

import androidx.annotation.StringRes
import local.museumslideshow.R
import local.museumslideshow.temas.CatalogoColores
import local.museumslideshow.temas.CatalogoMarcos

/** Un texto de pantalla: o una frase de `strings.xml` (con sus datos) o un texto que viene de afuera (el título de una galería). */
sealed interface Etiqueta {
    data class Res(@StringRes val id: Int, val args: List<Any> = emptyList()) : Etiqueta
    data class Texto(val texto: String) : Etiqueta
}

/** Un valor posible de un ajuste de lista: cómo se llama, cuándo es el actual y cómo se aplica. */
class Opcion(
    val etiqueta: Etiqueta,
    val esActual: (Preferencias) -> Boolean,
    val aplicar: (Preferencias) -> Preferencias,
)

/** Los ajustes de la pantalla, en el orden de la tabla del SPEC-0 (y al final, la entrada que abre Estado). */
enum class IdAjuste {
    TEMA, MODO, HORA_DIA, HORA_NOCHE, PARED_CLARO, PARED_OSCURO, MARCO, TAMANO, COMPLETA_CON_MARCO, EXPLICACION, SALA,
    CALIDAD, TIEMPO, ORDEN, GALERIA, PARED, CARTEL, CREDITO, CIERRE_BARRA, TRANSICION, DURACION, SINCRONIZAR, RESERVA, ESPACIO, RITMO, CAMINO, CARPETA, CLAVE, GALERIAS_NUEVAS,
    PROTECTOR, ESTADO, BUSCAR_ACTUALIZACIONES,
}

/** Una galería bajada, para armar las opciones del ajuste "Galería". */
data class GaleriaOpcion(val id: String, val titulo: String)

/** Una fila de la pantalla de Ajustes. */
sealed class ItemAjuste(val id: IdAjuste, @StringRes val nombre: Int) {
    abstract fun valor(p: Preferencias): Etiqueta

    /** `dir` = -1 (izquierda) o +1 (derecha / OK). Con `envolver` (OK) al llegar al final vuelve al principio. */
    open fun mover(p: Preferencias, dir: Int, envolver: Boolean): Preferencias = p

    /** Se puede cambiar con las flechas. */
    open val editable: Boolean get() = false

    /** Un texto de ayuda debajo del valor (por ejemplo "Se cambia desde la PC"). */
    @get:StringRes open val ayuda: Int? get() = null
}

/** Un ajuste con una lista de valores posibles. */
class ItemLista(id: IdAjuste, @StringRes nombre: Int, val opciones: List<Opcion>) : ItemAjuste(id, nombre) {
    override val editable get() = opciones.size > 1

    override fun valor(p: Preferencias): Etiqueta = (opciones.firstOrNull { it.esActual(p) } ?: opciones.first()).etiqueta

    override fun mover(p: Preferencias, dir: Int, envolver: Boolean): Preferencias {
        if (opciones.isEmpty() || dir == 0) return p
        val actual = opciones.indexOfFirst { it.esActual(p) }.coerceAtLeast(0)
        val nuevo = if (envolver) Math.floorMod(actual + dir, opciones.size) else (actual + dir).coerceIn(0, opciones.lastIndex)
        return opciones[nuevo].aplicar(p)
    }
}

/** Un ajuste numérico que salta de paso en paso. */
class ItemPasos(
    id: IdAjuste,
    @StringRes nombre: Int,
    val pasos: List<Int>,
    private val leer: (Preferencias) -> Int,
    private val poner: (Preferencias, Int) -> Preferencias,
    private val formato: (Int) -> Etiqueta,
) : ItemAjuste(id, nombre) {
    override val editable get() = true
    override fun valor(p: Preferencias): Etiqueta = formato(leer(p))
    override fun mover(p: Preferencias, dir: Int, envolver: Boolean): Preferencias =
        poner(p, Pasos.mover(leer(p), pasos, dir, envolver))
}

/** Una hora del día (de a 30 minutos). */
class ItemHora(
    id: IdAjuste,
    @StringRes nombre: Int,
    private val leer: (Preferencias) -> Int,
    private val poner: (Preferencias, Int) -> Preferencias,
) : ItemAjuste(id, nombre) {
    override val editable get() = true
    override fun valor(p: Preferencias): Etiqueta = hora(leer(p))
    override fun mover(p: Preferencias, dir: Int, envolver: Boolean): Preferencias = poner(p, Pasos.moverHora(leer(p), dir))
}

/** Solo muestra un valor (no se cambia acá): el interruptor del protector. */
class ItemInfo(
    id: IdAjuste,
    @StringRes nombre: Int,
    private val texto: (Preferencias) -> Etiqueta,
    @StringRes private val ayudaRes: Int?,
    val habilitado: Boolean,
) : ItemAjuste(id, nombre) {
    override fun valor(p: Preferencias): Etiqueta = texto(p)
    override val ayuda get() = ayudaRes
}

/**
 * Un ajuste que se escribe (la carpeta y la clave de Drive): OK abre una ventanita con el teclado de la tele (o el del
 * teléfono, con la app Google TV). Las flechas no lo cambian.
 */
class ItemTexto(
    id: IdAjuste,
    @StringRes nombre: Int,
    private val mostrar: (Preferencias) -> Etiqueta,
    @StringRes private val ayudaRes: Int,
    /** Con qué texto arranca la ventanita. La clave arranca vacía: nunca se muestra entera. */
    val inicial: (Preferencias) -> String,
    /** Aplica lo escrito. Vacío = borrar (vuelve a "sin cargar"). null = lo escrito no sirve (y se avisa). */
    val poner: (Preferencias, String) -> Preferencias?,
    /** Un texto que no se muestra (la clave): sin autocorrector ni sugerencias. */
    val secreto: Boolean,
) : ItemAjuste(id, nombre) {
    override fun valor(p: Preferencias): Etiqueta = mostrar(p)
    override val ayuda get() = ayudaRes
}

/** La entrada del final: abre la pantalla de Estado. */
class ItemAbrirEstado : ItemAjuste(IdAjuste.ESTADO, R.string.aj_estado) {
    override fun valor(p: Preferencias): Etiqueta = Etiqueta.Res(R.string.aj_estado_valor)
}

fun hora(minutos: Int): Etiqueta {
    val m = Math.floorMod(minutos, Pasos.MINUTOS_DEL_DIA)
    return Etiqueta.Res(R.string.formato_hora, listOf(m / 60, m % 60))
}

/** El tiempo por obra como se lee: "45 s" o "2 min". */
fun etiquetaTiempo(seg: Int): Etiqueta =
    if (seg >= 60 && seg % 60 == 0) Etiqueta.Res(R.string.formato_minutos, listOf(seg / 60))
    else Etiqueta.Res(R.string.formato_segundos, listOf(seg))

/** La duración de la transición como se lee: "1 s" o "1,5 s". */
fun etiquetaDuracion(ms: Int): Etiqueta {
    val entero = ms / 1000
    val dec = (ms % 1000) / 100
    return Etiqueta.Res(R.string.formato_segundos_texto, listOf(if (dec == 0) "$entero" else "$entero,$dec"))
}

object ListaAjustes {
    private fun res(@StringRes id: Int) = Etiqueta.Res(id)

    private fun <T> lista(
        id: IdAjuste,
        @StringRes nombre: Int,
        valores: List<T>,
        leer: (Preferencias) -> T,
        poner: (Preferencias, T) -> Preferencias,
        etiqueta: (T) -> Etiqueta,
    ) = ItemLista(id, nombre, valores.map { v -> Opcion(etiqueta(v), { leer(it) == v }, { poner(it, v) }) })

    private fun siNo(id: IdAjuste, @StringRes nombre: Int, leer: (Preferencias) -> Boolean, poner: (Preferencias, Boolean) -> Preferencias) =
        lista(id, nombre, listOf(false, true), leer, poner) { if (it) res(R.string.v_si) else res(R.string.v_no) }

    @StringRes fun nombreTema(t: Tema): Int = when (t) {
        Tema.GALERIA_MODERNA -> R.string.tema_galeria_moderna
        Tema.SALA_OSCURA -> R.string.tema_sala_oscura
        Tema.INMERSIVO -> R.string.tema_inmersivo
        Tema.LIBRO_DE_ARTE -> R.string.tema_libro_de_arte
    }

    @StringRes fun etiquetaEspacio(e: EspacioMaximo): Int = when (e) {
        EspacioMaximo.MEDIO_GB -> R.string.espacio_medio
        EspacioMaximo.UN_GB -> R.string.espacio_1
        EspacioMaximo.DOS_GB -> R.string.espacio_2
    }

    @StringRes private fun nombreMarco(id: String): Int? = when (id) {
        "fino" -> R.string.marco_fino
        "filete" -> R.string.marco_filete
        "madera" -> R.string.marco_madera
        "dorado" -> R.string.marco_dorado
        "dorado-redondo" -> R.string.marco_dorado_redondo
        "sin" -> R.string.marco_sin
        else -> null
    }

    @StringRes fun nombreColor(id: String): Int? = when (id) {
        "blanco-galeria" -> R.string.color_blanco_galeria
        "gris-calido" -> R.string.color_gris_calido
        "piedra" -> R.string.color_piedra
        "carbon" -> R.string.color_carbon
        "negro" -> R.string.color_negro
        "azul-noche" -> R.string.color_azul_noche
        "bordo" -> R.string.color_bordo
        else -> null
    }

    private fun etiquetaDe(id: String, nombre: (String) -> Int?): Etiqueta = nombre(id)?.let { res(it) } ?: Etiqueta.Texto(id)

    /** Todos los ajustes de la tabla del SPEC-0, en su orden, más la entrada que abre Estado. */
    fun construir(galerias: List<GaleriaOpcion>): List<ItemAjuste> {
        val marcos = buildList {
            add(Opcion(res(R.string.v_del_tema), { it.marco == MarcoElegido.DEL_TEMA }, { it.copy(marco = MarcoElegido.DEL_TEMA) }))
            for (m in CatalogoMarcos.todos) {
                add(Opcion(etiquetaDe(m.id, ::nombreMarco), { it.marco == MarcoElegido.FIJO(m.id) }, { it.copy(marco = MarcoElegido.FIJO(m.id)) }))
            }
            add(Opcion(res(R.string.marco_al_azar), { it.marco == MarcoElegido.AL_AZAR }, { it.copy(marco = MarcoElegido.AL_AZAR) }))
            add(Opcion(res(R.string.marco_de_la_galeria), { it.marco == MarcoElegido.DE_LA_GALERIA }, { it.copy(marco = MarcoElegido.DE_LA_GALERIA) }))
        }
        val galeriaOpciones = buildList {
            add(Opcion(res(R.string.v_todas), { g -> g.galeria == null || galerias.none { coincide(it, g.galeria) } }, { it.copy(galeria = null) }))
            for (g in galerias) {
                add(Opcion(Etiqueta.Texto(g.titulo), { p -> coincide(g, p.galeria) }, { it.copy(galeria = g.id) }))
            }
        }
        return listOf(
            lista(IdAjuste.TEMA, R.string.aj_tema, Tema.values().toList(), { it.tema }, { p, v -> p.copy(tema = v) }) { res(nombreTema(it)) },
            lista(IdAjuste.MODO, R.string.aj_modo, Modo.values().toList(), { it.modo }, { p, v -> p.copy(modo = v) }) {
                res(when (it) { Modo.CLARO -> R.string.modo_claro; Modo.OSCURO -> R.string.modo_oscuro; Modo.AUTOMATICO -> R.string.modo_automatico })
            },
            ItemHora(IdAjuste.HORA_DIA, R.string.aj_hora_dia, { it.horaDiaMin }, { p, v -> p.copy(horaDiaMin = v) }),
            ItemHora(IdAjuste.HORA_NOCHE, R.string.aj_hora_noche, { it.horaNocheMin }, { p, v -> p.copy(horaNocheMin = v) }),
            lista(
                IdAjuste.PARED_CLARO, R.string.aj_pared_claro, listOf<String?>(null) + CatalogoColores.claros.map { it.id },
                { it.colorParedClaro }, { p, v -> p.copy(colorParedClaro = v) },
            ) { if (it == null) res(R.string.v_del_tema) else etiquetaDe(it, ::nombreColor) },
            lista(
                IdAjuste.PARED_OSCURO, R.string.aj_pared_oscuro, listOf<String?>(null) + CatalogoColores.oscuros.map { it.id },
                { it.colorParedOscuro }, { p, v -> p.copy(colorParedOscuro = v) },
            ) { if (it == null) res(R.string.v_del_tema) else etiquetaDe(it, ::nombreColor) },
            ItemLista(IdAjuste.MARCO, R.string.aj_marco, marcos),
            lista(IdAjuste.TAMANO, R.string.aj_tamano, Tamano.values().toList(), { it.tamano }, { p, v -> p.copy(tamano = v) }) {
                res(when (it) {
                    Tamano.NORMAL -> R.string.tamano_normal
                    Tamano.GRANDE -> R.string.tamano_grande
                    Tamano.MAXIMO -> R.string.tamano_maximo
                    Tamano.PANTALLA_COMPLETA -> R.string.tamano_pantalla_completa
                })
            },
            siNo(IdAjuste.COMPLETA_CON_MARCO, R.string.aj_completa_con_marco, { it.pantallaCompletaConMarco }, { p, v -> p.copy(pantallaCompletaConMarco = v) }),
            lista(
                IdAjuste.EXPLICACION, R.string.aj_explicacion, ExplicacionEnPantalla.values().toList(),
                { it.explicacionEnPantalla }, { p, v -> p.copy(explicacionEnPantalla = v) },
            ) { res(if (it == ExplicacionEnPantalla.CUANDO_ENTRA) R.string.v_cuando_entra else R.string.v_nunca) },
            lista(IdAjuste.SALA, R.string.aj_sala, NombreSala.values().toList(), { it.nombreSala }, { p, v -> p.copy(nombreSala = v) }) {
                res(when (it) {
                    NombreSala.SIEMPRE -> R.string.sala_siempre
                    NombreSala.UNOS_SEGUNDOS -> R.string.v_unos_segundos
                    NombreSala.SOLO_CONTADOR -> R.string.sala_solo_contador
                    NombreSala.NUNCA -> R.string.v_nunca
                })
            },
            lista(IdAjuste.CALIDAD, R.string.aj_calidad, Calidad.values().toList(), { it.calidad }, { p, v -> p.copy(calidad = v) }) {
                res(if (it == Calidad.K4) R.string.calidad_4k else R.string.calidad_1080)
            },
            ItemPasos(IdAjuste.TIEMPO, R.string.aj_tiempo, Pasos.TIEMPO_SEG, { it.tiempoPorObraSeg }, { p, v -> p.copy(tiempoPorObraSeg = v) }, ::etiquetaTiempo),
            lista(IdAjuste.ORDEN, R.string.aj_orden, Orden.values().toList(), { it.orden }, { p, v -> p.copy(orden = v) }) {
                res(if (it == Orden.GALERIA) R.string.orden_galeria else R.string.orden_mezclar)
            },
            ItemLista(IdAjuste.GALERIA, R.string.aj_galeria, galeriaOpciones),
            lista(IdAjuste.PARED, R.string.aj_pared, Pared.values().toList(), { it.pared }, { p, v -> p.copy(pared = v) }) {
                res(when (it) { Pared.APAGADA -> R.string.pared_apagada; Pared.HASTA_2 -> R.string.pared_hasta_2; Pared.HASTA_3 -> R.string.pared_hasta_3 })
            },
            lista(IdAjuste.CARTEL, R.string.aj_cartel, CartelObra.values().toList(), { it.cartelObra }, { p, v -> p.copy(cartelObra = v) }) {
                res(when (it) { CartelObra.SIEMPRE -> R.string.v_siempre; CartelObra.UNOS_SEGUNDOS -> R.string.cartel_unos_segundos; CartelObra.NUNCA -> R.string.v_nunca })
            },
            siNo(IdAjuste.CREDITO, R.string.aj_credito, { it.mostrarCredito }, { p, v -> p.copy(mostrarCredito = v) }),
            lista(IdAjuste.CIERRE_BARRA, R.string.aj_cierre_barra, CierreBarra.values().toList(), { it.cierreBarra }, { p, v -> p.copy(cierreBarra = v) }) {
                if (it == CierreBarra.NUNCA) res(R.string.v_nunca) else Etiqueta.Res(R.string.formato_segundos, listOf(it.segundos ?: 0))
            },
            lista(IdAjuste.TRANSICION, R.string.aj_transicion, Transicion.values().toList(), { it.transicion }, { p, v -> p.copy(transicion = v) }) {
                res(if (it == Transicion.FUNDIDO) R.string.transicion_fundido else R.string.transicion_ninguna)
            },
            ItemPasos(IdAjuste.DURACION, R.string.aj_duracion, Pasos.DURACION_MS, { it.duracionTransicionMs }, { p, v -> p.copy(duracionTransicionMs = v) }, ::etiquetaDuracion),
            lista(IdAjuste.SINCRONIZAR, R.string.aj_sincronizar, SincronizarCada.values().toList(), { it.sincronizarCada }, { p, v -> p.copy(sincronizarCada = v) }) {
                res(when (it) {
                    SincronizarCada.UNA_HORA -> R.string.sinc_1h
                    SincronizarCada.SEIS_HORAS -> R.string.sinc_6h
                    SincronizarCada.VEINTICUATRO_HORAS -> R.string.sinc_24h
                    SincronizarCada.SOLO_A_MANO -> R.string.sinc_mano
                })
            },
            ItemPasos(IdAjuste.RESERVA, R.string.aj_reserva, Pasos.RESERVA_OBRAS, { it.reservaObras }, { p, v -> p.copy(reservaObras = v) }) {
                Etiqueta.Res(R.string.formato_reserva_obras, listOf(it))
            },
            lista(IdAjuste.ESPACIO, R.string.aj_espacio, EspacioMaximo.values().toList(), { it.espacioMaximo }, { p, v -> p.copy(espacioMaximo = v) }) {
                res(when (it) { EspacioMaximo.MEDIO_GB -> R.string.espacio_medio; EspacioMaximo.UN_GB -> R.string.espacio_1; EspacioMaximo.DOS_GB -> R.string.espacio_2 })
            },
            lista(IdAjuste.RITMO, R.string.aj_ritmo, RitmoBajada.values().toList(), { it.ritmoBajada }, { p, v -> p.copy(ritmoBajada = v) }) {
                res(if (it == RitmoBajada.TRANQUILO) R.string.ritmo_tranquilo else R.string.ritmo_normal)
            },
            lista(IdAjuste.CAMINO, R.string.aj_camino, CaminoObras.values().toList(), { it.caminoObras }, { p, v -> p.copy(caminoObras = v) }) {
                res(if (it == CaminoObras.COPIA_PRIMERO) R.string.camino_copia else R.string.camino_original)
            },
            ItemTexto(
                IdAjuste.CARPETA, R.string.aj_carpeta,
                { p -> p.carpetaDrive?.let { Etiqueta.Texto(CarpetaDrive.abreviar(it)) } ?: res(R.string.v_sin_cargar) },
                R.string.carpeta_ayuda,
                inicial = { it.carpetaDrive.orEmpty() },
                poner = { p, t -> if (t.isBlank()) p.copy(carpetaDrive = null) else CarpetaDrive.extraerId(t)?.let { p.copy(carpetaDrive = it) } },
                secreto = false,
            ),
            ItemTexto(
                IdAjuste.CLAVE, R.string.aj_clave,
                { p -> p.claveDrive?.let { Etiqueta.Texto(ClaveDrive.tapar(it)) } ?: res(R.string.v_sin_cargar) },
                R.string.clave_ayuda,
                inicial = { "" },
                poner = { p, t -> if (t.isBlank()) p.copy(claveDrive = null) else ClaveDrive.limpiar(t)?.let { p.copy(claveDrive = it) } },
                secreto = true,
            ),
            lista(IdAjuste.GALERIAS_NUEVAS, R.string.aj_galerias_nuevas, GaleriasNuevas.values().toList(), { it.galeriasNuevas }, { p, v -> p.copy(galeriasNuevas = v) }) {
                res(if (it == GaleriasNuevas.TILDADAS) R.string.v_tildadas else R.string.v_destildadas)
            },
            ItemInfo(IdAjuste.PROTECTOR, R.string.aj_protector, { res(R.string.protector_proximamente) }, null, habilitado = false),
            ItemAbrirEstado(),
            lista(
                IdAjuste.BUSCAR_ACTUALIZACIONES, R.string.aj_buscar_actualizaciones, BuscarActualizaciones.values().toList(),
                { it.buscarActualizaciones }, { p, v -> p.copy(buscarActualizaciones = v) },
            ) { res(if (it == BuscarActualizaciones.UNA_VEZ_POR_DIA) R.string.act_una_vez_por_dia else R.string.act_solo_a_mano) },
        ).also { check(it.map { i -> i.id } == IdAjuste.values().toList()) { "Falta o sobra un ajuste en la lista" } }
    }

    /** ¿La galería `g` es la elegida? La elección puede venir por id (carpeta) o por título (como acepta el pase). */
    private fun coincide(g: GaleriaOpcion, eleccion: String?): Boolean =
        eleccion != null && (g.id == eleccion || g.titulo.equals(eleccion, ignoreCase = true))
}

/**
 * Cómo se reparten los ajustes en las pantallas del menú. Cada uno vive en UN grupo; Protector y Estado son pantallas
 * aparte (no tienen valor para cambiar acá). Las claves guardadas no cambian: esto es solo dónde se muestra cada fila.
 */
enum class GrupoAjustes(val ids: List<IdAjuste>) {
    APARIENCIA(
        listOf(
            IdAjuste.TEMA, IdAjuste.MODO, IdAjuste.HORA_DIA, IdAjuste.HORA_NOCHE, IdAjuste.PARED_CLARO, IdAjuste.PARED_OSCURO,
            IdAjuste.MARCO, IdAjuste.TAMANO, IdAjuste.COMPLETA_CON_MARCO, IdAjuste.EXPLICACION, IdAjuste.SALA, IdAjuste.CARTEL,
            IdAjuste.CREDITO, IdAjuste.CIERRE_BARRA,
        ),
    ),
    PASE(
        listOf(
            IdAjuste.TIEMPO, IdAjuste.ORDEN, IdAjuste.GALERIA, IdAjuste.PARED, IdAjuste.TRANSICION, IdAjuste.DURACION, IdAjuste.CALIDAD,
        ),
    ),
    CONEXION(listOf(IdAjuste.CARPETA, IdAjuste.CLAVE, IdAjuste.SINCRONIZAR, IdAjuste.RESERVA, IdAjuste.ESPACIO, IdAjuste.RITMO, IdAjuste.CAMINO)),
    /** La pantalla Galerías lleva su propia fila "Galerías nuevas" al pie. */
    GALERIAS(listOf(IdAjuste.GALERIAS_NUEVAS)),
    ;

    companion object {
        /** Los ajustes que no están en ningún grupo: tienen su propia pantalla (Buscar actualizaciones vive en Actualizaciones). */
        val APARTE: List<IdAjuste> = listOf(IdAjuste.PROTECTOR, IdAjuste.ESTADO, IdAjuste.BUSCAR_ACTUALIZACIONES)
    }
}

/** Las filas de esta lista que son de ese grupo, en el orden del grupo. */
fun List<ItemAjuste>.delGrupo(grupo: GrupoAjustes): List<ItemAjuste> =
    grupo.ids.map { id -> first { it.id == id } }
