# Third-party notices

## Standby Pro (MIT License)

Parts of this app are adapted from **Standby Pro**, an open-source Flutter app:
https://github.com/Ghostzy0/standby-pro

The ideas and visual details below were ported and rewritten in Kotlin for Android views. The Dart code was not copied line by line.

| Adapted from Standby Pro | Where it is in this app |
| --- | --- |
| Theme colour sets: Aurora, Night Red, Solar, Matrix | pp/src/main/java/com/example/standbyclock/Themes.kt (Themes) |
| Ambient background: diagonal gradient and nine faint arcs | Themes.kt (AmbientView) |
| Redraw cadence: wake each second only when something shows seconds | Themes.kt (ClockCadence), used by ClockUi.kt |
| Nine-position burn-in cycle, 8 dp across and 6 dp down | ClockUi.kt (shiftForBurnIn) |
| Calendar and now-playing card layouts | PanelCards.kt (AgendaCard, MusicCard) |
| Quick-controls bar: style, face, group and settings | MainActivity.kt (quickBar) |
| Media-key fallback when no media session is available | MusicSession.kt (sendMediaKey) |

The original licence text is reproduced below, as the MIT License requires.

---
MIT License

Copyright (c) 2026 TheFadGhost

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.

