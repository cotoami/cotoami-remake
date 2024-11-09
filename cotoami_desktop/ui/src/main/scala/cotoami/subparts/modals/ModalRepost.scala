package cotoami.subparts.modals

import scala.util.chaining._
import slinky.core.facade.ReactElement
import slinky.web.html._

import fui.Cmd
import cotoami.{Context, Into, Msg => AppMsg}
import cotoami.models.{Coto, Cotonoma, Id}
import cotoami.subparts.{Modal, ViewCoto}
import cotoami.components.{materialSymbol, ScrollArea, Select}

object ModalRepost {

  case class Model(
      cotoId: Id[Coto],
      cotonomaName: String = "",
      options: Seq[Select.SelectOption] = Seq.empty,
      optionsLoading: Boolean = false
  )

  class Destination(
      name: String,
      cotonoma: Option[Cotonoma] = None
  ) extends Select.SelectOption {
    val value: String = cotonoma.map(_.id.uuid).getOrElse("")
    val label: String = name
  }

  sealed trait Msg extends Into[AppMsg] {
    def into = Modal.Msg.RepostMsg(this).pipe(AppMsg.ModalMsg)
  }

  object Msg {
    case class CotonomaNameInput(name: String) extends Msg
  }

  def update(msg: Msg, model: Model): (Model, Cmd[AppMsg]) =
    msg match {
      case Msg.CotonomaNameInput(name) =>
        (model.copy(cotonomaName = name), Cmd.none)
    }

  def apply(model: Model)(implicit
      context: Context,
      dispatch: Into[AppMsg] => Unit
  ): ReactElement =
    Modal.view(
      dialogClasses = "repost",
      closeButton = Some((classOf[Modal.Repost], dispatch))
    )(
      "Repost"
    )(
      section(className := "repost-form")(
        Select(
          className = "cotonoma-select",
          options = model.options,
          placeholder = Some("Cotonoma name"),
          inputValue = model.cotonomaName,
          onInputChange = Some(input => dispatch(Msg.CotonomaNameInput(input))),
          noOptionsMessage = Some(_ => NoOptionsMessage),
          formatOptionLabel = Some(divSelectOption),
          isLoading = model.optionsLoading
        ),
        button(
          className := "repost",
          `type` := "button",
          disabled := true
        )(materialSymbol("repeat"))
      ),
      context.domain.cotos.get(model.cotoId).map(articleCoto)
    )

  private val NoOptionsMessage = div()("Type cotonoma name...")

  private def divSelectOption(option: Select.SelectOption): ReactElement =
    div()(
      option.label,
      " hogehoge"
    )

  private def articleCoto(coto: Coto)(implicit context: Context): ReactElement =
    article(className := "coto")(
      header()(
        ViewCoto.addressAuthor(coto, context.domain.nodes)
      ),
      div(className := "body")(
        ScrollArea()(
          ViewCoto.divContentPreview(coto)
        )
      )
    )
}
