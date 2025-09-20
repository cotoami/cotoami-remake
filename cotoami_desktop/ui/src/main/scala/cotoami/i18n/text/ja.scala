package cotoami.i18n.text

import slinky.core.facade.{Fragment, ReactElement}
import slinky.web.html._

import cotoami.i18n.Text

object ja extends Text {
  val Coto = "コト"
  val Cotonoma = "コトノマ"
  val Ito = "イト"
  val Pin = "ピン"
  val Node = "ノード"
  val Owner = "オーナー"
  val Server = "サーバー"
  val Client = "クライアント"

  val Id = "ID"
  val Name = "名前"
  val Password = "パスワード"

  val OK = "OK"
  val Cancel = "キャンセル"
  val Post = "投稿"
  val Insert = "挿入"
  val Save = "保存"
  val Edit = "編集"
  val Preview = "プレビュー"
  val Delete = "削除"
  val Repost = "リポスト"
  val Promote = "コトノマ化"
  val Traverse = "探索"
  val Select = "選択"
  val Deselect = "選択解除"
  val Register = "登録"
  val Back = "戻る"

  val DeleteCotonoma = "コトノマ削除"
  val WriteSubcoto = "子コト"
  val OpenMap = "地図を開く"
  val CloseMap = "地図を閉じる"
  val SwapPane = "左右入れ替え"
  val LightMode = "ライトモード"
  val DarkMode = "ダークモード"
  val MarkAllAsRead = "すべて既読"
  val PostTo = "投稿先"

  def Coto_inRemoteNode(nodeName: String) = s"In ${nodeName} (リモート)"

  val Node_id = "ノードID"
  val Node_root = "ノードコトノマ"
  val Node_notYetConnected = "未接続"
  val Node_settings = "ノード設定"

  val Ito_description_placeholder = "イトの説明 (省略可)"
  val Ito_editPin = "ピンを編集"
  val Ito_editIto = "イトを編集"

  val Owner_resetPassword = "オーナーパスワードの更新"
  val Owner_confirmResetPassword =
    """
    既存のパスワードを無効にして、
    新しいオーナーパスワードを生成してもよろしいですか？
    """

  val Connection_disabled = "連携OFF"
  val Connection_connecting = "接続中"
  val Connection_initFailed = "接続失敗"
  val Connection_authenticationFailed = "認証失敗"
  val Connection_sessionExpired = "セッション期限切れ"
  val Connection_disconnected = "接続断"
  val Connection_connected = "接続中"

  val ChildPrivileges = "権限"
  val ChildPrivileges_asOwner = "オーナー (フル権限)"
  val ChildPrivileges_canPostCotos = "コトの投稿"
  val ChildPrivileges_canEditItos = "イトの編集"
  val ChildPrivileges_canPostCotonomas = "コトノマの投稿"
  val ChildPrivileges_readOnly = "閲覧のみ"

  val ConfirmDeleteCoto = "このコトを削除してもよろしいですか？"
  def ConfirmDeleteOthersCoto(someoneElse: ReactElement): ReactElement =
    span(className := "delete-others-coto")(
      someoneElse,
      " さんの投稿をオーナーとして削除してもよろしいですか？"
    )
  val ConfirmDeleteCotonoma = "このコトノマを削除してもよろしいですか？"

  val NavNodes_allNodes = "すべてのノード"
  val NavNodes_addNode = "ノードの追加"

  val NavCotonomas_current = "現在地"
  val NavCotonomas_recent = "最近の更新"

  val PaneStock_map_dockLeft = "左側に配置"
  val PaneStock_map_dockTop = "上側に配置"

  val SectionPins_layout_document = "ドキュメント"
  val SectionPins_layout_columns = "カラム"
  val SectionPins_layout_masonry = "メイソンリー"

  val SectionNodeTools_enableSync = "連携ON"
  val SectionNodeTools_disableSync = "連携OFF"

  val EditorCoto_placeholder_coto = "マークダウンで書く"
  val EditorCoto_placeholder_summary = "タイトル (省略可)"
  val EditorCoto_placeholder_newCotonomaName = "新規コトノマ名"
  val EditorCoto_placeholder_cotonomaName = "コトノマ名"
  val EditorCoto_placeholder_cotonomaContent = "コトノマの説明（マークダウン）"
  val EditorCoto_inputImage = "画像ファイルをドロップ、あるいはここをクリックして選択"
  val EditorCoto_date = "日時"
  val EditorCoto_location = "場所"
  val EditorCoto_help_selectLocation = "地図上の場所をクリックして選択"
  def EditorCoto_cotonomaAlreadyExists(cotonomaName: String) =
    s"コトノマ \"${cotonomaName}\" は既に登録済みです。"

  val ModalConfirm_title = "確認"

  val ModalWelcome_title = "Cotoami にようこそ"
  val ModalWelcome_recent = "最近"
  val ModalWelcome_new = "新規データベース"
  val ModalWelcome_new_name = "名前"
  val ModalWelcome_new_baseFolder = "保存先フォルダ"
  val ModalWelcome_new_selectBaseFolder = "保存先フォルダの選択"
  val ModalWelcome_new_folderName = "作成するフォルダの名前"
  val ModalWelcome_new_create = "作成"
  val ModalWelcome_open = "フォルダを選択して開く"
  val ModalWelcome_open_folder = "データベースフォルダ"
  val ModalWelcome_open_selectFolder = "データベースフォルダの選択"
  val ModalWelcome_open_open = "開く"
  def ModalWelcome_update_message(newVersion: String) =
    span()(
      "新バージョン ",
      span(className := "version")(newVersion),
      " が利用可能です。"
    )
  val ModalWelcome_update_updateNow = "アップデート"

