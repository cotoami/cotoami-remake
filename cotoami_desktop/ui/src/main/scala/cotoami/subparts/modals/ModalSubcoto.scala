package cotoami.subparts.modals

import scala.util.chaining._
import slinky.core.facade.ReactElement
import slinky.web.html._

import marubinotto.optionalClasses
import marubinotto.fui.Cmd
import marubinotto.facade.Nullable
import marubinotto.components.{materialSymbol, ScrollArea, Select}

import cotoami.{Context, Into, Msg => AppMsg}
import cotoami.models.{Coto, Cotonoma, Id, Ito}
import cotoami.repository.Root
import cotoami.backend.{CotoBackend, ErrorJson}
import cotoami.subparts.{Modal, PartsCoto, PartsNode}
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
      order: Option[Int] = None,
      cotoForm: CotoForm.Model = CotoForm.Model(),
      posting: Boolean = false,
      error: Option[String] = None
  ) {
    def readyToPost: Boolean =
      !posting && postTo.isDefined && cotoForm.hasValidContents

    def post: (Model, Cmd.One[AppMsg]) =
      (
        copy(posting = true),
        postTo
          .map(target =>
            CotoBackend.postSubcoto(
              sourceCotoId,
              cotoForm.toBackendInput,
              target.cotonoma.id
            )
          )
          .getOrElse(Cmd.none)
          .map(Msg.Posted(_).into)
      )
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
    def apply(
        sourceCotoId: Id[Coto],
        order: Option[Int],
        defaultCotonomaId: Option[Id[Cotonoma]]
    )(implicit
        context: Context
    ): Model = {
      val repo = context.repo
      val postedInIds =
        repo.cotos.get(sourceCotoId).map(_.postedInIds).getOrElse(Seq.empty)

      // Target cotonoma choices:
      //   1. the current cotonoma
      //   2. the cotonomas of the source coto (sourcCoto.postedInIds)
      //   3. the source coto as a cotonoma
      var targetCotonomaIds =
        repo.cotonomas.getByCotoId(sourceCotoId) match {
          // If the source coto is a cotonoma, it's the first candidate.
          case Some(sourceCotonoma) =>
            ((sourceCotonoma.id +: postedInIds) ++ repo.currentCotonomaId)

          // The cotonomas in which the source coto has been posted are
          // the default targets. If they contain the current cotonoma,
          // it's the first candidate, otherwise the last.
          case None =>
            repo.currentCotonomaId match {
              case Some(current) =>
                if (postedInIds.contains(current))
                  current +: postedInIds
                else
                  postedInIds :+ current
              case None => postedInIds
            }
        }
      targetCotonomaIds =
        (defaultCotonomaId ++ targetCotonomaIds).toSeq.distinct

      val targetCotonomas =
        targetCotonomaIds.map(repo.cotonomas.get).flatten.map(cotonoma =>
          new TargetCotonoma(
            cotonoma,
            !repo.canPostCotoTo(cotonoma) ||
              !repo.nodes.canEditItosIn(cotonoma.nodeId)
          )
        )

      Model(
        sourceCotoId = sourceCotoId,
        targetCotonomas = targetCotonomas,
        postTo = targetCotonomas.find(!_.isDisabled),
        order = order
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
    case class CotoFormMsg(submsg: CotoForm.Msg) extends Msg
    case class TargetCotonomaSelected(dest: Option[TargetCotonoma]) extends Msg
    object Post extends Msg
    case class Posted(result: Either[ErrorJson, (Coto, Ito)]) extends Msg
  }

  def update(msg: Msg, model: Model)(implicit
      context: Context
  ): (Model, Geomap, Cmd[AppMsg]) = {
    val default = (model, context.geomap, Cmd.none)
    msg match {
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
        model.post.pipe { case (model, cmd) =>
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
      span(className := "title-icon")(
        materialSymbol("subdirectory_arrow_right", "arrow"),
        materialSymbol(Coto.IconName)
      ),
      "New sub-coto"
    )(
      section(className := "source")(
        sourceCoto.map(articleCoto)
      ),
      section(className := "ito")(
        div(className := "ito-icon")(
          materialSymbol("arrow_downward")
        )
      ),
      CotoForm(
        form = model.cotoForm,
        vertical = true,
        onCtrlEnter = Some(() => dispatch(Msg.Post))
      )(context, submsg => dispatch(Msg.CotoFormMsg(submsg))),
      sectionPost(model)
    )
  }

  private def articleCoto(coto: Coto)(implicit
      context: Context,
      dispatch: Into[AppMsg] => Unit
  ): ReactElement =
    article(className := "coto embedded")(
      header()(
        PartsCoto.addressRemoteAuthor(coto)
      ),
      div(className := "body")(
        ScrollArea()(
          PartsCoto.divContentPreview(coto)
        )
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
          value = model.postTo,
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
      CotoForm.buttonPreview(model.cotoForm)(submsg =>
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
      repo.nodes.get(target.cotonoma.nodeId).map(PartsNode.imgNode(_)),
      span(className := "cotonoma-name")(target.cotonoma.name),
      Option.when(Some(target.cotonoma.id) == repo.currentCotonomaId) {
        span(className := "current-mark")("(current)")
      }
    )
  }
}
