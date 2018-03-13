package com.codingame.game

import com.codingame.game.Constants.CREEP_DAMAGE
import com.codingame.game.Constants.GIANT_BUST_RATE
import com.codingame.game.Constants.QUEEN_HP
import com.codingame.game.Constants.QUEEN_MASS
import com.codingame.game.Constants.QUEEN_RADIUS
import com.codingame.gameengine.core.GameManager
import com.codingame.gameengine.module.entities.Curve
import com.codingame.gameengine.module.entities.Entity
import com.codingame.gameengine.module.entities.GraphicEntityModule
import tooltipModule.TooltipModule

lateinit var theEntityManager: GraphicEntityModule
lateinit var theTooltipModule: TooltipModule
lateinit var theGameManager: GameManager<Player>

val viewportX = 0..1920
val viewportY = 110..1080

var <T : Entity<*>?> Entity<T>.location: Vector2
  get() = Vector2(x - viewportX.first, y - viewportY.first)

  set(value) {
    x = (value.x + viewportX.first).toInt()
    y = (value.y + viewportY.first).toInt()
  }

abstract class MyEntity {
  open var location = Vector2.Zero
  open var radius = 0

  abstract val mass: Int   // 0 := immovable
}

abstract class MyOwnedEntity(val owner: Player) : MyEntity() {
  abstract fun damage(damageAmount: Int)
}

class Queen(owner: Player) : MyOwnedEntity(owner) {
  override val mass = QUEEN_MASS
  override var radius = QUEEN_RADIUS

  private val queenOutline = theEntityManager.createCircle()
    .setRadius(QUEEN_RADIUS)
    .setLineColor(owner.colorToken)
    .setLineWidth(2)!!

  private val queenSprite = theEntityManager.createSprite()
    .setImage("queen.png")
    .setZIndex(40)
    .setAnchor(0.5)!!

  private val queenFillSprite = theEntityManager.createSprite()
    .setImage("queen-fill.png")
    .setTint(owner.colorToken)
    .setAnchor(0.5)
    .setZIndex(30)!!

  fun setHealth(health: Int) {
    when {
      health <= 0 -> queenFillSprite.alpha = 0.0
      else -> queenFillSprite.alpha = 0.8 * health / QUEEN_HP + 0.2
    }
    theTooltipModule.updateExtraTooltipText(queenSprite, "Health: $health")
  }

  init {
    theTooltipModule.registerEntity(queenSprite, mapOf("id" to queenSprite.id, "type" to "Queen"))
  }

  fun moveTowards(target: Vector2) {
    location = location.towards(target, Constants.UNIT_SPEED.toDouble())
  }

  override var location: Vector2
    get() = super.location
    set(value) {
      super.location = value
      queenSprite.location = location
      queenFillSprite.location = location
      queenOutline.location = location
    }

  override fun damage(damageAmount: Int) {
    owner.health -= damageAmount
  }
}

abstract class Creep(
  owner: Player,
  val creepType: CreepType
) : MyOwnedEntity(owner) {

  protected val speed: Int = creepType.speed
  val attackRange: Int = creepType.range
  override val mass: Int = creepType.mass

  var health: Int = creepType.hp
    set(value) {
      field = value

      if (field <= 0) {
        field = 0
        sprite.alpha = 0.0
        fillSprite.alpha = 0.0
      } else {
        fillSprite.alpha = health.toDouble() / maxHealth * 0.8 + 0.2
      }
    }

  private val maxHealth = health

  protected val sprite = theEntityManager.createSprite()
    .setImage(creepType.assetName)
    .setAnchor(0.5)
    .setZIndex(40)!!

  protected val fillSprite = theEntityManager.createSprite()
    .setImage(creepType.fillAssetName)
    .setTint(owner.colorToken)
    .setAnchor(0.5)
    .setZIndex(30)!!

  override var location: Vector2 = Vector2.Zero
    set(value) {
      field = value
      if (value == Vector2.Zero) return

      sprite.location = value
      fillSprite.location = value
    }

  open fun finalizeFrame() { }

  override var radius = creepType.radius

  override fun damage(damageAmount: Int) {
    if (damageAmount <= 0) return   // no accidental healing!

    health -= damageAmount
    if (health <= 0) {
      owner.activeCreeps.remove(this)
    }
    theTooltipModule.updateExtraTooltipText(sprite, "Health: $health")
  }

  abstract fun dealDamage()
  abstract fun move()

  init {
    theTooltipModule.registerEntity(sprite, mapOf("id" to sprite.id, "type" to creepType.toString()))
  }
}