  val ModalAppUpdate_title = "アプリケーションの更新"
  def ModalAppUpdate_message(
      newVersion: String,
      currentVersion: String
  ) = span()(
    "新バージョン ",
    span(className := "version")(newVersion),
    " をインストール中",
    " (現在のバージョン: ",
    span(className := "version")(currentVersion),
    ")"
  )
  val ModalAppUpdate_restart = "再起動"

  val ModalInputOwnerPassword_title = "オーナーパスワードの入力"
  val ModalInputOwnerPassword_message =
    "このデータベースを開くためにはオーナーパスワードを入力する必要があります。"

  val ModalInputClientPassword_title = "クライアントパスワードの入力"
  val ModalInputClientPassword_message =
    """
    設定済みのパスワードではログインできませんでした。
    再接続するためには、新しいクライアントパスワードを入力する必要があります。
    """

  val ModalNewOwnerPassword_title = "新しいオーナーパスワード"
  val ModalNewOwnerPassword_message =
    """
    このパスワードは、データベースファイルを他の環境で開く際に必要になります。
    念の為、安全な場所に保存して下さい。
    """

  val ModalNewClientPassword_title = "新しいクライアントパスワード"
  val ModalNewClientPassword_message =
    """
    このパスワードをクライアントノードのオーナーに共有してください。
    """

  val ModalSelection_title = "選択中のコト"
  val ModalSelection_clear = "全ての選択を解除"

  val ModalNewIto_title = "新規イト"
  val ModalNewIto_reverse = "反転"
  val ModalNewIto_clearSelection = "接続時にコトの選択を解除"
  val ModalNewIto_connect = "接続"

  val ModalSubcoto_title = "子コトの追加"

  val ModalNodeProfile_title = "ノード情報"
  val ModalNodeProfile_selfNode = "あなた"
  val ModalNodeProfile_switched = "切替中"
  val ModalNodeProfile_description = "説明"

  val FieldImageMaxSize = "画像リサイズの閾値（ピクセル）"
  val FieldImageMaxSize_placeholder = "リサイズなし"

  val FieldOwnerPassword = "オーナーパスワード"

  val SelfNodeServer_title = "サーバー設定"
  val SelfNodeServer_url = "URL"
  val SelfNodeServer_clientNodes = "クライアント"
  val SelfNodeServer_anonymousRead = "匿名接続の受付"
  val SelfNodeServer_confirmEnableAnonymousRead =
    """
    匿名の閲覧専用接続を許可しますか？
    (このノードのURLを知っている人は誰でもデータベースの内容を閲覧可能になります)
    """
  val SelfNodeServer_anonymousConnections = "接続中"

  val AsServer_title = "役割: サーバー"
  val AsServer_url = "URL"
  val AsServer_connection = "接続"

  val AsClient_title = "役割: クライアント"
  val AsClient_resetPassword = "クライアントパスワードの更新"
  val AsClient_confirmResetPassword =
    """
    既存のパスワードを無効にして、
    新しいクライアントパスワードを生成してもよろしいですか？
    """
  val AsClient_lastLogin = "最終ログイン"
  val AsClient_remoteAddress = "IPアドレス"

  val AsChild_title = "役割: 子ノード"

  val ModalIncorporate_title = "リモートノードの追加"
  val ModalIncorporate_nodeUrl = "ノードURL"
  val ModalIncorporate_incorporate = "取り込む"

  val ModalPromote_title = "コトをコトノマに変換"
  val ModalPromote_confirm =
    """
    このコトをコトノマに変換してもよろしいですか？
    この操作は取り消せません。
    """

  val ModalEditIto_disconnect = "切断"
  val ModalEditIto_confirmDisconnect = "このイトを切断してもよろしいですか？"

  val ModalRepost_title = "リポスト"
  val ModalRepost_repostTo = "リポスト先"
  val ModalRepost_typeCotonomaName = "コトノマ名を入力"
  val ModalRepost_newCotonoma = "新規コトノマ"
  val ModalRepost_root = "ノードルート"
  val ModalRepost_alreadyPostedIn = "投稿済みのコトノマ"

  val ModalClients_title = "クライアント"
  val ModalClients_add = "クライアント登録"
  val ModalClients_connecting = "接続中"
  val ModalClients_nodes = "ノード"
  val ModalClients_noClients = "クライアントの登録がありません。"
  val ModalClients_column_name = "名前"
  val ModalClients_column_lastLogin = "最終ログイン"
  val ModalClients_column_status = "ステータス"
  val ModalClients_column_enabled = "有効"

  val ModalNewClient_title = "新規クライアント"
  val ModalNewClient_registered =
    """
    新しいクライアントノードが登録されました。
    以下のパスワードをノードのオーナーに共有してください。
    """

  val ModalSwitchNode_title = "操作ノード切替"
  val ModalSwitchNode_switch = "切替"
  val ModalSwitchNode_message =
    """
    操作ノードを以下のように切り替えます。
    """

  val ModalNodeIcon_title = "ノードアイコンの変更"
  val ModalNodeIcon_inputImage = Fragment(
    "画像ファイルをドロップ、",
    br(),
    "あるいはここをクリックして選択"
  )
}
