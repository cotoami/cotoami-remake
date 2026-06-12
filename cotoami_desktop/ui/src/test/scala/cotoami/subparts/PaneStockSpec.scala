package cotoami.subparts

import org.scalajs.dom.URL
import org.scalatest.funsuite.AnyFunSuite

import cotoami.Model

class PaneStockSpec extends AnyFunSuite {
  test("OpenBrowser with a blank URL opens the embedded browser") {
    val model = Model(
      url = new URL("https://app.cotoami.local/"),
      flowInput = SectionFlowInput.Model(),
      geomap = SectionGeomap.Model(SectionGeomap.DefaultRemotePmtilesUrl)
    )

    val (updated, _) = PaneStock.update(PaneStock.Msg.OpenBrowser(""), model)

    assert(updated.stockBrowser.opened)
    assert(updated.stockBrowser.url == "")
    assert(updated.stockBrowser.title.isEmpty)
  }
}
