package cotoami.i18n.text

import slinky.core.facade.{Fragment, ReactElement}
import slinky.web.html._

import cotoami.i18n.Text

object zh_tw extends Text {
  val Coto = "事"
  val Cotonoma = "事間"
  val Ito = "線"
  val Pin = "固定"
  val Node = "節點"
  val Owner = "擁有者"
  val Server = "伺服器"
  val Client = "客戶端"

  val Id = "ID"
  val Name = "名稱"
  val Password = "密碼"

  val OK = "確定"
  val Cancel = "取消"
  val Post = "發布"
  val Insert = "插入"
  val Save = "儲存"
  val Edit = "編輯"
  val Preview = "預覽"
  val Delete = "刪除"
  val Repost = "轉發"
  val Promote = "提升"
  val Traverse = "遍歷"
  val Select = "選擇"
  val Deselect = "取消選擇"
  val Register = "註冊"
  val Back = "返回"

  val DeleteCotonoma = "刪除事間"
  val WriteSubcoto = "寫子事"
  val OpenMap = "開啟地圖"
  val CloseMap = "關閉地圖"
  val SwapPane = "交換面板"
  val LightMode = "淺色模式"
  val DarkMode = "深色模式"
  val MarkAllAsRead = "全部標記為已讀"
  val PostTo = "發布到"

  def Coto_inRemoteNode(nodeName: String) = s"在 ${nodeName} (遠端)"

  val Node_id = "節點ID"
  val Node_root = "節點根"
  val Node_notYetConnected = "尚未連接"
  val Node_settings = "節點設定"

  val Ito_description_placeholder = "線描述 (可選)"
  val Ito_editPin = "編輯固定"
  val Ito_editIto = "編輯線"

  val Owner_resetPassword = "重設擁有者密碼"
  val Owner_confirmResetPassword =
    """
    您確定要產生新的擁有者密碼嗎？ 
    這樣做將使目前密碼失效。
    """

  val Connection_disabled = "未同步"
  val Connection_connecting = "連接中"
  val Connection_initFailed = "初始化失敗"
  val Connection_authenticationFailed = "認證失敗"
  val Connection_sessionExpired = "會話過期"
  val Connection_disconnected = "已斷開"
  val Connection_connected = "已連接"

  val ChildPrivileges = "權限"
  val ChildPrivileges_asOwner = "擁有者 (完全權限)"
  val ChildPrivileges_canPostCotos = "發布事"
  val ChildPrivileges_canEditItos = "編輯線"
  val ChildPrivileges_canPostCotonomas = "發布事間"
  val ChildPrivileges_readOnly = "唯讀"

  val ConfirmDeleteCoto = "您確定要刪除這個事嗎？"
  def ConfirmDeleteOthersCoto(someoneElse: ReactElement): ReactElement =
    span(className := "delete-others-coto")(
      "作為擁有者，您即將刪除由以下使用者發布的事：",
      someoneElse
    )
  val ConfirmDeleteCotonoma = "您確定要刪除這個事間嗎？"

  val NavNodes_allNodes = "所有節點"
  val NavNodes_addNode = "新增節點"

  val NavCotonomas_current = "目前"
  val NavCotonomas_recent = "最近"

  val PaneStock_map_dockLeft = "停靠左側"
  val PaneStock_map_dockTop = "停靠頂部"

  val SectionPins_layout_document = "文件"
  val SectionPins_layout_columns = "欄"
  val SectionPins_layout_masonry = "瀑布流"

  val SectionNodeTools_enableSync = "啟用同步"
  val SectionNodeTools_disableSync = "停用同步"

  val EditorCoto_placeholder_coto = "用 Markdown 寫您的事"
  val EditorCoto_placeholder_summary = "摘要 (可選)"
  val EditorCoto_placeholder_newCotonomaName = "新事間名稱"
  val EditorCoto_placeholder_cotonomaName = "事間名稱"
  val EditorCoto_placeholder_cotonomaContent =
    "用 Markdown 寫事間描述"
  val EditorCoto_inputImage = "將圖片檔案拖到這裡，或點擊選擇一個"
  val EditorCoto_date = "日期"
  val EditorCoto_location = "位置"
  val EditorCoto_help_selectLocation = "點擊地圖上的位置"
  def EditorCoto_cotonomaAlreadyExists(cotonomaName: String) =
    s"事間 \"${cotonomaName}\" 在此節點中已存在。"

  val ModalConfirm_title = "確認"

  val ModalWelcome_title = "歡迎使用 Cotoami"
  val ModalWelcome_recent = "最近"
  val ModalWelcome_new = "新資料庫"
  val ModalWelcome_new_name = "名稱"
  val ModalWelcome_new_baseFolder = "基礎資料夾"
  val ModalWelcome_new_selectBaseFolder = "選擇基礎資料夾"
  val ModalWelcome_new_folderName = "要建立的資料夾名稱"
  val ModalWelcome_new_create = "建立"
  val ModalWelcome_open = "開啟"
  val ModalWelcome_open_folder = "資料庫資料夾"
  val ModalWelcome_open_selectFolder = "選擇資料庫資料夾"
  val ModalWelcome_open_open = "開啟"
  def ModalWelcome_update_message(newVersion: String) =
    span()(
      "Cotoami Desktop 的新版本 ",
      span(className := "version")(newVersion),
      " 可用。"
    )
  val ModalWelcome_update_updateNow = "立即更新"

