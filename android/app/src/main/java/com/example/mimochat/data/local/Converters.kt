package com.example.mimochat.data.local

import androidx.room.TypeConverter
import com.example.mimochat.data.MessageStatus

class Converters {
    @TypeConverter
    fun fromMessageStatus(status: MessageStatus): String = status.name

    @TypeConverter
    fun toMessageStatus(value: String): MessageStatus =
        try { MessageStatus.valueOf(value) } catch (_: Exception) { MessageStatus.SUCCESS }
}
