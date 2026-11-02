package io.github.kadirjohn.kspeed;

import android.content.Context;
import android.util.AttributeSet;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.preference.Preference;
import androidx.preference.PreferenceViewHolder;

import com.google.android.material.button.MaterialButton;

/**
 * Custom Material 3 card preference for the About section.
 * Displays app icon, title, version badge, and action buttons (Check update, GitHub).
 */
public class AboutCardPreference extends Preference {

    public interface Callback {
        void onCheckUpdate();
        void onViewSource();
    }

    private Callback callback;

    public AboutCardPreference(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        setLayoutResource(R.layout.preference_about_card);
        setSelectable(false);
    }

    public void setCallback(Callback callback) {
        this.callback = callback;
    }

    @Override
    public void onBindViewHolder(@NonNull PreferenceViewHolder holder) {
        super.onBindViewHolder(holder);
        Context ctx = getContext();
        TextView versionBadge = (TextView) holder.findViewById(R.id.about_version_badge);
        if (versionBadge != null && ctx != null) {
            versionBadge.setText("v" + UpdateChecker.currentVersion(ctx));
        }

        MaterialButton btnCheck = (MaterialButton) holder.findViewById(R.id.btn_check_update);
        if (btnCheck != null) {
            btnCheck.setOnClickListener(v -> {
                if (callback != null) {
                    callback.onCheckUpdate();
                }
            });
        }

        MaterialButton btnSource = (MaterialButton) holder.findViewById(R.id.btn_view_source);
        if (btnSource != null) {
            btnSource.setOnClickListener(v -> {
                if (callback != null) {
                    callback.onViewSource();
                }
            });
        }
    }
}
