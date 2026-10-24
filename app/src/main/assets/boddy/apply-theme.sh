#!/bin/bash
# The Boddy look: dark theme, readable fonts, a bottom dock, our wallpaper and prompt.
CFG=/root/.config/xfce4/xfconf/xfce-perchannel-xml
mkdir -p "$CFG" /root/.config/autostart /usr/share/backgrounds/boddy
cp /opt/boddy/wallpaper.svg /usr/share/backgrounds/boddy/wallpaper.svg

cat > "$CFG/xsettings.xml" <<'XML'
<?xml version="1.0" encoding="UTF-8"?>
<channel name="xsettings" version="1.0">
  <property name="Net" type="empty">
    <property name="ThemeName" type="string" value="Adwaita-dark"/>
    <property name="IconThemeName" type="string" value="Adwaita"/>
  </property>
  <property name="Xft" type="empty">
    <property name="DPI" type="int" value="110"/>
    <property name="Antialias" type="int" value="1"/>
    <property name="HintStyle" type="string" value="hintslight"/>
  </property>
  <property name="Gtk" type="empty">
    <property name="FontName" type="string" value="DejaVu Sans 10"/>
    <property name="CursorThemeSize" type="int" value="32"/>
  </property>
</channel>
XML

cat > "$CFG/xfwm4.xml" <<'XML'
<?xml version="1.0" encoding="UTF-8"?>
<channel name="xfwm4" version="1.0">
  <property name="general" type="empty">
    <property name="theme" type="string" value="Default-xhdpi"/>
    <property name="title_font" type="string" value="DejaVu Sans Bold 10"/>
    <property name="use_compositing" type="bool" value="false"/>
    <property name="workspace_count" type="int" value="1"/>
  </property>
</channel>
XML

# One bottom dock: Boddy menu, app launchers, open windows, clock.
cat > "$CFG/xfce4-panel.xml" <<'XML'
<?xml version="1.0" encoding="UTF-8"?>
<channel name="xfce4-panel" version="1.0">
  <property name="configver" type="int" value="2"/>
  <property name="panels" type="array">
    <value type="int" value="1"/>
    <property name="dark-mode" type="bool" value="true"/>
    <property name="panel-1" type="empty">
      <property name="position" type="string" value="p=10;x=0;y=0"/>
      <property name="length" type="uint" value="100"/>
      <property name="position-locked" type="bool" value="true"/>
      <property name="size" type="uint" value="48"/>
      <property name="icon-size" type="uint" value="32"/>
      <property name="plugin-ids" type="array">
        <value type="int" value="1"/>
        <value type="int" value="2"/>
        <value type="int" value="3"/>
        <value type="int" value="4"/>
        <value type="int" value="5"/>
        <value type="int" value="6"/>
        <value type="int" value="7"/>
        <value type="int" value="8"/>
        <value type="int" value="9"/>
        <value type="int" value="10"/>
        <value type="int" value="11"/>
      </property>
    </property>
  </property>
  <property name="plugins" type="empty">
    <property name="plugin-1" type="string" value="applicationsmenu">
      <property name="button-title" type="string" value="Boddy"/>
      <property name="show-button-title" type="bool" value="true"/>
    </property>
    <property name="plugin-2" type="string" value="launcher"/>
    <property name="plugin-3" type="string" value="launcher"/>
    <property name="plugin-4" type="string" value="launcher"/>
    <property name="plugin-5" type="string" value="launcher"/>
    <property name="plugin-6" type="string" value="launcher"/>
    <property name="plugin-7" type="string" value="launcher"/>
    <property name="plugin-8" type="string" value="launcher"/>
    <property name="plugin-9" type="string" value="tasklist">
      <property name="show-labels" type="bool" value="false"/>
    </property>
    <property name="plugin-10" type="string" value="separator">
      <property name="expand" type="bool" value="true"/>
      <property name="style" type="uint" value="0"/>
    </property>
    <property name="plugin-11" type="string" value="clock"/>
  </property>
</channel>
XML
# Launcher plugins find their app by listing the files in their folder.
for n in 2 3 4 5 6 7 8; do
    f=$(ls /root/.config/xfce4/panel/launcher-$n/ 2>/dev/null | head -1)
    [ -n "$f" ] && sed -i "s|<property name=\"plugin-$n\" type=\"string\" value=\"launcher\"/>|<property name=\"plugin-$n\" type=\"string\" value=\"launcher\"><property name=\"items\" type=\"array\"><value type=\"string\" value=\"$f\"/></property></property>|" "$CFG/xfce4-panel.xml"
done

cat > /root/.config/autostart/boddy-session.desktop <<'DESKTOP'
[Desktop Entry]
Type=Application
Name=Boddy session
Exec=/opt/boddy/session-init.sh
NoDisplay=true
DESKTOP

grep -q "boddy prompt" /root/.bashrc 2>/dev/null || cat >> /root/.bashrc <<'BASHRC'
# boddy prompt
PS1='\[\e[1;35m\]boddy\[\e[0m\]:\[\e[1;34m\]\w\[\e[0m\]\$ '
export PATH="/root/.local/bin:$PATH"
BASHRC
