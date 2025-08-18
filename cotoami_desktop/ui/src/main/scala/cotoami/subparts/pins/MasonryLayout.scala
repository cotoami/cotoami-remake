package cotoami.subparts.pins

import slinky.core.facade.ReactElement

import cotoami.{Context, Into, Msg => AppMsg}
import cotoami.models.Siblings

object MasonryLayout {

  def apply(
      pins: Siblings
  )(implicit context: Context, dispatch: Into[AppMsg] => Unit): ReactElement =
    sectionPinnedCotos(pins) { subCotos =>
      cotoami.subparts.pins.sectionSubCotos(subCotos)
    }
}
