use std::{
    collections::HashMap,
    path::{Path, PathBuf},
    process::Command,
    sync::{
        atomic::{AtomicU64, Ordering},
        Arc,
    },
};

use parking_lot::Mutex;
use serde::{Deserialize, Serialize};
use tauri::{AppHandle, Emitter, Manager, Webview, WebviewUrl, Window};
use tracing::error;

use super::error::Error;

const BROWSER_STATE_EVENT: &str = "browser-state";
const BROWSER_SELECTION_STATE_EVENT: &str = "browser-selection-state";
const BROWSER_SELECTION_CAPTURE_EVENT: &str = "browser-selection-capture";
const BROWSER_SCROLL_STATE_EVENT: &str = "browser-scroll-state";
const BROWSER_DOWNLOAD_STARTED_EVENT: &str = "browser-download-started";
const BROWSER_DOWNLOAD_FINISHED_EVENT: &str = "browser-download-finished";
const BROWSER_SHELL_QUERY_KEY: &str = "browserShell";
const BROWSER_SHELL_QUERY_VALUE: &str = "1";
const BLANK_BROWSER_PATH: &str = "browser-blank.html";
const BLANK_BROWSER_TITLE: &str = "Browser";
const COTOAMI_LOGOMARK_SVG: &str =
    include_str!("../../../ui/assets/static/images/logo/logomark.svg");

static BROWSER_WINDOW_COUNTER: AtomicU64 = AtomicU64::new(1);
static BROWSER_DOWNLOAD_COUNTER: AtomicU64 = AtomicU64::new(1);

#[derive(Clone, Default)]
pub struct BrowserRegistry {
    inner: Arc<Mutex<HashMap<String, BrowserSnapshot>>>,
    downloads: Arc<Mutex<HashMap<String, Vec<PendingDownload>>>>,
    download_intents: Arc<Mutex<HashMap<String, Vec<DownloadIntent>>>>,
}

#[derive(Clone, Default)]
struct BrowserSnapshot {
    url: Option<String>,
    title: Option<String>,
    is_loading: bool,
    can_go_back: bool,
    can_go_forward: bool,
    history_entries: Vec<String>,
    history_index: Option<usize>,
    pending_history_navigation: Option<PendingHistoryNavigation>,
}

#[derive(Clone, Copy)]
enum PendingHistoryNavigation {
    Back,
    Forward,
}

#[derive(Clone)]
struct PendingDownload {
    id: String,
    path: PathBuf,
}

#[derive(Clone)]
struct DownloadIntent {
    url: String,
    download: Option<String>,
}

#[derive(Clone, Serialize)]
pub struct BrowserViewState {
    url: String,
    title: Option<String>,
    is_loading: bool,
    can_go_back: bool,
    can_go_forward: bool,
}

#[derive(Clone, Serialize)]
struct BrowserStatePayload {
    content_label: String,
    url: String,
    title: Option<String>,
    is_loading: bool,
    can_go_back: bool,
    can_go_forward: bool,
}

#[derive(Clone, Deserialize, Serialize)]
pub struct SelectionRect {
    x: f64,
    y: f64,
    width: f64,
    height: f64,
}

#[derive(Clone, Deserialize, Serialize)]
pub struct BrowserSelectionStatePayload {
    content_label: String,
    url: String,
    has_selection: bool,
    rect: Option<SelectionRect>,
}

#[derive(Clone, Deserialize, Serialize)]
pub struct BrowserSelectionCapturePayload {
    content_label: String,
    request_id: Option<String>,
    action: Option<String>,
    url: String,
    title: Option<String>,
    selected_text: String,
    selected_html: String,
    has_selection: bool,
    rect: Option<SelectionRect>,
}

#[derive(Clone, Deserialize, Serialize)]
pub struct BrowserScrollStatePayload {
    content_label: String,
    url: String,
    x: f64,
    y: f64,
}

#[derive(Clone, Deserialize, Serialize)]
pub struct BrowserDownloadIntentPayload {
    content_label: String,
    url: String,
    download: Option<String>,
}

#[derive(Clone, Deserialize, Serialize)]
pub struct BrowserHistoryStatePayload {
    content_label: String,
    can_go_back: bool,
    can_go_forward: bool,
}

#[derive(Clone, Serialize)]
struct BrowserDownloadStartedPayload {
    content_label: String,
    id: String,
    url: String,
    source_url: String,
    path: String,
    filename: String,
}

#[derive(Clone, Serialize)]
struct BrowserDownloadFinishedPayload {
    content_label: String,
    id: String,
    url: String,
    path: String,
    success: bool,
}

