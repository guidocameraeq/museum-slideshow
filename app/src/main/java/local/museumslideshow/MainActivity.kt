package local.museumslideshow

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import local.museumslideshow.ajustes.GaleriaOpcion
import local.museumslideshow.ajustes.GrupoAjustes
import local.museumslideshow.ajustes.ListaAjustes
import local.museumslideshow.ajustes.delGrupo
import local.museumslideshow.ajustes.Preferencias
import local.museumslideshow.ajustes.PreferenciasDesdeTexto
import local.museumslideshow.ajustes.estaTildada
import local.museumslideshow.catalogo.Arbol
import local.museumslideshow.galeria.AccionesApp
import local.museumslideshow.galeria.GaleriaEnPantalla
import local.museumslideshow.galeria.SaltoEscena
import local.museumslideshow.pantallas.DatosSync
import local.museumslideshow.pantallas.DestinoInicio
import local.museumslideshow.pantallas.PantallaAjustesGrupo
import local.museumslideshow.pantallas.PantallaApp
import local.museumslideshow.pantallas.PantallaGalerias
import local.museumslideshow.pantallas.PantallaInicio
import local.museumslideshow.pantallas.PantallaProtector
import local.museumslideshow.pantallas.ResumenInicio
import local.museumslideshow.pantallas.PantallaEstado
import local.museumslideshow.pantallas.PantallaSinObras
import local.museumslideshow.pantallas.SinObras
import local.museumslideshow.protector.ResultadoInterruptor

class MainActivity : ComponentActivity() {
    /** Lo compartido con el protector (ajustes, catálogo, sincronización, reserva): una sola vez por proceso. */
    private val motor by lazy { MotorApp.de(applicationContext) }
    private var salto by mutableStateOf<SaltoEscena?>(null)

    /** La pantalla al frente (la Barra no cuenta: vive dentro de la galería). Al abrir la app es Inicio. */
    private var pantalla by mutableStateOf(PantallaApp.INICIO)

    /** A dónde vuelve Atrás desde las pantallas de ajustes, Protector, Galerías y Estado. */
    private var atras by mutableStateOf(PantallaApp.INICIO)

    /** La galería está armada (se entró con "Ver la galería"). Sale de Inicio => se libera. */
    private var galeriaAbierta by mutableStateOf(false)
    private var focoInicio by mutableIntStateOf(0)

    private val acciones = AccionesApp(
        cambiar = { t -> motor.cambiarAjustes(t) },
        sincronizar = { motor.sincronizarAhora() },
        // El botón "Ajustes" de la Barra abre Apariencia (lo que se toca mirando la obra); Atrás vuelve a la galería.
        abrirAjustes = { irA(PantallaApp.APARIENCIA, atrasA = PantallaApp.GALERIA) },
    )

    /** Cambia de pantalla. La galería se arma al entrar y se suelta al volver a Inicio; los ajustes abiertos desde ella la dejan viva. */
    private fun irA(destino: PantallaApp, atrasA: PantallaApp = PantallaApp.INICIO) {
        when {
            destino == PantallaApp.GALERIA -> galeriaAbierta = true
            destino == PantallaApp.INICIO -> { galeriaAbierta = false; salto = null }
            atrasA != PantallaApp.GALERIA -> galeriaAbierta = false
        }
        motor.galeriaAbierta(this, galeriaAbierta)
        if (destino == PantallaApp.INICIO) motor.pedirReservaSinGaleria()
        atras = atrasA
        pantalla = destino
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.i(Registro.TAG, "Arranca la app (versión ${BuildConfig.VERSION_NAME})")
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        setContent { Contenido() }

        lifecycleScope.launch {
            aplicarIntent(intent)
            motor.iniciar()
            motor.sincronizarAhora()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        motor.galeriaAbierta(this, false)
    }

    /** Para probar por adb sin reiniciar: `am start -n local.museumslideshow/.MainActivity --es tema sala-oscura`. */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        lifecycleScope.launch { aplicarIntent(intent) }
    }

    /**
     * Los extras del intent son cambios de ajustes (mismas claves que [PreferenciasDesdeTexto]) y se GUARDAN;
     * `reset` vuelve todo a los valores por defecto (también guardado). `escena` = índice (desde 0) donde arrancar.
     * `pantalla` = inicio, galeria, galerias, apariencia, pase, conexion, protector o estado: abre esa pantalla directo.
     * `protector` = usar o volver: prueba el interruptor del protector sin la pantalla.
     * Sin ningún extra (abrir desde Google TV) abre Inicio.
     */
    private suspend fun aplicarIntent(i: Intent?) {
        val extras = i?.extras
        val texto = HashMap<String, String>()
        if (extras != null) {
            for (k in extras.keySet()) {
                @Suppress("DEPRECATION")
                extras.get(k)?.toString()?.let { texto[k] = it }
            }
        }
        if (texto.isNotEmpty()) {
            val reset = texto.containsKey("reset")
            motor.almacen.cambiar { actual -> PreferenciasDesdeTexto.aplicar(if (reset) Preferencias() else actual, texto) }
            aplicarTildes(texto)
            texto["escena"]?.toIntOrNull()?.let {
                salto = SaltoEscena(it)
                Log.i(Registro.TAG, "Salto a la escena $it")
            }
            texto["protector"]?.let { aplicarProtector(it) }
        }
        PantallaApp.deIntent(texto)?.let { irA(it) }
    }

