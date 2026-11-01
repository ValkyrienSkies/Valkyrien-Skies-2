import argparse
import base64
import hashlib
import io
import json
import os
from pathlib import Path
import re
import time
import urllib.error
import urllib.parse
import urllib.request
import xml.etree.ElementTree as ET
import zipfile


MAVEN = 'https://maven.valkyrienskies.org'
REPOSITORIES = {
    'native': ('StewStrong/KrunchX', 'main'),
    'api': ('ValkyrienSkies/Valkyrien-Skies-Physics-API', 'main'),
    'bridge': ('ValkyrienSkies/Valkyrien-Skies-Krunch-Physics-API', 'main'),
    'core': ('ValkyrienSkies/vs-core', 'main'),
    'vs2': ('ValkyrienSkies/Valkyrien-Skies-2', '1.20.1/playtest'),
}
NATIVE_FILES = {'KrunchJni64.dll', 'KrunchJniArm64.dll', 'libKrunchJni64.so',
                'libKrunchJniarm64.so', 'libKrunchJni64.dylib', 'libKrunchJniarm64.dylib'}
BUNDLED_FILES = NATIVE_FILES - {'KrunchJniArm64.dll'}
NATIVE_JOBS = ('build-ubuntu-x86-64', 'build-ubuntu-arm-64', 'build-windows-x86-64',
               'build-windows-arm-64', 'build-mac-os-x86-64', 'build-mac-os-64', 'commit_artifacts')
BRIDGE_JOBS = ('test-ubuntu-x86-64', 'test-ubuntu-arm-64', 'test-ubuntu-24-x86-64',
               'test-ubuntu-20-x86-64', 'test-mac-os-x86-64', 'test-mac-os-64', 'publish_to_maven')


class ChainError(RuntimeError):
    pass


def request(url, method='GET', data=None, headers=None, missing_ok=False):
    payload = None if data is None else json.dumps(data).encode()
    request_headers = {'User-Agent': 'Valkyrien-Skies-dependency-publisher'}
    if payload is not None:
        request_headers['Content-Type'] = 'application/json'
    request_headers.update(headers or {})
    req = urllib.request.Request(url, data=payload, method=method, headers=request_headers)
    try:
        with urllib.request.urlopen(req, timeout=90) as response:
            body = response.read(200 * 1024 * 1024 + 1)
            if len(body) > 200 * 1024 * 1024:
                raise ChainError('Artifact exceeds the download limit')
            return body, response.headers
    except urllib.error.HTTPError as error:
        if error.code == 404 and missing_ok:
            return None
        raise ChainError(f'{method} {url}: HTTP {error.code}') from None


class GitHub:
    def __init__(self):
        self.token = os.environ.get('VS_CHAIN_GITHUB_TOKEN')
        self.native_token = os.environ.get('VS_CHAIN_NATIVE_TOKEN') or self.token
        if not self.token or not self.native_token:
            raise ChainError('Configure the dependency-publishing CircleCI context tokens first')

    def call(self, key, path, method='GET', data=None, missing_ok=False):
        repo = REPOSITORIES[key][0]
        result = request(f'https://api.github.com/repos/{repo}/{path}', method, data,
                         {'Authorization': 'Bearer ' + (self.native_token if key == 'native' else self.token),
                          'Accept': 'application/vnd.github+json', 'X-GitHub-Api-Version': '2022-11-28'},
                         missing_ok)
        return None if result is None else json.loads(result[0])

    def commit(self, key, ref):
        return self.call(key, 'commits/' + urllib.parse.quote(ref, safe=''))

    def head(self, key):
        return self.commit(key, REPOSITORIES[key][1])['sha']

    def properties(self, key, sha):
        result = self.call(key, 'contents/gradle.properties?ref=' + sha)
        return base64.b64decode(result['content']).decode('utf-8')

    def statuses(self, key, sha):
        result = self.call(key, f'commits/{sha}/status?per_page=100')
        return {item['context']: item for item in reversed(result['statuses'])}


