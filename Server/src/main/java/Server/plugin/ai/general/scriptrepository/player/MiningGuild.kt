package plugin.ai.general.scriptrepository.player

import core.game.content.global.action.PickupHandler
import core.game.interaction.DestinationFlag
import core.game.interaction.MovementPulse
import core.game.node.Node
import core.game.node.item.Item
import core.game.world.map.zone.ZoneBorders
import core.tools.Items
import plugin.ai.general.ScriptAPI
import plugin.ai.skillingbot.SkillingBotAssembler
import core.game.node.entity.skill.Skills
import core.game.node.entity.skill.gather.SkillingResource
import core.game.node.item.GroundItemManager
import plugin.ai.general.scriptrepository.*
import java.awt.List

class OreInfo() {
    val ores = mutableListOf<Int>()
    var item = 0

    constructor(item: Int) : this() {
        this.item = item
    }
}

@PlayerCompatible
@ScriptName("Mining Guild Miner")
@ScriptDescription("Start in Falador East Bank with a pick equipped", "or in your inventory.")
@ScriptIdentifier("mining_guild")
class MiningGuild() : Script() {
    var state = State.INIT
    var ladderSwitch = false

    val bottomLadder = ZoneBorders(3016, 9736, 3024, 9742)
    val topLadder = ZoneBorders(3016, 3336, 3022, 3342)
    val mine = ZoneBorders(3027, 9733, 3054, 9743)
    val bank = ZoneBorders(3009, 3355, 3018, 3358)
    var overlay: ScriptAPI.BottingOverlay? = null
    var coalAmount = 0
    var selectedOre = "coal"

    val oreMap = mapOf(
        "coal" to OreInfo(453),
        "iron" to OreInfo(440),
        "mithril" to OreInfo(447),
    )

    init {
        for (node in SkillingResource.values()) {
            when (node.getName()) {
                "coal" -> {
                    oreMap["coal"]?.ores?.add(node.id)
                }
                "iron rocks" -> {
                    oreMap["iron"]?.ores?.add(node.id)
                }
                "mithril rocks" -> {
                    oreMap["mithril"]?.ores?.add(node.id)
                }
            }
        }
    }

    private fun handleBrokenPickaxe(): Boolean {
        // TODO handle all pickaxe types
        if (bot.equipment.contains(466, 1) || bot.inventory.contains(466, 1)) {
            println("missing pickaxe head")
            val item = GroundItemManager.getItems().find { it.droppedBy(bot) && it.id in arrayOf(480) }
            if (item != null) {
                PickupHandler.take(bot, item)
                if (bot.inventory.contains(480, 1) && bot.inventory.contains(466, 1)) {
                    bot.inventory.remove(Item(480, 1))
                    bot.inventory.remove(Item(466, 1))
                    bot.inventory.add(Item(1265, 1))
                } else if (bot.inventory.contains(480, 1) && bot.equipment.contains(466, 1)) {
                    val item = bot.equipment.getItem(Item(466, 1))
                    val slot = item.slot
                    bot.equipment.remove(item)
                    bot.inventory.remove(Item(480, 1))
                    bot.equipment.add(Item(1265, 1), slot, false, false)
                }
            }
            return true
        }
        return false
    }

    override fun tick() {
        when (state) {

            State.INIT -> {
                overlay = scriptAPI.getOverlay()
                ladderSwitch = true
                overlay!!.init()
                overlay!!.setTitle("Mining")
                overlay!!.setTaskLabel("$selectedOre Mined:")
                overlay!!.setAmount(0)

                bot.dialogueInterpreter.sendOptions(
                    "Which ore would you like to mine?",
                    *oreMap.keys.toTypedArray()
                )
                bot.dialogueInterpreter.addAction { _, button ->
                    selectedOre = oreMap.keys.toTypedArray()[button - 2]
                    state = State.TO_MINE
                }
            }

            State.MINING -> {
                // Handle pickaxe head event
                if (!handleBrokenPickaxe()) {
                    if (bot.inventory.freeSlots() == 0) {
                        state = State.TO_BANK
                    }
                    if (!mine.insideBorder(bot)) {
                        scriptAPI.walkTo(mine.randomLoc)
                    } else {
//                        val rock = scriptAPI.getNearestNode("rocks", true)
                        val rock = scriptAPI.getNearestNode(oreMap[selectedOre]?.ores as Collection<Int>, true)
                        rock?.interaction?.handle(bot, rock.interaction[0])
                    }
                    // TODO replace with generic version
//                    overlay!!.setAmount(bot.inventory.getAmount(Items.COAL_453) + coalAmount)
                }
            }

            State.TO_BANK -> {
                if (bank.insideBorder(bot)) {
                    val bank = scriptAPI.getNearestNode("bank booth", true)
                    if (bank != null) {
                        bot.pulseManager.run(object : BankingPulse(this, bank) {
                            override fun pulse(): Boolean {
                                state = State.BANKING
                                return super.pulse()
                            }
                        })
                    }

                } else {
                    if (!ladderSwitch) {
                        val ladder = scriptAPI.getNearestNode(30941, true)
                        ladder ?: scriptAPI.walkTo(bottomLadder.randomLoc).also { return }
                        ladder?.interaction?.handle(bot, ladder.interaction[0]).also { ladderSwitch = true }
                    } else {
                        if (!bank.insideBorder(bot)) scriptAPI.walkTo(bank.randomLoc).also { return }
                    }
                }
            }

            State.BANKING -> {
                val itemId = oreMap[selectedOre]?.item ?: return
                coalAmount += bot.inventory.getAmount(itemId)
                scriptAPI.bankItem(itemId)
                state = State.TO_MINE
            }

            State.TO_MINE -> {
                if (ladderSwitch) {
                    if (!topLadder.insideBorder(bot.location)) {
                        scriptAPI.walkTo(topLadder.randomLoc)
                    } else {
                        val ladder = scriptAPI.getNearestNode("Ladder", true)
                        if (ladder != null) {
                            ladder.interaction.handle(bot, ladder.interaction[0])
                            ladderSwitch = false
                        } else {
                            scriptAPI.walkTo(topLadder.randomLoc)
                        }
                    }
                } else {
                    if (!mine.insideBorder(bot)) {
                        scriptAPI.walkTo(mine.randomLoc)
                    } else {
                        state = State.MINING
                    }
                }
            }

            State.TO_GE -> {
                scriptAPI.teleportToGE()
                state = State.SELLING
            }

            State.SELLING -> {
                val itemId = oreMap[selectedOre]?.item ?: return
                scriptAPI.sellOnGE(itemId)
                state = State.GO_BACK
            }

            State.GO_BACK -> {
                scriptAPI.teleport(bank.randomLoc)
                state = State.TO_MINE
            }

        }
    }

    open class BankingPulse(val script: Script, val bank: Node) :
        MovementPulse(script.bot, bank, DestinationFlag.OBJECT) {
        override fun pulse(): Boolean {
            script.bot.faceLocation(bank.location)
            return true
        }
    }

    override fun newInstance(): Script {
        return this
    }

    enum class State {
        MINING,
        TO_MINE,
        TO_BANK,
        BANKING,
        TO_GE,
        SELLING,
        GO_BACK,
        INIT
    }
}