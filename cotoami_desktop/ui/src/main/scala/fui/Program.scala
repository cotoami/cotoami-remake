package fui

import org.scalajs.dom.URL

import slinky.core.facade.ReactElement

case class Program[Model, Msg](
    init: (URL) => (Model, Seq[Cmd[Msg]]),
    view: (Model, Msg => Unit) => ReactElement,
    update: (Msg, Model) => (Model, Seq[Cmd[Msg]]),
    subscriptions: Model => Sub[Msg] = (model: Model) => Sub.Empty,
    onUrlChange: Option[URL => Msg] = None
)
