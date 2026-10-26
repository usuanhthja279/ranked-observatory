"""Capture Tracker match responses from normal Brave page navigation.

Uses a dedicated local profile; never copies cookies or solves challenges.
Existing IndiaStudyCapture gzip files are reused without requesting them again.
"""
import argparse
import asyncio
import gzip
import json
import os
import re
import subprocess
import sys
from datetime import datetime, timezone
from email.utils import parsedate_to_datetime
from pathlib import Path
from urllib.parse import urlparse

from playwright.async_api import async_playwright, TimeoutError as BrowserTimeout


def now():
    return datetime.now(timezone.utc).isoformat()


def read(path):
    with (gzip.open(path, 'rt', encoding='utf-8-sig') if path.suffix == '.gz'
          else path.open(encoding='utf-8-sig')) as f:
        return json.load(f)


def save(path, value):
    temp = path.with_suffix(path.suffix + '.partial')
    temp.write_text(json.dumps(value, ensure_ascii=False, indent=2), encoding='utf-8')
    temp.replace(path)


def retry_deadline(value):
    try:
        return datetime.now(timezone.utc).timestamp() + max(0, float(value))
    except (ValueError, TypeError):
        try:
            return parsedate_to_datetime(value).timestamp()
        except (ValueError, TypeError, OverflowError):
            return datetime.now(timezone.utc).timestamp() + 120


def brave_path():
    candidates = [Path(os.environ.get('LOCALAPPDATA', '')) / 'BraveSoftware/Brave-Browser/Application/brave.exe',
                  Path(os.environ.get('PROGRAMFILES', '')) / 'BraveSoftware/Brave-Browser/Application/brave.exe']
    return next((str(p) for p in candidates if p.is_file()), None)


