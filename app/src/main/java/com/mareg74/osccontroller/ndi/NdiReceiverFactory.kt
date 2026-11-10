package com.mareg74.osccontroller.ndi

object NdiReceiverFactory {
    fun create(): NdiReceiver {
        val sdk = SdkNdiReceiver()
        return if (sdk.isAvailable()) sdk else FakeNdiReceiver(available = false)
    }
}
