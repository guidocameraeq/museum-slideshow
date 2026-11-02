package local.museumslideshow.sync

import android.content.Context
import android.util.Log
import java.io.File
import local.museumslideshow.BuildConfig
import local.museumslideshow.Registro

/** Punto único para sincronizar de verdad (lo usan la pantalla y el Worker). */
object Sincronizacion {
    /** Cada cuántas horas corre solo. El ajuste "Sincronizar cada" llega en la sesión 3: se cambia acá. */
    const val PERIODO_HORAS = 6L

    /** Tope de espacio para lo guardado en la tele (luego será un ajuste). */
    const val TOPE_BYTES = 1L * 1024 * 1024 * 1024

    fun dirBase(context: Context) = File(context.filesDir, "galerias")

    fun ejecutar(context: Context): ResultadoSync {
        val s = Sincronizador(
            api = DriveApiHttp(BuildConfig.DRIVE_API_KEY),
            dirBase = dirBase(context),
            procesador = ProcesadorImagenAndroid(),
            topeBytes = TOPE_BYTES,
            ahora = { System.currentTimeMillis() },
        )
        val r = try {
            s.sincronizar(BuildConfig.DRIVE_CARPETA)
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
