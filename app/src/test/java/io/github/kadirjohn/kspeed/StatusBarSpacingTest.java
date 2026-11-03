package io.github.kadirjohn.kspeed;

import static org.junit.Assert.*;

import android.content.Context;
import android.graphics.drawable.ColorDrawable;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 36)
public class StatusBarSpacingTest {
    private final Context context = RuntimeEnvironment.getApplication();

    private LinearLayout strip(boolean rtl) {
        LinearLayout strip = new LinearLayout(context);
        strip.setOrientation(LinearLayout.HORIZONTAL);
        strip.setLayoutDirection(rtl ? View.LAYOUT_DIRECTION_RTL : View.LAYOUT_DIRECTION_LTR);
        return strip;
    }

    private View child(LinearLayout strip, int width, int marginStart) {
        View child = new View(context);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(width, 40);
        lp.setMarginStart(marginStart);
        strip.addView(child, lp);
        return child;
    }

    private void layout(LinearLayout strip) {
        strip.measure(View.MeasureSpec.makeMeasureSpec(1000, View.MeasureSpec.AT_MOST),
                View.MeasureSpec.makeMeasureSpec(80, View.MeasureSpec.AT_MOST));
        strip.layout(0, 0, strip.getMeasuredWidth(), strip.getMeasuredHeight());
    }

    private int margin(View view) {
        return ((LinearLayout.LayoutParams) view.getLayoutParams()).getMarginEnd();
    }

    @Test public void ringCannotOverlapSpeedWhenSystemIconsAreEmpty() {
        LinearLayout strip = strip(false);
        View speed = child(strip, 80, 0);
        child(strip, 0, 0);
        View battery = child(strip, 83, 0);
        battery.setVisibility(View.GONE);
        View duo = child(strip, 70, -16);
        StatusBarSpacing spacing = new StatusBarSpacing(speed);
        layout(strip);
        assertTrue(duo.getX() < speed.getRight()); // reproduces the old collision
        spacing.update();
        layout(strip);
        assertTrue(duo.getX() >= speed.getRight());
        assertEquals(16, margin(speed));
        for (int i = 0; i < 5; i++) { spacing.update(); layout(strip); }
        assertEquals(16, margin(speed)); // no growing margin / feedback loop
    }

    @Test public void visibleIconsAbsorbTheRingInsetWithoutExtraSpacing() {
        LinearLayout strip = strip(false);
        View speed = child(strip, 80, 0);
        View icons = child(strip, 50, 0);
        child(strip, 70, -16);
        StatusBarSpacing spacing = new StatusBarSpacing(speed);
        layout(strip);
        spacing.update();
        assertEquals(0, margin(speed));
        icons.setVisibility(View.GONE);
        layout(strip);
        spacing.update();
        layout(strip);
        assertEquals(16, margin(speed));
        icons.setVisibility(View.VISIBLE);
        layout(strip);
        spacing.update();
        layout(strip);
        assertEquals(0, margin(speed));
    }

    @Test public void translatedRingAndItsRemovalUpdateTheReservedSpace() {
        LinearLayout strip = strip(false);
        View speed = child(strip, 80, 0);
        View duo = child(strip, 70, -16);
        duo.setTranslationX(-9);
        StatusBarSpacing spacing = new StatusBarSpacing(speed);
        layout(strip);
        spacing.update();
        layout(strip);
        assertEquals(25, margin(speed));
        assertTrue(duo.getX() >= speed.getRight());
        strip.removeView(duo);
        layout(strip);
        spacing.update();
        assertEquals(0, margin(speed));
    }

    @Test public void rtlUsesTheMirroredEdge() {
        LinearLayout strip = strip(true);
        View speed = child(strip, 80, 0);
        View duo = child(strip, 70, -16);
        StatusBarSpacing spacing = new StatusBarSpacing(speed);
        layout(strip);
        assertTrue(duo.getX() + duo.getWidth() > speed.getLeft());
        spacing.update();
        layout(strip);
        assertEquals(16, margin(speed));
        assertTrue(duo.getX() + duo.getWidth() <= speed.getLeft());
    }

    @Test public void reappearingIndicatorDoesNotMeasureStaleHiddenBounds() {
        for (boolean rtl : new boolean[]{false, true}) {
            LinearLayout strip = strip(rtl);
            View speed = child(strip, 80, 0);
            child(strip, 50, 0);
            child(strip, 70, -16);
            StatusBarSpacing spacing = new StatusBarSpacing(speed);
            layout(strip);
            speed.setVisibility(View.GONE);
            layout(strip);

            // The speed view retains its old frame, while the icons have moved into
            // its former slot. The sampler runs before the next layout traversal.
            speed.setVisibility(View.VISIBLE);
            spacing.update();
            assertEquals("Stale hidden coordinates must not create a temporary gap", 0, margin(speed));

            layout(strip);
            spacing.update();
            assertEquals(0, margin(speed));
        }
    }

    @Test public void preDrawWaitsForSpacingRelayoutAndDetachesCleanly() {
        for (boolean rtl : new boolean[]{false, true}) {
            LinearLayout strip = strip(rtl);
            View speed = child(strip, 80, 0);
            View duo = child(strip, 70, -16);
            StatusBarSpacing spacing = new StatusBarSpacing(speed);
            spacing.onViewAttachedToWindow(speed);
            layout(strip);

            // A frame using the old margin must not be presented to the user.
            assertTrue(speed.getViewTreeObserver().dispatchOnPreDraw());
            assertEquals(16, margin(speed));
            layout(strip);
            assertFalse(speed.getViewTreeObserver().dispatchOnPreDraw());
            assertTrue(rtl ? duo.getX() + duo.getWidth() <= speed.getLeft()
                    : duo.getX() >= speed.getRight());

            // Translation-only changes are corrected at draw time, too.
            duo.setTranslationX(rtl ? 9 : -9);
            assertTrue(speed.getViewTreeObserver().dispatchOnPreDraw());
            assertEquals(25, margin(speed));
            layout(strip);
            assertFalse(speed.getViewTreeObserver().dispatchOnPreDraw());

            spacing.onViewDetachedFromWindow(speed);
            duo.setTranslationX(rtl ? 20 : -20);
            assertFalse(speed.getViewTreeObserver().dispatchOnPreDraw());
            assertEquals(25, margin(speed));
        }
    }

    @Test public void shortSpeedRowKeepsArrowBesideTextAndRowsEndTogether() {
        NetSpeedView speed = new NetSpeedView(context, NetSpeedView.SURFACE_COLLAPSED);
        TextView shortRow = (TextView) speed.getChildAt(0);
        TextView longRow = (TextView) speed.getChildAt(1);
        shortRow.setText("1 KB/s");
        longRow.setText("129 MB/s");
        for (TextView row : new TextView[]{shortRow, longRow}) {
            ColorDrawable arrow = new ColorDrawable(-1);
            arrow.setBounds(0, 0, 12, 12);
            row.setCompoundDrawables(arrow, null, null, null);
        }
        speed.measure(View.MeasureSpec.makeMeasureSpec(1000, View.MeasureSpec.AT_MOST),
                View.MeasureSpec.makeMeasureSpec(200, View.MeasureSpec.AT_MOST));
        speed.layout(0, 0, speed.getMeasuredWidth(), speed.getMeasuredHeight());
        assertTrue(shortRow.getWidth() < longRow.getWidth());
        assertEquals(shortRow.getRight(), longRow.getRight());
        float inkWidth = shortRow.getPaint().measureText(shortRow.getText().toString());
        assertTrue(shortRow.getWidth() - shortRow.getCompoundPaddingLeft() - inkWidth < 2f);
    }
}
