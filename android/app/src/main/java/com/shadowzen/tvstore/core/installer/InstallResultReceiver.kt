package com.shadowzen.tvstore.core.installer

import android.content.ActivityNotFoundException
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.os.Build

class InstallResultReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val sessionId = intent.getIntExtra(PackageInstaller.EXTRA_SESSION_ID, -1)
        val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)
        val outcome = if (status == PackageInstaller.STATUS_PENDING_USER_ACTION) {
            showSystemConfirmation(context, intent)
        } else {
            installOutcomeOf(status, intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE))
        }
        InstallEvents.publish(InstallSessionResult(sessionId, outcome))
    }

    private fun showSystemConfirmation(context: Context, intent: Intent): InstallOutcome {
        val confirmation = if (Build.VERSION.SDK_INT >= 33) {
            intent.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(Intent.EXTRA_INTENT)
        } ?: return InstallOutcome.Failed("系统没有提供安装确认界面", null)

        return try {
            context.startActivity(confirmation.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            InstallOutcome.WaitingForUser
        } catch (e: ActivityNotFoundException) {
            InstallOutcome.Failed("无法打开系统安装确认界面", e.message)
        }
    }

    companion object {
        const val ACTION_INSTALL_RESULT = "com.shadowzen.tvstore.action.INSTALL_RESULT"
    }
}
