package cotoami.i18n

import slinky.core.facade.ReactElement

trait Help {
  def ModalIncorporate_intro: ReactElement
  def ModalIncorporate_connect(operatingNodeId: String): ReactElement
}

object Help {
  def inLang(lang: String): Help = help.en
}