class TowerBustingCreep(
  owner: Player,
  creepType: CreepType,
  private val obstacles: List<Obstacle>
) : Creep(owner, creepType) {
  override fun move() {
    obstacles
      .filter { it.structure != null && it.structure is Tower && (it.structure as Tower).owner == owner.enemyPlayer }
      .minBy { it.location.distanceTo(location) }
      ?.let {
        location = location.towards(it.location, speed.toDouble())
      }
  }

  override fun dealDamage() {
    obstacles
      .firstOrNull {
        val struc = it.structure
        struc is Tower
          && struc.owner == owner.enemyPlayer
          && it.location.distanceTo(location) - radius - it.radius < 10
      }?.let { (it.structure as Tower).health -= GIANT_BUST_RATE }

    val enemyQueen = owner.enemyPlayer.queenUnit
    if (location.distanceTo(enemyQueen.location) < radius + enemyQueen.radius + attackRange + 10) {
      owner.enemyPlayer.health -= 1
    }
  }
}

class QueenChasingCreep(owner: Player, creepType: CreepType)
  : Creep(owner, creepType) {

  private var lastLocation: Vector2? = null
  override fun finalizeFrame() {
    val last = lastLocation

    if (last != null) {
      val movementVector = when {
        last.distanceTo(location) > 30 -> location - last
        else -> owner.enemyPlayer.queenUnit.location - location
      }
      sprite.rotation = Math.atan2(movementVector.y, movementVector.x)
      fillSprite.rotation = Math.atan2(movementVector.y, movementVector.x)
    }

    lastLocation = location
  }

  override fun move() {
    val enemyQueen = owner.enemyPlayer.queenUnit
    // move toward enemy queen, if not yet in range
    if (location.distanceTo(enemyQueen.location) - radius - enemyQueen.radius > attackRange)
      location = location.towards((enemyQueen.location + (location - enemyQueen.location).resizedTo(3.0)), speed.toDouble())
  }

  override fun dealDamage() {
    val enemyQueen = owner.enemyPlayer.queenUnit
    if (location.distanceTo(enemyQueen.location) < radius + enemyQueen.radius + attackRange + 10) {
      owner.enemyPlayer.health -= 1
    }
  }
}

//targets the closest enemy creep
class AutoAttackCreep(owner: Player, creepType: CreepType)
  : Creep(owner, creepType){

  private var lastLocation: Vector2? = null

  var attackTarget: Creep? = null

  private val projectile = theEntityManager.createCircle()!!
      .setZIndex(30)
      .setRadius(4)
      .setFillColor(owner.colorToken)
      .setLineColor(0xffffff)
      .setLineWidth(2)
      .setVisible(false)

  override fun finalizeFrame() {
    val target = findTarget() ?: owner.enemyPlayer.queenUnit

    val last = lastLocation

    if (last != null) {
      val movementVector = when {
        last.distanceTo(location) > 30 -> location - last
        else -> target.location - location
      }
      sprite.rotation = Math.atan2(movementVector.y, movementVector.x)
      fillSprite.rotation = Math.atan2(movementVector.y, movementVector.x)
    }

    lastLocation = location

    val localAttackTarget = attackTarget
    if (localAttackTarget != null) {
      projectile.isVisible = true
      projectile.setX(location.x.toInt() + viewportX.first, Curve.NONE)
      projectile.setY(location.y.toInt() + viewportY.first, Curve.NONE)
      theEntityManager.commitEntityState(0.0, projectile)
      projectile.setX(localAttackTarget.location.x.toInt() + viewportX.first, Curve.EASE_IN_AND_OUT)
      projectile.setY(localAttackTarget.location.y.toInt() + viewportY.first, Curve.EASE_IN_AND_OUT)
      theEntityManager.commitEntityState(0.99, projectile)
      projectile.isVisible = false
      theEntityManager.commitEntityState(1.0, projectile)
    }
  }

  override fun move() {
    val target = findTarget() ?: return
    // move toward target, if not yet in range
    if (location.distanceTo(target.location) - radius - target.radius > attackRange)
      location = location.towards((target.location + (location - target.location).resizedTo(3.0)), speed.toDouble())
  }

  override fun dealDamage() {
    attackTarget = null
    val target = findTarget() ?: return
    if (location.distanceTo(target.location) < radius + target.radius + attackRange + 10) {
      target.damage(CREEP_DAMAGE)
      attackTarget = target
    }
  }

  private fun findTarget(): Creep? {
    return owner.enemyPlayer.activeCreeps
      .minBy { it.location.distanceTo(location) }
  }
}