class Collector:
    def __init__(self, args):
        self.args = args
        self.root = args.study.resolve()
        self.plan = list(dict.fromkeys(read(self.root / 'capture-plan.json')['uniqueMatches']))
        if any(not re.fullmatch(r'[a-fA-F0-9-]{36}', m) for m in self.plan):
            raise ValueError('Invalid match ID in capture plan')
        self.failures = {}
        self.downloaded = 0
        self.state = dict(startedAt=now(), source='brave-page-network', total=len(self.plan))

    def path(self, match):
        return self.root / 'matches' / (match + '.json.gz')

    def status(self, phase, **extra):
        completed = sum(self.path(m).is_file() for m in self.plan)
        self.state.update(updatedAt=now(), phase=phase, complete=completed,
                          failed=len(self.failures), failures=self.failures, **extra)
        save(self.root / 'browser-capture-status.json', self.state)
        save(self.root / 'capture-status.json', self.state)
        print(json.dumps({k: self.state.get(k) for k in ('updatedAt', 'phase', 'complete', 'total', 'currentMatch', 'retryAt', 'detail')}, ensure_ascii=True), flush=True)

    async def capture(self, page, match):
        loop = asyncio.get_running_loop()
        result = loop.create_future()
        network = []
        browser_requests = {}
        cdp = await page.context.new_cdp_session(page)
        await cdp.send('Network.enable')

        def request_sent(event):
            url = urlparse(event['request']['url'])
            if url.hostname == 'api.tracker.gg':
                browser_requests[event['requestId']] = url.path

        def response_details(event):
            path = browser_requests.get(event['requestId'])
            if path:
                headers = {k.lower(): v for k, v in event.get('headers', {}).items()}
                network.append(dict(path=path, status=event.get('statusCode'),
                                    retryAfter=headers.get('retry-after'), source='browser-network-details'))

        def loading_failed(event):
            path = browser_requests.get(event['requestId'])
            if path:
                network.append(dict(path=path, error=event.get('errorText'),
                                    cors=event.get('corsErrorStatus'), source='browser-network-details'))

        cdp.on('Network.requestWillBeSent', request_sent)
        cdp.on('Network.responseReceivedExtraInfo', response_details)
        cdp.on('Network.loadingFailed', loading_failed)

        def failed(request):
            url = urlparse(request.url)
            if url.hostname == 'api.tracker.gg':
                network.append(dict(path=url.path, error=request.failure))

        async def receive(response):
            url = urlparse(response.url)
            if url.hostname == 'api.tracker.gg':
                network.append(dict(path=url.path, status=response.status))
            if url.hostname != 'api.tracker.gg' or url.path.rstrip('/') != '/api/v2/valorant/standard/matches/' + match:
                return
            if result.done():
                return
            info = dict(status=response.status, headers=await response.all_headers())
            if response.status == 200:
                try:
                    info['payload'] = await response.json()
                except Exception as exc:
                    info['error'] = type(exc).__name__
            if not result.done():
                result.set_result(info)

        page.on('response', receive)
        page.on('requestfailed', failed)
        try:
            try:
                await page.goto('https://tracker.gg/valorant/match/' + match,
                                wait_until='domcontentloaded', timeout=45000)
            except BrowserTimeout:
                pass  # The API response can still arrive after document navigation times out.
            try:
                return await asyncio.wait_for(asyncio.shield(result), timeout=75)
            except asyncio.TimeoutError:
                text = (await page.locator('body').inner_text(timeout=5000))[:3000]
                lower = text.lower()
                challenge = any(s in lower for s in ('verify you are human', 'checking your browser', 'just a moment', 'cloudflare ray'))
                actual = next((r for r in reversed(network) if r.get('path', '').endswith('/' + match) and r.get('status', 0) >= 400), None)
                if actual:
                    return dict(status=actual['status'], headers={'retry-after': actual.get('retryAfter') or ''}, network=network)
                return dict(status=None, error='human_challenge' if challenge else 'no_match_response',
                            pageTitle=await page.title(), network=network)
        finally:
            page.remove_listener('response', receive)
            page.remove_listener('requestfailed', failed)
            await cdp.detach()
            if not result.done():
                result.cancel()

    async def run(self):
        remaining = [m for m in self.plan if not self.path(m).is_file()]
        if not remaining:
            self.status('complete', currentMatch=None, retryAt=None)
            return 0
        previous = self.root / 'browser-capture-status.json'
        if previous.exists():
            prior = read(previous)
            until = prior.get('retryAt')
            if until and datetime.fromisoformat(until).timestamp() > datetime.now(timezone.utc).timestamp():
                self.status('waiting_retry', retryAt=until, detail='Persisted server retry deadline has not elapsed')
                return 2
        executable = self.args.brave or brave_path()
        if not executable:
            raise RuntimeError('Brave executable not found; supply --brave PATH')
        profile = Path('data/browser-profiles/tracker-study').resolve()
        profile.mkdir(parents=True, exist_ok=True)
        async with async_playwright() as pw:
            context = await pw.chromium.launch_persistent_context(
                str(profile), executable_path=executable, headless=False,
                viewport={'width': 1280, 'height': 850}, accept_downloads=False)
            page = context.pages[0] if context.pages else await context.new_page()
            try:
                for match in remaining:
                    self.status('navigating', currentMatch=match, retryAt=None, httpStatus=None, detail=None)
                    for attempt in range(3):
                        result = await self.capture(page, match)
                        code = result.get('status')
                        if code == 200:
                            payload = result.get('payload', {})
                            data = payload.get('data', {})
                            if data.get('attributes', {}).get('id') != match:
                                self.status('paused_invalid_response', detail='Response match ID did not match requested ID')
                                return 3
                            target = self.path(match)
                            temp = target.with_suffix(target.suffix + '.partial')
                            with gzip.open(temp, 'wt', encoding='utf-8') as f:
                                json.dump(payload, f, ensure_ascii=False, separators=(',', ':'))
                            temp.replace(target)
                            self.downloaded += 1
                            self.status('captured', retryAt=None, httpStatus=200, detail='HTTP 200 match JSON saved')
                            break
                        if code == 429 or (code is not None and code >= 500):
                            deadline = retry_deadline(result.get('headers', {}).get('retry-after'))
                            until = datetime.fromtimestamp(deadline, timezone.utc).isoformat()
                            self.status('waiting_retry', retryAt=until, httpStatus=code,
                                        detail='Server requested backoff; no further match navigation before retryAt')
                            if self.args.probe:
                                return 2
                            await page.goto('about:blank')
                            while datetime.now(timezone.utc).timestamp() < deadline:
                                await asyncio.sleep(min(30, max(0, deadline - datetime.now(timezone.utc).timestamp())))
                            continue
                        if code is None and result.get('error') == 'no_match_response' and attempt == 0:
                            self.status('retrying_network', detail='Transient browser network failure; one retry after 30 seconds')
                            await asyncio.sleep(30)
                            continue
                        self.failures[match] = dict(httpStatus=code, reason=result.get('error', 'access_denied'),
                                                    network=result.get('network', []))
                        await page.screenshot(path=str(self.root / 'browser-capture-blocker.png'))
                        self.status('paused_access', httpStatus=code,
                                    detail='Browser access requires attention; no challenge bypass or repeated navigation')
                        return 3
                    else:
                        self.status('paused_retry_limit', detail='Three server retry responses; saved deadline retained')
                        return 2
                    if self.args.probe:
                        self.status('probe_success', detail='One previously missing match captured through Brave')
                        await page.screenshot(path=str(self.root / 'browser-probe-success.png'))
                        return 0
                    await asyncio.sleep(self.args.delay)
                self.status('complete', currentMatch=None, retryAt=None, detail=None)
                return 0
            finally:
                await context.close()


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--study', type=Path, default=Path('data/studies/india-2026-10-23'))
    parser.add_argument('--brave', help='Installed Brave executable')
    parser.add_argument('--probe', action='store_true', help='Capture exactly one previously missing match')
    parser.add_argument('--analyze', action='store_true', help='Run India analysis only after the complete match plan is captured')
    parser.add_argument('--delay', type=float, default=5, help='Delay between match page navigations, minimum 3 seconds')
    args = parser.parse_args()
    args.delay = max(3, args.delay)
    root = args.study.resolve()
    if not root.is_relative_to(Path('data/studies').resolve()):
        parser.error('Study must be within data/studies')
    if args.analyze and root != Path('data/studies/india-2026-10-23').resolve():
        parser.error('--analyze currently supports the frozen India study only')
    with (root / 'browser-collector.lock').open('a+b') as lock:
        if sys.platform == 'win32':
            import msvcrt
            lock.write(b'0'); lock.flush(); lock.seek(0)
            msvcrt.locking(lock.fileno(), msvcrt.LK_NBLCK, 1)
        else:
            import fcntl
            fcntl.flock(lock.fileno(), fcntl.LOCK_EX | fcntl.LOCK_NB)
        try:
            result = asyncio.run(Collector(args).run())
            if result == 0 and args.analyze and not args.probe:
                subprocess.run([sys.executable, str(Path(__file__).with_name('analyze_india_study.py'))], check=True)
            return result
        except KeyboardInterrupt:
            return 130


if __name__ == '__main__':
    sys.exit(main())