impl BrowserRegistry {
    fn upsert(
        &self,
        content_label: &str,
        url: Option<String>,
        title: Option<String>,
        is_loading: Option<bool>,
    ) {
        let mut inner = self.inner.lock();
        let entry = inner.entry(content_label.to_string()).or_default();
        let url_changed = url.as_deref() != entry.url.as_deref();
        if let Some(url) = url {
            if url_changed {
                entry.apply_history_url(&url);
            }
            entry.url = Some(url);
        }
        if let Some(title) = title {
            entry.title = Some(title);
        } else if url_changed {
            entry.title = None;
        }
        if let Some(is_loading) = is_loading {
            entry.is_loading = is_loading;
        }
    }

    fn update_history_state(&self, content_label: &str, can_go_back: bool, can_go_forward: bool) {
        let mut inner = self.inner.lock();
        let entry = inner.entry(content_label.to_string()).or_default();
        entry.update_history_availability();
        entry.can_go_back = entry.can_go_back || can_go_back;
        entry.can_go_forward = entry.can_go_forward || can_go_forward;
    }

    fn set_pending_history_navigation(
        &self,
        content_label: &str,
        navigation: PendingHistoryNavigation,
    ) {
        let mut inner = self.inner.lock();
        let entry = inner.entry(content_label.to_string()).or_default();
        entry.pending_history_navigation = Some(navigation);
    }

    fn snapshot(&self, content_label: &str) -> BrowserSnapshot {
        self.inner
            .lock()
            .get(content_label)
            .cloned()
            .unwrap_or_default()
    }

    fn remove(&self, content_label: &str) {
        self.inner.lock().remove(content_label);
        self.download_intents.lock().remove(content_label);
        let key_prefix = format!("{content_label}\n");
        self.downloads
            .lock()
            .retain(|key, _| !key.starts_with(&key_prefix));
    }

    fn push_download(&self, content_label: &str, url: &str, download: PendingDownload) {
        let mut downloads = self.downloads.lock();
        downloads
            .entry(download_key(content_label, url))
            .or_default()
            .push(download);
    }

    fn pop_download(&self, content_label: &str, url: &str) -> Option<PendingDownload> {
        let key = download_key(content_label, url);
        let mut downloads = self.downloads.lock();
        let pending = downloads.get_mut(&key).and_then(|items| {
            if items.is_empty() {
                None
            } else {
                Some(items.remove(0))
            }
        });
        if downloads.get(&key).is_some_and(Vec::is_empty) {
            downloads.remove(&key);
        }
        pending
    }

    fn push_download_intent(&self, payload: BrowserDownloadIntentPayload) {
        let mut intents = self.download_intents.lock();
        intents
            .entry(payload.content_label)
            .or_default()
            .push(DownloadIntent {
                url: payload.url,
                download: payload.download,
            });
    }

    fn pop_download_intent(&self, content_label: &str) -> Option<DownloadIntent> {
        let mut intents = self.download_intents.lock();
        let intent = intents.get_mut(content_label).and_then(|items| items.pop());
        if intents.get(content_label).is_some_and(Vec::is_empty) {
            intents.remove(content_label);
        }
        intent
    }
}

impl BrowserSnapshot {
    fn apply_history_url(&mut self, url: &str) {
        if url.is_empty() {
            return;
        }

        match self.pending_history_navigation.take() {
            Some(PendingHistoryNavigation::Back) => {
                self.history_index = self.history_index.and_then(|index| index.checked_sub(1));
                self.align_history_index(url);
            }
            Some(PendingHistoryNavigation::Forward) => {
                if let Some(index) = self.history_index {
                    if index + 1 < self.history_entries.len() {
                        self.history_index = Some(index + 1);
                    }
                }
                self.align_history_index(url);
            }
            None => {
                if self
                    .history_index
                    .and_then(|index| self.history_entries.get(index))
                    .is_some_and(|current| current == url)
                {
                    self.update_history_availability();
                    return;
                }

                let keep_until = self
                    .history_index
                    .map(|index| index + 1)
                    .unwrap_or(self.history_entries.len());
                self.history_entries.truncate(keep_until);
                self.history_entries.push(url.to_string());
                self.history_index = Some(self.history_entries.len() - 1);
            }
        }

        self.update_history_availability();
    }

    fn align_history_index(&mut self, url: &str) {
        if self
            .history_index
            .and_then(|index| self.history_entries.get(index))
            .is_some_and(|current| current == url)
        {
            return;
        }

        if let Some(index) = self.history_entries.iter().position(|entry| entry == url) {
            self.history_index = Some(index);
        } else {
            let keep_until = self
                .history_index
                .map(|index| index + 1)
                .unwrap_or(self.history_entries.len());
            self.history_entries.truncate(keep_until);
            self.history_entries.push(url.to_string());
            self.history_index = Some(self.history_entries.len() - 1);
        }
    }

