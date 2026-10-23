"""Generate the vector launcher variants from a single stopwatch design (stdlib only)."""
from pathlib import Path
from math import sin, cos, radians
import os
import subprocess
import tempfile

ROOT = Path(__file__).resolve().parents[1]
RES = ROOT / 'app/src/main/res'
NAVY = '#101827'
WHITE = '#F5FAFF'

def point(angle):
    return (54 + 24.5 * cos(radians(angle)), 57 + 24.5 * sin(radians(angle)))

def arc(start):
    a, b = point(start), point(start + 104)
    return f'M{a[0]:.3f},{a[1]:.3f} A24.5,24.5 0,0 1,{b[0]:.3f},{b[1]:.3f}'

# Each entry is (path, fill, stroke, stroke-width).
paths = [
    ('M49,22 H59 Q60,22 60,23 V26 H48 V23 Q48,22 49,22 Z', WHITE, None, None),
    ('M51.5,26 H56.5 V33 H51.5 Z', WHITE, None, None),
    (arc(150), None, '#36D996', 4.5),
    (arc(270), None, '#F5C84C', 4.5),
    (arc(30), None, '#FF6478', 4.5),
    ('M54,43 V57 L65,63', None, WHITE, 3.5),
    ('M51.25,57 a2.75,2.75 0,1 0,5.5,0 a2.75,2.75 0,1 0,-5.5,0', WHITE, None, None),
]

def xml_paths(monochrome=False):
    result = []
    for path, fill, stroke, width in paths:
        attrs = [f'android:pathData="{path}"', f'android:fillColor="{WHITE if fill and monochrome else fill or "#00000000"}"']
        if stroke:
            attrs += [f'android:strokeColor="{WHITE if monochrome else stroke}"', f'android:strokeWidth="{width}"', 'android:strokeLineCap="round"', 'android:strokeLineJoin="round"']
        result.append('    <path ' + '\n        '.join(attrs) + ' />')
    return '\n'.join(result)

def vector(body):
    return '<?xml version="1.0" encoding="utf-8"?>\n<vector xmlns:android="http://schemas.android.com/apk/res/android"\n    android:width="108dp" android:height="108dp"\n    android:viewportWidth="108" android:viewportHeight="108">\n' + body + '\n</vector>\n'

(RES/'drawable/ic_launcher_foreground.xml').write_text(vector(xml_paths()))
(RES/'drawable/ic_launcher_monochrome.xml').write_text(vector(xml_paths(True)))
(RES/'drawable/ic_launcher_background.xml').write_text(vector(f'    <path android:fillColor="{NAVY}" android:pathData="M0,0 H108 V108 H0 Z" />'))
for name, background in [
    ('ic_launcher', 'M28,4 H80 Q104,4 104,28 V80 Q104,104 80,104 H28 Q4,104 4,80 V28 Q4,4 28,4 Z'),
    ('ic_launcher_round', 'M4,54 a50,50 0,1 0,100,0 a50,50 0,1 0,-100,0'),
]:
    body = f'    <path android:fillColor="{NAVY}" android:pathData="{background}" />\n'
    body += '    <group android:pivotX="54" android:pivotY="54" android:scaleX="1.35" android:scaleY="1.35">\n'
    body += xml_paths() + '\n    </group>'
    (RES/f'drawable/{name}_legacy.xml').write_text(vector(body))
    (RES/f'mipmap-anydpi/{name}.xml').unlink(missing_ok=True)
    adaptive = '<?xml version="1.0" encoding="utf-8"?>\n<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">\n    <background android:drawable="@drawable/ic_launcher_background" />\n    <foreground android:drawable="@drawable/ic_launcher_foreground" />\n    <monochrome android:drawable="@drawable/ic_launcher_monochrome" />\n</adaptive-icon>\n'
    (RES/f'mipmap-anydpi-v26/{name}.xml').write_text(adaptive)
# Remove the obsolete template robot bitmaps; Android 7+ can render the anydpi vectors.
for path in RES.glob('mipmap-*/ic_launcher*.webp'):
    path.unlink()
svg_paths = []
for path, fill, stroke, width in paths:
    attrs = f'd="{path}" fill="{fill or "none"}"'
    if stroke:
        attrs += f' stroke="{stroke}" stroke-width="{width}" stroke-linecap="round" stroke-linejoin="round"'
    svg_paths.append('  <path ' + attrs + '/>')
(ROOT/'design/app-icon.svg').write_text('<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 108 108">\n  <rect width="108" height="108" rx="25" fill="'+NAVY+'"/>\n'+'\n'.join(svg_paths)+'\n</svg>\n')

# Raster fallbacks are needed for launchers on Android 7 and are rendered at each density.
java_home = os.environ.get('JAVA_HOME')
javac = str(Path(java_home)/'bin/javac') if java_home else 'javac'
java = str(Path(java_home)/'bin/java') if java_home else 'java'
with tempfile.TemporaryDirectory(prefix='timer-icons-') as output:
    subprocess.run([javac, '-d', output, str(ROOT/'tools/LauncherIconRenderer.java')], check=True)
    subprocess.run([java, '-Djava.awt.headless=true', '-cp', output, 'LauncherIconRenderer', str(ROOT)], check=True)
