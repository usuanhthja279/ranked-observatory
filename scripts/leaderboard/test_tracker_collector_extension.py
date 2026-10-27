"""Focused extension test using a synthetic rendered profile and optional real probe.

Requires the local source preview at 127.0.0.1:8091 and the built unpacked extension.
The synthetic route is fully intercepted; --real uses normal Tracker access once.
"""
import argparse
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
    options = args.parse_args()
    scenario = {'value': 'real' if options.real else 'complete', 'requests': 0}
    template = FIXTURE.read_text(encoding='utf-8-sig')
    profile_dir = tempfile.mkdtemp(prefix='collector-browser-', dir=str(ROOT / 'build'))
    with sync_playwright() as playwright:
        context = playwright.chromium.launch_persistent_context(
            profile_dir, headless=True, executable_path=playwright.chromium.executable_path,
            viewport={'width': 1440, 'height': 900},
            args=['--disable-extensions-except=' + str(EXTENSION), '--load-extension=' + str(EXTENSION)])
        try:
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
                                  body=template.replace('__BAD_INDEX__', bad).replace('__DUPLICATE_INDEX__', duplicate))
                context.route('https://tracker.gg/**', fulfill)
            page = context.pages[0] if context.pages else context.new_page()
            page.goto(PROFILE, wait_until='domcontentloaded', timeout=30000)
            page.locator('#matchAnalyzerCollect').wait_for(state='visible', timeout=30000)
            page.wait_for_timeout(1000)
            def run():
                page.locator('#matchAnalyzerCollect').click()
                try:
                    page.wait_for_function(
                        "() => /Finished:|Collection stopped:|No extension response|Collector could not/.test(document.querySelector('#matchAnalyzerCollectStatus').textContent)",
                        timeout=120000)
                except BrowserTimeout:
                    print('Timed out; status:', page.locator('#matchAnalyzerCollectStatus').inner_text(), flush=True)
                    raise
                return page.locator('#matchAnalyzerCollectStatus').inner_text()
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
            outcome = run()
            print('Synthetic outcome:', outcome, 'details:', page.locator('#matchAnalyzerFailures').inner_text(), flush=True)
            assert 'Finished: 5 validated match(es), 0 issue(s).' in outcome, outcome
            assert page.locator('#matchAnalyzerCollected option').count() == 5
            assert '1/1/0' in page.locator('#matchAnalyzerResult').inner_text()
            page.locator('#matchAnalyzerCollected').select_option(index=4)
            assert 'Mock Map 5' in page.locator('#matchAnalyzerResult').inner_text()
            assert page.locator('#matchAnalyzerResult tbody tr').first.locator('td').nth(4).inner_text() == '1–0'
            page.screenshot(path=str(OUTPUT / 'synthetic-five.png'))
            print('PASS: five validated synthetic matches appear in the app', flush=True)
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