    fn update_history_availability(&mut self) {
        let index = self.history_index.unwrap_or(0);
        self.can_go_back = self.history_index.is_some_and(|index| index > 0);
        self.can_go_forward =
            self.history_index.is_some() && index + 1 < self.history_entries.len();
    }
}

fn download_key(content_label: &str, url: &str) -> String {
    format!("{content_label}\n{url}")
}

fn parse_browser_url(url: &str) -> Result<tauri::Url, Error> {
    let parsed = tauri::Url::parse(url)
        .map_err(|e| Error::system_error(format!("Invalid browser URL: {e}")))?;
    match parsed.scheme() {
        "http" | "https" => Ok(parsed),
        _ => Err(Error::new(
            "unsupported-browser-url",
            "Only HTTP and HTTPS URLs can be opened in the in-app browser.",
        )),
    }
}

fn parse_optional_browser_url(url: Option<&str>) -> Result<Option<tauri::Url>, Error> {
    url.map(str::trim)
        .filter(|url| !url.is_empty())
        .map(parse_browser_url)
        .transpose()
}

fn state_url(url: &tauri::Url) -> String {
    if url.path().ends_with(BLANK_BROWSER_PATH) {
        String::new()
    } else {
        url.to_string()
    }
}

fn suggested_download_filename(url: &tauri::Url) -> String {
    url.path_segments()
        .and_then(|segments| segments.rev().find(|segment| !segment.is_empty()))
        .map(str::trim)
        .filter(|name| !name.is_empty())
        .map(ToOwned::to_owned)
        .unwrap_or_else(|| "download".to_string())
}

fn valid_download_filename(filename: &str) -> Option<String> {
    let filename = filename.trim();
    if filename.is_empty() {
        return None;
    }
    Path::new(filename)
        .file_name()
        .and_then(|name| name.to_str())
        .map(str::trim)
        .filter(|name| !name.is_empty() && *name != "." && *name != "..")
        .map(ToOwned::to_owned)
}

fn filename_from_download_intent(intent: Option<&DownloadIntent>) -> Option<String> {
    intent
        .and_then(|intent| intent.download.as_deref().and_then(valid_download_filename))
        .or_else(|| {
            intent.and_then(|intent| {
                tauri::Url::parse(&intent.url)
                    .ok()
                    .map(|url| suggested_download_filename(&url))
                    .and_then(|filename| valid_download_filename(&filename))
            })
        })
}

fn unique_download_path(download_dir: &Path, filename: &str) -> PathBuf {
    let candidate = download_dir.join(filename);
    if !candidate.exists() {
        return candidate;
    }

    let path = Path::new(filename);
    let stem = path
        .file_stem()
        .map(|stem| stem.to_string_lossy().into_owned())
        .filter(|stem| !stem.is_empty())
        .unwrap_or_else(|| "download".to_string());
    let extension = path
        .extension()
        .map(|extension| extension.to_string_lossy());

    for index in 1.. {
        let filename = extension
            .as_ref()
            .map(|extension| format!("{stem} ({index}).{extension}"))
            .unwrap_or_else(|| format!("{stem} ({index})"));
        let candidate = download_dir.join(filename);
        if !candidate.exists() {
            return candidate;
        }
    }

    unreachable!("download filename search should always find an unused name")
}

fn path_string(path: &Path) -> String {
    path.to_string_lossy().into_owned()
}

fn blank_browser_path(theme: Option<&str>) -> String {
    let Some(theme) = theme.filter(|theme| matches!(*theme, "light" | "dark")) else {
        return BLANK_BROWSER_PATH.to_string();
    };
    let mut url = tauri::Url::parse("https://browser-blank.local/")
        .expect("static blank browser URL must be valid");
    url.query_pairs_mut().append_pair("theme", theme);
    format!("{BLANK_BROWSER_PATH}?{}", url.query().unwrap_or_default())
}

fn set_blank_theme(webview: &tauri::Webview, theme: &str) -> Result<(), Error> {
    if !matches!(theme, "light" | "dark") {
        return Ok(());
    }
    let Some(url) = webview.url().ok() else {
        return Ok(());
    };
    if !url.path().ends_with(BLANK_BROWSER_PATH) {
        return Ok(());
    }
    webview.eval(&format!(
        "document.documentElement.dataset.theme = '{}';",
        theme
    ))?;
    Ok(())
}

fn page_initialization_script(content_label: &str) -> String {
    let installer = include_str!("browser_page.js");
    let content_label = serde_json::to_string(content_label)
        .expect("serializing browser content label to JavaScript must succeed");
    let logomark_svg = serde_json::to_string(COTOAMI_LOGOMARK_SVG)
        .expect("serializing Cotoami logomark SVG must succeed");
    format!(
        r#"{installer}
window.__cotoamiInstallBrowserPage({{ contentLabel: {content_label}, logomarkSvg: {logomark_svg} }});"#
    )
}

