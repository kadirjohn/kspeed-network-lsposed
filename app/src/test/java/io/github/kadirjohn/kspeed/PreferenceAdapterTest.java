package io.github.kadirjohn.kspeed;

import android.content.Context;
import android.view.ViewGroup;
import androidx.preference.Preference;
import androidx.preference.PreferenceCategory;
import androidx.preference.PreferenceManager;
import androidx.preference.PreferenceScreen;
import androidx.recyclerview.widget.RecyclerView;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 36)
public class PreferenceAdapterTest {
    @Test public void hiddenRowsAndCategoriesRefreshDecorationAndNotifyRecycler() {
        Context context = RuntimeEnvironment.getApplication();
        PreferenceScreen screen = new PreferenceManager(context).createPreferenceScreen(context);
        PreferenceCategory category = new PreferenceCategory(context);
        screen.addPreference(category);
        Preference row = new Preference(context);
        category.addPreference(row);
        RecyclerView.Adapter<RecyclerView.ViewHolder> stock = new RecyclerView.Adapter<>() {
            @Override public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup p, int t) { throw new AssertionError(); }
            @Override public void onBindViewHolder(RecyclerView.ViewHolder h, int p) {}
            @Override public int getItemCount() { return 0; }
        };
        SettingsActivity.SettingsFragment.VisiblePrefsAdapter adapter =
                new SettingsActivity.SettingsFragment.VisiblePrefsAdapter(stock, screen);
        final int[] notifications = {0};
        adapter.registerAdapterDataObserver(new RecyclerView.AdapterDataObserver() {
            @Override public void onChanged() { notifications[0]++; }
        });
        assertEquals(2, adapter.size());
        row.setVisible(false);
        stock.notifyItemRangeRemoved(1, 1);
        assertEquals(1, adapter.size());
        assertSame(category, adapter.get(0));
        assertNull(adapter.get(1));
        row.setVisible(true);
        stock.notifyDataSetChanged();
        assertEquals(2, adapter.size());
        category.setVisible(false);
        stock.notifyItemRangeChanged(0, 2, "visibility");
        assertEquals(0, adapter.size());
        assertEquals(3, notifications[0]);
    }
}
