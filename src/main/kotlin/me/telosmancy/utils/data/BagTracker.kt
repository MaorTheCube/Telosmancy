package me.telosmancy.utils.data

import me.telosmancy.Telosmancy
import me.telosmancy.events.ChatPacketEvent
import me.telosmancy.events.core.EventBus
import me.telosmancy.events.core.on
import me.telosmancy.features.impl.tracking.PityCounterModule
import me.telosmancy.utils.ChatManager.hideMessage
import me.telosmancy.utils.LocalAPI
import me.telosmancy.utils.Message
import me.telosmancy.utils.TabListUtils
import me.telosmancy.utils.data.persistence.TrackingKey
import me.telosmancy.utils.data.persistence.TypeSafeDataAccess
import me.telosmancy.utils.noControlCodes
import me.telosmancy.utils.toNative
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.minecraft.client.multiplayer.chat.GuiMessageTag
import net.minecraft.network.chat.Component
import net.minecraft.world.entity.item.ItemEntity

/**
 * Bag tracking system that detects bag drops and manages pity counters.
 *
 * Now uses type-safe TrackingKey API for compile-time safety.
 *
 * Lifetime Stats Tracked:
 * - companionBags: Companion bag drops
 * - royalBags: Royal bag drops
 * - bloodshotBags: Bloodshot totem drops (from various bosses)
 * - voidbound: Voidbound totem drops (Nihility)
 * - unholy: Unholy totem drops (Holy Cross, Pendant of Sin)
 * - eventBags: Event bag drops (Halloween, Valentine, Christmas)
 * - totalRuns: Total boss runs (incremented on boss defeat)
 *
 * Item Drop Detection:
 * - When a bag animation plays, starts scanning for dropped items near player
 * - Scans for 20 ticks (1 second) after bag drop
 * - Detects specific items by texture path and resets their pity counters
 */
object BagTracker {

    /** Pairs a base item with a flag for its shiny variant, so shiny pity is tracked separately. */
    private data class DropVariant(val item: Item, val shiny: Boolean) {
        val pityKey: String get() = if (shiny) "${item.name}-shiny" else item.name
    }

    private var currentBoss: String = ""

    // Item scanning state
    private var ticksRemaining = 0
    private val detectedVariants = mutableSetOf<DropVariant>()
    private val recentPityCache = mutableMapOf<DropVariant, Int>()
    private val recentPityCacheTime = mutableMapOf<DropVariant, Long>()
    
    init {
        // Register tick handler for item scanning
        ClientTickEvents.END_CLIENT_TICK.register { client ->
            if (client.player != null && client.level != null) {
                onTick()
            }
        }
        
        EventBus.subscribe(this)
        
        // Register custom ChatPacketEvent handler to modify/hide messages
        on<ChatPacketEvent> {
            this.handleChatMessage()
        }
    }
    
    // ==================== BAG DROP HANDLERS ====================
    
    /**
     * Handle lootbag open sound - starts scanning for dropped items.
     * This is the primary trigger for item scanning (triggered by sound event).
     * Called from SoundSystemMixin when "noise:player.bags.open" sound plays.
     */
    fun handleLootbagOpen() {
        Telosmancy.logger.info("Lootbag open sound detected, starting item scanning")
        startItemScanning()
    }
    
    /**
     * Handle bloodshot bag drop - increments lifetime stat
     */
    @JvmOverloads
    fun onBloodshotBagDrop(itemName: String? = null) {
        currentBoss = LocalAPI.getCurrentCharacterFighting()
        Telosmancy.logger.info("Bloodshot bag dropped by boss: $currentBoss")
        
        // Increment bloodshot bag lifetime stat
        TypeSafeDataAccess.increment(TrackingKey.LifetimeStat.BloodshotBags)
    }
    
    /**
     * Handle unholy bag drop - increments lifetime stat
     */
    @JvmOverloads
    fun onUnholyBagDrop(itemName: String? = null) {
        currentBoss = LocalAPI.getCurrentCharacterFighting()
        Telosmancy.logger.info("Unholy bag dropped by boss: $currentBoss")
        
        // Increment unholy stat
        TypeSafeDataAccess.increment(TrackingKey.LifetimeStat.UnholyBags)
    }
    
