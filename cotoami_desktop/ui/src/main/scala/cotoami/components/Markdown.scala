package cotoami.components

import scala.scalajs.js
import scala.scalajs.js.annotation.JSImport

import slinky.core._
import slinky.core.annotations.react
import slinky.core.facade.ReactElement

@js.native
@JSImport("react-markdown", JSImport.Default)
object ReactMarkdown extends js.Object

@react object Markdown extends ExternalComponent {
  case class Props(
      children: ReactElement*
  )

  override val component = ReactMarkdown
}
