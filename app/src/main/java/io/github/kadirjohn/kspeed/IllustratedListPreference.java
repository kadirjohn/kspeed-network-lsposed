package io.github.kadirjohn.kspeed;

import android.content.Context;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.RadioButton;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.preference.ListPreference;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

/**
 * A ListPreference that presents a Material dialog with illustrations/icons next to each option.
 * Used for Display Mode and Traffic Source selection.
 */
public class IllustratedListPreference extends ListPreference {

    public IllustratedListPreference(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
    }

    public IllustratedListPreference(@NonNull Context context) {
        super(context);
    }

    @Override
    protected void onClick() {
        Context ctx = getContext();
        if (ctx == null) {
            super.onClick();
            return;
        }

        final CharSequence[] entries = getEntries();
        final CharSequence[] values = getEntryValues();
        if (entries == null || values == null || entries.length == 0) {
            super.onClick();
            return;
        }

        final int[] iconResIds = getIconResIdsForKey(getKey());
        if (iconResIds == null || iconResIds.length != entries.length) {
            super.onClick();
            return;
        }

        int currentIdx = findIndexOfValue(getValue());
        final ItemAdapter adapter = new ItemAdapter(ctx, entries, iconResIds, currentIdx);

        new MaterialAlertDialogBuilder(ctx)
                .setTitle(getTitle())
                .setAdapter(adapter, (dialog, which) -> {
                    String selectedVal = values[which].toString();
                    if (callChangeListener(selectedVal)) {
                        setValue(selectedVal);
                    }
                    dialog.dismiss();
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private static int[] getIconResIdsForKey(String key) {
        if (Constants.KEY_MODE.equals(key)) {
            return new int[]{
                    R.drawable.illus_mode_both,
                    R.drawable.illus_mode_combined,
                    R.drawable.illus_mode_down
            };
        }
        if (Constants.KEY_SOURCE.equals(key)) {
            return new int[]{
                    R.drawable.illus_source_total,
                    R.drawable.illus_source_wifi,
                    R.drawable.illus_source_cell
            };
        }
        return null;
    }

    private static class ItemAdapter extends ArrayAdapter<CharSequence> {
        private final int[] iconResIds;
        private final int selectedIdx;

        ItemAdapter(Context context, CharSequence[] items, int[] iconResIds, int selectedIdx) {
            super(context, 0, items);
            this.iconResIds = iconResIds;
            this.selectedIdx = selectedIdx;
        }

        @NonNull
        @Override
        public View getView(int position, @Nullable View convertView, @NonNull ViewGroup parent) {
            if (convertView == null) {
                convertView = LayoutInflater.from(getContext())
                        .inflate(R.layout.dialog_illustrated_list_item, parent, false);
            }

            ImageView iv = convertView.findViewById(R.id.item_icon);
            TextView tv = convertView.findViewById(R.id.item_title);
            RadioButton rb = convertView.findViewById(R.id.item_radio);

            tv.setText(getItem(position));
            if (iconResIds != null && position < iconResIds.length) {
                iv.setImageResource(iconResIds[position]);
                iv.setVisibility(View.VISIBLE);
            } else {
                iv.setVisibility(View.GONE);
            }
            rb.setChecked(position == selectedIdx);

            return convertView;
        }
    }
}
