package cotoami.models

import java.time.Instant
import com.softwaremill.quicklens._

import cotoami.backend.{CotoContent, Cotonoma}
import cotoami.subparts.FormCoto.{CotoForm, CotonomaForm}

case class WaitingPost(
    postId: String,
    content: Option[String],
    summary: Option[String],
    isCotonoma: Boolean,
    postedIn: Cotonoma,
    error: Option[String] = None
) extends CotoContent

object WaitingPost {
  def newPostId(): String =
    Instant.now().toEpochMilli().toString
}

case class WaitingPosts(posts: Seq[WaitingPost] = Seq.empty) {
  def isEmpty: Boolean = this.posts.isEmpty

  def add(post: WaitingPost): WaitingPosts =
    this.modify(_.posts).using(post +: _)

  def addCoto(
      postId: String,
      form: CotoForm,
      postedIn: Cotonoma
  ): WaitingPosts =
    this.add(
      WaitingPost(postId, form.content, form.summary, false, postedIn)
    )

  def addCotonoma(
      postId: String,
      form: CotonomaForm,
      postedIn: Cotonoma
  ): WaitingPosts =
    this.add(
      WaitingPost(postId, None, Some(form.name), true, postedIn)
    )

  def setError(postId: String, error: String): WaitingPosts =
    this.modify(_.posts.eachWhere(_.postId == postId).error).setTo(
      Some(error)
    )

  def remove(postId: String): WaitingPosts =
    this.modify(_.posts).using(_.filterNot(_.postId == postId))
}
