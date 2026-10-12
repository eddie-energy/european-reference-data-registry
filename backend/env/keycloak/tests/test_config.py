"""Exercise the image's public environment contract without a running server."""
import json
import os
from pathlib import Path
import subprocess
import unittest


ROOT = Path(__file__).resolve().parents[1]


class ConfigurationTest(unittest.TestCase):
    def configure(self, **values):
        env = {key: value for key, value in os.environ.items()
               if not key.startswith('CEEDS_')}
        env.update(values)
        return subprocess.run(['bash', str(ROOT / 'bootstrap-config.sh')],
                              env=env, capture_output=True, text=True)

    def parsed(self, **values):
        result = self.configure(**values)
        self.assertEqual(0, result.returncode, result.stderr)
        return json.loads(result.stdout)

    def test_default_fhooe_without_demo_login(self):
        result = self.parsed()
        self.assertFalse(result['demoUser'])
        self.assertEqual([{'alias': 'fhooe', 'name': 'FHOOE', 'enabled': True,
                           'domains': [{'name': 'fh-ooe.at', 'verified': False}],
                           'attributes': {'ceeds_role': ['OPERATIONAL_ENTITY'],
                                          'ceeds_nations': ['']}}], result['organizations'])

    def test_three_orgs_preserve_empty_nation_position(self):
        result = self.parsed(CEEDS_BOOTSTRAP_ORGANIZATIONS='fhooe;eda;enedis',
                             CEEDS_BOOTSTRAP_ORGANIZATION_ROLES='OPERATIONAL_ENTITY;NDSF;NDSF',
                             CEEDS_BOOTSTRAP_ORGANIZATION_NATIONS=';AUT;FRA')
        self.assertEqual(['fhooe', 'eda', 'enedis'],
                         [org['alias'] for org in result['organizations']])
        self.assertEqual([[''], ['AUT'], ['FRA']],
                         [org['attributes']['ceeds_nations'] for org in result['organizations']])
        self.assertEqual([], result['organizations'][0]['domains'])

    def test_trailing_empty_position_and_multiple_values(self):
        result = self.parsed(CEEDS_BOOTSTRAP_ORGANIZATIONS='eda;other',
                             CEEDS_BOOTSTRAP_ORGANIZATION_ROLES='ndsf,operational_entity;',
                             CEEDS_BOOTSTRAP_ORGANIZATION_NATIONS='AUT, fra;')
        self.assertEqual(['NDSF', 'OPERATIONAL_ENTITY'],
                         result['organizations'][0]['attributes']['ceeds_role'])
        self.assertEqual(['AUT', 'FRA'], result['organizations'][0]['attributes']['ceeds_nations'])
        self.assertEqual([''], result['organizations'][1]['attributes']['ceeds_role'])

    def test_missing_companions_do_not_grant_role(self):
        result = self.parsed(CEEDS_BOOTSTRAP_ORGANIZATIONS='new-org')
        self.assertEqual({'ceeds_role': [''], 'ceeds_nations': ['']},
                         result['organizations'][0]['attributes'])

    def test_explicit_empty_org_list_disables_seed(self):
        self.assertEqual([], self.parsed(CEEDS_BOOTSTRAP_ORGANIZATIONS='')['organizations'])

    def test_reject_invalid_inputs(self):
        cases = [
            {'CEEDS_BOOTSTRAP_ORGANIZATIONS': 'one;two', 'CEEDS_BOOTSTRAP_ORGANIZATION_ROLES': 'NDSF'},
            {'CEEDS_BOOTSTRAP_ORGANIZATIONS': 'one;one'},
            {'CEEDS_BOOTSTRAP_ORGANIZATIONS': 'one;'},
            {'CEEDS_BOOTSTRAP_ORGANIZATIONS': 'one', 'CEEDS_BOOTSTRAP_ORGANIZATION_ROLES': 'ADMIN'},
            {'CEEDS_BOOTSTRAP_ORGANIZATIONS': 'one', 'CEEDS_BOOTSTRAP_ORGANIZATION_NATIONS': 'ceeds'},
            {'CEEDS_BOOTSTRAP_ORGANIZATIONS': 'one', 'CEEDS_BOOTSTRAP_ORGANIZATION_ROLES': 'NDSF'},
            {'CEEDS_BOOTSTRAP_DEMO_USER': 'yes'},
            {'CEEDS_FRONTEND_URLS': 'https://example.test;'},
            {'CEEDS_FRONTEND_URLS': 'javascript:alert(1)'},
            {'CEEDS_BOOTSTRAP_ORGANIZATIONS': '', 'CEEDS_BOOTSTRAP_ORGANIZATION_ROLES': 'NDSF'},
        ]
        for values in cases:
            with self.subTest(values=values):
                self.assertNotEqual(0, self.configure(**values).returncode)

    def test_frontend_urls_and_demo_opt_in(self):
        result = self.parsed(CEEDS_FRONTEND_URLS='https://registry.test/;https://other.test/app',
                             CEEDS_BOOTSTRAP_DEMO_USER='true')
        self.assertTrue(result['demoUser'])
        self.assertEqual(['https://registry.test', 'https://other.test/app'], result['frontendUrls'])


if __name__ == '__main__':
    unittest.main()
