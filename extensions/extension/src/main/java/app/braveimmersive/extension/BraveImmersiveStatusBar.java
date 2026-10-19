package app.braveimmersive.extension;

import android.app.Activity;
import android.os.Looper;
import android.view.View;
import android.view.Window;

import java.lang.ref.WeakReference;

/** Runtime bridge called by the injected Brave bytecode. */
public final class BraveImmersiveStatusBar {
    private static final Object LOCK = new Object();
    private static final int FULLSCREEN = View.SYSTEM_UI_FLAG_FULLSCREEN;
    private static final int FALLBACK_HIDE_DP = 48;
    private static final int FALLBACK_SHOW_DP = 8;

    private static WeakReference<Activity> activityRef = new WeakReference<>(null);
    private static boolean originalFullscreen;
    private static boolean capturedOriginal;
    private static int topControlsHeight = -1;
    private static int topControlsMinHeight;

    private BraveImmersiveStatusBar() {}

    /** Called from ChromeTabbedActivity.onResume(). */
    public static void setCurrentActivity(final Activity activity) {
        if (activity == null) return;
        runOnUiThread(activity, () -> {
            synchronized (LOCK) {
                Activity previous = activityRef.get();
                if (previous != activity) {
                    restorePrevious(previous);
                    activityRef = new WeakReference<>(activity);
                    capturedOriginal = false;
                    topControlsHeight = -1;
                    topControlsMinHeight = 0;
                }

                Window window = activity.getWindow();
                if (window == null) return;
                View decor = window.getDecorView();
                if (!capturedOriginal) {
                    originalFullscreen = (decor.getSystemUiVisibility() & FULLSCREEN) != 0;
                    capturedOriginal = true;
                }
                setHiddenInternal(activity, false);
            }
        });
    }

    /** Receives the browser-controls dimensions so the collapsed threshold is exact. */
    public static void onTopControlsHeightChanged(int height, int minHeight) {
        synchronized (LOCK) {
            topControlsHeight = Math.max(0, height);
            topControlsMinHeight = Math.max(0, Math.min(minHeight, topControlsHeight));
        }
    }

    /** Called on each Chromium browser-controls offset update. */
    public static void onControlsOffsetChanged(final int topOffset, final int minHeightOffset) {
        final Activity activity = activityRef.get();
        if (activity == null || activity.isFinishing() || activity.isDestroyed()) return;

        runOnUiThread(activity, () -> {
            synchronized (LOCK) {
                if (activityRef.get() != activity) return;

                final float density = activity.getResources().getDisplayMetrics().density;
                final int hideSlack = Math.max(1, Math.round(1.0f * density));
                final int showSlack = Math.max(hideSlack + 1, Math.round(4.0f * density));
                final int height = topControlsHeight;
                final int minHeight = height > 0
                        ? Math.max(0, Math.min(minHeightOffset, height))
                        : topControlsMinHeight;

                if (height > 0 && height > minHeight) {
                    // Chromium scrolls the top controls upward until only the minimum-height
                    // portion remains visible. The final offset is -(height - minHeight).
                    final int collapsedOffset = -(height - minHeight);
                    if (topOffset <= collapsedOffset + hideSlack) {
                        setHiddenInternal(activity, true);
                    } else if (topOffset >= collapsedOffset + showSlack) {
                        setHiddenInternal(activity, false);
                    }
                } else {
                    // Fallback for builds that do not report a usable controls height.
                    if (topOffset <= -Math.round(FALLBACK_HIDE_DP * density)) {
                        setHiddenInternal(activity, true);
                    } else if (topOffset >= -Math.round(FALLBACK_SHOW_DP * density)) {
                        setHiddenInternal(activity, false);
                    }
                }
            }
        });
    }

    private static void setHiddenInternal(Activity activity, boolean hidden) {
        Window window = activity.getWindow();
        if (window == null) return;
        View decor = window.getDecorView();
        int flags = decor.getSystemUiVisibility();
        if (hidden) {
            flags |= FULLSCREEN;
        } else if (!originalFullscreen) {
            flags &= ~FULLSCREEN;
        } else {
            flags |= FULLSCREEN;
        }
        if (flags != decor.getSystemUiVisibility()) decor.setSystemUiVisibility(flags);
    }

    private static void restorePrevious(Activity activity) {
        if (activity == null || !capturedOriginal) return;
        Window window = activity.getWindow();
        if (window == null) return;
        View decor = window.getDecorView();
        int flags = decor.getSystemUiVisibility();
        if (originalFullscreen) flags |= FULLSCREEN;
        else flags &= ~FULLSCREEN;
        decor.setSystemUiVisibility(flags);
    }

    private static void runOnUiThread(Activity activity, Runnable action) {
        if (Looper.myLooper() == Looper.getMainLooper()) action.run();
        else activity.runOnUiThread(action);
    }
}