fn dispatch_selection_capture_request(
    webview: &tauri::Webview,
    request_id: &str,
) -> Result<(), Error> {
    let request_id = serde_json::to_string(request_id)
        .expect("serializing browser selection request id must succeed");
    webview.eval(&format!(
        r#"window.dispatchEvent(new CustomEvent("__cotoami_clip_capture_request", {{ detail: {{ requestId: {request_id} }} }}));"#
    ))?;
    Ok(())
}

fn dispatch_selection_clip_overlay(
    webview: &tauri::Webview,
    visible: bool,
    clip_label: &str,
    post_label: &str,
    rect: Option<&SelectionRect>,
) -> Result<(), Error> {
    let clip_label =
        serde_json::to_string(clip_label).expect("serializing browser clip label must succeed");
    let post_label =
        serde_json::to_string(post_label).expect("serializing browser post label must succeed");
    let rect = serde_json::to_string(&rect).expect("serializing browser clip rect must succeed");
    webview.eval(&format!(
        r#"window.dispatchEvent(new CustomEvent("__cotoami_clip_overlay", {{ detail: {{ visible: {visible}, labels: {{ clip: {clip_label}, post: {post_label} }}, rect: {rect} }} }}));"#
    ))?;
    Ok(())
}

fn dispatch_scroll_restore(webview: &tauri::Webview, x: f64, y: f64) -> Result<(), Error> {
    if !x.is_finite() || !y.is_finite() {
        return Err(Error::new(
            "invalid-scroll-position",
            "Scroll position must be finite.",
        ));
    }
    webview.eval(&format!("window.scrollTo({}, {});", x.max(0.0), y.max(0.0)))?;
    Ok(())
}

fn validate_selection_payload(webview: &Webview, content_label: &str) -> Result<(), Error> {
    if !content_label.starts_with("browser-content-") || webview.label() != content_label {
        return Err(Error::new(
            "invalid-browser-selection-source",
            "Invalid browser selection source.",
        ));
    }
    Ok(())
}

fn validate_scroll_payload(webview: &Webview, content_label: &str) -> Result<(), Error> {
    if !content_label.starts_with("browser-content-") || webview.label() != content_label {
        return Err(Error::new(
            "invalid-browser-scroll-source",
            "Invalid browser scroll source.",
        ));
    }
    Ok(())
}

fn window_title(url: &tauri::Url) -> String {
    url.host_str()
        .filter(|host| !host.is_empty())
        .unwrap_or(url.as_str())
        .to_string()
}

fn window_title_from_state(state: &BrowserViewState) -> String {
    state
        .title
        .as_deref()
        .filter(|title| !title.is_empty())
        .map(ToOwned::to_owned)
        .or_else(|| {
            tauri::Url::parse(&state.url)
                .ok()
                .map(|url| window_title(&url))
        })
        .unwrap_or_else(|| {
            if state.url.is_empty() {
                BLANK_BROWSER_TITLE.to_string()
            } else {
                state.url.clone()
            }
        })
}

fn next_labels() -> (String, String) {
    let id = BROWSER_WINDOW_COUNTER.fetch_add(1, Ordering::Relaxed);
    (
        format!("browser-shell-{id}"),
        format!("browser-content-{id}"),
    )
}

fn shell_path(
    content_label: &str,
    initial_url: Option<&str>,
    locale: Option<&str>,
    database_folder: Option<&str>,
    focused_node_id: Option<&str>,
    focused_cotonoma_id: Option<&str>,
    theme: Option<&str>,
    initial_state_key: Option<&str>,
) -> String {
    let mut params = tauri::Url::parse("https://browser-shell.local/")
        .expect("static browser shell URL must be valid");
    let mut query_pairs = params.query_pairs_mut();
    query_pairs
        .append_pair(BROWSER_SHELL_QUERY_KEY, BROWSER_SHELL_QUERY_VALUE)
        .append_pair("contentLabel", content_label);
    if let Some(initial_url) = initial_url.filter(|url| !url.is_empty()) {
        query_pairs.append_pair("initialUrl", initial_url);
    }
    if let Some(locale) = locale.filter(|locale| !locale.is_empty()) {
        query_pairs.append_pair("locale", locale);
    }
    if let Some(database_folder) = database_folder.filter(|folder| !folder.is_empty()) {
        query_pairs.append_pair("databaseFolder", database_folder);
    }
    if let Some(focused_node_id) = focused_node_id.filter(|id| !id.is_empty()) {
        query_pairs.append_pair("focusedNodeId", focused_node_id);
    }
    if let Some(focused_cotonoma_id) = focused_cotonoma_id.filter(|id| !id.is_empty()) {
        query_pairs.append_pair("focusedCotonomaId", focused_cotonoma_id);
    }
    if let Some(theme) = theme.filter(|theme| !theme.is_empty()) {
        query_pairs.append_pair("theme", theme);
    }
    if let Some(initial_state_key) =
        initial_state_key.filter(|key| !key.is_empty())
    {
        query_pairs.append_pair("initialStateKey", initial_state_key);
    }
    drop(query_pairs);
    format!("index.html?{}", params.query().unwrap_or_default())
}

