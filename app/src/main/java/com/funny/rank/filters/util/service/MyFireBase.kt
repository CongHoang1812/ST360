package com.funny.rank.filters.util.service

import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

/** Nhận tin Firebase Cloud Messaging. Chưa xử lý gì: chỗ để gửi token lên server / tự tạo thông báo sau này. */
class MyFireBase : FirebaseMessagingService() {
    override fun onNewToken(token: String) {
    }

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
    }
}
