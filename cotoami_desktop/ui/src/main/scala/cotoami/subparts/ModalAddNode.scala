package cotoami.subparts

import scala.util.chaining._
import slinky.core.facade.ReactElement
import slinky.web.html._

import fui.Cmd
import cotoami.CloseModal
import cotoami.utils.Validation
import cotoami.backend.ServerNode

object ModalAddNode {

  case class Model(
      nodeUrl: String = "",
      password: String = "",
      systemError: Option[String] = None,
      processing: Boolean = false
  ) {
    def validateNodeUrl: Validation.Result =
      if (this.nodeUrl.isBlank())
        Validation.Result.toBeValidated
      else
        Validation.Result(ServerNode.validateUrl(this.nodeUrl))

    def readyToConnect: Boolean = validateNodeUrl.validated
  }

  sealed trait Msg {
    def asAppMsg: cotoami.Msg = Modal.AddNodeMsg(this).pipe(cotoami.ModalMsg)
  }

  case class NodeUrlInput(url: String) extends Msg
  case class PasswordInput(password: String) extends Msg

  def update(msg: Msg, model: Model): (Model, Seq[Cmd[cotoami.Msg]]) =
    msg match {
      case NodeUrlInput(url) =>
        (model.copy(nodeUrl = url), Seq.empty)

      case PasswordInput(password) =>
        (model.copy(password = password), Seq.empty)
    }

  def apply(
      model: Model,
      dispatch: cotoami.Msg => Unit
  ): ReactElement =
    dialog(
      className := "add-node",
      open := true,
      data - "tauri-drag-region" := "default"
    )(
      article()(
        header()(
          button(
            className := "close default",
            onClick := (_ => dispatch(CloseModal))
          ),
          h1()("Add Node")
        ),
        model.systemError.map(e => div(className := "system-error")(e)),
        div(className := "body")(
          section(className := "introduction")(
            """
            You can incorporate another database node into your database.
            Once incorporated, it will sync with the original node 
            in real-time as long as you are online.
            """
          ),
          sectionConnect(model, dispatch)
        )
      )
    )

  private def sectionConnect(
      model: Model,
      dispatch: cotoami.Msg => Unit
  ): ReactElement =
    section(className := "connect")(
      h2()("Connect"),
      form()(
        // Node URL
        div(className := "input-field")(
          label(htmlFor := "node-url")("Node URL"),
          input(
            `type` := "text",
            id := "node-url",
            name := "nodeUrl",
            placeholder := "https://example.com",
            value := model.nodeUrl,
            Validation.ariaInvalid(model.validateNodeUrl),
            onChange := ((e) => dispatch(NodeUrlInput(e.target.value).asAppMsg))
          ),
          Validation.sectionValidationError(model.validateNodeUrl)
        ),

        // Password
        div(className := "input-field")(
          label(htmlFor := "password")("Password"),
          input(
            `type` := "password",
            id := "password",
            name := "password",
            value := model.password,
            onChange := ((e) =>
              dispatch(PasswordInput(e.target.value).asAppMsg)
            )
          )
        ),

        // Preview
        div(className := "buttons")(
          button(
            `type` := "submit",
            disabled := !model.readyToConnect || model.processing
          )(
            "Preview"
          )
        )
      )
    )
}