    /**
     * Handle voidbound bag drop - increments lifetime stat
     */
    @JvmOverloads
    fun onVoidboundBagDrop(itemName: String? = null) {
        currentBoss = LocalAPI.getCurrentCharacterFighting()
        Telosmancy.logger.info("Voidbound bag dropped by boss: $currentBoss")
        
        // Increment voidbound bag lifetime stat
        TypeSafeDataAccess.increment(TrackingKey.LifetimeStat.VoidboundBags)
    }
    
    /**
     * Handle royal bag drop
     * Increments royalBags stat
     */
    fun onRoyalBagDrop() {
        Telosmancy.logger.info("Royal bag dropped")
        TypeSafeDataAccess.increment(TrackingKey.LifetimeStat.RoyalBags)
    }
    
    /**
     * Handle companion bag drop
     * Increments companionBags stat
     */
    fun onCompanionBagDrop() {
        Telosmancy.logger.info("Companion bag dropped")
        TypeSafeDataAccess.increment(TrackingKey.LifetimeStat.CompanionBags)
    }
    
    /**
     * Handle event bag drop (Halloween, Valentine, Christmas)
     * Increments eventBags stat
     */
    fun onEventBagDrop() {
        Telosmancy.logger.info("Event bag dropped")
        TypeSafeDataAccess.increment(TrackingKey.LifetimeStat.EventBags)
    }
    
    // ==================== BOSS DEFEAT HANDLER ====================
    
    /**
     * Handle boss defeat - increments totalRuns for ANY dungeon/boss defeated
     * Also increments pity counters for ALL items that the boss can drop
     * This should be called when a boss is defeated (detected via chat messages)
     */
    fun onBossDefeat(bossName: String) {
        Telosmancy.logger.info("Boss defeated: $bossName")
        
        // Increment total runs for ANY boss defeat
        TypeSafeDataAccess.increment(TrackingKey.LifetimeStat.TotalRuns)
        
        // Increment pity counters for all items this boss can drop
        incrementPityCounters(bossName)
    }
    
    /**
     * Increment pity counters for all items that a boss can drop.
     * Uses Item-based tracking (TrackingKey.PityCounter(item.name)).
     */
    private fun incrementPityCounters(bossName: String) {
        val boss = BossData.findByKey(bossName)
        if (boss == null) {
            Telosmancy.logger.warn("Boss not found in BossData: $bossName")
            return
        }
        
        if (boss.items.isEmpty()) {
            Telosmancy.logger.debug("Boss $bossName has no items configured, skipping pity increment")
            return
        }
        
        Telosmancy.logger.info("Incrementing pity counters for ${boss.items.size} items from boss: $bossName")
        
        // Increment pity for ALL items this boss can drop
        for (item in boss.items) {
            val pityKey = TrackingKey.PityCounter(item.name)
            val newCount = TypeSafeDataAccess.increment(pityKey)
            Telosmancy.logger.debug("  ${item.displayName}: $newCount")
        }
    }
    
    // ==================== UTILITY METHODS ====================
    
    /**
     * Get current boss being fought
     */
    fun getCurrentBoss(): String = currentBoss
    
    // ==================== ITEM SCANNING ====================
    
    /**
     * Start scanning for dropped items near the player.
     * Called when a bag animation plays.
     */
    private fun startItemScanning() {
        ticksRemaining = 20 // Scan for 1 second (20 ticks)
        detectedVariants.clear()
        Telosmancy.logger.info("Started item scanning for 20 ticks")
    }
    
