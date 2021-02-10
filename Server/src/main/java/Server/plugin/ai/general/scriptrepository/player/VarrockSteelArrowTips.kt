package plugin.ai.general.scriptrepository.player

import core.game.interaction.DestinationFlag
import core.game.interaction.MovementPulse
import core.game.node.item.Item
import core.game.world.map.Location
import core.tools.Items
import core.tools.RandomFunction
import plugin.ai.skillingbot.SkillingBotAssembler
import core.game.node.entity.skill.Skills
import core.game.node.entity.skill.smithing.Bars
import core.game.node.entity.skill.smithing.SmithingPulse
import core.tools.stringtools.colorize
import plugin.ai.general.GeneralBotCreator
import plugin.ai.general.scriptrepository.*

@PlayerCompatible
@ScriptName("Varrock Steel Arrow Tip Smithing")
@ScriptDescription("Smiths steel arrow tips from steel bars near Varrock west bank.")
@ScriptIdentifier("varrock_steel_tips")
class VarrockSteelArrowTips : Script() {
    var state = State.SMITHING
    override fun tick() {
        when(state) {
            State.SMITHING -> {
                if (bot.inventory.getAmount(Items.STEEL_BAR_2353) == 0) {
                    state = State.BANKING
                }
                val anvil = scriptAPI.getNearestNode("anvil", true)
                if (anvil != null) {
                    bot.pulseManager.run(object : MovementPulse(bot, anvil, DestinationFlag.OBJECT) {
                        override fun pulse(): Boolean {
                            bot.faceLocation(anvil.location)
                            bot.pulseManager.run(SmithingPulse(bot, Item(2353), Bars.STEEL_ARROW_TIPS, 27))
                            state = State.BANKING
                            return true
                        }
                    })
                }
            }

            State.BANKING -> {
                val bank = scriptAPI.getNearestNode("Bank booth")
                if(bank != null)
                    bot.pulseManager.run(object: MovementPulse(bot,bank, DestinationFlag.OBJECT){
                        override fun pulse(): Boolean {
                            bot.faceLocation(bank.location)

                            val arrowTips = Items.STEEL_ARROWTIPS_41
                            val arrowTipsCount = bot.inventory.getAmount(arrowTips)
                            bot.inventory.remove(Item(arrowTips, arrowTipsCount))
                            bot.bank.add(Item(arrowTips, arrowTipsCount))

                            val bar = Items.STEEL_BAR_2353
                            val barCount = bot.bank.getAmount(bar)
                            // If the player has no bars left, stop the script
                            if (barCount == 0) {
                                val pulse: GeneralBotCreator.BotScriptPulse? = bot.getAttribute("botting:script",null)
                                pulse?.stop()
                                bot.interfaceManager.closeOverlay()
                                bot.sendMessage(colorize("%ROut of Steel Bars. Stopping script..."))
                                return true
                            }
                            val capacity = bot.inventory.capacity() - bot.inventory.itemCount()
                            val withdrawAmount = if (barCount > capacity) capacity else barCount
                            bot.bank.remove(Item(bar, withdrawAmount))
                            bot.inventory.add(Item(bar, withdrawAmount))

                            state = State.SMITHING
                            return true
                        }
                    })
            }
        }
    }

    override fun newInstance(): Script {
        val script = VarrockSteelArrowTips()
        script.bot = SkillingBotAssembler().produce(SkillingBotAssembler.Wealth.RICH, Location.create(3189, 3436, 0))
        return script
    }

    init {
        skills[Skills.SMITHING] = RandomFunction.random(33,99)
        inventory.add(Item(Items.HAMMER_2347))
        inventory.add(Item(Items.STEEL_BAR_2353,27))
    }

    enum class State {
        SMITHING,
        BANKING
    }
}