package com.example.autodial.data

import androidx.room.TypeConverter

class Converters {
    @TypeConverter
    fun fromOfferStatus(status: OfferStatus): String {
        return status.name
    }

    @TypeConverter
    fun toOfferStatus(value: String): OfferStatus {
        return OfferStatus.valueOf(value)
    }
}
