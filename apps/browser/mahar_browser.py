#!/usr/bin/env python3
"""
Mahar Browser 1.0
=================
A lightweight, tabbed web browser for Mahar OS built with PyQt5 + QtWebEngine.

Features:
  - Closable tabs, new-tab button, Ctrl+T / Ctrl+W, double-click tab bar
  - Back / forward / reload / stop / home navigation + smart address bar
  - Bookmarks (sqlite3) with bookmark bar, star button and manager dialog
  - Browsing history (sqlite3) with search and clear
  - Download manager dialog with progress bars
  - Basic ad-blocker (QWebEngineUrlRequestInterceptor) with live counter
  - Dark mode for chrome + page content (persisted)
  - Settings dialog (homepage, search engine, ad-block, clear data)
  - Zoom (Ctrl + / - / 0), find in page (Ctrl+F), status bar
  - IP Changer: route traffic through your own proxy (host/port in
    Settings; needs browser restart to apply)

Debian build deps: python3-pyqt5 python3-pyqt5.qtwebengine
"""

import os
import re
import sqlite3
import sys
import urllib.parse
from datetime import datetime

# ---------------------------------------------------------------------------
# Graceful dependency check
# ---------------------------------------------------------------------------
IMPORT_ERROR = None
try:
    from PyQt5.QtCore import Qt, QUrl, QSettings, pyqtSignal, QSize, QEvent
    from PyQt5.QtGui import QIcon, QKeySequence
    from PyQt5.QtWidgets import (
        QApplication, QMainWindow, QTabWidget, QToolBar, QLineEdit,
        QAction, QMenu, QDialog, QVBoxLayout, QHBoxLayout, QLabel,
        QPushButton, QListWidget, QListWidgetItem, QTableWidget,
        QTableWidgetItem, QProgressBar, QCheckBox, QComboBox,
        QFileDialog, QMessageBox, QStatusBar, QShortcut, QToolButton,
        QHeaderView, QWidget, QDialogButtonBox, QAbstractItemView,
    )
    from PyQt5.QtWebEngineWidgets import (
        QWebEngineView, QWebEnginePage, QWebEngineProfile,
        QWebEngineDownloadItem,
    )
    from PyQt5.QtWebEngineCore import QWebEngineUrlRequestInterceptor
    from PyQt5.QtGui import QDesktopServices
except ImportError as _e:                       # handled in main()
    IMPORT_ERROR = str(_e)

# ---------------------------------------------------------------------------
# Constants
# ---------------------------------------------------------------------------
APP_NAME = "Mahar Browser"
APP_VERSION = "1.0"
ORG_NAME = "MaharOS"

CONFIG_DIR = os.path.expanduser("~/.config/mahar-browser")
BOOKMARKS_DB = os.path.join(CONFIG_DIR, "bookmarks.db")
HISTORY_DB = os.path.join(CONFIG_DIR, "history.db")
DEFAULT_DOWNLOAD_DIR = os.path.expanduser("~/Downloads")
ICON_PATH = "/usr/share/icons/hicolor/256x256/apps/mahar-browser.png"
DEFAULT_HOMEPAGE = "https://duckduckgo.com"

SEARCH_ENGINES = {
    "DuckDuckGo": "https://duckduckgo.com/?q={}",
    "Google": "https://www.google.com/search?q={}",
    "Bing": "https://www.bing.com/search?q={}",
}

# ~50 known ad / tracker domains and URL substrings. Requests whose host or
# full URL contain one of these entries are blocked by the interceptor.
AD_BLOCKLIST = [
    # classic ad networks
    "doubleclick.net", "googlesyndication.com", "googleadservices.com",
    "2mdn.net", "adservice.google.com", "ads.yahoo.com", "advertising.com",
    "adsrvr.org", "adnxs.com", "criteo.com", "criteo.net", "pubmatic.com",
    "rubiconproject.com", "openx.net", "openx.com", "casalemedia.com",
    "contextweb.com", "sovrn.com", "tribalfusion.com", "zedo.com",
    "adform.net", "adform.com", "smartadserver.com", "serving-sys.com",
    "adtech.de", "yieldmo.com", "sharethrough.com", "nativo.com",
    # native / content-recommendation widgets
    "taboola.com", "outbrain.com", "mgid.com", "revcontent.com",
    "adblade.com", "content.ad", "zergnet.com",
    # pop / aggressive networks
    "popads.net", "popcash.net", "adcash.com", "exoclick.com",
    "propellerads.com", "adsterra.com", "clickadu.com", "hilltopads.net",
    "juicyads.com", "trafficjunky.net", "bidvertiser.com",
    # misc ad / monetisation
    "infolinks.com", "media.net", "chitika.net", "amazon-adsystem.com",
    # analytics / tracking pixels
    "scorecardresearch.com", "quantserve.com", "moatads.com", "moat.com",
    "hotjar.com", "crazyegg.com", "fullstory.com", "mixpanel.com",
    "segment.io", "optimizely.com", "newrelic.com",
    "googletagmanager.com", "google-analytics.com",
    "connect.facebook.net", "pixel.facebook.com", "analytics.tiktok.com",
    # generic URL substrings
    "/ads/", "/adserver", "/popunder", "/banner-ads", "ad_banner",
    "sponsored-link", "/track/pixel",
]

# Dark-mode CSS injected into pages (inverts colours, then restores media).
_DARK_JS_ON = (
    "var s=document.getElementById('mahar-dark');"
    "if(!s){s=document.createElement('style');s.id='mahar-dark';"
    "s.textContent='html{filter:invert(1) hue-rotate(180deg)!important;background:#111!important;}"
    "img,video,canvas,picture,[style*=\"background-image\"]{filter:invert(1) hue-rotate(180deg)!important}';"
    "document.head.appendChild(s);}"
)
_DARK_JS_OFF = (
    "var s=document.getElementById('mahar-dark');if(s){s.remove();}"
)

