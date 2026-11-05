package local.museumslideshow.pantallas

import java.text.Normalizer
import local.museumslideshow.ajustes.MarcoElegido
import local.museumslideshow.ajustes.Modo
import local.museumslideshow.ajustes.Preferencias
import local.museumslideshow.ajustes.Tamano
import local.museumslideshow.ajustes.estaTildada
import local.museumslideshow.ajustes.modoEfectivo
import local.museumslideshow.reserva.EstadoReserva
import local.museumslideshow.catalogo.CatalogoCargado
import local.museumslideshow.catalogo.Obra
import local.museumslideshow.galeria.Pase
import local.museumslideshow.temas.CatalogoMarcos
import local.museumslideshow.temas.RegistroTemas

/** Las pantallas de la app. `id` es el texto del extra `--es pantalla <id>` para abrirla directo por adb. */
enum class PantallaApp(val id: String) {
    INICIO("inicio"),
    GALERIA("galeria"),
    GALERIAS("galerias"),
    APARIENCIA("apariencia"),
    PASE("pase"),
    CONEXION("conexion"),
    PROTECTOR("protector"),
    ESTADO("estado"),
    ;

    companion object {
        fun deTexto(texto: String?): PantallaApp? {
            val t = Normalizer.normalize(texto.orEmpty().trim(), Normalizer.Form.NFD)
                .replace(Regex("\\p{M}+"), "").lowercase()
            return values().firstOrNull { it.id == t }
        }

        /**
         * Qué pantalla pide un intent (sus extras ya pasados a texto): el extra `pantalla` manda; `escena` abre la galería;
         * sin ningún extra (abrir la app desde Google TV, o `am start` pelado) es Inicio. Otros extras (un ajuste) no
         * cambian de pantalla: null.
         */
        fun deIntent(extras: Map<String, String>): PantallaApp? = when {
            extras.containsKey("pantalla") -> deTexto(extras["pantalla"])
            extras.containsKey("escena") -> GALERIA
            extras.isEmpty() -> INICIO
            else -> null
        }
    }
}

// ------------------------------------------------------------------------------------------------- Inicio

/** A dónde lleva cada botón de Inicio, en el orden en que se recorren con las flechas (0 = el botón principal). */
enum class DestinoInicio { VER_GALERIA, GALERIAS, APARIENCIA, PASE, CONEXION, PROTECTOR, ESTADO }

data class PasoInicio(val foco: Int, val abrir: DestinoInicio? = null, val salir: Boolean = false)

/**
 * El foco de Inicio como función pura. Foco 0 = "Ver la galería"; 1 a 6 = los seis botones en dos columnas
 * (1-2, 3-4, 5-6). Abajo desde el principal cae en el primero; arriba desde la primera fila vuelve al principal.
 */
object NavegacionInicio {
    const val CANTIDAD = 7

    fun paso(foco: Int, tecla: Tecla): PasoInicio {
        val f = foco.coerceIn(0, CANTIDAD - 1)
        return when (tecla) {
            Tecla.ATRAS -> PasoInicio(f, salir = true)
            Tecla.OK -> PasoInicio(f, abrir = DestinoInicio.values()[f])
            Tecla.ABAJO -> PasoInicio(if (f == 0) 1 else if (f + 2 < CANTIDAD) f + 2 else f)
            Tecla.ARRIBA -> PasoInicio(if (f <= 2) 0 else f - 2)
            Tecla.DERECHA -> PasoInicio(if (f in 1 until CANTIDAD - 1 && esColumnaIzquierda(f)) f + 1 else f)
            Tecla.IZQUIERDA -> PasoInicio(if (f > 0 && !esColumnaIzquierda(f)) f - 1 else f)
        }
    }

    private fun esColumnaIzquierda(f: Int) = f % 2 == 1
}

