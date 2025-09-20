package cotoami.i18n.text

import slinky.core.facade.{Fragment, ReactElement}
import slinky.web.html._

import cotoami.i18n.Text

object fr extends Text {
  val Coto = "Coto"
  val Cotonoma = "Cotonoma"
  val Ito = "Ito"
  val Pin = "Épingle"
  val Node = "Nœud"
  val Owner = "Propriétaire"
  val Server = "Serveur"
  val Client = "Client"

  val Id = "ID"
  val Name = "Nom"
  val Password = "Mot de passe"

  val OK = "OK"
  val Cancel = "Annuler"
  val Post = "Publier"
  val Insert = "Insérer"
  val Save = "Enregistrer"
  val Edit = "Modifier"
  val Preview = "Aperçu"
  val Delete = "Supprimer"
  val Repost = "Republier"
  val Promote = "Promouvoir"
  val Traverse = "Parcourir"
  val Select = "Sélectionner"
  val Deselect = "Désélectionner"
  val Register = "Enregistrer"
  val Back = "Retour"

  val DeleteCotonoma = "Supprimer Cotonoma"
  val WriteSubcoto = "Écrire un Sous-coto"
  val OpenMap = "Ouvrir la Carte"
  val CloseMap = "Fermer la Carte"
  val SwapPane = "Échanger les Panneaux"
  val LightMode = "Mode Clair"
  val DarkMode = "Mode Sombre"
  val MarkAllAsRead = "Marquer Tout comme Lu"
  val PostTo = "Publier dans"

  def Coto_inRemoteNode(nodeName: String) = s"Dans ${nodeName} (distant)"

  val Node_id = "ID du Nœud"
  val Node_root = "Racine du Nœud"
  val Node_notYetConnected = "Pas encore connecté"
  val Node_settings = "Paramètres du Nœud"

  val Ito_description_placeholder = "Description de l'Ito (optionnel)"
  val Ito_editPin = "Modifier l'Épingle"
  val Ito_editIto = "Modifier l'Ito"

  val Owner_resetPassword = "Réinitialiser le Mot de Passe Propriétaire"
  val Owner_confirmResetPassword =
    """
    Êtes-vous sûr de vouloir générer un nouveau mot de passe propriétaire ? 
    Cela invalidera le mot de passe actuel.
    """

  val Connection_disabled = "non synchronisé"
  val Connection_connecting = "connexion en cours"
  val Connection_initFailed = "échec de l'initialisation"
  val Connection_authenticationFailed = "échec de l'authentification"
  val Connection_sessionExpired = "session expirée"
  val Connection_disconnected = "déconnecté"
  val Connection_connected = "connecté"

  val ChildPrivileges = "Privilèges"
  val ChildPrivileges_asOwner = "Propriétaire (privilèges complets)"
  val ChildPrivileges_canPostCotos = "Publier des cotos"
  val ChildPrivileges_canEditItos = "Modifier les itos"
  val ChildPrivileges_canPostCotonomas = "Publier des cotonomas"
  val ChildPrivileges_readOnly = "Lecture seule"

  val ConfirmDeleteCoto = "Êtes-vous sûr de vouloir supprimer le coto ?"
  def ConfirmDeleteOthersCoto(someoneElse: ReactElement): ReactElement =
    span(className := "delete-others-coto")(
      "En tant que propriétaire, vous êtes sur le point de supprimer un coto publié par :",
      someoneElse
    )
  val ConfirmDeleteCotonoma = "Êtes-vous sûr de vouloir supprimer le cotonoma ?"

  val NavNodes_allNodes = "Tous les Nœuds"
  val NavNodes_addNode = "Ajouter un Nœud"

  val NavCotonomas_current = "Actuel"
  val NavCotonomas_recent = "Récent"

  val PaneStock_map_dockLeft = "Ancrer à Gauche"
  val PaneStock_map_dockTop = "Ancrer en Haut"

  val SectionPins_layout_document = "Document"
  val SectionPins_layout_columns = "Colonnes"
  val SectionPins_layout_masonry = "Maçonnerie"

