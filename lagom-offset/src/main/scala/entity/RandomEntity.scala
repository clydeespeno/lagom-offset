package entity

import com.lightbend.lagom.scaladsl.persistence.PersistentEntity

import java.util.UUID

class RandomEntity extends PersistentEntity {
  override type Command = RandomCmd
  override type Event = RandomEvent
  override type State = RandomState

  override def initialState: State = RandomState(UUID.fromString(entityId))

  override lazy val behavior: Behavior =
    cmdHandler
      .orElse(eventHandler)

  private val cmdHandler = Actions()
    .onCommand[Randomize.type, UUID] {
      case (_, ctx, state) =>
        ctx.thenPersist(RandomValueAdded(RandomValue()))(_ => ctx.reply(state.uuid))
    }
    .onReadOnlyCommand[ReadLatest.type, RandomValue] {
      case (_, ctx, state) =>
        state.latest.fold {
          ctx.commandFailed(NoRandomValueCreated(state.uuid))
        } { latest =>
          ctx.reply(latest)
        }
    }

  private val eventHandler = Actions()
    .onEvent {
      case (evt: RandomValueAdded, state) => state.copy(latest = Some(evt.value))
    }
}