/** Los datos que muestra Inicio, sacados del catálogo, los ajustes y la reserva. Solo cuentan las galerías tildadas. */
data class ResumenInicio(
    /** El título de la galería elegida, o null si el pase recorre todas las tildadas. */
    val galeriaElegida: String?,
    /** Las obras que pasarían con la elección actual. */
    val obrasDelPase: Int,
    val galerias: Int,
    val obras: Int,
    /** Cuántas de las próximas obras de la reserva ya están bajadas. */
    val obrasListas: Int,
    /** De cuántas se compone la reserva (N, o menos si el pase tiene menos obras). */
    val obrasListasDe: Int,
    /**
     * La obra de fondo (con su ficha): la primera ya bajada de la galería elegida o, si no, de las tildadas, así se ve con su
     * copia 4K. Si ninguna está bajada, la primera de la galería (y se usa su tapa chica).
     */
    val tapa: Obra?,
    /** La galería de esa obra: su tapa guardada (`tapas/<id>.jpg`) es el fondo cuando la obra todavía no está bajada. */
    val tapaGaleriaId: String?,
    val ultimaSincronizacion: Long?,
) {
    companion object {
        fun de(cat: CatalogoCargado?, prefs: Preferencias, reserva: EstadoReserva? = null): ResumenInicio {
            val todas = cat?.galerias.orEmpty().filter { prefs.estaTildada(it.driveId) }
            val elegidas = Pase.galeriasElegidas(todas, prefs.galeria)
            val unaSola = elegidas.size == 1
            val deLaElegida = elegidas.firstOrNull { it.obras.isNotEmpty() }
            val gTapa = (if (unaSola) deLaElegida else null) ?: todas.firstOrNull { it.obras.isNotEmpty() }
            // El fondo es una obra que ya está en la tele (su copia 4K); si ninguna lo está, la primera y se usa la tapa chica.
            val fondoBajada = (listOfNotNull(gTapa) + elegidas + todas).firstNotNullOfOrNull { g ->
                g.obras.firstOrNull { it.bajada }?.let { it to g }
            }
            val total = todas.sumOf { it.obras.size }
            val delPase = elegidas.sumOf { it.obras.size }
            val conPedido = reserva?.conPedido == true
            val de = if (conPedido) reserva!!.porDelante else minOf(prefs.reservaObras, delPase)
            val listas = if (conPedido) reserva!!.listasPorDelante else minOf(reserva?.obrasGuardadas ?: 0, de)
            return ResumenInicio(
                galeriaElegida = if (unaSola) elegidas.first().titulo else null,
                obrasDelPase = delPase,
                galerias = todas.size,
                obras = total,
                obrasListas = listas,
                obrasListasDe = de,
                tapa = fondoBajada?.first ?: gTapa?.obras?.firstOrNull(),
                tapaGaleriaId = fondoBajada?.second?.driveId ?: gTapa?.driveId,
                ultimaSincronizacion = cat?.ultimaSincronizacionBuena,
            )
        }
    }
}

// ------------------------------------------------------------------------------------------------- Apariencia

/** Lo que dibuja la vista previa de Apariencia: la pared, el marco y cuánto ocupa la obra. */
data class DatosVistaPrevia(
    val paredArriba: Long,
    val paredAbajo: Long,
    val idColor: String,
    /** Id del catálogo de marcos que se dibuja ("sin" = sin marco). */
    val marco: String,
    /** Cuánto del lugar ocupa la obra (0,7 a 1). */
    val escala: Float,
    /** Grosor del marco y del filete en px del escenario de 1920x1080 para el tamaño elegido (la vista previa los escala). */
    val grosor: Int = 0,
    val filete: Int = 0,
    val modo: Modo = Modo.CLARO,
) {
    companion object {
        fun de(prefs: Preferencias, minutos: Int): DatosVistaPrevia {
            val tema = RegistroTemas.de(prefs.tema)
            val modo = prefs.modoEfectivo(minutos)
            val elegido = if (modo == Modo.OSCURO) prefs.colorParedOscuro else prefs.colorParedClaro
            val color = tema.colorPared(modo, elegido)
            // El marco que de verdad se dibuja (una obra rectangular nunca usa el redondo: va con el dorado).
            val pedido = when {
                tema.sinMarcoSiempre -> "sin"
                else -> when (val m = prefs.marco) {
                    is MarcoElegido.FIJO -> m.id
                    MarcoElegido.DEL_TEMA -> tema.marcoDelTema
                    MarcoElegido.AL_AZAR, MarcoElegido.DE_LA_GALERIA -> "fino"   // depende de cada obra: se muestra el fino
                }
            }
            val def = CatalogoMarcos.resolver(if (pedido == "dorado-redondo") "dorado" else pedido)
            val marco = def.id
            val tamano = tema.tamanoFijo ?: prefs.tamano
            val tam = if (tamano == Tamano.PANTALLA_COMPLETA) Tamano.MAXIMO else tamano
            val escala = when (tamano) {
                Tamano.NORMAL -> 0.7f
                Tamano.GRANDE -> 0.85f
                Tamano.MAXIMO, Tamano.PANTALLA_COMPLETA -> 1f
            }
            return DatosVistaPrevia(color.c1, color.c2, color.id, marco, escala, def.grosorDe(tam), def.fileteDe(tam), modo)
        }
    }
}