  val SectionNodeTools_enableSync = "Activer la Synchronisation"
  val SectionNodeTools_disableSync = "Désactiver la Synchronisation"

  val EditorCoto_placeholder_coto = "Écrivez votre coto en Markdown"
  val EditorCoto_placeholder_summary = "Résumé (optionnel)"
  val EditorCoto_placeholder_newCotonomaName = "Nom du nouveau cotonoma"
  val EditorCoto_placeholder_cotonomaName = "Nom du cotonoma"
  val EditorCoto_placeholder_cotonomaContent =
    "Écrivez une description du cotonoma en Markdown"
  val EditorCoto_inputImage =
    "Déposez un fichier image ici, ou cliquez pour en sélectionner un"
  val EditorCoto_date = "Date"
  val EditorCoto_location = "Emplacement"
  val EditorCoto_help_selectLocation = "Cliquez sur un emplacement sur la carte"
  def EditorCoto_cotonomaAlreadyExists(cotonomaName: String) =
    s"Le cotonoma \"${cotonomaName}\" existe déjà dans ce nœud."

  val ModalConfirm_title = "Confirmation"

  val ModalWelcome_title = "Bienvenue dans Cotoami"
  val ModalWelcome_recent = "Récent"
  val ModalWelcome_new = "Nouvelle Base de Données"
  val ModalWelcome_new_name = "Nom"
  val ModalWelcome_new_baseFolder = "Dossier de Base"
  val ModalWelcome_new_selectBaseFolder = "Sélectionner un Dossier de Base"
  val ModalWelcome_new_folderName = "Nom du Dossier à Créer"
  val ModalWelcome_new_create = "Créer"
  val ModalWelcome_open = "Ouvrir"
  val ModalWelcome_open_folder = "Dossier de Base de Données"
  val ModalWelcome_open_selectFolder =
    "Sélectionner un Dossier de Base de Données"
  val ModalWelcome_open_open = "Ouvrir"
  def ModalWelcome_update_message(newVersion: String) =
    span()(
      "Une nouvelle version ",
      span(className := "version")(newVersion),
      " de Cotoami Desktop est disponible."
    )
  val ModalWelcome_update_updateNow = "Mettre à Jour Maintenant"

  val ModalAppUpdate_title = "Mise à Jour de l'Application"
  def ModalAppUpdate_message(
      newVersion: String,
      currentVersion: String
  ) = span()(
    "Téléchargement et installation de la version ",
    span(className := "version")(newVersion),
    " (actuelle : ",
    span(className := "version")(currentVersion),
    ")"
  )
  val ModalAppUpdate_restart = "Redémarrer l'Application"

  val ModalInputOwnerPassword_title = "Mot de Passe Propriétaire Requis"
  val ModalInputOwnerPassword_message =
    "Vous devez saisir le mot de passe propriétaire pour ouvrir cette base de données."

  val ModalInputClientPassword_title = "Mot de Passe Client Requis"
  val ModalInputClientPassword_message =
    """
    Échec de la connexion au nœud serveur avec le mot de passe configuré.
    Pour vous reconnecter à ce nœud, veuillez saisir un nouveau mot de passe.
    """

  val ModalNewOwnerPassword_title = "Nouveau Mot de Passe Propriétaire"
  val ModalNewOwnerPassword_message =
    """
    Stockez ce mot de passe dans un endroit sûr. 
    Vous en aurez besoin pour ouvrir cette base de données sur un autre ordinateur. 
    Vous pouvez générer un nouveau mot de passe depuis le profil du nœud à tout moment.
    """

  val ModalNewClientPassword_title = "Nouveau Mot de Passe Client"
  val ModalNewClientPassword_message =
    """
    Envoyez ce mot de passe au propriétaire du nœud en utilisant une méthode sécurisée.
    """

  val ModalSelection_title = "Cotos Sélectionnés"
  val ModalSelection_clear = "Effacer la Sélection"

  val ModalNewIto_title = "Nouvel Ito"
  val ModalNewIto_reverse = "Inverser"
  val ModalNewIto_clearSelection = "Effacer la sélection lors de la connexion"
  val ModalNewIto_connect = "Connecter"

