package com.huawo.nt.sdkdemo.util

import com.huawo.nt.sdkdemo.data.model.SdkException
import com.huawo.sdk.bluetoothsdk.interfaces.callback.BoolCallback
import com.huawo.sdk.bluetoothsdk.interfaces.callback.BoolValueCallback
import com.huawo.sdk.bluetoothsdk.interfaces.callback.CreateBondCallback
import com.huawo.sdk.bluetoothsdk.interfaces.callback.IntValueCallback
import com.huawo.sdk.bluetoothsdk.interfaces.callback.RemoveBondCallback
import com.huawo.sdk.bluetoothsdk.interfaces.callback.StringValueCallback
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine

suspend fun awaitVoid(failMessage: String, block: (BoolCallback) -> Unit) {
    suspendCancellableCoroutine { cont ->
        block(
            object : BoolCallback() {
                override fun onSuccess() {
                    if (cont.isActive) cont.resume(Unit)
                }

                override fun onFail(code: Int) {
                    if (cont.isActive) cont.resumeWithException(SdkException(code, failMessage))
                }
            },
        )
    }
}

suspend fun awaitBoolValue(failMessage: String, block: (BoolValueCallback) -> Unit): Boolean {
    return suspendCancellableCoroutine { cont ->
        block(
            object : BoolValueCallback() {
                override fun onSuccess(value: Boolean) {
                    if (cont.isActive) cont.resume(value)
                }

                override fun onFail(code: Int) {
                    if (cont.isActive) cont.resumeWithException(SdkException(code, failMessage))
                }
            },
        )
    }
}

suspend fun awaitIntValue(failMessage: String, block: (IntValueCallback) -> Unit): Int {
    return suspendCancellableCoroutine { cont ->
        block(
            object : IntValueCallback() {
                override fun onSuccess(value: Int) {
                    if (cont.isActive) cont.resume(value)
                }

                override fun onFail(code: Int) {
                    if (cont.isActive) cont.resumeWithException(SdkException(code, failMessage))
                }
            },
        )
    }
}

suspend fun awaitStringValue(failMessage: String, block: (StringValueCallback) -> Unit): String {
    return suspendCancellableCoroutine { cont ->
        block(
            object : StringValueCallback() {
                override fun onSuccess(value: String?) {
                    if (cont.isActive) cont.resume(value.orEmpty())
                }

                override fun onFail(code: Int) {
                    if (cont.isActive) cont.resumeWithException(SdkException(code, failMessage))
                }
            },
        )
    }
}

suspend fun awaitCreateBond(failMessage: String, block: (CreateBondCallback) -> Unit) {
    suspendCancellableCoroutine { cont ->
        block(
            object : CreateBondCallback() {
                override fun onSuccess() {
                    if (cont.isActive) cont.resume(Unit)
                }

                override fun onFail(code: Int) {
                    if (cont.isActive) cont.resumeWithException(SdkException(code, failMessage))
                }
            },
        )
    }
}

suspend fun awaitRemoveBond(failMessage: String, block: (RemoveBondCallback) -> Unit) {
    suspendCancellableCoroutine { cont ->
        block(
            object : RemoveBondCallback() {
                override fun onSuccess() {
                    if (cont.isActive) cont.resume(Unit)
                }

                override fun onFail(code: Int) {
                    if (cont.isActive) cont.resumeWithException(SdkException(code, failMessage))
                }
            },
        )
    }
}
