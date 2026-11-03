package io.github.kadirjohn.kspeed;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.util.TypedValue;
import android.view.View;

import androidx.annotation.AttrRes;
import androidx.annotation.ColorInt;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.preference.Preference;
import androidx.preference.PreferenceCategory;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

/**
 * ItemDecoration that groups preference items into rounded Material 3 surfaceContainer cards,
 * providing generous internal margins so icons never touch the card outline,
 * and excluding the About section from category outlines.
 *
 * The visible-preference list is supplied by {@link SettingsFragment#onCreateAdapter} via
 * {@link VisiblePreferences}, which flattens the PreferenceScreen using public APIs only
 * (matching androidx.preference's own flattening rules: invisible preferences and
 * non-visible groups are skipped, and a group's children are inlined when the group is on
 * the same screen as its children). This replaces the previous direct use of the
 * library-restricted PreferenceGroupAdapter and removes the RestrictedApi lint errors.
 */
public class CategoryCardItemDecoration extends RecyclerView.ItemDecoration {

    /** A live view of the flattened visible-preference list for the bound adapter. */
    public interface VisiblePreferences {
        @Nullable
        Preference get(int position);

        int size();
    }

    private final Paint cardPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint strokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final float cornerRadius;
    private final int marginHorizontal;
    private final int marginBetweenCards;
    private final VisiblePreferences visible;

    public static class GroupRange {
        public int startPos;
        public int endPos;
    }

    public CategoryCardItemDecoration(Context context, VisiblePreferences visible) {
        this.visible = visible;
        cornerRadius = dpToPx(context, 20);
        // Enlarged outline width (closer to screen edge with 10dp margin)
        marginHorizontal = (int) dpToPx(context, 10);
        marginBetweenCards = (int) dpToPx(context, 14);

        int surfaceContainer = resolveThemeColor(context,
                com.google.android.material.R.attr.colorSurfaceContainer, 0xFFF2E6E6);
        int outlineVariant = resolveThemeColor(context,
                com.google.android.material.R.attr.colorOutlineVariant, 0x1F000000);

        cardPaint.setStyle(Paint.Style.FILL);
        cardPaint.setColor(surfaceContainer);

        strokePaint.setStyle(Paint.Style.STROKE);
        strokePaint.setStrokeWidth(dpToPx(context, 1));
        strokePaint.setColor(outlineVariant);
    }

    @Override
    public void getItemOffsets(@NonNull Rect outRect, @NonNull View view,
                               @NonNull RecyclerView parent, @NonNull RecyclerView.State state) {
        int pos = parent.getChildAdapterPosition(view);
        if (pos == RecyclerView.NO_POSITION) {
            return;
        }

        Preference pref = visible.get(pos);
        if (pref == null) {
            return;
        }
        if (pref instanceof PreferenceCategory) {
            outRect.top = marginBetweenCards;
            outRect.bottom = (int) dpToPx(view.getContext(), 2);
        } else if (pref instanceof AboutCardPreference) {
            outRect.top = marginBetweenCards;
            outRect.bottom = (int) dpToPx(view.getContext(), 24);
        } else {
            boolean isLast = true;
            Preference next = visible.get(pos + 1);
            if (next != null) {
                isLast = (next instanceof PreferenceCategory) || (next instanceof AboutCardPreference);
            }
            if (isLast) {
                outRect.bottom = (int) dpToPx(view.getContext(), 12);
            }
        }
    }

    @Override
    public void onDraw(@NonNull Canvas c, @NonNull RecyclerView parent, @NonNull RecyclerView.State state) {
        List<GroupRange> groups = findGroups();

        for (GroupRange g : groups) {
            View firstChild = null;
            View lastChild = null;

            for (int i = 0; i < parent.getChildCount(); i++) {
                View child = parent.getChildAt(i);
                int pos = parent.getChildAdapterPosition(child);
                if (pos >= g.startPos && pos <= g.endPos) {
                    if (firstChild == null || child.getTop() < firstChild.getTop()) {
                        firstChild = child;
                    }
                    if (lastChild == null || child.getBottom() > lastChild.getBottom()) {
                        lastChild = child;
                    }
                }
            }

            if (firstChild != null && lastChild != null) {
                float left = marginHorizontal;
                float right = parent.getWidth() - marginHorizontal;
                float top = firstChild.getTop();
                float bottom = lastChild.getBottom();

                RectF rect = new RectF(left, top, right, bottom);
                c.drawRoundRect(rect, cornerRadius, cornerRadius, cardPaint);
                c.drawRoundRect(rect, cornerRadius, cornerRadius, strokePaint);
            }
        }
    }

    private List<GroupRange> findGroups() {
        List<GroupRange> groups = new ArrayList<>();
        int count = visible.size();
        GroupRange current = null;

        for (int i = 0; i < count; i++) {
            Preference p = visible.get(i);
            if (p == null) {
                continue;
            }
            // Completely exclude About section from category outline cards
            if (p instanceof AboutCardPreference || "cat_about".equals(p.getKey())) {
                if (current != null) {
                    current.endPos = i - 1;
                    groups.add(current);
                    current = null;
                }
                continue;
            }

            if (p instanceof PreferenceCategory) {
                if (current != null) {
                    current.endPos = i - 1;
                    groups.add(current);
                }
                current = new GroupRange();
                current.startPos = i;
            }
        }
        if (current != null) {
            current.endPos = count - 1;
            groups.add(current);
        }
        return groups;
    }

    private static float dpToPx(Context c, float dp) {
        return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dp, c.getResources().getDisplayMetrics());
    }

    @ColorInt
    private static int resolveThemeColor(Context context, @AttrRes int attr, @ColorInt int fallback) {
        TypedValue value = new TypedValue();
        if (context.getTheme().resolveAttribute(attr, value, true)) {
            return value.data;
        }
        return fallback;
    }
}
