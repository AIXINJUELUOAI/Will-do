package com.antgskds.calendarassistant.shared.ui.permission

import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.SnackbarDuration
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

@Stable
class PermissionGate internal constructor(
    private val snackbarHostState: SnackbarHostState,
    private val scope: CoroutineScope,
) {
    private var pendingRequest: PendingPermissionRequest? = null

    fun require(
        permissionName: String,
        isGranted: () -> Boolean,
        requestPermission: () -> Unit,
        onGranted: () -> Unit,
    ) {
        if (isGranted()) {
            onGranted()
            return
        }

        val request = PendingPermissionRequest(isGranted, requestPermission, onGranted)
        pendingRequest = request
        scope.launch {
            snackbarHostState.currentSnackbarData?.dismiss()
            val result = snackbarHostState.showSnackbar(
                message = "此功能需要$permissionName",
                actionLabel = "去授权",
                duration = SnackbarDuration.Long,
            )
            if (result == SnackbarResult.ActionPerformed) {
                request.requestPermission()
            } else if (pendingRequest === request) {
                pendingRequest = null
            }
        }
    }

    fun resumePending() {
        val request = pendingRequest ?: return
        if (request.isGranted()) {
            pendingRequest = null
            request.onGranted()
        }
    }

    private class PendingPermissionRequest(
        val isGranted: () -> Boolean,
        val requestPermission: () -> Unit,
        val onGranted: () -> Unit,
    )
}

@Composable
fun rememberPermissionGate(snackbarHostState: SnackbarHostState): PermissionGate {
    val scope = rememberCoroutineScope()
    return remember(snackbarHostState, scope) {
        PermissionGate(snackbarHostState, scope)
    }
}
