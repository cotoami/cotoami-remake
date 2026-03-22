package cotoami.subparts.modeless

import cotoami.models.{Coto, Id}

enum ModelessDialogId {
  case CotoDetails(id: Id[Coto])
  case EditCoto
  case Geomap
  case NewCoto
  case NodeProfile
  case Subcoto
}