def write_json(path, value):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(value, indent=2, sort_keys=True) + '\n', encoding='utf-8')


def validate_manifest(manifest):
    if manifest.get('schema') != 1 or set(manifest.get('sources', {})) != set(REPOSITORIES):
        raise ChainError('Invalid chain manifest')
    for key, source in manifest['sources'].items():
        if (source.get('repository'), source.get('branch')) != REPOSITORIES[key]:
            raise ChainError(f'Unexpected repository or target branch for {key}')
        if not re.fullmatch('[0-9a-f]{40}', source.get('sha', '')):
            raise ChainError(f'Invalid resolved commit for {key}')
    return manifest


def resolve(github, native_ref, api_ref, controller_sha):
    sources = {}
    for key, (repository, branch) in REPOSITORIES.items():
        ref = {'native': native_ref, 'api': api_ref, 'vs2': controller_sha}.get(key, branch)
        sha = github.commit(key, ref)['sha']
        sources[key] = {'repository': repository, 'branch': branch, 'sha': sha}
    if github.head('vs2') != sources['vs2']['sha']:
        raise ChainError('VS2 advanced before resolution; start a new pipeline from its current branch head')
    return validate_manifest({'schema': 1, 'sources': sources})


def property_value(text, key):
    matches = re.findall(r'^' + re.escape(key) + r'[ \t]*=[ \t]*([^\r\n]*)', text, re.MULTILINE)
    if len(matches) != 1:
        raise ChainError(f'Expected exactly one {key} property')
    return matches[0].strip()


def replace_properties(text, values):
    for key, value in values.items():
        property_value(text, key)
        text = re.sub(r'^(' + re.escape(key) + r'[ \t]*=[ \t]*)[^\r\n]*',
                      lambda match: match[1] + value, text, flags=re.MULTILINE)
    return text


def pin(github, key, base, values, native_sha):
    original = github.properties(key, base)
    updated = replace_properties(original, values)
    base_commit = github.commit(key, base)
    tree = base_commit['commit']['tree']['sha']
    if updated != original:
        tree = github.call(key, 'git/trees', 'POST', {
            'base_tree': tree,
            'tree': [{'path': 'gradle.properties', 'mode': '100644', 'type': 'blob', 'content': updated}],
        })['sha']
    head = github.head(key)
    if github.commit(key, head)['commit']['tree']['sha'] == tree:
        print(f'{key}: reuse matching commit {head}', flush=True)
        return head
    if head != base:
        raise ChainError(f'{key} advanced outside this chain; refusing to overwrite {head}')
    commit = github.call(key, 'git/commits', 'POST', {
        'message': f'Update dependency pins for native {native_sha[:12]}',
        'tree': tree, 'parents': [base],
    })['sha']
    github.call(key, 'git/refs/heads/' + REPOSITORIES[key][1], 'PATCH', {'sha': commit, 'force': False})
    print(f'{key}: pushed {commit}', flush=True)
    return commit


def native_manifest(sha):
    result = request(f'{MAVEN}/physxbinaries/krunchxbinaries/{sha}/artifact-manifest.txt', missing_ok=True)
    if result is None:
        return None
    text = result[0].decode('utf-8')
    if re.findall(r'^commit: ([0-9a-f]{40})$', text, re.MULTILINE) != [sha]:
        raise ChainError('Native manifest commit mismatch')
    entries = {}
    for checksum, size, name in re.findall(r'^([0-9a-f]{64})\s+(\d+)\s+(\S+)\s*$', text, re.MULTILINE):
        if name not in NATIVE_FILES or name in entries:
            raise ChainError(f'Unexpected native artifact: {name}')
        entries[name] = {'sha256': checksum, 'size': int(size)}
    if set(entries) != NATIVE_FILES:
        raise ChainError('Native manifest is missing platform artifacts')
    for name, entry in entries.items():
        result = request(f'{MAVEN}/physxbinaries/krunchxbinaries/{sha}/{name}', method='HEAD', missing_ok=True)
        if result is None:
            return None
        if int(result[1].get('Content-Length', '-1')) != entry['size']:
            raise ChainError(f'Native artifact size mismatch: {name}')
    return entries


