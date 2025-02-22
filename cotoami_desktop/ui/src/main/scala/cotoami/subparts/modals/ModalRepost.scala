package cotoami.subparts.modals

import scala.util.chaining._
import scala.scalajs.js

import slinky.core.facade.ReactElement
import slinky.web.html._

import fui.Cmd
import cotoami.utils.facade.Nullable
import cotoami.{Context, Into, Msg => AppMsg}
import cotoami.models.{Coto, Cotonoma, Id, Node}
import cotoami.repository.{Cotonomas, Nodes, Root}
import cotoami.backend.{CotoBackend, CotonomaBackend, ErrorJson}
import cotoami.subparts.{imgNode, Modal, ViewCoto}
import cotoami.components.{materialSymbol, ScrollArea, Select}

object ModalRepost {

  /////////////////////////////////////////////////////////////////////////////
  // Model
  /////////////////////////////////////////////////////////////////////////////

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
    def targetNodeIds(repo: Root): js.Array[Id[Node]] =
      js.Array(
        // You can always repost a coto to the operating node.
        repo.nodes.operatingId,
        // You can repost a coto to the same node in which the coto has posted
        // only if you have a permission to post to the node.
        Option.when(repo.nodes.canPostTo(coto.nodeId))(coto.nodeId)
      ).flatten.distinct

