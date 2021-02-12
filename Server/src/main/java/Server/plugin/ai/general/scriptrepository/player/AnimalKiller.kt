package plugin.ai.general.scriptrepository.player

import core.game.node.item.Item
import core.game.system.SystemLogger
import core.game.world.map.Location
import core.tools.Items
import plugin.ai.general.ScriptAPI
import plugin.ai.general.scriptrepository.*

@PlayerCompatible
@ScriptName("Animal Killer")
@ScriptDescription("Kills animals and loots drops. Start in any animal area.")
@ScriptIdentifier("animal_killer")
class AnimalKiller : Script() {
    var state = State.INIT
    var animalCounter = 0
    var overlay: ScriptAPI.BottingOverlay? = null
    var startLocation = Location(0, 0, 0)
    var timer = 3
    var lootItems = false
    var selectedAnimal = "Chicken"

    val lootMap = mapOf(
        "Chicken" to Item(Items.FEATHER_314),
        "Cow" to Item(Items.COWHIDE_1739),
    )

    override fun tick() {
        when (state) {

            State.INIT -> {
                overlay = scriptAPI.getOverlay()
                overlay!!.init()
                overlay!!.setTitle(selectedAnimal)
                overlay!!.setTaskLabel("$selectedAnimal KO'd:")
                overlay!!.setAmount(0)
                state = State.CONFIG

                bot.dialogueInterpreter.sendOptions(
                    "Which animal would you like to kill?",
                    *lootMap.keys.toTypedArray()
                )
                bot.dialogueInterpreter.addAction { _, button ->
                    selectedAnimal = lootMap.keys.toTypedArray()[button - 2]
                    state = State.LOOT_PROMPT
                }
                startLocation = bot.location
            }

            State.LOOT_PROMPT -> {
                val lootName = lootMap[selectedAnimal]?.name
                bot.dialogueInterpreter.sendOptions("Loot $lootName?", "Yes", "No")
                bot.dialogueInterpreter.addAction { _, button ->
                    lootItems = button == 2
                    state = State.KILLING
                }
            }

            State.KILLING -> {
                val animal = scriptAPI.getNearestNode(selectedAnimal)
                if (animal == null) {
                    scriptAPI.randomWalkTo(startLocation, 3)
                } else {
                    scriptAPI.attackNpcInRadius(bot, selectedAnimal, 10)
                    if (lootItems) {
                        state = State.IDLE
                        timer = 4
                        SystemLogger.log("Going to idle state")
                    }
                    animalCounter++
                    overlay!!.setAmount(animalCounter)
                }
            }

            State.IDLE -> {
                if (timer-- <= 0) {
                    state = State.LOOTING
                }
            }

            State.LOOTING -> {
                lootMap[selectedAnimal]?.id?.let { scriptAPI.takeNearestGroundItem(it) }
                state = State.KILLING
            }

            else -> {
                println("Invalid state")
            }
        }
    }

    override fun newInstance(): Script {
        return this
    }

    enum class State {
        INIT,
        LOOT_PROMPT,
        IDLE,
        KILLING,
        LOOTING,
        RETURN,
        CONFIG
    }
}