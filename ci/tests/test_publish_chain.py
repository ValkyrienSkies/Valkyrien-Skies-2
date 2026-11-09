import hashlib
import importlib.util
import io
import json
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch
import zipfile


spec = importlib.util.spec_from_file_location('chain', Path(__file__).parents[1] / 'publish_chain.py')
chain = importlib.util.module_from_spec(spec)
spec.loader.exec_module(chain)


class FakeGitHub:
    def __init__(self):
        self.heads = {}
        self.commits = {}
        self.trees = {}
        self.updates = []
        self.fail = None
        self.race = False
        initial = {
            'native': '', 'api': '',
            'bridge': 'krunchx_commit_hash=old\nvs_phys_api_version=old\nuntouched=value\n',
            'core': 'krunch_version=old\nuntouched=value\n',
            'vs2': 'vs_core_version=old\nkrunch_version=old\nkrunch_api_version=old\nmod_version=2.4.13\nuntouched=value\n',
        }
        for index, (key, text) in enumerate(initial.items()):
            sha = str(index + 1) * 40
            tree = hashlib.sha1((key + text).encode()).hexdigest()
            self.heads[key] = sha
            self.trees[tree] = text
            self.commits[sha] = {'sha': sha, 'commit': {'tree': {'sha': tree}}, 'parents': []}

    def commit(self, key, ref):
        return self.commits[ref if ref in self.commits else self.heads[key]]

    def head(self, key):
        return self.heads[key]

    def properties(self, key, sha):
        return self.trees[self.commits[sha]['commit']['tree']['sha']]

    def statuses(self, key, sha):
        jobs = {'native': chain.NATIVE_JOBS, 'bridge': chain.BRIDGE_JOBS,
                'vs2': ('gradle_build', 'fabric_server_test', 'forge_server_test', 'publish_to_maven')}
        return {'ci/circleci: ' + name: {'state': 'failure' if self.fail == key else 'success',
                                       'context': 'ci/circleci: ' + name, 'target_url': 'https://ci.example/job'}
                for name in jobs.get(key, ('gradle_build', 'publish_to_maven'))}

    def call(self, key, path, method='GET', data=None, missing_ok=False):
        if path == 'git/trees':
            if [item['path'] for item in data['tree']] != ['gradle.properties']:
                raise AssertionError('Only dependency properties may change')
            text = data['tree'][0]['content']
            tree = hashlib.sha1((data['base_tree'] + text).encode()).hexdigest()
            self.trees[tree] = text
            return {'sha': tree}
        if path == 'git/commits':
            sha = hashlib.sha1(json.dumps(data, sort_keys=True).encode()).hexdigest()
            self.commits[sha] = {'sha': sha, 'commit': {'tree': {'sha': data['tree']}}, 'parents': data['parents']}
            return {'sha': sha}
        if path.startswith('git/refs/heads/'):
            if data['force'] or self.race or self.heads[key] not in self.commits[data['sha']]['parents']:
                raise chain.ChainError('Non-fast-forward update rejected')
            self.heads[key] = data['sha']
            self.updates.append((key, data['sha']))
            return {}
        raise AssertionError((key, path, method))


