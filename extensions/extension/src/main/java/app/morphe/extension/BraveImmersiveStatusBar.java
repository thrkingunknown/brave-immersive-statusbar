/*
 * Copyright (C) 2026 Community patch contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package app.morphe.extension;

import android.app.Activity;
import android.os.Build;
import android.view.View;
import android.view.ViewTreeObserver;
import android.view.Window;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.graphics.Rect;

import java.lang.ref.WeakReference;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Runtime half of the patch. It observes the address-bar view's actual visible bounds rather than
 * relying on View.VISIBLE/GONE, because Chromium commonly hides browser controls using translation.
 */
public final class BraveImmersiveStatusBar {
    private static final float HIDE_THRESHOLD = 0.12f;
    private static final float SHOW_THRESHOLD = 0.30f;

    // Weak keys and weak values prevent this registry from retaining an Activity or its decor tree.
    private static final Map<Activity, WeakReference<ToolbarWatcher>> WATCHERS = new WeakHashMap<>();

    private BraveImmersiveStatusBar() {}

    public static void install(Activity activity) {
        if (activity == null) return;

        try {
            final ToolbarWatcher watcher;
            synchronized (WATCHERS) {
                WeakReference<ToolbarWatcher> reference = WATCHERS.get(activity);
                ToolbarWatcher existing = reference == null ? null : reference.get();
                if (existing != null) {
                    // onResume may run again after Chromium restores its own system-bar state.
                    existing.requestReconcile();
                    return;
                }

                watcher = new ToolbarWatcher(activity);
                WATCHERS.put(activity, new WeakReference<>(watcher));
            }

            // Deferring one UI-queue turn avoids racing the remainder of Activity.onResume().
            activity.getWindow().getDecorView().post(watcher::attach);
        } catch (RuntimeException ignored) {
            // Fail open: a missing/changed view hierarchy must not crash Brave.
        }
    }