def start_native(github, sha):
    if native_manifest(sha) is not None:
        return
    statuses = github.statuses('native', sha)
    if any('ci/circleci: ' + job in statuses for job in NATIVE_JOBS):
        return
    tag = 'full-build-chain-' + sha
    existing = github.call('native', 'git/ref/tags/' + tag, missing_ok=True)
    if existing:
        if existing['object']['sha'] != sha:
            raise ChainError('Native build tag already points to a different commit')
        return
    github.call('native', 'git/refs', 'POST', {'ref': 'refs/tags/' + tag, 'sha': sha})
    print(f'native: started {tag}', flush=True)


def pom_ready(group, artifact, version, dependency=None):
    base = f'{MAVEN}/{group.replace(".", "/")}/{artifact}/{version}/{artifact}-{version}'
    result = request(base + '.pom', missing_ok=True)
    if result is None:
        return None
    root = ET.fromstring(result[0])
    ns = {'m': 'http://maven.apache.org/POM/4.0.0'}
    for name, expected in (('groupId', group), ('artifactId', artifact), ('version', version)):
        if root.findtext('m:' + name, namespaces=ns) != expected:
            raise ChainError(f'Published {artifact} POM {name} mismatch')
    if dependency:
        matches = [d for d in root.findall('m:dependencies/m:dependency', ns)
                   if d.findtext('m:artifactId', namespaces=ns) == dependency[0]]
        if len(matches) != 1 or matches[0].findtext('m:version', namespaces=ns) != dependency[1]:
            raise ChainError(f'Published {artifact} dependency mismatch')
    jar = request(base + '.jar', method='HEAD', missing_ok=True)
    return base + '.jar' if jar and int(jar[1].get('Content-Length', '0')) > 0 else None


def verify_bundled_native(url, entries):
    payload = request(url)[0]
    with zipfile.ZipFile(io.BytesIO(payload)) as archive:
        for name in BUNDLED_FILES:
            if archive.namelist().count(name) != 1:
                raise ChainError(f'{name} missing or duplicated in {url}')
            if hashlib.sha256(archive.read(name)).hexdigest() != entries[name]['sha256']:
                raise ChainError(f'{name} does not match the selected native build')
    return hashlib.sha256(payload).hexdigest()


def wait_for(github, key, sha, jobs, artifact, timeout, interval=30, clock=time.monotonic, sleep=time.sleep):
    deadline = clock() + timeout
    required = {'ci/circleci: ' + job for job in jobs}
    while True:
        statuses = github.statuses(key, sha)
        failed = [s for name, s in statuses.items() if name in required and s['state'] in ('error', 'failure')]
        if failed:
            raise ChainError(f'{key} CI failed: ' + ', '.join(s['context'] + ' ' + (s.get('target_url') or '') for s in failed))
        if all(name in statuses and statuses[name]['state'] == 'success' for name in required):
            result = artifact()
            if result:
                print(f'{key}: published artifacts and required CI verified for {sha}', flush=True)
                return result
        if clock() >= deadline:
            raise ChainError(f'Timed out waiting for {key} {sha}; rerun failed jobs after resolving its CI')
        print(f'{key}: waiting for CI/publication of {sha[:12]}', flush=True)
        sleep(interval)


