package com.antitheft.guard.service

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.antitheft.guard.ui.MainActivity

/** Disarms every feature. The service then sees an empty armed set and stops itself. */
internal const val ACTION_DISARM = "com.antitheft.guard.action.DISARM"

/** Silences a sounding alarm. Deliberately separate from disarming — see [GuardService]. */
internal const val ACTION_STOP_ALARM = "com.antitheft.guard.action.STOP_ALARM"

internal fun Context.openAppIntent(): PendingIntent = PendingIntent.getActivity(
    this,
    REQUEST_OPEN_APP,
    Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
)

internal fun Context.disarmIntent(): PendingIntent = serviceCommand(ACTION_DISARM, REQUEST_DISARM)

internal fun Context.stopAlarmIntent(): PendingIntent =
    serviceCommand(ACTION_STOP_ALARM, REQUEST_STOP_ALARM)

/** The plain intent behind [stopAlarmIntent], for callers that send it directly. */
internal fun Context.stopAlarmCommand(): Intent =
    Intent(this, GuardService::class.java).setAction(ACTION_STOP_ALARM)

private fun Context.serviceCommand(action: String, requestCode: Int): PendingIntent =
    PendingIntent.getService(
        this,
        requestCode,
        Intent(this, GuardService::class.java).setAction(action),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

private const val REQUEST_OPEN_APP = 0
private const val REQUEST_DISARM = 1
private const val REQUEST_STOP_ALARM = 2
