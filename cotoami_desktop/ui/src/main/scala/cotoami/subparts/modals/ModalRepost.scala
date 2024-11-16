package cotoami.subparts.modals

import scala.util.chaining._
import scala.scalajs.js

import slinky.core.facade.ReactElement
import slinky.web.html._

import fui.Cmd
import cotoami.utils.facade.Nullable
import cotoami.{Context, Into, Msg => AppMsg}
import cotoami.models.{Coto, Cotonoma, Id, Node}
import cotoami.repositories.{Domain, Nodes}
import cotoami.backend.{CotonomaBackend, ErrorJson}
import cotoami.subparts.{imgNode, Modal, ViewCoto}
import cotoami.components.{materialSymbol, ScrollArea, Select}

object ModalRepost {

  case class Model(
      coto: Coto,
      originalCoto: Coto,
      alreadyPostedIn: Seq[Cotonoma],
      query: String = "",
      options: Seq[Destination] = Seq.empty,
      optionsLoading: Boolean = false,
      dest: Option[Destination] = None,
      reposting: Boolean = false,
      error: Option[String] = None
  ) {
    def targetNodes(domain: Domain): js.Array[Id[Node]] =
      js.Array(
        // You can always repost a coto to the operating node.
        domain.nodes.operatingId,
        // You can repost a coto to the same node in which the coto has posted
        // only if you have a permission to post to the node.
        Option.when(domain.nodes.canPostTo(coto.nodeId))(coto.nodeId)
      ).flatten

    def readyToRepost: Boolean = dest.isDefined
  }

  object Model {
    def apply(coto: Coto, domain: Domain): Option[Model] =
      domain.cotos.getOriginal(coto) match {
        case Some(originalCoto) =>
          Some(Model(coto, originalCoto, domain.cotonomas.posted(originalCoto)))
        case _ => None
      }
  }

  class Destination(
      val name: String,
      val cotonoma: Option[Cotonoma] = None,
      val disabled: Boolean = false
  ) extends Select.SelectOption {
    val value: String = cotonoma.map(_.id.uuid).getOrElse("")
    val label: String = name
    val isDisabled: Boolean = disabled
  }

  sealed trait Msg extends Into[AppMsg] {
    def into = Modal.Msg.RepostMsg(this).pipe(AppMsg.ModalMsg)
  }

  object Msg {
    case class CotonomaQueryInput(query: String) extends Msg
    case class CotonomasFetched(
        query: String,
        result: Either[ErrorJson, js.Array[Cotonoma]]
    ) extends Msg
    case class DestinationSelected(dest: Option[Destination]) extends Msg
  }

  def update(msg: Msg, model: Model)(implicit
      context: Context
  ): (Model, Cmd[AppMsg]) =
    msg match {
      case Msg.CotonomaQueryInput(query) =>
        if (query.isBlank())
          (model.copy(query = query, options = Seq.empty), Cmd.none)
        else
          (
            model.copy(query = query),
            CotonomaBackend.fetchByPrefix(
              query,
              Some(model.targetNodes(context.domain))
            ).map(Msg.CotonomasFetched(query, _).into)
          )

      case Msg.CotonomasFetched(query, Right(cotonomas)) => {
        // If there are no cotonomas whose name is the same as the `query`,
        // add an option to create a new cotonoma with such a name and
        // repost the coto to it.
        val newCotonoma =
          if (!cotonomas.exists(_.name == query))
            Seq(new Destination(query, None))
          else
            Seq.empty
        val prefixMatches =
          cotonomas.map(cotonoma =>
            new Destination(
              cotonoma.name,
              Some(cotonoma),
              // Disable an option in which the coto has been already posted
              model.alreadyPostedIn.exists(_.id == cotonoma.id)
            )
          ).toSeq
        (
          model.copy(
            options = newCotonoma ++ prefixMatches,
            optionsLoading = false
          ),
          Cmd.none
        )
      }

      case Msg.CotonomasFetched(query, Left(e)) =>
        (
          model.copy(
            error = Some(e.default_message),
            options = Seq.empty,
            optionsLoading = false
          ),
          Cmd.none
        )

      case Msg.DestinationSelected(dest) =>
        (model.copy(dest = dest), Cmd.none)
    }

  def apply(model: Model)(implicit
      context: Context,
      dispatch: Into[AppMsg] => Unit
  ): ReactElement =
    Modal.view(
      dialogClasses = "repost",
      closeButton = Some((classOf[Modal.Repost], dispatch)),
      error = model.error
    )(
      "Repost"
    )(
      section(className := "repost-form")(
        Select(
          className = "cotonoma-select",
          options = model.options,
          placeholder = Some("Repost to..."),
          inputValue = model.query,
          onInputChange =
            Some(input => dispatch(Msg.CotonomaQueryInput(input))),
          noOptionsMessage = Some(_ => NoOptionsMessage),
          formatOptionLabel = Some(divSelectOption(context.domain.nodes, _)),
          isLoading = model.optionsLoading,
          isClearable = true,
          autoFocus = true,
          onChange = Some(option => {
            dispatch(
              Msg.DestinationSelected(
                Nullable.toOption(option).map(_.asInstanceOf[Destination])
              )
            )
          })
        ),
        button(
          className := "repost",
          `type` := "button",
          disabled := !model.readyToRepost,
          aria - "busy" := model.reposting.toString()
        )(materialSymbol("repeat"))
      ),
      articleCoto(model.originalCoto),
      sectionAlreadyPostedIn(model)
    )

  private val NoOptionsMessage = div()("Type cotonoma name...")

  private def divSelectOption(
      nodes: Nodes,
      option: Select.SelectOption
  ): ReactElement = {
    val dest = option.asInstanceOf[Destination]
    dest.cotonoma match {
      case Some(cotonoma) =>
        div(className := "existing-cotonoma")(
          nodes.get(cotonoma.nodeId).map(imgNode(_)),
          span(className := "cotonoma-name")(cotonoma.name)
        )
      case None =>
        div(className := "new-cotonoma")(
          span(className := "create")("Create:"),
          nodes.operating.map(imgNode(_)),
          span(className := "cotonoma-name")(dest.name)
        )
    }
  }

  private def sectionAlreadyPostedIn(model: Model)(implicit
      context: Context
  ): ReactElement =
    section(className := "already-posted-in")(
      h2()("Already posted in:"),
      ul()(
        model.alreadyPostedIn.map(cotonoma =>
          li()(
            context.domain.nodes.get(cotonoma.nodeId).map(imgNode(_)),
            span(className := "cotonoma-name")(cotonoma.name)
          )
        ): _*
      )
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
