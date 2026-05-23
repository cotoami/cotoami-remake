(function () {
  window.__cotoamiInstallBrowserPage = function ({ contentLabel, logomarkSvg }) {
    if (window.__cotoamiSelectionClipInstalled) return;
    window.__cotoamiSelectionClipInstalled = true;

    let scheduled = false;
    let scrollScheduled = false;
    let postMessageIpcPreferred = false;
    let clipOverlay = null;
    let clipActionInFlight = false;

    // Tauri sometimes tries custom-protocol IPC before postMessage IPC.
    // Suppress the expected custom-protocol warning for selection plumbing.
    function preferPostMessageIpc() {
      if (postMessageIpcPreferred) return;
      postMessageIpcPreferred = true;

      const originalFetch = window.fetch;
      const originalWarn = console.warn;
      window.fetch = function (resource, options) {
        const url = String(resource && resource.url ? resource.url : resource);
        if (url.startsWith("ipc://localhost/")) {
          return Promise.reject(
            new Error("Cotoami browser selection uses native IPC.")
          );
        }
        return originalFetch.call(this, resource, options);
      };
      console.warn = function (message, error) {
        if (
          typeof message === "string" &&
          message.indexOf("IPC custom protocol failed") !== -1 &&
          error &&
          error.message === "Cotoami browser selection uses native IPC."
        ) {
          return;
        }
        return originalWarn.apply(this, arguments);
      };

      window.setTimeout(function () {
        window.fetch = originalFetch;
        console.warn = originalWarn;
      }, 1000);
    }

    function invoke(command, args) {
      try {
        if (window.__TAURI_INTERNALS__ && window.__TAURI_INTERNALS__.invoke) {
          preferPostMessageIpc();
          window.__TAURI_INTERNALS__.invoke(command, args).catch(function () {});
        }
      } catch (_) {}
    }

    function isPlainPrimaryClick(event) {
      return (
        event.button === 0 &&
        !event.metaKey &&
        !event.ctrlKey &&
        !event.shiftKey &&
        !event.altKey
      );
    }

    function closestTargetedLink(start) {
      let element = start;
      while (element && element !== document) {
        if (
          element.nodeType === Node.ELEMENT_NODE &&
          element.matches &&
          element.matches("a[href][target], area[href][target]")
        ) {
          return element;
        }
        element = element.parentElement || element.parentNode;
      }
      return null;
    }

    function shouldNavigateTargetedLink(link, event) {
      if (event.defaultPrevented || !isPlainPrimaryClick(event)) return false;
      if (link.hasAttribute("download")) return false;
      try {
        const url = new URL(link.href, window.location.href);
        return url.protocol === "http:" || url.protocol === "https:";
      } catch (_) {
        return false;
      }
    }

    function navigateTargetedLink(event) {
      const link = closestTargetedLink(event.target);
      if (!link || !shouldNavigateTargetedLink(link, event)) return;
      event.preventDefault();
      window.location.href = link.href;
    }

    function closestHttpLink(start) {
      let element = start;
      while (element && element !== document) {
        if (
          element.nodeType === Node.ELEMENT_NODE &&
          element.matches &&
          element.matches("a[href], area[href]")
        ) {
          try {
            const url = new URL(element.href, window.location.href);
            if (url.protocol === "http:" || url.protocol === "https:") {
              return element;
            }
          } catch (_) {}
        }
        element = element.parentElement || element.parentNode;
      }
      return null;
    }

    function reportDownloadIntent(event) {
      if (event.defaultPrevented || !isPlainPrimaryClick(event)) return;
      const link = closestHttpLink(event.target);
      if (!link) return;
      invoke("browser_download_intent", {
        payload: {
          content_label: contentLabel,
          url: link.href,
          download: link.getAttribute("download") || null,
        },
      });
    }

    function selectedRange() {
      const selection = window.getSelection && window.getSelection();
      if (!selection || selection.rangeCount === 0 || selection.isCollapsed) {
        return null;
      }
      const text = selection.toString();
      if (!text || !text.trim()) return null;
      return selection.getRangeAt(0);
    }

    function usefulRect(range) {
      const rects = Array.from(range.getClientRects ? range.getClientRects() : []);
      const rect =
        rects.find((rect) => rect.width > 0 && rect.height > 0) ||
        (range.getBoundingClientRect && range.getBoundingClientRect());
      if (!rect || rect.width <= 0 || rect.height <= 0) return null;
      return {
        x: rect.left,
        y: rect.top,
        width: rect.width,
        height: rect.height,
      };
    }

    function selectionHtml(range) {
      const container = document.createElement("div");
      container.appendChild(range.cloneContents());
      return container.innerHTML;
    }

    function clearBrowserSelection() {
      const selection = window.getSelection && window.getSelection();
      if (selection && selection.removeAllRanges) selection.removeAllRanges();
    }

    function createClipOverlayIcon() {
      const icon = document.createElement("span");
      icon.setAttribute("aria-hidden", "true");
      icon.style.cssText =
        "display:inline-flex;align-items:center;justify-content:center;width:18px;height:18px;line-height:0;margin:0 4px 0 8px;user-select:none;";
      icon.innerHTML = logomarkSvg;
      const svg = icon.querySelector("svg");
      if (svg) {
        svg.removeAttribute("width");
        svg.removeAttribute("height");
        svg.style.width = "18px";
        svg.style.height = "18px";
        svg.style.display = "block";
      }
      return icon;
    }

    function createClipActionButton(action, fallbackLabel) {
      const button = document.createElement("button");
      button.type = "button";
      button.dataset.cotoamiClipAction = action;
      button.setAttribute("aria-label", fallbackLabel);
      button.style.cssText = [
        "display:inline-flex",
        "align-items:center",
        "gap:6px",
        "height:34px",
        "padding:0 10px",
        "border:0",
        "border-radius:6px",
        "background:transparent",
        "color:#202124",
        "font:600 13px -apple-system,BlinkMacSystemFont,Segoe UI,sans-serif",
        "line-height:1",
        "cursor:pointer",
        "user-select:none",
      ].join(";");

      const label = document.createElement("span");
      label.dataset.cotoamiClipLabel = action;
      label.textContent = fallbackLabel;
      button.appendChild(label);

      button.addEventListener(
        "mousedown",
        function (event) {
          event.preventDefault();
          event.stopPropagation();
        },
        true
      );
      button.addEventListener(
        "click",
        function (event) {
          event.preventDefault();
          event.stopPropagation();
          if (clipActionInFlight) return;
          clipActionInFlight = true;
          setClipOverlayDisabled(true);
          hideClipButton(false);
          window.dispatchEvent(
            new CustomEvent("__cotoami_clip_capture_request", {
              detail: { requestId: null, action },
            })
          );
          clearBrowserSelection();
          clearSelectionState();
        },
        true
      );
      return button;
    }

    function ensureClipOverlay() {
      if (clipOverlay) return clipOverlay;
      clipOverlay = document.createElement("div");
      clipOverlay.style.cssText = [
        "position:fixed",
        "z-index:2147483647",
        "display:none",
        "align-items:center",
        "gap:2px",
        "padding:2px",
        "height:34px",
        "border:1px solid rgba(0,0,0,.18)",
        "border-radius:8px",
        "background:#fff",
        "box-shadow:0 8px 24px rgba(0,0,0,.22)",
        "user-select:none",
      ].join(";");
      clipOverlay.appendChild(createClipOverlayIcon());
      clipOverlay.appendChild(createClipActionButton("clip", "Clip"));
      clipOverlay.appendChild(createClipActionButton("post", "Post"));
      return clipOverlay;
    }

    function hideClipButton(resetInFlight) {
      if (resetInFlight !== false) clipActionInFlight = false;
      if (clipOverlay) clipOverlay.style.display = "none";
    }

    function setClipOverlayDisabled(disabled) {
      if (!clipOverlay) return;
      Array.from(clipOverlay.querySelectorAll("button")).forEach((button) => {
        button.disabled = disabled;
        button.style.pointerEvents = disabled ? "none" : "auto";
        button.style.opacity = disabled ? ".62" : "1";
      });
    }

    function updateClipActionLabel(overlay, action, label) {
      const button = overlay.querySelector(
        "[data-cotoami-clip-action='" + action + "']"
      );
      const labelElement = overlay.querySelector(
        "[data-cotoami-clip-label='" + action + "']"
      );
      if (button) {
        button.setAttribute("aria-label", label);
        button.title = label;
      }
      if (labelElement) labelElement.textContent = label;
    }

    function showClipButton(rect, labels) {
      if (!rect) {
        hideClipButton();
        return;
      }
      const overlay = ensureClipOverlay();
      clipActionInFlight = false;
      setClipOverlayDisabled(false);
      updateClipActionLabel(overlay, "clip", (labels && labels.clip) || "Clip");
      updateClipActionLabel(overlay, "post", (labels && labels.post) || "Post");
      if (!overlay.isConnected) {
        const parent = document.documentElement || document.body;
        parent.appendChild(overlay);
      }
      overlay.style.left = Math.max(4, rect.x) + "px";
      overlay.style.top = Math.max(4, rect.y - 42) + "px";
      overlay.style.display = "inline-flex";
    }

    function clearSelectionState() {
      hideClipButton();
      invoke("browser_selection_state", {
        payload: {
          content_label: contentLabel,
          url: window.location.href,
          has_selection: false,
        },
      });
    }

    function reportSelectionState() {
      scheduled = false;
      const range = selectedRange();
      const rect = range && usefulRect(range);
      if (!range || !rect) {
        clearSelectionState();
        return;
      }
      invoke("browser_selection_state", {
        payload: {
          content_label: contentLabel,
          url: window.location.href,
          has_selection: true,
          rect,
        },
      });
    }

    function scheduleSelectionState() {
      if (scheduled) return;
      scheduled = true;
      window.requestAnimationFrame(reportSelectionState);
    }

    function reportScrollState() {
      scrollScheduled = false;
      invoke("browser_scroll_state", {
        payload: {
          content_label: contentLabel,
          url: window.location.href,
          x: window.scrollX || window.pageXOffset || 0,
          y: window.scrollY || window.pageYOffset || 0,
        },
      });
    }

    function scheduleScrollState() {
      if (scrollScheduled) return;
      scrollScheduled = true;
      window.requestAnimationFrame(reportScrollState);
    }

    document.addEventListener("selectionchange", scheduleSelectionState, true);
    document.addEventListener("mouseup", scheduleSelectionState, true);
    document.addEventListener("click", reportDownloadIntent, true);
    document.addEventListener("click", navigateTargetedLink, true);
    document.addEventListener(
      "keyup",
      function (event) {
        if (event.key === "Escape") clearSelectionState();
        else scheduleSelectionState();
      },
      true
    );
    window.addEventListener(
      "scroll",
      function () {
        clearSelectionState();
        scheduleScrollState();
      },
      true
    );
    window.addEventListener("resize", clearSelectionState, true);
    window.addEventListener("blur", clearSelectionState, true);
    window.addEventListener(
      "__cotoami_clip_overlay",
      function (event) {
        const detail = event.detail || {};
        if (detail.visible) showClipButton(detail.rect, detail.labels);
        else hideClipButton();
      },
      true
    );
    window.addEventListener(
      "__cotoami_clip_capture_request",
      function (event) {
        const requestId = event.detail && event.detail.requestId;
        const action = event.detail && event.detail.action;
        const range = selectedRange();
        const rect = range && usefulRect(range);
        const selection = window.getSelection && window.getSelection();
        invoke("browser_selection_capture", {
          payload: {
            content_label: contentLabel,
            request_id: requestId,
            action: action || "clip",
            url: window.location.href,
            title: document.title || "",
            selected_text: selection ? selection.toString() : "",
            selected_html: range ? selectionHtml(range) : "",
            has_selection: Boolean(range && rect),
            rect,
          },
        });
      },
      true
    );
  };
})();
