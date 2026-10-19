package app.braveimmersive.extension;

import android.app.Activity;
import android.os.Build;
import android.os.Looper;
import android.view.View;
import android.view.Window;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.graphics.Color;

import java.lang.ref.WeakReference;

/** Runtime bridge called by the injected Brave bytecode. */
public final class BraveImmersiveStatusBar {
    private static final Object LOCK = new Object();
    private static final int FULLSCREEN = View.SYSTEM_UI_FLAG_FULLSCREEN;
    private static final int LAYOUT_FULLSCREEN = View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN;
    private static final int LAYOUT_STABLE = View.SYSTEM_UI_FLAG_LAYOUT_STABLE;
    private static final int EDGE_TO_EDGE_FLAGS = LAYOUT_FULLSCREEN | LAYOUT_STABLE;
    private static final int FALLBACK_HIDE_DP = 48;
    private static final int FALLBACK_SHOW_DP = 8;

    private static WeakReference<Activity> activityRef = new WeakReference<>(null);
    private static boolean originalFullscreen;
    private static boolean originalStatusBarVisible = true;
    private static boolean originalDecorFitsSystemWindows = true;
    private static int originalDecorSystemUiVisibility;
    private static int originalStatusBarColor;
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
                    originalDecorSystemUiVisibility = decor.getSystemUiVisibility();
                    originalFullscreen = (originalDecorSystemUiVisibility & FULLSCREEN) != 0;
                    // Preserve Brave's pre-patch layout mode. Android has no public getter for
                    // Window.getDecorFitsSystemWindows(), so infer it from the initial layout flag.
                    originalDecorFitsSystemWindows =
                            (originalDecorSystemUiVisibility & LAYOUT_FULLSCREEN) == 0;
                    originalStatusBarColor = window.getStatusBarColor();
                    originalStatusBarVisible = !originalFullscreen;
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        WindowInsets insets = decor.getRootWindowInsets();
                        if (insets != null) {
                            originalStatusBarVisible =
                                    insets.isVisible(WindowInsets.Type.statusBars());
                        }
                    }
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
                        ? Math.max(0, Math.min(topControlsMinHeight, height))
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
        if (hidden) {
            // Hide-only leaves the web content laid out below the status-bar inset. Disable
            // decor fitting and lay out behind a transparent status bar while controls collapse.
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                window.setDecorFitsSystemWindows(false);
            }
            window.setStatusBarColor(Color.TRANSPARENT);
            int layoutFlags = decor.getSystemUiVisibility() | EDGE_TO_EDGE_FLAGS;
            if (layoutFlags != decor.getSystemUiVisibility()) {
                decor.setSystemUiVisibility(layoutFlags);
            }
        } else {
            restoreWindowLayout(window, decor);
        }

        // System-bar visibility and content layout are separate: WindowInsetsController hides
        // the bar, while the layout changes above let the webpage occupy the released area.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            WindowInsetsController controller = window.getInsetsController();
            if (controller != null) {
                controller.setSystemBarsBehavior(
                        WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
                if (hidden || !originalStatusBarVisible) {
                    controller.hide(WindowInsets.Type.statusBars());
                } else {
                    controller.show(WindowInsets.Type.statusBars());
                }
                return;
            }
        }

        int flags = decor.getSystemUiVisibility();
        if (hidden || originalFullscreen) flags |= FULLSCREEN;
        else flags &= ~FULLSCREEN;
        if (flags != decor.getSystemUiVisibility()) decor.setSystemUiVisibility(flags);
    }

    /** Restore only the layout properties this patch changes, preserving Chromium's other flags. */
    private static void restoreWindowLayout(Window window, View decor) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.setDecorFitsSystemWindows(originalDecorFitsSystemWindows);
        }
        window.setStatusBarColor(originalStatusBarColor);
        int flags = decor.getSystemUiVisibility();
        flags = (flags & ~EDGE_TO_EDGE_FLAGS)
                | (originalDecorSystemUiVisibility & EDGE_TO_EDGE_FLAGS);
        if (flags != decor.getSystemUiVisibility()) decor.setSystemUiVisibility(flags);
    }

    private static void restorePrevious(Activity activity) {
        if (activity == null || !capturedOriginal) return;
        Window window = activity.getWindow();
        if (window == null) return;
        restoreWindowLayout(window, window.getDecorView());

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            WindowInsetsController controller = window.getInsetsController();
            if (controller != null) {
                controller.setSystemBarsBehavior(
                        WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
                if (originalStatusBarVisible) {
                    controller.show(WindowInsets.Type.statusBars());
                } else {
                    controller.hide(WindowInsets.Type.statusBars());
                }
                return;
            }
        }

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
