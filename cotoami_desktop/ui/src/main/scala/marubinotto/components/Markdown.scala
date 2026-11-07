package marubinotto.components

import scala.scalajs.js
import scala.scalajs.js.annotation.JSImport

import marubinotto.react._
import marubinotto.react.ReactNode

object Markdown extends ExternalComponent {
  case class Props(
      remarkPlugins: Seq[js.Object | js.Tuple2[js.Object, js.Object]],
      rehypePlugins: Seq[js.Object | js.Tuple2[js.Object, js.Object]],
      components: js.UndefOr[js.Object] = js.undefined
  ) derives PropsEncoder

  def apply(
      remarkPlugins: Seq[js.Object | js.Tuple2[js.Object, js.Object]],
      rehypePlugins: Seq[js.Object | js.Tuple2[js.Object, js.Object]],
      components: js.UndefOr[js.Object] = js.undefined
  )(children: ReactNode*) =
    super.apply(Props(remarkPlugins, rehypePlugins, components))(children*)

  override val component = ReactMarkdown
}

@js.native
@JSImport("react-markdown", JSImport.Default)
object ReactMarkdown extends js.Object
