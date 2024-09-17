package cotoami

package object models {

  case class Id[T](uuid: String) extends AnyVal

  trait Entity[T] {
    def id: Id[T]
  }
}
