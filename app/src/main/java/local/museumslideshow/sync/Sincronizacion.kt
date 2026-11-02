package local.museumslideshow.sync

import android.content.Context
import android.util.Log
import java.io.File
import kotlinx.coroutines.flow.first
import local.museumslideshow.BuildConfig
import local.museumslideshow.Registro
import local.museumslideshow.ajustes.AlmacenAjustes
import local.museumslideshow.ajustes.CarpetaDrive
import local.museumslideshow.ajustes.Preferencias

/** Punto único para sincronizar de verdad (lo usan la pantalla, la Barra y el Worker). Lee los ajustes del almacén. */
object Sincronizacion {
    fun dirBase(context: Context) = File(context.filesDir, "galerias")

    /** La carpeta que se sincroniza: la de los ajustes si tiene valor, si no la de `local.properties` (al compilar). */
    fun carpetaEfectiva(p: Preferencias, deFabrica: String): String = p.carpetaDrive?.takeIf { it.isNotBlank() } ?: deFabrica

    suspend fun ejecutar(context: Context): ResultadoSync {
        val ctx = context.applicationContext
        val p = try {
            AlmacenAjustes.de(ctx).flow.first()
        } catch (e: Exception) {
            Log.w(Registro.TAG, "No se pudieron leer los ajustes (${e.javaClass.simpleName}); se usan los de fábrica")
            Preferencias()
        }
        return ejecutar(ctx, carpetaEfectiva(p, CarpetaDrive.extraerId(BuildConfig.DRIVE_CARPETA) ?: BuildConfig.DRIVE_CARPETA), p.espacioMaximo.bytes)
    }

    fun ejecutar(context: Context, carpeta: String, topeBytes: Long): ResultadoSync {
        val s = Sincronizador(
            api = DriveApiHttp(BuildConfig.DRIVE_API_KEY),
            dirBase = dirBase(context),
            procesador = ProcesadorImagenAndroid(),
            topeBytes = topeBytes,
            ahora = { System.currentTimeMillis() },
        )
        val r = try {
            s.sincronizar(carpeta)
        } catch (e: Exception) {
            Log.e(Registro.TAG, "Sincronización abortada (${e.javaClass.simpleName})")
            ResultadoSync(false, 0, 0, 0, emptyList())
        }
        Log.i(Registro.TAG, Registro.limpiar("Sincronización: ok=${r.ok} bajadas=${r.bajadas} borradas=${r.borradas} salteadas=${r.salteadas} problemas=${r.problemas.size}"))
        for (p in r.problemas) {
            Log.i(Registro.TAG, Registro.limpiar("  problema ${p.tipo} galería='${p.galeria}' archivo='${p.archivo ?: ""}' detalle='${p.detalle ?: ""}'"))
        }
        return r
    }
}
