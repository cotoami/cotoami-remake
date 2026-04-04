use std::{
    collections::HashMap,
    sync::{
        atomic::{AtomicU64, Ordering},
        Arc,
    },
};

use parking_lot::Mutex;
use serde::Serialize;
use tauri::{AppHandle, Emitter, Manager, WebviewUrl, Window};

use super::error::Error;

const BROWSER_STATE_EVENT: &str = "browser-state";
const BROWSER_SHELL_QUERY_KEY: &str = "browserShell";
const BROWSER_SHELL_QUERY_VALUE: &str = "1";

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
        if let Some(url) = url {
            entry.url = Some(url);
        }
        if let Some(title) = title {
            entry.title = Some(title);
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

fn browser_window_title(url: &tauri::Url) -> String {
    url.host_str()
        .filter(|host| !host.is_empty())
        .unwrap_or(url.as_str())
        .to_string()
}

fn browser_window_title_from_state(state: &BrowserViewState) -> String {
    state
        .title
        .as_deref()
        .filter(|title| !title.is_empty())
        .map(ToOwned::to_owned)
        .or_else(|| {
            tauri::Url::parse(&state.url)
                .ok()
                .map(|url| browser_window_title(&url))
        })
        .unwrap_or_else(|| state.url.clone())
}

fn next_browser_labels() -> (String, String) {
    let id = BROWSER_WINDOW_COUNTER.fetch_add(1, Ordering::Relaxed);
    (
        format!("browser-shell-{id}"),
        format!("browser-content-{id}"),
    )
}

fn browser_shell_path(content_label: &str, initial_url: &str) -> String {
    let mut params = tauri::Url::parse("https://browser-shell.local/")
        .expect("static browser shell URL must be valid");
    params
        .query_pairs_mut()
        .append_pair(BROWSER_SHELL_QUERY_KEY, BROWSER_SHELL_QUERY_VALUE)
        .append_pair("contentLabel", content_label)
        .append_pair("initialUrl", initial_url);
    format!("index.html?{}", params.query().unwrap_or_default())
}

fn open_browser_window_internal(app_handle: &AppHandle, url: &tauri::Url) -> Result<(), Error> {
    let (shell_label, content_label) = next_browser_labels();
    tauri::WebviewWindowBuilder::new(
        app_handle,
        &shell_label,
        WebviewUrl::App(browser_shell_path(&content_label, url.as_str()).into()),
    )
    .title(browser_window_title(url))
    .inner_size(1200.0, 900.0)
    .center()
    .focused(true)
    .resizable(true)
    .build()?;
    Ok(())
}

fn browser_webview(app_handle: &AppHandle, content_label: &str) -> Result<tauri::Webview, Error> {
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
    let webview = browser_webview(app_handle, content_label)?;
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
    let webview = browser_webview(app_handle, content_label)?;
    let snapshot = registry.snapshot(content_label);
    let state = BrowserViewState {
        url: snapshot
            .url
            .unwrap_or_else(|| webview.url().map(|url| url.to_string()).unwrap_or_default()),
        title: snapshot.title,
        is_loading: snapshot.is_loading,
    };

    if let Some(window) = app_handle.get_webview_window(shell_label) {
        let _ = window.set_title(&browser_window_title_from_state(&state));
        let _ = window.emit(
            BROWSER_STATE_EVENT,
            BrowserStatePayload {
                content_label: content_label.to_string(),
                url: state.url.clone(),
                title: state.title.clone(),
                is_loading: state.is_loading,
            },
        );
    }

    Ok(state)
}

#[tauri::command]
pub fn open_browser_window(app_handle: AppHandle, url: String) -> Result<(), Error> {
    let url = parse_browser_url(&url)?;
    open_browser_window_internal(&app_handle, &url)
}

#[tauri::command]
pub fn browser_attach(
    window: Window,
    app_handle: AppHandle,
    registry: tauri::State<'_, BrowserRegistry>,
    content_label: String,
    initial_url: String,
    x: f64,
    y: f64,
    width: f64,
    height: f64,
) -> Result<BrowserViewState, Error> {
    let initial_url = parse_browser_url(&initial_url)?;

    if app_handle.get_webview(&content_label).is_none() {
        let shell_label = window.label().to_string();
        let browser_registry = registry.inner().clone();
        let browser_registry_for_page_load = browser_registry.clone();
        let browser_registry_for_title = browser_registry.clone();
        let app_for_page_load = app_handle.clone();
        let app_for_title = app_handle.clone();
        let app_for_new_window = app_handle.clone();
        let content_label_for_page_load = content_label.clone();
        let content_label_for_title = content_label.clone();
        let shell_label_for_page_load = shell_label.clone();
        let shell_label_for_title = shell_label.clone();

        window.add_child(
            tauri::webview::WebviewBuilder::new(
                &content_label,
                WebviewUrl::External(initial_url.clone()),
            )
            .on_page_load(move |_webview, payload| {
                browser_registry_for_page_load.upsert(
                    &content_label_for_page_load,
                    Some(payload.url().to_string()),
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
            .on_document_title_changed(move |_webview, title| {
                browser_registry_for_title.upsert(
                    &content_label_for_title,
                    None,
                    Some(title),
                    None,
                );
                let _ = emit_browser_state(
                    &app_for_title,
                    &browser_registry_for_title,
                    &shell_label_for_title,
                    &content_label_for_title,
                );
            })
            .on_new_window(move |url, _features| {
                if matches!(url.scheme(), "http" | "https") {
                    let _ = open_browser_window_internal(&app_for_new_window, &url);
                }
                tauri::webview::NewWindowResponse::Deny
            }),
            tauri::LogicalPosition::new(x.max(0.0), y.max(0.0)),
            tauri::LogicalSize::new(width.max(1.0), height.max(1.0)),
        )?;
    } else {
        resize_browser_view(&app_handle, &content_label, x, y, width, height)?;
    }

    registry.upsert(
        &content_label,
        Some(initial_url.to_string()),
        None,
        Some(true),
    );
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
    let webview = browser_webview(&app_handle, &content_label)?;
    let shell_label = webview.window().label().to_string();

    registry.upsert(&content_label, Some(url.to_string()), None, Some(true));
    webview.navigate(url)?;

    emit_browser_state(&app_handle, registry.inner(), &shell_label, &content_label)
}

#[tauri::command]
pub fn browser_reload(
    app_handle: AppHandle,
    registry: tauri::State<'_, BrowserRegistry>,
    content_label: String,
) -> Result<BrowserViewState, Error> {
    let webview = browser_webview(&app_handle, &content_label)?;
    let shell_label = webview.window().label().to_string();

    registry.upsert(&content_label, None, None, Some(true));
    webview.reload()?;

    emit_browser_state(&app_handle, registry.inner(), &shell_label, &content_label)
}

#[tauri::command]
pub fn browser_go_back(
    app_handle: AppHandle,
    registry: tauri::State<'_, BrowserRegistry>,
    content_label: String,
) -> Result<BrowserViewState, Error> {
    let webview = browser_webview(&app_handle, &content_label)?;
    let shell_label = webview.window().label().to_string();

    registry.upsert(&content_label, None, None, Some(true));
    webview.eval("window.history.back();")?;

    emit_browser_state(&app_handle, registry.inner(), &shell_label, &content_label)
}

#[tauri::command]
pub fn browser_go_forward(
    app_handle: AppHandle,
    registry: tauri::State<'_, BrowserRegistry>,
    content_label: String,
) -> Result<BrowserViewState, Error> {
    let webview = browser_webview(&app_handle, &content_label)?;
    let shell_label = webview.window().label().to_string();

    registry.upsert(&content_label, None, None, Some(true));
    webview.eval("window.history.forward();")?;

    emit_browser_state(&app_handle, registry.inner(), &shell_label, &content_label)
}