fn open_browser_window_internal(
    app_handle: &AppHandle,
    url: Option<&tauri::Url>,
    locale: Option<&str>,
    database_folder: Option<&str>,
    focused_node_id: Option<&str>,
    focused_cotonoma_id: Option<&str>,
    theme: Option<&str>,
    initial_state_key: Option<&str>,
) -> Result<(), Error> {
    let (shell_label, content_label) = next_labels();
    tauri::WebviewWindowBuilder::new(
        app_handle,
        &shell_label,
        WebviewUrl::App(
            shell_path(
                &content_label,
                url.map(|url| url.as_str()),
                locale,
                database_folder,
                focused_node_id,
                focused_cotonoma_id,
                theme,
                initial_state_key,
            )
            .into(),
        ),
    )
    .title(
        url.map(window_title)
            .unwrap_or_else(|| BLANK_BROWSER_TITLE.to_string()),
    )
    .inner_size(1200.0, 900.0)
    .center()
    .focused(true)
    .resizable(true)
    .build()?;
    Ok(())
}

fn content_webview(app_handle: &AppHandle, content_label: &str) -> Result<tauri::Webview, Error> {
    app_handle.get_webview(content_label).ok_or_else(|| {
        Error::new(
            "browser-not-found",
            "The browser view is no longer available.",
        )
    })
}

fn resize_browser_view(
    app_handle: &AppHandle,
    content_label: &str,
    x: f64,
    y: f64,
    width: f64,
    height: f64,
) -> Result<(), Error> {
    let webview = content_webview(app_handle, content_label)?;
    webview.set_position(tauri::LogicalPosition::new(x.max(0.0), y.max(0.0)))?;
    webview.set_size(tauri::LogicalSize::new(width.max(1.0), height.max(1.0)))?;
    Ok(())
}

fn emit_browser_state(
    app_handle: &AppHandle,
    registry: &BrowserRegistry,
    shell_label: &str,
    content_label: &str,
) -> Result<BrowserViewState, Error> {
    let webview = content_webview(app_handle, content_label)?;
    let snapshot = registry.snapshot(content_label);
    let state = BrowserViewState {
        url: snapshot
            .url
            .unwrap_or_else(|| webview.url().map(|url| url.to_string()).unwrap_or_default()),
        title: snapshot.title,
        is_loading: snapshot.is_loading,
        can_go_back: snapshot.can_go_back,
        can_go_forward: snapshot.can_go_forward,
    };

    if let Some(window) = app_handle.get_webview_window(shell_label) {
        let _ = window.set_title(&window_title_from_state(&state));
    }

    if let Err(e) = app_handle.emit(
        BROWSER_STATE_EVENT,
        BrowserStatePayload {
            content_label: content_label.to_string(),
            url: state.url.clone(),
            title: state.title.clone(),
            is_loading: state.is_loading,
            can_go_back: state.can_go_back,
            can_go_forward: state.can_go_forward,
        },
    ) {
        error!(
            "failed to emit browser state: shell_label={shell_label}, content_label={content_label}, reason={e}"
        );
    }

    Ok(state)
}

fn emit_download_started(
    app_handle: &AppHandle,
    content_label: &str,
    id: &str,
    url: &tauri::Url,
    source_url: &str,
    path: &Path,
) {
    if let Err(e) = app_handle.emit(
        BROWSER_DOWNLOAD_STARTED_EVENT,
        BrowserDownloadStartedPayload {
            content_label: content_label.to_string(),
            id: id.to_string(),
            url: url.to_string(),
            source_url: source_url.to_string(),
            path: path_string(path),
            filename: path
                .file_name()
                .map(|name| name.to_string_lossy().into_owned())
                .unwrap_or_else(|| suggested_download_filename(url)),
        },
    ) {
        error!("failed to emit browser download started: reason={e}");
    }
}

fn emit_download_finished(
    app_handle: &AppHandle,
    content_label: &str,
    id: &str,
    url: &tauri::Url,
    path: &Path,
    success: bool,
) {
    if let Err(e) = app_handle.emit(
        BROWSER_DOWNLOAD_FINISHED_EVENT,
        BrowserDownloadFinishedPayload {
            content_label: content_label.to_string(),
            id: id.to_string(),
            url: url.to_string(),
            path: path_string(path),
            success,
        },
    ) {
        error!("failed to emit browser download finished: reason={e}");
    }
}

