"""Focused extension test using a synthetic rendered profile and optional real probe.

Requires the local source preview at 127.0.0.1:8091 and the built unpacked extension.
The synthetic route is fully intercepted; --real uses normal Tracker access once.
"""
import argparse
import json
from pathlib import Path
import tempfile
from playwright.sync_api import sync_playwright, TimeoutError as BrowserTimeout

ROOT = Path(__file__).resolve().parents[2]
EXTENSION = ROOT / 'build/tracker-collector-extension'
FIXTURE = ROOT / 'src/test/fixtures/tracker-collector-profile.html'
PROFILE = 'http://127.0.0.1:8091/players?player=the%20chosen%20one%23m%C3%98%C3%98da&region=ap'
OUTPUT = ROOT / 'build/tracker-collector-test'
OUTPUT.mkdir(parents=True, exist_ok=True)

def main():
    args = argparse.ArgumentParser()
    args.add_argument('--real', action='store_true')
    args.add_argument('--network-only', action='store_true')
    options = args.parse_args()
    scenario = {'value': 'real' if options.real else 'complete', 'requests': 0}
    template = FIXTURE.read_text(encoding='utf-8-sig')
    profile_dir = tempfile.mkdtemp(prefix='collector-browser-', dir=str(ROOT / 'build'))
    with sync_playwright() as playwright:
        context = playwright.chromium.launch_persistent_context(
            profile_dir, headless=True, executable_path=playwright.chromium.executable_path,
            viewport={'width': 1440, 'height': 900}, downloads_path=str(OUTPUT),
            args=['--disable-extensions-except=' + str(EXTENSION), '--load-extension=' + str(EXTENSION)])
        try:
            observed_downloads = []
            context.on('page', lambda opened: opened.on('download', lambda download: observed_downloads.append(download)))
            worker = context.service_workers[0] if context.service_workers else context.wait_for_event('serviceworker', timeout=15000)
            print('Extension loaded:', worker.url.split('/')[0] + '//<local-extension>', flush=True)
            if not options.real:
                def fulfill(route):
                    scenario['requests'] += 1
                    if scenario['value'] in ('blocked', 'rate_limited'):
                        status = 403 if scenario['value'] == 'blocked' else 429
                        body = '<html><body>verify you are human</body></html>' if status == 403 else '<html><body>Too many requests</body></html>'
                        route.fulfill(status=status, content_type='text/html', body=body)
                        return
                    bad = '2' if scenario['value'] == 'partial' else '-1'
                    duplicate = '1' if scenario['value'] == 'partial' else '-1'
                    route.fulfill(status=200, content_type='text/html',
                                  body=template.replace('__BAD_INDEX__', bad).replace('__DUPLICATE_INDEX__', duplicate).replace("function openMatch(i) { window.activeIndex = i;", "function openMatch(i) { window.activeIndex = i; fetch('https://api.tracker.gg/api/v2/valorant/standard/matches/' + matchId(i));" if scenario['value'] == 'network' else "function openMatch(i) { window.activeIndex = i;"))
                context.route('https://tracker.gg/**', fulfill)
                def match_response(route):
                    match_id = route.request.url.rsplit('/', 1)[-1]
                    player = 'the chosen one#mØØda'
                    stat = lambda v: {'value':v}
                    segments = [{'type':'player-summary','attributes':{'platformUserIdentifier':player},'metadata':{'agentName':'Jett'},'stats':{'kills':stat(1),'deaths':stat(1),'assists':stat(0)}}]
                    for n in (1,2):
                        segments.extend([{'type':'player-round','attributes':{'round':n,'platformUserIdentifier':player},'metadata':{'teamId':'Blue','teamSide':'attacker'},'stats':{'kills':stat(1 if n==1 else 0),'deaths':stat(1 if n==2 else 0),'assists':stat(0),'damage':stat(100),'loadoutValue':stat(800)}},{'type':'round-summary','attributes':{'round':n},'stats':{'winningTeam':stat('Blue' if n==1 else 'Red')}}])
                    route.fulfill(status=200, content_type='application/json', headers={'Access-Control-Allow-Origin':'https://tracker.gg'}, body=json.dumps({'data':{'attributes':{'id':match_id},'metadata':{'rounds':2,'mapName':'Synthetic network'},'segments':segments}}))
                context.route('https://api.tracker.gg/api/v2/valorant/standard/matches/**', match_response)
            page = context.pages[0] if context.pages else context.new_page()
            page.goto(PROFILE, wait_until='domcontentloaded', timeout=30000)
            page.locator('#matchAnalyzerCollect').wait_for(state='visible', timeout=30000)
            page.wait_for_timeout(1000)
            def run(background=False):
                page.locator('#matchAnalyzerCollect').click()
                if background:
                    page.wait_for_function(
                        "() => /Reading recent match|Validated/.test(document.querySelector('#matchAnalyzerCollectStatus').textContent)",
                        timeout=30000)
                    window_state = worker.evaluate('''async () => {
                        const j = (await chrome.storage.session.get('collectorJob')).collectorJob;
                        if (!j?.trackerTabId) return null;
                        const tracker = await chrome.tabs.get(j.trackerTabId);
                        const app = await chrome.tabs.get(j.appTabId);
                        return { trackerActive: tracker.active, separateWindow: tracker.windowId !== app.windowId };
                    }''')
                    assert window_state and window_state['trackerActive'] and window_state['separateWindow'], window_state
                    other_tab.bring_to_front()
                    after_switch = worker.evaluate('''async () => {
                        const j = (await chrome.storage.session.get('collectorJob')).collectorJob;
                        const tracker = await chrome.tabs.get(j.trackerTabId);
                        const app = await chrome.tabs.get(j.appTabId);
                        const tabs = await chrome.tabs.query({windowId: app.windowId});
                        return { trackerActive: tracker.active, otherTabActive: tabs.some(t => t.active && t.url?.includes('another-tab')) };
                    }''')
                    assert after_switch['trackerActive'] and after_switch['otherTabActive'], after_switch
                    tracker_page = next(p for p in context.pages if p.url.startswith('https://tracker.gg/valorant/profile/riot/'))
                    visibility = tracker_page.evaluate('document.visibilityState')
                    print('Tracker page visibility after switch:', visibility, flush=True)
                    assert visibility == 'visible', visibility
                try:
                    page.wait_for_function(
                        "() => /Finished:|Collection stopped:|No extension response|Collector could not/.test(document.querySelector('#matchAnalyzerCollectStatus').textContent)",
                        timeout=120000)
                except BrowserTimeout:
                    print('Timed out; status:', page.locator('#matchAnalyzerCollectStatus').inner_text(), flush=True)
                    raise
                return page.locator('#matchAnalyzerCollectStatus').inner_text()
            if options.network_only:
                scenario['value'] = 'network'
                outcome = run()
                print('Network result:', outcome, flush=True)
                assert 'JSON save started for 5 response(s)' in outcome, outcome
                page.wait_for_timeout(1000)
                assert observed_downloads, 'No download event observed'
                exported = json.loads(Path(observed_downloads[-1].path()).read_text(encoding='utf-8'))
                assert exported['complete'] and exported['responseCount'] == 5
                assert len(exported['matches']) == 5 and all(m['validation'] == 'passed' and m['response']['data']['attributes']['id'] == m['matchId'] for m in exported['matches'])
                print('PASS: saved JSON contains five validated synthetic response bodies', flush=True)
                return
            if options.real:
                outcome = run()
                page.screenshot(path=str(OUTPUT / 'real-result.png'))
                print('Real Tracker result:', outcome, flush=True)
                print('Validated match options:', page.locator('#matchAnalyzerCollected option').count(), flush=True)
                if page.locator('#matchAnalyzerCollected option').count() != 5:
                    raise SystemExit(2)
                return
            page.evaluate("document.querySelector('#matchAnalyzerCollect').click()")
            page.wait_for_function("() => document.querySelector('#matchAnalyzerCollectStatus').textContent.includes('No extension response')", timeout=10000)
            assert scenario['requests'] == 0, 'Synthetic DOM click must not start collection'
            print('PASS: untrusted synthetic click does not start collection', flush=True)
            other_tab = context.new_page()
            other_tab.goto('about:blank#another-tab')
            page.bring_to_front()
            outcome = run(background=True)
            print('PASS: switched main-window tab while Tracker stayed active in its own window', flush=True)
            print('Synthetic outcome:', outcome, 'details:', page.locator('#matchAnalyzerFailures').inner_text(), flush=True)
            assert 'Finished: 5 validated match(es), 0 issue(s).' in outcome, outcome
            assert scenario['requests'] == 1, f"Expected one profile load, got {scenario['requests']}"
            assert page.locator('#matchAnalyzerCollected option').count() == 5
            assert '1/1/0' in page.locator('#matchAnalyzerResult').inner_text()
            page.locator('#matchAnalyzerCollected').select_option(index=4)
            assert 'Mock Map 5' in page.locator('#matchAnalyzerResult').inner_text()
            assert page.locator('#matchAnalyzerResult tbody tr').first.locator('td').nth(4).inner_text() == '1–0'
            assert 'spike detonated' in page.locator('#matchAnalyzerResult tbody tr').first.inner_text().lower()
            assert 'UNFAMILIAR OUTCOME' in page.locator('#matchAnalyzerResult tbody tr').nth(1).inner_text()
            page.screenshot(path=str(OUTPUT / 'synthetic-five.png'))
            print('PASS: five validated synthetic matches appear in the app', flush=True)
            scenario['value'] = 'network'
            outcome = run()
            assert 'Finished: 5 validated match(es), 0 issue(s).' in outcome, outcome
            assert page.locator('#matchAnalyzerCollected option').count() == 5
            assert 'Validated Tracker network response' in page.locator('#matchAnalyzerResult').inner_text()
            assert '100.0 damage/round' in page.locator('#matchAnalyzerResult').inner_text()
            assert 'JSON save started for 5 response(s)' in outcome, outcome
            page.wait_for_timeout(1000)
            assert observed_downloads, 'No download event observed'
            exported = json.loads(Path(observed_downloads[-1].path()).read_text(encoding='utf-8'))
            assert exported['complete'] is True and exported['responseCount'] == 5
            assert len(exported['matches']) == 5 and all(m['validation'] == 'passed' and m['response']['data']['attributes']['id'] == m['matchId'] for m in exported['matches'])
            print('PASS: saved JSON contains five validated synthetic response bodies', flush=True)
            scenario['value'] = 'partial'
            outcome = run()
            assert 'Finished: 3 validated match(es), 1 issue(s).' in outcome, outcome
            assert page.locator('#matchAnalyzerCollected option').count() == 3
            assert 'Match 3:' in page.locator('#matchAnalyzerFailures').inner_text()
            print('PASS: duplicate match skipped and invalid match reported', flush=True)
            scenario['value'] = 'blocked'
            outcome = run()
            assert 'Collection stopped:' in outcome, outcome
            assert '403' in outcome or 'verification' in outcome.lower(), outcome
            page.screenshot(path=str(OUTPUT / 'synthetic-block.png'))
            print('PASS: access block stops collection', flush=True)
            scenario['value'] = 'rate_limited'
            outcome = run()
            assert 'Collection stopped:' in outcome, outcome
            assert '429' in outcome, outcome
            print('PASS: rate limit stops collection', flush=True)
        finally:
            context.close()

if __name__ == '__main__':
    main()