class PlayerHUD(private val player: Player, isSecondPlayer: Boolean) {
  private val left = if (isSecondPlayer) 1920/2 else 0
  private val right = if (isSecondPlayer) 1920 else 1920/2
  private val top = 0 //viewportY.last
  private val bottom = viewportY.first //1080

  private val healthBarWidth = 400
  private val healthBarPadding = 15

  private val background = theEntityManager.createRectangle()!!
    .setX(left).setY(top)
    .setWidth(right-left).setHeight(bottom-top)
    .setFillColor(player.colorToken)
    .setLineAlpha(0.0)
    .setZIndex(4000)

  private val avatar = theEntityManager.createSprite()
    .setImage(player.avatarToken)
    .setX(left + 10).setY(top + 10)
    .setBaseWidth(bottom - top - 10 - 10)
    .setBaseHeight(bottom - top - 10 - 10)
    .setZIndex(4003)!!

  private val heartSprite = theEntityManager.createSprite()
    .setX(left + 155).setY((top + bottom)/2)
    .setScale(2.0)
    .setImage("heart.png")
    .setAnchor(0.5)
    .setZIndex(4002)!!

  private val healthBarBackground = theEntityManager.createRectangle()!!
    .setX(left + 200 - healthBarPadding).setY(top + 25 - healthBarPadding)
    .setWidth(healthBarWidth + 2*healthBarPadding).setHeight(bottom-top-25-25+2*healthBarPadding)
    .setLineAlpha(0.0)
    .setFillColor(0).setFillAlpha(0.4)
    .setZIndex(4001)

  private val healthBarFill = theEntityManager.createRectangle()!!
    .setLineAlpha(0.0)
    .setX(left + 200).setY(top + 25)
    .setWidth(healthBarWidth).setHeight(bottom-top-25-25)
    .setFillColor(0x55ff55)
    .setLineAlpha(0.0)
    .setZIndex(4002)

  private val playerName = theEntityManager.createText(player.nicknameToken)!!
    .setX(left + 200 + 5).setY(top + 25 - 2)
    .setFillColor(0)
    .setScale(2.0)
    .setZIndex(4003)

  private val moneySprite = theEntityManager.createSprite()
    .setX(healthBarBackground.x + healthBarBackground.width + 50).setY((top + bottom)/2)
    .setImage("money.png")
    .setScale(2.0)
    .setAnchor(0.5)
    .setZIndex(4002)!!

  private val moneyText = theEntityManager.createText("0")
    .setX(moneySprite.x + 50).setY(top + 20)
    .setScale(2.0)
    .setZIndex(4002)!!

  fun update() {
    healthBarFill.width = healthBarWidth * player.health / QUEEN_HP
    moneyText.text = when (player.resourcesPerTurn) {
      0 -> "${player.resources}"
      else -> "${player.resources} (+${player.resourcesPerTurn})"
    }
  }
}