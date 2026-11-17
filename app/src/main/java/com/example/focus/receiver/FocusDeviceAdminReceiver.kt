package com.example.focus.receiver

import android.app.admin.DeviceAdminReceiver
import android.content.Context
import android.content.Intent
import com.example.focus.data.FocusRepository

class FocusDeviceAdminReceiver : DeviceAdminReceiver() {

    override fun onDisableRequested(context: Context, intent: Intent): CharSequence {
        val state = FocusRepository.state.value
        return if (state.isLocked) {
            "DIQQAT: FocusLock qat'iy rejimida bloklash vaqtida ilovani o'chirish yoki administratsiyani bekor qilish taqiqlangan!"
        } else {
            "FocusLock qurilma administratori o'chirilmoqda."
        }
    }
}