DARK_QSS = """
QMainWindow, QDialog { background: #1e1e1e; color: #e0e0e0; }
QToolBar { background: #2b2b2b; border: none; spacing: 4px; }
QLineEdit { background: #333333; color: #f0f0f0; border: 1px solid #555555;
            border-radius: 12px; padding: 5px 10px; selection-background-color: #0a7bc2; }
QPushButton, QToolButton { background: #3a3a3a; color: #f0f0f0; border: 1px solid #555;
            border-radius: 6px; padding: 5px 10px; }
QPushButton:hover, QToolButton:hover { background: #4a4a4a; }
QTabWidget::pane { border: none; background: #1e1e1e; }
QTabBar::tab { background: #2b2b2b; color: #cccccc; padding: 7px 14px;
               border-top-left-radius: 8px; border-top-right-radius: 8px; margin-right: 2px; }
QTabBar::tab:selected { background: #3d3d3d; color: #ffffff; }
QTabBar::tab:hover { background: #383838; }
QMenuBar { background: #2b2b2b; color: #e0e0e0; }
QMenuBar::item:selected { background: #0a7bc2; }
QMenu { background: #2b2b2b; color: #e0e0e0; border: 1px solid #555; }
QMenu::item:selected { background: #0a7bc2; color: white; }
QStatusBar { background: #2b2b2b; color: #bbbbbb; }
QListWidget, QTableWidget { background: #252525; color: #e0e0e0;
            alternate-background-color: #2b2b2b; border: 1px solid #444; }
QHeaderView::section { background: #333333; color: #e0e0e0; border: none; padding: 4px; }
QLabel { color: #e0e0e0; }
QComboBox { background: #333333; color: #f0f0f0; border: 1px solid #555;
            border-radius: 6px; padding: 4px; }
QComboBox QAbstractItemView { background: #2b2b2b; color: #e0e0e0; }
QCheckBox { color: #e0e0e0; }
QProgressBar { background: #333; border: 1px solid #555; border-radius: 4px; text-align: center; }
QProgressBar::chunk { background: #0a7bc2; }
QScrollBar:vertical { background: #2b2b2b; width: 12px; }
QScrollBar::handle:vertical { background: #555; border-radius: 6px; min-height: 20px; }
"""


# ---------------------------------------------------------------------------
# Small helpers
# ---------------------------------------------------------------------------
def elide(text, length=28):
    """Shorten text with an ellipsis for tab titles / buttons."""
    text = text or ""
    return text if len(text) <= length else text[:length - 1] + "…"


def resolve_input(text, search_template):
    """Turn address-bar text into a QUrl: URL if it looks like one,
    otherwise a search-engine query."""
    text = text.strip()
    if not text:
        return None
    if re.match(r"^[a-zA-Z][a-zA-Z0-9+.\-]*://", text):
        return QUrl(text)
    if " " not in text and ("." in text or ":" in text
                            or text.lower() == "localhost"):
        return QUrl("https://" + text)
    return QUrl(search_template.format(urllib.parse.quote_plus(text)))


def ensure_config_dir():
    os.makedirs(CONFIG_DIR, exist_ok=True)
    os.makedirs(DEFAULT_DOWNLOAD_DIR, exist_ok=True)


def db_connect(path, schema):
    conn = sqlite3.connect(path)
    conn.execute(schema)
    conn.commit()
    return conn


# ---------------------------------------------------------------------------
# Ad-blocker: real request interceptor
# ---------------------------------------------------------------------------
class AdBlockInterceptor(QWebEngineUrlRequestInterceptor):
    """Blocks requests matching AD_BLOCKLIST. Toggleable at runtime."""

    blocked = pyqtSignal(int)

    def __init__(self, parent=None):
        super().__init__(parent)
        self.enabled = True
        self.blocked_count = 0

    def interceptRequest(self, info):
        if not self.enabled:
            return
        url = info.requestUrl().toString().lower()
        host = info.requestUrl().host().lower()
        for entry in AD_BLOCKLIST:
            if entry in host or entry in url:
                info.block(True)
                self.blocked_count += 1
                self.blocked.emit(self.blocked_count)
                return

    def reset_count(self):
        self.blocked_count = 0
        self.blocked.emit(0)