#[tauri::command]
pub fn open_browser_window(
    app_handle: AppHandle,
    url: Option<String>,
    locale: Option<String>,
    database_folder: Option<String>,
    focused_node_id: Option<String>,
    focused_cotonoma_id: Option<String>,
    theme: Option<String>,
    initial_state_key: Option<String>,
) -> Result<(), Error> {
    let url = parse_optional_browser_url(url.as_deref())?;
    open_browser_window_internal(
        &app_handle,
        url.as_ref(),
        locale.as_deref(),
        database_folder.as_deref(),
        focused_node_id.as_deref(),
        focused_cotonoma_id.as_deref(),
        theme.as_deref(),
        initial_state_key.as_deref(),
    )
}

#[tauri::command]
pub fn browser_attach(
    window: Window,
    app_handle: AppHandle,
    registry: tauri::State<'_, BrowserRegistry>,
    content_label: String,
    initial_url: Option<String>,
    theme: Option<String>,
    x: f64,
    y: f64,
    width: f64,
    height: f64,
) -> Result<BrowserViewState, Error> {
    let initial_url = parse_optional_browser_url(initial_url.as_deref())?;

    if app_handle.get_webview(&content_label).is_none() {
        registry.upsert(
            &content_label,
            Some(
                initial_url
                    .as_ref()
                    .map(|url| url.to_string())
                    .unwrap_or_default(),
            ),
            None,
            Some(initial_url.is_some()),
        );

        let webview_url = initial_url
            .map(WebviewUrl::External)
            .unwrap_or_else(|| WebviewUrl::App(blank_browser_path(theme.as_deref()).into()));
        let shell_label = window.label().to_string();
        let browser_registry = registry.inner().clone();
        let browser_registry_for_page_load = browser_registry.clone();
        let browser_registry_for_title = browser_registry.clone();
        let browser_registry_for_download = browser_registry.clone();
        let app_for_page_load = app_handle.clone();
        let app_for_title = app_handle.clone();
        let app_for_new_window = app_handle.clone();
        let app_for_download = app_handle.clone();
        let content_label_for_page_load = content_label.clone();
        let content_label_for_title = content_label.clone();
        let content_label_for_new_window = content_label.clone();
        let content_label_for_download = content_label.clone();
        let shell_label_for_page_load = shell_label.clone();
        let shell_label_for_title = shell_label.clone();

        window.add_child(
            tauri::webview::WebviewBuilder::new(&content_label, webview_url)
                .initialization_script(page_initialization_script(&content_label))
                .on_page_load(move |_webview, payload| {
                    browser_registry_for_page_load.upsert(
                        &content_label_for_page_load,
                        Some(state_url(payload.url())),
                        None,
                        Some(matches!(
                            payload.event(),
                            tauri::webview::PageLoadEvent::Started
                        )),
                    );
                    let _ = emit_browser_state(
                        &app_for_page_load,
                        &browser_registry_for_page_load,
                        &shell_label_for_page_load,
                        &content_label_for_page_load,
                    );
                })
                .on_document_title_changed(move |webview, title| {
                    if let Some(url) = webview
                        .url()
                        .ok()
                        .filter(|url| !url.path().ends_with(BLANK_BROWSER_PATH))
                    {
                        browser_registry_for_title.upsert(
                            &content_label_for_title,
                            Some(url.to_string()),
                            Some(title),
                            None,
                        );
                        let _ = emit_browser_state(
                            &app_for_title,
                            &browser_registry_for_title,
                            &shell_label_for_title,
                            &content_label_for_title,
                        );
                    }
                })
                .on_new_window(move |url, _features| {
                    if matches!(url.scheme(), "http" | "https") {
                        if let Some(webview) =
                            app_for_new_window.get_webview(&content_label_for_new_window)
                        {
                            let _ = webview.navigate(url);
                        }
                    }
                    tauri::webview::NewWindowResponse::Deny
                })
                .on_download(move |_webview, event| match event {
                    tauri::webview::DownloadEvent::Requested { url, destination } => {
                        let intent = browser_registry_for_download
                            .pop_download_intent(&content_label_for_download);
                        let source_url = intent
                            .as_ref()
                            .map(|intent| intent.url.as_str())
                            .unwrap_or_else(|| url.as_str())
                            .to_string();
                        let filename = filename_from_download_intent(intent.as_ref())
                            .unwrap_or_else(|| suggested_download_filename(&url));
                        let Ok(download_dir) = app_for_download.path().download_dir() else {
                            return false;
                        };
                        let path = unique_download_path(&download_dir, &filename);
                        let id = format!(
                            "browser-download-{}",
                            BROWSER_DOWNLOAD_COUNTER.fetch_add(1, Ordering::Relaxed)
                        );
                        *destination = path.clone();
                        browser_registry_for_download.push_download(
                            &content_label_for_download,
                            url.as_str(),
                            PendingDownload {
                                id: id.clone(),
                                path: path.clone(),
                            },
                        );
                        emit_download_started(
                            &app_for_download,
                            &content_label_for_download,
                            &id,
                            &url,
                            &source_url,
                            &path,
                        );
                        true
                    }
                    tauri::webview::DownloadEvent::Finished { url, path, success } => {
                        let pending = browser_registry_for_download
                            .pop_download(&content_label_for_download, url.as_str());
                        let fallback_path = path.as_deref();
                        match (pending, fallback_path) {
                            (Some(pending), _) => emit_download_finished(
                                &app_for_download,
                                &content_label_for_download,
                                &pending.id,
                                &url,
                                &pending.path,
                                success,
                            ),
                            (None, Some(path)) => emit_download_finished(
                                &app_for_download,
                                &content_label_for_download,
                                "",
                                &url,
                                path,
                                success,
                            ),
                            (None, None) => {}
                        }
                        true
                    }
                    _ => true,
                }),
            tauri::LogicalPosition::new(x.max(0.0), y.max(0.0)),
            tauri::LogicalSize::new(width.max(1.0), height.max(1.0)),
        )?;
    } else {
        resize_browser_view(&app_handle, &content_label, x, y, width, height)?;
    }

    emit_browser_state(
        &app_handle,
        registry.inner(),
        window.label(),
        &content_label,
    )
}

