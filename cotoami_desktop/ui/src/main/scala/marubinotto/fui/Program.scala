package marubinotto.fui

import org.scalajs.dom.URL

import slinky.core.facade.ReactElement

case class Program[Model, Msg](
    init: (URL) => (Model, Cmd[Msg]),
    view: (Model, Msg => Unit) => ReactElement,
    update: (Msg, Model) => (Model, Cmd[Msg]),
    subscriptions: Model => Sub[Msg] = (model: Model) => Sub.Empty,
    onUrlChange: Option[URL => Msg] = None
)
