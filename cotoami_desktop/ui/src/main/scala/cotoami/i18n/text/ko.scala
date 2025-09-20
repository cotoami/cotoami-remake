package cotoami.i18n.text

import slinky.core.facade.{Fragment, ReactElement}
import slinky.web.html._

import cotoami.i18n.Text

object ko extends Text {
  val Coto = "Coto"
  val Cotonoma = "Cotonoma"
  val Ito = "Ito"
  val Pin = "핀"
  val Node = "노드"
  val Owner = "소유자"
  val Server = "서버"
  val Client = "클라이언트"

  val Id = "ID"
  val Name = "이름"
  val Password = "비밀번호"

  val OK = "확인"
  val Cancel = "취소"
  val Post = "게시"
  val Insert = "삽입"
  val Save = "저장"
  val Edit = "편집"
  val Preview = "미리보기"
  val Delete = "삭제"
  val Repost = "재게시"
  val Promote = "승격"
  val Traverse = "탐색"
  val Select = "선택"
  val Deselect = "선택 해제"
  val Register = "등록"
  val Back = "뒤로"

  val DeleteCotonoma = "Cotonoma 삭제"
  val WriteSubcoto = "하위 Coto 작성"
  val OpenMap = "지도 열기"
  val CloseMap = "지도 닫기"
  val SwapPane = "패널 교체"
  val LightMode = "라이트 모드"
  val DarkMode = "다크 모드"
  val MarkAllAsRead = "모두 읽음으로 표시"
  val PostTo = "게시 위치"

  def Coto_inRemoteNode(nodeName: String) = s"${nodeName}에서 (원격)"

  val Node_id = "노드 ID"
  val Node_root = "노드 루트"
  val Node_notYetConnected = "아직 연결되지 않음"
  val Node_settings = "노드 설정"

  val Ito_description_placeholder = "Ito 설명 (선택사항)"
  val Ito_editPin = "핀 편집"
  val Ito_editIto = "Ito 편집"

  val Owner_resetPassword = "소유자 비밀번호 재설정"
  val Owner_confirmResetPassword =
    """
    새로운 소유자 비밀번호를 생성하시겠습니까? 
    이렇게 하면 현재 비밀번호가 무효화됩니다.
    """

  val Connection_disabled = "동기화되지 않음"
  val Connection_connecting = "연결 중"
  val Connection_initFailed = "초기화 실패"
  val Connection_authenticationFailed = "인증 실패"
  val Connection_sessionExpired = "세션 만료"
  val Connection_disconnected = "연결 끊김"
  val Connection_connected = "연결됨"

  val ChildPrivileges = "권한"
  val ChildPrivileges_asOwner = "소유자 (전체 권한)"
  val ChildPrivileges_canPostCotos = "Coto 게시"
  val ChildPrivileges_canEditItos = "Ito 편집"
  val ChildPrivileges_canPostCotonomas = "Cotonoma 게시"
  val ChildPrivileges_readOnly = "읽기 전용"

  val ConfirmDeleteCoto = "이 Coto를 삭제하시겠습니까?"
  def ConfirmDeleteOthersCoto(someoneElse: ReactElement): ReactElement =
    span(className := "delete-others-coto")(
      "소유자로서 다음 사용자가 게시한 Coto를 삭제하려고 합니다:",
      someoneElse
    )
  val ConfirmDeleteCotonoma = "이 Cotonoma를 삭제하시겠습니까?"

  val NavNodes_allNodes = "모든 노드"
  val NavNodes_addNode = "노드 추가"

  val NavCotonomas_current = "현재"
  val NavCotonomas_recent = "최근"

  val PaneStock_map_dockLeft = "왼쪽에 도킹"
  val PaneStock_map_dockTop = "위쪽에 도킹"

  val SectionPins_layout_document = "문서"
  val SectionPins_layout_columns = "열"
  val SectionPins_layout_masonry = "벽돌식"

