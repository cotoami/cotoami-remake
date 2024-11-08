package cotoami.components

import scala.scalajs.js
import scala.scalajs.js.annotation.JSImport

import slinky.core._
import slinky.core.annotations.react

@react object Select extends ExternalComponent {
  case class Props(
      className: String,
      placeholder: scala.Option[String] = None,
      options: Seq[Option]
  )

  trait Option extends js.Object {
    val value: String
    val label: String
  }

  override val component = ReactSelect
}

@js.native
@JSImport("react-select", JSImport.Default)
object ReactSelect extends js.Object