  val ModalSubcoto_title = "Nouveau Sous-coto"

  val ModalNodeProfile_title = "Profil du Nœud"
  val ModalNodeProfile_selfNode = "Vous"
  val ModalNodeProfile_switched = "basculé"
  val ModalNodeProfile_description = "Description"

  val FieldImageMaxSize = "Seuil de Redimensionnement d'Image (pixels)"
  val FieldImageMaxSize_placeholder = "Pas de redimensionnement"

  val FieldOwnerPassword = "Mot de Passe Propriétaire"

  val SelfNodeServer_title = "Serveur de Nœud"
  val SelfNodeServer_url = "URL"
  val SelfNodeServer_clientNodes = "Nœuds Clients"
  val SelfNodeServer_anonymousRead = "Accepter la Lecture Anonyme"
  val SelfNodeServer_confirmEnableAnonymousRead =
    """
    Êtes-vous sûr de vouloir autoriser l'accès anonyme en lecture seule
    (toute personne connaissant l'URL de ce nœud peut voir votre contenu) ?
    """
  val SelfNodeServer_anonymousConnections = "Connexions actives"

  val AsServer_title = "En tant que Serveur"
  val AsServer_url = "URL"
  val AsServer_connection = "Connexion"

  val AsClient_title = "En tant que Client"
  val AsClient_resetPassword = "Réinitialiser le Mot de Passe Client"
  val AsClient_confirmResetPassword =
    """
    Êtes-vous sûr de vouloir générer un nouveau mot de passe client ? 
    Cela invalidera le mot de passe actuel.
    """
  val AsClient_lastLogin = "Dernière Connexion"
  val AsClient_remoteAddress = "Adresse Distante"

  val AsChild_title = "En tant qu'Enfant"

  val ModalIncorporate_title = "Incorporer un Nœud Distant"
  val ModalIncorporate_nodeUrl = "URL du Nœud"
  val ModalIncorporate_incorporate = "Incorporer"

  val ModalPromote_title = "Promouvoir en Cotonoma"
  val ModalPromote_confirm =
    """
    Êtes-vous sûr de vouloir promouvoir ce coto en cotonoma ?
    Cette action ne peut pas être annulée.
    """

  val ModalEditIto_disconnect = "Déconnecter"
  val ModalEditIto_confirmDisconnect =
    "Êtes-vous sûr de vouloir supprimer cet ito ?"

  val ModalRepost_title = "Republier"
  val ModalRepost_repostTo = "Republier dans"
  val ModalRepost_typeCotonomaName = "Tapez le nom du cotonoma"
  val ModalRepost_newCotonoma = "Nouveau cotonoma"
  val ModalRepost_root = "racine"
  val ModalRepost_alreadyPostedIn = "Déjà publié dans"

  val ModalClients_title = "Nœuds Clients"
  val ModalClients_add = "Ajouter un Client"
  val ModalClients_connecting = "connexion en cours"
  val ModalClients_nodes = "nœuds"
  val ModalClients_noClients = "Aucun nœud client enregistré pour le moment."
  val ModalClients_column_name = "Nom"
  val ModalClients_column_lastLogin = "Dernière Connexion"
  val ModalClients_column_status = "Statut"
  val ModalClients_column_enabled = "Activé"

  val ModalNewClient_title = "Nouveau Client"
  val ModalNewClient_registered =
    """
    Le nœud enfant ci-dessous a été enregistré.
    Envoyez le mot de passe généré au propriétaire du nœud de manière sécurisée.
    """

  val ModalSwitchNode_title = "Changer de Nœud"
  val ModalSwitchNode_switch = "Changer"
  val ModalSwitchNode_message =
    """
    Vous êtes sur le point de changer le nœud sur lequel opérer comme indiqué ci-dessous.
    """

  val ModalNodeIcon_title = "Changer l'Icône du Nœud"
  val ModalNodeIcon_inputImage = Fragment(
    "Glissez et déposez un fichier image ici,",
    br(),
    "ou cliquez pour en sélectionner un"
  )
}