# ---------------------------------------------------------------------------
# Downloads
# ---------------------------------------------------------------------------
class DownloadDialog(QDialog):
    """Shows active and finished downloads with progress bars."""

    def __init__(self, browser, parent=None):
        super().__init__(parent)
        self.browser = browser
        self.setWindowTitle("Downloads — " + APP_NAME)
        self.resize(560, 340)
        self._rows = {}  # id(item) -> (QTableWidgetItem status, QProgressBar)

        layout = QVBoxLayout(self)
        self.table = QTableWidget(0, 4, self)
        self.table.setHorizontalHeaderLabels(["File", "Progress", "Size", "Status"])
        self.table.horizontalHeader().setSectionResizeMode(0, QHeaderView.Stretch)
        self.table.setEditTriggers(QAbstractItemView.NoEditTriggers)
        self.table.setSelectionBehavior(QAbstractItemView.SelectRows)
        layout.addWidget(self.table)

        btn_row = QHBoxLayout()
        open_folder = QPushButton("Open Downloads Folder")
        open_folder.clicked.connect(self._open_folder)
        clear_done = QPushButton("Clear Finished")
        clear_done.clicked.connect(self._clear_finished)
        close_btn = QPushButton("Close")
        close_btn.clicked.connect(self.accept)
        btn_row.addWidget(open_folder)
        btn_row.addWidget(clear_done)
        btn_row.addStretch()
        btn_row.addWidget(close_btn)
        layout.addLayout(btn_row)

        self.refresh()

    # -- population ------------------------------------------------------
    def refresh(self):
        self.table.setRowCount(0)
        self._rows.clear()
        for item in self.browser.download_items:
            self._add_row(item)

    def _add_row(self, item):
        row = self.table.rowCount()
        self.table.insertRow(row)
        name = os.path.basename(item.downloadFileName()) or "download"
        self.table.setItem(row, 0, QTableWidgetItem(name))
        bar = QProgressBar(self.table)
        bar.setRange(0, 100)
        bar.setValue(0)
        self.table.setCellWidget(row, 1, bar)
        self.table.setItem(row, 2, QTableWidgetItem(""))
        status_item = QTableWidgetItem("Starting…")
        self.table.setItem(row, 3, status_item)
        self._rows[id(item)] = (status_item, bar)
        self._sync_row(item)

    def _sync_row(self, item):
        widgets = self._rows.get(id(item))
        if not widgets:
            return
        status_item, bar = widgets
        total = item.totalBytes()
        received = item.receivedBytes()
        if total > 0:
            bar.setValue(int(received * 100 / total))
            size_txt = "%s / %s" % (self._fmt(received), self._fmt(total))
        else:
            bar.setValue(0)
            size_txt = self._fmt(received)
        self.table.item(self.table.row(status_item), 2).setText(size_txt)
        state = item.state()
        if state == QWebEngineDownloadItem.DownloadInProgress:
            status_item.setText("Downloading…")
        elif state == QWebEngineDownloadItem.DownloadCompleted:
            status_item.setText("Completed")
            bar.setValue(100)
        elif state == QWebEngineDownloadItem.DownloadCancelled:
            status_item.setText("Cancelled")
        elif state == QWebEngineDownloadItem.DownloadInterrupted:
            status_item.setText("Interrupted: %s" % item.interruptReasonString())

    @staticmethod
    def _fmt(n):
        for unit in ("B", "KB", "MB", "GB"):
            if n < 1024 or unit == "GB":
                return "%.1f %s" % (n, unit)
            n /= 1024.0
        return "%d B" % n

    # -- live updates (called by the main window) ------------------------
    def update_item(self, item):
        if id(item) not in self._rows:
            self._add_row(item)
        else:
            self._sync_row(item)

    # -- buttons ----------------------------------------------------------
    def _open_folder(self):
        QDesktopServices.openUrl(
            QUrl.fromLocalFile(self.browser.settings.value(
                "download_dir", DEFAULT_DOWNLOAD_DIR)))

    def _clear_finished(self):
        self.browser.download_items = [
            i for i in self.browser.download_items
            if i.state() == QWebEngineDownloadItem.DownloadInProgress
        ]
        self.refresh()


# ---------------------------------------------------------------------------
# Bookmarks
# ---------------------------------------------------------------------------
BOOKMARK_SCHEMA = (
    "CREATE TABLE IF NOT EXISTS bookmarks ("
    "id INTEGER PRIMARY KEY, url TEXT UNIQUE, title TEXT, added TEXT)"
)


class BookmarkManagerDialog(QDialog):
    """List / open / delete bookmarks."""

    def __init__(self, browser, parent=None):
        super().__init__(parent)
        self.browser = browser
        self.setWindowTitle("Bookmark Manager — " + APP_NAME)
        self.resize(560, 380)

        layout = QVBoxLayout(self)
        self.list = QListWidget(self)
        self.list.setAlternatingRowColors(True)
        self.list.itemDoubleClicked.connect(self._open_selected)
        layout.addWidget(self.list)

        row = QHBoxLayout()
        open_btn = QPushButton("Open")
        open_btn.clicked.connect(self._open_selected)
        del_btn = QPushButton("Delete")
        del_btn.clicked.connect(self._delete_selected)
        close_btn = QPushButton("Close")
        close_btn.clicked.connect(self.accept)
        row.addWidget(open_btn)
        row.addWidget(del_btn)
        row.addStretch()
        row.addWidget(close_btn)
        layout.addLayout(row)
        self.refresh()

    def refresh(self):
        self.list.clear()
        conn = db_connect(BOOKMARKS_DB, BOOKMARK_SCHEMA)
        for bid, url, title, added in conn.execute(
                "SELECT id, url, title, added FROM bookmarks ORDER BY added DESC"):
            item = QListWidgetItem("%s\n%s" % (title or url, url))
            item.setData(Qt.UserRole, (bid, url))
            self.list.addItem(item)
        conn.close()

    def _selected(self):
        item = self.list.currentItem()
        return item.data(Qt.UserRole) if item else (None, None)

    def _open_selected(self):
        _bid, url = self._selected()
        if url:
            self.browser.new_tab(QUrl(url))
            self.accept()

    def _delete_selected(self):
        bid, url = self._selected()
        if bid is None:
            return
        if QMessageBox.question(self, APP_NAME,
                                "Delete bookmark?\n%s" % url) == QMessageBox.Yes:
            conn = db_connect(BOOKMARKS_DB, BOOKMARK_SCHEMA)
            conn.execute("DELETE FROM bookmarks WHERE id=?", (bid,))
            conn.commit()
            conn.close()
            self.browser.refresh_bookmark_bar()
            self.refresh()


# ---------------------------------------------------------------------------
# History
# ---------------------------------------------------------------------------
HISTORY_SCHEMA = (
    "CREATE TABLE IF NOT EXISTS history ("
    "id INTEGER PRIMARY KEY, url TEXT, title TEXT, visited_at TEXT)"
)


class HistoryDialog(QDialog):
    """Searchable history with double-click to open and clear button."""

    def __init__(self, browser, parent=None):
        super().__init__(parent)
        self.browser = browser
        self.setWindowTitle("History — " + APP_NAME)
        self.resize(620, 420)

        layout = QVBoxLayout(self)
        self.filter = QLineEdit(self)
        self.filter.setPlaceholderText("Search history…")
        self.filter.textChanged.connect(self.refresh)
        layout.addWidget(self.filter)

        self.list = QListWidget(self)
        self.list.setAlternatingRowColors(True)
        self.list.itemDoubleClicked.connect(self._open_selected)
        layout.addWidget(self.list)

        row = QHBoxLayout()
        clear_btn = QPushButton("Clear History")
        clear_btn.clicked.connect(self._clear)
        close_btn = QPushButton("Close")
        close_btn.clicked.connect(self.accept)
        row.addWidget(clear_btn)
        row.addStretch()
        row.addWidget(close_btn)
        layout.addLayout(row)
        self.refresh()

    def refresh(self):
        term = self.filter.text().strip().lower()
        self.list.clear()
        conn = db_connect(HISTORY_DB, HISTORY_SCHEMA)
        query = ("SELECT url, title, visited_at FROM history "
                 "ORDER BY visited_at DESC LIMIT 500")
        for url, title, visited in conn.execute(query):
            label = "%s\n%s  ·  %s" % (title or url, url, visited)
            if term and term not in label.lower():
                continue
            item = QListWidgetItem(label)
            item.setData(Qt.UserRole, url)
            self.list.addItem(item)
        conn.close()

    def _open_selected(self, item):
        url = item.data(Qt.UserRole)
        if url:
            self.browser.new_tab(QUrl(url))
            self.accept()

    def _clear(self):
        if QMessageBox.question(
                self, APP_NAME,
                "Delete all browsing history?") == QMessageBox.Yes:
            conn = db_connect(HISTORY_DB, HISTORY_SCHEMA)
            conn.execute("DELETE FROM history")
            conn.commit()
            conn.close()
            self.refresh()


