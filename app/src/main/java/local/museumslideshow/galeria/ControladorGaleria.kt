package local.museumslideshow.galeria

import android.util.Log
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.withFrameNanos
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.util.Calendar
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import local.museumslideshow.Registro
import local.museumslideshow.acomodo.Texto
import local.museumslideshow.ajustes.Preferencias
import local.museumslideshow.ajustes.Transicion
import local.museumslideshow.ajustes.estaTildada
import local.museumslideshow.ajustes.modoEfectivo
import local.museumslideshow.ajustes.paraEscena
import local.museumslideshow.catalogo.CatalogoCargado
import local.museumslideshow.reserva.EnlaceReserva
import local.museumslideshow.reserva.ObraRef
import local.museumslideshow.temas.RegistroTemas

/**
 * El motor de la galería: lleva el pase (qué escena toca), arma cada escena, hace el fundido y responde al control.
 * Todo corre en el hilo principal (la composición); lo único que sale a otro hilo es abrir las imágenes.
 * Las órdenes (siguiente, anterior, relayout) se juntan y se resuelven de a una escena por vez.
 */
class ControladorGaleria(
    private val scope: CoroutineScope,
    private val constructor: ConstructorEscena,
    private val alSalir: () -> Unit,
    /** La reserva de obras: dice cuáles están bajadas y recibe el orden del pase y dónde va. Sin ella todas cuentan como bajadas. */
    private val reserva: EnlaceReserva? = null,
) {
    val superficie = SuperficieObra()

    var actual by mutableStateOf<EscenaLista?>(null)
        private set
    var anterior by mutableStateOf<EscenaLista?>(null)
        private set

    /** El reloj único del fundido: 0 = se ve la escena vieja, 1 = la nueva. Las dos capas leen este mismo valor. */
    var avance by mutableFloatStateOf(1f)
        private set

    var elegida by mutableIntStateOf(0)
        private set
    var marcaElegida by mutableStateOf(false)
        private set

    private var catalogo: CatalogoCargado? = null
    private var prefs = Preferencias()
    private var pase: Pase? = null
    private val semilla = Pase.SEMILLA_SESION

    /** Pantalla completa y Explicación pedidas con el control o con la Barra (la Barra muestra su estado). */
    var pantallaCompleta by mutableStateOf(false)
        private set
    private var expl = EstadoExplicacion.AUTO

    /** La Barra (abajo) está abierta: el pase está en pausa y las teclas son de ella. */
    var barraAbierta by mutableStateOf(false)
        private set

    /** Hay quién abra la Barra (la app sí; un protector sin Barra, no). */
    var barraDisponible = false

    private var suspendido = false
    private var pausadoAntes = false

    private val despertar = Channel<Unit>(Channel.CONFLATED)
    private var pendDelta = 0
    private var pendIr: Int? = null
    private var pendRelay = false

    private var timer: Job? = null
    private var precarga: Deferred<EscenaLista?>? = null
    private var precargaClave: ClaveEscena? = null

    // ---------------------------------------------------------------- entradas

    /** Cambió el catálogo o los ajustes: se rearma el pase conservando la obra actual y se ve si hay que redibujar. */
    fun configurar(cat: CatalogoCargado, p: Preferencias) {
        catalogo = cat
        val cambioTema = p.tema != prefs.tema || p.pared != prefs.pared || p.orden != prefs.orden || p.galeria != prefs.galeria ||
            p.seleccionGalerias != prefs.seleccionGalerias || p.galeriasNuevas != prefs.galeriasNuevas
        val cambioTiempo = p.tiempoPorObraSeg != prefs.tiempoPorObraSeg
        prefs = p
        val tema = RegistroTemas.de(p.tema)
        val obraActual = pase?.actual?.obras?.firstOrNull()?.driveId
        val nuevo = Pase.armar(cat.galerias, p, tema, semilla, obraActual = obraActual, indiceSolicitado = pendIr)
        val estabaPausado = pase?.pausado == true
        pase = nuevo
        if (nuevo != null && estabaPausado) nuevo.pausar()
        if (cambioTema) descartarPrecarga()
        if (nuevo == null) {
            Log.w(Registro.TAG, "Galería: no hay obras para mostrar")
            // Hay obras tildadas pero ninguna con medidas todavía: "Preparando obras…" mientras la reserva las baja.
            preparando = actual == null && cat.galerias.any { prefs.estaTildada(it.driveId) && it.obras.isNotEmpty() }
            notificarReserva()
            return
        }
        if (cambioTiempo && timer?.isActive == true) arrancarTimer()   // la obra que sigue ya dura el tiempo nuevo
        nuevo.asegurarLista(::escenaLista)
        notificarReserva()
        pendRelay = true
        despertar.trySend(Unit)
    }

    /** Una escena se puede mostrar si todas sus obras ya están bajadas (sin reserva, siempre). */
    private fun escenaLista(e: EscenaDef): Boolean =
        e.obras.all { reserva?.estaBajada(it.driveId) ?: it.bajada }

    /** Le cuenta a la reserva el orden de todo el pase y dónde va, para que tenga bajado lo que viene. */
    private fun notificarReserva() {
        val r = reserva ?: return
        val cat = catalogo ?: return
        val extras = Pase.obrasSinMedidas(cat.galerias, prefs).take(ConstantesGaleria.MAX_SIN_MEDIDAS).map { ObraRef(it.driveId, it.archivo) }
        val p = pase
        if (p == null) {
            if (extras.isNotEmpty()) r.actualizar(emptyList(), 0, extras)
            return
        }
        r.actualizar(p.ordenDeObras().map { ObraRef(it.driveId, it.archivo) }, p.posicionDeObra(), extras)
    }

    /** "Preparando obras…": todavía no hay ninguna obra del pase bajada. */
    var preparando by mutableStateOf(false)
        private set

    /** Llegó una obra nueva a la reserva: si se estaba esperando la primera, se vuelve a intentar. */
    fun reintentar() {
        if (pase == null) return
        pendRelay = true
        despertar.trySend(Unit)
    }

    /** Saltar a una escena (extra `escena` del `am start`). */
    fun saltarA(indice: Int) {
        pendIr = indice
        despertar.trySend(Unit)
    }

    fun estadoControl() = EstadoControl(
        enPared = (actual?.def?.obras?.size ?: 0) >= 2,
        vistaExplicacion = actual?.esVista == true,
    )

    fun alAccion(a: Accion) {
        when (a) {
            Accion.NINGUNA -> Unit
            Accion.ANTERIOR -> { pendDelta -= 1; despertar.trySend(Unit) }
            Accion.SIGUIENTE -> { pendDelta += 1; despertar.trySend(Unit) }
            Accion.PANTALLA_COMPLETA -> {
                pantallaCompleta = !pantallaCompleta
                Log.i(Registro.TAG, "Control: pantalla completa = $pantallaCompleta")
                relayout()
            }
            Accion.EXPLICACION -> alternarExplicacion()
            Accion.ELEGIR_ANTERIOR -> elegirObra(-1)
            Accion.ELEGIR_SIGUIENTE -> elegirObra(1)
            Accion.BARRA -> abrirBarra()
            Accion.PAUSAR_O_SEGUIR -> pausa(pase?.pausado != true)
            Accion.PAUSAR -> pausa(true)
            Accion.SEGUIR -> pausa(false)
            Accion.ATRAS -> atras()
        }
    }

    private fun pausa(pausar: Boolean) {
        val p = pase ?: return
        if (pausar) p.pausar() else p.seguir()
        Log.i(Registro.TAG, "Control: pausa = ${p.pausado}")
        if (p.pausado) timer?.cancel() else arrancarTimer()
    }

    private fun abrirBarra() {
        if (!barraDisponible || barraAbierta) return
        barraAbierta = true
        Log.i(Registro.TAG, "Barra: abierta")
    }

    fun cerrarBarra() {
        if (!barraAbierta) return
        barraAbierta = false
        Log.i(Registro.TAG, "Barra: cerrada")
    }

    /**
     * Algo se puso encima de la galería (la Barra, o las pantallas de Ajustes y Estado): el pase se pausa mientras dure
     * y, al volver, sigue solo si no estaba en pausa antes (la pausa que pidió el usuario con Play/Pausa se respeta).
     */
    fun suspender(s: Boolean) {
        if (s == suspendido) return
        suspendido = s
        val p = pase ?: return
        if (s) {
            pausadoAntes = p.pausado
            if (!p.pausado) pausa(true)
        } else if (!pausadoAntes) {
            pausa(false)
        }
    }

    /** Alterna la Pantalla completa (igual que OK). Lo usa la Barra. */
    fun alternarPantallaCompleta() = alAccion(Accion.PANTALLA_COMPLETA)

    /** Alterna la Explicación (igual que mantener OK). Lo usa la Barra. */
    fun alternarExplicacionDesdeBarra() = alAccion(Accion.EXPLICACION)

    /** ¿La escena de ahora muestra la explicación? (para el interruptor de la Barra). */
    val explicacionVisible: Boolean get() = actual?.muestraExplicacion == true

    private fun alternarExplicacion() {
        val a = actual ?: return
        val obra = a.def.obras.getOrNull(elegida.coerceIn(0, a.def.obras.lastIndex))
        if (obra?.ficha?.explicacion.isNullOrBlank()) {
            Log.i(Registro.TAG, "Control: la obra no tiene explicación")
            return
        }
        expl = if (a.muestraExplicacion) EstadoExplicacion.OCULTA else EstadoExplicacion.FORZADA
        Log.i(Registro.TAG, "Control: explicación = $expl")
        relayout()
    }

    private fun elegirObra(d: Int) {
        val a = actual ?: return
        val n = a.def.obras.size
        if (n < 2 || a.esVista) return
        elegida = Math.floorMod(elegida + d, n)
        marcaElegida = true
        Log.i(Registro.TAG, "Control: obra elegida = $elegida")
        // La marca es solo de interfaz; si hay pantalla completa o explicación abierta, la escena cambia de obra.
        if (pantallaCompleta || expl != EstadoExplicacion.AUTO) relayout()
    }

    private fun atras() {
        val a = actual
        when {
            a?.esVista == true || expl == EstadoExplicacion.FORZADA -> { expl = EstadoExplicacion.AUTO; relayout() }
            pantallaCompleta -> { pantallaCompleta = false; relayout() }
            else -> alSalir()
        }
    }

    private fun relayout() {
        pendRelay = true
        despertar.trySend(Unit)
    }

    // ---------------------------------------------------------------- motor

    /** Corre mientras la galería está en pantalla. */
    suspend fun correr() {
        for (u in despertar) {
            while (true) {
                val delta = pendDelta
                val ir = pendIr
                val relay = pendRelay
                if (delta == 0 && ir == null && !relay) break
                pendDelta = 0; pendIr = null; pendRelay = false
                try {
                    procesar(delta, ir)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.e(Registro.TAG, "Galería: error al cambiar de escena (${e.javaClass.simpleName}: ${e.message})")
                }
            }
        }
    }

    private fun claveActual(p: Pase): ClaveEscena {
        val ahora = Calendar.getInstance()
        val minutos = ahora.get(Calendar.HOUR_OF_DAY) * 60 + ahora.get(Calendar.MINUTE)
        val usaElegida = pantallaCompleta || expl != EstadoExplicacion.AUTO
        return ClaveEscena(p.actual, pantallaCompleta, expl, if (usaElegida) elegida else 0, prefs.modoEfectivo(minutos), prefs.paraEscena())
    }

    private suspend fun procesar(delta: Int, ir: Int?) {
        val p = pase ?: return
        if (ir != null) p.irA(ir) else if (delta != 0) p.moverSaltando(delta, ::escenaLista)
        val hayLista = p.asegurarLista(::escenaLista)
        if (ir != null || delta != 0) notificarReserva()
        if (!hayLista) {
            // Ninguna obra del pase está bajada todavía: "Preparando obras…" hasta que llegue la primera (reintentar()).
            if (actual == null) preparando = true
            return
        }
        preparando = false
        val previa = actual
        val cambioEscena = previa == null || previa.def != p.actual
        if (cambioEscena) {
            expl = EstadoExplicacion.AUTO
            elegida = 0
            marcaElegida = false
        }
        val clave = claveActual(p)
        if (!cambioEscena && previa != null && previa.clave == clave) {
            if (timer?.isActive != true) arrancarTimer()
            return
        }
        val nueva = obtener(clave) ?: return
        instalar(nueva)
    }

    private suspend fun obtener(clave: ClaveEscena): EscenaLista? {
        val pj = precarga
        if (pj != null && precargaClave == clave) {
            precarga = null
            precargaClave = null
            val r = try { pj.await() } catch (e: CancellationException) { null }
            if (r != null) return r
        } else {
            descartarPrecarga()
        }
        return try {
            constructor.construir(clave, actual)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(Registro.TAG, "Galería: no se pudo armar la escena (${e.javaClass.simpleName}: ${e.message})")
            null
        }
    }

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    private fun descartarPrecarga() {
        val pj = precarga ?: return
        precarga = null
        precargaClave = null
        pj.cancel()
        pj.invokeOnCompletion { if (it == null) pj.getCompleted()?.liberar() }
    }

    /** Cuál de las dos superficies muestra la escena actual (0 = A abajo, 1 = B arriba). */
    private var slotActual = 0

    private suspend fun instalar(nueva: EscenaLista) {
        val vieja = actual
        timer?.cancel()
        val conFundido = vieja != null && prefs.transicion == Transicion.FUNDIDO && prefs.duracionTransicionMs > 0
        // La escena se dibuja UNA vez en la superficie libre, escondida; el fundido es solo mover alphas.
        val libre = 1 - slotActual
        val msDibujo = superficie.dibujar(libre, nueva)
        repeat(2) { withFrameNanos { it } }     // que el compositor ya tenga el búfer nuevo
        var cuadros = 0
        var ms = 0L
        if (conFundido && vieja != null) {
            // Reloj propio (no el de las animaciones del sistema): el alpha de la superficie y el de la capa de
            // interfaz salen del mismo inicio y duración, en cada cuadro.
            val inicio = System.nanoTime()
            val duracion = prefs.duracionTransicionMs * 1_000_000L
            avance = 0f
            anterior = vieja
            actual = nueva
            nueva.iniciarTemporizadores(scope, prefs)
            while (true) {
                val t = withFrameNanos { it }
                val a = ((t - inicio).toFloat() / duracion).coerceIn(0f, 1f)
                avance = a
                superficie.alphaArriba(if (libre == 1) a else 1f - a)
                cuadros++
                if (a >= 1f) break
            }
            ms = ((System.nanoTime() - inicio) / 1_000_000L).coerceAtLeast(1)
        } else {
            actual = nueva
            nueva.iniciarTemporizadores(scope, prefs)
        }
        avance = 1f
        superficie.alphaArriba(if (libre == 1) 1f else 0f)
        anterior = null
        slotActual = libre
        superficie.soltar(1 - libre)
        if (cuadros > 0) {
            Log.i(
                Registro.TAG,
                "Transición: $cuadros cuadros en $ms ms = ${"%.1f".format(cuadros * 1000f / ms)} cuadros/s " +
                    "(dibujo de la escena nueva, una vez: ${"%.0f".format(msDibujo)} ms)",
            )
        }
        vieja?.liberar()
        registrar(nueva)
        arrancarTimer()
        programarPrecarga()
    }

    private fun registrar(e: EscenaLista) {
        val d = e.disp
        val cartel = d.textos.filterIsInstance<Texto.Cartel>().firstOrNull()
        val lugar = when {
            cartel != null -> {
                val o = d.obras.first().exterior
                if (cartel.rect.x >= o.derecha) "derecha" else if (cartel.rect.derecha <= o.x) "izquierda" else "abajo"
            }
            d.textos.any { it is Texto.Chip } -> "cuadrito"
            d.libro != null -> "pagina"
            d.textos.any { it is Texto.Nombre } -> "nombres"
            else -> "ninguno"
        }
        val interiores = d.obras.joinToString(" ") {
            "[%.0f,%.0f %.0fx%.0f]".format(it.interior.x, it.interior.y, it.interior.ancho, it.interior.alto)
        }
        val c = e.clave
        Log.i(
            Registro.TAG,
            "Escena ${c.def.posicion}/${c.def.total} galería='${c.def.galeriaTitulo}' archivos=${c.def.obras.joinToString(",") { it.archivo }} " +
                "tema=${e.tema.id.id} modo=${e.modo} tamaño=${c.prefs.tamano} completa=${c.completa} vista=${e.esVista} " +
                "nivel=${d.nivel} cartel=$lugar interiores=$interiores " +
                "memoria_obras=${ObraCargada.vivos.get() / (1024 * 1024)}MB",
        )
    }

    private fun arrancarTimer() {
        timer?.cancel()
        val p = pase ?: return
        if (p.pausado || actual?.esVista == true) return
        val ms = prefs.tiempoPorObraSeg * 1000L
        timer = scope.launch {
            delay(ms)
            pendDelta += 1
            despertar.trySend(Unit)
        }
    }

    /** Después del fundido, y de reciclar lo viejo, arma en segundo plano la escena que sigue (la segunda "pantalla"). */
    private fun programarPrecarga() {
        descartarPrecarga()
        val p = pase ?: return
        if (p.escenas.size <= 1) return
        val a = actual ?: return
        if (a.esVista) return
        val siguiente = p.indiceSaltando(1, ::escenaLista)
        if (siguiente == p.indice) return
        val sig = p.escenas[siguiente]
        val clave = ClaveEscena(sig, pantallaCompleta, EstadoExplicacion.AUTO, 0, claveActual(p).modo, prefs.paraEscena())
        precargaClave = clave
        precarga = scope.async {
            delay(ConstantesGaleria.RECICLADO_DEMORA_MS + ConstantesGaleria.PRECARGA_DEMORA_MS)
            try {
                constructor.construir(clave, actual)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(Registro.TAG, "Pre-carga fallida (${e.javaClass.simpleName})")
                null
            }
        }
    }

    /** La galería se va de pantalla: suelta todo. */
    fun liberarTodo() {
        superficie.cerrar()
        timer?.cancel()
        descartarPrecarga()
        anterior?.liberar()
        actual?.liberar()
        anterior = null
        actual = null
    }
}