    private static final class ToolbarWatcher implements
            ViewTreeObserver.OnGlobalLayoutListener,
            ViewTreeObserver.OnPreDrawListener {
        private final Activity activity;
        private final View decor;
        private final Rect visibleRect = new Rect();

        private WeakReference<View> addressBarReference = new WeakReference<>(null);
        private boolean preDrawRegistered;
        private boolean globalLayoutRegistered;
        private boolean toolbarStateKnown;
        private boolean toolbarHidden;
        private boolean statusBarHiddenByPatch;
        private boolean reconcileRequested;

        ToolbarWatcher(Activity activity) {
            this.activity = activity;
            this.decor = activity.getWindow().getDecorView();
        }

        void requestReconcile() {
            reconcileRequested = true;
        }

        void attach() {
            if (activity.isFinishing()) return;
            findAddressBar();
            if (addressBarReference.get() != null) {
                registerPreDraw();
            } else {
                registerGlobalLayout();
            }
        }

        @Override
        public void onGlobalLayout() {
            if (addressBarReference.get() == null || addressBarReference.get().getParent() == null) {
                findAddressBar();
            }
            if (addressBarReference.get() != null) {
                unregisterGlobalLayout();
                registerPreDraw();
            }
        }

        @Override
        public boolean onPreDraw() {
            try {
                if (activity.isFinishing()) {
                    unregisterPreDraw();
                    unregisterGlobalLayout();
                    return true;
                }

                View addressBar = addressBarReference.get();
                if (addressBar == null || addressBar.getParent() == null) {
                    addressBarReference = new WeakReference<>(null);
                    unregisterPreDraw();
                    registerGlobalLayout();
                    return true;
                }

                float visibleFraction = getVisibleFraction(addressBar);
                if (Float.isNaN(visibleFraction)) return true;
                updateState(visibleFraction);
            } catch (RuntimeException ignored) {
                // Fail open on OEM/Chromium view-hierarchy differences.
            }
            return true;
        }

        private void updateState(float visibleFraction) {
            final boolean currentlyHidden = visibleFraction <= HIDE_THRESHOLD;

            if (reconcileRequested) {
                reconcileRequested = false;
                if (currentlyHidden) {
                    setStatusBarHidden(true);
                    toolbarHidden = true;
                    toolbarStateKnown = true;
                } else {
                    // Explicitly show on resume/reconcile even if a previous system UI state was
                    // established before this watcher started observing the window.
                    setStatusBarHidden(false);
                    toolbarHidden = false;
                    toolbarStateKnown = true;
                }
                return;
            }

            if (!toolbarStateKnown) {
                toolbarStateKnown = true;
                toolbarHidden = currentlyHidden;
                // Establish the desired state at first observation in either direction.
                setStatusBarHidden(toolbarHidden);
                return;
            }

            // Hysteresis avoids flicker during Chromium's toolbar slide animation.
            if (!toolbarHidden && visibleFraction <= HIDE_THRESHOLD) {
                toolbarHidden = true;
                setStatusBarHidden(true);
            } else if (toolbarHidden && visibleFraction >= SHOW_THRESHOLD) {
                toolbarHidden = false;
                setStatusBarHidden(false);
            }
        }

        private float getVisibleFraction(View view) {
            int height = view.getHeight();
            if (height <= 0) return Float.NaN;

            if (view.getVisibility() != View.VISIBLE || !view.isShown() || view.getAlpha() <= 0.05f) {
                return 0.0f;
            }

            if (!view.getGlobalVisibleRect(visibleRect)) return 0.0f;
            int visibleHeight = Math.max(0, Math.min(height, visibleRect.height()));
            return visibleHeight / (float) height;
        }

        private void findAddressBar() {
            // Chromium/Brave resource names are more stable than obfuscated implementation details.
            // Prefer the actual omnibox, with toolbar-container fallbacks for alternate layouts.
            String[] resourceNames = {
                "location_bar",
                "url_bar",
                "toolbar",
                "control_container",
                "location_bar_frame_layout"
            };

            for (String resourceName : resourceNames) {
                int id = activity.getResources().getIdentifier(
                        resourceName, "id", activity.getPackageName());
                if (id == 0) continue;
                View candidate = activity.findViewById(id);
                if (candidate != null && candidate != decor) {
                    addressBarReference = new WeakReference<>(candidate);
                    return;
                }
            }
            addressBarReference = new WeakReference<>(null);
        }

        private void registerGlobalLayout() {
            ViewTreeObserver observer = decor.getViewTreeObserver();
            if (!observer.isAlive() || globalLayoutRegistered) return;
            observer.addOnGlobalLayoutListener(this);
            globalLayoutRegistered = true;
        }

        private void unregisterGlobalLayout() {
            ViewTreeObserver observer = decor.getViewTreeObserver();
            if (!observer.isAlive() || !globalLayoutRegistered) return;
            observer.removeOnGlobalLayoutListener(this);
            globalLayoutRegistered = false;
        }

        private void registerPreDraw() {
            ViewTreeObserver observer = decor.getViewTreeObserver();
            if (!observer.isAlive() || preDrawRegistered) return;
            observer.addOnPreDrawListener(this);
            preDrawRegistered = true;
        }

        private void unregisterPreDraw() {
            ViewTreeObserver observer = decor.getViewTreeObserver();
            if (!observer.isAlive() || !preDrawRegistered) return;
            observer.removeOnPreDrawListener(this);
            preDrawRegistered = false;
        }

        private void setStatusBarHidden(boolean hidden) {
            // Hiding is edge-triggered; showing is also allowed when our local state says visible,
            // because Android/Brave may have changed the window's system-bar state independently.
            if (hidden && statusBarHiddenByPatch) return;

            try {
                Window window = activity.getWindow();
                if (Build.VERSION.SDK_INT >= 30) {
                    WindowInsetsController controller = window.getInsetsController();
                    if (controller != null) {
                        if (hidden) {
                            controller.setSystemBarsBehavior(
                                    WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
                            controller.hide(WindowInsets.Type.statusBars());
                        } else {
                            controller.show(WindowInsets.Type.statusBars());
                        }
                        statusBarHiddenByPatch = hidden;
                        return;
                    }
                }

                View windowDecor = window.getDecorView();
                int flags = View.SYSTEM_UI_FLAG_FULLSCREEN;
                if (Build.VERSION.SDK_INT >= 19) {
                    flags |= View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY;
                }
                int existingFlags = windowDecor.getSystemUiVisibility();
                windowDecor.setSystemUiVisibility(
                        hidden ? (existingFlags | flags) : (existingFlags & ~flags));
                statusBarHiddenByPatch = hidden;
            } catch (RuntimeException ignored) {
                // Keep the browser usable if the window implementation rejects a UI flag change.
            }
        }
    }
}
