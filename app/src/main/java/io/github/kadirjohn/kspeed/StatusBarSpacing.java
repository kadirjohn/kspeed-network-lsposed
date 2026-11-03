package io.github.kadirjohn.kspeed;

import android.util.Log;
import android.view.View;
import android.view.ViewTreeObserver;
import android.widget.LinearLayout;

/** Reserves only the space needed when a following sibling draws back over its flow boundary. */
final class StatusBarSpacing implements View.OnAttachStateChangeListener,
        ViewTreeObserver.OnPreDrawListener {
    private final View indicator;
    private ViewTreeObserver observer;

    StatusBarSpacing(View indicator) {
        this.indicator = indicator;
        indicator.addOnAttachStateChangeListener(this);
    }

    @Override
    public void onViewAttachedToWindow(View v) {
        observer = v.getViewTreeObserver();
        observer.addOnPreDrawListener(this);
    }

    @Override
    public void onViewDetachedFromWindow(View v) {
        if (observer != null && observer.isAlive()) observer.removeOnPreDrawListener(this);
        observer = null;
    }

    @Override
    public boolean onPreDraw() {
        // A changed margin needs another layout. Keep the previous frame until that
        // layout finishes, so the indicator's first visible frame is in its final seat.
        return !update();
    }

    // Pre-draw also sees translation-only changes, without waiting for a sample or layout callback.
    boolean update() {
        if (indicator.getVisibility() != View.VISIBLE || indicator.getWidth() == 0
                || !(indicator.getParent() instanceof LinearLayout)) return false;
        LinearLayout parent = (LinearLayout) indicator.getParent();
        if (parent.getOrientation() != LinearLayout.HORIZONTAL
                || !(indicator.getLayoutParams() instanceof LinearLayout.LayoutParams)) return false;
        // GONE views retain their old frame while siblings close the gap. After
        // becoming VISIBLE (or changing text width), those coordinates are stale.
        if (indicator.isLayoutRequested() || parent.isLayoutRequested()) return false;
        LinearLayout.LayoutParams lp = (LinearLayout.LayoutParams) indicator.getLayoutParams();
        boolean rtl = parent.getLayoutDirection() == View.LAYOUT_DIRECTION_RTL;
        float nearest = rtl ? -Float.MAX_VALUE : Float.MAX_VALUE;
        for (int i = parent.indexOfChild(indicator) + 1; i < parent.getChildCount(); i++) {
            View sibling = parent.getChildAt(i);
            if (sibling.getVisibility() != View.VISIBLE || sibling.getWidth() == 0) continue;
            nearest = rtl ? Math.max(nearest, sibling.getX() + sibling.getWidth())
                    : Math.min(nearest, sibling.getX());
        }
        int margin = lp.getMarginEnd();
        int required = 0;
        if (nearest != Float.MAX_VALUE && nearest != -Float.MAX_VALUE) {
            float overlap = rtl ? nearest - indicator.getLeft() : indicator.getRight() - nearest;
            required = Math.max(0, (int) Math.ceil(margin + overlap));
        }
        if (margin != required) {
            if (Log.isLoggable("KSpeed", Log.DEBUG)) {
                Log.d("KSpeed", "layout view=" + Integer.toHexString(System.identityHashCode(indicator))
                        + " marginEnd=" + margin + "->" + required
                        + " width=" + indicator.getWidth());
            }
            lp.setMarginEnd(required);
            indicator.setLayoutParams(lp);
            return true;
        }
        return false;
    }
}
