package cotoami.i18n.text

import slinky.core.facade.{Fragment, ReactElement}
import slinky.web.html._

import cotoami.i18n.Text

object de extends Text {
  val Coto = "Coto"
  val Cotonoma = "Cotonoma"
  val Ito = "Ito"
  val Pin = "Pin"
  val Node = "Knoten"
  val Owner = "Eigentümer"
  val Server = "Server"
  val Client = "Client"

  val Id = "ID"
  val Name = "Name"
  val Password = "Passwort"

  val OK = "OK"
  val Cancel = "Abbrechen"
  val Post = "Posten"
  val Insert = "Einfügen"
  val Save = "Speichern"
  val Edit = "Bearbeiten"
  val Preview = "Vorschau"
  val Delete = "Löschen"
  val Repost = "Erneut posten"
  val Promote = "Befördern"
  val Traverse = "Durchlaufen"
  val Select = "Auswählen"
  val Deselect = "Abwählen"
  val Register = "Registrieren"
  val Back = "Zurück"

  val DeleteCotonoma = "Cotonoma löschen"
  val WriteSubcoto = "Sub-Coto schreiben"
  val OpenMap = "Karte öffnen"
  val CloseMap = "Karte schließen"
  val SwapPane = "Bereiche tauschen"
  val LightMode = "Heller Modus"
  val DarkMode = "Dunkler Modus"
  val MarkAllAsRead = "Alle als gelesen markieren"
  val PostTo = "Posten in"

  def Coto_inRemoteNode(nodeName: String) = s"In ${nodeName} (entfernt)"

  val Node_id = "Knoten-ID"
  val Node_root = "Knoten-Wurzel"
  val Node_notYetConnected = "Noch nicht verbunden"
  val Node_settings = "Knoten-Einstellungen"

  val Ito_description_placeholder = "Ito-Beschreibung (optional)"
  val Ito_editPin = "Pin bearbeiten"
  val Ito_editIto = "Ito bearbeiten"

  val Owner_resetPassword = "Eigentümer-Passwort zurücksetzen"
  val Owner_confirmResetPassword =
    """
    Sind Sie sicher, dass Sie ein neues Eigentümer-Passwort generieren möchten? 
    Dadurch wird das aktuelle Passwort ungültig.
    """

  val Connection_disabled = "nicht synchronisiert"
  val Connection_connecting = "verbinde"
  val Connection_initFailed = "Initialisierung fehlgeschlagen"
  val Connection_authenticationFailed = "Authentifizierung fehlgeschlagen"
  val Connection_sessionExpired = "Sitzung abgelaufen"
  val Connection_disconnected = "getrennt"
  val Connection_connected = "verbunden"

  val ChildPrivileges = "Berechtigungen"
  val ChildPrivileges_asOwner = "Eigentümer (volle Berechtigungen)"
  val ChildPrivileges_canPostCotos = "Cotos posten"
  val ChildPrivileges_canEditItos = "Itos bearbeiten"
  val ChildPrivileges_canPostCotonomas = "Cotonomas posten"
  val ChildPrivileges_readOnly = "Nur lesen"

  val ConfirmDeleteCoto = "Sind Sie sicher, dass Sie das Coto löschen möchten?"
  def ConfirmDeleteOthersCoto(someoneElse: ReactElement): ReactElement =
    span(className := "delete-others-coto")(
      "Als Eigentümer sind Sie dabei, ein Coto zu löschen, das gepostet wurde von:",
      someoneElse
    )
  val ConfirmDeleteCotonoma =
    "Sind Sie sicher, dass Sie das Cotonoma löschen möchten?"

  val NavNodes_allNodes = "Alle Knoten"
  val NavNodes_addNode = "Knoten hinzufügen"

  val NavCotonomas_current = "Aktuell"
  val NavCotonomas_recent = "Kürzlich"

  val PaneStock_map_dockLeft = "Links andocken"
  val PaneStock_map_dockTop = "Oben andocken"

  val SectionPins_layout_document = "Dokument"
  val SectionPins_layout_columns = "Spalten"
  val SectionPins_layout_masonry = "Mauerwerk"

