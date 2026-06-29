package me.telosmancy.commands

import com.github.stivais.commodore.Commodore
import me.telosmancy.features.Category
import me.telosmancy.features.ModuleManager

val panicCommand = Commodore("panic") {
    runs {
        for (module in ModuleManager.getModulesByCategory(Category.SECRET)) {
            if (module.enabled) module.toggle()
        }
    }
}
