package com.yunhao.studio.spatial.chart.content

import android.content.ContentResolver
import android.content.Intent
import android.content.Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION
import android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
import android.net.Uri
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class RangeSelectionTarget(val axisLabel: String) {
    Z("Z"),
    X("X"),
    Y("Y"),
    COLOR("Color"),
}

data class SpreadsheetRangePickerRequest(
    val target: RangeSelectionTarget,
    val document: SpreadsheetDocument,
    val initialExpression: String = "",
)

data class SpreadsheetRangePickerResult(
    val target: RangeSelectionTarget,
    val rangeExpression: String,
)

object SpreadsheetLaunchBridge {
    private val _pendingUri = MutableStateFlow<Uri?>(null)
    val pendingUri = _pendingUri.asStateFlow()
    private val _filePickerVisible = MutableStateFlow(false)
    val filePickerVisible = _filePickerVisible.asStateFlow()
    private val _rangePickerRequest = MutableStateFlow<SpreadsheetRangePickerRequest?>(null)
    val rangePickerRequest = _rangePickerRequest.asStateFlow()
    private val _rangePickerResult = MutableStateFlow<SpreadsheetRangePickerResult?>(null)
    val rangePickerResult = _rangePickerResult.asStateFlow()
    private val _rangePickerVisible = MutableStateFlow(false)
    val rangePickerVisible = _rangePickerVisible.asStateFlow()

    fun handleIntent(intent: Intent?, contentResolver: ContentResolver?) {
        val uri = intent?.data ?: return
        val flags = intent.flags
        if (contentResolver != null && (flags and FLAG_GRANT_PERSISTABLE_URI_PERMISSION) != 0) {
            runCatching {
                contentResolver.takePersistableUriPermission(uri, FLAG_GRANT_READ_URI_PERMISSION)
            }
        }
        _pendingUri.value = uri
    }

    fun consume(uri: Uri) {
        if (_pendingUri.value == uri) {
            _pendingUri.value = null
        }
    }

    fun publish(uri: Uri) {
        _pendingUri.value = uri
    }

    fun setFilePickerVisible(visible: Boolean) {
        _filePickerVisible.value = visible
    }

    fun openRangePicker(request: SpreadsheetRangePickerRequest) {
        _rangePickerRequest.value = request
    }

    fun clearRangePickerRequest() {
        _rangePickerRequest.value = null
    }

    fun publishRangeSelection(result: SpreadsheetRangePickerResult) {
        _rangePickerResult.value = result
    }

    fun consumeRangeSelection() {
        _rangePickerResult.value = null
    }

    fun setRangePickerVisible(visible: Boolean) {
        _rangePickerVisible.value = visible
    }
}