    /**
     * Tick handler for scanning dropped items.
     * Should be called every client tick.
     */
    fun onTick() {
        if (ticksRemaining <= 0) return
        
        val player = Telosmancy.mc.player ?: return
        val level = Telosmancy.mc.level ?: return
        
        // Create bounding box around player (10 block radius)
        val box = player.boundingBox.inflate(10.0)
        
        // Scan for item entities
        val itemEntities = level.getEntitiesOfClass(ItemEntity::class.java, box) { it.isAlive && it.isCurrentlyGlowing}
        
        Telosmancy.logger.debug("Scanning for items: ticksRemaining=$ticksRemaining, entities=${itemEntities.size}")
        
        for (itemEntity in itemEntities) {
            val itemStack = itemEntity.item
            
            // Get texture path from item
            val resourceLocation = try {
                itemStack.components.get(net.minecraft.core.component.DataComponents.ITEM_MODEL)
            } catch (e: Exception) {
                null
            }
            
            if (resourceLocation == null) continue
            
            // Convert ResourceLocation to string format "namespace:path"
            val texturePath = "${resourceLocation.namespace}:${resourceLocation.path}"

            Telosmancy.logger.debug("Found item entity with texture: $texturePath")

            // Detect shiny variant by texture path suffix
            val isShiny = texturePath.endsWith("-shiny")
            val lookupPath = if (isShiny) texturePath.dropLast("-shiny".length) else texturePath

            val droppedItem = resolveContextualItem(lookupPath)
            if (droppedItem == null) continue
            if (isShiny && !droppedItem.hasShiny) continue

            val variant = DropVariant(droppedItem, isShiny)
            if (!detectedVariants.contains(variant)) {
                val pityKey = TrackingKey.PityCounter(variant.pityKey)
                val preResetPity = TypeSafeDataAccess.get(pityKey) ?: 0
                recentPityCache[variant] = preResetPity
                recentPityCacheTime[variant] = System.currentTimeMillis()

                if (PityCounterModule.useCustomMsg) {
                    sendPityResetMessage(droppedItem, preResetPity, isShiny)
                }

                TypeSafeDataAccess.reset(pityKey)
                Telosmancy.logger.info("Detected and reset pity for ${droppedItem.displayName}${if (isShiny) " (shiny)" else ""} (was at $preResetPity)")

                detectedVariants.add(variant)
            }
        }
        
        ticksRemaining--
    }
    
    /**
     * Handle chat messages to detect item drops from "X got Y" messages.
     */
    private fun ChatPacketEvent.handleChatMessage() {
        val plainText = value.noControlCodes
        
        // Look for " got " pattern (e.g., "PlayerName got Holy Cross")
        val gotIndex = plainText.lastIndexOf(" got ")
        if (gotIndex == -1) return
        
        // Extract player name
        val spaceBeforePlayer = plainText.lastIndexOf(' ', gotIndex - 1)
        if (spaceBeforePlayer == -1) return
        
        // Extract player name and check against current player
        val playerName = plainText.substring(spaceBeforePlayer + 1, gotIndex)
        val currentPlayerName = Telosmancy.mc.player?.gameProfile?.name ?: return
        
        // Only modify messages and process logic if the drop belongs to the current player
        if (playerName != currentPlayerName) return
        
        // Find the longest matching item display name after " got "
        var droppedItem: Item? = null
        for (item in Item.all) {
            val itemDisplayName = item.displayName
            val itemStartIndex = gotIndex + 5 // " got ".length
            
            if (plainText.indexOf(itemDisplayName, itemStartIndex) == itemStartIndex) {
                // Prefer longer matches (e.g., "Lost Treasure Scripture" over "Lost Treasure")
                if (droppedItem == null || itemDisplayName.length > droppedItem.displayName.length) {
                    droppedItem = item
                }
            }
        }
        
        if (droppedItem == null) return

        // Resolve contextual item (hardmode variants)
        droppedItem = resolveContextualItemByDisplayName(droppedItem)

        // Check for shiny: text immediately after item name mentions "shiny"
        val afterItem = plainText.substring(gotIndex + 5 + droppedItem.displayName.length).trimStart()
        val isShiny = droppedItem.hasShiny && afterItem.startsWith("shiny", ignoreCase = true)

        // Only process specific target rarities (or any shiny drop)
        val rarity = droppedItem.rarity
        if (!isShiny &&
            rarity != Item.Rarity.ROYAL &&
            rarity != Item.Rarity.BLOODSHOT &&
            rarity != Item.Rarity.VOIDBOUND &&
            rarity != Item.Rarity.UNHOLY &&
            rarity != Item.Rarity.COMPANION) {
            return
        }

        val variant = DropVariant(droppedItem, isShiny)
        val alreadyDetected = detectedVariants.contains(variant)
        val preResetPity: Int

        if (!alreadyDetected) {
            val pityKey = TrackingKey.PityCounter(variant.pityKey)
            preResetPity = TypeSafeDataAccess.get(pityKey) ?: 0
            recentPityCache[variant] = preResetPity
            recentPityCacheTime[variant] = System.currentTimeMillis()

            TypeSafeDataAccess.reset(pityKey)
            Telosmancy.logger.info("Detected and reset pity for ${droppedItem.displayName}${if (isShiny) " (shiny)" else ""} via chat (was at $preResetPity)")

            detectedVariants.add(variant)
        } else {
            preResetPity = recentPityCache[variant] ?: 0
            Telosmancy.logger.debug("Item ${droppedItem.displayName}${if (isShiny) " (shiny)" else ""} already detected via entity scan")
        }

        if (PityCounterModule.useCustomMsg) {
            hideMessage()
            if (!alreadyDetected) {
                sendPityResetMessage(droppedItem, preResetPity, isShiny)
            }
        } else {
            hideMessage()

            val originalComponent = this.component
            val area = if (LocalAPI.isInDungeon()) LocalAPI.getCurrentCharacterArea() else BossData.findByItem(droppedItem)?.label ?: "Unknown"
            val shareText = "[${if (isShiny) "SHINY" else droppedItem.rarity}] Dropped ${droppedItem.displayName}${if (isShiny) " (Shiny)" else ""} at $preResetPity pity from $area!"

            val buttonMessage = " <click:suggest_command:'${shareText}'><hover:show_text:\"<#AAAAAA>Pity: $preResetPity<br> Click to share in chat!</#AAAAAA>\"><#AAAAAA><b>⧉</b></#AAAAAA></hover></click>"
            val buttonComponent = buttonMessage.toNative()

            val finalMessage = Component.empty().append(originalComponent).append(buttonComponent)

            Telosmancy.mc.execute {
                Telosmancy.mc.gui.chat.addClientSystemMessage(finalMessage)
            }
        }
    }
    