  val SectionNodeTools_enableSync = "Synchronisation aktivieren"
  val SectionNodeTools_disableSync = "Synchronisation deaktivieren"

  val EditorCoto_placeholder_coto = "Schreiben Sie Ihr Coto in Markdown"
  val EditorCoto_placeholder_summary = "Zusammenfassung (optional)"
  val EditorCoto_placeholder_newCotonomaName = "Neuer Cotonoma-Name"
  val EditorCoto_placeholder_cotonomaName = "Cotonoma-Name"
  val EditorCoto_placeholder_cotonomaContent =
    "Schreiben Sie eine Cotonoma-Beschreibung in Markdown"
  val EditorCoto_inputImage =
    "Ziehen Sie eine Bilddatei hierher oder klicken Sie, um eine auszuwählen"
  val EditorCoto_date = "Datum"
  val EditorCoto_location = "Ort"
  val EditorCoto_help_selectLocation = "Klicken Sie auf einen Ort auf der Karte"
  def EditorCoto_cotonomaAlreadyExists(cotonomaName: String) =
    s"Das Cotonoma \"${cotonomaName}\" existiert bereits in diesem Knoten."

  val ModalConfirm_title = "Bestätigung"

  val ModalWelcome_title = "Willkommen bei Cotoami"
  val ModalWelcome_recent = "Kürzlich"
  val ModalWelcome_new = "Neue Datenbank"
  val ModalWelcome_new_name = "Name"
  val ModalWelcome_new_baseFolder = "Basis-Ordner"
  val ModalWelcome_new_selectBaseFolder = "Basis-Ordner auswählen"
  val ModalWelcome_new_folderName = "Name des zu erstellenden Ordners"
  val ModalWelcome_new_create = "Erstellen"
  val ModalWelcome_open = "Öffnen"
  val ModalWelcome_open_folder = "Datenbank-Ordner"
  val ModalWelcome_open_selectFolder = "Datenbank-Ordner auswählen"
  val ModalWelcome_open_open = "Öffnen"
  def ModalWelcome_update_message(newVersion: String) =
    span()(
      "Eine neue Version ",
      span(className := "version")(newVersion),
      " von Cotoami Desktop ist verfügbar."
    )
  val ModalWelcome_update_updateNow = "Jetzt aktualisieren"

  val ModalAppUpdate_title = "Anwendung wird aktualisiert"
  def ModalAppUpdate_message(
      newVersion: String,
      currentVersion: String
  ) = span()(
    "Lade herunter und installiere Version ",
    span(className := "version")(newVersion),
    " (aktuell: ",
    span(className := "version")(currentVersion),
    ")"
  )
  val ModalAppUpdate_restart = "Anwendung neu starten"

  val ModalInputOwnerPassword_title = "Eigentümer-Passwort erforderlich"
  val ModalInputOwnerPassword_message =
    "Sie müssen das Eigentümer-Passwort eingeben, um diese Datenbank zu öffnen."

  val ModalInputClientPassword_title = "Client-Passwort erforderlich"
  val ModalInputClientPassword_message =
    """
    Anmeldung am Server-Knoten mit dem konfigurierten Passwort fehlgeschlagen.
    Um sich wieder mit diesem Knoten zu verbinden, geben Sie bitte ein neues Passwort ein.
    """

  val ModalNewOwnerPassword_title = "Neues Eigentümer-Passwort"
  val ModalNewOwnerPassword_message =
    """
    Bewahren Sie dieses Passwort an einem sicheren Ort auf. 
    Sie benötigen es, um diese Datenbank auf einem anderen Computer zu öffnen. 
    Sie können jederzeit ein neues Passwort aus dem Knoten-Profil generieren.
    """

  val ModalNewClientPassword_title = "Neues Client-Passwort"
  val ModalNewClientPassword_message =
    """
    Senden Sie dieses Passwort mit einer sicheren Methode an den Knoten-Eigentümer.
    """

  val ModalSelection_title = "Ausgewählte Cotos"
  val ModalSelection_clear = "Auswahl löschen"

  val ModalNewIto_title = "Neues Ito"
  val ModalNewIto_reverse = "Umkehren"
  val ModalNewIto_clearSelection = "Auswahl beim Verbinden löschen"
  val ModalNewIto_connect = "Verbinden"

