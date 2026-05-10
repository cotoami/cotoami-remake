package cotoami.subparts

import org.scalajs.dom.URL
import org.scalatest.funsuite.AnyFunSuite

import cotoami.{Context, Model as AppModel}
import cotoami.models.WaitingPosts
import cotoami.subparts.EditorCoto.CotoForm

class SectionFlowInputSpec extends AnyFunSuite {
  test("ReplaceCotoDraft replaces existing fields with a preview coto draft") {
    val current = SectionFlowInput.Model(
      form = CotoForm.Model(
        inPreview = false,
        summaryInput = "old summary",
        contentInput = "old content"
      ),
      folded = true,
      focused = false,
      posting = true,
      persistDraft = false
    )
    given Context = AppModel(
      url = new URL("https://app.cotoami.local/"),
      flowInput = current,
      geomap = SectionGeomap.Model(SectionGeomap.DefaultRemotePmtilesUrl)
    )

    val (updated, _, _, _) = SectionFlowInput.update(
      SectionFlowInput.Msg.ReplaceCotoDraft("new content", preview = true),
      current,
      WaitingPosts(),
      persistDraft = Some(false)
    )

    val form = updated.form.asInstanceOf[CotoForm.Model]
    assert(form.contentInput == "new content")
    assert(form.summaryInput == "")
    assert(form.inPreview)
    assert(form.mediaBase64.isEmpty)
    assert(form.geolocation.isEmpty)
    assert(!updated.folded)
    assert(updated.focused)
    assert(!updated.posting)
  }
}
