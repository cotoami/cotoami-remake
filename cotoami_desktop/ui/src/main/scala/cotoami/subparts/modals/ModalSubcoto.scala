package cotoami.subparts.modals

import scala.util.chaining._
import slinky.core.facade.ReactElement
import slinky.web.html._

import fui.Cmd
import fui.Cmd.One.pure
import cotoami.{Context, Into, Msg => AppMsg}
import cotoami.utils.Validation
import cotoami.utils.facade.Nullable
import cotoami.models.{Coto, Cotonoma, Id, Ito}
import cotoami.repository.Root
import cotoami.backend.{CotoBackend, ErrorJson, ItoBackend}
import cotoami.components.{materialSymbol, optionalClasses, ScrollArea, Select}
import cotoami.subparts.{imgNode, Modal, PartsCoto, PartsIto}
import cotoami.subparts.EditorCoto._
import cotoami.subparts.SectionGeomap.{Model => Geomap}

object ModalSubcoto {

  /////////////////////////////////////////////////////////////////////////////
  // Model
  /////////////////////////////////////////////////////////////////////////////

  case class Model(
      sourceCotoId: Id[Coto],
      targetCotonomas: Seq[TargetCotonoma],
      postTo: Option[TargetCotonoma],
      descriptionInput: String = "",
      cotoForm: CotoForm.Model = CotoForm.Model(),
      posting: Boolean = false,
      error: Option[String] = None
  ) {
    def description: Option[String] =
      Option.when(!descriptionInput.isBlank())(descriptionInput.trim)

    val validateDescription: Validation.Result =
      description
        .map(Ito.validateDescription)
        .map(Validation.Result(_))
        .getOrElse(Validation.Result.notYetValidated)

    def readyToPost: Boolean =
      !posting && postTo.isDefined && cotoForm.hasValidContents && !validateDescription.failed

    def postAndConnect(geomap: Geomap): (Model, Cmd.One[AppMsg]) =
      (
        copy(posting = true),
        post(geomap).flatMap(_ match {
          case Right(coto) => connect(coto)
          case Left(e)     => pure(Left(e))
        }).map(Msg.Posted(_).into)
      )

    private def post(geomap: Geomap): Cmd.One[Either[ErrorJson, Coto]] =
      postTo.map(target =>
        CotoBackend.post(
          cotoForm.content,
          cotoForm.summary,
          cotoForm.mediaBase64,
          geomap.focusedLocation,
          cotoForm.dateTimeRange,
          target.cotonoma.id
        )
      ).getOrElse(Cmd.none)

    private def connect(
        targetCoto: Coto
    ): Cmd.One[Either[ErrorJson, (Ito, Coto)]] =
      ItoBackend.connect(sourceCotoId, targetCoto.id, description, None, None)
        .map(_.map(_ -> targetCoto))
  }

  class TargetCotonoma(
      val cotonoma: Cotonoma,
      val disabled: Boolean = false
  ) extends Select.SelectOption {
    val value: String = cotonoma.id.uuid
    val label: String = cotonoma.name
    val isDisabled: Boolean = disabled
  }

  object Model {
    def apply(sourceCotoId: Id[Coto], repo: Root): Model = {
      val postedInIds =
        repo.cotos.get(sourceCotoId).map(_.postedInIds).getOrElse(Seq.empty)

      val targetCotonomaIds =
        repo.cotonomas.getByCotoId(sourceCotoId) match {
          case Some(sourceCotonoma) =>
            ((sourceCotonoma.id +: postedInIds) ++ repo.currentCotonomaId).distinct

          case None =>
            repo.currentCotonomaId match {
              case Some(current) =>
                if (postedInIds.contains(current))
                  current +: postedInIds.filterNot(_ == current)
                else
                  postedInIds :+ current
              case None => postedInIds
            }
        }

      val targetCotonomas =
        targetCotonomaIds.map(repo.cotonomas.get).flatten.map(cotonoma =>
          new TargetCotonoma(cotonoma, !repo.nodes.canPostTo(cotonoma.nodeId))
        )

      Model(
        sourceCotoId = sourceCotoId,
        targetCotonomas = targetCotonomas,
        postTo = targetCotonomas.headOption
      )
    }
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
    case class TargetCotonomaSelected(dest: Option[TargetCotonoma]) extends Msg
    object Post extends Msg
    case class Posted(result: Either[ErrorJson, (Ito, Coto)]) extends Msg
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

      case Msg.TargetCotonomaSelected(target) =>
        default.copy(_1 = model.copy(postTo = target))

      case Msg.Post =>
        model.postAndConnect(context.geomap).pipe { case (model, cmd) =>
          default.copy(_1 = model, _3 = cmd)
        }

      case Msg.Posted(Right(_)) =>
        default.copy(
          _1 = model.copy(posting = false),
          _3 = Modal.close(classOf[Modal.Subcoto])
        )

      case Msg.Posted(Left(e)) =>
        default.copy(
          _1 = model.copy(posting = false, error = Some(e.default_message))
        )
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
        onCtrlEnter = () => dispatch(Msg.Post)
      )(context, submsg => dispatch(Msg.CotoFormMsg(submsg))),
      sectionPost(model)
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

  private def sectionPost(
      model: Model
  )(implicit context: Context, dispatch: Into[AppMsg] => Unit): ReactElement =
    section(className := "post")(
      div(className := "post-to")(
        div(className := "label")("Post to:"),
        Select(
          className = "cotonoma-select",
          placeholder = Some("Post to..."),
          menuPlacement = "top",
          options = model.targetCotonomas,
          formatOptionLabel = Some(divSelectOption(_, context.repo)),
          value = model.postTo.getOrElse(null),
          onChange = Some(option => {
            dispatch(
              Msg.TargetCotonomaSelected(
                Nullable.toOption(option).map(_.asInstanceOf[TargetCotonoma])
              )
            )
          })
        ),
        div(className := "space")()
      ),
      CotoForm.buttonPreview(model = model.cotoForm)(submsg =>
        dispatch(Msg.CotoFormMsg(submsg))
      ),
      button(
        className := "post",
        `type` := "button",
        disabled := !model.readyToPost,
        aria - "busy" := model.posting.toString(),
        onClick := (_ => dispatch(Msg.Post))
      )("Post", span(className := "shortcut-help")("(Ctrl + Enter)"))
    )

  private def divSelectOption(
      option: Select.SelectOption,
      repo: Root
  ): ReactElement = {
    val target = option.asInstanceOf[TargetCotonoma]
    div(className := "target-cotonoma")(
      repo.nodes.get(target.cotonoma.nodeId).map(imgNode(_)),
      span(className := "cotonoma-name")(target.cotonoma.name),
      Option.when(Some(target.cotonoma.id) == repo.currentCotonomaId) {
        span(className := "current-mark")("(current)")
      }
    )
  }
}
