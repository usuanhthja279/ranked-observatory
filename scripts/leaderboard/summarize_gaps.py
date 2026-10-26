import json,collections,datetime
from pathlib import Path
root=Path(__file__).resolve().parents[2]
old=root/'build/global-verification'
base=root/'build/gap-investigation'
audit=json.loads((root/'docs/global-pagination-verification.json').read_text(encoding='utf-8'))
missing={r['key']:r for r in audit['missingFromGlobal']}
pages=[json.loads((old/f'global-{n}.json').read_text(encoding='utf-8')) for n in range(1,543)]
rows=[r for p in pages for r in p['rows']]
rr=collections.Counter(r['rr'] for r in rows)
seen={};duplicate_same_rr=0;duplicates=0
for r in rows:
 if r['key'] in seen:
  duplicates+=1;duplicate_same_rr+=seen[r['key']]['rr']==r['rr']
 else:seen[r['key']]=r
regional_raw={r:json.loads((old/f'{r}-complete.json').read_text(encoding='utf-8'))['rawRows'] for r in ['ap','kr','eu','na','latam','br']}
live=[];recovered={}
for n in [1,300,301,302,510]:
 samples=[json.loads((base/str(a)/f'global-{n}.json').read_text(encoding='utf-8')) for a in range(3)]
 ids=[{r['key'] for r in s['rows']} for s in samples]
 for s in samples:
  for r in s['rows']:
   if r['key'] in missing:recovered[r['key']]=dict(player=r['player'],region=r['region'],rr=r['rr'],page=n)
 live.append(dict(page=n,sourceUpdated=[s['rows'][0]['sourceUpdated'] for s in samples],overlapWithOrdinary=[len(ids[0]&x) for x in ids[1:]],rows=[len(s['rows']) for s in samples]))
report=dict(checkedAt=datetime.datetime.now(datetime.timezone.utc).isoformat(),act=audit['act'],historical=dict(globalRawRows=len(rows),regionalRawRows=regional_raw,regionalRawTotal=sum(regional_raw.values()),globalUnique=len(seen),duplicates=duplicates,duplicateObservationsWithUnchangedRR=duplicate_same_rr,unmatchedRegionalIdentities=len(missing),unmatchedWithRRPoolOver100Rows=sum(rr[r['rr']]>100 for r in missing.values()),adjacentPageBoundariesWithSameRR=sum(a['rows'][-1]['rr']==b['rows'][0]['rr'] for a,b in zip(pages,pages[1:])),pageBoundaryCount=len(pages)-1,observedFrom=pages[0]['checkedAt'],observedThrough=pages[-1]['checkedAt']),liveSamples=live,previouslyUnmatchedRecovered=list(recovered.values()),conclusions=['All six regions occur in global; its raw row count equals the sum of regional raw row counts. No evidence of a region omission or early pagination cap in this capture.','Identity gaps are strongly associated with large RR ties and duplicate observations. Mutable offset pagination and independently cached pages are the leading explanation; these observations do not isolate upstream tie-breaking from live rank movement.','Four of five sampled pages retained identical source timestamps and identities despite two fresh URL query parameters; outer URL freshness does not guarantee a fresh underlying board.','Three earlier unmatched identities were recovered in only five sampled global pages. The 1593 value is a historical set difference, not a permanent source exclusion count.','This targeted investigation did not repeat a full simultaneous global/regional census and cannot prove every unmatched identity recoverable.','Regional reconciliation improves coverage but itself contains duplicates and is not a guarantee of completeness. For fresh per-player regional statistics regional-only polling costs approximately the same 542 pages as global-only, but does not supply a source global rank.','Keep the current schedule unchanged during this investigation. If global rank is optional, prefer regional-only half-hour collection; if global rank plus refreshed gap coverage is required, retain reconciliation or explicitly accept stale supplemental observations with a slower regional cadence.'])
(root/'docs/global-gap-investigation.json').write_text(json.dumps(report,indent=2,ensure_ascii=False),encoding='utf-8')
print(json.dumps(dict(recovered=list(recovered.values()),report='docs/global-gap-investigation.json'),ensure_ascii=True))


