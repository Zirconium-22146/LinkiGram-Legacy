package app.nimarkogram.messenger.utils.ui

import android.content.Context
import androidx.recyclerview.widget.RecyclerView
import org.telegram.messenger.R
import org.telegram.ui.ActionBar.Theme
import org.telegram.ui.Components.glass.GlassTabView

object MainTabsManager {

    enum class TabType {
        CHATS,
        CONTACTS,
        CHANNELS,
        CALLS
    }

    data class Tab(
        var type: TabType,
        @JvmField
        var enabled: Boolean
    )

    @JvmOverloads
    fun getEnabledTabs(includeSearch: Boolean = false): List<Tab> {
        return listOf(
            Tab(TabType.CHATS, true),
            Tab(TabType.CONTACTS, true),
            Tab(TabType.CHANNELS, true),
            Tab(TabType.CALLS, true)
        )
    }

    fun getAllTabs(): List<Tab> {
        return getEnabledTabs()
    }

    fun createTabView(
        context: Context,
        resourceProvider: Theme.ResourcesProvider?,
        currentAccount: Int,
        type: TabType,
        fromSettings: Boolean,
        showSearch: Boolean
    ): GlassTabView {
        return when (type) {
            TabType.CHATS -> GlassTabView.createMainTab(
                context,
                resourceProvider,
                GlassTabView.TabAnimation.CHATS,
                R.string.MainTabsChats
            )
            TabType.CONTACTS -> GlassTabView.createMainTab(
                context,
                resourceProvider,
                GlassTabView.TabAnimation.CONTACTS,
                R.string.MainTabsContacts
            )
            TabType.CHANNELS -> GlassTabView.createMainTab(
                context,
                resourceProvider,
                GlassTabView.TabAnimation.CHATS,
                R.string.FilterChannels
            )
            TabType.CALLS -> GlassTabView.createMainTab(
                context,
                resourceProvider,
                GlassTabView.TabAnimation.CALLS,
                R.string.MainTabsCalls
            )
        }
    }

    fun getPosition(type: TabType): Int {
        val tabs = getEnabledTabs()
        for (i in tabs.indices) {
            if (tabs[i].type == type) {
                return i
            }
        }
        return -1
    }

    fun hasTab(type: TabType): Boolean = true

    fun saveTabs(tabs: List<Tab>) {
    }

    fun resetTabs() {
    }
}
