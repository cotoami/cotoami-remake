package cotoami.subparts.modals

import scala.util.chaining._
import scala.scalajs.js

import slinky.core.facade.ReactElement
import slinky.web.html._

import fui.Cmd
import cotoami.{Context, Into, Msg => AppMsg}
import cotoami.models.{Coto, Cotonoma, Id, Node}
import cotoami.repositories.{Cotos, Domain, Nodes}
import cotoami.backend.{CotonomaBackend, ErrorJson}
import cotoami.subparts.{imgNode, Modal, ViewCoto}
import cotoami.components.{materialSymbol, ScrollArea, Select}

object ModalRepost {

  case class Model(
      cotoId: Id[Coto],
      cotonomaName: String = "",
      options: Seq[Destination] = Seq.empty,
      optionsLoading: Boolean = false,
      error: Option[String] = None
  ) {
    def coto(cotos: Cotos): Option[Coto] = cotos.get(cotoId)

    def originalCoto(cotos: Cotos): Option[Coto] =
      coto(cotos).map(cotos.getOriginal)

    def targetNodes(domain: Domain): js.Array[Id[Node]] =
      js.Array(
        // You can always repost a coto to the operating node.
        domain.nodes.operatingId,
        // You can repost a coto to the same node in which the coto has posted
        // only if you have a permission to post to the node.
        coto(domain.cotos).map(_.nodeId).flatMap(nodeId =>
          Option.when(domain.nodes.canPostTo(nodeId))(nodeId)
        )
      ).flatten
  }

  class Destination(
      val name: String,
      val cotonoma: Option[Cotonoma] = None
  ) extends Select.SelectOption {
    val value: String = cotonoma.map(_.id.uuid).getOrElse("")
    val label: String = name
  }

  sealed trait Msg extends Into[AppMsg] {
    def into = Modal.Msg.RepostMsg(this).pipe(AppMsg.ModalMsg)
  }

  object Msg {
    case class CotonomaNameInput(name: String) extends Msg
    case class CotonomaOptionsFetched(
        query: String,
        result: Either[ErrorJson, js.Array[Cotonoma]]
    ) extends Msg
  }

  def update(msg: Msg, model: Model)(implicit
      context: Context
  ): (Model, Cmd[AppMsg]) =
    msg match {
      case Msg.CotonomaNameInput(name) =>
        (
          model.copy(cotonomaName = name),
          CotonomaBackend.fetchByPrefix(
            name,
            Some(model.targetNodes(context.domain))
          ).map(Msg.CotonomaOptionsFetched(name, _).into)
        )

      case Msg.CotonomaOptionsFetched(query, Right(cotonomas)) => {
        val newCotonoma =
          if (!query.isBlank && !cotonomas.exists(_.name == query))
            Seq(new Destination(query, None))
          else
            Seq.empty
        val prefixMatches =
          cotonomas.map(cotonoma =>
            new Destination(cotonoma.name, Some(cotonoma))
          ).toSeq
        (
          model.copy(
            options = newCotonoma ++ prefixMatches,
            optionsLoading = false
          ),
          Cmd.none
        )
      }

      case Msg.CotonomaOptionsFetched(query, Left(e)) =>
        (
          model.copy(
            error = Some(e.default_message),
            options = Seq.empty,
            optionsLoading = false
          ),
          Cmd.none
        )
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
          placeholder = Some("Cotonoma name"),
          inputValue = model.cotonomaName,
          onInputChange = Some(input => dispatch(Msg.CotonomaNameInput(input))),
          noOptionsMessage = Some(_ => NoOptionsMessage),
          formatOptionLabel = Some(divSelectOption(context.domain.nodes, _)),
          isLoading = model.optionsLoading
        ),
        button(
          className := "repost",
          `type` := "button",
          disabled := true
        )(materialSymbol("repeat"))
      ),
      model.originalCoto(context.domain.cotos).map(articleCoto)
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