    def readyToRepost: Boolean = dest.isDefined
  }

  object Model {
    def apply(coto: Coto, repo: Root): Option[Model] =
      repo.cotos.getOriginal(coto) match {
        case Some(originalCoto) =>
          Some(Model(coto, originalCoto, repo.cotonomas.posted(originalCoto)))
        case _ => None
      }
  }

  sealed trait Destination extends Select.SelectOption

  class ExistingCotonoma(
      val cotonoma: Cotonoma,
      val disabled: Boolean = false
  ) extends Destination {
    val value: String = s"cotonoma:${cotonoma.id}"
    val label: String = cotonoma.name
    val isDisabled: Boolean = disabled
  }

  class NewCotonoma(
      val name: String,
      // While we want to use the `Id[Node]` type here, but the type can't be restored
      // when passed back from the Select component. (Probably since it's an AnyVal the
      // type information would be lost).
      val targetNodeId: String
  ) extends Destination {
    val value: String = s"new-cotonoma:${name}:${targetNodeId}"
    val label: String = name
    val isDisabled: Boolean = false
  }

  /////////////////////////////////////////////////////////////////////////////
  // Update
  /////////////////////////////////////////////////////////////////////////////

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
    case object Repost extends Msg
    case class CotonomaCreated(result: Either[ErrorJson, (Cotonoma, Coto)])
        extends Msg
    case class Reposted(result: Either[ErrorJson, (Coto, Coto)]) extends Msg
  }

  def update(msg: Msg, model: Model)(implicit
      context: Context
  ): (Model, Cotonomas, Cmd[AppMsg]) = {
    val default = (model, context.repo.cotonomas, Cmd.none)
    msg match {
      case Msg.CotonomaQueryInput(query) =>
        if (query.isBlank())
          default.copy(_1 = model.copy(query = query, options = Seq.empty))
        else
          default.copy(
            _1 = model.copy(query = query),
            _3 = CotonomaBackend.fetchByPrefix(
              query,
              Some(model.targetNodeIds(context.repo))
            ).map(Msg.CotonomasFetched(query, _).into)
          )

      case Msg.CotonomasFetched(query, Right(cotonomas)) => {
        // If there are no cotonomas whose name is the same as the `query`,
        // add an option to create a new cotonoma with such a name and
        // repost the coto to it.
        val newCotonoma =
          model.targetNodeIds(context.repo).map(nodeId =>
            if (
              !cotonomas.exists(cotonoma =>
                cotonoma.name == query && cotonoma.nodeId == nodeId
              )
            )
              Some(new NewCotonoma(query, nodeId.uuid))
            else
              None
          ).flatten
        val prefixMatches =
          cotonomas.map(cotonoma =>
            new ExistingCotonoma(
              cotonoma,
              // Disable an option in which the coto has been already posted
              model.alreadyPostedIn.exists(_.id == cotonoma.id)
            )
          )
        default.copy(
          _1 = model.copy(
            options = (newCotonoma ++ prefixMatches).toSeq,
            optionsLoading = false
          )
        )
      }

      case Msg.CotonomasFetched(query, Left(e)) =>
        default.copy(
          _1 = model.copy(
            error = Some(e.default_message),
            options = Seq.empty,
            optionsLoading = false
          )
        )

      case Msg.DestinationSelected(dest) =>
        default.copy(_1 = model.copy(dest = dest))

      case Msg.Repost =>
        model.dest match {
          case Some(dest: ExistingCotonoma) =>
            default.copy(
              _1 = model.copy(reposting = true),
              _2 = context.repo.cotonomas.put(dest.cotonoma),
              _3 = CotoBackend.repost(model.originalCoto.id, dest.cotonoma.id)
                .map(Msg.Reposted(_).into)
            )

          case Some(dest: NewCotonoma) =>
            default.copy(
              _1 = model.copy(reposting = true),
              _3 = context.repo.nodes.get(Id(dest.targetNodeId))
                .flatMap(_.rootCotonomaId)
                .map(cotonomaId =>
                  CotonomaBackend.post(dest.name, None, None, cotonomaId)
                    .map(Msg.CotonomaCreated(_).into)
                )
                .getOrElse(Cmd.none)
            )

          case None => default // should be unreachable
        }

      case Msg.CotonomaCreated(Right((cotonoma, _))) =>
        default.copy(
          _2 = context.repo.cotonomas.put(cotonoma),
          _3 = CotoBackend.repost(model.originalCoto.id, cotonoma.id)
            .map(Msg.Reposted(_).into)
        )

      case Msg.CotonomaCreated(Left(e)) =>
        default.copy(
          _1 = model.copy(
            error = Some(e.default_message),
            reposting = false
          )
        )

      case Msg.Reposted(Right((repost, original))) =>
        default.copy(
          _1 = model.copy(
            originalCoto = original,
            alreadyPostedIn = context.repo.cotonomas.posted(original),
            reposting = false,
            query = "",
            options = Seq.empty,
            dest = None
          )
        )

      case Msg.Reposted(Left(e)) =>
        default.copy(
          _1 = model.copy(
            error = Some(e.default_message),
            reposting = false
          )
        )
    }
  }

  /////////////////////////////////////////////////////////////////////////////
  // View
  /////////////////////////////////////////////////////////////////////////////

  def apply(model: Model)(implicit
      context: Context,
      dispatch: Into[AppMsg] => Unit
  ): ReactElement =
    Modal.view(
      dialogClasses = "repost",
      closeButton = Some((classOf[Modal.Repost], dispatch)),
      error = model.error
    )(
      materialSymbol(Coto.RepostIconName),
      "Repost"
    )(
      section(className := "repost-form")(
        Select(
          className = "cotonoma-select",
          options = model.options,
          placeholder = Some("Repost to..."),
          inputValue = model.query,
          value = model.dest.getOrElse(null),
          onInputChange =
            Some(input => dispatch(Msg.CotonomaQueryInput(input))),
          noOptionsMessage = Some(_ => NoOptionsMessage),
          formatOptionLabel = Some(divSelectOption(context.repo.nodes, _)),
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
          aria - "busy" := model.reposting.toString(),
          onClick := (_ => dispatch(Msg.Repost))
        )(materialSymbol(Coto.RepostIconName))
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
    dest match {
      case dest: ExistingCotonoma =>
        div(className := "existing-cotonoma")(
          nodes.get(dest.cotonoma.nodeId).map(imgNode(_)),
          span(className := "cotonoma-name")(dest.cotonoma.name),
          spanRootCotonomaMark(dest.cotonoma, nodes)
        )

      case dest: NewCotonoma =>
        div(className := "new-cotonoma")(
          span(className := "description")("New cotonoma:"),
          nodes.get(Id(dest.targetNodeId)).map(imgNode(_)),
          span(className := "cotonoma-name")(dest.name)
        )
    }
  }

  private def articleCoto(coto: Coto)(implicit context: Context): ReactElement =
    article(className := "coto embedded")(
      header()(
        ViewCoto.addressAuthor(coto, context.repo.nodes)
      ),
      div(className := "body")(
        ScrollArea()(
          ViewCoto.divContentPreview(coto)
        )
      )
    )

  private def sectionAlreadyPostedIn(model: Model)(implicit
      context: Context
  ): ReactElement =
    section(className := "already-posted-in")(
      h2()("Already posted in:"),
      ScrollArea()(
        ul()(
          model.alreadyPostedIn.reverse.map(cotonoma =>
            li()(
              context.repo.nodes.get(cotonoma.nodeId).map(imgNode(_)),
              span(className := "cotonoma-name")(cotonoma.name),
              spanRootCotonomaMark(cotonoma, context.repo.nodes)
            )
          ): _*
        )
      )
    )

  private def spanRootCotonomaMark(
      cotonoma: Cotonoma,
      nodes: Nodes
  ): ReactElement =
    Option.when(nodes.isNodeRoot(cotonoma)) {
      span(className := "root-mark")("(root)")
    }
}
