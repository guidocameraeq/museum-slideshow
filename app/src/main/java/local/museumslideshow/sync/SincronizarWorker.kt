package local.museumslideshow.sync

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import local.museumslideshow.ajustes.SincronizarCada

class SincronizarWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        Sincronizacion.ejecutar(applicationContext)
        Result.success()
    }

    companion object {
        private const val NOMBRE = "sincronizar"
        private const val NOMBRE_REINTENTO = "sincronizar-reintento"

        /**
         * Google pidió una pausa hasta `hastaMs`: agenda UN reintento para cuando venza (con la misma red exigida). Con "Solo a mano"
         * no se agenda nada: se espera a que el usuario toque Sincronizar. Un reintento nuevo reemplaza al anterior.
         */
        fun reintentarTrasPausa(context: Context, cada: SincronizarCada, hastaMs: Long, ahoraMs: Long = System.currentTimeMillis()) {
            if (cada.horas == null) return
            val espera = (hastaMs - ahoraMs).coerceAtLeast(0L) + 30_000L
            val pedido = OneTimeWorkRequestBuilder<SincronizarWorker>()
                .setInitialDelay(espera, TimeUnit.MILLISECONDS)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(NOMBRE_REINTENTO, ExistingWorkPolicy.REPLACE, pedido)
        }

        /**
         * Deja el trabajo periódico según el ajuste "Sincronizar cada": 1 h / 6 h / 24 h lo programa (o lo actualiza si ya
         * existía, UPDATE) y "Solo a mano" lo cancela.
         */
        fun programar(context: Context, cada: SincronizarCada) {
            val wm = WorkManager.getInstance(context)
            val horas = cada.horas
            if (horas == null) {
                wm.cancelUniqueWork(NOMBRE)
                wm.cancelUniqueWork(NOMBRE_REINTENTO)
                return
            }
            val pedido = PeriodicWorkRequestBuilder<SincronizarWorker>(horas.toLong(), TimeUnit.HOURS)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build()
            wm.enqueueUniquePeriodicWork(NOMBRE, ExistingPeriodicWorkPolicy.UPDATE, pedido)
        }
    }
}
