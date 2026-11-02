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
import androidx.preference.Preference;
import androidx.preference.PreferenceCategory;
import androidx.preference.PreferenceGroup;
import androidx.preference.PreferenceGroupAdapter;
import androidx.preference.PreferenceScreen;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

/**
 * ItemDecoration that groups preference items into rounded Material 3 surfaceContainer cards (24dp radius),
 * removing thin divider lines and adding clean whitespace between sections.
 */
public class CategoryCardItemDecoration extends RecyclerView.ItemDecoration {

    private final Paint cardPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint strokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final float cornerRadius;
    private final int marginHorizontal;
    private final int marginBetweenCards;

    public static class GroupRange {
        public int startPos;
        public int endPos;
        public boolean isCustomCard;
    }

    public CategoryCardItemDecoration(Context context) {
        cornerRadius = dpToPx(context, 24);
        marginHorizontal = (int) dpToPx(context, 16);
        marginBetweenCards = (int) dpToPx(context, 16);

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

    public void updateThemeColors(Context context) {
        int surfaceContainer = resolveThemeColor(context,
                com.google.android.material.R.attr.colorSurfaceContainer, 0xFFF2E6E6);
        int outlineVariant = resolveThemeColor(context,
                com.google.android.material.R.attr.colorOutlineVariant, 0x1F000000);
        cardPaint.setColor(surfaceContainer);
        strokePaint.setColor(outlineVariant);
    }

    @Override
    public void getItemOffsets(@NonNull Rect outRect, @NonNull View view,
                               @NonNull RecyclerView parent, @NonNull RecyclerView.State state) {
        RecyclerView.Adapter<?> adapter = parent.getAdapter();
        if (!(adapter instanceof PreferenceGroupAdapter)) {
            super.getItemOffsets(outRect, view, parent, state);
            return;
        }

        PreferenceGroupAdapter pga = (PreferenceGroupAdapter) adapter;
        int pos = parent.getChildAdapterPosition(view);
        if (pos == RecyclerView.NO_POSITION) {
            return;
        }

        Preference pref = pga.getItem(pos);
        if (pref instanceof PreferenceCategory) {
            outRect.top = marginBetweenCards;
            outRect.bottom = (int) dpToPx(view.getContext(), 4);
        } else if (pref instanceof AboutCardPreference) {
            outRect.top = (int) dpToPx(view.getContext(), 4);
            outRect.bottom = marginBetweenCards;
        } else {
            // Check if this is the last item in its category
            boolean isLast = true;
            if (pos + 1 < pga.getItemCount()) {
                Preference next = pga.getItem(pos + 1);
                isLast = (next instanceof PreferenceCategory) || (next instanceof AboutCardPreference);
            }
            if (isLast) {
                outRect.bottom = (int) dpToPx(view.getContext(), 12);
            }
        }
    }

    @Override
    public void onDraw(@NonNull Canvas c, @NonNull RecyclerView parent, @NonNull RecyclerView.State state) {
        RecyclerView.Adapter<?> adapter = parent.getAdapter();
        if (!(adapter instanceof PreferenceGroupAdapter)) {
            return;
        }

        PreferenceGroupAdapter pga = (PreferenceGroupAdapter) adapter;
        List<GroupRange> groups = findGroups(pga);

        for (GroupRange g : groups) {
            if (g.isCustomCard) {
                // AboutCardPreference already renders its own card
                continue;
            }

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

    private List<GroupRange> findGroups(PreferenceGroupAdapter pga) {
        List<GroupRange> groups = new ArrayList<>();
        int count = pga.getItemCount();
        GroupRange current = null;

        for (int i = 0; i < count; i++) {
            Preference p = pga.getItem(i);
            if (p instanceof PreferenceCategory) {
                if (current != null) {
                    current.endPos = i - 1;
                    groups.add(current);
                }
                current = new GroupRange();
                current.startPos = i;
                current.isCustomCard = false;
            } else if (p instanceof AboutCardPreference) {
                if (current != null) {
                    current.endPos = i - 1;
                    groups.add(current);
                }
                GroupRange aboutGroup = new GroupRange();
                aboutGroup.startPos = i;
                aboutGroup.endPos = i;
                aboutGroup.isCustomCard = true;
                groups.add(aboutGroup);
                current = null;
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
