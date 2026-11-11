package local.museumslideshow.actualizacion

import android.app.PendingIntent
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import local.museumslideshow.BuildConfig
import local.museumslideshow.Registro
import local.museumslideshow.ajustes.BuscarActualizaciones

/** En qué anda la actualización. Lo muestra la pantalla Actualizaciones. */
sealed interface EstadoActualizacion {
    object Quieto : EstadoActualizacion
    object Buscando : EstadoActualizacion
    /** No se pudo preguntar a GitHub (sin internet o GitHub no respondió). */
    object SinConexion : EstadoActualizacion
    data class Bajando(val version: VersionPublicada, val porcentaje: Int) : EstadoActualizacion
    /** Falta "instalar apps desconocidas" para esta app. `sinPantalla` = la tele no abrió esa pantalla sola. */
    data class FaltaPermiso(val version: VersionPublicada, val sinPantalla: Boolean = false) : EstadoActualizacion
    /** Bajada; la tele está mostrando (o va a mostrar) su cartel de "¿Instalar?". */
    data class Instalando(val version: VersionPublicada) : EstadoActualizacion
    /** Falló la bajada o la instalación. */
    data class Fallo(val version: VersionPublicada) : EstadoActualizacion
}

/**
 * Busca versiones nuevas en GitHub Releases, baja el APK y lo instala con `PackageInstaller` (Android muestra su cartel
 * para confirmar: nunca se instala sola). No usa ADB ni comandos de sistema. Una sola instancia por proceso; el estado
 * es de Compose y se toca desde el hilo principal.
 */
class Actualizador private constructor(context: Context) {
    private val ctx = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val guardado = ctx.getSharedPreferences("actualizaciones", Context.MODE_PRIVATE)

    val instalada: String = BuildConfig.VERSION_NAME
    private val codigoInstalada: Int = BuildConfig.VERSION_CODE

    var estado by mutableStateOf<EstadoActualizacion>(EstadoActualizacion.Quieto)
        private set

    /** La última versión que se vio en GitHub (se guarda: Inicio avisa aunque se haya buscado ayer). */
    var ultima by mutableStateOf(guardado.getString(K_ULTIMA, null)?.let { Versiones.leerUltima(it) })
        private set
    var ultimaBusquedaMs by mutableStateOf(guardado.getLong(K_CUANDO, 0L).takeIf { it > 0 })
        private set

    /** La versión de GitHub si es más nueva que la instalada; null si estás al día o no se buscó. */
    val nueva: VersionPublicada? get() = ultima?.takeIf { Versiones.hayNueva(codigoInstalada, it) }

    private val ocupado get() = estado is EstadoActualizacion.Buscando || estado is EstadoActualizacion.Bajando

    /** Busca ya (botón "Buscar ahora"). */
    fun buscar() {
        if (ocupado) return
        estado = EstadoActualizacion.Buscando
        scope.launch {
            val r = withContext(Dispatchers.IO) { pedirUltima() }
            val ahora = System.currentTimeMillis()
            ultimaBusquedaMs = ahora
            guardado.edit().putLong(K_CUANDO, ahora).apply()
            when (r) {
                is Respuesta.Hay -> {
                    ultima = r.version
                    guardado.edit().putString(K_ULTIMA, r.json).apply()
                    Log.i(Registro.TAG, "Actualizaciones: en GitHub está la ${r.version.version} (instalada $instalada)")
                    estado = EstadoActualizacion.Quieto
                }
                Respuesta.Ninguna -> {
                    ultima = null
                    guardado.edit().remove(K_ULTIMA).apply()
                    Log.i(Registro.TAG, "Actualizaciones: no hay ninguna versión publicada")
                    estado = EstadoActualizacion.Quieto
                }
                Respuesta.SinConexion -> estado = EstadoActualizacion.SinConexion
            }
        }
    }

    /** Al arrancar la app o el protector: busca sola si el ajuste lo pide y pasó un día. Nunca instala. */
    fun buscarSiToca(cada: BuscarActualizaciones) {
        if (Versiones.tocaBuscar(cada, ultimaBusquedaMs, System.currentTimeMillis())) buscar()
    }

    /**
     * El botón "Actualizar" (o "Dar el permiso"): si falta el permiso de instalar, abre esa pantalla de la tele;
     * si está, baja la versión nueva y la instala. `pantalla` = la actividad (para abrir los Ajustes de la tele).
     */
    fun alApretarActualizar(pantalla: Context) {
        val v = nueva ?: return
        if (ocupado || estado is EstadoActualizacion.Instalando) return
        if (!ctx.packageManager.canRequestPackageInstalls()) {
            val abrio = try {
                pantalla.startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${ctx.packageName}")))
                true
            } catch (e: ActivityNotFoundException) {
                false
            }
            estado = EstadoActualizacion.FaltaPermiso(v, sinPantalla = !abrio)
            return
        }
        bajarEInstalar(v)
    }

    private fun bajarEInstalar(v: VersionPublicada) {
        estado = EstadoActualizacion.Bajando(v, 0)
        scope.launch {
            val apk = withContext(Dispatchers.IO) { bajar(v) }
            if (apk == null) {
                estado = EstadoActualizacion.Fallo(v)
                return@launch
            }
            val ok = withContext(Dispatchers.IO) { instalar(apk) }
            estado = if (ok) EstadoActualizacion.Instalando(v) else EstadoActualizacion.Fallo(v)
        }
    }