# ---------------------------------------------------------------------------
# Settings
# ---------------------------------------------------------------------------
class SettingsDialog(QDialog):
    """Homepage, search engine, ad-block, clear browsing data."""

    def __init__(self, browser, parent=None):
        super().__init__(parent)
        self.browser = browser
        self.setWindowTitle("Settings — " + APP_NAME)
        self.resize(440, 300)
        s = browser.settings

        layout = QVBoxLayout(self)

        layout.addWidget(QLabel("Homepage:"))
        self.home_edit = QLineEdit(s.value("homepage", DEFAULT_HOMEPAGE), self)
        layout.addWidget(self.home_edit)

        layout.addWidget(QLabel("Search engine:"))
        self.engine_combo = QComboBox(self)
        self.engine_combo.addItems(sorted(SEARCH_ENGINES))
        current = s.value("search_engine", "DuckDuckGo")
        idx = self.engine_combo.findText(current)
        if idx >= 0:
            self.engine_combo.setCurrentIndex(idx)
        layout.addWidget(self.engine_combo)

        self.adblock_check = QCheckBox("Block ads and trackers", self)
        self.adblock_check.setChecked(
            s.value("adblock", True, type=bool))
        layout.addWidget(self.adblock_check)

        layout.addWidget(QLabel("IP Changer (proxy):"))
        self.proxy_check = QCheckBox("Route traffic through proxy", self)
        self.proxy_check.setChecked(
            s.value("proxy_enabled", False, type=bool))
        layout.addWidget(self.proxy_check)
        proxy_row = QHBoxLayout()
        self.proxy_host = QLineEdit(s.value("proxy_host", ""), self)
        self.proxy_host.setPlaceholderText("Host (e.g. 1.2.3.4)")
        self.proxy_port = QLineEdit(s.value("proxy_port", ""), self)
        self.proxy_port.setPlaceholderText("Port")
        self.proxy_port.setFixedWidth(80)
        proxy_row.addWidget(QLabel("Host:"))
        proxy_row.addWidget(self.proxy_host)
        proxy_row.addWidget(QLabel("Port:"))
        proxy_row.addWidget(self.proxy_port)
        layout.addLayout(proxy_row)
        layout.addWidget(QLabel(
            "Note: proxy changes apply after restarting the browser."))

        layout.addWidget(QLabel("Clear browsing data:"))
        data_row = QHBoxLayout()
        clear_hist = QPushButton("Clear History")
        clear_hist.clicked.connect(self._clear_history)
        clear_cache = QPushButton("Clear Cache")
        clear_cache.clicked.connect(self._clear_cache)
        data_row.addWidget(clear_hist)
        data_row.addWidget(clear_cache)
        data_row.addStretch()
        layout.addLayout(data_row)
        layout.addStretch()

        buttons = QDialogButtonBox(
            QDialogButtonBox.Save | QDialogButtonBox.Cancel, self)
        buttons.accepted.connect(self._save)
        buttons.rejected.connect(self.reject)
        layout.addWidget(buttons)

    def _save(self):
        s = self.browser.settings
        s.setValue("homepage", self.home_edit.text().strip() or DEFAULT_HOMEPAGE)
        s.setValue("search_engine", self.engine_combo.currentText())
        s.setValue("adblock", self.adblock_check.isChecked())
        s.setValue("proxy_enabled", self.proxy_check.isChecked())
        s.setValue("proxy_host", self.proxy_host.text().strip())
        s.setValue("proxy_port", self.proxy_port.text().strip())
        self.browser.apply_adblock_setting()
        self.accept()

    def _clear_history(self):
        conn = db_connect(HISTORY_DB, HISTORY_SCHEMA)
        conn.execute("DELETE FROM history")
        conn.commit()
        conn.close()
        QMessageBox.information(self, APP_NAME, "Browsing history cleared.")

    def _clear_cache(self):
        self.browser.profile.clearHttpCache()
        QMessageBox.information(self, APP_NAME, "Web cache cleared.")


# ---------------------------------------------------------------------------
# Find bar
# ---------------------------------------------------------------------------
class FindBar(QWidget):
    """Small Ctrl+F find-in-page bar."""

    def __init__(self, browser, parent=None):
        super().__init__(parent)
        self.browser = browser
        layout = QHBoxLayout(self)
        layout.setContentsMargins(6, 4, 6, 4)
        layout.addWidget(QLabel("Find:"))
        self.edit = QLineEdit(self)
        self.edit.setPlaceholderText("Find in page…")
        self.edit.returnPressed.connect(lambda: self._find())
        self.edit.textChanged.connect(lambda: self._find())
        layout.addWidget(self.edit)
        prev_btn = QPushButton("‹ Prev")
        prev_btn.clicked.connect(lambda: self._find(backward=True))
        next_btn = QPushButton("Next ›")
        next_btn.clicked.connect(lambda: self._find())
        close_btn = QPushButton("✕")
        close_btn.setFixedWidth(32)
        close_btn.clicked.connect(self.hide_bar)
        layout.addWidget(prev_btn)
        layout.addWidget(next_btn)
        layout.addWidget(close_btn)
        self.hide()

    def show_bar(self):
        self.show()
        self.edit.setFocus()
        self.edit.selectAll()

    def hide_bar(self):
        view = self.browser.current_view()
        if view:
            view.page().findText("")
        self.hide()
        if view:
            view.setFocus()

    def _find(self, backward=False):
        view = self.browser.current_view()
        if not view:
            return
        text = self.edit.text()
        if not text:
            return
        flags = QWebEngineView.FindFlags()
        if backward:
            flags |= QWebEngineView.FindBackward
        view.page().findText(text, flags)


