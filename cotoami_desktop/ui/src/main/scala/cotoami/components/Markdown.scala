package cotoami.components

import scala.scalajs.js
import scala.scalajs.js.|
import scala.scalajs.js.annotation.JSImport

import slinky.core._
import slinky.core.annotations.react
import slinky.core.facade.ReactElement

@react object Markdown extends ExternalComponent {
  case class Props(
      remarkPlugins: Seq[js.Object | js.Tuple2[js.Object, js.Object]],
      rehypePlugins: Seq[js.Object | js.Tuple2[js.Object, js.Object]],
      children: ReactElement*
  )

  override val component = ReactMarkdown
}

@js.native
@JSImport("react-markdown", JSImport.Default)
object ReactMarkdown extends js.Object
