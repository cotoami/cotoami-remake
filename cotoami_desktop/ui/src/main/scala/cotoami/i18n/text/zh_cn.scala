package cotoami.i18n.text

import slinky.core.facade.{Fragment, ReactElement}
import slinky.web.html._

import cotoami.i18n.Text

object zh_cn extends Text {
  val Coto = "事"
  val Cotonoma = "事间"
  val Ito = "线"
  val Pin = "固定"
  val Node = "节点"
  val Owner = "所有者"
  val Server = "服务器"
  val Client = "客户端"

  val Id = "ID"
  val Name = "名称"
  val Password = "密码"

  val OK = "确定"
  val Cancel = "取消"
  val Post = "发布"
  val Insert = "插入"
  val Save = "保存"
  val Edit = "编辑"
  val Preview = "预览"
  val Delete = "删除"
  val Repost = "转发"
  val Promote = "提升"
  val Traverse = "遍历"
  val Select = "选择"
  val Deselect = "取消选择"
  val Register = "注册"
  val Back = "返回"

  val DeleteCotonoma = "删除事间"
  val WriteSubcoto = "写子事"
  val OpenMap = "打开地图"
  val CloseMap = "关闭地图"
  val SwapPane = "交换面板"
  val LightMode = "浅色模式"
  val DarkMode = "深色模式"
  val MarkAllAsRead = "全部标记为已读"
  val PostTo = "发布到"

  def Coto_inRemoteNode(nodeName: String) = s"在 ${nodeName} (远程)"

  val Node_id = "节点ID"
  val Node_root = "节点根"
  val Node_notYetConnected = "尚未连接"
  val Node_settings = "节点设置"

  val Ito_description_placeholder = "线描述 (可选)"
  val Ito_editPin = "编辑固定"
  val Ito_editIto = "编辑线"

  val Owner_resetPassword = "重置所有者密码"
  val Owner_confirmResetPassword =
    """
    您确定要生成新的所有者密码吗？ 
    这样做将使当前密码失效。
    """

  val Connection_disabled = "未同步"
  val Connection_connecting = "连接中"
  val Connection_initFailed = "初始化失败"
  val Connection_authenticationFailed = "认证失败"
  val Connection_sessionExpired = "会话过期"
  val Connection_disconnected = "已断开"
  val Connection_connected = "已连接"

  val ChildPrivileges = "权限"
  val ChildPrivileges_asOwner = "所有者 (完全权限)"
  val ChildPrivileges_canPostCotos = "发布事"
  val ChildPrivileges_canEditItos = "编辑线"
  val ChildPrivileges_canPostCotonomas = "发布事间"
  val ChildPrivileges_readOnly = "只读"

  val ConfirmDeleteCoto = "您确定要删除这个事吗？"
  def ConfirmDeleteOthersCoto(someoneElse: ReactElement): ReactElement =
    span(className := "delete-others-coto")(
      "作为所有者，您即将删除由以下用户发布的事：",
      someoneElse
    )
  val ConfirmDeleteCotonoma = "您确定要删除这个事间吗？"

  val NavNodes_allNodes = "所有节点"
  val NavNodes_addNode = "添加节点"

  val NavCotonomas_current = "当前"
  val NavCotonomas_recent = "最近"

  val PaneStock_map_dockLeft = "停靠左侧"
  val PaneStock_map_dockTop = "停靠顶部"

  val SectionPins_layout_document = "文档"
  val SectionPins_layout_columns = "列"
  val SectionPins_layout_masonry = "瀑布流"

  val SectionNodeTools_enableSync = "启用同步"
  val SectionNodeTools_disableSync = "禁用同步"

  val EditorCoto_placeholder_coto = "用 Markdown 写您的事"
  val EditorCoto_placeholder_summary = "摘要 (可选)"
  val EditorCoto_placeholder_newCotonomaName = "新事间名称"
  val EditorCoto_placeholder_cotonomaName = "事间名称"
  val EditorCoto_placeholder_cotonomaContent =
    "用 Markdown 写事间描述"
  val EditorCoto_inputImage = "将图片文件拖到这里，或点击选择一个"
  val EditorCoto_date = "日期"
  val EditorCoto_location = "位置"
  val EditorCoto_help_selectLocation = "点击地图上的位置"
  def EditorCoto_cotonomaAlreadyExists(cotonomaName: String) =
    s"事间 \"${cotonomaName}\" 在此节点中已存在。"

  val ModalConfirm_title = "确认"

  val ModalWelcome_title = "欢迎使用 Cotoami"
  val ModalWelcome_recent = "最近"
  val ModalWelcome_new = "新数据库"
  val ModalWelcome_new_name = "名称"
  val ModalWelcome_new_baseFolder = "基础文件夹"
  val ModalWelcome_new_selectBaseFolder = "选择基础文件夹"
  val ModalWelcome_new_folderName = "要创建的文件夹名称"
  val ModalWelcome_new_create = "创建"
  val ModalWelcome_open = "打开"
  val ModalWelcome_open_folder = "数据库文件夹"
  val ModalWelcome_open_selectFolder = "选择数据库文件夹"
  val ModalWelcome_open_open = "打开"
  def ModalWelcome_update_message(newVersion: String) =
    span()(
      "Cotoami Desktop 的新版本 ",
      span(className := "version")(newVersion),
      " 可用。"
    )
  val ModalWelcome_update_updateNow = "立即更新"

