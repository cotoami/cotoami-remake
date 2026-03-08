package marubinotto.components

import scala.scalajs.js
import scala.scalajs.js.|
import scala.scalajs.js.annotation.JSImport

import slinky.core._
import slinky.core.facade.ReactElement

object Markdown extends ExternalComponent {
  case class Props(
      remarkPlugins: Seq[js.Object | js.Tuple2[js.Object, js.Object]],
      rehypePlugins: Seq[js.Object | js.Tuple2[js.Object, js.Object]]
  )

  def apply(
      remarkPlugins: Seq[js.Object | js.Tuple2[js.Object, js.Object]],
      rehypePlugins: Seq[js.Object | js.Tuple2[js.Object, js.Object]]
  )(children: ReactElement*) =
    super.apply(Props(remarkPlugins, rehypePlugins))(children: _*)

  override val component = ReactMarkdown
}

@js.native
@JSImport("react-markdown", JSImport.Default)
object ReactMarkdown extends js.Object