    /**
     * Resolve contextual item from texture path.
     * Handles hardmode variants (True Ophan, True Seraph, etc.)
     */
    private fun resolveContextualItem(texturePath: String): Item? {
        // Find default item by texture path
        val defaultItem = Item.all.find { it.texturePath == texturePath } ?: return null
        
        try {
            val currentArea = LocalAPI.getCurrentCharacterArea()
            
            // Build list of contextual items based on current area
            val contextItems = mutableListOf<Item>()
            when (currentArea) {
                "Dawn of Creation" -> contextItems.addAll(BossData.itemsOf("TRUE_OPHAN"))
                "Seraph's Domain" -> contextItems.addAll(BossData.itemsOf("TRUE_SERAPH"))
                "Celestial's Province" -> contextItems.addAll(BossData.itemsOf("ASMODEUS", "SERAPHIM"))
                "Rustborn Kingdom" -> contextItems.addAll(BossData.itemsOf("VALERION", "NEBULA", "OPHANIM"))
            }
            
            // Check if any contextual item matches the texture path
            for (item in contextItems) {
                if (item.texturePath == defaultItem.texturePath) {
                    return item
                }
            }
        } catch (e: Exception) {
            Telosmancy.logger.warn("Failed to resolve contextual item: ${e.message}")
        }
        
        return defaultItem
    }
    
    /**
     * Resolve contextual item from display name.
     * Handles hardmode variants (True Ophan, True Seraph, etc.) when detecting from chat messages.
     */
    private fun resolveContextualItemByDisplayName(defaultItem: Item): Item {
        try {
            val currentArea = LocalAPI.getCurrentCharacterArea()
            
            // Build list of contextual items based on current area
            val contextItems = mutableListOf<Item>()
            when (currentArea) {
                "Dawn of Creation" -> contextItems.addAll(BossData.itemsOf("TRUE_OPHAN"))
                "Seraph's Domain" -> contextItems.addAll(BossData.itemsOf("TRUE_SERAPH"))
                "Celestial's Province" -> contextItems.addAll(BossData.itemsOf("ASMODEUS", "SERAPHIM"))
                "Rustborn Kingdom" -> contextItems.addAll(BossData.itemsOf("VALERION", "NEBULA", "OPHANIM"))
            }
            
            // Check if any contextual item matches the display name
            for (item in contextItems) {
                if (item.displayName == defaultItem.displayName) {
                    return item
                }
            }
        } catch (e: Exception) {
            Telosmancy.logger.warn("Failed to resolve contextual item by display name: ${e.message}")
        }
        
        return defaultItem
    }
    
