package marubinotto

/** A type for a React prop to invoke an effect with a given parameter. It is a
  * workaround to implement time-based actions in a declarative way.
  *
  * cf. https://github.com/facebook/react/issues/6646
  */
case class Action[T](triggered: Int = 0, parameter: Option[T] = None) {
  def trigger: Action[T] =
    copy(triggered = triggered + 1, parameter = None)

  def trigger(parameter: T): Action[T] =
    copy(triggered = triggered + 1, parameter = Some(parameter))
}

object Action {
  def default[T]: Action[T] = Action[T]()
}
