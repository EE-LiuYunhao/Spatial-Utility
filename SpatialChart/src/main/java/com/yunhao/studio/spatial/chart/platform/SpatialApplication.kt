package com.yunhao.studio.spatial.chart.platform

import android.app.Application
import com.pico.spatial.ui.foundation.dsl.launch
import com.yunhao.studio.spatial.chart.mainApp

class SpatialApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        launch(::mainApp)
    }
}
