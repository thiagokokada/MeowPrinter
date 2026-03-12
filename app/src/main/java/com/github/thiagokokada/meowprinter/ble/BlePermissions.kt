package com.github.thiagokokada.meowprinter.ble

import android.Manifest

object BlePermissions {
    val required = arrayOf(
        Manifest.permission.BLUETOOTH_SCAN,
        Manifest.permission.BLUETOOTH_CONNECT
    )
}