# ---------------------------------------------------------------------------
# Main window
# ---------------------------------------------------------------------------
class MaharBrowser(QMainWindow):
    """Main browser window: tabs, toolbars, menus, dialogs, status bar."""

    def __init__(self):
        super().__init__()
        self.setWindowTitle(APP_NAME)
        if os.path.exists(ICON_PATH):
            self.setWindowIcon(QIcon(ICON_PATH))

        ensure_config_dir()
        self.settings = QSettings(ORG_NAME, "MaharBrowser")

        # -- shared persistent web profile + ad-block interceptor ----------
        self.profile = QWebEngineProfile.defaultProfile()
        self.profile.setPersistentStoragePath(
            os.path.join(CONFIG_DIR, "webstorage"))
        self.profile.setCachePath(os.path.join(CONFIG_DIR, "webcache"))
        self.profile.setHttpCacheType(QWebEngineProfile.DiskHttpCache)
        self.interceptor = AdBlockInterceptor(self.profile)
        self.profile.setUrlRequestInterceptor(self.interceptor)
        self.profile.downloadRequested.connect(self._on_download_requested)
        self.interceptor.blocked.connect(self._update_blocked_label)

        self.download_items = []          # QWebEngineDownloadItem list
        self.download_dialog = None
        self._zoom_levels = {}            # id(view) -> float

        self._build_ui()
        self.apply_adblock_setting()
        self.apply_dark_mode(initial=True)
        self.new_tab(QUrl(self.settings.value("homepage", DEFAULT_HOMEPAGE)))
        self.resize(1180, 760)

    # ------------------------------------------------------------------
    # UI construction
    # ------------------------------------------------------------------
    def _build_ui(self):
        # find bar object created first: menus reference self.find_bar
        self.find_bar = FindBar(self, self)
        self._build_menus()
        self._build_toolbar()
        self._build_bookmark_bar()

        # tabs
        self.tabs = QTabWidget(self)
        self.tabs.setTabsClosable(True)
        self.tabs.setMovable(True)
        self.tabs.setDocumentMode(True)
        self.tabs.tabCloseRequested.connect(self.close_tab)
        self.tabs.currentChanged.connect(self._on_tab_changed)
        # double-click empty tab-bar area -> new tab
        self.tabs.tabBar().installEventFilter(self)
        # "+" new-tab corner button
        plus = QPushButton("+", self.tabs)
        plus.setFixedSize(30, 28)
        plus.setToolTip("New tab (Ctrl+T)")
        plus.clicked.connect(lambda: self.new_tab())
        self.tabs.setCornerWidget(plus, Qt.TopRightCorner)
        self.setCentralWidget(self.tabs)

        # find bar (bottom, hidden) - object already created above
        self.addToolBar(Qt.BottomToolBarArea, self._wrap_find_bar())

        # status bar: message + progress + blocked-ads counter
        self.status = QStatusBar(self)
        self.setStatusBar(self.status)
        self.progress = QProgressBar(self.status)
        self.progress.setFixedWidth(160)
        self.progress.setRange(0, 100)
        self.progress.hide()
        self.status.addPermanentWidget(self.progress)
        self.blocked_label = QLabel("Ads blocked: 0", self.status)
        self.status.addPermanentWidget(self.blocked_label)

        # shortcuts
        QShortcut(QKeySequence("Ctrl+T"), self, activated=lambda: self.new_tab())
        QShortcut(QKeySequence("Ctrl+W"), self,
                  activated=lambda: self.close_tab(self.tabs.currentIndex()))
        QShortcut(QKeySequence("Ctrl+D"), self, activated=self.toggle_bookmark)
        QShortcut(QKeySequence("Ctrl+H"), self, activated=self.show_history)
        QShortcut(QKeySequence("Ctrl+F"), self,
                  activated=self.find_bar.show_bar)
        QShortcut(QKeySequence("Ctrl+="), self, activated=lambda: self._zoom(1))
        QShortcut(QKeySequence("Ctrl+-"), self, activated=lambda: self._zoom(-1))
        QShortcut(QKeySequence("Ctrl+0"), self, activated=lambda: self._zoom(0))

    def _wrap_find_bar(self):
        bar = QToolBar("Find", self)
        bar.addWidget(self.find_bar)
        bar.setMovable(False)
        bar.setVisible(True)
        return bar

    # -- menus ------------------------------------------------------------
    def _build_menus(self):
        menubar = self.menuBar()

        file_menu = menubar.addMenu("&File")
        file_menu.addAction("New Tab", lambda: self.new_tab(),
                            QKeySequence("Ctrl+T"))
        file_menu.addAction("Close Tab", self._close_current_tab,
                            QKeySequence("Ctrl+W"))
        file_menu.addSeparator()
        file_menu.addAction("Downloads…", self.show_downloads)
        file_menu.addSeparator()
        file_menu.addAction("Settings…", self.show_settings)
        file_menu.addSeparator()
        file_menu.addAction("E&xit", self.close, QKeySequence("Ctrl+Q"))

        edit_menu = menubar.addMenu("&Edit")
        edit_menu.addAction("Find in Page…", self.find_bar.show_bar,
                            QKeySequence("Ctrl+F"))

        view_menu = menubar.addMenu("&View")
        view_menu.addAction("Zoom In", lambda: self._zoom(1),
                            QKeySequence("Ctrl+="))
        view_menu.addAction("Zoom Out", lambda: self._zoom(-1),
                            QKeySequence("Ctrl+-"))
        view_menu.addAction("Reset Zoom", lambda: self._zoom(0),
                            QKeySequence("Ctrl+0"))
        view_menu.addSeparator()
        self.bookmark_bar_action = view_menu.addAction(
            "Bookmarks Bar", self._toggle_bookmark_bar)
        self.bookmark_bar_action.setCheckable(True)
        self.bookmark_bar_action.setChecked(
            self.settings.value("bookmark_bar", True, type=bool))
        self.adblock_action = view_menu.addAction(
            "Block Ads", self._toggle_adblock)
        self.adblock_action.setCheckable(True)
        self.dark_action = view_menu.addAction(
            "Dark Mode", self._toggle_dark_mode)
        self.dark_action.setCheckable(True)
        self.dark_action.setChecked(
            self.settings.value("darkmode", False, type=bool))
        view_menu.addSeparator()
        view_menu.addAction("Reload", self._reload_current,
                            QKeySequence("Ctrl+R"))

        bm_menu = menubar.addMenu("&Bookmarks")
        bm_menu.addAction("Bookmark This Page", self.toggle_bookmark,
                          QKeySequence("Ctrl+D"))
        bm_menu.addAction("Bookmark Manager…", self.show_bookmark_manager)

        hist_menu = menubar.addMenu("&History")
        hist_menu.addAction("Show History…", self.show_history,
                            QKeySequence("Ctrl+H"))
        hist_menu.addAction("Clear History", self._clear_history_menu)

        help_menu = menubar.addMenu("&Help")
        help_menu.addAction("About Mahar Browser", self.show_about)

    # -- toolbar ----------------------------------------------------------
    def _build_toolbar(self):
        tb = QToolBar("Navigation", self)
        tb.setMovable(False)
        self.addToolBar(tb)

        self.back_btn = QToolButton(tb)
        self.back_btn.setText("‹")
        self.back_btn.setToolTip("Back (Alt+Left)")
        self.back_btn.setFixedSize(34, 30)
        self.back_btn.clicked.connect(
            lambda: self._nav(lambda v: v.back()))
        self.fwd_btn = QToolButton(tb)
        self.fwd_btn.setText("›")
        self.fwd_btn.setToolTip("Forward (Alt+Right)")
        self.fwd_btn.setFixedSize(34, 30)
        self.fwd_btn.clicked.connect(
            lambda: self._nav(lambda v: v.forward()))
        self.reload_btn = QToolButton(tb)
        self.reload_btn.setText("⟳")
        self.reload_btn.setToolTip("Reload (Ctrl+R)")
        self.reload_btn.setFixedSize(34, 30)
        self.reload_btn.clicked.connect(self._reload_current)
        self.stop_btn = QToolButton(tb)
        self.stop_btn.setText("✕")
        self.stop_btn.setToolTip("Stop loading (Esc)")
        self.stop_btn.setFixedSize(34, 30)
        self.stop_btn.clicked.connect(lambda: self._nav(lambda v: v.stop()))
        self.home_btn = QToolButton(tb)
        self.home_btn.setText("⌂")
        self.home_btn.setToolTip("Home")
        self.home_btn.setFixedSize(34, 30)
        self.home_btn.clicked.connect(self.go_home)
        for b in (self.back_btn, self.fwd_btn, self.reload_btn,
                  self.stop_btn, self.home_btn):
            tb.addWidget(b)

        self.address = QLineEdit(tb)
        self.address.setPlaceholderText("Search or enter address")
        self.address.setClearButtonEnabled(True)
        self.address.returnPressed.connect(self._load_from_address)
        tb.addWidget(self.address)

        self.star_btn = QToolButton(tb)
        self.star_btn.setText("☆")
        self.star_btn.setToolTip("Bookmark this page (Ctrl+D)")
        self.star_btn.setFixedSize(34, 30)
        self.star_btn.clicked.connect(self.toggle_bookmark)
        tb.addWidget(self.star_btn)

        dl_btn = QToolButton(tb)
        dl_btn.setText("⤓")
        dl_btn.setToolTip("Downloads")
        dl_btn.setFixedSize(34, 30)
        dl_btn.clicked.connect(self.show_downloads)
        tb.addWidget(dl_btn)

    def _build_bookmark_bar(self):
        self.bookmark_bar = QToolBar("Bookmarks Bar", self)
        self.bookmark_bar.setMovable(False)
        self.addToolBar(Qt.TopToolBarArea, self.bookmark_bar)
        self.bookmark_bar.setVisible(
            self.settings.value("bookmark_bar", True, type=bool))
        self.refresh_bookmark_bar()

    # ------------------------------------------------------------------
    # Tabs
    # ------------------------------------------------------------------
    def new_tab(self, url=None):
        view = QWebEngineView(self)
        view.setPage(QWebEnginePage(self.profile, view))
        view.loadStarted.connect(lambda: self._on_load_started(view))
        view.loadProgress.connect(
            lambda p, v=view: self._on_load_progress(v, p))
        view.loadFinished.connect(
            lambda ok, v=view: self._on_load_finished(v, ok))
        view.urlChanged.connect(lambda u, v=view: self._on_url_changed(v, u))
        view.titleChanged.connect(
            lambda t, v=view: self._on_title_changed(v, t))

        index = self.tabs.addTab(view, "New Tab")
        self.tabs.setCurrentIndex(index)
        self._zoom_levels[id(view)] = 1.0
        if url is None:
            url = QUrl(self.settings.value("homepage", DEFAULT_HOMEPAGE))
        view.load(url)
        return view

    def close_tab(self, index):
        if index < 0:
            return
        widget = self.tabs.widget(index)
        self.tabs.removeTab(index)
        if widget:
            widget.deleteLater()
        if self.tabs.count() == 0:
            self.new_tab()

    def _close_current_tab(self):
        self.close_tab(self.tabs.currentIndex())

    def current_view(self):
        return self.tabs.currentWidget()

    def eventFilter(self, obj, event):
        # double-click on empty tab-bar space opens a new tab
        if (obj is self.tabs.tabBar()
                and event.type() == QEvent.MouseButtonDblClick
                and self.tabs.tabBar().tabAt(event.pos()) == -1):
            self.new_tab()
            return True
        return super().eventFilter(obj, event)

    def _on_tab_changed(self, index):
        view = self.tabs.widget(index)
        if view:
            self.address.setText(view.url().toString())
            self._update_nav_buttons(view)
            self._update_star(view.url())

    # ------------------------------------------------------------------
    # Navigation
    # ------------------------------------------------------------------
    def _nav(self, fn):
        view = self.current_view()
        if view:
            fn(view)

    def _reload_current(self):
        view = self.current_view()
        if view:
            view.reload()

    def go_home(self):
        view = self.current_view()
        if view:
            view.load(QUrl(self.settings.value("homepage", DEFAULT_HOMEPAGE)))

    def _load_from_address(self):
        view = self.current_view()
        if not view:
            return
        template = SEARCH_ENGINES.get(
            self.settings.value("search_engine", "DuckDuckGo"),
            SEARCH_ENGINES["DuckDuckGo"])
        url = resolve_input(self.address.text(), template)
        if url:
            view.load(url)

    def _on_url_changed(self, view, url):
        if view is self.current_view():
            self.address.setText(url.toString())
            self._update_nav_buttons(view)
            self._update_star(url)

    def _on_title_changed(self, view, title):
        index = self.tabs.indexOf(view)
        if index >= 0:
            self.tabs.setTabText(index, elide(title or "New Tab"))
            self.tabs.setTabToolTip(index, title)

    def _update_nav_buttons(self, view):
        hist = view.history()
        self.back_btn.setEnabled(hist.canGoBack())
        self.fwd_btn.setEnabled(hist.canGoForward())

    def _on_load_started(self, view):
        if view is self.current_view():
            self.progress.show()
            self.progress.setValue(0)
            self.status.showMessage("Loading…")

    def _on_load_progress(self, view, percent):
        if view is self.current_view():
            self.progress.setValue(percent)
            if percent >= 100:
                self.progress.hide()
                self.status.clearMessage()

    def _on_load_finished(self, view, ok):
        if view is self.current_view():
            self.progress.hide()
            self.status.clearMessage()
            if not ok:
                self.status.showMessage("Failed to load page.", 4000)
            self._update_nav_buttons(view)
        # record history + re-apply dark mode CSS for new page
        url = view.url()
        if ok and url.scheme() in ("http", "https"):
            self._record_history(url.toString(), view.title())
        if self.dark_action.isChecked():
            view.page().runJavaScript(_DARK_JS_ON)

    # ------------------------------------------------------------------
    # Bookmarks
    # ------------------------------------------------------------------
    def _is_bookmarked(self, url):
        conn = db_connect(BOOKMARKS_DB, BOOKMARK_SCHEMA)
        row = conn.execute("SELECT 1 FROM bookmarks WHERE url=?",
                           (url,)).fetchone()
        conn.close()
        return row is not None

    def _update_star(self, url):
        url = url.toString() if hasattr(url, "toString") else str(url)
        marked = bool(url) and self._is_bookmarked(url)
        self.star_btn.setText("★" if marked else "☆")
        self.star_btn.setToolTip(
            "Remove bookmark (Ctrl+D)" if marked
            else "Bookmark this page (Ctrl+D)")

    def toggle_bookmark(self):
        view = self.current_view()
        if not view:
            return
        url = view.url().toString()
        if not url or url in ("about:blank", ""):
            return
        conn = db_connect(BOOKMARKS_DB, BOOKMARK_SCHEMA)
        if self._is_bookmarked(url):
            conn.execute("DELETE FROM bookmarks WHERE url=?", (url,))
            conn.commit()
            conn.close()
            self.status.showMessage("Bookmark removed.", 2500)
        else:
            conn.execute(
                "INSERT OR IGNORE INTO bookmarks (url, title, added) "
                "VALUES (?, ?, ?)",
                (url, view.title() or url,
                 datetime.now().strftime("%Y-%m-%d %H:%M")))
            conn.commit()
            conn.close()
            self.status.showMessage("Bookmarked.", 2500)
        self._update_star(url)
        self.refresh_bookmark_bar()

    def refresh_bookmark_bar(self):
        self.bookmark_bar.clear()
        conn = db_connect(BOOKMARKS_DB, BOOKMARK_SCHEMA)
        rows = conn.execute(
            "SELECT url, title FROM bookmarks ORDER BY added DESC").fetchall()
        conn.close()
        for url, title in rows:
            btn = QPushButton(elide(title or url, 20), self.bookmark_bar)
            btn.setToolTip(url)
            btn.setFlat(True)
            btn.clicked.connect(
                lambda _=False, u=url: self.new_tab(QUrl(u)))
            self.bookmark_bar.addWidget(btn)
        self.bookmark_bar.setVisible(
            self.bookmark_bar_action.isChecked())

    def _toggle_bookmark_bar(self):
        visible = self.bookmark_bar_action.isChecked()
        self.settings.setValue("bookmark_bar", visible)
        self.bookmark_bar.setVisible(visible)

    def show_bookmark_manager(self):
        BookmarkManagerDialog(self, self).exec_()

    # ------------------------------------------------------------------
    # History
    # ------------------------------------------------------------------
    def _record_history(self, url, title):
        conn = db_connect(HISTORY_DB, HISTORY_SCHEMA)
        conn.execute(
            "INSERT INTO history (url, title, visited_at) VALUES (?, ?, ?)",
            (url, title or url, datetime.now().strftime("%Y-%m-%d %H:%M")))
        conn.commit()
        conn.close()

    def show_history(self):
        HistoryDialog(self, self).exec_()

    def _clear_history_menu(self):
        if QMessageBox.question(
                self, APP_NAME,
                "Delete all browsing history?") == QMessageBox.Yes:
            conn = db_connect(HISTORY_DB, HISTORY_SCHEMA)
            conn.execute("DELETE FROM history")
            conn.commit()
            conn.close()
            self.status.showMessage("History cleared.", 2500)

    # ------------------------------------------------------------------
    # Downloads
    # ------------------------------------------------------------------
    def _on_download_requested(self, download):
        suggested = download.downloadFileName() or "download"
        start_dir = self.settings.value("download_dir", DEFAULT_DOWNLOAD_DIR)
        path, _ = QFileDialog.getSaveFileName(
            self, "Save File",
            os.path.join(start_dir, os.path.basename(suggested)))
        if not path:
            download.cancel()
            return
        self.settings.setValue("download_dir", os.path.dirname(path))
        download.setDownloadDirectory(os.path.dirname(path))
        download.setDownloadFileName(os.path.basename(path))
        download.accept()
        self.download_items.append(download)
        download.downloadProgress.connect(
            lambda _r, _t, d=download: self._download_updated(d))
        download.finished.connect(
            lambda d=download: self._download_updated(d))
        download.stateChanged.connect(
            lambda _s, d=download: self._download_updated(d))
        if self.download_dialog and self.download_dialog.isVisible():
            self.download_dialog.update_item(download)
        self.status.showMessage("Download started: %s" % path, 4000)

    def _download_updated(self, item):
        if self.download_dialog and self.download_dialog.isVisible():
            self.download_dialog.update_item(item)
        if item.state() == QWebEngineDownloadItem.DownloadCompleted:
            self.status.showMessage(
                "Download finished: %s" % item.downloadFileName(), 5000)

    def show_downloads(self):
        if self.download_dialog is None:
            self.download_dialog = DownloadDialog(self, self)
        else:
            self.download_dialog.refresh()
        self.download_dialog.show()
        self.download_dialog.raise_()
        self.download_dialog.activateWindow()

    # ------------------------------------------------------------------
    # Ad-block toggle
    # ------------------------------------------------------------------
    def apply_adblock_setting(self):
        enabled = self.settings.value("adblock", True, type=bool)
        self.interceptor.enabled = enabled
        self.adblock_action.setChecked(enabled)
        self.status.showMessage(
            "Ad blocking ON." if enabled else "Ad blocking OFF.", 2500)

    def _toggle_adblock(self):
        enabled = self.adblock_action.isChecked()
        self.settings.setValue("adblock", enabled)
        self.interceptor.enabled = enabled
        self.status.showMessage(
            "Ad blocking ON — reload pages to take effect."
            if enabled else "Ad blocking OFF — reload pages to take effect.",
            3000)

    def _update_blocked_label(self, count):
        self.blocked_label.setText("Ads blocked: %d" % count)

    # ------------------------------------------------------------------
    # Dark mode
    # ------------------------------------------------------------------
    def _toggle_dark_mode(self):
        enabled = self.dark_action.isChecked()
        self.settings.setValue("darkmode", enabled)
        self.apply_dark_mode()
        for i in range(self.tabs.count()):
            view = self.tabs.widget(i)
            if view:
                view.page().runJavaScript(
                    _DARK_JS_ON if enabled else _DARK_JS_OFF)

    def apply_dark_mode(self, initial=False):
        enabled = self.dark_action.isChecked() if not initial else \
            self.settings.value("darkmode", False, type=bool)
        QApplication.instance().setStyleSheet(DARK_QSS if enabled else "")
        if not initial:
            self.dark_action.setChecked(enabled)

    # ------------------------------------------------------------------
    # Zoom
    # ------------------------------------------------------------------
    def _zoom(self, direction):
        """direction: +1 in, -1 out, 0 reset."""
        view = self.current_view()
        if not view:
            return
        key = id(view)
        factor = self._zoom_levels.get(key, 1.0)
        if direction > 0:
            factor = min(5.0, factor * 1.15)
        elif direction < 0:
            factor = max(0.25, factor / 1.15)
        else:
            factor = 1.0
        self._zoom_levels[key] = factor
        view.setZoomFactor(factor)
        self.status.showMessage("Zoom: %d%%" % int(factor * 100), 1500)

    # ------------------------------------------------------------------
    # Settings / about
    # ------------------------------------------------------------------
    def show_settings(self):
        SettingsDialog(self, self).exec_()

    def show_about(self):
        QMessageBox.about(
            self, "About " + APP_NAME,
            "<h2>%s %s</h2>"
            "<p>A lightweight, tabbed web browser made for Mahar OS.</p>"
            "<p>Built with Python, PyQt5 and QtWebEngine.<br>"
            "Features tabs, bookmarks, history, downloads,<br>"
            "a basic ad-blocker and dark mode.</p>"
            % (APP_NAME, APP_VERSION))