  val SectionNodeTools_enableSync = "동기화 활성화"
  val SectionNodeTools_disableSync = "동기화 비활성화"

  val EditorCoto_placeholder_coto = "Markdown으로 Coto를 작성하세요"
  val EditorCoto_placeholder_summary = "요약 (선택사항)"
  val EditorCoto_placeholder_newCotonomaName = "새 Cotonoma 이름"
  val EditorCoto_placeholder_cotonomaName = "Cotonoma 이름"
  val EditorCoto_placeholder_cotonomaContent =
    "Markdown으로 Cotonoma 설명을 작성하세요"
  val EditorCoto_inputImage = "여기에 이미지 파일을 드롭하거나 클릭하여 선택하세요"
  val EditorCoto_date = "날짜"
  val EditorCoto_location = "위치"
  val EditorCoto_help_selectLocation = "지도에서 위치를 클릭하세요"
  def EditorCoto_cotonomaAlreadyExists(cotonomaName: String) =
    s"Cotonoma \"${cotonomaName}\"이(가) 이 노드에 이미 존재합니다."

  val ModalConfirm_title = "확인"

  val ModalWelcome_title = "Cotoami에 오신 것을 환영합니다"
  val ModalWelcome_recent = "최근"
  val ModalWelcome_new = "새 데이터베이스"
  val ModalWelcome_new_name = "이름"
  val ModalWelcome_new_baseFolder = "기본 폴더"
  val ModalWelcome_new_selectBaseFolder = "기본 폴더 선택"
  val ModalWelcome_new_folderName = "생성할 폴더 이름"
  val ModalWelcome_new_create = "생성"
  val ModalWelcome_open = "열기"
  val ModalWelcome_open_folder = "데이터베이스 폴더"
  val ModalWelcome_open_selectFolder = "데이터베이스 폴더 선택"
  val ModalWelcome_open_open = "열기"
  def ModalWelcome_update_message(newVersion: String) =
    span()(
      "Cotoami Desktop의 새 버전 ",
      span(className := "version")(newVersion),
      "을(를) 사용할 수 있습니다."
    )
  val ModalWelcome_update_updateNow = "지금 업데이트"

  val ModalAppUpdate_title = "애플리케이션 업데이트"
  def ModalAppUpdate_message(
      newVersion: String,
      currentVersion: String
  ) = span()(
    "버전 ",
    span(className := "version")(newVersion),
    " 다운로드 및 설치 중 (현재: ",
    span(className := "version")(currentVersion),
    ")"
  )
  val ModalAppUpdate_restart = "앱 재시작"

  val ModalInputOwnerPassword_title = "소유자 비밀번호 필요"
  val ModalInputOwnerPassword_message =
    "이 데이터베이스를 열려면 소유자 비밀번호를 입력해야 합니다."

  val ModalInputClientPassword_title = "클라이언트 비밀번호 필요"
  val ModalInputClientPassword_message =
    """
    구성된 비밀번호로 서버 노드에 로그인하지 못했습니다.
    이 노드에 다시 연결하려면 새 비밀번호를 입력하세요.
    """

  val ModalNewOwnerPassword_title = "새 소유자 비밀번호"
  val ModalNewOwnerPassword_message =
    """
    이 비밀번호를 안전한 곳에 저장하세요. 
    다른 컴퓨터에서 이 데이터베이스를 열 때 필요합니다. 
    언제든지 노드 프로필에서 새 비밀번호를 생성할 수 있습니다.
    """

  val ModalNewClientPassword_title = "새 클라이언트 비밀번호"
  val ModalNewClientPassword_message =
    """
    안전한 방법을 사용하여 이 비밀번호를 노드 소유자에게 보내세요.
    """

  val ModalSelection_title = "선택된 Coto"
  val ModalSelection_clear = "선택 지우기"

  val ModalNewIto_title = "새 Ito"
  val ModalNewIto_reverse = "역순"
  val ModalNewIto_clearSelection = "연결 시 선택 지우기"
  val ModalNewIto_connect = "연결"