def run(github, manifest, output, timeout):
    validate_manifest(manifest)
    source = manifest['sources']
    native_sha = source['native']['sha']
    api_sha = source['api']['sha']
    result = {'schema': 1, 'sources': source, 'published': {}}

    def record(key, sha, version=None, **details):
        result['published'][key] = {'sha': sha, 'version': version, **details}
        write_json(output, result)

    start_native(github, native_sha)
    native = wait_for(github, 'native', native_sha, NATIVE_JOBS,
                      lambda: native_manifest(native_sha), timeout)
    record('native', native_sha, artifacts=native)
    api_version = '1.0.0+' + api_sha[:10]
    wait_for(github, 'api', api_sha, ('gradle_build', 'publish_to_maven'),
             lambda: pom_ready('org.valkyrienskies', 'physics_api', api_version), timeout)
    record('api', api_sha, api_version)

    bridge_sha = pin(github, 'bridge', source['bridge']['sha'],
                     {'krunchx_commit_hash': native_sha, 'vs_phys_api_version': api_version}, native_sha)
    bridge_version = '1.0.0+' + bridge_sha[:10]
    bridge_jar = wait_for(github, 'bridge', bridge_sha, BRIDGE_JOBS,
                         lambda: pom_ready('org.valkyrienskies', 'physics_api_krunch', bridge_version,
                                           ('physics_api', api_version)), timeout)
    record('bridge', bridge_sha, bridge_version, jar_sha256=verify_bundled_native(bridge_jar, native))

    core_sha = pin(github, 'core', source['core']['sha'], {'krunch_version': bridge_version}, native_sha)
    core_version = '1.1.0+' + core_sha[:10]

    def core_artifacts():
        jars = {name: pom_ready('org.valkyrienskies.core', name, core_version)
                for name in ('api', 'internal', 'util', 'impl')}
        return jars if all(jars.values()) else None

    core_jars = wait_for(github, 'core', core_sha, ('gradle_build', 'publish_to_maven'), core_artifacts, timeout)
    record('core', core_sha, core_version, jar_sha256=verify_bundled_native(core_jars['impl'], native))

    vs2_sha = pin(github, 'vs2', source['vs2']['sha'],
                  {'vs_core_version': core_version, 'krunch_version': bridge_version,
                   'krunch_api_version': api_version}, native_sha)
    vs2_version = property_value(github.properties('vs2', vs2_sha), 'mod_version') + '+' + vs2_sha[:10]

    def vs2_artifacts():
        jars = {loader: pom_ready('org.valkyrienskies', 'valkyrienskies-120-' + loader, vs2_version)
                for loader in ('common', 'fabric', 'forge')}
        return jars if all(jars.values()) else None

    jars = wait_for(github, 'vs2', vs2_sha,
                    ('gradle_build', 'fabric_server_test', 'forge_server_test', 'publish_to_maven'),
                    vs2_artifacts, timeout)
    record('vs2', vs2_sha, vs2_version, artifacts=jars)
    print('Dependency chain complete. No playtest release tag was created.', flush=True)
    return result


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('action', choices=('resolve', 'publish'))
    parser.add_argument('--manifest', type=Path, required=True)
    parser.add_argument('--output', type=Path, default=Path('chain/result.json'))
    parser.add_argument('--native-ref', default='main')
    parser.add_argument('--api-ref', default='main')
    parser.add_argument('--controller-sha', default=os.environ.get('CIRCLE_SHA1'))
    parser.add_argument('--timeout', type=int, default=3600)
    args = parser.parse_args()
    github = GitHub()
    if args.action == 'resolve':
        if not args.controller_sha:
            raise ChainError('The VS2 controller commit is required')
        manifest = resolve(github, args.native_ref, args.api_ref, args.controller_sha)
        write_json(args.manifest, manifest)
        print(json.dumps(manifest, indent=2), flush=True)
    else:
        run(github, json.loads(args.manifest.read_text(encoding='utf-8')), args.output, args.timeout)


if __name__ == '__main__':
    try:
        main()
    except (ChainError, urllib.error.URLError) as error:
        raise SystemExit(str(error))