    /**
     * Send pity reset message for a specific item.
     */
    private fun sendPityResetMessage(item: Item, pityCount: Int, shiny: Boolean = false) {
        val mc = Telosmancy.mc
        if (mc.player == null) return
        
        val lootboost = TabListUtils.getLootboostPercentage() ?: 0
        
        // Configuration for rarity styles
        data class RarityStyle(val indicatorColor: Int, val prefix: String, val itemNameColor: String, val logName: String)
        
        val style = if (shiny) {
            RarityStyle(
                0x00CCCC,
                "<#FFFFFF>\uD818\uDE80 </#FFFFFF><bold><gradient:#00CCCC:#00FFFF>SHINY</gradient></bold>",
                "<#00CCCC>",
                "SHINY"
            )
        } else when (item.rarity) {
            Item.Rarity.IRRADIATED -> RarityStyle(
                0x189506,
                "<#FFFFFF>\uD814\uDF19 </#FFFFFF><bold><gradient:#189506:#15cd15>IRRADIATED</bold>",
                "<#189506>",
                "IRRADIATED"
            )
            Item.Rarity.GILDED -> RarityStyle(
                0xb93f12,
                "<#FFFFFF>\uD818\uDCF1 </#FFFFFF><bold><gradient:#b93f12:#df5320>GILDED</bold>",
                "<#b93f12>",
                "GILDED"
            )
            Item.Rarity.ROYAL -> RarityStyle(
                0x7d1775,
                "<#FFFFFF>\uD814\uDF1B </#FFFFFF><bold><gradient:#7d1775:#aa00aa>ROYAL</bold>",
                "<#7d1775>",
                "ROYAL"
            )
            Item.Rarity.BLOODSHOT -> RarityStyle(
                0x9D0000,
                "<#FFFFFF>\uD814\uDF1C </#FFFFFF><bold><gradient:#9D0000:#FF1A1A>BLOODSHOT</gradient></bold>",
                "<#9D0000>",
                "BLOODSHOT"
            )
            Item.Rarity.VOIDBOUND -> RarityStyle(
                0x8d15f0,
                "<#FFFFFF>\uD818\uDE35 </#FFFFFF><bold><gradient:#8d15f0:#be74fb>VOIDBOUND</gradient></bold>",
                "<#8d15f0>",
                "VOIDBOUND"
            )
            Item.Rarity.UNHOLY -> RarityStyle(
                0x5D6069,
                "<#FFFFFF>\uD815\uDC66 </#FFFFFF><bold><gradient:#5D6069:#DCE8D5>UNHOLY</gradient></bold>",
                "<#5D6069>",
                "UNHOLY"
            )
            Item.Rarity.COMPANION -> RarityStyle(
                0xae9000,
                "<#FFFFFF>\uD814\uDF1A </#FFFFFF><bold><gradient:#ae9000:#ffaa00>COMPANION</bold>",
                "<#ae9000>",
                "COMPANION"
            )
            Item.Rarity.SHINY -> RarityStyle(
                0x00CCCC,
                "<#FFFFFF>\uD818\uDE80 </#FFFFFF><bold><gradient:#00CCCC:#00FFFF>SHINY</gradient></bold>",
                "<#00CCCC>",
                "SHINY"
            )
        }
        
        val lootBoostStr = if (lootboost > 0) " <#FFFF00>[+$lootboost% LB]" else ""
        val m = Message.Colors.MUTED
        val area = if (LocalAPI.isInDungeon()) LocalAPI.getCurrentCharacterArea() else BossData.findByItem(item)?.label ?: "Unknown"
        
        // Build message using MiniMessage
        var message = "${style.prefix} $m- <#AAAAAA>Dropped <underlined>${style.itemNameColor}${item.displayName}</underlined> <#AAAAAA>at <#FFFF00>$pityCount</#FFFF00> <#AAAAAA>pity from ${style.itemNameColor}$area$lootBoostStr"
        if (PityCounterModule.showAnnounceButton) {
            val label = if (shiny) "SHINY" else item.rarity.toString()
            val suffix = if (shiny) " (Shiny)" else ""
            val shareText = "[$label] Dropped ${item.displayName}$suffix at ${pityCount} pity from $area!"

            message += " <click:suggest_command:'${shareText}'><hover:show_text:\"<#AAAAAA>Click to share in chat!</#AAAAAA>\"><#AAAAAA><b>⧉</b></#AAAAAA></hover></click>"
        }
        
        val chatIndicator = GuiMessageTag(
            style.indicatorColor,
            null,
            "${style.prefix} Drop".toNative(),
            "${style.logName} Drop"
        )
        
        mc.gui.chat.addPlayerMessage(message.toNative(), null, chatIndicator)
        
        val logMessage = "Sent pity reset message: Dropped ${item.displayName} at $pityCount pity${if (lootboost > 0) " [+$lootboost% Loot Boost]" else ""}"
        Telosmancy.logger.info(logMessage)
    }
}