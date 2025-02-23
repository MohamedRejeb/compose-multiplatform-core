/*
 * Copyright 2024 The Android Open Source Project
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

package androidx.compose.integration.macrobenchmark.target

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material.Text
import androidx.compose.ui.util.trace

class InsightActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // artificial trace sections + sleeps to trigger artificial Insights. If these fail to
        // trigger insights, see src/trace_processor/metrics/sql/android/android_startup.sql
        trace("ResourcesManager#getResources") { // duration based insight
            trace("inflate") { // duration based insight
                // currently sleep works for this, but spin loop may more accurately simulate work
                // if this breaks
                @Suppress("BanThreadSleep") Thread.sleep(500)

                repeat(50) { // count based insight
                    trace("Broadcast dispatched SOMETHING") {}
                }
                repeat(100) { // count based insight
                    trace("broadcastReceiveReginald") {}
                }
            }
        }

        setContent { Text("Compose Macrobenchmark Target") }
    }
}
