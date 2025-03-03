package cotoami.subparts.modals

import scala.util.chaining._
import slinky.core.facade.ReactElement
import slinky.web.html._

import fui.Cmd
import cotoami.{Context, Into, Msg => AppMsg}
import cotoami.utils.Validation
import cotoami.models.{Coto, Cotonoma, Id, Ito}
import cotoami.repository.Root
import cotoami.components.{materialSymbol, optionalClasses, ScrollArea, Select}
import cotoami.subparts.{Modal, PartsCoto, PartsIto}
import cotoami.subparts.EditorCoto._
import cotoami.subparts.SectionGeomap.{Model => Geomap}

object ModalSubcoto {

  /////////////////////////////////////////////////////////////////////////////
  // Model
  /////////////////////////////////////////////////////////////////////////////

  case class Model(
      sourceCotoId: Id[Coto],
      sourceCotonomas: Seq[Cotonoma],
      descriptionInput: String = "",
      cotoForm: CotoForm.Model = CotoForm.Model(),
      error: Option[String] = None
  ) {
    def description: Option[String] =
      Option.when(!descriptionInput.isBlank())(descriptionInput.trim)

    val validateDescription: Validation.Result =
      description
        .map(Ito.validateDescription)
        .map(Validation.Result(_))
        .getOrElse(Validation.Result.notYetValidated)
  }

  object Model {
    def apply(sourceCotoId: Id[Coto], repo: Root): Model =
      Model(
        sourceCotoId = sourceCotoId,
        sourceCotonomas = repo.cotos.get(sourceCotoId)
          .map(_.postedInIds.map(repo.cotonomas.get).flatten)
          .getOrElse(Seq.empty)
      )
  }

  /////////////////////////////////////////////////////////////////////////////
  // Update
  /////////////////////////////////////////////////////////////////////////////

  sealed trait Msg extends Into[AppMsg] {
    def into = Modal.Msg.SubcotoMsg(this).pipe(AppMsg.ModalMsg)
  }

  object Msg {
    case class DescriptionInput(description: String) extends Msg
    case class CotoFormMsg(submsg: CotoForm.Msg) extends Msg
  }

  def update(msg: Msg, model: Model)(implicit
      context: Context
  ): (Model, Geomap, Cmd[AppMsg]) = {
    val default = (model, context.geomap, Cmd.none)
    msg match {
      case Msg.DescriptionInput(description) =>
        default.copy(_1 = model.copy(descriptionInput = description))

      case Msg.CotoFormMsg(submsg) => {
        val (form, geomap, subcmd) = CotoForm.update(submsg, model.cotoForm)
        default.copy(
          _1 = model.copy(cotoForm = form),
          _2 = geomap,
          _3 = subcmd.map(Msg.CotoFormMsg).map(_.into)
        )
      }
    }
  }

  /////////////////////////////////////////////////////////////////////////////
  // View
  /////////////////////////////////////////////////////////////////////////////

  def apply(model: Model)(implicit
      context: Context,
      dispatch: Into[AppMsg] => Unit
  ): ReactElement = {
    val sourceCoto = context.repo.cotos.get(model.sourceCotoId)
    Modal.view(
      dialogClasses = optionalClasses(
        Seq(
          ("subcoto", true),
          ("with-media-content", model.cotoForm.mediaBlob.isDefined)
        )
      ),
      closeButton = Some((classOf[Modal.Subcoto], dispatch)),
      error = model.error
    )(
      "New sub-coto"
    )(
      section(className := "source")(
        sourceCoto.map(articleCoto)
      ),
      sectionIto(model),
      CotoForm(
        model = model.cotoForm,
        vertical = true,
        onCtrlEnter = () => ()
      )(context, submsg => dispatch(Msg.CotoFormMsg(submsg))),
      section(className := "post")(
        div(className := "space")(),
        Select(
          className = "cotonoma-select",
          options = Seq.empty,
          placeholder = Some("Post to...")
        ),
        button(
          className := "post",
          `type` := "button"
        )("Post", span(className := "shortcut-help")("(Ctrl + Enter)"))
      )
    )
  }

  private def articleCoto(coto: Coto)(implicit
      context: Context
  ): ReactElement =
    article(className := "coto embedded")(
      header()(
        PartsCoto.addressAuthor(coto, context.repo.nodes)
      ),
      div(className := "body")(
        ScrollArea()(
          PartsCoto.divContentPreview(coto)
        )
      )
    )

  private def sectionIto(
      model: Model
  )(implicit dispatch: Into[AppMsg] => Unit): ReactElement =
    section(className := "ito")(
      div(className := "ito-icon")(
        materialSymbol("arrow_downward")
      ),
      PartsIto.inputDescription(
        model.descriptionInput,
        model.validateDescription,
        value => dispatch(Msg.DescriptionInput(value))
      )
    )
}