#[tauri::command]
pub fn browser_resize(
    window: Window,
    app_handle: AppHandle,
    registry: tauri::State<'_, BrowserRegistry>,
    content_label: String,
    x: f64,
    y: f64,
    width: f64,
    height: f64,
) -> Result<BrowserViewState, Error> {
    resize_browser_view(&app_handle, &content_label, x, y, width, height)?;
    emit_browser_state(
        &app_handle,
        registry.inner(),
        window.label(),
        &content_label,
    )
}

#[tauri::command]
pub fn browser_close(
    app_handle: AppHandle,
    registry: tauri::State<'_, BrowserRegistry>,
    content_label: String,
) -> Result<(), Error> {
    if let Some(webview) = app_handle.get_webview(&content_label) {
        webview.close()?;
    }
    registry.remove(&content_label);
    Ok(())
}

#[tauri::command]
pub fn browser_navigate(
    app_handle: AppHandle,
    registry: tauri::State<'_, BrowserRegistry>,
    content_label: String,
    url: String,
) -> Result<BrowserViewState, Error> {
    let url = parse_browser_url(&url)?;
    let webview = content_webview(&app_handle, &content_label)?;
    let shell_label = webview.window().label().to_string();

    registry.upsert(&content_label, Some(url.to_string()), None, Some(true));
    webview.navigate(url)?;

    emit_browser_state(&app_handle, registry.inner(), &shell_label, &content_label)
}

#[tauri::command]
pub fn browser_set_blank_theme(
    app_handle: AppHandle,
    content_label: String,
    theme: String,
) -> Result<(), Error> {
    let webview = content_webview(&app_handle, &content_label)?;
    set_blank_theme(&webview, &theme)
}

#[tauri::command]
pub fn browser_selection_state(
    webview: Webview,
    app_handle: AppHandle,
    payload: BrowserSelectionStatePayload,
) -> Result<(), Error> {
    validate_selection_payload(&webview, &payload.content_label)?;
    if let Err(e) = app_handle.emit(BROWSER_SELECTION_STATE_EVENT, payload) {
        error!("failed to emit browser selection state: reason={e}");
    }
    Ok(())
}

#[tauri::command]
pub fn browser_selection_capture(
    webview: Webview,
    app_handle: AppHandle,
    payload: BrowserSelectionCapturePayload,
) -> Result<(), Error> {
    validate_selection_payload(&webview, &payload.content_label)?;
    if let Err(e) = app_handle.emit(BROWSER_SELECTION_CAPTURE_EVENT, payload) {
        error!("failed to emit browser selection capture: reason={e}");
    }
    Ok(())
}

#[tauri::command]
pub fn browser_scroll_state(
    webview: Webview,
    app_handle: AppHandle,
    payload: BrowserScrollStatePayload,
) -> Result<(), Error> {
    validate_scroll_payload(&webview, &payload.content_label)?;
    if let Err(e) = app_handle.emit(BROWSER_SCROLL_STATE_EVENT, payload) {
        error!("failed to emit browser scroll state: reason={e}");
    }
    Ok(())
}

