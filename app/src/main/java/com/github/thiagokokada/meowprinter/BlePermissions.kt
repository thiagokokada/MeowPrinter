package com.github.thiagokokada.meowprinter

import android.Manifest

object BlePermissions {
    val required = arrayOf(
        Manifest.permission.BLUETOOTH_SCAN,
        Manifest.permission.BLUETOOTH_CONNECT
    )
}
