package local.museumslideshow.pantallas

import java.text.Normalizer
import local.museumslideshow.ajustes.MarcoElegido
import local.museumslideshow.ajustes.Modo
import local.museumslideshow.ajustes.Preferencias
import local.museumslideshow.ajustes.Tamano
import local.museumslideshow.ajustes.modoEfectivo
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

/** Los datos que muestra Inicio, sacados del catálogo y los ajustes. */
data class ResumenInicio(
    /** El título de la galería elegida, o null si el pase recorre todas. */
    val galeriaElegida: String?,
    /** Las obras que pasarían con la elección actual. */
    val obrasDelPase: Int,
    val galerias: Int,
    val obras: Int,
    /** Hoy = las obras bajadas; en la parte 2 (reserva) pasa a ser las de la reserva. */
    val obrasListas: Int,
    /** La obra de fondo: la primera bajada de la galería elegida, o la primera del catálogo. */
    val tapa: Obra?,
    val ultimaSincronizacion: Long?,
) {
    companion object {
        fun de(cat: CatalogoCargado?, prefs: Preferencias): ResumenInicio {
            val todas = cat?.galerias.orEmpty()
            val elegidas = Pase.galeriasElegidas(todas, prefs.galeria)
            val unaSola = elegidas.size == 1
            val deLaElegida = elegidas.firstOrNull { it.obras.isNotEmpty() }
            val gTapa = (if (unaSola) deLaElegida else null) ?: todas.firstOrNull { it.obras.isNotEmpty() }
            val total = todas.sumOf { it.obras.size }
            return ResumenInicio(
                galeriaElegida = if (unaSola) elegidas.first().titulo else null,
                obrasDelPase = elegidas.sumOf { it.obras.size },
                galerias = todas.size,
                obras = total,
                obrasListas = total,
                tapa = gTapa?.obras?.firstOrNull(),
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
) {
    companion object {
        fun de(prefs: Preferencias, minutos: Int): DatosVistaPrevia {
            val tema = RegistroTemas.de(prefs.tema)
            val modo = prefs.modoEfectivo(minutos)
            val elegido = if (modo == Modo.OSCURO) prefs.colorParedOscuro else prefs.colorParedClaro
            val color = tema.colorPared(modo, elegido)
            // El marco que de verdad se dibuja hoy (los que no tienen foto caen en "fino", como en la galería).
            val pedido = when {
                tema.sinMarcoSiempre -> "sin"
                else -> when (val m = prefs.marco) {
                    is MarcoElegido.FIJO -> m.id
                    MarcoElegido.DEL_TEMA -> tema.marcoDelTema
                    MarcoElegido.AL_AZAR, MarcoElegido.DE_LA_GALERIA -> "fino"
                }
            }
            val marco = CatalogoMarcos.resolver(pedido).id
            val tamano = tema.tamanoFijo ?: prefs.tamano
            val escala = when (tamano) {
                Tamano.NORMAL -> 0.7f
                Tamano.GRANDE -> 0.85f
                Tamano.MAXIMO, Tamano.PANTALLA_COMPLETA -> 1f
            }
            return DatosVistaPrevia(color.c1, color.c2, color.id, marco, escala)
        }
    }
}
