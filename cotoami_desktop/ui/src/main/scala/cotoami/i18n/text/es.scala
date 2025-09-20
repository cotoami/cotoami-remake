package cotoami.i18n.text

import slinky.core.facade.{Fragment, ReactElement}
import slinky.web.html._

import cotoami.i18n.Text

object es extends Text {
  val Coto = "Coto"
  val Cotonoma = "Cotonoma"
  val Ito = "Ito"
  val Pin = "Pin"
  val Node = "Nodo"
  val Owner = "Propietario"
  val Server = "Servidor"
  val Client = "Cliente"

  val Id = "ID"
  val Name = "Nombre"
  val Password = "Contraseña"

  val OK = "OK"
  val Cancel = "Cancelar"
  val Post = "Publicar"
  val Insert = "Insertar"
  val Save = "Guardar"
  val Edit = "Editar"
  val Preview = "Vista previa"
  val Delete = "Eliminar"
  val Repost = "Republicar"
  val Promote = "Promover"
  val Traverse = "Recorrer"
  val Select = "Seleccionar"
  val Deselect = "Deseleccionar"
  val Register = "Registrar"
  val Back = "Atrás"

  val DeleteCotonoma = "Eliminar Cotonoma"
  val WriteSubcoto = "Escribir Sub-coto"
  val OpenMap = "Abrir Mapa"
  val CloseMap = "Cerrar Mapa"
  val SwapPane = "Intercambiar Panel"
  val LightMode = "Modo Claro"
  val DarkMode = "Modo Oscuro"
  val MarkAllAsRead = "Marcar Todo como Leído"
  val PostTo = "Publicar en"

  def Coto_inRemoteNode(nodeName: String) = s"En ${nodeName} (remoto)"

  val Node_id = "ID del Nodo"
  val Node_root = "Raíz del Nodo"
  val Node_notYetConnected = "Aún no conectado"
  val Node_settings = "Configuración del Nodo"

  val Ito_description_placeholder = "Descripción del Ito (opcional)"
  val Ito_editPin = "Editar Pin"
  val Ito_editIto = "Editar Ito"

  val Owner_resetPassword = "Restablecer Contraseña del Propietario"
  val Owner_confirmResetPassword =
    """
    ¿Está seguro de que desea generar una nueva contraseña de propietario? 
    Hacerlo invalidará la contraseña actual.
    """

  val Connection_disabled = "no sincronizado"
  val Connection_connecting = "conectando"
  val Connection_initFailed = "falló la inicialización"
  val Connection_authenticationFailed = "falló la autenticación"
  val Connection_sessionExpired = "sesión expirada"
  val Connection_disconnected = "desconectado"
  val Connection_connected = "conectado"

  val ChildPrivileges = "Privilegios"
  val ChildPrivileges_asOwner = "Propietario (privilegios completos)"
  val ChildPrivileges_canPostCotos = "Publicar cotos"
  val ChildPrivileges_canEditItos = "Editar itos"
  val ChildPrivileges_canPostCotonomas = "Publicar cotonomas"
  val ChildPrivileges_readOnly = "Solo lectura"

  val ConfirmDeleteCoto = "¿Está seguro de que desea eliminar el coto?"
  def ConfirmDeleteOthersCoto(someoneElse: ReactElement): ReactElement =
    span(className := "delete-others-coto")(
      "Como propietario, está a punto de eliminar un coto publicado por:",
      someoneElse
    )
  val ConfirmDeleteCotonoma = "¿Está seguro de que desea eliminar el cotonoma?"

  val NavNodes_allNodes = "Todos los Nodos"
  val NavNodes_addNode = "Agregar Nodo"

  val NavCotonomas_current = "Actual"
  val NavCotonomas_recent = "Reciente"

  val PaneStock_map_dockLeft = "Acoplar a la Izquierda"
  val PaneStock_map_dockTop = "Acoplar Arriba"

  val SectionPins_layout_document = "Documento"
  val SectionPins_layout_columns = "Columnas"
  val SectionPins_layout_masonry = "Mampostería"

