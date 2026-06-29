package me.telosmancy

import com.terraformersmc.modmenu.api.ConfigScreenFactory
import com.terraformersmc.modmenu.api.ModMenuApi
import me.telosmancy.clickgui.ClickGUI

class ModMenuIntegration : ModMenuApi {
    override fun getModConfigScreenFactory(): ConfigScreenFactory<ClickGUI> = ConfigScreenFactory { ClickGUI }
}