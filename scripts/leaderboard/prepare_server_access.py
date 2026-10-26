"""Curate published country aggregates; never convert cloud RTT into claimed game ping."""
import argparse, datetime, hashlib, json, urllib.request
from pathlib import Path

ROOT=Path(__file__).resolve().parents[2]
INPUT=ROOT/'build/published-latency-input.json'
SOURCE='https://myping.is/pulse-data.json'
MAP={
 'ap':dict(officialServerCount=6,targets={'ap-south-1':'Mumbai','ap-southeast-1':'Singapore','ap-east-1':'Hong Kong','ap-northeast-1':'Tokyo','ap-southeast-2':'Sydney'},unmeasured=['Manila']),
 'eu':dict(officialServerCount=8,targets={'eu-central-1':'Frankfurt','eu-west-3':'Paris','eu-north-1':'Stockholm','eu-west-2':'London'},unmeasured=['Istanbul','Warsaw','Madrid','Bahrain']),
 'na':dict(officialServerCount=6,targets={'us-west-2':'Oregon','us-west-1':'N. California','us-east-1':'N. Virginia'},unmeasured=['Texas','Illinois','Georgia']),
 'br':dict(officialServerCount=1,targets={'sa-east-1':'São Paulo'},unmeasured=[]),
 'kr':dict(officialServerCount=1,targets={'ap-northeast-2':'Seoul'},unmeasured=[]),
 'latam':dict(officialServerCount=3,targets={},unmeasured=['Santiago','Mexico City','Miami'])
}

def prepare(fetch=False):
 if fetch:
  with urllib.request.urlopen(SOURCE,timeout=30) as response:INPUT.write_bytes(response.read())
 raw=INPUT.read_bytes(); data=json.loads(raw.decode('utf-8-sig'))
 assert data['schema']==1 and data['totals']['from']<=data['totals']['to']
 allowed=set().union(*(set(r['targets']) for r in MAP.values()))
 countries={}
 for country in data['countries']:
  code=country['country'].lower();assert len(code)==2 and code.isalpha()
  cells={}
  for row in country['rows']:
   if row['endpoint'] not in allowed:continue
   assert row['samples']>=0 and row['medianMs']>=0 and 0<=row['reachRate']<=1
   cells[row['endpoint']]={k:row[k] for k in ['samples','medianMs','reachRate']}
  countries[code]=dict(runs=country['runs'],farFromHome=country['farFromHome'],uncheckedShare=country['uncheckedShare'],endpoints=cells)
 MAP['ap']['homeCoverageGaps']=['ph']
 MAP['eu']['homeCoverageGaps']=['tr','pl','es','bh']
 result=dict(version=1,enabled=True,source=dict(title='myping.is country-aggregated browser measurements',url='https://myping.is/ping-statistics',datasetUrl=SOURCE,
   type='measured-https-cloud-proxy',fromDate=data['totals']['from'],toDate=data['totals']['to'],retrievedAt=datetime.datetime.now(datetime.timezone.utc).isoformat(),sha256=hashlib.sha256(raw).hexdigest(),
   countryCount=len(countries),siteCompletedTests=data['totals']['completedTests'],siteValorantTests=data['games'].get('valorant',0),
   description='Measured browser HTTPS RTT to nearby AWS locations; not Riot game-server RTT, not UDP ping, not a national internet survey. Country cells pool games. Tests/readings are not unique players.'),
   settings=dict(qualityWeights=[.7,.2,.1],pingScaleMs=60,maxWeightAdjustment=.1,proxyShrink=.25,minCountryRuns=40,minCellSamples=50,fullCellSamples=100,maxUncheckedShare=.75,maxAgeDays=30),
   regions=MAP,countries=countries,
   references=[dict(title='Riot server locations and shard boundaries',url='https://support.riotgames.com/en-us/valorant/support-tools/server-select'),
    dict(title='Publisher method and source quality flags',url='https://myping.is/ping-statistics'),
    dict(title='Riot diagnostic addresses; not a country ping matrix',url='https://support.riotgames.com/en-us/valorant/connectivity/how-to-use-tracert-to-obtain-network-logs')])
 target=ROOT/'leaderboard-service/src/main/resources/server-access-data.json';target.write_text(json.dumps(result,indent=2,ensure_ascii=True),encoding='utf-8')
 (ROOT/'docs/server-access-data.json').write_text(json.dumps(result,indent=2,ensure_ascii=True),encoding='utf-8')
 print(json.dumps(dict(fromDate=data['totals']['from'],toDate=data['totals']['to'],countries=len(countries),flagged=[c for c,v in countries.items() if v['farFromHome']],sourceType=result['source']['type'])))
 return result

if __name__=='__main__':
 parser=argparse.ArgumentParser();parser.add_argument('--fetch',action='store_true');prepare(parser.parse_args().fetch)