  val ModalSubcoto_title = "Neues Sub-Coto"

  val ModalNodeProfile_title = "Knoten-Profil"
  val ModalNodeProfile_selfNode = "Sie"
  val ModalNodeProfile_switched = "gewechselt"
  val ModalNodeProfile_description = "Beschreibung"

  val FieldImageMaxSize = "Schwellenwert für Bildgrößenänderung (Pixel)"
  val FieldImageMaxSize_placeholder = "Keine Größenänderung"

  val FieldOwnerPassword = "Eigentümer-Passwort"

  val SelfNodeServer_title = "Knoten-Server"
  val SelfNodeServer_url = "URL"
  val SelfNodeServer_clientNodes = "Client-Knoten"
  val SelfNodeServer_anonymousRead = "Anonymes Lesen akzeptieren"
  val SelfNodeServer_confirmEnableAnonymousRead =
    """
    Sind Sie sicher, dass Sie anonymen schreibgeschützten Zugriff erlauben möchten
    (jeder, der die URL dieses Knotens kennt, kann Ihren Inhalt einsehen)?
    """
  val SelfNodeServer_anonymousConnections = "Aktive Verbindungen"

  val AsServer_title = "Als Server"
  val AsServer_url = "URL"
  val AsServer_connection = "Verbindung"

  val AsClient_title = "Als Client"
  val AsClient_resetPassword = "Client-Passwort zurücksetzen"
  val AsClient_confirmResetPassword =
    """
    Sind Sie sicher, dass Sie ein neues Client-Passwort generieren möchten? 
    Dadurch wird das aktuelle Passwort ungültig.
    """
  val AsClient_lastLogin = "Letzte Anmeldung"
  val AsClient_remoteAddress = "Entfernte Adresse"

  val AsChild_title = "Als Kind"

  val ModalIncorporate_title = "Entfernten Knoten einbinden"
  val ModalIncorporate_nodeUrl = "Knoten-URL"
  val ModalIncorporate_incorporate = "Einbinden"

  val ModalPromote_title = "Zu Cotonoma befördern"
  val ModalPromote_confirm =
    """
    Sind Sie sicher, dass Sie dieses Coto zu einem Cotonoma befördern möchten?
    Diese Aktion kann nicht rückgängig gemacht werden.
    """

  val ModalEditIto_disconnect = "Trennen"
  val ModalEditIto_confirmDisconnect =
    "Sind Sie sicher, dass Sie dieses Ito löschen möchten?"

  val ModalRepost_title = "Erneut posten"
  val ModalRepost_repostTo = "Erneut posten in"
  val ModalRepost_typeCotonomaName = "Cotonoma-Name eingeben"
  val ModalRepost_newCotonoma = "Neues Cotonoma"
  val ModalRepost_root = "Wurzel"
  val ModalRepost_alreadyPostedIn = "Bereits gepostet in"

  val ModalClients_title = "Client-Knoten"
  val ModalClients_add = "Client hinzufügen"
  val ModalClients_connecting = "verbinde"
  val ModalClients_nodes = "Knoten"
  val ModalClients_noClients = "Noch keine Client-Knoten registriert."
  val ModalClients_column_name = "Name"
  val ModalClients_column_lastLogin = "Letzte Anmeldung"
  val ModalClients_column_status = "Status"
  val ModalClients_column_enabled = "Aktiviert"

  val ModalNewClient_title = "Neuer Client"
  val ModalNewClient_registered =
    """
    Der unten stehende Kind-Knoten wurde registriert.
    Senden Sie das generierte Passwort auf sichere Weise an den Eigentümer des Knotens.
    """

  val ModalSwitchNode_title = "Knoten wechseln"
  val ModalSwitchNode_switch = "Wechseln"
  val ModalSwitchNode_message =
    """
    Sie sind dabei, den Knoten zu wechseln, auf dem Sie arbeiten, wie unten gezeigt.
    """

  val ModalNodeIcon_title = "Knoten-Symbol ändern"
  val ModalNodeIcon_inputImage = Fragment(
    "Ziehen Sie eine Bilddatei hierher,",
    br(),
    "oder klicken Sie, um eine auszuwählen"
  )
}
