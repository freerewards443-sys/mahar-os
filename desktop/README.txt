Mahar OS — desktop integration files
=====================================
Source tree: desktop/
The build script copies each file to its install location inside
the live image. (All paths below are inside the target system.)

IceWM (window manager)
----------------------
icewm/preferences            -> /etc/icewm/preferences
    Taskbar at bottom, start menu on, 24h clock, 2 workspaces,
    click-to-focus, Super_L opens the start menu (KeySysMenu),
    IceWM's own background disabled (feh draws the wallpaper).
THEME SELECTION (build-script step — no source file needed)
----------------------------------------------------------
IceWM picks its theme from a one-line config file named exactly
"theme". The build script must create it as /etc/icewm/theme
with this content:
    Theme="mahar"
(There is deliberately no source file for it in this tree; the
line above is the entire file.)
icewm/theme/mahar/default.theme -> /usr/share/icewm/themes/mahar/default.theme
    The "mahar" dark theme (dark taskbar/menus/titlebars,
    blue #1a73e8 accent, white text).
    NOTE: the taskbar start-button logo is NOT a preference;
    IceWM reads start.xpm from this same theme directory, so the
    build script must also install a Mahar-logo start.xpm as
    /usr/share/icewm/themes/mahar/start.xpm.
icewm/toolbar                -> /etc/icewm/toolbar
    Quick-launch: Mahar Browser, Mahar Files, Mahar Player,
    Terminal (xterm), Text Editor (mousepad).
icewm/startup                -> /etc/icewm/startup  (chmod 755)
    Sets the wallpaper with feh, starts nm-applet and a volume
    tray app (pasystray/volumeicon), each guarded by command -v.
icewm/keys                   -> /etc/icewm/keys
    Alt+Ctrl+T terminal, Alt+F4 close window. Alt+Tab is IceWM's
    built-in switcher (KeySysSwitchNext default). The Windows key
    opens the start menu via KeySysMenu="Super_L" in preferences.
icewm/menu                   -> /etc/icewm/menu
    "Mahar OS" start menu: Mahar Browser / Files / Player,
    Terminal, Text Editor, Calculator, Settings submenu
    (Network -> nm-connection-editor, Volume -> pavucontrol),
    Log Out / Reboot / Shutdown via icesh + systemctl.

Autologin
---------
nodm/nodm.conf               -> /etc/default/nodm
    NODM_ENABLED=true, user "mahar", session icewm-session.
    (nodm package must be installed in the image.)

Display-manager session entry
-----------------------------
xsessions/mahar.desktop       -> /usr/share/xsessions/mahar.desktop
    "Mahar OS" session running /usr/bin/icewm-session.

Boot splash
-----------
plymouth/mahar.plymouth       -> /usr/share/plymouth/themes/mahar/mahar.plymouth
plymouth/mahar.script         -> /usr/share/plymouth/themes/mahar/mahar.script
    Dark (#0b1020) splash: centered logo.png, "Mahar OS" title,
    animated Loading indicator, status messages at the bottom.
    Enable with: plymouth-set-default-theme -R mahar
    (logo.png must be placed in the theme dir by the build script.)

Welcome app
-----------
welcome/mahar_welcome.py      -> /usr/local/bin/mahar-welcome  (chmod 755)
    PyQt5 "Welcome to Mahar OS" greeter: logo, launch buttons for
    Mahar Browser / Files / Player / Terminal, system-info panel
    (/proc/cpuinfo, /proc/meminfo, kernel), About text, dark QSS.
welcome/mahar-welcome.desktop -> /usr/share/applications/mahar-welcome.desktop
    Launcher for the welcome app.