#[tauri::command]
pub fn browser_download_intent(
    webview: Webview,
    registry: tauri::State<'_, BrowserRegistry>,
    payload: BrowserDownloadIntentPayload,
) -> Result<(), Error> {
    validate_scroll_payload(&webview, &payload.content_label)?;
    registry.push_download_intent(payload);
    Ok(())
}

#[tauri::command]
pub fn browser_history_state(
    webview: Webview,
    app_handle: AppHandle,
    registry: tauri::State<'_, BrowserRegistry>,
    payload: BrowserHistoryStatePayload,
) -> Result<(), Error> {
    validate_scroll_payload(&webview, &payload.content_label)?;
    registry.update_history_state(
        &payload.content_label,
        payload.can_go_back,
        payload.can_go_forward,
    );
    let shell_label = webview.window().label().to_string();
    let _ = emit_browser_state(
        &app_handle,
        registry.inner(),
        &shell_label,
        &payload.content_label,
    )?;
    Ok(())
}

#[tauri::command]
pub fn browser_request_selection_capture(
    app_handle: AppHandle,
    content_label: String,
    request_id: String,
) -> Result<(), Error> {
    let webview = content_webview(&app_handle, &content_label)?;
    dispatch_selection_capture_request(&webview, &request_id)
}

#[tauri::command]
pub fn browser_set_selection_clip_overlay(
    app_handle: AppHandle,
    content_label: String,
    visible: bool,
    clip_label: String,
    post_label: String,
    rect: Option<SelectionRect>,
) -> Result<(), Error> {
    let webview = content_webview(&app_handle, &content_label)?;
    dispatch_selection_clip_overlay(&webview, visible, &clip_label, &post_label, rect.as_ref())
}

#[tauri::command]
pub fn browser_restore_scroll(
    app_handle: AppHandle,
    content_label: String,
    x: f64,
    y: f64,
) -> Result<(), Error> {
    let webview = content_webview(&app_handle, &content_label)?;
    dispatch_scroll_restore(&webview, x, y)
}

#[tauri::command]
pub fn browser_reveal_downloaded_file(path: String) -> Result<(), Error> {
    let path = PathBuf::from(path);
    if !path.is_absolute() {
        return Err(Error::new(
            "invalid-download-path",
            "Downloaded file path must be absolute.",
        ));
    }

    #[cfg(target_os = "macos")]
    let status = Command::new("open").arg("-R").arg(&path).status();

    #[cfg(target_os = "windows")]
    let status = Command::new("explorer")
        .arg(format!("/select,{}", path_string(&path)))
        .status();

    #[cfg(all(not(target_os = "macos"), not(target_os = "windows")))]
    let status = Command::new("xdg-open")
        .arg(path.parent().unwrap_or_else(|| Path::new("/")))
        .status();

    match status {
        Ok(status) if status.success() => Ok(()),
        Ok(status) => Err(Error::system_error(format!(
            "Couldn't reveal downloaded file: {status}"
        ))),
        Err(e) => Err(Error::system_error(format!(
            "Couldn't reveal downloaded file: {e}"
        ))),
    }
}

#[tauri::command]
pub fn browser_reload(
    app_handle: AppHandle,
    registry: tauri::State<'_, BrowserRegistry>,
    content_label: String,
) -> Result<BrowserViewState, Error> {
    let webview = content_webview(&app_handle, &content_label)?;
    let shell_label = webview.window().label().to_string();

    webview.reload()?;

    emit_browser_state(&app_handle, registry.inner(), &shell_label, &content_label)
}

#[tauri::command]
pub fn browser_go_back(
    app_handle: AppHandle,
    registry: tauri::State<'_, BrowserRegistry>,
    content_label: String,
) -> Result<BrowserViewState, Error> {
    let webview = content_webview(&app_handle, &content_label)?;
    let shell_label = webview.window().label().to_string();

    registry.set_pending_history_navigation(&content_label, PendingHistoryNavigation::Back);
    webview.eval("window.history.back();")?;

    emit_browser_state(&app_handle, registry.inner(), &shell_label, &content_label)
}

#[tauri::command]
pub fn browser_go_forward(
    app_handle: AppHandle,
    registry: tauri::State<'_, BrowserRegistry>,
    content_label: String,
) -> Result<BrowserViewState, Error> {
    let webview = content_webview(&app_handle, &content_label)?;
    let shell_label = webview.window().label().to_string();

    registry.set_pending_history_navigation(&content_label, PendingHistoryNavigation::Forward);
    webview.eval("window.history.forward();")?;

    emit_browser_state(&app_handle, registry.inner(), &shell_label, &content_label)
}
