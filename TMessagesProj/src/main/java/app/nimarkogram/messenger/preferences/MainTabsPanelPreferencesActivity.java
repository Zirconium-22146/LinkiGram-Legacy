package app.nimarkogram.messenger.preferences;

import android.view.View;

import java.util.ArrayList;

import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.ui.Components.UItem;
import org.telegram.ui.Components.UniversalAdapter;

import app.nimarkogram.messenger.NimarkoConfig;
import app.nimarkogram.messenger.preferences.helpers.PopupHelper;
import app.nimarkogram.messenger.preferences.helpers.SettingsHelper;

/**
 * LinkiGram Legacy 1.4: панель вкладок — положение, состав и третья вкладка.
 * Любая правка пересобирает главный экран, чтобы инсеты и отступы контента
 * пересчитались под новое положение полосы.
 */
public class MainTabsPanelPreferencesActivity extends BasePreferencesActivity {

    private static final int ID_POSITION = 1;
    private static final int ID_SHOW_CONTACTS = 2;
    private static final int ID_SHOW_CHANNELS = 3;
    private static final int ID_SHOW_CALLS = 4;
    private static final int ID_THIRD_XCFG = 5;
    private static final int ID_RESET = 6;

    @Override
    public String getTitle() {
        return LocaleController.getString(R.string.NM_TP_Title);
    }

    @Override
    public void fillItems(ArrayList<UItem> items, UniversalAdapter adapter) {
        items.add(UItem.asHeader(LocaleController.getString(R.string.NM_TP_Position)));
        items.add(UItem.asButton(ID_POSITION,
                LocaleController.getString(R.string.NM_TP_Position), getPositionValue()));
        items.add(UItem.asShadow(LocaleController.getString(R.string.NM_TP_PositionDesc)));

        items.add(UItem.asHeader(LocaleController.getString(R.string.NM_TP_Visible)));
        items.add(UItem.asCheck(ID_SHOW_CONTACTS,
                LocaleController.getString(R.string.NM_TP_ShowContacts))
                .setChecked(NimarkoConfig.mainTabsShowContacts));
        items.add(UItem.asCheck(ID_SHOW_CHANNELS,
                LocaleController.getString(R.string.NM_TP_ShowChannels))
                .setChecked(NimarkoConfig.mainTabsShowChannels)
                .setEnabled(!NimarkoConfig.mainTabsThirdXcfg));
        items.add(UItem.asCheck(ID_SHOW_CALLS,
                LocaleController.getString(R.string.NM_TP_ShowCalls))
                .setChecked(NimarkoConfig.mainTabsShowCalls));
        items.add(UItem.asShadow(LocaleController.getString(R.string.NM_TP_VisibleDesc)));

        items.add(UItem.asHeader(LocaleController.getString(R.string.NM_TP_Third)));
        items.add(SettingsHelper.asSwitchCG(ID_THIRD_XCFG,
                LocaleController.getString(R.string.NM_TP_ThirdXcfg),
                LocaleController.getString(R.string.NM_TP_ThirdXcfgDesc))
                .setChecked(NimarkoConfig.mainTabsThirdXcfg));

        items.add(UItem.asButton(ID_RESET, R.drawable.msg_reset, LocaleController.getString(R.string.Reset)));
        items.add(UItem.asShadow(null));
    }

    @Override
    public void onClick(UItem item, View view, int position, float x, float y) {
        final int id = item.id;
        if (id == ID_POSITION) {
            final ArrayList<String> options = new ArrayList<>();
            options.add(LocaleController.getString(R.string.NM_TP_PositionTop));
            options.add(LocaleController.getString(R.string.NM_TP_PositionBottom));
            PopupHelper.show(options, LocaleController.getString(R.string.NM_TP_Position),
                    NimarkoConfig.mainTabsPosition, getContext(), i -> {
                        if (NimarkoConfig.mainTabsPosition == i) {
                            return;
                        }
                        NimarkoConfig.setMainTabsPosition(i);
                        updateButtonValue(view, getPositionValue());
                        rebuildMainTabs();
                    });
        } else if (id == ID_SHOW_CONTACTS) {
            NimarkoConfig.setMainTabsShowContacts(!NimarkoConfig.mainTabsShowContacts);
            applyCheck(item, view, NimarkoConfig.mainTabsShowContacts);
            rebuildMainTabs();
        } else if (id == ID_SHOW_CHANNELS) {
            NimarkoConfig.setMainTabsShowChannels(!NimarkoConfig.mainTabsShowChannels);
            applyCheck(item, view, NimarkoConfig.mainTabsShowChannels);
            rebuildMainTabs();
        } else if (id == ID_SHOW_CALLS) {
            NimarkoConfig.setMainTabsShowCalls(!NimarkoConfig.mainTabsShowCalls);
            applyCheck(item, view, NimarkoConfig.mainTabsShowCalls);
            rebuildMainTabs();
        } else if (id == ID_THIRD_XCFG) {
            NimarkoConfig.setMainTabsThirdXcfg(!NimarkoConfig.mainTabsThirdXcfg);
            applyCheck(item, view, NimarkoConfig.mainTabsThirdXcfg);
            if (listView != null && listView.adapter != null) {
                listView.adapter.update(true);
            }
            rebuildMainTabs();
        } else if (id == ID_RESET) {
            NimarkoConfig.setMainTabsPosition(NimarkoConfig.MAIN_TABS_POSITION_TOP);
            NimarkoConfig.setMainTabsShowContacts(true);
            NimarkoConfig.setMainTabsShowChannels(true);
            NimarkoConfig.setMainTabsShowCalls(true);
            NimarkoConfig.setMainTabsThirdXcfg(false);
            if (listView != null && listView.adapter != null) {
                listView.adapter.update(true);
            }
            rebuildMainTabs();
        }
    }

    private String getPositionValue() {
        return LocaleController.getString(NimarkoConfig.mainTabsPosition == NimarkoConfig.MAIN_TABS_POSITION_TOP
                ? R.string.NM_TP_PositionValueTop : R.string.NM_TP_PositionValueBottom);
    }

    private void updateButtonValue(View view, String value) {
        try {
            SettingsHelper.updateButtonValue(view, value);
        } catch (Throwable ignore) {
        }
    }

    private void applyCheck(UItem item, View view, boolean value) {
        item.checked = value;
        updateCheckState(view, value);
    }

    private void rebuildMainTabs() {
        if (getParentLayout() != null) {
            getParentLayout().rebuildAllFragmentViews(false, false);
        }
    }
}
