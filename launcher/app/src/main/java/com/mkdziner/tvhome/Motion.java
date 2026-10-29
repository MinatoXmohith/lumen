package com.mkdziner.tvhome;

import android.content.Context;
import android.provider.Settings;
import android.view.View;
import android.view.animation.Interpolator;
import android.view.animation.PathInterpolator;

import androidx.dynamicanimation.animation.DynamicAnimation;
import androidx.dynamicanimation.animation.SpringAnimation;
import androidx.dynamicanimation.animation.SpringForce;

/**
 * One motion language for the launcher: two curves, durations that ignore the system "animator duration scale"
 * (this TV runs at 0.5x so other apps feel quick; the launcher's own motion keeps its designed length), and
 * interruptible springs for focus, which never jump when keys are pressed quickly.
 */
final class Motion {

    /** Things arriving: quick start, long gentle settle. */
    static final Interpolator EMPHASIZED = new PathInterpolator(0.2f, 0f, 0f, 1f);
    /** Things moving between two states. */
    static final Interpolator STANDARD = new PathInterpolator(0.3f, 0f, 0.1f, 1f);
    /** Things leaving. */
    static final Interpolator EXIT = new PathInterpolator(0.4f, 0f, 1f, 1f);

    private static float scale = 1f;

    private Motion() {}

    static void init(Context c) {
        try {
            scale = Settings.Global.getFloat(c.getContentResolver(), Settings.Global.ANIMATOR_DURATION_SCALE, 1f);
        } catch (Exception e) {
            scale = 1f;
        }
    }

    /** Designed duration in ms, compensated for the system animator scale; 0 when animations are switched off. */
    static long ms(long designed) {
        if (scale <= 0.01f) return 0;
        return (long) (designed / scale);
    }

    /** Springs a view property to target, reusing the spring stored under tagId so it can be interrupted smoothly. */
    static void spring(View v, DynamicAnimation.ViewProperty prop, int tagId, float target, float stiffness, float damping) {
        Object tag = v.getTag(tagId);
        SpringAnimation a;
        if (tag instanceof SpringAnimation) {
            a = (SpringAnimation) tag;
        } else {
            a = new SpringAnimation(v, prop);
            a.setSpring(new SpringForce());
            v.setTag(tagId, a);
        }
        a.getSpring().setStiffness(stiffness).setDampingRatio(damping).setFinalPosition(target);
        a.start();
    }

    /** Focus lift used by every tile: scale, a little rise and a shadow, all on springs. */
    static void focus(View v, boolean focused, float scaleTo, float rise) {
        if (scale <= 0.01f) {   // animations off: jump straight there
            v.setScaleX(focused ? scaleTo : 1f);
            v.setScaleY(focused ? scaleTo : 1f);
            v.setTranslationY(focused ? -rise : 0f);
            return;
        }
        spring(v, DynamicAnimation.SCALE_X, R.id.spring_scale_x, focused ? scaleTo : 1f, 400f, 0.86f);
        spring(v, DynamicAnimation.SCALE_Y, R.id.spring_scale_y, focused ? scaleTo : 1f, 400f, 0.86f);
        spring(v, DynamicAnimation.TRANSLATION_Y, R.id.spring_translation_y, focused ? -rise : 0f, 420f, 0.85f);
        spring(v, DynamicAnimation.TRANSLATION_Z, R.id.spring_translation_z, focused ? rise * 4 : 0f, 300f, 1f);
    }
}
