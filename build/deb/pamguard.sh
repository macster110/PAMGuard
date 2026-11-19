#!/bin/sh
#
# PAMGuard launcher for Linux.
#
# Works out the display scale before Java starts, since Java on Linux doesn't
# always get it right on high resolution screens. In order of preference:
#   1. PAMGUARD_UI_SCALE environment variable, e.g. PAMGUARD_UI_SCALE=2
#   2. The scale chosen in the PAMGuard start up dialog (~/Pamguard/uiScale.txt),
#      unless that says "auto"
#   3. GDK_SCALE
#   4. Xft.dpi from xrdb (96 dpi = 1, 192 dpi = 2)
#   5. The GNOME scaling factor
#   6. 1
# Java on Linux only supports whole number scales, so values are rounded.
#
# If the scale is changed in the start up dialog, PAMGuard exits with code 75
# and this script starts it again with the new scale.
#
# Any arguments are passed to PAMGuard, e.g. -c to show the start up dialog.

PAMGUARD_DIR=/usr/share/pamguard
PAMGUARD_JAR="$PAMGUARD_DIR/Pamguard"
JAVA_MEMORY="${PAMGUARD_MEMORY:--Xmx8g -Xms512m}"
RESTART_CODE=75

# round a number to a whole number scale of at least 1, or print nothing.
to_scale() {
	echo "$1" | awk '{ v = $1 + 0; if (v > 0) { s = int(v + 0.5); if (s < 1) s = 1; print s } }'
}

detect_scale() {
	if [ -n "$PAMGUARD_UI_SCALE" ]; then
		to_scale "$PAMGUARD_UI_SCALE"
		return
	fi
	scale_file="$HOME/Pamguard/uiScale.txt"
	if [ -r "$scale_file" ]; then
		saved=$(grep -v '^[[:space:]]*#' "$scale_file" | grep -v '^[[:space:]]*$' | head -n 1 | tr -d '[:space:]xX%')
		if [ -n "$saved" ] && [ "$saved" != "auto" ]; then
			s=$(to_scale "$saved")
			if [ -n "$s" ]; then
				echo "$s"
				return
			fi
		fi
	fi
	if [ -n "$GDK_SCALE" ]; then
		s=$(to_scale "$GDK_SCALE")
		if [ -n "$s" ]; then
			echo "$s"
			return
		fi
	fi
	if command -v xrdb > /dev/null 2>&1; then
		dpi=$(xrdb -query 2> /dev/null | awk '/^Xft.dpi:/ { print $2; exit }')
		if [ -n "$dpi" ]; then
			s=$(echo "$dpi" | awk '{ v = $1 / 96; if (v > 0) { s = int(v + 0.5); if (s < 1) s = 1; print s } }')
			if [ -n "$s" ]; then
				echo "$s"
				return
			fi
		fi
	fi
	if command -v gsettings > /dev/null 2>&1; then
		g=$(gsettings get org.gnome.desktop.interface scaling-factor 2> /dev/null | awk '{ print $NF }')
		s=$(to_scale "$g")
		if [ -n "$s" ]; then
			echo "$s"
			return
		fi
	fi
	echo 1
}

export PAMGUARD_RESTART_CODE=$RESTART_CODE

while true; do
	UI_SCALE=$(detect_scale)
	echo "Starting PAMGuard with display scale $UI_SCALE"
	# shellcheck disable=SC2086
	java $JAVA_MEMORY \
		-Dsun.java2d.uiScale="$UI_SCALE" \
		-Dawt.useSystemAAFontSettings=on -Dswing.aatext=true \
		-jar "$PAMGUARD_JAR" "$@"
	code=$?
	if [ "$code" -ne "$RESTART_CODE" ]; then
		exit "$code"
	fi
done
