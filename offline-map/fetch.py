#!/usr/bin/env python3
"""Offline map for nDashMo: the OpenFreeMap tiles for Sumedang + greater Bandung, zoom 0-14 (about 2,500 tiles, ~55 MB),
stored in an MBTiles file the app reads (OfflineMap.java). The same tiles the app loads online, so the map style works
unchanged. Fetched gently (4 at a time) from OpenFreeMap's current build; Python standard library only.
Usage: python3 offline-map/fetch.py [output.mbtiles]   (offline-map/build.sh runs it and copies the file to the tablet)
"""
import concurrent.futures as cf, gzip, json, math, sqlite3, sys, time, urllib.request

BOUNDS = (107.25, -7.35, 108.30, -6.55)     # west, south, east, north: Sumedang, Bandung, Cimahi, Bandung + West Bandung regencies
MAXZOOM = 14
TILEJSON = 'https://tiles.openfreemap.org/planet'
OUT = sys.argv[1] if len(sys.argv) > 1 else 'sumedang-bandung.mbtiles'
UA = {'User-Agent': 'nDashMo offline map (personal car tablet)'}

def get(url, tries=4):
    for i in range(tries):
        try:
            with urllib.request.urlopen(urllib.request.Request(url, headers=UA), timeout=30) as r:
                return r.read()
        except Exception as e:
            if getattr(e, 'code', None) == 404: return None
            time.sleep(2 * (i + 1))
    raise RuntimeError(f'failed: {url}')

def tile_range(z):
    w, s, e, n = BOUNDS
    def xy(lon, lat):
        k = 2 ** z
        return int((lon + 180) / 360 * k), int((1 - math.asinh(math.tan(math.radians(lat))) / math.pi) / 2 * k)
    (x0, y0), (x1, y1) = xy(w, n), xy(e, s)
    return [(x, y) for x in range(x0, x1 + 1) for y in range(y0, y1 + 1)]

tj = json.loads(get(TILEJSON))
url = tj['tiles'][0]
build = url.split('/')[-4]                  # e.g. 20261025_080012_pt
jobs = [(z, x, y) for z in range(MAXZOOM + 1) for (x, y) in tile_range(z)]
print(f'{len(jobs)} tiles from OpenFreeMap build {build}')

db = sqlite3.connect(OUT)
db.executescript('''DROP TABLE IF EXISTS tiles; DROP TABLE IF EXISTS metadata;
  CREATE TABLE metadata (name TEXT, value TEXT);
  CREATE TABLE tiles (zoom_level INTEGER, tile_column INTEGER, tile_row INTEGER, tile_data BLOB);
  CREATE UNIQUE INDEX tile_index ON tiles (zoom_level, tile_column, tile_row);''')
meta = {'name': 'nDashMo Sumedang + greater Bandung', 'format': 'pbf', 'type': 'baselayer', 'minzoom': '0', 'maxzoom': str(MAXZOOM),
        'bounds': ','.join(map(str, BOUNDS)), 'attribution': 'OpenFreeMap, OpenMapTiles, OpenStreetMap contributors',
        'mtime': f'OpenFreeMap {build[:4]}-{build[4:6]}-{build[6:8]}, fetched {time.strftime("%Y-%m-%d")}',
        'json': json.dumps({'vector_layers': tj.get('vector_layers', [])})}
db.executemany('INSERT INTO metadata VALUES (?, ?)', meta.items())

def fetch(job):
    z, x, y = job
    return job, get(url.replace('{z}', str(z)).replace('{x}', str(x)).replace('{y}', str(y)))

done = total = 0
with cf.ThreadPoolExecutor(4) as ex:
    for (z, x, y), data in ex.map(fetch, jobs):
        done += 1
        if data:
            if data[:2] != b'\x1f\x8b': data = gzip.compress(data)     # MBTiles keeps vector tiles gzipped
            db.execute('INSERT INTO tiles VALUES (?, ?, ?, ?)', (z, x, 2 ** z - 1 - y, data))   # TMS rows
            total += len(data)
        if done % 250 == 0 or done == len(jobs):
            db.commit(); print(f'  {done}/{len(jobs)} tiles, {total / 1e6:.1f} MB', flush=True)
db.commit(); db.execute('VACUUM'); db.close()
print(f'wrote {OUT}')