# ---------------------------------------------------------------------------
# Entry point
# ---------------------------------------------------------------------------
def main():
    if IMPORT_ERROR:
        msg = (
            "Mahar Browser could not start.\n\n"
            "Missing dependency: %s\n\n"
            "Install the required packages with:\n"
            "    sudo apt install python3-pyqt5 python3-pyqt5.qtwebengine"
            % IMPORT_ERROR)
        try:
            # Aliased imports: a plain "from ... import QApplication" here
            # would shadow the module-global inside main() and crash.
            from PyQt5.QtWidgets import QApplication as _QA
            from PyQt5.QtWidgets import QMessageBox as _QB
            err_app = _QA(sys.argv)
            _QB.critical(None, APP_NAME, msg)
        except Exception:
            sys.stderr.write(msg + "\n")
        sys.exit(1)

    # QtWebEngine needs these before QApplication is created.
    flags = "--disable-gpu --no-sandbox"
    # IP Changer: apply saved proxy via Chromium flags (needs restart to change).
    try:
        ps = QSettings(ORG_NAME, "MaharBrowser")
        if ps.value("proxy_enabled", False, type=bool):
            ph = (ps.value("proxy_host", "") or "").strip()
            pp = (ps.value("proxy_port", "") or "").strip()
            if ph and pp:
                flags += " --proxy-server=http://%s:%s" % (ph, pp)
    except Exception:
        pass
    os.environ["QTWEBENGINE_CHROMIUM_FLAGS"] = flags
    app = QApplication(sys.argv)
    app.setApplicationName(APP_NAME)
    app.setOrganizationName(ORG_NAME)
    app.setApplicationVersion(APP_VERSION)

    window = MaharBrowser()
    window.show()
    sys.exit(app.exec_())


if __name__ == "__main__":
    main()
