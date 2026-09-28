import io, json, tempfile, threading, unittest
from concurrent.futures import ThreadPoolExecutor
from unittest.mock import patch
from app import ApiError, Application, Store, Translator, validate_request, deepl_provider

class ApiTests(unittest.TestCase):
    def setUp(self):
        self.clock = 1800000000
        self.store = Store(':memory:', 'test-only-secret', now=lambda: self.clock)
        self.network = self.store.network_id('test-network')
        self.token = self.store.create_session(self.network)
        self.user = self.store.authenticate(self.token)
        self.calls = []
        def provider(request):
            self.calls.append(request)
            return '번역 ' + request['instructions']
        self.translator = Translator(self.store, provider)
        self.app = Application(self.store, self.translator, ['https://web.example'])
    def tearDown(self):
        self.store.db.close()
    def request(self, data=None, path='/translate', token=True, origin='https://web.example', method='POST'):
        body = json.dumps(data or {'text': 'Hello.'}).encode()
        env = {'REQUEST_METHOD': method, 'PATH_INFO': path, 'HTTP_ORIGIN': origin,
               'HTTP_AUTHORIZATION': 'Bearer ' + self.token if token else '', 'REMOTE_ADDR': 'test-network',
               'CONTENT_TYPE': 'application/json', 'CONTENT_LENGTH': str(len(body)), 'wsgi.input': io.BytesIO(body)}
        response = []
        body = b''.join(self.app(env, lambda status, headers: response.append((status, dict(headers)))))
        return response[0], json.loads(body)
    def test_origin_and_authentication(self):
        self.assertTrue(self.request(origin='https://other.example')[0][0].startswith('403'))
        self.assertTrue(self.request(token=False)[0][0].startswith('401'))
        self.assertEqual(self.calls, [])
    def test_cache_separates_context_and_instructions(self):
        for data in [{'text':'Hello.'}, {'text':'Hello.'}, {'text':'Hello.', 'context':'40K'}, {'text':'Hello.', 'instructions':'존댓말'}]:
            status, result = self.request(data)
            self.assertTrue(status[0].startswith('200'))
        self.assertEqual(len(self.calls), 3)
        self.assertEqual(result['quotaRemaining'], 197)
    def test_bad_inputs(self):
        for value in [[], {'text':''}, {'text':1}, {'text':'a'*10001}, {'text':'Hi', 'instructions':'x'*301}, {'text':'Hi', 'sourceLang':'DE'}]:
            with self.assertRaises(ApiError): validate_request(value)
    def test_session_expiry(self):
        self.clock += 31*86400
        with self.assertRaises(ApiError): self.store.authenticate(self.token)
    def test_network_quota_survives_new_session(self):
        for _ in range(200): self.store.reserve(self.user, self.network, 1)
        another = self.store.authenticate(self.store.create_session(self.network))
        with self.assertRaises(ApiError): self.store.reserve(another, self.network, 1)
        self.clock += 120
        self.store.reserve(another, self.network, 1)
        self.assertEqual(self.store.quota_status(another, self.network)['quotaRemaining'], 2)
    def test_budget_atomic(self):
        self.store.monthly_chars = 10
        def reserve(_):
            try:
                self.store.reserve(self.user, self.network, 6)
                return True
            except ApiError: return False
        with ThreadPoolExecutor(max_workers=8) as pool: self.assertEqual(sum(pool.map(reserve, range(8))), 1)
    def test_single_flight(self):
        started, release = threading.Event(), threading.Event()
        def provider(request):
            self.calls.append(request)
            started.set()
            release.wait(2)
            return '동일 결과'
        translator = Translator(self.store, provider)
        request = validate_request({'text':'Same.'})
        with ThreadPoolExecutor(max_workers=8) as pool:
            first = pool.submit(translator.translate, request, self.user, self.network)
            self.assertTrue(started.wait(1))
            rest = [pool.submit(translator.translate, request, self.user, self.network) for _ in range(7)]
            release.set()
            results = [first.result()] + [future.result() for future in rest]
        self.assertEqual(len(self.calls), 1)
        self.assertTrue(all(x['translated'] == '동일 결과' for x in results))
        self.assertEqual(self.store.quota_status(self.user, self.network)['quotaRemaining'], 199)
    def test_failure_refunds_request(self):
        def fail(_): raise ApiError(502, 'provider_error')
        translator = Translator(self.store, fail)
        with self.assertRaises(ApiError): translator.translate(validate_request({'text':'Hi.'}), self.user, self.network)
        self.assertEqual(self.store.quota_status(self.user, self.network)['quotaRemaining'], 200)
        self.assertEqual(translator.pending, {})
        self.assertEqual(self.request()[1]['quotaRemaining'], 199)
    def test_restart(self):
        with tempfile.TemporaryDirectory() as folder:
            path = folder + '/state.sqlite'
            first = Store(path, 'test-only-secret', now=lambda:self.clock)
            token = first.create_session('network')
            user = first.authenticate(token)
            first.reserve(user, 'network', 20)
            first.save_cache('key', '저장된 번역')
            first.db.close()
            second = Store(path, 'test-only-secret', now=lambda:self.clock)
            self.assertEqual(second.authenticate(token), user)
            self.assertEqual(second.quota_status(user, 'network')['quotaRemaining'], 199)
            self.assertEqual(second.cached('key'), '저장된 번역')
            second.db.close()
    def test_provider_payload(self):
        captured = []
        def open_request(request, timeout):
            captured.append(json.loads(request.data))
            return io.BytesIO(json.dumps({'translations':[{'text':'결과'}]}).encode())
        with patch('urllib.request.urlopen', open_request):
            result = deepl_provider('test-placeholder:fx')(validate_request({'text':'Hi.', 'context':'fiction', 'instructions':'존댓말'}))
        self.assertEqual(result, '결과')
        self.assertEqual(captured[0]['custom_instructions'], ['존댓말'])
        self.assertEqual(captured[0]['context'], 'fiction')
    def test_health_preflight(self):
        self.assertEqual(self.request(path='/health',method='GET',origin='')[1], {'status':'ok'})
        response,_ = self.request(method='OPTIONS')
        self.assertEqual(response[1]['Access-Control-Allow-Origin'], 'https://web.example')
    def test_busy(self):
        self.translator.slots = threading.BoundedSemaphore(0)
        response,result = self.request()
        self.assertTrue(response[0].startswith('503'))
        self.assertEqual(result['error'], 'busy')
        self.assertEqual(self.store.quota_status(self.user,self.network)['quotaRemaining'], 200)