    /** Extra `protector` = usar | volver: prueba el interruptor por adb y deja en el registro el valor de antes, el de después y el resultado. */
    private suspend fun aplicarProtector(pedido: String) {
        val interruptor = motor.interruptor
        val r = when (pedido.trim().lowercase()) {
            "usar" -> interruptor.usar()
            "volver" -> interruptor.volverAlAnterior()
            else -> {
                Log.w(Registro.TAG, Registro.limpiar("Protector: extra no entendido '$pedido' (usar o volver)"))
                return
            }
        }
        val detalle = when (r) {
            is ResultadoInterruptor.Hecho -> "ok anterior='${r.antes ?: ""}' nuevo='${r.despues ?: ""}'"
            ResultadoInterruptor.SinPermiso -> "falta el permiso WRITE_SECURE_SETTINGS"
            ResultadoInterruptor.Fallo -> "fallo"
        }
        Log.i(Registro.TAG, Registro.limpiar("Protector: $pedido -> $detalle"))
    }

    /** Extras `tildar` y `destildar` (id o nombre de carpeta, sin importar mayúsculas ni tildes): para probar antes de la pantalla Galerías. */
    private suspend fun aplicarTildes(texto: Map<String, String>) {
        val pedidos = listOfNotNull(texto["tildar"]?.let { it to true }, texto["destildar"]?.let { it to false })
        if (pedidos.isEmpty()) return
        val arbol = Arbol(motor.catalogoAhora())
        motor.almacen.cambiar { actual ->
            var p = actual
            for ((nombre, valor) in pedidos) {
                val ids = arbol.resolver(nombre)
                Log.i(Registro.TAG, Registro.limpiar("Tildes: ${if (valor) "tildar" else "destildar"} '$nombre' -> ${ids.size} carpetas"))
                for (id in ids) p = arbol.tildar(p, id, valor)
            }
            p
        }
    }

    @Composable
    private fun Contenido() {
        val p = motor.preferencias
        if (p == null) {
            Box(Modifier.fillMaxSize().background(Color.Black))
            return
        }
        val cat = motor.catalogo
        val sincronizando = motor.sincronizando
        val mensajeSync = motor.mensajeSync
        val reserva = motor.reserva
        val estadoReserva by reserva.estado.collectAsState()
        val tildadas = cat?.galerias.orEmpty().filter { p.estaTildada(it.driveId) }
        val hayObras = tildadas.sumOf { it.obras.size } > 0
        val todos = remember(cat, p.seleccionGalerias, p.galeriasNuevas) { ListaAjustes.construir(tildadas.map { GaleriaOpcion(it.driveId, it.titulo) }) }
        val volver = { irA(atras) }
        Box(Modifier.fillMaxSize()) {
            if (galeriaAbierta) {
                if (cat != null && hayObras) {
                    GaleriaEnPantalla(
                        cat, p,
                        alSalir = { irA(PantallaApp.INICIO) },
                        salto = salto,
                        app = acciones,
                        sincronizando = sincronizando,
                        mensajeSync = mensajeSync,
                        teclasActivas = pantalla == PantallaApp.GALERIA,
                        reserva = reserva,
                    )
                } else {
                    PantallaSinObras(
                        SinObras.motivo(sincronizando, cat, hayTildadas = tildadas.isNotEmpty()),
                        activa = pantalla == PantallaApp.GALERIA,
                        alVerEstado = { irA(PantallaApp.ESTADO, atrasA = PantallaApp.GALERIA) },
                        alVolver = { irA(PantallaApp.INICIO) },
                    )
                }
            }
            key(pantalla) {
                when (pantalla) {
                    PantallaApp.INICIO -> PantallaInicio(
                        cat, p, focoInicio,
                        alCambiarFoco = { focoInicio = it },
                        alAbrir = { destino -> abrirDesdeInicio(destino) },
                        alSalir = { finish() },
                        reserva = estadoReserva,
                    )
                    PantallaApp.GALERIA -> Unit
                    PantallaApp.GALERIAS -> PantallaGalerias(cat, p, cambiar = { t -> motor.cambiarAjustes(t) }, activa = true, alVolver = volver)
                    PantallaApp.APARIENCIA, PantallaApp.PASE, PantallaApp.CONEXION -> {
                        val grupo = when (pantalla) {
                            PantallaApp.APARIENCIA -> GrupoAjustes.APARIENCIA
                            PantallaApp.PASE -> GrupoAjustes.PASE
                            else -> GrupoAjustes.CONEXION
                        }
                        PantallaAjustesGrupo(
                            grupo, todos.delGrupo(grupo), p,
                            activa = true,
                            cambiar = { t -> motor.cambiarAjustes(t) },
                            alVolver = volver,
                            obraPrevia = ResumenInicio.de(cat, p).tapa,
                            galeriaPrevia = ResumenInicio.de(cat, p).tapaGaleriaId,
                            reserva = estadoReserva,
                            sync = if (grupo == GrupoAjustes.CONEXION) DatosSync(sincronizando, mensajeSync, cat?.ultimaSincronizacionBuena) else null,
                            alSincronizar = { motor.sincronizarAhora() },
                        )
                    }
                    PantallaApp.PROTECTOR -> PantallaProtector(motor.interruptor, activa = true, alVolver = volver)
                    PantallaApp.ESTADO -> PantallaEstado(cat, p, sincronizando, alVolver = volver, reserva = estadoReserva)
                }
            }
        }
    }

    private fun abrirDesdeInicio(destino: DestinoInicio) = irA(
        when (destino) {
            DestinoInicio.VER_GALERIA -> PantallaApp.GALERIA
            DestinoInicio.GALERIAS -> PantallaApp.GALERIAS
            DestinoInicio.APARIENCIA -> PantallaApp.APARIENCIA
            DestinoInicio.PASE -> PantallaApp.PASE
            DestinoInicio.CONEXION -> PantallaApp.CONEXION
            DestinoInicio.PROTECTOR -> PantallaApp.PROTECTOR
            DestinoInicio.ESTADO -> PantallaApp.ESTADO
        },
    )
}
