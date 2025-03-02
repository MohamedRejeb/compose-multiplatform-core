/*
 * Copyright 2022 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package androidx.window.demo.common

import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.RecyclerView
import androidx.window.demo.common.databinding.ActivityDisplayFeaturesConfigChangeBinding
import androidx.window.demo.common.infolog.InfoLogAdapter
import androidx.window.demo.common.util.PictureInPictureUtil.appendPictureInPictureMenu
import androidx.window.demo.common.util.PictureInPictureUtil.handlePictureInPictureMenuItem
import androidx.window.layout.FoldingFeature
import androidx.window.layout.WindowInfoTracker
import androidx.window.layout.WindowLayoutInfo
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/** Demo activity that shows all display features and current device state on the screen. */
open class DisplayFeaturesActivity : EdgeToEdgeActivity() {

    private val infoLogAdapter = InfoLogAdapter()
    private val displayFeatureViews = ArrayList<View>()
    private lateinit var binding: ActivityDisplayFeaturesConfigChangeBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityDisplayFeaturesConfigChangeBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val recyclerView = findViewById<RecyclerView>(R.id.infoLogRecyclerView)
        recyclerView.adapter = infoLogAdapter

        lifecycleScope.launch(Dispatchers.Main) {
            // The block passed to repeatOnLifecycle is executed when the lifecycle
            // is at least STARTED and is cancelled when the lifecycle is STOPPED.
            // It automatically restarts the block when the lifecycle is STARTED again.
            lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                // Safely collect from windowInfoRepo when the lifecycle is STARTED
                // and stops collection when the lifecycle is STOPPED
                WindowInfoTracker.getOrCreate(this@DisplayFeaturesActivity)
                    .windowLayoutInfo(this@DisplayFeaturesActivity)
                    .collect { newLayoutInfo ->
                        // New posture information
                        updateStateLog(newLayoutInfo)
                        updateCurrentState(newLayoutInfo)
                    }
            }
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        appendPictureInPictureMenu(menuInflater, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when {
            handlePictureInPictureMenuItem(this, item) -> true
            else -> super.onOptionsItemSelected(item)
        }
    }

    /** Updates the device state and display feature positions. */
    private fun updateCurrentState(windowLayoutInfo: WindowLayoutInfo) {
        // Cleanup previously added feature views
        val rootLayout = binding.featureContainerLayout
        for (featureView in displayFeatureViews) {
            rootLayout.removeView(featureView)
        }
        displayFeatureViews.clear()

        // Update the UI with the current state
        val stateStringBuilder = StringBuilder()
        stateStringBuilder.append(getString(R.string.window_layout)).append(": ")

        // Add views that represent display features
        for (displayFeature in windowLayoutInfo.displayFeatures) {
            val lp = getLayoutParamsForFeatureInFrameLayout(displayFeature, rootLayout)

            // Make sure that zero-wide and zero-high features are still shown
            if (lp.width == 0) {
                lp.width = 1
            }
            if (lp.height == 0) {
                lp.height = 1
            }

            val featureView = View(this)
            val foldFeature = displayFeature as? FoldingFeature
            val color = getColor(R.color.colorFeatureFold)

            featureView.foreground = ColorDrawable(color)

            foldFeature?.let { feature ->
                if (feature.isSeparating) {
                    stateStringBuilder.append(getString(R.string.screens_are_separated))
                } else {
                    stateStringBuilder.append(getString(R.string.screens_are_not_separated))
                }
                stateStringBuilder
                    .append(" - ")
                    .append(
                        if (feature.orientation == FoldingFeature.Orientation.HORIZONTAL) {
                            getString(R.string.screen_is_horizontal)
                        } else {
                            getString(R.string.screen_is_vertical)
                        }
                    )
                    .append(" - ")
                    .append(
                        if (feature.occlusionType == FoldingFeature.OcclusionType.FULL) {
                            getString(R.string.occlusion_is_full)
                        } else {
                            getString(R.string.occlusion_is_none)
                        }
                    )
            }

            rootLayout.addView(featureView, lp)
            featureView.id = View.generateViewId()

            displayFeatureViews.add(featureView)
        }
        binding.currentState.text = stateStringBuilder.toString()
    }

    /** Adds the current state to the text log of changes on screen. */
    private fun updateStateLog(info: Any) {
        infoLogAdapter.append(getCurrentTimeString(), info.toString())
        infoLogAdapter.notifyDataSetChanged()
    }

    private fun getCurrentTimeString(): String {
        val sdf = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault())
        val currentDate = sdf.format(Date())
        return currentDate.toString()
    }
}
