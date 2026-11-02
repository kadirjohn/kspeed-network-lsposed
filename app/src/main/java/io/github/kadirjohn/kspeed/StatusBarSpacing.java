package io.github.kadirjohn.kspeed;

import android.view.View;
import android.view.ViewTreeObserver;
import android.widget.LinearLayout;

/** Reserves only the space needed when a following sibling draws back over its flow boundary. */
final class StatusBarSpacing implements View.OnAttachStateChangeListener,
        ViewTreeObserver.OnGlobalLayoutListener {
    private final View indicator;
    private ViewTreeObserver observer;
    private boolean pending;
    private final Runnable refresh = () -> {
        pending = false;
        update();
    };

    StatusBarSpacing(View indicator) {
        this.indicator = indicator;
        indicator.addOnAttachStateChangeListener(this);
    }

    @Override
    public void onViewAttachedToWindow(View v) {
        observer = v.getViewTreeObserver();
        observer.addOnGlobalLayoutListener(this);
    }

    @Override
    public void onViewDetachedFromWindow(View v) {
        if (observer != null && observer.isAlive()) observer.removeOnGlobalLayoutListener(this);
        observer = null;
        indicator.removeCallbacks(refresh);
        pending = false;
    }

    @Override
    public void onGlobalLayout() {
        if (!pending) {
            pending = indicator.post(refresh);
        }
    }

    // Also called by the sampler: translation-only changes do not necessarily trigger layout.
    void update() {
        if (indicator.getVisibility() != View.VISIBLE || indicator.getWidth() == 0
                || !(indicator.getParent() instanceof LinearLayout)) return;
        LinearLayout parent = (LinearLayout) indicator.getParent();
        if (parent.getOrientation() != LinearLayout.HORIZONTAL
                || !(indicator.getLayoutParams() instanceof LinearLayout.LayoutParams)) return;
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
            lp.setMarginEnd(required);
            indicator.setLayoutParams(lp);
        }
    }
}
