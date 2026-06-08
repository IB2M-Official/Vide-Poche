package com.example.notification

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.example.MainActivity

/**
 * Récepteur d'alarmes (BroadcastReceiver) pour les alertes d'expiration de garantie.
 */
class WarrantyReminderReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val title = intent.getStringExtra(EXTRA_RECEIPT_TITLE) ?: "Produit"
        val receiptId = intent.getLongExtra(EXTRA_RECEIPT_ID, -1L)
        val message = intent.getStringExtra(EXTRA_REMINDER_MESSAGE) 
            ?: "Attention : la garantie pour $title expire bientôt (dans moins de 30 jours) !"

        showNotification(context, title, receiptId, message)
    }

    private fun showNotification(context: Context, productTitle: String, receiptId: Long, message: String) {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        // Créer le canal de notification à partir d'Android O
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Rappels de Garanties",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notifie l'utilisateur avant l'expiration de ses garanties"
                enableVibration(true)
            }
            notificationManager.createNotificationChannel(channel)
        }

        // Action lors du clic sur la notification : Ouvrir MainActivity
        val clickIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            receiptId.hashCode(),
            clickIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info) // Utilise un icône système par défaut robuste
            .setContentTitle("Date limite de garantie proche")
            .setContentText(message)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        notificationManager.notify(receiptId.hashCode(), notification)
    }

    companion object {
        const val CHANNEL_ID = "warranty_expiry_channel"
        const val EXTRA_RECEIPT_TITLE = "extra_receipt_title"
        const val EXTRA_RECEIPT_ID = "extra_receipt_id"
        const val EXTRA_REMINDER_MESSAGE = "extra_reminder_message"
    }
}
