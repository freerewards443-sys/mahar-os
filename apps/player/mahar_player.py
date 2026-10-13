#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
Mahar Player 1.0
================
A dark-themed media player for Mahar OS, built with PyQt5 and
python-vlc (libVLC backend).

Features:
    - Playback of MP4 / MKV / AVI / MP3 (anything libVLC supports)
    - Play/Pause, Stop, Previous/Next, seek slider with time labels
    - Volume slider + mute toggle, fullscreen (button, double-click, F11)
    - Playlist with add files / add folder / remove / clear, drag & drop,
      shuffle, repeat (off/all/one), auto-advance, persisted via QSettings
    - Subtitle file loading + subtitle track switching
    - Playback speed control (0.5x - 2x)
    - Dark theme with blue accent (#1a73e8)
"""

import os
import random
import sys

# ----------------------------------------------------------------------
# Graceful dependency check: python-vlc (and libVLC) must be present.
# ----------------------------------------------------------------------
try:
    import vlc  # type: ignore
except Exception:
    from PyQt5.QtWidgets import QApplication, QMessageBox  # noqa: F401
    _app = QApplication(sys.argv)
    QMessageBox.critical(
        None,
        "Mahar Player - Missing dependency",
        "The libVLC backend could not be found.\n\n"
        "Please install the required packages:\n"
        "    sudo apt install vlc python3-vlc\n\n"
        "Mahar Player will now exit.",
    )
    sys.exit(1)

from PyQt5.QtCore import (
    QSettings,
    Qt,
    QTimer,
    QUrl,
)
from PyQt5.QtGui import QIcon, QKeySequence
from PyQt5.QtWidgets import (
    QAction,
    QActionGroup,
    QApplication,
    QDockWidget,
    QFileDialog,
    QFrame,
    QHBoxLayout,
    QLabel,
    QListWidget,
    QListWidgetItem,
    QMainWindow,
    QMenu,
    QMessageBox,
    QPushButton,
    QShortcut,
    QSizePolicy,
    QSlider,
    QStatusBar,
    QToolBar,
    QVBoxLayout,
    QWidget,
)

# ----------------------------------------------------------------------
# Constants
# ----------------------------------------------------------------------
ORG_NAME = "MaharOS"
APP_NAME = "MaharPlayer"
WINDOW_TITLE = "Mahar Player"
ACCENT = "#1a73e8"
ICON_PATH = "/usr/share/icons/hicolor/256x256/apps/mahar-player.png"

MEDIA_FILTER = (
    "Media files (*.mp4 *.mkv *.avi *.mov *.webm *.flv *.wmv *.mpg *.mpeg "
    "*.m4v *.3gp *.mp3 *.ogg *.oga *.wav *.flac *.m4a *.aac *.wma *.opus);;"
    "Video files (*.mp4 *.mkv *.avi *.mov *.webm *.flv *.wmv *.mpg *.mpeg "
    "*.m4v *.3gp);;"
    "Audio files (*.mp3 *.ogg *.oga *.wav *.flac *.m4a *.aac *.wma *.opus);;"
    "All files (*.*)"
)

SUBTITLE_FILTER = (
    "Subtitle files (*.srt *.ass *.ssa *.sub *.vtt);;"
    "All files (*.*)"
)

MEDIA_EXTENSIONS = {
    ".mp4", ".mkv", ".avi", ".mov", ".webm", ".flv", ".wmv",
    ".mpg", ".mpeg", ".m4v", ".3gp", ".ts", ".m2ts", ".ogv",
    ".mp3", ".ogg", ".oga", ".wav", ".flac", ".m4a", ".aac",
    ".wma", ".opus", ".mid", ".midi",
}

SUBTITLE_EXTENSIONS = {".srt", ".ass", ".ssa", ".sub", ".vtt", ".idx"}

SPEEDS = [
    ("0.5x", 0.5),
    ("1x", 1.0),
    ("1.25x", 1.25),
    ("1.5x", 1.5),
    ("2x", 2.0),
]

DARK_QSS = """
QMainWindow, QWidget {{
    background-color: #14161c;
    color: #e8eaf0;
    font-size: 13px;
}}
QMenuBar {{ background-color: #1b1e26; }}
QMenuBar::item:selected {{ background-color: #2a2e38; }}
QMenu {{
    background-color: #1b1e26;
    border: 1px solid #2a2e38;
    padding: 4px;
}}
QMenu::item {{ padding: 6px 24px; }}
QMenu::item:selected {{ background-color: {accent}; color: white; }}
QMenu::separator {{ height: 1px; background: #2a2e38; margin: 4px 0; }}
QToolBar {{
    background-color: #1b1e26;
    border: none;
    spacing: 4px;
    padding: 4px;
}}
QPushButton {{
    background-color: #23262f;
    border: 1px solid #32363f;
    border-radius: 6px;
    padding: 6px 12px;
    color: #e8eaf0;
}}
QPushButton:hover {{ border-color: {accent}; }}
QPushButton:pressed {{ background-color: {accent}; color: white; }}
QPushButton#playButton {{
    background-color: {accent};
    color: white;
    font-weight: bold;
    min-width: 64px;
    font-size: 14px;
}}
QPushButton#playButton:hover {{ background-color: #2f86ec; }}
QListWidget {{
    background-color: #101218;
    border: 1px solid #2a2e38;
    border-radius: 6px;
    alternate-background-color: #151821;
}}
QListWidget::item {{ padding: 6px 8px; }}
QListWidget::item:selected {{
    background-color: {accent};
    color: white;
    border-radius: 4px;
}}
QListWidget::item:hover {{ background-color: #23262f; }}
QSlider::groove:horizontal {{
    height: 6px;
    background: #2a2e38;
    border-radius: 3px;
}}
QSlider::handle:horizontal {{
    width: 14px; height: 14px;
    margin: -4px 0;
    background: {accent};
    border-radius: 7px;
}}
QSlider::sub-page:horizontal {{ background: {accent}; border-radius: 3px; }}
QLabel {{ color: #e8eaf0; }}
QLabel#timeLabel {{ color: #9aa0ae; font-family: monospace; }}
QDockWidget {{ titlebar-close-icon: none; }}
QDockWidget::title {{
    background-color: #1b1e26;
    padding: 6px;
    font-weight: bold;
}}
QStatusBar {{
    background-color: #1b1e26;
    color: #9aa0ae;
}}
QFrame#videoFrame {{
    background-color: #000000;
    border: none;
}}
QToolTip {{
    background-color: #23262f;
    color: #e8eaf0;
    border: 1px solid #32363f;
}}
""".format(accent=ACCENT)


def format_time(ms):
    """Format milliseconds as mm:ss (or h:mm:ss for long media)."""
    if ms is None or ms < 0:
        ms = 0
    total = ms // 1000
    hours, rest = divmod(total, 3600)
    minutes, seconds = divmod(rest, 60)
    if hours:
        return "%d:%02d:%02d" % (hours, minutes, seconds)
    return "%02d:%02d" % (minutes, seconds)


def is_media_file(path):
    """Return True if the path has a known media extension."""
    return os.path.splitext(path)[1].lower() in MEDIA_EXTENSIONS


def is_subtitle_file(path):
    """Return True if the path has a known subtitle extension."""
    return os.path.splitext(path)[1].lower() in SUBTITLE_EXTENSIONS


# ----------------------------------------------------------------------
# Video widget (double-click toggles fullscreen)
# ----------------------------------------------------------------------
class VideoFrame(QFrame):
    """Black video surface that forwards double-click to the player."""

    def __init__(self, player_window, parent=None):
        super().__init__(parent)
        self.setObjectName("videoFrame")
        self.setFrameShape(QFrame.NoFrame)
        self.setSizePolicy(QSizePolicy.Expanding, QSizePolicy.Expanding)
        self.player_window = player_window

    def mouseDoubleClickEvent(self, event):  # noqa: N802
        self.player_window.toggle_fullscreen()
        event.accept()


# ----------------------------------------------------------------------
# Main window
# ----------------------------------------------------------------------
class MaharPlayer(QMainWindow):
    """Main application window for Mahar Player."""

    def __init__(self):
        super().__init__()
        self.setWindowTitle(WINDOW_TITLE)
        self.resize(1000, 640)
        if os.path.exists(ICON_PATH):
            self.setWindowIcon(QIcon(ICON_PATH))

        self.setAcceptDrops(True)
        self.settings = QSettings(ORG_NAME, APP_NAME)

        # -- libVLC ----------------------------------------------------
        self.instance = vlc.Instance()
        self.player = self.instance.media_player_new()
        self.events = self.player.event_manager()
        self.events.event_attach(
            vlc.EventType.MediaPlayerEndReached, self._on_end_reached)
        self.events.event_attach(
            vlc.EventType.MediaPlayerEncounteredError, self._on_error)
        self.events.event_attach(
            vlc.EventType.MediaPlayerPlaying, self._on_state_event)
        self.events.event_attach(
            vlc.EventType.MediaPlayerPaused, self._on_state_event)
        self.events.event_attach(
            vlc.EventType.MediaPlayerStopped, self._on_state_event)

        # -- playback state --------------------------------------------
        self.playlist = []          # list of absolute file paths
        self.current_index = -1
        self.shuffle = False
        self.repeat_mode = 0        # 0=off, 1=all, 2=one
        self._seeking = False       # True while the user drags the seek bar
        self._rate = 1.0

        self._build_ui()
        self._build_menus()
        self._build_shortcuts()
        self._restore_session()

        # -- position polling timer (250 ms) ---------------------------
        self.timer = QTimer(self)
        self.timer.setInterval(250)
        self.timer.timeout.connect(self._update_position)
        self.timer.start()

    # ==================================================================
    # UI construction
    # ==================================================================
    def _build_ui(self):
        """Build the central video area, controls, playlist and status bar."""
        central = QWidget(self)
        self.setCentralWidget(central)
        layout = QVBoxLayout(central)
        layout.setContentsMargins(0, 0, 0, 0)
        layout.setSpacing(0)

        # Video surface ------------------------------------------------
        self.video_frame = VideoFrame(self)
        layout.addWidget(self.video_frame, 1)

        # Controls bar -------------------------------------------------
        controls = QWidget(self)
        controls_layout = QHBoxLayout(controls)
        controls_layout.setContentsMargins(8, 6, 8, 6)
        controls_layout.setSpacing(6)

        self.prev_button = QPushButton("⏮", self)
        self.prev_button.setToolTip("Previous track")
        self.prev_button.clicked.connect(self.play_previous)
        controls_layout.addWidget(self.prev_button)

        self.play_button = QPushButton("▶", self)
        self.play_button.setObjectName("playButton")
        self.play_button.setToolTip("Play / Pause (Space)")
        self.play_button.clicked.connect(self.toggle_play)
        controls_layout.addWidget(self.play_button)

        self.stop_button = QPushButton("⏹", self)
        self.stop_button.setToolTip("Stop")
        self.stop_button.clicked.connect(self.stop)
        controls_layout.addWidget(self.stop_button)

        self.next_button = QPushButton("⏭", self)
        self.next_button.setToolTip("Next track")
        self.next_button.clicked.connect(self.play_next)
        controls_layout.addWidget(self.next_button)

        self.time_current = QLabel("00:00", self)
        self.time_current.setObjectName("timeLabel")
        controls_layout.addWidget(self.time_current)

        self.seek_slider = QSlider(Qt.Horizontal, self)
        self.seek_slider.setRange(0, 1000)
        self.seek_slider.setToolTip("Seek")
        self.seek_slider.sliderPressed.connect(self._seek_pressed)
        self.seek_slider.sliderReleased.connect(self._seek_released)
        controls_layout.addWidget(self.seek_slider, 1)

        self.time_total = QLabel("00:00", self)
        self.time_total.setObjectName("timeLabel")
        controls_layout.addWidget(self.time_total)

        self.mute_button = QPushButton("🔊", self)
        self.mute_button.setToolTip("Mute / Unmute (M)")
        self.mute_button.clicked.connect(self.toggle_mute)
        controls_layout.addWidget(self.mute_button)

        self.volume_slider = QSlider(Qt.Horizontal, self)
        self.volume_slider.setRange(0, 125)
        self.volume_slider.setFixedWidth(100)
        self.volume_slider.setToolTip("Volume")
        self.volume_slider.setValue(self.player.audio_get_volume())
        self.volume_slider.valueChanged.connect(self._volume_changed)
        controls_layout.addWidget(self.volume_slider)

        self.fullscreen_button = QPushButton("⛶", self)
        self.fullscreen_button.setToolTip("Fullscreen (F11)")
        self.fullscreen_button.clicked.connect(self.toggle_fullscreen)
        controls_layout.addWidget(self.fullscreen_button)

        layout.addWidget(controls)

        # Playlist dock (right side) -----------------------------------
        dock = QDockWidget("Playlist", self)
        dock.setAllowedAreas(Qt.LeftDockWidgetArea | Qt.RightDockWidgetArea)
        dock.setFeatures(
            QDockWidget.DockWidgetMovable | QDockWidget.DockWidgetFloatable)

        dock_widget = QWidget(self)
        dock_layout = QVBoxLayout(dock_widget)
        dock_layout.setContentsMargins(6, 6, 6, 6)

        self.playlist_view = QListWidget(self)
        self.playlist_view.setAlternatingRowColors(True)
        self.playlist_view.setSelectionMode(QListWidget.ExtendedSelection)
        self.playlist_view.itemDoubleClicked.connect(self._playlist_double_clicked)
        dock_layout.addWidget(self.playlist_view, 1)

        playlist_buttons = QHBoxLayout()
        playlist_buttons.setSpacing(4)

        self.shuffle_button = QPushButton("🔀", self)
        self.shuffle_button.setCheckable(True)
        self.shuffle_button.setToolTip("Shuffle")
        self.shuffle_button.toggled.connect(self._shuffle_toggled)
        playlist_buttons.addWidget(self.shuffle_button)

        self.repeat_button = QPushButton("🔁", self)
        self.repeat_button.setCheckable(True)
        self.repeat_button.setToolTip("Repeat: off → all → one")
        self.repeat_button.clicked.connect(self._cycle_repeat)
        playlist_buttons.addWidget(self.repeat_button)

        playlist_buttons.addStretch(1)
        dock_layout.addLayout(playlist_buttons)

        dock.setWidget(dock_widget)
        self.addDockWidget(Qt.RightDockWidgetArea, dock)
        self.playlist_dock = dock

        # Status bar ---------------------------------------------------
        self.status_bar = QStatusBar(self)
        self.setStatusBar(self.status_bar)
        self.status_bar.showMessage("Ready — open a media file to begin.")

    def _build_menus(self):
        """Build the File / Playback / Subtitles / Playlist menus."""
        menubar = self.menuBar()

        # -- File -------------------------------------------------------
        file_menu = menubar.addMenu("&File")

        open_file = QAction("&Open file…", self)
        open_file.setShortcut(QKeySequence.Open)
        open_file.triggered.connect(self.open_file_dialog)
        file_menu.addAction(open_file)

        open_folder = QAction("Open &folder…", self)
        open_folder.triggered.connect(self.open_folder_dialog)
        file_menu.addAction(open_folder)

        file_menu.addSeparator()

        exit_action = QAction("E&xit", self)
        exit_action.setShortcut(QKeySequence.Quit)
        exit_action.triggered.connect(self.close)
        file_menu.addAction(exit_action)

        # -- Playback ---------------------------------------------------
        playback_menu = menubar.addMenu("&Playback")

        self.play_pause_action = QAction("Play / Pause", self)
        self.play_pause_action.setShortcut(QKeySequence("Space"))
        self.play_pause_action.triggered.connect(self.toggle_play)
        playback_menu.addAction(self.play_pause_action)

        stop_action = QAction("Stop", self)
        stop_action.setShortcut(QKeySequence("S"))
        stop_action.triggered.connect(self.stop)
        playback_menu.addAction(stop_action)

        playback_menu.addSeparator()

        prev_action = QAction("Previous track", self)
        prev_action.setShortcut(QKeySequence("P"))
        prev_action.triggered.connect(self.play_previous)
        playback_menu.addAction(prev_action)

        next_action = QAction("Next track", self)
        next_action.setShortcut(QKeySequence("N"))
        next_action.triggered.connect(self.play_next)
        playback_menu.addAction(next_action)

        playback_menu.addSeparator()

        fullscreen_action = QAction("Fullscreen", self)
        fullscreen_action.setShortcut(QKeySequence("F11"))
        fullscreen_action.triggered.connect(self.toggle_fullscreen)
        playback_menu.addAction(fullscreen_action)

        # Speed submenu ------------------------------------------------
        speed_menu = playback_menu.addMenu("Playback &speed")
        speed_group = QActionGroup(self)
        speed_group.setExclusive(True)
        for label, rate in SPEEDS:
            action = QAction(label, self)
            action.setCheckable(True)
            action.setChecked(rate == 1.0)
            action.triggered.connect(
                lambda checked=False, r=rate: self.set_speed(r))
            speed_group.addAction(action)
            speed_menu.addAction(action)

        # -- Subtitles --------------------------------------------------
        subtitles_menu = menubar.addMenu("&Subtitles")

        load_sub = QAction("Load subtitle &file…", self)
        load_sub.triggered.connect(self.load_subtitle_dialog)
        subtitles_menu.addAction(load_sub)

        self.subtitle_track_menu = subtitles_menu.addMenu("Subtitle &track")
        subtitles_menu.aboutToShow.connect(self._refresh_subtitle_tracks)

        disable_sub = QAction("&Disable subtitles", self)
        disable_sub.triggered.connect(lambda: self.player.video_set_spu(-1))
        subtitles_menu.addAction(disable_sub)

        # -- Playlist ---------------------------------------------------
        playlist_menu = menubar.addMenu("P&laylist")

        add_files = QAction("&Add files…", self)
        add_files.triggered.connect(self.add_files_dialog)
        playlist_menu.addAction(add_files)

        add_folder = QAction("Add &folder…", self)
        add_folder.triggered.connect(self.add_folder_dialog)
        playlist_menu.addAction(add_folder)

        playlist_menu.addSeparator()

        remove_action = QAction("&Remove selected", self)
        remove_action.setShortcut(QKeySequence.Delete)
        remove_action.triggered.connect(self.remove_selected)
        playlist_menu.addAction(remove_action)

        clear_action = QAction("&Clear playlist", self)
        clear_action.triggered.connect(self.clear_playlist)
        playlist_menu.addAction(clear_action)

    def _build_shortcuts(self):
        """Keyboard shortcuts not already covered by menu actions."""
        QShortcut(QKeySequence(Qt.Key_Escape), self, self._exit_fullscreen)
        QShortcut(QKeySequence("M"), self, self.toggle_mute)
        QShortcut(QKeySequence(Qt.Key_F), self, self.toggle_fullscreen)

    # ==================================================================
    # Session persistence (QSettings)
    # ==================================================================
    def _restore_session(self):
        """Restore the playlist, volume and flags from the previous run."""
        files = self.settings.value("playlist", [], type=list)
        for path in files:
            if os.path.isfile(path):
                self._append_to_playlist(path)
        last = self.settings.value("last_index", -1, type=int)
        if 0 <= last < len(self.playlist):
            self.current_index = last
            self.playlist_view.setCurrentRow(last)

        volume = self.settings.value("volume", 100, type=int)
        self.volume_slider.setValue(volume)
        self.player.audio_set_volume(volume)

        self.shuffle = self.settings.value("shuffle", False, type=bool)
        self.shuffle_button.setChecked(self.shuffle)
        self.repeat_mode = self.settings.value("repeat", 0, type=int)
        self._update_repeat_button()

    def _save_session(self):
        """Persist the playlist and playback flags."""
        self.settings.setValue("playlist", self.playlist)
        self.settings.setValue("last_index", self.current_index)
        self.settings.setValue("volume", self.volume_slider.value())
        self.settings.setValue("shuffle", self.shuffle)
        self.settings.setValue("repeat", self.repeat_mode)
        self.settings.sync()

    def closeEvent(self, event):  # noqa: N802
        self._save_session()
        self.timer.stop()
        self.player.stop()
        super().closeEvent(event)

    # ==================================================================
    # Playlist management
    # ==================================================================
    def _append_to_playlist(self, path):
        """Add one absolute file path to the playlist."""
        path = os.path.abspath(path)
        self.playlist.append(path)
        item = QListWidgetItem(os.path.basename(path))
        item.setToolTip(path)
        item.setData(Qt.UserRole, path)
        self.playlist_view.addItem(item)

    def add_files_dialog(self):
        """Show the multi-select file dialog and queue chosen files."""
        files, _ = QFileDialog.getOpenFileNames(
            self, "Add media files", os.path.expanduser("~"), MEDIA_FILTER)
        added = 0
        for path in files:
            self._append_to_playlist(path)
            added += 1
        if added:
            self.status_bar.showMessage("%d file(s) added to playlist." % added)
        self._save_session()

    def add_folder_dialog(self):
        """Pick a folder and queue every media file inside it (recursive)."""
        folder = QFileDialog.getExistingDirectory(
            self, "Add folder", os.path.expanduser("~"))
        if folder:
            self.add_folder(folder)

    def add_folder(self, folder):
        """Recursively add all media files found under folder."""
        added = 0
        for root, _dirs, files in os.walk(folder):
            for name in sorted(files):
                path = os.path.join(root, name)
                if is_media_file(path):
                    self._append_to_playlist(path)
                    added += 1
        if added:
            self.status_bar.showMessage(
                "%d file(s) added from %s." % (added, folder))
        self._save_session()

    def open_file_dialog(self):
        """Open one file immediately (adds it to the playlist first)."""
        path, _ = QFileDialog.getOpenFileName(
            self, "Open media file", os.path.expanduser("~"), MEDIA_FILTER)
        if path:
            self._append_to_playlist(path)
            self.play(len(self.playlist) - 1)

    def open_folder_dialog(self):
        """Open a folder: add its media and play the first file."""
        folder = QFileDialog.getExistingDirectory(
            self, "Open folder", os.path.expanduser("~"))
        if folder:
            before = len(self.playlist)
            self.add_folder(folder)
            if len(self.playlist) > before:
                self.play(before)

    def remove_selected(self):
        """Remove the selected playlist entries (keep playback sane)."""
        rows = sorted({i.row() for i in self.playlist_view.selectedIndexes()},
                      reverse=True)
        if not rows:
            return
        for row in rows:
            if row == self.current_index:
                self.stop()
                self.current_index = -1
            elif row < self.current_index:
                self.current_index -= 1
            del self.playlist[row]
            self.playlist_view.takeItem(row)
        self._highlight_current()
        self._save_session()

    def clear_playlist(self):
        """Empty the entire playlist and stop playback."""
        self.stop()
        self.playlist.clear()
        self.playlist_view.clear()
        self.current_index = -1
        self.status_bar.showMessage("Playlist cleared.")
        self._save_session()

    def _playlist_double_clicked(self, item):
        self.play(self.playlist_view.row(item))

    def _highlight_current(self):
        """Bold the currently playing row in the playlist."""
        for row in range(self.playlist_view.count()):
            item = self.playlist_view.item(row)
            font = item.font()
            font.setBold(row == self.current_index)
            item.setFont(font)

    # ==================================================================
    # Playback engine
    # ==================================================================
    def play(self, index):
        """Start playing the playlist entry at index."""
        if not (0 <= index < len(self.playlist)):
            return
        path = self.playlist[index]
        if not os.path.isfile(path):
            QMessageBox.warning(
                self, WINDOW_TITLE,
                "File not found:\n%s" % path)
            return
        self.current_index = index
        media = self.instance.media_new(path)
        self.player.set_media(media)

        # Embed video in the QFrame (Linux: window handle).
        self.player.set_xwindow(self.video_frame.winId())

        self.player.play()
        self.player.set_rate(self._rate)
        self.playlist_view.setCurrentRow(index)
        self._highlight_current()
        self.play_button.setText("⏸")
        self.status_bar.showMessage(
            "Playing: %s" % os.path.basename(path))
        self._save_session()

    def toggle_play(self):
        """Play/Pause toggle; starts the current or first track if idle."""
        state = self.player.get_state()
        if state in (vlc.State.Playing, vlc.State.Buffering):
            self.player.pause()
            self.play_button.setText("▶")
        elif state == vlc.State.Paused:
            self.player.play()
            self.play_button.setText("⏸")
        else:
            if 0 <= self.current_index < len(self.playlist):
                self.play(self.current_index)
            elif self.playlist:
                self.play(0)
            else:
                self.open_file_dialog()

    def stop(self):
        """Stop playback and reset the position display."""
        self.player.stop()
        self.play_button.setText("▶")
        self.seek_slider.setValue(0)
        self.time_current.setText("00:00")
        self.status_bar.showMessage("Stopped.")

    def play_next(self, _checked=False):
        """Advance to the next track (shuffle-aware, wrap-around)."""
        if not self.playlist:
            return
        self.play(self._next_index())

    def play_previous(self, _checked=False):
        """Go to the previous track (wrap-around)."""
        if not self.playlist:
            return
        if self.shuffle:
            index = self._random_index(exclude=self.current_index)
        else:
            index = (self.current_index - 1) % len(self.playlist)
        self.play(index)

    def _next_index(self):
        if self.shuffle:
            return self._random_index(exclude=self.current_index)
        return (self.current_index + 1) % len(self.playlist)

    def _random_index(self, exclude=-1):
        choices = [i for i in range(len(self.playlist)) if i != exclude]
        return random.choice(choices) if choices else exclude

    # -- libVLC event callbacks (run on VLC threads) --------------------
    def _on_end_reached(self, _event):
        """Auto-advance when a track finishes (respect repeat mode)."""
        # Event callbacks run off the GUI thread: marshal back via timer.
        QTimer.singleShot(0, self._handle_end_reached)

    def _handle_end_reached(self):
        if self.repeat_mode == 2 and self.current_index >= 0:  # repeat one
            self.play(self.current_index)
        elif self.repeat_mode == 1 or self.shuffle:            # repeat all
            self.play_next()
        elif self.current_index + 1 < len(self.playlist):
            self.play_next()
        else:
            self.stop()

    def _on_error(self, _event):
        QTimer.singleShot(0, self._show_error)

    def _show_error(self):
        name = (os.path.basename(self.playlist[self.current_index])
                if 0 <= self.current_index < len(self.playlist)
                else "unknown file")
        QMessageBox.critical(
            self, WINDOW_TITLE,
            "Could not play this file:\n%s\n\n"
            "The format may be unsupported or the file may be damaged."
            % name)

    def _on_state_event(self, _event):
        QTimer.singleShot(0, self._refresh_play_button)

    def _refresh_play_button(self):
        state = self.player.get_state()
        if state in (vlc.State.Playing, vlc.State.Buffering):
            self.play_button.setText("⏸")
        else:
            self.play_button.setText("▶")

    # ==================================================================
    # Seek / position
    # ==================================================================
    def _update_position(self):
        """Refresh the seek slider and time labels every 250 ms."""
        if self._seeking:
            return
        length = self.player.get_length()
        time = self.player.get_time()
        if length > 0:
            self.seek_slider.setValue(int(time * 1000 / length))
            self.time_total.setText(format_time(length))
        else:
            self.seek_slider.setValue(0)
            self.time_total.setText("00:00")
        self.time_current.setText(format_time(time))

    def _seek_pressed(self):
        self._seeking = True

    def _seek_released(self):
        self._seeking = False
        length = self.player.get_length()
        if length > 0:
            self.player.set_time(int(
                self.seek_slider.value() * length / 1000))
        self._update_position()

    # ==================================================================
    # Volume / mute
    # ==================================================================
    def _volume_changed(self, value):
        self.player.audio_set_volume(value)
        if value == 0:
            self.player.audio_set_mute(True)
            self.mute_button.setText("🔇")
        else:
            if self.player.audio_get_mute():
                self.player.audio_set_mute(False)
            self.mute_button.setText("🔊")

    def toggle_mute(self):
        """Mute/unmute toggle."""
        muted = self.player.audio_get_mute()
        self.player.audio_set_mute(not muted)
        self.mute_button.setText("🔇" if not muted else "🔊")

    # ==================================================================
    # Fullscreen
    # ==================================================================
    def toggle_fullscreen(self):
        """Toggle fullscreen mode for the whole window."""
        if self.isFullScreen():
            self.showNormal()
        else:
            self.showFullScreen()

    def _exit_fullscreen(self):
        if self.isFullScreen():
            self.showNormal()

    # ==================================================================
    # Shuffle / repeat
    # ==================================================================
    def _shuffle_toggled(self, on):
        self.shuffle = on
        self.shuffle_button.setToolTip(
            "Shuffle (on)" if on else "Shuffle")
        self.status_bar.showMessage(
            "Shuffle %s." % ("enabled" if on else "disabled"))
        self._save_session()

    def _cycle_repeat(self):
        self.repeat_mode = (self.repeat_mode + 1) % 3
        self._update_repeat_button()
        names = {0: "off", 1: "all", 2: "one"}
        self.status_bar.showMessage(
            "Repeat: %s." % names[self.repeat_mode])
        self._save_session()

    def _update_repeat_button(self):
        labels = {0: "🔁", 1: "🔁", 2: "🔂"}
        self.repeat_button.setText(labels[self.repeat_mode])
        self.repeat_button.setChecked(self.repeat_mode != 0)
        self.repeat_button.setToolTip(
            "Repeat: %s" % {0: "off", 1: "all", 2: "one"}[self.repeat_mode])

    # ==================================================================
    # Playback speed
    # ==================================================================
    def set_speed(self, rate):
        """Set the playback rate (0.5x - 2x)."""
        self._rate = rate
        self.player.set_rate(rate)
        label = ("%g" % rate)
        self.status_bar.showMessage("Speed: %sx." % label)

    # ==================================================================
    # Subtitles
    # ==================================================================
    def load_subtitle_dialog(self):
        """Load an external subtitle file for the current media."""
        if self.current_index < 0:
            QMessageBox.information(
                self, WINDOW_TITLE, "Play a file first, then load subtitles.")
            return
        path, _ = QFileDialog.getOpenFileName(
            self, "Load subtitle file", os.path.expanduser("~"),
            SUBTITLE_FILTER)
        if path:
            if self.player.video_set_subtitle_file(path) == 0:
                self.status_bar.showMessage(
                    "Subtitles loaded: %s" % os.path.basename(path))
            else:
                QMessageBox.warning(
                    self, WINDOW_TITLE,
                    "Could not load subtitle file:\n%s" % path)

    def _refresh_subtitle_tracks(self):
        """Rebuild the 'Subtitle track' submenu from available SPU tracks."""
        self.subtitle_track_menu.clear()
        try:
            tracks = self.player.video_get_spu_description()
        except Exception:
            tracks = []
        if not tracks:
            none = QAction("(no subtitle tracks)", self)
            none.setEnabled(False)
            self.subtitle_track_menu.addAction(none)
            return
        current = self.player.video_get_spu()
        for track_id, name in tracks:
            label = name.decode("utf-8", "replace") \
                if isinstance(name, bytes) else str(name)
            action = QAction(label, self)
            action.setCheckable(True)
            action.setChecked(track_id == current)
            action.triggered.connect(
                lambda checked=False, t=track_id:
                self.player.video_set_spu(t))
            self.subtitle_track_menu.addAction(action)

    # ==================================================================
    # Drag & drop
    # ==================================================================
    def dragEnterEvent(self, event):  # noqa: N802
        if event.mimeData().hasUrls():
            event.acceptProposedAction()

    def dropEvent(self, event):  # noqa: N802
        added = 0
        for url in event.mimeData().urls():
            path = url.toLocalFile()
            if not path:
                continue
            if os.path.isdir(path):
                before = len(self.playlist)
                self.add_folder(path)
                added += len(self.playlist) - before
            elif is_media_file(path):
                self._append_to_playlist(path)
                added += 1
            elif is_subtitle_file(path):
                # Dropped subtitle file applies to the current media.
                if self.current_index >= 0:
                    self.player.video_set_subtitle_file(path)
        if added:
            self.status_bar.showMessage(
                "%d file(s) added via drag & drop." % added)
            self._save_session()
        event.acceptProposedAction()


# ----------------------------------------------------------------------
# Entry point
# ----------------------------------------------------------------------
def main():
    """Create the application, apply the theme, and run."""
    app = QApplication(sys.argv)
    app.setApplicationName(APP_NAME)
    app.setOrganizationName(ORG_NAME)
    app.setStyleSheet(DARK_QSS)

    window = MaharPlayer()
    window.show()
    sys.exit(app.exec_())


if __name__ == "__main__":
    main()
