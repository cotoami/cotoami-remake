use std::{
    collections::HashMap,
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
const BROWSER_SHELL_QUERY_KEY: &str = "browserShell";
const BROWSER_SHELL_QUERY_VALUE: &str = "1";
const BLANK_BROWSER_PATH: &str = "browser-blank.html";
const BLANK_BROWSER_TITLE: &str = "Browser";
const COTOAMI_LOGOMARK_SVG: &str =
    include_str!("../../../ui/assets/static/images/logo/logomark.svg");

static BROWSER_WINDOW_COUNTER: AtomicU64 = AtomicU64::new(1);

#[derive(Clone, Default)]
pub struct BrowserRegistry {
    inner: Arc<Mutex<HashMap<String, BrowserSnapshot>>>,
}

#[derive(Clone, Default)]
struct BrowserSnapshot {
    url: Option<String>,
    title: Option<String>,
    is_loading: bool,
}

#[derive(Clone, Serialize)]
pub struct BrowserViewState {
    url: String,
    title: Option<String>,
    is_loading: bool,
}

#[derive(Clone, Serialize)]
struct BrowserStatePayload {
    content_label: String,
    url: String,
    title: Option<String>,
    is_loading: bool,
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
        let url_changed = url
            .as_deref()
            .zip(entry.url.as_deref())
            .is_some_and(|(next, current)| next != current);
        if let Some(url) = url {
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

    fn snapshot(&self, content_label: &str) -> BrowserSnapshot {
        self.inner
            .lock()
            .get(content_label)
            .cloned()
            .unwrap_or_default()
    }
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
    let content_label = serde_json::to_string(content_label)
        .expect("serializing browser content label to JavaScript must succeed");
    let logomark_svg =
        serde_json::to_string(COTOAMI_LOGOMARK_SVG).expect("serializing Cotoami logomark SVG must succeed");
    format!(
        r#"
(function () {{
  if (window.__cotoamiSelectionClipInstalled) return;
  window.__cotoamiSelectionClipInstalled = true;

  const contentLabel = {content_label};
  const cotoamiLogomark = {logomark_svg};
  let scheduled = false;
  let scrollScheduled = false;
  let postMessageIpcPreferred = false;
  let clipButton = null;

  function preferPostMessageIpc() {{
    if (postMessageIpcPreferred) return;
    postMessageIpcPreferred = true;

    const originalFetch = window.fetch;
    const originalWarn = console.warn;
    window.fetch = function (resource, options) {{
      const url = String(resource && resource.url ? resource.url : resource);
      if (url.startsWith("ipc://localhost/")) {{
        return Promise.reject(new Error("Cotoami browser selection uses native IPC."));
      }}
      return originalFetch.call(this, resource, options);
    }};
    console.warn = function (message, error) {{
      if (
        typeof message === "string" &&
        message.indexOf("IPC custom protocol failed") !== -1 &&
        error &&
        error.message === "Cotoami browser selection uses native IPC."
      ) {{
        return;
      }}
      return originalWarn.apply(this, arguments);
    }};

    window.setTimeout(function () {{
      window.fetch = originalFetch;
      console.warn = originalWarn;
    }}, 1000);
  }}

  function invoke(command, args) {{
    try {{
      if (window.__TAURI_INTERNALS__ && window.__TAURI_INTERNALS__.invoke) {{
        preferPostMessageIpc();
        window.__TAURI_INTERNALS__.invoke(command, args).catch(function () {{}});
      }}
    }} catch (_) {{}}
  }}

  function closestTargetedLink(start) {{
    let element = start;
    while (element && element !== document) {{
      if (
        element.nodeType === Node.ELEMENT_NODE &&
        element.matches &&
        element.matches("a[href][target], area[href][target]")
      ) {{
        return element;
      }}
      element = element.parentElement || element.parentNode;
    }}
    return null;
  }}

  function isPlainPrimaryClick(event) {{
    return event.button === 0 &&
      !event.metaKey &&
      !event.ctrlKey &&
      !event.shiftKey &&
      !event.altKey;
  }}

  function shouldNavigateTargetedLink(link, event) {{
    if (event.defaultPrevented || !isPlainPrimaryClick(event)) return false;
    if (link.hasAttribute("download")) return false;
    try {{
      const url = new URL(link.href, window.location.href);
      return url.protocol === "http:" || url.protocol === "https:";
    }} catch (_) {{
      return false;
    }}
  }}

  function navigateTargetedLink(event) {{
    const link = closestTargetedLink(event.target);
    if (!link || !shouldNavigateTargetedLink(link, event)) return;
    event.preventDefault();
    window.location.href = link.href;
  }}

  function selectedRange() {{
    const selection = window.getSelection && window.getSelection();
    if (!selection || selection.rangeCount === 0 || selection.isCollapsed) return null;
    const text = selection.toString();
    if (!text || !text.trim()) return null;
    return selection.getRangeAt(0);
  }}

  function usefulRect(range) {{
    const rects = Array.from(range.getClientRects ? range.getClientRects() : []);
    const rect = rects.find(r => r.width > 0 && r.height > 0) ||
      (range.getBoundingClientRect && range.getBoundingClientRect());
    if (!rect || rect.width <= 0 || rect.height <= 0) return null;
    return {{
      x: rect.left,
      y: rect.top,
      width: rect.width,
      height: rect.height
    }};
  }}

  function selectionHtml(range) {{
    const container = document.createElement("div");
    container.appendChild(range.cloneContents());
    return container.innerHTML;
  }}

  function ensureClipButton() {{
    if (clipButton) return clipButton;
    clipButton = document.createElement("button");
    clipButton.type = "button";
    clipButton.setAttribute("aria-label", "Clip");
    clipButton.style.cssText = [
      "position:fixed",
      "z-index:2147483647",
      "display:none",
      "align-items:center",
      "gap:6px",
      "height:34px",
      "padding:0 10px",
      "border:1px solid rgba(0,0,0,.18)",
      "border-radius:8px",
      "background:#fff",
      "color:#202124",
      "box-shadow:0 8px 24px rgba(0,0,0,.22)",
      "font:600 13px -apple-system,BlinkMacSystemFont,Segoe UI,sans-serif",
      "line-height:1",
      "cursor:pointer",
      "user-select:none"
    ].join(";");
    const icon = document.createElement("span");
    icon.setAttribute("aria-hidden", "true");
    icon.style.cssText = "display:inline-flex;align-items:center;justify-content:center;width:18px;height:18px;line-height:0;";
    icon.innerHTML = cotoamiLogomark;
    const svg = icon.querySelector("svg");
    if (svg) {{
      svg.removeAttribute("width");
      svg.removeAttribute("height");
      svg.style.width = "18px";
      svg.style.height = "18px";
      svg.style.display = "block";
    }}
    const label = document.createElement("span");
    label.dataset.cotoamiClipLabel = "";
    label.textContent = "Clip";
    clipButton.appendChild(icon);
    clipButton.appendChild(label);
    clipButton.addEventListener("mousedown", function (event) {{
      event.preventDefault();
      event.stopPropagation();
    }}, true);
    clipButton.addEventListener("click", function (event) {{
      event.preventDefault();
      event.stopPropagation();
      window.dispatchEvent(new CustomEvent("__cotoami_clip_capture_request", {{
        detail: {{ requestId: null }}
      }}));
    }}, true);
    return clipButton;
  }}

  function hideClipButton() {{
    if (clipButton) clipButton.style.display = "none";
  }}

  function showClipButton(rect, label) {{
    if (!rect) {{
      hideClipButton();
      return;
    }}
    const button = ensureClipButton();
    const labelElement = button.querySelector("[data-cotoami-clip-label]");
    const displayLabel = label || "Clip";
    button.setAttribute("aria-label", displayLabel);
    button.title = displayLabel;
    if (labelElement) labelElement.textContent = displayLabel;
    if (!button.isConnected) {{
      const parent = document.documentElement || document.body;
      parent.appendChild(button);
    }}
    button.style.left = Math.max(4, rect.x) + "px";
    button.style.top = Math.max(4, rect.y - 42) + "px";
    button.style.display = "inline-flex";
  }}

  function clearSelectionState() {{
    hideClipButton();
    invoke("browser_selection_state", {{ payload: {{
      content_label: contentLabel,
      url: window.location.href,
      has_selection: false
    }} }});
  }}

  function reportSelectionState() {{
    scheduled = false;
    const range = selectedRange();
    const rect = range && usefulRect(range);
    if (!range || !rect) {{
      clearSelectionState();
      return;
    }}
    invoke("browser_selection_state", {{ payload: {{
      content_label: contentLabel,
      url: window.location.href,
      has_selection: true,
      rect
    }} }});
  }}

  function scheduleSelectionState() {{
    if (scheduled) return;
    scheduled = true;
    window.requestAnimationFrame(reportSelectionState);
  }}

  function reportScrollState() {{
    scrollScheduled = false;
    invoke("browser_scroll_state", {{ payload: {{
      content_label: contentLabel,
      url: window.location.href,
      x: window.scrollX || window.pageXOffset || 0,
      y: window.scrollY || window.pageYOffset || 0
    }} }});
  }}

  function scheduleScrollState() {{
    if (scrollScheduled) return;
    scrollScheduled = true;
    window.requestAnimationFrame(reportScrollState);
  }}

  document.addEventListener("selectionchange", scheduleSelectionState, true);
  document.addEventListener("mouseup", scheduleSelectionState, true);
  document.addEventListener("click", navigateTargetedLink, true);
  document.addEventListener("keyup", event => {{
    if (event.key === "Escape") clearSelectionState();
    else scheduleSelectionState();
  }}, true);
  window.addEventListener("scroll", function () {{
    clearSelectionState();
    scheduleScrollState();
  }}, true);
  window.addEventListener("resize", clearSelectionState, true);
  window.addEventListener("blur", clearSelectionState, true);
  window.addEventListener("__cotoami_clip_overlay", event => {{
    const detail = event.detail || {{}};
    if (detail.visible) showClipButton(detail.rect, detail.label);
    else hideClipButton();
  }}, true);
  window.addEventListener("__cotoami_clip_capture_request", event => {{
    const requestId = event.detail && event.detail.requestId;
    const range = selectedRange();
    const rect = range && usefulRect(range);
    const selection = window.getSelection && window.getSelection();
    invoke("browser_selection_capture", {{ payload: {{
      content_label: contentLabel,
      request_id: requestId,
      url: window.location.href,
      title: document.title || "",
      selected_text: selection ? selection.toString() : "",
      selected_html: range ? selectionHtml(range) : "",
      has_selection: Boolean(range && rect),
      rect
    }} }});
  }}, true);
}})();
"#
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
    label: &str,
    rect: Option<&SelectionRect>,
) -> Result<(), Error> {
    let label =
        serde_json::to_string(label).expect("serializing browser clip label must succeed");
    let rect = serde_json::to_string(&rect).expect("serializing browser clip rect must succeed");
    webview.eval(&format!(
        r#"window.dispatchEvent(new CustomEvent("__cotoami_clip_overlay", {{ detail: {{ visible: {visible}, label: {label}, rect: {rect} }} }}));"#
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
    webview.eval(&format!(
        "window.scrollTo({}, {});",
        x.max(0.0),
        y.max(0.0)
    ))?;
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
        },
    ) {
        error!(
            "failed to emit browser state: shell_label={shell_label}, content_label={content_label}, reason={e}"
        );
    }

    Ok(state)
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
        let app_for_page_load = app_handle.clone();
        let app_for_title = app_handle.clone();
        let app_for_new_window = app_handle.clone();
        let content_label_for_page_load = content_label.clone();
        let content_label_for_title = content_label.clone();
        let content_label_for_new_window = content_label.clone();
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
    label: String,
    rect: Option<SelectionRect>,
) -> Result<(), Error> {
    let webview = content_webview(&app_handle, &content_label)?;
    dispatch_selection_clip_overlay(&webview, visible, &label, rect.as_ref())
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

    webview.eval("window.history.forward();")?;

    emit_browser_state(&app_handle, registry.inner(), &shell_label, &content_label)
}
