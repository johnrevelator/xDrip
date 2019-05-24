package com.eveningoutpost.dexdrip.insulin.watlaa

import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.Calendar

internal object TimeUtil {


    fun exactTime(time: Calendar): ByteArray {
        val month = time.get(Calendar.MONTH) + 1
        var dayOfWeek = time.get(Calendar.DAY_OF_WEEK)
        if (dayOfWeek == Calendar.SUNDAY) {
            dayOfWeek = 7
        } else {
            dayOfWeek -= 1
        }
        val fractionsOfSecond = (time.get(Calendar.MILLISECOND) * 0.255f).toInt()

        return ByteBuffer.allocate(10)
                .order(ByteOrder.LITTLE_ENDIAN)
                .putShort(time.get(Calendar.YEAR).toShort())
                .put(month.toByte())
                .put(time.get(Calendar.DAY_OF_MONTH).toByte())
                .put(time.get(Calendar.HOUR_OF_DAY).toByte())
                .put(time.get(Calendar.MINUTE).toByte())
                .put(time.get(Calendar.SECOND).toByte())
                .put(dayOfWeek.toByte())
                .put(fractionsOfSecond.toByte())
                .array()
    }

    fun timezoneWithDstOffset(time: Calendar): ByteArray {
        val timezone = time.get(Calendar.ZONE_OFFSET) / 1000 / 60 / 15
        val dstOffset = time.get(Calendar.DST_OFFSET) / 1000 / 60 / 15

        return ByteBuffer.allocate(2)
                .order(ByteOrder.LITTLE_ENDIAN)
                .put(timezone.toByte())
                .put(dstOffset.toByte())
                .array()
    }

}