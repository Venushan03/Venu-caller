package com.example.telecom

import android.os.Build
import android.telecom.Call
import android.telecom.CallScreeningService
import android.telecom.Connection
import com.example.data.ScreeningCache

class PrismCallScreeningService : CallScreeningService() {
    override fun onScreenCall(callDetails: Call.Details) {
        val handleNumber = callDetails.handle?.schemeSpecificPart ?: ""
        val cache = ScreeningCache(applicationContext)
        val isExplicitlyBlocked = cache.isBlocked(handleNumber)

        val failedVerification = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            callDetails.callerNumberVerificationStatus == Connection.VERIFICATION_STATUS_FAILED
        } else {
            false
        }

        val response = if (isExplicitlyBlocked) {
            CallResponse.Builder()
                .setDisallowCall(true)
                .setRejectCall(true)
                .setSilenceCall(true)
                .setSkipNotification(true)
                .build()
        } else if (failedVerification) {
            CallResponse.Builder()
                .setDisallowCall(false)
                .setRejectCall(false)
                .setSilenceCall(false)
                .build()
        } else {
            CallResponse.Builder()
                .setDisallowCall(false)
                .setRejectCall(false)
                .build()
        }

        respondToCall(callDetails, response)
    }
}