  val ModalAppUpdate_title = "更新应用程序"
  def ModalAppUpdate_message(
      newVersion: String,
      currentVersion: String
  ) = span()(
    "正在下载并安装版本 ",
    span(className := "version")(newVersion),
    " (当前: ",
    span(className := "version")(currentVersion),
    ")"
  )
  val ModalAppUpdate_restart = "重启应用"

  val ModalInputOwnerPassword_title = "需要所有者密码"
  val ModalInputOwnerPassword_message =
    "您需要输入所有者密码来打开此数据库。"

  val ModalInputClientPassword_title = "需要客户端密码"
  val ModalInputClientPassword_message =
    """
    使用配置的密码登录服务器节点失败。
    要重新连接到此节点，请输入新密码。
    """

  val ModalNewOwnerPassword_title = "新所有者密码"
  val ModalNewOwnerPassword_message =
    """
    请将此密码保存在安全的地方。 
    您需要它在另一台计算机上打开此数据库。 
    您可以随时从节点配置文件生成新密码。
    """

  val ModalNewClientPassword_title = "新客户端密码"
  val ModalNewClientPassword_message =
    """
    请使用安全方法将此密码发送给节点所有者。
    """

  val ModalSelection_title = "已选择的事"
  val ModalSelection_clear = "清除选择"

  val ModalNewIto_title = "新线"
  val ModalNewIto_reverse = "反转"
  val ModalNewIto_clearSelection = "连接时清除选择"
  val ModalNewIto_connect = "连接"

  val ModalSubcoto_title = "新子事"

  val ModalNodeProfile_title = "节点配置文件"
  val ModalNodeProfile_selfNode = "您"
  val ModalNodeProfile_switched = "已切换"
  val ModalNodeProfile_description = "描述"

  val FieldImageMaxSize = "图片调整大小阈值 (像素)"
  val FieldImageMaxSize_placeholder = "不调整大小"

  val FieldOwnerPassword = "所有者密码"

  val SelfNodeServer_title = "节点服务器"
  val SelfNodeServer_url = "URL"
  val SelfNodeServer_clientNodes = "客户端节点"
  val SelfNodeServer_anonymousRead = "接受匿名读取"
  val SelfNodeServer_confirmEnableAnonymousRead =
    """
    您确定要允许匿名只读访问吗
    (任何知道此节点URL的人都可以查看您的内容)？
    """
  val SelfNodeServer_anonymousConnections = "活动连接"

  val AsServer_title = "作为服务器"
  val AsServer_url = "URL"
  val AsServer_connection = "连接"

  val AsClient_title = "作为客户端"
  val AsClient_resetPassword = "重置客户端密码"
  val AsClient_confirmResetPassword =
    """
    您确定要生成新的客户端密码吗？ 
    这样做将使当前密码失效。
    """
  val AsClient_lastLogin = "最后登录"
  val AsClient_remoteAddress = "远程地址"

  val AsChild_title = "作为子节点"

  val ModalIncorporate_title = "合并远程节点"
  val ModalIncorporate_nodeUrl = "节点URL"
  val ModalIncorporate_incorporate = "合并"

  val ModalPromote_title = "提升为事间"
  val ModalPromote_confirm =
    """
    您确定要将此事提升为事间吗？
    此操作无法撤销。
    """

  val ModalEditIto_disconnect = "断开"
  val ModalEditIto_confirmDisconnect =
    "您确定要删除此线吗？"

  val ModalRepost_title = "转发"
  val ModalRepost_repostTo = "转发到"
  val ModalRepost_typeCotonomaName = "输入事间名称"
  val ModalRepost_newCotonoma = "新事间"
  val ModalRepost_root = "根"
  val ModalRepost_alreadyPostedIn = "已发布在"

  val ModalClients_title = "客户端节点"
  val ModalClients_add = "添加客户端"
  val ModalClients_connecting = "连接中"
  val ModalClients_nodes = "节点"
  val ModalClients_noClients = "尚未注册客户端节点。"
  val ModalClients_column_name = "名称"
  val ModalClients_column_lastLogin = "最后登录"
  val ModalClients_column_status = "状态"
  val ModalClients_column_enabled = "已启用"

  val ModalNewClient_title = "新客户端"
  val ModalNewClient_registered =
    """
    下面的子节点已注册。
    请以安全方式将生成的密码发送给节点所有者。
    """

  val ModalSwitchNode_title = "切换节点"
  val ModalSwitchNode_switch = "切换"
  val ModalSwitchNode_message =
    """
    您即将切换要操作的节点，如下所示。
    """

  val ModalNodeIcon_title = "更改节点图标"
  val ModalNodeIcon_inputImage = Fragment(
    "将图片文件拖放到这里，",
    br(),
    "或点击选择一个"
  )
}
