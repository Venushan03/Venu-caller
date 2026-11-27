package com.example.telecom

import android.telecom.Call
import android.telecom.InCallService

class PrismInCallService : InCallService() {
    override fun onCreate() {
        super.onCreate()
        CallCoordinator.attachService(this)
    }

    override fun onDestroy() {
        CallCoordinator.detachService()
        super.onDestroy()
    }

    override fun onCallAdded(call: Call) {
        super.onCallAdded(call)
        CallCoordinator.onTelecomCallAdded(this, call)
    }

    override fun onCallRemoved(call: Call) {
        super.onCallRemoved(call)
        CallCoordinator.onTelecomCallRemoved(this, call)
    }
}
