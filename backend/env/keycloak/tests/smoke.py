"""Real-container acceptance tests. Usage: python3 tests/smoke.py IMAGE"""
import json
import http.client
import subprocess
import sys
import time
import unittest
import urllib.error
import urllib.parse
import urllib.request
import uuid


IMAGE = sys.argv.pop(1) if len(sys.argv) > 1 else 'ceeds-keycloak:test'


def docker(*args, **kwargs):
    result = subprocess.run(['docker', *args], capture_output=True, text=True, **kwargs)
    if result.returncode:
        raise RuntimeError(f'docker {args[0]} failed: {result.stderr}')
    return (result.stdout + (result.stderr if args[0] == 'logs' else '')).strip()


class ContainerBootstrapTest(unittest.TestCase):
    def setUp(self):
        self.name = 'ceeds-keycloak-test-' + uuid.uuid4().hex[:12]
        self.volume = self.name + '-data'
        docker('volume', 'create', self.volume)
        self.running = False

    def tearDown(self):
        if self.running:
            docker('rm', '-f', self.name)
        docker('volume', 'rm', self.volume)

    def start(self, path='', command_args=None, bootstrap=True, **env):
        if self.running:
            docker('stop', '--time', '20', self.name)
            docker('rm', self.name)
        values = {'KC_BOOTSTRAP_ADMIN_USERNAME': 'admin',
                  'KC_BOOTSTRAP_ADMIN_PASSWORD': 'test-admin-password',
                  'CEEDS_DIRECTORY_CLIENT_SECRET': 'test-directory-secret',
                  'KC_HTTP_RELATIVE_PATH': path or '/', **env}
        args = ['run', '-d', '--name', self.name, '-p', '127.0.0.1::8080',
                '--mount', f'type=volume,src={self.volume},dst=/opt/keycloak/data']
        if not bootstrap:
            args += ['--entrypoint', '/opt/keycloak/bin/kc.sh']
        for key, value in values.items():
            args += ['-e', f'{key}={value}']
        docker(*args, IMAGE, *(command_args or ['start-dev']))
        self.running = True
        port = docker('port', self.name, '8080/tcp').rsplit(':', 1)[1]
        self.base = f'http://127.0.0.1:{port}{path}'
        deadline = time.monotonic() + 240
        while time.monotonic() < deadline:
            state = json.loads(docker('inspect', '--format', '{{json .State}}', self.name))
            if not state['Running']:
                self.fail(docker('logs', self.name))
            try:
                self.request('/realms/master')
                health = state.get('Health', {}).get('Status')
                if not bootstrap or health is None or health == 'healthy':
                    return
            except urllib.error.HTTPError as error:
                error.close()
            except (urllib.error.URLError, TimeoutError, ConnectionError, http.client.RemoteDisconnected):
                pass
            time.sleep(2)
        self.fail('Startup timed out:\n' + docker('logs', self.name))

    def request(self, path, method='GET', body=None, token=None, form=None):
        headers = {}
        data = None
        if body is not None:
            headers['Content-Type'] = 'application/json'
            data = json.dumps(body).encode()
        if form is not None:
            data = urllib.parse.urlencode(form).encode()
            headers['Content-Type'] = 'application/x-www-form-urlencoded'
        if token:
            headers['Authorization'] = 'Bearer ' + token
        req = urllib.request.Request(self.base + path, data=data, headers=headers, method=method)
        with urllib.request.urlopen(req, timeout=15) as response:
            raw = response.read()
            return json.loads(raw) if raw else None

    def login(self):
        self.token = self.request('/realms/master/protocol/openid-connect/token', 'POST',
                                 form={'client_id': 'admin-cli', 'grant_type': 'password',
                                       'username': 'admin', 'password': 'test-admin-password'})['access_token']

    def admin(self, path, method='GET', body=None):
        return self.request('/admin/realms/ceeds' + path, method, body, self.token)

    def organizations(self):
        return {org['alias']: self.admin('/organizations/' + org['id'])
                for org in self.admin('/organizations?max=1000')}

    def test_defaults_and_existing_realm_reconciliation(self):
        self.start()
        self.request('/realms/ceeds')  # Fails on old image without mounted import/setup.
        self.login()
        orgs = self.organizations()
        self.assertEqual({'fhooe'}, orgs.keys())
        self.assertEqual(['OPERATIONAL_ENTITY'], orgs['fhooe']['attributes']['ceeds_role'])
        self.assertEqual([], self.admin('/users?username=ceeds&exact=true'))
        self.assertIn('org-default-attrs', self.admin('/events/config')['eventsListeners'])

        # Existing organizations and unrelated configuration must survive restarts.
        fhooe = orgs['fhooe']
        fhooe['name'] = 'Custom FHOOE'
        fhooe['attributes']['custom'] = ['preserve-me']
        self.admin('/organizations/' + fhooe['id'], 'PUT', fhooe)
        self.admin('/events/config', 'PUT', {'eventsListeners': ['jboss-logging', 'email']})
        # Cross the endpoint's 100-item bootstrap page boundary. Fully configured
        # unrelated orgs should not cost two Java admin processes each on restart.
        for index in range(101):
            self.admin('/organizations', 'POST', {'name': f'Existing {index:03}',
                       'alias': f'existing-{index:03}', 'enabled': True,
                       'attributes': {'ceeds_role': ['OPERATIONAL_ENTITY'],
                                      'ceeds_nations': [''], 'custom': ['preserve']}})
        self.admin('/organizations', 'POST', {'name': 'Unlisted', 'alias': 'unlisted',
                                             'enabled': True, 'attributes': {'custom': ['keep']}})
        self.start('/auth', command_args=['start-dev', '--http-relative-path=/auth'],
                   KC_HTTP_RELATIVE_PATH='/',
                   CEEDS_BOOTSTRAP_ORGANIZATIONS='fhooe;eda;enedis',
                   CEEDS_BOOTSTRAP_ORGANIZATION_ROLES='OPERATIONAL_ENTITY;NDSF;NDSF',
                   CEEDS_BOOTSTRAP_ORGANIZATION_NATIONS=';AUT;FRA',
                   CEEDS_BOOTSTRAP_DEMO_USER='true',
                   CEEDS_FRONTEND_URLS='https://registry.example.test',
                   CEEDS_DIRECTORY_CLIENT_SECRET='changed-secret')
        self.login()
        orgs = self.organizations()
        self.assertEqual({'fhooe', 'eda', 'enedis', 'unlisted'} |
                         {f'existing-{index:03}' for index in range(101)}, orgs.keys())
        self.assertEqual(fhooe['id'], orgs['fhooe']['id'])
        self.assertEqual('Custom FHOOE', orgs['fhooe']['name'])
        self.assertEqual(['preserve-me'], orgs['fhooe']['attributes']['custom'])
        self.assertEqual(['AUT'], orgs['eda']['attributes']['ceeds_nations'])
        self.assertEqual(['FRA'], orgs['enedis']['attributes']['ceeds_nations'])
        self.assertEqual([''], orgs['unlisted']['attributes']['ceeds_role'])
        self.assertEqual(['keep'], orgs['unlisted']['attributes']['custom'])
        self.assertIn('email', self.admin('/events/config')['eventsListeners'])
        user = self.admin('/users?username=ceeds&exact=true')[0]
        members = self.admin('/organizations/' + orgs['fhooe']['id'] + '/members')
        self.assertIn(user['id'], [member['id'] for member in members])
        frontend = self.admin('/clients?clientId=ceeds-frontend')[0]
        self.assertEqual(['https://registry.example.test/*'], frontend['redirectUris'])

        # Tokens use the actual organization mapper configured by bootstrap.
        self.admin('/clients', 'POST', {'clientId': 'smoke-login', 'publicClient': True,
                                      'directAccessGrantsEnabled': True,
                                      'defaultClientScopes': ['basic', 'profile', 'email', 'organization']})
        access = self.request('/realms/ceeds/protocol/openid-connect/token', 'POST',
                              form={'client_id': 'smoke-login', 'grant_type': 'password',
                                    'username': 'ceeds', 'password': 'ceeds'})['access_token']
        import base64
        claims = json.loads(base64.urlsafe_b64decode(access.split('.')[1] + '==='))
        self.assertEqual(orgs['fhooe']['id'], claims['organization']['fhooe']['id'])
        self.assertEqual(['OPERATIONAL_ENTITY'], claims['organization']['fhooe']['ceeds_role'])
        self.admin('/organizations/' + orgs['eda']['id'] + '/members', 'POST', user['id'])
        access = self.request('/realms/ceeds/protocol/openid-connect/token', 'POST',
                              form={'client_id': 'smoke-login', 'grant_type': 'password',
                                    'username': 'ceeds', 'password': 'ceeds',
                                    'scope': 'organization:eda'})['access_token']
        claims = json.loads(base64.urlsafe_b64decode(access.split('.')[1] + '==='))
        self.assertEqual(['NDSF'], claims['organization']['eda']['ceeds_role'])
        self.assertEqual(['AUT'], claims['organization']['eda']['ceeds_nations'])
        directory_token = self.request('/realms/ceeds/protocol/openid-connect/token', 'POST',
                                       form={'client_id': 'ceeds-directory', 'grant_type': 'client_credentials',
                                             'client_secret': 'changed-secret'})['access_token']
        self.request('/admin/realms/ceeds/organizations', token=directory_token)
        with self.assertRaises(urllib.error.HTTPError) as forbidden:
            self.request('/admin/realms/ceeds/organizations', 'POST',
                         body={'name': 'Forbidden', 'alias': 'forbidden'}, token=directory_token)
        self.assertEqual(403, forbidden.exception.code)
        forbidden.exception.close()

        self.start(CEEDS_BOOTSTRAP_ORGANIZATIONS='eda',
                   CEEDS_BOOTSTRAP_ORGANIZATION_ROLES='NDSF,OPERATIONAL_ENTITY',
                   CEEDS_BOOTSTRAP_ORGANIZATION_NATIONS='FRA,GER')
        self.login()
        orgs = self.organizations()
        self.assertEqual(105, len(orgs))
        self.assertEqual(['FRA', 'GER'], orgs['eda']['attributes']['ceeds_nations'])
        self.assertEqual(['FRA'], orgs['enedis']['attributes']['ceeds_nations'])
        self.assertEqual(user['id'], self.admin('/users?username=ceeds&exact=true')[0]['id'])

    def test_explicit_empty_seed_and_demo_disabled(self):
        self.start(command_args=['start', '--hostname=http://localhost'],
                   CEEDS_BOOTSTRAP_ORGANIZATIONS='')
        self.login()
        self.assertEqual({}, self.organizations())
        self.assertEqual([], self.admin('/users?username=ceeds&exact=true'))

    def test_invalid_input_exits_before_server_start(self):
        result = subprocess.run(['docker', 'run', '--rm',
                 '-e', 'KC_BOOTSTRAP_ADMIN_USERNAME=admin',
                 '-e', 'KC_BOOTSTRAP_ADMIN_PASSWORD=test-password',
                 '-e', 'CEEDS_BOOTSTRAP_ORGANIZATIONS=one;two',
                 '-e', 'CEEDS_BOOTSTRAP_ORGANIZATION_ROLES=NDSF',
                 IMAGE, 'start-dev'], capture_output=True, text=True, timeout=30)
        self.assertNotEqual(0, result.returncode)
        self.assertIn('list length', result.stderr)
        self.assertNotIn('Listening on:', result.stdout)

    def test_other_keycloak_commands_need_no_bootstrap_credentials(self):
        self.assertIn('Keycloak', docker('run', '--rm', IMAGE, '--version'))

    def test_production_rejects_demo_credentials(self):
        result = subprocess.run(['docker', 'run', '--rm',
                 '-e', 'KC_BOOTSTRAP_ADMIN_USERNAME=admin',
                 '-e', 'KC_BOOTSTRAP_ADMIN_PASSWORD=test-password',
                 '-e', 'CEEDS_DIRECTORY_CLIENT_SECRET=test-secret',
                 '-e', 'CEEDS_BOOTSTRAP_DEMO_USER=true',
                 IMAGE, 'start'], capture_output=True, text=True, timeout=45)
        self.assertNotEqual(0, result.returncode)
        self.assertIn('only allowed with start-dev', result.stderr)

    def test_termination_during_bootstrap_stops_server(self):
        docker('run', '-d', '--name', self.name,
               '-e', 'KC_BOOTSTRAP_ADMIN_USERNAME=admin',
               '-e', 'KC_BOOTSTRAP_ADMIN_PASSWORD=test-password', IMAGE, 'start-dev')
        self.running = True
        # Wait for both processes to start, not for setup to finish.
        deadline = time.monotonic() + 30
        while 'Waiting for local Keycloak' not in docker('logs', self.name):
            if time.monotonic() >= deadline:
                self.fail('Bootstrap did not start')
            time.sleep(0.2)
        docker('stop', '--time', '15', self.name)
        state = json.loads(docker('inspect', '--format', '{{json .State}}', self.name))
        self.assertFalse(state['Running'])
        self.assertEqual(143, state['ExitCode'])  # SIGTERM forwarded; no forced SIGKILL.

    def test_bad_existing_admin_credentials_timeout_and_stop_container(self):
        # Create the existing master admin independently of CEEDS setup.
        self.start(bootstrap=False)
        started = time.monotonic()
        with self.assertRaises(AssertionError) as failure:
            self.start(KC_BOOTSTRAP_ADMIN_PASSWORD='wrong-password')
        self.assertIn('CEEDS bootstrap failed (exit 124', str(failure.exception))
        self.assertLess(time.monotonic() - started, 220)
        state = json.loads(docker('inspect', '--format', '{{json .State}}', self.name))
        self.assertFalse(state['Running'])
        self.assertEqual(124, state['ExitCode'])


if __name__ == '__main__':
    unittest.main()
