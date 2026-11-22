import datetime
import json
import pathlib
import urllib.parse
import urllib.request
from zoneinfo import ZoneInfo

path = pathlib.Path(__file__).parent / 'data' / 'weather.json'
end = datetime.datetime.now(ZoneInfo('Asia/Shanghai')).date() - datetime.timedelta(days=1)
start = datetime.date(2026, 11, 19)
if end < start:
    raise SystemExit(0)
params = urllib.parse.urlencode({'latitude': 32.9, 'longitude': 115.817, 'start_date': start.isoformat(), 'end_date': end.isoformat(), 'daily': 'weather_code,temperature_2m_max,temperature_2m_min,precipitation_sum', 'timezone': 'Asia/Shanghai'})
with urllib.request.urlopen('https://archive-api.open-meteo.com/v1/archive?' + params, timeout=60) as response:
    data = json.load(response)['daily']
rows = []
for i, date in enumerate(data['time']):
    values = [data[k][i] for k in ('weather_code', 'temperature_2m_max', 'temperature_2m_min', 'precipitation_sum')]
    if all(v is not None for v in values):
        rows.append(dict(date=date, code=values[0], max=values[1], min=values[2], rain=values[3]))
existing = json.loads(path.read_text(encoding='utf-8')) if path.exists() else {'days': []}
merged = {row['date']: row for row in existing['days']}
merged.update({row['date']: row for row in rows})
path.write_text(json.dumps({'days': sorted(merged.values(), key=lambda x: x['date'])}, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')
