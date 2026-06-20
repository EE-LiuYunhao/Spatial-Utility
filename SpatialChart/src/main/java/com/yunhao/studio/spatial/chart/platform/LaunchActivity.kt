package com.yunhao.studio.spatial.chart.platform

import android.os.Bundle
import com.yunhao.studio.spatial.chart.content.SpreadsheetLaunchBridge
import com.pico.spatial.ui.platform.stub.SpatialLaunchActivity

class LaunchActivity : SpatialLaunchActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        SpreadsheetLaunchBridge.handleIntent(intent, contentResolver)
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        SpreadsheetLaunchBridge.handleIntent(intent, contentResolver)
    }
}
