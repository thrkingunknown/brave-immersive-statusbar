package app.braveimmersive.extension;

import android.app.Activity;
import android.os.Build;
import android.os.Looper;
import android.view.View;
import android.view.Window;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.view.WindowManager;
import android.graphics.Color;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.Map;

/** Runtime bridge called by the injected Brave bytecode. */
public final class BraveImmersiveStatusBar {
    private static final Object LOCK = new Object();
    private static final int FULLSCREEN = View.SYSTEM_UI_FLAG_FULLSCREEN;
    private static final int LAYOUT_FULLSCREEN = View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN;
    private static final int LAYOUT_STABLE = View.SYSTEM_UI_FLAG_LAYOUT_STABLE;
    private static final int IMMERSIVE_STICKY = View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY;
    // Chromium's compositor uses these legacy flags when deciding whether to recompute
    // the top-controls viewport. WindowInsetsController visibility alone is insufficient.
    private static final int CHROMIUM_FULLSCREEN_FLAGS = FULLSCREEN | LAYOUT_FULLSCREEN
            | LAYOUT_STABLE | IMMERSIVE_STICKY;
    private static final int EDGE_TO_EDGE_FLAGS = LAYOUT_FULLSCREEN | LAYOUT_STABLE;
    private static final int FALLBACK_HIDE_DP = 48;
    private static final int FALLBACK_SHOW_DP = 8;

    private static WeakReference<Activity> activityRef = new WeakReference<>(null);
    private static boolean originalFullscreen;
    private static boolean originalWindowFullscreen;
    private static boolean originalStatusBarVisible = true;
    private static boolean originalDecorFitsSystemWindows = true;
    private static int originalDecorSystemUiVisibility;
    private static int originalStatusBarColor;
    private static boolean capturedOriginal;
    private static boolean currentlyHidden;
    private static final Map<View, Integer> originalViewFlags = new IdentityHashMap<>();
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
                    currentlyHidden = false;
                    originalViewFlags.clear();
                    topControlsHeight = -1;
                    topControlsMinHeight = 0;
                }

                Window window = activity.getWindow();
                if (window == null) return;
                View decor = window.getDecorView();
                if (!capturedOriginal) {
                    originalDecorSystemUiVisibility = decor.getSystemUiVisibility();
                    originalFullscreen = (originalDecorSystemUiVisibility & FULLSCREEN) != 0;
                    originalWindowFullscreen =
                            (window.getAttributes().flags & WindowManager.LayoutParams.FLAG_FULLSCREEN) != 0;
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
        if (hidden == currentlyHidden) return;
        Window window = activity.getWindow();
        if (window == null) return;

        View decor = window.getDecorView();
        if (hidden) {
            // Match the layout effect of android:windowFullscreen, but only while the toolbar
            // is collapsed. This window flag is important because Chromium may cache/apply its
            // top-inset layout differently from a visibility-only WindowInsetsController hide.
            window.addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);

            // Chromium also reads legacy fullscreen flags from its view hierarchy to
            // decide when to recalculate the compositor viewport. Set the flags on the
            // decor, content root, CompositorViewHolder, and its ancestors—not just the
            // WindowInsetsController—so the web contents can extend into the top inset.
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                window.setDecorFitsSystemWindows(false);
            }
            window.setStatusBarColor(Color.TRANSPARENT);
            applyChromiumFullscreenFlags(window, decor);
            decor.requestApplyInsets();
            decor.requestLayout();
        } else {
            restoreChromiumFullscreenFlags(decor);
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
                currentlyHidden = hidden;
                return;
            }
        }

        int flags = decor.getSystemUiVisibility();
        if (hidden || originalFullscreen) flags |= FULLSCREEN;
        else flags &= ~FULLSCREEN;
        if (flags != decor.getSystemUiVisibility()) decor.setSystemUiVisibility(flags);
        currentlyHidden = hidden;
    }

    /** Apply the flags Chromium uses for fullscreen viewport updates to the relevant hierarchy. */
    private static void applyChromiumFullscreenFlags(Window window, View decor) {
        originalViewFlags.clear();
        ArrayList<View> targets = new ArrayList<>();
        targets.add(decor);
        View content = window.findViewById(android.R.id.content);
        if (content != null && content != decor) targets.add(content);
        collectCompositorAncestors(content != null ? content : decor, targets);
        for (View view : targets) {
            if (view == null) continue;
            originalViewFlags.put(view, view.getSystemUiVisibility() & CHROMIUM_FULLSCREEN_FLAGS);
            int current = view.getSystemUiVisibility();
            int updated = current | CHROMIUM_FULLSCREEN_FLAGS;
            if (updated != current) view.setSystemUiVisibility(updated);
            view.requestLayout();
        }
    }

    /** Find Chromium's compositor and include its parent chain through the content root. */
    private static boolean collectCompositorAncestors(View view, ArrayList<View> targets) {
        if (!(view instanceof android.view.ViewGroup)) return false;
        android.view.ViewGroup group = (android.view.ViewGroup) view;
        for (int i = 0; i < group.getChildCount(); i++) {
            View child = group.getChildAt(i);
            boolean found = child.getClass().getName().contains("CompositorViewHolder");
            if (!found) found = collectCompositorAncestors(child, targets);
            if (found) {
                if (!targets.contains(child)) targets.add(child);
                return true;
            }
        }
        return false;
    }

    /** Restore only fullscreen-related bits, preserving unrelated flags changed by Chromium. */
    private static void restoreChromiumFullscreenFlags(View decor) {
        for (Map.Entry<View, Integer> entry : originalViewFlags.entrySet()) {
            View view = entry.getKey();
            if (view == null) continue;
            int current = view.getSystemUiVisibility();
            int restored = (current & ~CHROMIUM_FULLSCREEN_FLAGS) | entry.getValue();
            if (restored != current) view.setSystemUiVisibility(restored);
            view.requestLayout();
        }
        originalViewFlags.clear();
        if (decor != null) {
            decor.requestApplyInsets();
            decor.requestLayout();
        }
    }

    /** Restore only the layout properties this patch changes, preserving Chromium's other flags. */
    private static void restoreWindowLayout(Window window, View decor) {
        if (originalWindowFullscreen) {
            window.addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
        } else {
            window.clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.setDecorFitsSystemWindows(originalDecorFitsSystemWindows);
        }
        window.setStatusBarColor(originalStatusBarColor);
        int flags = decor.getSystemUiVisibility();
        flags = (flags & ~EDGE_TO_EDGE_FLAGS)
                | (originalDecorSystemUiVisibility & EDGE_TO_EDGE_FLAGS);
        if (flags != decor.getSystemUiVisibility()) decor.setSystemUiVisibility(flags);
        // Window fullscreen/layout changes can otherwise leave Chromium holding its previous
        // top-inset measurement until another unrelated layout pass.
        decor.requestApplyInsets();
    }

    private static void restorePrevious(Activity activity) {
        if (activity == null || !capturedOriginal) return;
        Window window = activity.getWindow();
        if (window == null) return;
        restoreChromiumFullscreenFlags(window.getDecorView());
        restoreWindowLayout(window, window.getDecorView());
        currentlyHidden = false;

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