  val ModalAppUpdate_title = "更新應用程式"
  def ModalAppUpdate_message(
      newVersion: String,
      currentVersion: String
  ) = span()(
    "正在下載並安裝版本 ",
    span(className := "version")(newVersion),
    " (目前: ",
    span(className := "version")(currentVersion),
    ")"
  )
  val ModalAppUpdate_restart = "重新啟動應用程式"

  val ModalInputOwnerPassword_title = "需要擁有者密碼"
  val ModalInputOwnerPassword_message =
    "您需要輸入擁有者密碼來開啟此資料庫。"

  val ModalInputClientPassword_title = "需要客戶端密碼"
  val ModalInputClientPassword_message =
    """
    使用設定的密碼登入伺服器節點失敗。
    要重新連接到此節點，請輸入新密碼。
    """

  val ModalNewOwnerPassword_title = "新擁有者密碼"
  val ModalNewOwnerPassword_message =
    """
    請將此密碼儲存在安全的地方。 
    您需要它在另一台電腦上開啟此資料庫。 
    您可以隨時從節點設定檔產生新密碼。
    """

  val ModalNewClientPassword_title = "新客戶端密碼"
  val ModalNewClientPassword_message =
    """
    請使用安全方法將此密碼傳送給節點擁有者。
    """

  val ModalSelection_title = "已選擇的事"
  val ModalSelection_clear = "清除選擇"

  val ModalNewIto_title = "新線"
  val ModalNewIto_reverse = "反轉"
  val ModalNewIto_clearSelection = "連接時清除選擇"
  val ModalNewIto_connect = "連接"

  val ModalSubcoto_title = "新子事"

  val ModalNodeProfile_title = "節點設定檔"
  val ModalNodeProfile_selfNode = "您"
  val ModalNodeProfile_switched = "已切換"
  val ModalNodeProfile_description = "描述"

  val FieldImageMaxSize = "圖片調整大小閾值 (像素)"
  val FieldImageMaxSize_placeholder = "不調整大小"

  val FieldOwnerPassword = "擁有者密碼"

  val SelfNodeServer_title = "節點伺服器"
  val SelfNodeServer_url = "URL"
  val SelfNodeServer_clientNodes = "客戶端節點"
  val SelfNodeServer_anonymousRead = "接受匿名讀取"
  val SelfNodeServer_confirmEnableAnonymousRead =
    """
    您確定要允許匿名唯讀存取嗎
    (任何知道此節點URL的人都可以檢視您的內容)？
    """
  val SelfNodeServer_anonymousConnections = "活動連接"

  val AsServer_title = "作為伺服器"
  val AsServer_url = "URL"
  val AsServer_connection = "連接"

  val AsClient_title = "作為客戶端"
  val AsClient_resetPassword = "重設客戶端密碼"
  val AsClient_confirmResetPassword =
    """
    您確定要產生新的客戶端密碼嗎？ 
    這樣做將使目前密碼失效。
    """
  val AsClient_lastLogin = "最後登入"
  val AsClient_remoteAddress = "遠端位址"

  val AsChild_title = "作為子節點"

  val ModalIncorporate_title = "合併遠端節點"
  val ModalIncorporate_nodeUrl = "節點URL"
  val ModalIncorporate_incorporate = "合併"

  val ModalPromote_title = "提升為事間"
  val ModalPromote_confirm =
    """
    您確定要將此事提升為事間嗎？
    此操作無法復原。
    """

  val ModalEditIto_disconnect = "斷開"
  val ModalEditIto_confirmDisconnect =
    "您確定要刪除此線嗎？"

  val ModalRepost_title = "轉發"
  val ModalRepost_repostTo = "轉發到"
  val ModalRepost_typeCotonomaName = "輸入事間名稱"
  val ModalRepost_newCotonoma = "新事間"
  val ModalRepost_root = "根"
  val ModalRepost_alreadyPostedIn = "已發布在"

  val ModalClients_title = "客戶端節點"
  val ModalClients_add = "新增客戶端"
  val ModalClients_connecting = "連接中"
  val ModalClients_nodes = "節點"
  val ModalClients_noClients = "尚未註冊客戶端節點。"
  val ModalClients_column_name = "名稱"
  val ModalClients_column_lastLogin = "最後登入"
  val ModalClients_column_status = "狀態"
  val ModalClients_column_enabled = "已啟用"

  val ModalNewClient_title = "新客戶端"
  val ModalNewClient_registered =
    """
    下面的子節點已註冊。
    請以安全方式將產生的密碼傳送給節點擁有者。
    """

  val ModalSwitchNode_title = "切換節點"
  val ModalSwitchNode_switch = "切換"
  val ModalSwitchNode_message =
    """
    您即將切換要操作的節點，如下所示。
    """

  val ModalNodeIcon_title = "更改節點圖示"
  val ModalNodeIcon_inputImage = Fragment(
    "將圖片檔案拖放到這裡，",
    br(),
    "或點擊選擇一個"
  )
}
