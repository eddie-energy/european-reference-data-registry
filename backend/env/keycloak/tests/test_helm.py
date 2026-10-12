"""Render the deployment contract; the source chart needs an explicit image tag."""
from pathlib import Path
import shutil
import subprocess
import unittest


REPO = Path(__file__).resolve().parents[4]


@unittest.skipUnless(shutil.which('helm'), 'Helm is not installed')
class HelmDeploymentTest(unittest.TestCase):
    def render(self, *args):
        return subprocess.run(['helm', 'template', 'test', str(REPO / 'helm'), *args],
                              capture_output=True, text=True)

    def test_source_chart_requires_available_image_tag(self):
        result = self.render()
        self.assertNotEqual(0, result.returncode)
        self.assertIn('keycloak.image.version', result.stderr)

    def test_explicit_tag_and_org_environment_render_without_mounts(self):
        result = self.render('--set-string', 'keycloak.image.version=snapshot-tested',
                             '--set-string', 'keycloak.bootstrap.organizations=fhooe;eda;enedis',
                             '--set-string', 'keycloak.bootstrap.roles=OPERATIONAL_ENTITY;NDSF;NDSF',
                             '--set-string', 'keycloak.bootstrap.nations=;AUT;FRA')
        self.assertEqual(0, result.returncode, result.stderr)
        self.assertIn('ghcr.io/eddie-energy/ceeds-keycloak:snapshot-tested', result.stdout)
        self.assertIn('value: ";AUT;FRA"', result.stdout)
        self.assertNotIn('realm-config', result.stdout)
        self.assertNotIn('kind: Job', result.stdout)


if __name__ == '__main__':
    unittest.main()
