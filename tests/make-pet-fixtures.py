"""Create original, redistributable diagnostic art and real importer rejection cases.

Generated artwork is dedicated to CC0-1.0. No external character assets are used.
Outputs belong in ignored artifacts; this is not a mock of a device integration.
"""
from pathlib import Path
import json
import struct
import zlib
import zipfile

root = Path(__file__).resolve().parents[1] / 'artifacts' / 'pet-fixtures'
root.mkdir(parents=True, exist_ok=True)
width, height = 1536, 2288
pixels = bytearray(width * height * 4)


def rectangle(x, y, w, h, color):
    row = bytes(color) * w
    for yy in range(y, y + h):
        start = (yy * width + x) * 4
        pixels[start:start + w * 4] = row


for state in range(11):
    for frame in range(8):
        x, y = frame * 192, state * 208 + (frame % 3) * 3
        # Original geometric robot: bobbing silhouette, blinking eyes, moving antenna.
        rectangle(x + 50, y + 48, 92, 70, (181, 183, 209, 255))
        rectangle(x + 62, y + 65, 68, 30, (25, 44, 62, 255))
        rectangle(x + 70, y + 75, 12, 3 if frame == 2 else 12, (133, 235, 210, 255))
        rectangle(x + 110, y + 75, 12, 3 if frame == 2 else 12, (133, 235, 210, 255))
        rectangle(x + 62, y + 122, 68, 45, (96 + state * 10, 90, 151, 255))
        rectangle(x + 35, y + 125 - frame * 2, 20, 30, (181, 183, 209, 255))
        rectangle(x + 137, y + 125, 20, 30, (181, 183, 209, 255))
        rectangle(x + 66, y + 170, 22, 20, (133, 235, 210, 255))
        rectangle(x + 104, y + 170, 22, 20, (133, 235, 210, 255))
        rectangle(x + 92, y + 25, 8, 23, (133, 235, 210, 255))
        rectangle(x + 86 + frame, y + 19, 20, 8, (190, 150, 230, 255))


def png(data, w=width, h=height):
    def chunk(kind, payload):
        return struct.pack('>I', len(payload)) + kind + payload + struct.pack('>I', zlib.crc32(kind + payload))
    rows = b''.join(b'\0' + data[y * w * 4:(y + 1) * w * 4] for y in range(h))
    return b'\x89PNG\r\n\x1a\n' + chunk(b'IHDR', struct.pack('>IIBBBBB', w, h, 8, 6, 0, 0, 0)) + chunk(b'IDAT', zlib.compress(rows)) + chunk(b'IEND', b'')


sheet = png(pixels)
pet = dict(id='sliver-wolf-diagnostic', displayName='Circuit Companion', spriteVersionNumber=2,
           spritesheetPath='sprite.png', author='Sliver Wolf project', license='CC0-1.0')


def archive(name, manifest=pet, image=sheet, extra=(), prefix=''):
    with zipfile.ZipFile(root / name, 'w', zipfile.ZIP_DEFLATED) as z:
        z.writestr(prefix + 'pet.json', json.dumps(manifest))
        z.writestr(prefix + 'sprite.png', image)
        z.writestr(prefix + 'LICENSE.txt', 'Original diagnostic robot artwork. CC0-1.0. No third-party character assets.')
        for path, data in extra:
            z.writestr(path, data)


archive('valid-v2.zip')
archive('nested-v2.zip', prefix='circuit/')
archive('traversal.zip', extra=[('../escape.txt', b'no')])
archive('absolute.zip', extra=[('/escape.txt', b'no')])
archive('duplicate.zip', extra=[('PET.JSON', b'{}')])
archive('executable.zip', extra=[('startup.js', b'no')])
archive('wrong-version.zip', manifest={**pet, 'spriteVersionNumber': 1})
archive('sheet-escape.zip', manifest={**pet, 'spritesheetPath': '../sprite.png'})
archive('invalid-image.zip', image=b'not an image')
archive('wrong-dimensions.zip', image=png(bytes(4 * 16 * 16), 16, 16))
archive('expansion.zip', extra=[('README.txt', bytes(17 * 1024 * 1024))])
link = zipfile.ZipInfo('README.txt')
link.create_system = 3
link.external_attr = 0o120777 << 16
archive('symlink.zip', extra=[(link, b'../../elsewhere')])
empty_wave = bytearray(pixels)
empty_wave[3 * 208 * width * 4:4 * 208 * width * 4] = bytes(208 * width * 4)
archive('missing-wave.zip', image=png(empty_wave))
(root / 'truncated.zip').write_bytes((root / 'valid-v2.zip').read_bytes()[:-10])
print(f'Generated {len(list(root.glob("*.zip")))} ZIP fixtures in {root}')
