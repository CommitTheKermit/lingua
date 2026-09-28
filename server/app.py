"""Anonymous translation API; books and user-written translations stay local."""
import hashlib
import hmac
import json
import os
import secrets
import sqlite3
import threading
import time
import urllib.error
import urllib.request
from concurrent.futures import Future
from datetime import datetime, timezone
from http import HTTPStatus

MAX_QUOTA = 200
REFILL_SECONDS = 120
REFILL_AMOUNT = 3


class ApiError(Exception):
    def __init__(self, status, code):
        self.status, self.code = status, code


def validate_request(value):
    if not isinstance(value, dict):
        raise ApiError(400, 'invalid_request')
    result = {}
    for key, maximum in [('text', 10000), ('context', 5000), ('instructions', 300)]:
        text = value.get(key, '')
        if not isinstance(text, str) or len(text) > maximum:
            raise ApiError(400, 'invalid_request')
        result[key] = text.strip()
    if not result['text']:
        raise ApiError(400, 'invalid_request')
    if value.get('sourceLang', 'EN') != 'EN' or value.get('targetLang', 'KO') != 'KO':
        raise ApiError(400, 'invalid_request')
    return result


class Store:
    def __init__(self, path, secret, monthly_chars=450000, now=time.time):
        self.db = sqlite3.connect(path, check_same_thread=False)
        self.db.execute('PRAGMA journal_mode=WAL')
        self.db.executescript('''
          CREATE TABLE IF NOT EXISTS sessions (token TEXT PRIMARY KEY, expires REAL);
          CREATE TABLE IF NOT EXISTS quota (id TEXT PRIMARY KEY, remaining INTEGER, updated REAL);
          CREATE TABLE IF NOT EXISTS budget (month TEXT PRIMARY KEY, characters INTEGER);
          CREATE TABLE IF NOT EXISTS cache (id TEXT PRIMARY KEY, translated TEXT, expires REAL);
          CREATE TABLE IF NOT EXISTS rate (id TEXT PRIMARY KEY, count INTEGER, expires REAL);
        ''')
        self.lock = threading.RLock()
        self.secret, self.monthly_chars, self.now = secret.encode(), monthly_chars, now

    def network_id(self, address):
        # 원 IP 대신 서버 비밀값으로 만든 해시만 저장한다.
        return 'net:' + hmac.new(self.secret, address.encode(), 'sha256').hexdigest()

    def rate_limit(self, identity, maximum):
        now = self.now()
        with self.lock, self.db:
            self.db.execute('DELETE FROM rate WHERE expires <= ?', (now,))
            row = self.db.execute('SELECT count FROM rate WHERE id=?', (identity,)).fetchone()
            if row and row[0] >= maximum:
                raise ApiError(429, 'rate_limited')
            self.db.execute('INSERT INTO rate VALUES (?,1,?) ON CONFLICT(id) DO UPDATE SET count=count+1', (identity, now + 60))

    def create_session(self, network):
        self.rate_limit('session:' + network, 10)
        token = secrets.token_urlsafe(32)
        with self.lock, self.db:
            self.db.execute('DELETE FROM sessions WHERE expires <= ?', (self.now(),))
            self.db.execute('INSERT INTO sessions VALUES (?,?)', (self.token_id(token), self.now() + 30*86400))
        return token

    @staticmethod
    def token_id(token):
        return hashlib.sha256(token.encode()).hexdigest()

    def authenticate(self, token):
        identity = self.token_id(token)
        with self.lock:
            row = self.db.execute('SELECT expires FROM sessions WHERE token=?', (identity,)).fetchone()
        if not row or row[0] <= self.now():
            raise ApiError(401, 'session_expired')
        return 'user:' + identity

    def _quota(self, identity):
        row = self.db.execute('SELECT remaining,updated FROM quota WHERE id=?', (identity,)).fetchone()
        remaining, updated = row if row else (MAX_QUOTA, self.now())
        intervals = max(0, int((self.now() - updated) // REFILL_SECONDS))
        return min(MAX_QUOTA, remaining + intervals * REFILL_AMOUNT), updated + intervals * REFILL_SECONDS

    def quota_status(self, user, network):
        with self.lock:
            remaining, updated = min((self._quota(user), self._quota(network)), key=lambda pair: pair[0])
        return dict(quotaRemaining=remaining, quotaMax=MAX_QUOTA,
                    nextRefillAtMs=int((updated + REFILL_SECONDS)*1000) if remaining < MAX_QUOTA else None)

    def reserve(self, user, network, characters):
        month = datetime.fromtimestamp(self.now(), timezone.utc).strftime('%Y-%m')
        with self.lock, self.db:
            quotas = [(identity, *self._quota(identity)) for identity in (user, network)]
            if any(remaining < 1 for _, remaining, _ in quotas):
                raise ApiError(429, 'quota_exhausted')
            used = self.db.execute('SELECT characters FROM budget WHERE month=?', (month,)).fetchone()
            if (used[0] if used else 0) + characters > self.monthly_chars:
                raise ApiError(429, 'budget_exhausted')
            for identity, remaining, updated in quotas:
                self.db.execute('INSERT OR REPLACE INTO quota VALUES (?,?,?)', (identity, remaining-1, updated))
            self.db.execute('INSERT INTO budget VALUES (?,?) ON CONFLICT(month) DO UPDATE SET characters=characters+excluded.characters', (month, characters))

    def refund_requests(self, user, network):
        with self.lock, self.db:
            for identity in (user, network):
                remaining, updated = self._quota(identity)
                self.db.execute('INSERT OR REPLACE INTO quota VALUES (?,?,?)', (identity, min(MAX_QUOTA, remaining+1), updated))
        # 시간 초과 요청도 외부에서 과금될 수 있어 문자 예산은 보수적으로 유지한다.

    def cached(self, key):
        with self.lock:
            row = self.db.execute('SELECT translated FROM cache WHERE id=? AND expires>?', (key, self.now())).fetchone()
        return row[0] if row else None

    def save_cache(self, key, translated):
        with self.lock, self.db:
            self.db.execute('DELETE FROM cache WHERE expires<=?', (self.now(),))
            self.db.execute('INSERT OR REPLACE INTO cache VALUES (?,?,?)', (key, translated, self.now()+30*86400))


class Translator:
    def __init__(self, store, provider, concurrent=4):
        self.store, self.provider = store, provider
        self.slots = threading.BoundedSemaphore(concurrent)
        self.lock, self.pending = threading.Lock(), {}

    def translate(self, request, user, network):
        key = hashlib.sha256(json.dumps(request, sort_keys=True, ensure_ascii=False).encode()).hexdigest()
        self.store.rate_limit('translate:' + network, 60)
        with self.lock:
            cached = self.store.cached(key)
            if cached is not None:
                return dict(translated=cached, cached=True, **self.store.quota_status(user, network))
            future = self.pending.get(key)
            owner = future is None
            if owner:
                future = Future()
                self.pending[key] = future
        if not owner:
            translated = future.result(timeout=30)
            return dict(translated=translated, cached=True, **self.store.quota_status(user, network))
        acquired, reserved = False, False
        try:
            acquired = self.slots.acquire(blocking=False)
            if not acquired:
                raise ApiError(503, 'busy')
            self.store.reserve(user, network, len(request['text']))
            reserved = True
            translated = self.provider(request)
            self.store.save_cache(key, translated)
            future.set_result(translated)
            return dict(translated=translated, cached=False, **self.store.quota_status(user, network))
        except Exception as error:
            if reserved:
                self.store.refund_requests(user, network)
            future.set_exception(error)
            raise
        finally:
            if acquired:
                self.slots.release()
            with self.lock:
                self.pending.pop(key, None)


def deepl_provider(api_key):
    endpoint = 'https://api-free.deepl.com/v2/translate' if api_key.endswith(':fx') else 'https://api.deepl.com/v2/translate'
    def translate(request):
        body = dict(text=[request['text']], source_lang='EN', target_lang='KO', preserve_formatting=True)
        if request['context']:
            body['context'] = request['context']
        if request['instructions']:
            body['custom_instructions'] = [request['instructions']]
        req = urllib.request.Request(endpoint, data=json.dumps(body).encode(), headers={
            'Authorization': 'DeepL-Auth-Key ' + api_key, 'Content-Type': 'application/json'})
        try:
            with urllib.request.urlopen(req, timeout=25) as response:
                translated = json.load(response)['translations'][0]['text']
            if not isinstance(translated, str):
                raise ValueError('invalid provider response')
            return translated
        except urllib.error.HTTPError as error:
            raise ApiError(502, 'provider_rejected_profile' if error.code == 400 and request['instructions'] else 'provider_error') from None
        except Exception:
            raise ApiError(502, 'provider_error') from None
    return translate


class Application:
    def __init__(self, store, translator, origins, trust_tunnel=False):
        self.store, self.translator = store, translator
        self.origins, self.trust_tunnel = set(origins), trust_tunnel

    def __call__(self, env, start_response):
        started = time.monotonic()
        origin = env.get('HTTP_ORIGIN', '')
        headers = [('Content-Type', 'application/json; charset=utf-8'), ('Cache-Control', 'no-store'), ('Vary', 'Origin')]
        if origin in self.origins:
            headers += [('Access-Control-Allow-Origin', origin), ('Access-Control-Allow-Headers', 'Content-Type, Authorization'), ('Access-Control-Allow-Methods', 'POST, OPTIONS')]
        status = 200
        try:
            path, method = env.get('PATH_INFO'), env.get('REQUEST_METHOD')
            if path == '/health' and method == 'GET':
                result = {'status': 'ok'}
            elif origin not in self.origins:
                raise ApiError(403, 'origin_denied')
            elif path not in ('/session', '/translate'):
                raise ApiError(404, 'not_found')
            elif method == 'OPTIONS':
                result = {}
            elif method != 'POST':
                raise ApiError(405, 'method_not_allowed')
            else:
                address = env.get('REMOTE_ADDR', '')
                if self.trust_tunnel:
                    # 전용 터널만 루프백 리스너에 연결한다. 외부 바인딩과 함께 쓰지 않는다.
                    address = env.get('HTTP_CF_CONNECTING_IP', address)
                network = self.store.network_id(address)
                if path == '/session':
                    result = {'token': self.store.create_session(network)}
                else:
                    auth = env.get('HTTP_AUTHORIZATION', '')
                    if not auth.startswith('Bearer ') or len(auth) > 200:
                        raise ApiError(401, 'session_expired')
                    user = self.store.authenticate(auth[7:])
                    if env.get('CONTENT_TYPE', '').split(';')[0] != 'application/json':
                        raise ApiError(415, 'json_required')
                    try:
                        length = int(env.get('CONTENT_LENGTH') or '0')
                        if not 0 < length <= 65536:
                            raise ApiError(413, 'request_too_large')
                        value = json.loads(env['wsgi.input'].read(length))
                    except (ValueError, UnicodeError):
                        raise ApiError(400, 'invalid_request') from None
                    result = self.translator.translate(validate_request(value), user, network)
        except ApiError as error:
            status, result = error.status, {'error': error.code}
        except Exception:
            status, result = 500, {'error': 'internal_error'}
        headers += [('Server-Timing', f'total;dur={(time.monotonic()-started)*1000:.1f}')]
        if status == 429:
            headers.append(('Retry-After', '60'))
        payload = json.dumps(result, ensure_ascii=False).encode()
        headers.append(('Content-Length', str(len(payload))))
        start_response(f'{status} {HTTPStatus(status).phrase}', headers)
        return [payload]


def main():
    from dotenv import load_dotenv
    from waitress import serve
    load_dotenv(os.environ.get('LINGUA_ENV_FILE', '.env'))
    key, secret = os.environ['DEEPL_API_KEY'], os.environ['SESSION_SECRET']
    if len(secret) < 32:
        raise ValueError('SESSION_SECRET must have at least 32 characters')
    store = Store(os.environ.get('DATABASE_PATH', 'lingua.sqlite'), secret,
                  int(os.environ.get('MONTHLY_CHAR_LIMIT', '450000')))
    origins = [x.strip() for x in os.environ['ALLOWED_ORIGINS'].split(',') if x.strip()]
    app = Application(store, Translator(store, deepl_provider(key)), origins,
                      os.environ.get('TRUST_TUNNEL') == '1')
    serve(app, host='127.0.0.1', port=int(os.environ.get('PORT', '8787')),
          threads=12, max_request_body_size=65536, channel_timeout=35,
          clear_untrusted_proxy_headers=True)


if __name__ == '__main__':
    main()