    /** Lo avisa [ReceptorInstalacion]: si se canceló el cartel vuelve a ofrecer la versión; si falló, lo dice. */
    fun alTerminarInstalacion(cancelada: Boolean) {
        val v = (estado as? EstadoActualizacion.Instalando)?.version ?: nueva ?: return
        estado = if (cancelada) EstadoActualizacion.Quieto else EstadoActualizacion.Fallo(v)
    }

    private sealed interface Respuesta {
        class Hay(val version: VersionPublicada, val json: String) : Respuesta
        object Ninguna : Respuesta
        object SinConexion : Respuesta
    }

    private fun pedirUltima(): Respuesta = try {
        val c = URL(Versiones.URL_ULTIMA).openConnection() as HttpURLConnection
        c.connectTimeout = 15_000
        c.readTimeout = 15_000
        c.setRequestProperty("Accept", "application/vnd.github+json")
        c.setRequestProperty("User-Agent", "MuseumSlideshow")
        try {
            when (c.responseCode) {
                200 -> {
                    val json = c.inputStream.bufferedReader().use { it.readText() }
                    Versiones.leerUltima(json)?.let { Respuesta.Hay(it, json) } ?: Respuesta.Ninguna
                }
                404 -> Respuesta.Ninguna
                else -> {
                    Log.w(Registro.TAG, "Actualizaciones: GitHub respondió ${c.responseCode}")
                    Respuesta.SinConexion
                }
            }
        } finally {
            c.disconnect()
        }
    } catch (e: Exception) {
        Log.w(Registro.TAG, "Actualizaciones: no se pudo preguntar a GitHub (${e.javaClass.simpleName})")
        Respuesta.SinConexion
    }

    /** Baja el APK a la caché de la app, mostrando el avance. Null si falló o vino incompleto. */
    private suspend fun bajar(v: VersionPublicada): File? {
        val destino = File(ctx.cacheDir, "actualizacion.apk")
        val parcial = File(ctx.cacheDir, "actualizacion.apk.parcial")
        return try {
            destino.delete()
            val c = URL(v.apkUrl).openConnection() as HttpURLConnection
            c.connectTimeout = 15_000
            c.readTimeout = 30_000
            c.setRequestProperty("User-Agent", "MuseumSlideshow")
            try {
                if (c.responseCode != 200) throw IllegalStateException("HTTP ${c.responseCode}")
                val total = c.contentLengthLong.takeIf { it > 0 } ?: v.apkBytes
                var leidos = 0L
                var ultimoPct = -1
                c.inputStream.use { ent ->
                    parcial.outputStream().use { sal ->
                        val buf = ByteArray(64 * 1024)
                        while (true) {
                            val n = ent.read(buf)
                            if (n < 0) break
                            sal.write(buf, 0, n)
                            leidos += n
                            val pct = if (total > 0) ((leidos * 100) / total).toInt().coerceIn(0, 100) else 0
                            if (pct != ultimoPct) {
                                ultimoPct = pct
                                withContext(Dispatchers.Main) { estado = EstadoActualizacion.Bajando(v, pct) }
                            }
                        }
                    }
                }
                if (v.apkBytes > 0 && leidos != v.apkBytes) throw IllegalStateException("vino incompleto ($leidos de ${v.apkBytes})")
            } finally {
                c.disconnect()
            }
            if (!parcial.renameTo(destino)) throw IllegalStateException("no se pudo guardar")
            Log.i(Registro.TAG, "Actualizaciones: bajada la ${v.version} (${destino.length()} bytes)")
            destino
        } catch (e: Exception) {
            Log.w(Registro.TAG, Registro.limpiar("Actualizaciones: falló la bajada (${e.javaClass.simpleName}: ${e.message})"))
            parcial.delete()
            null
        }
    }

    /** Le pasa el APK a Android; el resultado (y el cartel de confirmar) llega a [ReceptorInstalacion]. */
    private fun instalar(apk: File): Boolean = try {
        val pi = ctx.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL)
        params.setAppPackageName(ctx.packageName)
        val id = pi.createSession(params)
        pi.openSession(id).use { sesion ->
            sesion.openWrite("museum-slideshow.apk", 0, apk.length()).use { sal ->
                apk.inputStream().use { it.copyTo(sal) }
                sesion.fsync(sal)
            }
            val mutable = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0
            val aviso = PendingIntent.getBroadcast(
                ctx, id, Intent(ctx, ReceptorInstalacion::class.java), PendingIntent.FLAG_UPDATE_CURRENT or mutable,
            )
            sesion.commit(aviso.intentSender)
        }
        Log.i(Registro.TAG, "Actualizaciones: APK entregado a Android para instalar")
        true
    } catch (e: Exception) {
        Log.w(Registro.TAG, Registro.limpiar("Actualizaciones: no se pudo instalar (${e.javaClass.simpleName}: ${e.message})"))
        false
    }

    companion object {
        private const val K_ULTIMA = "ultima_json"
        private const val K_CUANDO = "ultima_busqueda"

        @Volatile private var instancia: Actualizador? = null

        fun de(context: Context): Actualizador =
            instancia ?: synchronized(this) {
                instancia ?: Actualizador(context.applicationContext).also { instancia = it }
            }
    }
}
