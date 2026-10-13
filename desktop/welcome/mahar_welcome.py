#!/usr/bin/env python3
"""Mahar OS Welcome app — first-run greeter.

Shows the Mahar logo, quick-launch buttons for the Mahar apps,
a small system-info panel, and an About blurb. Dark themed.

Installs to: /usr/local/bin/mahar-welcome (executable)
Launcher:    /usr/share/applications/mahar-welcome.desktop
"""
import os
import platform
import subprocess
import sys

try:
    from PyQt5.QtCore import Qt
    from PyQt5.QtGui import QPixmap
    from PyQt5.QtWidgets import (
        QApplication, QGridLayout, QGroupBox, QHBoxLayout, QLabel,
        QMainWindow, QPushButton, QVBoxLayout, QWidget,
    )
    HAVE_QT = True
except ImportError:
    HAVE_QT = False

# Where the build script installs the Mahar logo artwork.
LOGO_PATHS = [
    "/usr/share/pixmaps/mahar-logo.png",
    "/usr/share/icons/hicolor/256x256/apps/mahar-logo.png",
    "/usr/share/backgrounds/mahar/mahar-logo.png",
]

# (button label, command to launch)
APPS = [
    ("Mahar Browser", "mahar-browser"),
    ("Mahar Files", "mahar-files"),
    ("Mahar Player", "mahar-player"),
    ("Terminal", "xterm"),
]

DARK_QSS = """
QMainWindow, QWidget { background-color: #14171d; color: #e8e8e8; }
QLabel#title { font-size: 26px; font-weight: bold; color: #ffffff; }
QLabel#subtitle { font-size: 13px; color: #9aa0ae; }
QGroupBox { border: 1px solid #3a3f4c; border-radius: 6px;
            margin-top: 12px; font-weight: bold; color: #9aa0ae; }
QGroupBox::title { subcontrol-origin: margin; left: 8px; padding: 0 4px; }
QPushButton { background-color: #1a73e8; color: #ffffff; border: none;
              border-radius: 6px; padding: 10px 14px; font-size: 13px; }
QPushButton:hover { background-color: #2b83f0; }
QPushButton:pressed { background-color: #155fc0; }
QLabel#about { color: #9aa0ae; font-size: 12px; }
"""


def read_cpu_model():
    """Return the CPU model name from /proc/cpuinfo (or 'Unknown')."""
    try:
        with open("/proc/cpuinfo", encoding="utf-8", errors="replace") as fh:
            for line in fh:
                if line.startswith("model name"):
                    return line.split(":", 1)[1].strip()
    except OSError:
        pass
    return "Unknown CPU"


def read_mem_total():
    """Return total RAM as a human string from /proc/meminfo."""
    try:
        with open("/proc/meminfo", encoding="utf-8") as fh:
            for line in fh:
                if line.startswith("MemTotal:"):
                    kb = int(line.split()[1])
                    if kb >= 1024 * 1024:
                        return "%.1f GB" % (kb / 1024 / 1024)
                    return "%.0f MB" % (kb / 1024)
    except OSError:
        pass
    return "Unknown"


def system_info_lines():
    """Collect (label, value) rows for the info panel."""
    uname = platform.uname()
    return [
        ("Operating system", "Mahar OS (Debian-based)"),
        ("Kernel", "%s %s" % (uname.system, uname.release)),
        ("Architecture", uname.machine),
        ("CPU", read_cpu_model()),
        ("Memory", read_mem_total()),
    ]


def launch(command):
    """Launch an app detached from this window; never raise."""
    try:
        subprocess.Popen([command],
                         stdout=subprocess.DEVNULL,
                         stderr=subprocess.DEVNULL,
                         start_new_session=True)
    except OSError as exc:
        print("Could not launch %s: %s" % (command, exc), file=sys.stderr)


class WelcomeWindow(QMainWindow if HAVE_QT else object):
    def __init__(self):
        super().__init__()
        self.setWindowTitle("Welcome to Mahar OS")
        self.setMinimumSize(560, 520)

        root = QWidget()
        layout = QVBoxLayout(root)
        layout.setContentsMargins(28, 24, 28, 24)
        layout.setSpacing(10)

        # --- Logo ---
        logo_label = QLabel(alignment=Qt.AlignCenter)
        pixmap = None
        for path in LOGO_PATHS:
            if os.path.exists(path):
                pixmap = QPixmap(path)
                break
        if pixmap and not pixmap.isNull():
            logo_label.setPixmap(
                pixmap.scaledToHeight(96, Qt.SmoothTransformation))
        else:
            logo_label.setText("\U0001F30A")  # fallback glyph, no crash
            logo_label.setStyleSheet("font-size: 64px;")
        layout.addWidget(logo_label)

        # --- Headings ---
        title = QLabel("Welcome to Mahar OS", objectName="title",
                       alignment=Qt.AlignCenter)
        subtitle = QLabel("A fast, lightweight, Windows-like desktop.",
                          objectName="subtitle", alignment=Qt.AlignCenter)
        layout.addWidget(title)
        layout.addWidget(subtitle)

        # --- Quick-launch buttons ---
        apps_box = QGroupBox("Get started")
        grid = QGridLayout(apps_box)
        for i, (label, cmd) in enumerate(APPS):
            btn = QPushButton(label)
            btn.clicked.connect(lambda _=False, c=cmd: launch(c))
            grid.addWidget(btn, i // 2, i % 2)
        layout.addWidget(apps_box)

        # --- System info ---
        info_box = QGroupBox("This system")
        info_layout = QVBoxLayout(info_box)
        for label, value in system_info_lines():
            row = QHBoxLayout()
            name = QLabel("<b>%s</b>" % label)
            name.setFixedWidth(150)
            val = QLabel(value)
            val.setWordWrap(True)
            row.addWidget(name)
            row.addWidget(val, 1)
            info_layout.addLayout(row)
        layout.addWidget(info_box)

        # --- About ---
        about = QLabel(
            "Mahar OS is a lightweight Debian-based operating system with "
            "the Mahar Browser, Mahar Files and Mahar Player built in.",
            objectName="about", wordWrap=True, alignment=Qt.AlignCenter)
        layout.addWidget(about)

        layout.addStretch(1)
        self.setCentralWidget(root)


def main():
    if not HAVE_QT:
        print("mahar-welcome needs PyQt5 (python3-pyqt5), which is not "
              "installed.", file=sys.stderr)
        return 1
    app = QApplication(sys.argv)
    app.setStyleSheet(DARK_QSS)
    win = WelcomeWindow()
    win.show()
    return app.exec_()


if __name__ == "__main__":
    sys.exit(main())
