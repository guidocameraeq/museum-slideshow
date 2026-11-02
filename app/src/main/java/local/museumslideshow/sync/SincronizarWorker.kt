package local.museumslideshow.sync

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
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

        /**
         * Deja el trabajo periódico según el ajuste "Sincronizar cada": 1 h / 6 h / 24 h lo programa (o lo actualiza si ya
         * existía, UPDATE) y "Solo a mano" lo cancela.
         */
        fun programar(context: Context, cada: SincronizarCada) {
            val wm = WorkManager.getInstance(context)
            val horas = cada.horas
            if (horas == null) {
                wm.cancelUniqueWork(NOMBRE)
                return
            }
            val pedido = PeriodicWorkRequestBuilder<SincronizarWorker>(horas.toLong(), TimeUnit.HOURS)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build()
            wm.enqueueUniquePeriodicWork(NOMBRE, ExistingPeriodicWorkPolicy.UPDATE, pedido)
        }
    }
}
