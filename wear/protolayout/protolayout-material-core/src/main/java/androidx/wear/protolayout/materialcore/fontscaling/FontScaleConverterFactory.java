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

package androidx.wear.protolayout.materialcore.fontscaling;

import android.content.res.Configuration;
import android.util.SparseArray;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RestrictTo;
import androidx.annotation.VisibleForTesting;

/** Stores lookup tables for creating {@link FontScaleConverter}s at various scales. */
// This is copied from
// https://cs.android.com/android/_/android/platform/frameworks/base/+/2a4e99a798cc69944f64d54b81aee987fbea45d6:core/java/android/content/res/FontScaleConverterFactory.java
// TODO: b/342359552 - Use Android Platform api instead when it becomes public.
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public class FontScaleConverterFactory {
    private static final float SCALE_KEY_MULTIPLIER = 100f;

    @VisibleForTesting
    static final SparseArray<FontScaleConverter> LOOKUP_TABLES = new SparseArray<>();

    @SuppressWarnings("NonFinalStaticField")
    private static float sMinScaleBeforeCurvesApplied = 1.05f;

    static {
        // These were generated by frameworks/base/tools/fonts/font-scaling-array-generator.js and
        // manually tweaked for optimum readability.
        put(
                /* scaleKey= */ 1.15f,
                new FontScaleConverter(
                        /* fromSp= */ new float[] {8f, 10f, 12f, 14f, 18f, 20f, 24f, 30f, 100},
                        /* toDp= */ new float[] {
                            9.2f, 11.5f, 13.8f, 16.4f, 19.8f, 21.8f, 25.2f, 30f, 100
                        }));

        put(
                /* scaleKey= */ 1.3f,
                new FontScaleConverter(
                        /* fromSp= */ new float[] {8f, 10f, 12f, 14f, 18f, 20f, 24f, 30f, 100},
                        /* toDp= */ new float[] {
                            10.4f, 13f, 15.6f, 18.8f, 21.6f, 23.6f, 26.4f, 30f, 100
                        }));

        put(
                /* scaleKey= */ 1.5f,
                new FontScaleConverter(
                        /* fromSp= */ new float[] {8f, 10f, 12f, 14f, 18f, 20f, 24f, 30f, 100},
                        /* toDp= */ new float[] {12f, 15f, 18f, 22f, 24f, 26f, 28f, 30f, 100}));

        put(
                /* scaleKey= */ 1.8f,
                new FontScaleConverter(
                        /* fromSp= */ new float[] {8f, 10f, 12f, 14f, 18f, 20f, 24f, 30f, 100},
                        /* toDp= */ new float[] {
                            14.4f, 18f, 21.6f, 24.4f, 27.6f, 30.8f, 32.8f, 34.8f, 100
                        }));

        put(
                /* scaleKey= */ 2f,
                new FontScaleConverter(
                        /* fromSp= */ new float[] {8f, 10f, 12f, 14f, 18f, 20f, 24f, 30f, 100},
                        /* toDp= */ new float[] {16f, 20f, 24f, 26f, 30f, 34f, 36f, 38f, 100}));

        sMinScaleBeforeCurvesApplied = getScaleFromKey(LOOKUP_TABLES.keyAt(0)) - 0.02f;
        if (sMinScaleBeforeCurvesApplied <= 1.0f) {
            throw new IllegalStateException(
                    "You should only apply non-linear scaling to font scales > 1");
        }
    }

    private FontScaleConverterFactory() {}

    /**
     * Returns true if non-linear font scaling curves would be in effect for the given scale, false
     * if the scaling would follow a linear curve or for no scaling.
     *
     * <p>Example usage: <code>
     * isNonLinearFontScalingActive(getResources().getConfiguration().fontScale)</code>
     */
    public static boolean isNonLinearFontScalingActive(float fontScale) {
        return fontScale >= sMinScaleBeforeCurvesApplied;
    }

    /**
     * Finds a matching FontScaleConverter for the given fontScale factor.
     *
     * @param fontScale the scale factor, usually from {@link Configuration#fontScale}.
     * @return a converter for the given scale, or null if non-linear scaling should not be used.
     */
    @Nullable
    public static FontScaleConverter forScale(float fontScale) {
        if (!isNonLinearFontScalingActive(fontScale)) {
            return null;
        }

        FontScaleConverter lookupTable = get(fontScale);
        if (lookupTable != null) {
            return lookupTable;
        }

        // Didn't find an exact match: interpolate between two existing tables
        final int index = LOOKUP_TABLES.indexOfKey(getKey(fontScale));
        if (index >= 0) {
            // This should never happen, should have been covered by get() above.
            return LOOKUP_TABLES.valueAt(index);
        }
        // Didn't find an exact match: interpolate between two existing tables
        final int lowerIndex = -(index + 1) - 1;
        final int higherIndex = lowerIndex + 1;
        if (lowerIndex < 0 || higherIndex >= LOOKUP_TABLES.size()) {
            // We have gone beyond our bounds and have nothing to interpolate between. Just give
            // them a straight linear table instead.
            // This works because when FontScaleConverter encounters a size beyond its bounds, it
            // calculates a linear fontScale factor using the ratio of the last element pair.
            return new FontScaleConverter(new float[] {1f}, new float[] {fontScale});
        } else {
            float startScale = getScaleFromKey(LOOKUP_TABLES.keyAt(lowerIndex));
            float endScale = getScaleFromKey(LOOKUP_TABLES.keyAt(higherIndex));
            float interpolationPoint =
                    MathUtils.constrainedMap(
                            /* rangeMin= */ 0f,
                            /* rangeMax= */ 1f,
                            startScale,
                            endScale,
                            fontScale);
            return createInterpolatedTableBetween(
                    LOOKUP_TABLES.valueAt(lowerIndex),
                    LOOKUP_TABLES.valueAt(higherIndex),
                    interpolationPoint);
        }
    }

    @NonNull
    private static FontScaleConverter createInterpolatedTableBetween(
            FontScaleConverter start, FontScaleConverter end, float interpolationPoint) {
        float[] commonSpSizes = new float[] {8f, 10f, 12f, 14f, 18f, 20f, 24f, 30f, 100f};
        float[] dpInterpolated = new float[commonSpSizes.length];

        for (int i = 0; i < commonSpSizes.length; i++) {
            float sp = commonSpSizes[i];
            float startDp = start.convertSpToDp(sp);
            float endDp = end.convertSpToDp(sp);
            dpInterpolated[i] = MathUtils.lerp(startDp, endDp, interpolationPoint);
        }

        return new FontScaleConverter(commonSpSizes, dpInterpolated);
    }

    private static int getKey(float fontScale) {
        return (int) (fontScale * SCALE_KEY_MULTIPLIER);
    }

    private static float getScaleFromKey(int key) {
        return (float) key / SCALE_KEY_MULTIPLIER;
    }

    private static void put(float scaleKey, @NonNull FontScaleConverter fontScaleConverter) {
        LOOKUP_TABLES.put(getKey(scaleKey), fontScaleConverter);
    }

    @Nullable
    private static FontScaleConverter get(float scaleKey) {
        return LOOKUP_TABLES.get(getKey(scaleKey));
    }
}
