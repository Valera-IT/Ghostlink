"""Rotate the shared media bucket. Without --apply only report planned work."""
import argparse
from collections import defaultdict
from datetime import datetime, timedelta, timezone
import json
import os
from pathlib import Path
import re
import tomllib

HIGH = 9_500_000_000
LOW = 8_000_000_000
MEDIA = re.compile(r"([a-z0-9-]+)/([A-Za-z0-9_-]{1,128})/[A-Za-z0-9_-]{1,128}/[0-9a-z]+-[0-9a-f]{16}\.(?:jpg|m4a|mp4)")


def plan(files, messages, cutoff):
    # Shared keys (including a voice clip used as its own thumbnail) form one unit.
    parents = {}

    def root(key):
        parents.setdefault(key, key)
        while key != parents[key]:
            parents[key] = parents[parents[key]]
            key = parents[key]
        return key

    for key, thumb, _ in messages:
        parents[root(key)] = root(thumb)
    groups = defaultdict(list)
    refs = defaultdict(list)
    for key, _, snapshot in messages:
        refs[root(key)].append(snapshot)
    for key, versions in files.items():
        groups[root(key)].extend(versions)
    candidates = []
    for group, versions in groups.items():
        newest = max(v['LastModified'] for v in versions)
        if newest <= cutoff:
            candidates.append((refs[group], newest, group, versions))
    def priority(entry):
        references, newest, group, _ = entry
        unfinished = any(doc.get('attachment').get('expired') for doc in references)
        return (not unfinished, bool(references), newest, group)

    return sorted(candidates, key=priority)


def trim(total, groups, remove):
    for refs, _, _, versions in groups:
        if total <= LOW and not any(doc.get('attachment').get('expired') for doc in refs):
            break
        if remove is not None:
            remove(refs, versions)
        total -= sum(v['Size'] for v in versions)
    return total


def rotate(apply):
    import boto3
    from google.cloud import firestore
    from google.oauth2.service_account import Credentials

    config = tomllib.loads((Path(__file__).resolve().parents[1] / 'wrangler.toml').read_text())['vars']
    bucket = config['B2_BUCKET']
    projects = config['FIREBASE_PROJECTS'].split(',')
    s3 = boto3.client('s3', endpoint_url='https://' + config['B2_ENDPOINT'],
                      region_name=config['B2_REGION'], aws_access_key_id=os.environ['B2_KEY_ID'],
                      aws_secret_access_key=os.environ['B2_APP_KEY'])
    accounts = json.loads(os.environ['SERVICE_ACCOUNTS'])
    if isinstance(accounts, dict):
        accounts = [accounts]
    db = {a['project_id']: firestore.Client(project=a['project_id'], credentials=Credentials.from_service_account_info(a))
          for a in accounts if a['project_id'] in projects}
    if set(db) != set(projects):
        raise ValueError('Service account required for every configured project')
    state = db[projects[0]].collection('maintenance').document('mediaRotation')
    continuing = (state.get().to_dict() or {}).get('active', False)
    total = 0
    files = defaultdict(list)
    chats = set()
    cutoff = datetime.now(timezone.utc) - timedelta(hours=24)
    for page in s3.get_paginator('list_object_versions').paginate(Bucket=bucket):
        for version in page.get('Versions', []):
            total += version['Size']
            key = version['Key']
            match = MEDIA.fullmatch(key)
            if match and match[1] in db:
                files[key].append(version)
                chats.add((match[1], match[2]))
    print(f'Bucket usage: {total} bytes')
    if total < HIGH and not continuing:
        return
    if apply:
        state.set({'active': True})
    messages = []
    for project, chat in sorted(chats):
        collection = db[project].collection('chats').document(chat).collection('messages')
        for doc in collection.order_by('attachment.key').select(['attachment']).stream():
            attachment = doc.get('attachment')
            key, thumb = attachment['key'], attachment['thumbKey']
            if not all(k.startswith(f'{project}/{chat}/') for k in (key, thumb)):
                raise ValueError('Unexpected cross-chat attachment')
            messages.append((key, thumb, doc))
    def remove(refs, versions):
        for doc in refs:
            if not doc.get('attachment').get('expired'):
                # Concurrent edits/deletions abort this run before deleting its files.
                project = doc.get('attachment')['key'].split('/')[0]
                doc.reference.update({'attachment.expired': True},
                                     option=db[project].write_option(last_update_time=doc.update_time))
        for version in versions:
            s3.delete_object(Bucket=bucket, Key=version['Key'], VersionId=version['VersionId'])

    total = trim(total, plan(files, messages, cutoff), remove if apply else None)
    print(f'{"Remaining" if apply else "Projected"} usage: {total} bytes')
    if apply and total <= LOW:
        state.set({'active': False})


if __name__ == '__main__':
    parser = argparse.ArgumentParser()
    parser.add_argument('--apply', action='store_true')
    rotate(parser.parse_args().apply)
