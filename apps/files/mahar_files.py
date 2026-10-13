#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
Mahar Files 1.0 — a Windows-Explorer-like two-pane file manager for Mahar OS.

Features
--------
* Left pane: directory tree (QTreeView, directories only)
* Right pane: icon view and details view with image thumbnails
* Breadcrumb address bar (clickable crumbs + Ctrl+L editable path)
* Back / Forward / Up / Home navigation with history stacks
* Copy / Cut / Paste implemented with shutil (no clipboard daemons needed)
* Delete via send2trash when available, otherwise guarded permanent delete
* Inline rename (F2), new folder (Shift+Ctrl+N), refresh (F5)
* Recursive search with result cap, toggleable preview pane
* Show/hide hidden files, icon zoom (Ctrl + / Ctrl -)
* Status bar with item / selection counts

Requires: PyQt5. Optional: send2trash (pip install send2trash).
"""

import hashlib
import mimetypes
import os
import shutil
import sys

from PyQt5.QtCore import (QDir, QDirIterator, QFileInfo, QItemSelectionModel,
                          QModelIndex, QSize, Qt, QTimer, QUrl, pyqtSignal)
from PyQt5.QtGui import QDesktopServices, QIcon, QPixmap
from PyQt5.QtWidgets import (QAbstractItemView, QAction, QActionGroup,
                             QApplication, QDialog, QDialogButtonBox,
                             QFileIconProvider, QFileSystemModel, QFormLayout,
                             QHBoxLayout,
                             QLabel, QLineEdit, QListView, QMainWindow, QMenu,
                             QMessageBox, QPlainTextEdit,
                             QShortcut, QSplitter, QStackedWidget, QStatusBar,
                             QTableView, QToolBar, QToolButton, QTreeView,
                             QVBoxLayout, QWidget)

# ---------------------------------------------------------------------------
# Constants
# ---------------------------------------------------------------------------

APP_NAME = "Mahar Files"
APP_VERSION = "1.0"
WINDOW_ICON_PATH = "/usr/share/icons/hicolor/256x256/apps/mahar-files.png"
THUMB_DIR = os.path.expanduser("~/.cache/mahar-files/thumbs")
SEARCH_RESULT_CAP = 2000       # max search hits shown in the right pane
DIRSIZE_FILE_CAP = 200000      # max files walked when sizing a folder
THUMB_MAX_BYTES = 20 * 1024 * 1024  # never thumbnail images larger than this
THUMB_SIZE = 128               # cached thumbnail edge length in pixels

IMAGE_EXTS = {".png", ".jpg", ".jpeg", ".bmp", ".gif", ".webp", ".tif",
              ".tiff", ".svg", ".ico", ".ppm", ".pgm", ".pbm", ".xpm"}

try:
    from send2trash import send2trash
    HAVE_SEND2TRASH = True
except ImportError:  # graceful fallback: guarded permanent delete
    HAVE_SEND2TRASH = False
    send2trash = None

os.makedirs(THUMB_DIR, exist_ok=True)


# ---------------------------------------------------------------------------
# Helpers
# ---------------------------------------------------------------------------

def fmt_size(num):
    """Format a byte count as a human-readable string."""
    try:
        num = float(num)
    except (TypeError, ValueError):
        return "—"
    for unit in ("B", "KB", "MB", "GB", "TB"):
        if num < 1024.0 or unit == "TB":
            return f"{num:.1f} {unit}" if unit != "B" else f"{int(num)} B"
        num /= 1024.0


def fmt_perms(path):
    """Return a 'rwxr-xr-x (0755)' style permission string for *path*."""
    try:
        mode = os.stat(path).st_mode & 0o777
    except OSError:
        return "—"
    bits = (("r", 0o400), ("w", 0o200), ("x", 0o100),
            ("r", 0o040), ("w", 0o020), ("x", 0o010),
            ("r", 0o004), ("w", 0o002), ("x", 0o001))
    text = "".join(ch if mode & mask else "-" for ch, mask in bits)
    return f"{text} ({oct(mode)})"


def is_image(path):
    """True if *path* looks like an image file (by extension)."""
    return os.path.splitext(path)[1].lower() in IMAGE_EXTS


def is_text_file(path, probe_bytes=4096):
    """Heuristic: True if *path* looks like a readable text file."""
    if os.path.isdir(path):
        return False
    try:
        with open(path, "rb") as fh:
            chunk = fh.read(probe_bytes)
    except OSError:
        return False
    if not chunk:
        return True
    if b"\x00" in chunk:  # binary
        return False
    text_exts = {".txt", ".md", ".rst", ".py", ".sh", ".log", ".ini",
                 ".cfg", ".conf", ".json", ".xml", ".html", ".htm",
                 ".css", ".js", ".ts", ".c", ".h", ".cpp", ".java",
                 ".yaml", ".yml", ".toml", ".csv", ".tex", ".desktop"}
    if os.path.splitext(path)[1].lower() in text_exts:
        return True
    mime, _ = mimetypes.guess_type(path)
    if mime and mime.startswith("text/"):
        return True
    try:
        chunk.decode("utf-8")
        return True
    except UnicodeDecodeError:
        return False


def kind_of(path):
    """Short human-readable kind label, e.g. 'PNG image' or 'Folder'."""
    if os.path.isdir(path):
        return "Folder"
    mime, _ = mimetypes.guess_type(path)
    if mime:
        main, _, sub = mime.partition("/")
        pretty = sub.replace("-", " ").replace("_", " ").upper()
        if main == "image":
            return f"{pretty} image"
        if main == "video":
            return f"{pretty} video"
        if main == "audio":
            return f"{pretty} audio"
        if main == "text":
            return f"{pretty} text file"
        return f"{pretty} file"
    ext = os.path.splitext(path)[1].upper().lstrip(".")
    return f"{ext} file" if ext else "File"


_thumb_mem = {}


def thumbnail(path, size=THUMB_SIZE):
    """Return a cached QPixmap thumbnail for an image *path* (or None).

    Thumbnails are cached in memory (keyed by path + mtime + size) and on
    disk under ~/.cache/mahar-files/thumbs so repeat visits are instant.
    """
    try:
        mtime = os.path.getmtime(path)
        if os.path.getsize(path) > THUMB_MAX_BYTES:
            return None
    except OSError:
        return None
    key = (path, mtime, size)
    cached_pm = _thumb_mem.get(key)
    if cached_pm is not None:
        return cached_pm
    digest = hashlib.md5(
        f"{path}|{mtime}|{size}".encode("utf-8", "replace")).hexdigest()
    disk_path = os.path.join(THUMB_DIR, digest + ".png")
    pixmap = QPixmap()
    if os.path.exists(disk_path):
        pixmap.load(disk_path)
    else:
        pixmap = QPixmap(path)
        if not pixmap.isNull():
            pixmap = pixmap.scaled(size, size, Qt.KeepAspectRatio,
                                   Qt.SmoothTransformation)
            try:
                pixmap.save(disk_path, "PNG")
            except OSError:
                pass
    if pixmap.isNull():
        return None
    if len(_thumb_mem) > 600:  # bound memory use
        _thumb_mem.clear()
    _thumb_mem[key] = pixmap
    return pixmap


def dir_size(path, cap=DIRSIZE_FILE_CAP):
    """Recursively sum file sizes under *path*.

    Returns (total_bytes, truncated) where *truncated* is True when the
    walk stopped early because more than *cap* files were seen.
    """
    total = 0
    seen = 0
    try:
        for root, _dirs, files in os.walk(path):
            for name in files:
                try:
                    total += os.path.getsize(os.path.join(root, name))
                except OSError:
                    pass
                seen += 1
                if seen > cap:
                    return total, True
    except OSError:
        pass
    return total, False


def unique_path(dest_dir, name):
    """Return a non-colliding path in *dest_dir* based on *name*.

    Appends ' (copy)', ' (copy 2)', ... before the extension when needed.
    """
    base, ext = os.path.splitext(name)
    candidate = os.path.join(dest_dir, name)
    if not os.path.lexists(candidate):
        return candidate
    i = 1
    while True:
        suffix = " (copy)" if i == 1 else f" (copy {i})"
        candidate = os.path.join(dest_dir, base + suffix + ext)
        if not os.path.lexists(candidate):
            return candidate
        i += 1


# ---------------------------------------------------------------------------
# Properties dialog
# ---------------------------------------------------------------------------

class PropertiesDialog(QDialog):
    """Shows name, type, size, modified date and permissions for one path."""

    def __init__(self, path, parent=None):
        super().__init__(parent)
        self.setWindowTitle(f"Properties — {os.path.basename(path) or path}")
        self.setMinimumWidth(380)
        layout = QVBoxLayout(self)
        form = QFormLayout()
        layout.addLayout(form)

        form.addRow("Name:", QLabel(os.path.basename(path) or path))
        form.addRow("Location:", QLabel(os.path.dirname(path) or "/"))
        form.addRow("Type:", QLabel(kind_of(path)))

        if os.path.isdir(path):
            size, truncated = dir_size(path)
            note = " (stopped counting early)" if truncated else ""
            form.addRow("Size:", QLabel(fmt_size(size) + note))
            try:
                entries = len(os.listdir(path))
                form.addRow("Contains:", QLabel(f"{entries} item(s)"))
            except OSError:
                pass
        else:
            try:
                form.addRow("Size:", QLabel(fmt_size(os.path.getsize(path))))
            except OSError:
                form.addRow("Size:", QLabel("—"))

        try:
            from datetime import datetime
            mtime = datetime.fromtimestamp(
                os.path.getmtime(path)).strftime("%Y-%m-%d %H:%M:%S")
        except OSError:
            mtime = "—"
        form.addRow("Modified:", QLabel(mtime))
        form.addRow("Permissions:", QLabel(fmt_perms(path)))

        buttons = QDialogButtonBox(QDialogButtonBox.Close)
        buttons.rejected.connect(self.reject)
        layout.addWidget(buttons)


# ---------------------------------------------------------------------------
# File model with thumbnail decorations
# ---------------------------------------------------------------------------

class MaharFileModel(QFileSystemModel):
    """QFileSystemModel that shows image thumbnails as decorations."""

    def data(self, index, role=Qt.DisplayRole):
        if (role == Qt.DecorationRole and index.isValid()
                and index.column() == 0 and not self.isDir(index)):
            path = self.filePath(index)
            if is_image(path):
                pixmap = thumbnail(path)
                if pixmap is not None:
                    return pixmap
        return super().data(index, role)


# ---------------------------------------------------------------------------
# Breadcrumb address bar
# ---------------------------------------------------------------------------

class BreadcrumbBar(QWidget):
    """Clickable breadcrumb crumbs with a Ctrl+L editable path mode."""

    navigated = pyqtSignal(str)

    def __init__(self, parent=None):
        super().__init__(parent)
        self._path = "/"
        outer = QHBoxLayout(self)
        outer.setContentsMargins(0, 0, 0, 0)
        outer.setSpacing(0)

        self.stack = QStackedWidget()
        outer.addWidget(self.stack, 1)

        # Page 0: clickable crumbs
        crumbs_page = QWidget()
        self.crumb_layout = QHBoxLayout(crumbs_page)
        self.crumb_layout.setContentsMargins(2, 2, 2, 2)
        self.crumb_layout.setSpacing(2)
        crumbs_page.setToolTip("Double-click empty space or press Ctrl+L "
                              "to edit the path")
        crumbs_page.mouseDoubleClickEvent = lambda _e: self.edit_path()
        self.stack.addWidget(crumbs_page)

        # Page 1: editable path
        self.edit = QLineEdit()
        self.edit.setPlaceholderText("Type a folder path and press Enter…")
        self.edit.returnPressed.connect(self._commit_edit)
        self.edit.installEventFilter(self)
        self.stack.addWidget(self.edit)

    def eventFilter(self, obj, event):
        if obj is self.edit and event.type() == event.KeyPress:
            if event.key() == Qt.Key_Escape:
                self.show_crumbs()
                return True
        return super().eventFilter(obj, event)

    def edit_path(self):
        """Switch to the editable path line."""
        self.edit.setText(self._path)
        self.stack.setCurrentWidget(self.edit)
        self.edit.selectAll()
        self.edit.setFocus()

    def show_crumbs(self):
        self.stack.setCurrentIndex(0)

    def _commit_edit(self):
        text = self.edit.text().strip()
        self.show_crumbs()
        if text:
            self.navigated.emit(os.path.expanduser(text))

    def set_path(self, path):
        """Rebuild the crumb buttons for *path*."""
        self._path = os.path.abspath(path)
        while self.crumb_layout.count():
            item = self.crumb_layout.takeAt(0)
            widget = item.widget()
            if widget is not None:
                widget.deleteLater()

        chunks = []  # list of (label, full_path)
        rest = self._path
        while True:
            head, tail = os.path.split(rest)
            if tail:
                chunks.append((tail, rest))
                rest = head
            else:
                chunks.append(("🖥 /" if head == "/" else head, head or "/"))
                break
        chunks.reverse()

        for label, full in chunks:
            btn = QToolButton()
            btn.setText(label)
            btn.setCursor(Qt.PointingHandCursor)
            btn.setStyleSheet("QToolButton { border: none; padding: 4px 6px; }"
                              "QToolButton:hover { background: palette(highlight);"
                              " color: palette(highlighted-text);"
                              " border-radius: 4px; }")
            btn.clicked.connect(lambda _checked=False, p=full:
                                self.navigated.emit(p))
            self.crumb_layout.addWidget(btn)
            if full != chunks[-1][1]:
                sep = QLabel("›")
                sep.setStyleSheet("color: gray;")
                self.crumb_layout.addWidget(sep)
        self.crumb_layout.addStretch(1)


# ---------------------------------------------------------------------------
# Main window
# ---------------------------------------------------------------------------

class MaharFiles(QMainWindow):
    """The Mahar Files main window."""

    def __init__(self):
        super().__init__()
        self.setWindowTitle(APP_NAME)
        self.resize(1120, 700)

        if os.path.exists(WINDOW_ICON_PATH):
            self.setWindowIcon(QIcon(WINDOW_ICON_PATH))
        else:  # graceful fallback to a theme icon
            self.setWindowIcon(QIcon.fromTheme("system-file-manager"))

        # --- state ---------------------------------------------------------
        self.current_path = QDir.homePath()
        self.back_stack = []
        self.forward_stack = []
        self.clipboard = []        # list of (src_path, is_cut)
        self.searching = False
        self.search_base = ""
        self.icon_size = 96
        self.icon_provider = QFileIconProvider()

        self._build_models()
        self._build_actions()
        self._build_ui()
        self._build_menus()
        self._connect_signals()

        self.act_preview.setChecked(True)
        self._goto(self.current_path)

    # -- construction ------------------------------------------------------

    def _build_models(self):
        self.left_model = QFileSystemModel(self)
        self.left_model.setFilter(QDir.Dirs | QDir.NoDotAndDotDot)
        self.left_model.setRootPath("/")

        self.right_model = MaharFileModel(self)
        self.right_model.setReadOnly(False)  # allow inline rename
        self.right_model.setFilter(QDir.AllEntries | QDir.NoDotAndDotDot)
        self.right_model.setRootPath("/")

        # Search results: Name | Size | Type | Modified, path in UserRole+1
        from PyQt5.QtGui import QStandardItemModel
        self.results_model = QStandardItemModel(0, 4, self)
        self.results_model.setHorizontalHeaderLabels(
            ["Name", "Size", "Type", "Modified"])
        self.results_model.setSortRole(Qt.UserRole)

    def _build_actions(self):
        a = {}  # name -> QAction, shared by toolbar / menus / shortcuts
        def act(name, text, slot, shortcut=None, tip="", checkable=False,
                icon=None):
            action = QAction(text, self)
            if icon:
                action.setIcon(QIcon.fromTheme(icon))
            if shortcut:
                action.setShortcut(shortcut)
            if tip:
                action.setToolTip(tip)
                action.setStatusTip(tip)
            action.setCheckable(checkable)
            action.triggered.connect(slot)
            self.addAction(action)  # window-level shortcut scope
            a[name] = action
            return action

        act("back", "Back", self.go_back, "Alt+Left",
            "Go back", icon="go-previous")
        act("forward", "Forward", self.go_forward, "Alt+Right",
            "Go forward", icon="go-next")
        act("up", "Up", self.go_up, "Alt+Up",
            "Go to parent folder", icon="go-up")
        act("home", "Home", self.go_home, "Alt+Home",
            "Go to home folder", icon="go-home")
        act("refresh", "Refresh", self.refresh, "F5",
            "Refresh the current folder", icon="view-refresh")
        act("new_folder", "New folder", self.new_folder, "Shift+Ctrl+N",
            "Create a new folder", icon="folder-new")
        act("cut", "Cut", self.cut_selection, "Ctrl+X",
            "Cut selection", icon="edit-cut")
        act("copy", "Copy", self.copy_selection, "Ctrl+C",
            "Copy selection", icon="edit-copy")
        act("paste", "Paste", self.paste, "Ctrl+V",
            "Paste", icon="edit-paste")
        act("delete", "Delete", self.delete_selection, "Delete",
            "Delete selection", icon="edit-delete")
        act("rename", "Rename", self.rename_selection, "F2",
            "Rename (inline edit)", icon="edit-rename")
        act("properties", "Properties", self.show_properties, "Alt+Enter",
            "Show properties")
        act("open", "Open", self.open_selection, "Return",
            "Open selection", icon="document-open")

        self.view_group = QActionGroup(self)
        self.view_group.setExclusive(True)
        self.act_icons = act("icons", "Icons", lambda: self.set_view_mode("icons"),
                             "Ctrl+1", "Icon view", checkable=True,
                             icon="view-grid")
        self.act_details = act("details", "Details",
                               lambda: self.set_view_mode("details"),
                               "Ctrl+2", "Details view", checkable=True,
                               icon="view-list")
        self.view_group.addAction(self.act_icons)
        self.view_group.addAction(self.act_details)
        self.act_icons.setChecked(True)

        self.act_preview = act("preview", "Preview pane",
                               self.toggle_preview, "Ctrl+P",
                               "Show/hide the preview pane",
                               checkable=True, icon="view-preview")
        self.act_hidden = act("hidden", "Show hidden files",
                              self.toggle_hidden, "Ctrl+H",
                              "Show/hide hidden files",
                              checkable=True, icon="view-hidden")
        act("zoom_in", "Zoom in", lambda: self.zoom(16), "Ctrl++",
            "Make icons bigger", icon="zoom-in")
        act("zoom_out", "Zoom out", lambda: self.zoom(-16), "Ctrl+-",
            "Make icons smaller", icon="zoom-out")
        act("about", "About", self.show_about, None,
            "About Mahar Files", icon="help-about")
        act("quit", "Quit", self.close, "Ctrl+Q", "Quit Mahar Files")
        self.actions = a

    def _build_ui(self):
        # --- toolbar ------------------------------------------------------
        tb = QToolBar("Main toolbar", self)
        tb.setMovable(False)
        tb.setToolButtonStyle(Qt.ToolButtonTextBesideIcon)
        self.addToolBar(tb)
        A = self.actions
        for name in ("back", "forward", "up", "home", "refresh"):
            tb.addAction(A[name])
        tb.addSeparator()
        self.crumbs = BreadcrumbBar()
        # expanding breadcrumb bar
        from PyQt5.QtWidgets import QSizePolicy
        self.crumbs.setSizePolicy(QSizePolicy.Expanding, QSizePolicy.Preferred)
        tb.addWidget(self.crumbs)
        tb.addSeparator()
        self.search_box = QLineEdit()
        self.search_box.setPlaceholderText("🔍 Search here…")
        self.search_box.setClearButtonEnabled(True)
        self.search_box.setFixedWidth(220)
        self.search_box.setToolTip("Search the current folder recursively "
                                   "(Enter). Esc clears.")
        tb.addWidget(self.search_box)
        tb.addSeparator()
        for name in ("icons", "details"):
            btn = QToolButton()
            btn.setDefaultAction(A[name])
            btn.setToolButtonStyle(Qt.ToolButtonIconOnly)
            tb.addWidget(btn)
        tb.addSeparator()
        preview_btn = QToolButton()
        preview_btn.setDefaultAction(self.act_preview)
        preview_btn.setToolButtonStyle(Qt.ToolButtonIconOnly)
        tb.addWidget(preview_btn)

        # --- central splitter: tree | (views + preview) -------------------
        central = QSplitter(Qt.Horizontal, self)
        self.setCentralWidget(central)

        self.tree = QTreeView()
        self.tree.setModel(self.left_model)
        self.tree.setRootIndex(self.left_model.index("/"))
        self.tree.setHeaderHidden(True)
        for col in (1, 2, 3):
            self.tree.hideColumn(col)
        self.tree.setContextMenuPolicy(Qt.CustomContextMenu)
        self.tree.setMinimumWidth(180)
        central.addWidget(self.tree)

        right_split = QSplitter(Qt.Horizontal, self)

        self.view_stack = QStackedWidget()
        self.icon_view = QListView()
        self.icon_view.setModel(self.right_model)
        self.icon_view.setViewMode(QListView.IconMode)
        self.icon_view.setResizeMode(QListView.Adjust)
        self.icon_view.setWordWrap(True)
        self.icon_view.setSpacing(6)
        self.icon_view.setIconSize(QSize(self.icon_size, self.icon_size))
        self.icon_view.setGridSize(QSize(self.icon_size + 36,
                                        self.icon_size + 52))
        self.icon_view.setSortingEnabled(True)
        self.icon_view.setSelectionMode(QAbstractItemView.ExtendedSelection)
        self.icon_view.setEditTriggers(QAbstractItemView.EditKeyPressed |
                                       QAbstractItemView.SelectedClicked)
        self.icon_view.setContextMenuPolicy(Qt.CustomContextMenu)

        self.details_view = QTableView()
        self.details_view.setModel(self.right_model)
        self.details_view.setIconSize(QSize(28, 28))
        self.details_view.setSortingEnabled(True)
        self.details_view.setSelectionBehavior(QAbstractItemView.SelectRows)
        self.details_view.setSelectionMode(QAbstractItemView.ExtendedSelection)
        self.details_view.setEditTriggers(QAbstractItemView.EditKeyPressed |
                                          QAbstractItemView.SelectedClicked)
        self.details_view.setContextMenuPolicy(Qt.CustomContextMenu)
        self.details_view.verticalHeader().setVisible(False)
        self.details_view.horizontalHeader().setStretchLastSection(True)
        self.details_view.setColumnWidth(0, 280)
        self.details_view.setColumnWidth(1, 90)
        self.details_view.setColumnWidth(2, 150)

        self.view_stack.addWidget(self.icon_view)
        self.view_stack.addWidget(self.details_view)
        right_split.addWidget(self.view_stack)

        # --- preview pane --------------------------------------------------
        self.preview = QWidget()
        pv = QVBoxLayout(self.preview)
        pv.setContentsMargins(8, 8, 8, 8)
        self.preview_title = QLabel()
        self.preview_title.setWordWrap(True)
        self.preview_title.setStyleSheet("font-weight: bold;")
        pv.addWidget(self.preview_title)
        self.preview_image = QLabel()
        self.preview_image.setAlignment(Qt.AlignCenter)
        self.preview_image.setMinimumHeight(160)
        pv.addWidget(self.preview_image)
        self.preview_text = QPlainTextEdit()
        self.preview_text.setReadOnly(True)
        self.preview_text.setMaximumBlockCount(60)
        pv.addWidget(self.preview_text, 1)
        self.preview_meta = QLabel()
        self.preview_meta.setWordWrap(True)
        self.preview_meta.setStyleSheet("color: palette(mid);")
        pv.addWidget(self.preview_meta)
        pv.addStretch(1)
        self.preview.setMinimumWidth(220)
        self.preview.setMaximumWidth(340)
        right_split.addWidget(self.preview)
        right_split.setSizes([700, 260])

        central.addWidget(right_split)
        central.setSizes([240, 880])
        central.setStretchFactor(0, 0)
        central.setStretchFactor(1, 1)

        # --- status bar ----------------------------------------------------
        status = QStatusBar(self)
        self.setStatusBar(status)
        self.lbl_items = QLabel()
        self.lbl_sel = QLabel()
        status.addWidget(self.lbl_items, 1)
        status.addWidget(self.lbl_sel)

    def _build_menus(self):
        A = self.actions
        menubar = self.menuBar()

        m_file = menubar.addMenu("&File")
        m_file.addAction(A["new_folder"])
        m_file.addSeparator()
        m_file.addAction(A["open"])
        m_file.addSeparator()
        for name in ("cut", "copy", "paste", "delete", "rename"):
            m_file.addAction(A[name])
        m_file.addSeparator()
        m_file.addAction(A["properties"])
        m_file.addSeparator()
        m_file.addAction(A["quit"])

        m_view = menubar.addMenu("&View")
        m_view.addAction(self.act_icons)
        m_view.addAction(self.act_details)
        m_view.addSeparator()
        m_view.addAction(self.act_preview)
        m_view.addAction(self.act_hidden)
        m_view.addSeparator()
        m_view.addAction(A["refresh"])
        m_view.addAction(A["zoom_in"])
        m_view.addAction(A["zoom_out"])

        m_go = menubar.addMenu("&Go")
        for name in ("back", "forward", "up", "home"):
            m_go.addAction(A[name])

        m_help = menubar.addMenu("&Help")
        m_help.addAction(A["about"])

    def _connect_signals(self):
        self.crumbs.navigated.connect(self.navigate)
        self.tree.clicked.connect(self._on_tree_clicked)
        self.tree.customContextMenuRequested.connect(
            self._tree_context_menu)
        for view in (self.icon_view, self.details_view):
            view.doubleClicked.connect(self._on_double_clicked)
            view.customContextMenuRequested.connect(
                self._view_context_menu)
            view.selectionModel().selectionChanged.connect(
                self._on_selection_changed)
        self.tree.selectionModel().selectionChanged.connect(
            self._on_selection_changed)
        self.search_box.returnPressed.connect(self.start_search)
        self.search_box.textChanged.connect(self._on_search_text_changed)
        QShortcut("Ctrl+L", self, self.crumbs.edit_path)
        QShortcut("Escape", self.search_box, self._exit_search_if_active,
                  context=Qt.WidgetShortcut)

    # -- small helpers -----------------------------------------------------

    def active_view(self):
        """The currently visible right-pane view."""
        return (self.icon_view if self.view_stack.currentIndex() == 0
                else self.details_view)

    def set_view_mode(self, mode):
        self.view_stack.setCurrentIndex(0 if mode == "icons" else 1)
        (self.act_icons if mode == "icons" else self.act_details).setChecked(True)
        self._update_status()

    def toggle_preview(self):
        self.preview.setVisible(self.act_preview.isChecked())
        if self.act_preview.isChecked():
            self._refresh_preview()

    def toggle_hidden(self):
        show = self.act_hidden.isChecked()
        filt = QDir.AllEntries | QDir.NoDotAndDotDot
        if show:
            filt |= QDir.Hidden
        self.right_model.setFilter(filt)
        tree_filt = QDir.Dirs | QDir.NoDotAndDotDot
        if show:
            tree_filt |= QDir.Hidden
        self.left_model.setFilter(tree_filt)
        self._update_status()

    def zoom(self, delta):
        self.icon_size = max(32, min(256, self.icon_size + delta))
        self.icon_view.setIconSize(QSize(self.icon_size, self.icon_size))
        self.icon_view.setGridSize(QSize(self.icon_size + 36,
                                        self.icon_size + 52))

    def refresh(self):
        """Force the right pane to re-read the current folder."""
        if self.searching:
            self.start_search()
            return
        idx = self.right_model.index(self.current_path)
        self.icon_view.setRootIndex(idx)
        self.details_view.setRootIndex(idx)
        self._update_status()

    # -- navigation --------------------------------------------------------

    def navigate(self, path):
        """Go to *path*, pushing the current folder onto the back stack."""
        path = os.path.abspath(os.path.expanduser(path))
        if not os.path.isdir(path):
            QMessageBox.warning(self, APP_NAME,
                                f"Folder not found:\n{path}")
            return
        if self.searching:
            self._exit_search(silent=True)
        if path != self.current_path:
            self.back_stack.append(self.current_path)
            self.forward_stack.clear()
        self._goto(path)

    def _goto(self, path):
        """Switch the panes to *path* without touching history stacks."""
        self.current_path = path
        idx = self.right_model.index(path)
        self.icon_view.setRootIndex(idx)
        self.details_view.setRootIndex(idx)
        self.crumbs.set_path(path)
        self.actions["back"].setEnabled(bool(self.back_stack))
        self.actions["forward"].setEnabled(bool(self.forward_stack))
        self.actions["up"].setEnabled(path != "/")
        # keep the tree roughly in sync (expand to the folder when known)
        tree_idx = self.left_model.index(path)
        if tree_idx.isValid():
            self.tree.scrollTo(tree_idx)
        self._update_status()
        self._refresh_preview()

    def go_back(self):
        if self.searching:
            self._exit_search()
            return
        if not self.back_stack:
            return
        self.forward_stack.append(self.current_path)
        self._goto(self.back_stack.pop())

    def go_forward(self):
        if not self.forward_stack:
            return
        self.back_stack.append(self.current_path)
        self._goto(self.forward_stack.pop())

    def go_up(self):
        parent = os.path.dirname(self.current_path.rstrip("/")) or "/"
        if parent != self.current_path:
            self.navigate(parent)

    def go_home(self):
        self.navigate(QDir.homePath())

    def _on_tree_clicked(self, index):
        path = self.left_model.filePath(index)
        if path and os.path.isdir(path):
            self.navigate(path)

    # -- selection / status / preview --------------------------------------

    def _selected_paths(self):
        """Absolute paths of the current selection in the active view."""
        view = self.active_view()
        sel = view.selectionModel()
        if sel is None:
            return []
        if self.searching:
            paths = []
            for idx in sel.selectedRows(0):
                path = idx.data(Qt.UserRole + 1)
                if path:
                    paths.append(path)
            return paths
        model = view.model()
        return [model.filePath(i) for i in sel.selectedRows()
                if model.filePath(i)]

    def _update_status(self):
        view = self.active_view()
        if self.searching:
            count = self.results_model.rowCount()
            self.lbl_items.setText(
                f"{count} result(s)" + (" (capped)" if count >= SEARCH_RESULT_CAP else ""))
        else:
            count = self.right_model.rowCount(view.rootIndex())
            self.lbl_items.setText(f"{count} item(s)")
        paths = self._selected_paths()
        if paths:
            total = 0
            for p in paths:
                try:
                    if os.path.isfile(p) and not os.path.islink(p):
                        total += os.path.getsize(p)
                except OSError:
                    pass
            self.lbl_sel.setText(f"{len(paths)} selected ({fmt_size(total)})")
        else:
            self.lbl_sel.setText("")

    def _on_selection_changed(self, _selected=None, _deselected=None):
        self._update_status()
        self._refresh_preview()

    def _refresh_preview(self):
        if not self.act_preview.isChecked() or not self.preview.isVisible():
            return
        paths = self._selected_paths()
        if not paths:
            self.preview_title.setText("No selection")
            self.preview_image.clear()
            self.preview_text.clear()
            self.preview_meta.setText("Select a file to preview it here.")
            return
        path = paths[0]
        name = os.path.basename(path) or path
        self.preview_title.setText(name)
        self.preview_image.clear()
        self.preview_text.clear()
        try:
            if os.path.isdir(path):
                try:
                    n = len(os.listdir(path))
                except OSError:
                    n = "?"
                self.preview_meta.setText(
                    f"Folder\n{n} item(s)\n{fmt_perms(path)}")
            elif is_image(path):
                pixmap = thumbnail(path, size=320)
                if pixmap:
                    self.preview_image.setPixmap(
                        pixmap.scaled(300, 220, Qt.KeepAspectRatio,
                                     Qt.SmoothTransformation))
                self.preview_meta.setText(
                    f"{kind_of(path)}\n{fmt_size(os.path.getsize(path))}")
            elif is_text_file(path):
                lines = []
                try:
                    with open(path, "r", errors="replace") as fh:
                        for _ in range(50):
                            line = fh.readline()
                            if not line:
                                break
                            lines.append(line.rstrip("\n"))
                except OSError as e:
                    lines = [f"<cannot read: {e}>"]
                self.preview_text.setPlainText("\n".join(lines))
                self.preview_meta.setText(
                    f"{kind_of(path)}\n{fmt_size(os.path.getsize(path))}")
            else:
                self.preview_meta.setText(
                    f"{kind_of(path)}\n{fmt_size(os.path.getsize(path))}\n"
                    f"Modified: {self._mtime_str(path)}\n{fmt_perms(path)}")
        except OSError as e:
            self.preview_meta.setText(f"Cannot preview:\n{e}")

    @staticmethod
    def _mtime_str(path):
        try:
            from datetime import datetime
            return datetime.fromtimestamp(
                os.path.getmtime(path)).strftime("%Y-%m-%d %H:%M")
        except OSError:
            return "—"

    # -- open ---------------------------------------------------------------

    def _on_double_clicked(self, index):
        if self.searching:
            path = index.data(Qt.UserRole + 1)
            if not path:
                path = index.sibling(index.row(), 0).data(Qt.UserRole + 1)
        else:
            path = self.right_model.filePath(index)
        if not path:
            return
        if os.path.isdir(path):
            self.navigate(path)
        else:
            self.open_path(path)

    def open_path(self, path):
        try:
            if not QDesktopServices.openUrl(QUrl.fromLocalFile(path)):
                QMessageBox.warning(self, APP_NAME,
                                    f"Could not open:\n{path}")
        except Exception as e:  # never crash on a bad open
            QMessageBox.warning(self, APP_NAME, f"Could not open:\n{e}")

    def open_selection(self):
        paths = self._selected_paths()
        if not paths:
            return
        path = paths[0]
        if os.path.isdir(path):
            self.navigate(path)
        else:
            self.open_path(path)

    # -- file operations -----------------------------------------------------

    def new_folder(self):
        """Create a new folder in the current directory, then rename inline."""
        if self.searching:
            self._exit_search()
        try:
            base = "New folder"
            target = unique_path(self.current_path, base)
            os.mkdir(target)
        except OSError as e:
            QMessageBox.warning(self, APP_NAME,
                                f"Could not create folder:\n{e}")
            return
        # the model may need a moment to notice the new folder
        QTimer.singleShot(400, lambda: self._begin_inline_edit(target))

    def _begin_inline_edit(self, path):
        if self.searching:
            return
        view = self.active_view()
        index = self.right_model.index(path)
        if not index.isValid():
            return
        view.setCurrentIndex(index)
        view.scrollTo(index)
        view.edit(index)

    def rename_selection(self):
        paths = self._selected_paths()
        if len(paths) != 1 or self.searching:
            return
        self._begin_inline_edit(paths[0])

    def copy_selection(self):
        paths = self._selected_paths()
        if not paths:
            return
        self.clipboard = [(p, False) for p in paths]
        self.statusBar().showMessage(
            f"Copied {len(paths)} item(s) — paste into a folder.", 4000)

    def cut_selection(self):
        paths = self._selected_paths()
        if not paths:
            return
        self.clipboard = [(p, True) for p in paths]
        self.statusBar().showMessage(
            f"Cut {len(paths)} item(s) — paste to move them.", 4000)

    def paste(self):
        """Paste the internal clipboard into the current folder."""
        if not self.clipboard:
            QMessageBox.information(self, APP_NAME, "Clipboard is empty.")
            return
        if self.searching:
            self._exit_search()
        dest_dir = self.current_path
        done, errors = 0, []
        for src, is_cut in list(self.clipboard):
            try:
                if not os.path.lexists(src):
                    errors.append(f"{src}: no longer exists")
                    continue
                name = os.path.basename(src.rstrip("/"))
                target = unique_path(dest_dir, name)
                if is_cut:
                    src_abs = os.path.abspath(src)
                    # refuse to move a folder into itself
                    if os.path.abspath(target).startswith(src_abs + os.sep):
                        errors.append(f"{name}: cannot move a folder into itself")
                        continue
                    if src_abs == os.path.abspath(target):
                        continue  # pasting where it already is
                    shutil.move(src, target)
                else:
                    if os.path.isdir(src) and not os.path.islink(src):
                        shutil.copytree(src, target)
                    else:
                        shutil.copy2(src, target)
                done += 1
            except OSError as e:
                errors.append(f"{name}: {e.strerror or e}")
            except Exception as e:  # e.g. shutil.Error
                errors.append(f"{name}: {e}")
        # cut entries are consumed by a paste; copied entries stay available
        self.clipboard = [(p, c) for p, c in self.clipboard if not c]
        self.refresh()
        msg = f"Pasted {done} item(s)."
        if errors:
            msg += "\n\nProblems:\n" + "\n".join(errors[:10])
            QMessageBox.warning(self, APP_NAME, msg)
        else:
            self.statusBar().showMessage(msg, 4000)

    def delete_selection(self):
        paths = self._selected_paths()
        if not paths:
            return
        names = "\n".join(os.path.basename(p) or p for p in paths[:8])
        if len(paths) > 8:
            names += f"\n… and {len(paths) - 8} more"
        if HAVE_SEND2TRASH:
            ok = QMessageBox.question(
                self, "Move to Trash?",
                f"Move {len(paths)} item(s) to the Trash?\n\n{names}",
                QMessageBox.Yes | QMessageBox.No) == QMessageBox.Yes
            if not ok:
                return
            failed = []
            for p in paths:
                try:
                    send2trash(p)
                except Exception as e:
                    failed.append(f"{os.path.basename(p)}: {e}")
            if failed:
                QMessageBox.warning(self, APP_NAME,
                                    "Some items could not be trashed:\n" +
                                    "\n".join(failed[:10]))
        else:
            ok = QMessageBox.warning(
                self, "Delete permanently?",
                "The 'send2trash' package is not installed, so the selected "
                f"{len(paths)} item(s) will be PERMANENTLY deleted and cannot "
                f"be recovered.\n\n{names}\n\nContinue?",
                QMessageBox.Ok | QMessageBox.Cancel) == QMessageBox.Ok
            if not ok:
                return
            failed = []
            for p in paths:
                try:
                    if os.path.isdir(p) and not os.path.islink(p):
                        shutil.rmtree(p)
                    else:
                        os.remove(p)
                except OSError as e:
                    failed.append(f"{os.path.basename(p)}: "
                                  f"{e.strerror or e}")
            if failed:
                QMessageBox.warning(self, APP_NAME,
                                    "Some items could not be deleted:\n" +
                                    "\n".join(failed[:10]))
        if self.searching:
            self.start_search()  # refresh results
        else:
            self.refresh()

    def show_properties(self):
        paths = self._selected_paths()
        if not paths:
            # fall back to the tree selection
            index = self.tree.currentIndex()
            if index.isValid():
                paths = [self.left_model.filePath(index)]
        if not paths:
            return
        PropertiesDialog(paths[0], self).exec_()

    # -- search ---------------------------------------------------------------

    def start_search(self):
        """Recursively search the current folder by name (cap: 2000 hits)."""
        text = self.search_box.text().strip()
        if not text:
            self._exit_search()
            return
        self.searching = True
        self.search_base = self.current_path
        needle = text.lower()
        self.results_model.removeRows(0, self.results_model.rowCount())
        self.results_model.setSortRole(Qt.UserRole)
        from PyQt5.QtGui import QStandardItem
        filt = (QDir.AllEntries | QDir.NoDotAndDotDot | QDir.Hidden
                | QDir.System)
        it = QDirIterator(self.search_base, filt,
                          QDirIterator.Subdirectories)
        count = 0
        while it.hasNext() and count < SEARCH_RESULT_CAP:
            path = it.next()
            if needle not in os.path.basename(path).lower():
                continue
            is_dir = os.path.isdir(path)
            base = os.path.basename(path) or path
            name_item = QStandardItem(base)
            name_item.setData(base.lower(), Qt.UserRole)  # sort key
            name_item.setData(path, Qt.UserRole + 1)      # full path
            if is_dir:
                name_item.setIcon(
                    self.icon_provider.icon(QFileIconProvider.Folder))
            else:
                pixmap = thumbnail(path) if is_image(path) else None
                if pixmap is not None:
                    name_item.setIcon(QIcon(pixmap))
                else:
                    name_item.setIcon(
                        self.icon_provider.icon(QFileInfo(path)))
            name_item.setEditable(False)

            if is_dir:
                size_item = QStandardItem("")
                size_item.setData(-1, Qt.UserRole)
                type_item = QStandardItem("Folder")
            else:
                try:
                    size = os.path.getsize(path)
                except OSError:
                    size = -1
                size_item = QStandardItem(
                    fmt_size(size) if size >= 0 else "—")
                size_item.setData(size, Qt.UserRole)
                type_item = QStandardItem(kind_of(path))
            size_item.setEditable(False)
            type_item.setEditable(False)
            type_item.setData(type_item.text(), Qt.UserRole)

            mtime_item = QStandardItem(self._mtime_str(path))
            try:
                mtime_item.setData(os.path.getmtime(path), Qt.UserRole)
            except OSError:
                mtime_item.setData(0, Qt.UserRole)
            mtime_item.setEditable(False)

            self.results_model.appendRow(
                [name_item, size_item, type_item, mtime_item])
            count += 1
        for view in (self.icon_view, self.details_view):
            view.setModel(self.results_model)
            view.setRootIndex(QModelIndex())
        self._update_status()
        self.statusBar().showMessage(
            f"Search: {count} result(s) for '{text}' in {self.search_base} "
            f"(Esc to clear)", 6000)

    def _on_search_text_changed(self, text):
        if not text and self.searching:
            self._exit_search()

    def _exit_search_if_active(self):
        if self.searching:
            self.search_box.clear()  # triggers _exit_search via textChanged

    def _exit_search(self, silent=False):
        if not self.searching:
            return
        self.searching = False
        for view in (self.icon_view, self.details_view):
            view.setModel(self.right_model)
        if not silent:
            self.search_box.clear()
        self._goto(self.current_path)

    # -- context menus ----------------------------------------------------------

    def _view_context_menu(self, pos):
        view = self.active_view()
        index = view.indexAt(pos)
        if index.isValid():
            view.setCurrentIndex(index)
            sel = view.selectionModel()
            if not sel.isSelected(index):
                sel.select(index, QItemSelectionModel.ClearAndSelect |
                           QItemSelectionModel.Rows)
        menu = QMenu(self)
        A = self.actions
        if index.isValid():
            menu.addAction(A["open"])
            menu.addSeparator()
        menu.addAction(A["new_folder"])
        menu.addSeparator()
        for name in ("cut", "copy", "paste", "delete", "rename"):
            menu.addAction(A[name])
        menu.addSeparator()
        menu.addAction(A["properties"])
        menu.exec_(view.viewport().mapToGlobal(pos))

    def _tree_context_menu(self, pos):
        index = self.tree.indexAt(pos)
        menu = QMenu(self)
        A = self.actions
        if index.isValid():
            self.tree.setCurrentIndex(index)
            path = self.left_model.filePath(index)
            open_act = QAction("Open", self)
            open_act.triggered.connect(lambda: self.navigate(path))
            menu.addAction(open_act)
            menu.addSeparator()
            new_act = QAction("New folder here", self)
            new_act.triggered.connect(
                lambda: self._new_folder_in(path))
            menu.addAction(new_act)
            menu.addSeparator()
        menu.addAction(A["refresh"])
        menu.addSeparator()
        menu.addAction(A["properties"])
        menu.exec_(self.tree.viewport().mapToGlobal(pos))

    def _new_folder_in(self, folder):
        try:
            target = unique_path(folder, "New folder")
            os.mkdir(target)
            self.navigate(folder)
        except OSError as e:
            QMessageBox.warning(self, APP_NAME,
                                f"Could not create folder:\n{e}")

    # -- about ------------------------------------------------------------------

    def show_about(self):
        QMessageBox.about(
            self, f"About {APP_NAME}",
            f"<b>{APP_NAME} {APP_VERSION}</b><br>"
            "made for Mahar OS<br><br>"
            "A lightweight, Windows-like two-pane file manager.")


# ---------------------------------------------------------------------------
# Entry point
# ---------------------------------------------------------------------------

def main():
    app = QApplication(sys.argv)
    app.setApplicationName(APP_NAME)
    app.setOrganizationName("Mahar OS")
    window = MaharFiles()
    window.show()
    sys.exit(app.exec_())


if __name__ == "__main__":
    main()