  val ModalSubcoto_title = "새 하위 Coto"

  val ModalNodeProfile_title = "노드 프로필"
  val ModalNodeProfile_selfNode = "당신"
  val ModalNodeProfile_switched = "전환됨"
  val ModalNodeProfile_description = "설명"

  val FieldImageMaxSize = "이미지 크기 조정 임계값 (픽셀)"
  val FieldImageMaxSize_placeholder = "크기 조정 없음"

  val FieldOwnerPassword = "소유자 비밀번호"

  val SelfNodeServer_title = "노드 서버"
  val SelfNodeServer_url = "URL"
  val SelfNodeServer_clientNodes = "클라이언트 노드"
  val SelfNodeServer_anonymousRead = "익명 읽기 허용"
  val SelfNodeServer_confirmEnableAnonymousRead =
    """
    익명 읽기 전용 액세스를 허용하시겠습니까
    (이 노드의 URL을 아는 사람은 누구나 콘텐츠를 볼 수 있습니다)?
    """
  val SelfNodeServer_anonymousConnections = "활성 연결"

  val AsServer_title = "서버로서"
  val AsServer_url = "URL"
  val AsServer_connection = "연결"

  val AsClient_title = "클라이언트로서"
  val AsClient_resetPassword = "클라이언트 비밀번호 재설정"
  val AsClient_confirmResetPassword =
    """
    새로운 클라이언트 비밀번호를 생성하시겠습니까? 
    이렇게 하면 현재 비밀번호가 무효화됩니다.
    """
  val AsClient_lastLogin = "마지막 로그인"
  val AsClient_remoteAddress = "원격 주소"

  val AsChild_title = "자식으로서"

  val ModalIncorporate_title = "원격 노드 통합"
  val ModalIncorporate_nodeUrl = "노드 URL"
  val ModalIncorporate_incorporate = "통합"

  val ModalPromote_title = "Cotonoma로 승격"
  val ModalPromote_confirm =
    """
    이 Coto를 Cotonoma로 승격하시겠습니까?
    이 작업은 취소할 수 없습니다.
    """

  val ModalEditIto_disconnect = "연결 해제"
  val ModalEditIto_confirmDisconnect =
    "이 Ito를 삭제하시겠습니까?"

  val ModalRepost_title = "재게시"
  val ModalRepost_repostTo = "재게시 위치"
  val ModalRepost_typeCotonomaName = "Cotonoma 이름 입력"
  val ModalRepost_newCotonoma = "새 Cotonoma"
  val ModalRepost_root = "루트"
  val ModalRepost_alreadyPostedIn = "이미 게시됨"

  val ModalClients_title = "클라이언트 노드"
  val ModalClients_add = "클라이언트 추가"
  val ModalClients_connecting = "연결 중"
  val ModalClients_nodes = "노드"
  val ModalClients_noClients = "아직 등록된 클라이언트 노드가 없습니다."
  val ModalClients_column_name = "이름"
  val ModalClients_column_lastLogin = "마지막 로그인"
  val ModalClients_column_status = "상태"
  val ModalClients_column_enabled = "활성화됨"

  val ModalNewClient_title = "새 클라이언트"
  val ModalNewClient_registered =
    """
    아래 자식 노드가 등록되었습니다.
    생성된 비밀번호를 안전한 방법으로 노드 소유자에게 보내세요.
    """

  val ModalSwitchNode_title = "노드 전환"
  val ModalSwitchNode_switch = "전환"
  val ModalSwitchNode_message =
    """
    아래와 같이 작업할 노드를 전환하려고 합니다.
    """

  val ModalNodeIcon_title = "노드 아이콘 변경"
  val ModalNodeIcon_inputImage = Fragment(
    "여기에 이미지 파일을 드래그 앤 드롭하거나,",
    br(),
    "클릭하여 선택하세요"
  )
}