class ChainTests(unittest.TestCase):
    def setUp(self):
        self.github = FakeGitHub()
        self.manifest = chain.resolve(self.github, 'main', 'main', self.github.head('vs2'))
        self.directory = tempfile.TemporaryDirectory()
        self.addCleanup(self.directory.cleanup)
        self.output = Path(self.directory.name) / 'result.json'
        self.native_bytes = {name: ('native bytes ' + name).encode() for name in chain.NATIVE_FILES}
        self.native_entries = {name: {'size': len(data), 'sha256': hashlib.sha256(data).hexdigest()}
                               for name, data in self.native_bytes.items()}
        self.native_sha = self.github.head('native')
        self.corrupt_bundle = False

    def request(self, url, method='GET', data=None, headers=None, missing_ok=False):
        if url.endswith('artifact-manifest.txt'):
            lines = ['krunchx artifact manifest', 'commit: ' + self.native_sha, 'sha256  bytes  path']
            lines.extend(f'{entry["sha256"]}  {entry["size"]}  {name}' for name, entry in self.native_entries.items())
            return ('\n'.join(lines) + '\n').encode(), {}
        filename = url.rsplit('/', 1)[-1]
        if filename in self.native_bytes:
            return self.native_bytes[filename], {'Content-Length': str(len(self.native_bytes[filename]))}
        parts = url.removeprefix(chain.MAVEN + '/').split('/')
        artifact, version = parts[-3:-1]
        group = '.'.join(parts[:-3])
        if filename.endswith('.pom'):
            dependency = ''
            if artifact == 'physics_api_krunch':
                api_version = '1.0.0+' + self.github.head('api')[:10]
                dependency = ('<dependencies><dependency><groupId>org.valkyrienskies</groupId>'
                              f'<artifactId>physics_api</artifactId><version>{api_version}</version>'
                              '</dependency></dependencies>')
            return (f'<project xmlns="http://maven.apache.org/POM/4.0.0"><groupId>{group}</groupId>'
                    f'<artifactId>{artifact}</artifactId><version>{version}</version>{dependency}</project>').encode(), {}
        if method == 'HEAD':
            return b'', {'Content-Length': '100'}
        buffer = io.BytesIO()
        with zipfile.ZipFile(buffer, 'w') as archive:
            for name in chain.BUNDLED_FILES:
                archive.writestr(name, b'wrong' if self.corrupt_bundle else self.native_bytes[name])
        return buffer.getvalue(), {}

    def run_chain(self):
        with patch.object(chain, 'request', side_effect=self.request):
            return chain.run(self.github, self.manifest, self.output, timeout=1)

    def test_advances_only_pins_and_verifies_the_full_chain(self):
        result = self.run_chain()
        self.assertEqual(['bridge', 'core', 'vs2'], [key for key, sha in self.github.updates])
        self.assertEqual(set(chain.REPOSITORIES), set(result['published']))
        for key in ('bridge', 'core', 'vs2'):
            self.assertIn('untouched=value', self.github.properties(key, self.github.head(key)))
        properties = self.github.properties('vs2', self.github.head('vs2'))
        self.assertEqual(result['published']['core']['version'], chain.property_value(properties, 'vs_core_version'))
        self.assertEqual(result['published']['bridge']['version'], chain.property_value(properties, 'krunch_version'))

    def test_resume_reuses_commits_after_partial_publication_failure(self):
        self.github.fail = 'core'
        with self.assertRaisesRegex(chain.ChainError, 'core CI failed'):
            self.run_chain()
        self.assertEqual(['bridge', 'core'], [key for key, sha in self.github.updates])
        self.github.fail = None
        self.run_chain()
        self.run_chain()
        self.assertEqual(['bridge', 'core', 'vs2'], [key for key, sha in self.github.updates])

    def test_concurrent_branch_changes_are_preserved(self):
        newer = '9' * 40
        self.github.heads['core'] = newer
        self.github.commits[newer] = {'sha': newer, 'commit': {'tree': {'sha': 'unrelated'}}}
        with self.assertRaisesRegex(chain.ChainError, 'advanced outside'):
            self.run_chain()
        self.assertEqual(newer, self.github.head('core'))
        self.assertEqual(['bridge'], [key for key, sha in self.github.updates])

    def test_ref_update_race_never_forces_a_push(self):
        self.github.race = True
        with self.assertRaisesRegex(chain.ChainError, 'Non-fast-forward'):
            self.run_chain()
        self.assertEqual([], self.github.updates)

    def test_wrong_native_in_bridge_prevents_core_update(self):
        self.corrupt_bundle = True
        with self.assertRaisesRegex(chain.ChainError, 'selected native build'):
            self.run_chain()
        self.assertEqual(['bridge'], [key for key, sha in self.github.updates])

    def test_manifest_cannot_redirect_repository_writes(self):
        self.manifest['sources']['core']['repository'] = 'somewhere/else'
        with self.assertRaisesRegex(chain.ChainError, 'Unexpected repository'):
            self.run_chain()
        self.assertEqual([], self.github.updates)

    def test_manifest_requires_all_platforms(self):
        self.native_entries.pop('KrunchJniArm64.dll')
        with self.assertRaisesRegex(chain.ChainError, 'missing platform'):
            self.run_chain()
        self.assertEqual([], self.github.updates)

    def test_missing_ci_status_is_not_success(self):
        self.github.statuses = lambda *args: {}
        artifact = unittest.mock.Mock(return_value=True)
        times = iter([0, 1])
        with self.assertRaisesRegex(chain.ChainError, 'Timed out'):
            chain.wait_for(self.github, 'api', 'a' * 40, ('gradle_build',), artifact,
                           timeout=1, clock=lambda: next(times), sleep=lambda _: None)
        artifact.assert_not_called()

    def test_duplicate_properties_are_rejected(self):
        with self.assertRaisesRegex(chain.ChainError, 'exactly one'):
            chain.replace_properties('version=one\nversion=two\n', {'version': 'three'})

    def test_preserves_property_spacing_and_line_endings(self):
        self.assertEqual('version = next\r\n# keep\r\n',
                         chain.replace_properties('version = old\r\n# keep\r\n', {'version': 'next'}))

    def test_existing_native_build_is_not_retriggered(self):
        with patch.object(chain, 'native_manifest', return_value=None), patch.object(self.github, 'call') as calls:
            chain.start_native(self.github, self.native_sha)
            calls.assert_not_called()

    def test_new_native_build_tag_is_fixed_to_full_commit(self):
        self.github.statuses = lambda *args: {}
        with patch.object(chain, 'native_manifest', return_value=None), patch.object(self.github, 'call', return_value=None) as calls:
            chain.start_native(self.github, self.native_sha)
        data = calls.call_args.args[3]
        self.assertEqual('refs/tags/full-build-chain-' + self.native_sha, data['ref'])
        self.assertEqual(self.native_sha, data['sha'])


if __name__ == '__main__':
    unittest.main()