  val SectionNodeTools_enableSync = "Habilitar Sincronización"
  val SectionNodeTools_disableSync = "Deshabilitar Sincronización"

  val EditorCoto_placeholder_coto = "Escriba su coto en Markdown"
  val EditorCoto_placeholder_summary = "Resumen (opcional)"
  val EditorCoto_placeholder_newCotonomaName = "Nombre del nuevo cotonoma"
  val EditorCoto_placeholder_cotonomaName = "Nombre del cotonoma"
  val EditorCoto_placeholder_cotonomaContent =
    "Escriba una descripción del cotonoma en Markdown"
  val EditorCoto_inputImage =
    "Suelte un archivo de imagen aquí, o haga clic para seleccionar uno"
  val EditorCoto_date = "Fecha"
  val EditorCoto_location = "Ubicación"
  val EditorCoto_help_selectLocation = "Haga clic en una ubicación en el mapa"
  def EditorCoto_cotonomaAlreadyExists(cotonomaName: String) =
    s"El cotonoma \"${cotonomaName}\" ya existe en este nodo."

  val ModalConfirm_title = "Confirmación"

  val ModalWelcome_title = "Bienvenido a Cotoami"
  val ModalWelcome_recent = "Reciente"
  val ModalWelcome_new = "Nueva Base de Datos"
  val ModalWelcome_new_name = "Nombre"
  val ModalWelcome_new_baseFolder = "Carpeta Base"
  val ModalWelcome_new_selectBaseFolder = "Seleccionar una Carpeta Base"
  val ModalWelcome_new_folderName = "Nombre de la Carpeta a Crear"
  val ModalWelcome_new_create = "Crear"
  val ModalWelcome_open = "Abrir"
  val ModalWelcome_open_folder = "Carpeta de Base de Datos"
  val ModalWelcome_open_selectFolder =
    "Seleccionar una Carpeta de Base de Datos"
  val ModalWelcome_open_open = "Abrir"
  def ModalWelcome_update_message(newVersion: String) =
    span()(
      "Una nueva versión ",
      span(className := "version")(newVersion),
      " de Cotoami Desktop está disponible."
    )
  val ModalWelcome_update_updateNow = "Actualizar Ahora"

  val ModalAppUpdate_title = "Actualizando Aplicación"
  def ModalAppUpdate_message(
      newVersion: String,
      currentVersion: String
  ) = span()(
    "Descargando e instalando la versión ",
    span(className := "version")(newVersion),
    " (actual: ",
    span(className := "version")(currentVersion),
    ")"
  )
  val ModalAppUpdate_restart = "Reiniciar Aplicación"

  val ModalInputOwnerPassword_title = "Contraseña del Propietario Requerida"
  val ModalInputOwnerPassword_message =
    "Necesita ingresar la contraseña del propietario para abrir esta base de datos."

  val ModalInputClientPassword_title = "Contraseña del Cliente Requerida"
  val ModalInputClientPassword_message =
    """
    Falló el inicio de sesión en el nodo servidor con la contraseña configurada.
    Para reconectarse a este nodo, por favor ingrese una nueva contraseña.
    """

  val ModalNewOwnerPassword_title = "Nueva Contraseña del Propietario"
  val ModalNewOwnerPassword_message =
    """
    Guarde esta contraseña en un lugar seguro. 
    La necesitará para abrir esta base de datos en otra computadora. 
    Puede generar una nueva contraseña desde el perfil del nodo en cualquier momento.
    """

  val ModalNewClientPassword_title = "Nueva Contraseña del Cliente"
  val ModalNewClientPassword_message =
    """
    Envíe esta contraseña al propietario del nodo usando un método seguro.
    """

  val ModalSelection_title = "Cotos Seleccionados"
  val ModalSelection_clear = "Limpiar Selección"

  val ModalNewIto_title = "Nuevo Ito"
  val ModalNewIto_reverse = "Invertir"
  val ModalNewIto_clearSelection = "Limpiar selección al conectar"
  val ModalNewIto_connect = "Conectar"

