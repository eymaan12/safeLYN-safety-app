package com.example.SafeLYN

import android.os.Parcel
import android.os.Parcelable

data class Guardian(
    var id: String = "",
    val name: String = "",
    val phoneNumber: String = ""
) : Parcelable {
    constructor(parcel: Parcel) : this(
        parcel.readString() ?: "",
        parcel.readString() ?: "",
        parcel.readString() ?: ""
    )

    override fun writeToParcel(parcel: Parcel, flags: Int) {
        parcel.writeString(id)
        parcel.writeString(name)
        parcel.writeString(phoneNumber)
    }

    override fun describeContents(): Int {
        return 0
    }

    companion object CREATOR : Parcelable.Creator<Guardian> {
        override fun createFromParcel(parcel: Parcel): Guardian {
            return Guardian(parcel)
        }

        override fun newArray(size: Int): Array<Guardian?> {
            return arrayOfNulls(size)
        }
    }
}