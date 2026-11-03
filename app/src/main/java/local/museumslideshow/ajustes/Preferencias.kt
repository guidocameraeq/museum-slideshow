package local.museumslideshow.ajustes

/** Id de tema. El `id` es el texto que usan los ajustes y `am start` (`--es tema sala-oscura`). */
enum class Tema(val id: String) {
    GALERIA_MODERNA("galeria-moderna"),
    SALA_OSCURA("sala-oscura"),
    INMERSIVO("inmersivo"),
    LIBRO_DE_ARTE("libro-de-arte"),
    ;

    companion object {
        fun deId(id: String): Tema? = values().firstOrNull { it.id == id }
    }
}

enum class Modo { CLARO, OSCURO, AUTOMATICO }

/** Qué marco se usa. FIJO lleva el id del catálogo (`fino`, `madera`...). */
sealed class MarcoElegido {
    object DEL_TEMA : MarcoElegido()
    data class FIJO(val id: String) : MarcoElegido()
    object AL_AZAR : MarcoElegido()
    object DE_LA_GALERIA : MarcoElegido()
}

enum class Tamano { NORMAL, GRANDE, MAXIMO, PANTALLA_COMPLETA }
enum class ExplicacionEnPantalla { CUANDO_ENTRA, NUNCA }
enum class NombreSala { SIEMPRE, UNOS_SEGUNDOS, SOLO_CONTADOR, NUNCA }
enum class Calidad { K4, HD1080 }
enum class Orden { GALERIA, MEZCLAR }

/** Qué pasa con una galería que no figura en la selección (la que el curador sube después). */
enum class GaleriasNuevas { TILDADAS, DESTILDADAS }

/** Cómo se ven las galerías en la pantalla Galerías: tarjetas con tapa o lista con tildes. */
enum class VistaGalerias { TARJETAS, LISTA }

/** `maximo` = cuántas verticales cuelgan juntas como mucho (1 = de a una). */
enum class Pared(val maximo: Int) { APAGADA(1), HASTA_2(2), HASTA_3(3) }
enum class CartelObra { SIEMPRE, UNOS_SEGUNDOS, NUNCA }
enum class Transicion { FUNDIDO, NINGUNA }
enum class SincronizarCada(val horas: Int?) { UNA_HORA(1), SEIS_HORAS(6), VEINTICUATRO_HORAS(24), SOLO_A_MANO(null) }
/** Qué tan seguido se baja de Drive: siempre de a una descarga; `pausaMs` es el respiro entre una y otra. */
enum class RitmoBajada(val pausaMs: Long) { TRANQUILO(2000L), NORMAL(0L) }
enum class EspacioMaximo(val bytes: Long) {
    MEDIO_GB(512L * 1024 * 1024), UN_GB(1024L * 1024 * 1024), DOS_GB(2048L * 1024 * 1024)
}

/**
 * Todos los ajustes (tabla del SPEC-0) con su valor por defecto. Se guardan con [AlmacenAjustes].
 * Las horas del Automático son minutos desde la medianoche (7:00 = 420).
 */
data class Preferencias(
    val tema: Tema = Tema.GALERIA_MODERNA,
    val modo: Modo = Modo.AUTOMATICO,
    val horaDiaMin: Int = 7 * 60,
    val horaNocheMin: Int = 19 * 60,
    /** Id de un color de pared claro; null = el del tema. */
    val colorParedClaro: String? = null,
    /** Id de un color de pared oscuro; null = el del tema. */
    val colorParedOscuro: String? = null,
    val marco: MarcoElegido = MarcoElegido.DEL_TEMA,
    val tamano: Tamano = Tamano.GRANDE,
    val pantallaCompletaConMarco: Boolean = false,
    val explicacionEnPantalla: ExplicacionEnPantalla = ExplicacionEnPantalla.CUANDO_ENTRA,
    val nombreSala: NombreSala = NombreSala.SIEMPRE,
    val calidad: Calidad = Calidad.K4,
    val tiempoPorObraSeg: Int = 60,
    val orden: Orden = Orden.GALERIA,
    /** Id (carpeta) de la galería elegida; null = Todas. */
    val galeria: String? = null,
    val pared: Pared = Pared.HASTA_3,
    val cartelObra: CartelObra = CartelObra.SIEMPRE,
    val transicion: Transicion = Transicion.FUNDIDO,
    val duracionTransicionMs: Int = 1500,
    val sincronizarCada: SincronizarCada = SincronizarCada.SEIS_HORAS,
    val espacioMaximo: EspacioMaximo = EspacioMaximo.UN_GB,
    val ritmoBajada: RitmoBajada = RitmoBajada.TRANQUILO,
    /** null = el de `local.properties` al compilar. */
    val carpetaDrive: String? = null,
    /** Id de carpeta -> tildada. Lo que falta toma el valor de [galeriasNuevas]. */
    val seleccionGalerias: Map<String, Boolean> = emptyMap(),
    val galeriasNuevas: GaleriasNuevas = GaleriasNuevas.TILDADAS,
    /** Cuántas obras próximas guarda la tele (20 / 50 / 100 / 200; por adb se acepta cualquier valor para probar). */
    val reservaObras: Int = 50,
    /** Cómo se ve la pantalla Galerías (no cambia cómo se ve una escena). */
    val vistaGalerias: VistaGalerias = VistaGalerias.TARJETAS,
) {
    companion object {
        val TIEMPO_POR_OBRA_SEG = 10..600
        val DURACION_TRANSICION_MS = 500..3000
        val RESERVA_OBRAS = 1..1000
    }
}

/**
 * Solo lo que cambia cómo SE VE una escena. El tiempo por obra, la transición y lo de la sincronización se dejan en su
 * valor por defecto: cambiarlos no obliga a armar la escena de nuevo (y la obra no parpadea).
 */
fun Preferencias.paraEscena(): Preferencias {
    val d = Preferencias()
    return copy(
        tiempoPorObraSeg = d.tiempoPorObraSeg,
        transicion = d.transicion,
        duracionTransicionMs = d.duracionTransicionMs,
        sincronizarCada = d.sincronizarCada,
        espacioMaximo = d.espacioMaximo,
        ritmoBajada = d.ritmoBajada,
        carpetaDrive = d.carpetaDrive,
        seleccionGalerias = d.seleccionGalerias,
        galeriasNuevas = d.galeriasNuevas,
        reservaObras = d.reservaObras,
        vistaGalerias = d.vistaGalerias,
    )
}

/** Claro u oscuro de verdad: si es AUTOMATICO, según la hora (minutos desde la medianoche). */
fun Preferencias.modoEfectivo(horaMinutos: Int): Modo = when (modo) {
    Modo.CLARO -> Modo.CLARO
    Modo.OSCURO -> Modo.OSCURO
    Modo.AUTOMATICO -> {
        val h = Math.floorMod(horaMinutos, 24 * 60)
        val esDia = if (horaDiaMin <= horaNocheMin) h in horaDiaMin until horaNocheMin
        else h >= horaDiaMin || h < horaNocheMin
        if (esDia) Modo.CLARO else Modo.OSCURO
    }
}