  val ModalSubcoto_title = "Nuevo Sub-coto"

  val ModalNodeProfile_title = "Perfil del Nodo"
  val ModalNodeProfile_selfNode = "Usted"
  val ModalNodeProfile_switched = "cambiado"
  val ModalNodeProfile_description = "Descripción"

  val FieldImageMaxSize = "Umbral de Redimensionamiento de Imagen (píxeles)"
  val FieldImageMaxSize_placeholder = "Sin redimensionamiento"

  val FieldOwnerPassword = "Contraseña del Propietario"

  val SelfNodeServer_title = "Servidor del Nodo"
  val SelfNodeServer_url = "URL"
  val SelfNodeServer_clientNodes = "Nodos Cliente"
  val SelfNodeServer_anonymousRead = "Aceptar Lectura Anónima"
  val SelfNodeServer_confirmEnableAnonymousRead =
    """
    ¿Está seguro de que desea permitir el acceso anónimo de solo lectura
    (cualquiera que conozca la URL de este nodo puede ver su contenido)?
    """
  val SelfNodeServer_anonymousConnections = "Conexiones activas"

  val AsServer_title = "Como Servidor"
  val AsServer_url = "URL"
  val AsServer_connection = "Conexión"

  val AsClient_title = "Como Cliente"
  val AsClient_resetPassword = "Restablecer Contraseña del Cliente"
  val AsClient_confirmResetPassword =
    """
    ¿Está seguro de que desea generar una nueva contraseña de cliente? 
    Hacerlo invalidará la contraseña actual.
    """
  val AsClient_lastLogin = "Último Inicio de Sesión"
  val AsClient_remoteAddress = "Dirección Remota"

  val AsChild_title = "Como Hijo"

  val ModalIncorporate_title = "Incorporar Nodo Remoto"
  val ModalIncorporate_nodeUrl = "URL del Nodo"
  val ModalIncorporate_incorporate = "Incorporar"

  val ModalPromote_title = "Promover a Cotonoma"
  val ModalPromote_confirm =
    """
    ¿Está seguro de que desea promover este coto a un cotonoma?
    Esta acción no se puede deshacer.
    """

  val ModalEditIto_disconnect = "Desconectar"
  val ModalEditIto_confirmDisconnect =
    "¿Está seguro de que desea eliminar este ito?"

  val ModalRepost_title = "Republicar"
  val ModalRepost_repostTo = "Republicar en"
  val ModalRepost_typeCotonomaName = "Escriba el nombre del cotonoma"
  val ModalRepost_newCotonoma = "Nuevo cotonoma"
  val ModalRepost_root = "raíz"
  val ModalRepost_alreadyPostedIn = "Ya publicado en"

  val ModalClients_title = "Nodos Cliente"
  val ModalClients_add = "Agregar Cliente"
  val ModalClients_connecting = "conectando"
  val ModalClients_nodes = "nodos"
  val ModalClients_noClients = "Aún no se han registrado nodos cliente."
  val ModalClients_column_name = "Nombre"
  val ModalClients_column_lastLogin = "Último Inicio de Sesión"
  val ModalClients_column_status = "Estado"
  val ModalClients_column_enabled = "Habilitado"

  val ModalNewClient_title = "Nuevo Cliente"
  val ModalNewClient_registered =
    """
    El nodo hijo a continuación ha sido registrado.
    Envíe la contraseña generada al propietario del nodo de manera segura.
    """

  val ModalSwitchNode_title = "Cambiar Nodo"
  val ModalSwitchNode_switch = "Cambiar"
  val ModalSwitchNode_message =
    """
    Está a punto de cambiar el nodo en el que operar como se muestra a continuación.
    """

  val ModalNodeIcon_title = "Cambiar Icono del Nodo"
  val ModalNodeIcon_inputImage = Fragment(
    "Arrastre y suelte un archivo de imagen aquí,",
    br(),
    "o haga clic para seleccionar uno"
  )
}
