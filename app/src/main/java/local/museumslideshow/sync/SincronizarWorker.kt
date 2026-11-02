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

class SincronizarWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        Sincronizacion.ejecutar(applicationContext)
        Result.success()
    }

    companion object {
        private const val NOMBRE = "sincronizar"

        /** Trabajo periódico único: si ya está programado, no se toca (KEEP). */
        fun programar(context: Context) {
            val pedido = PeriodicWorkRequestBuilder<SincronizarWorker>(Sincronizacion.PERIODO_HORAS, TimeUnit.HOURS)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build()
            WorkManager.getInstance(context)
                .enqueueUniquePeriodicWork(NOMBRE, ExistingPeriodicWorkPolicy.KEEP, pedido)
        }
    }
}
