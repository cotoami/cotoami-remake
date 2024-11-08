package cotoami.subparts.modals

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
      options: Seq[Select.Option] = Seq(
        new Destination("Rust"),
        new Destination("Scala")
      )
  )

  class Destination(
      name: String,
      cotonoma: Option[Cotonoma] = None
  ) extends Select.Option {
    val value: String = cotonoma.map(_.id.uuid).getOrElse("")
    val label: String = name
  }

  sealed trait Msg

  def update(msg: Msg, model: Model): (Model, Cmd[AppMsg]) =
    (model, Cmd.none)

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
          placeholder = Some("Type cotonoma name..."),
          options = model.options
        ),
        button(
          className := "repost",
          `type` := "button",
          disabled := true
        )(materialSymbol("repeat"))
      ),
      context.domain.cotos.get(model.cotoId).map(articleCoto)
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
