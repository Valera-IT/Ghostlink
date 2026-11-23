import unittest
from rotate import plan


class PlanTest(unittest.TestCase):
    def test_priority_and_shared_files(self):
        files = {key: [{'Key': key, 'LastModified': age}]
                 for key, age in [('old', 1), ('thumb', 2), ('copy', 2),
                                  ('orphan', 3), ('voice', 4), ('fresh', 9), ('pending', 5)]}
        live = {'attachment': {}}
        expired = {'attachment': {'expired': True}}
        messages = [('old', 'thumb', live), ('copy', 'thumb', live),
                    ('voice', 'voice', live), ('pending', 'pending', expired)]
        groups = plan(files, messages, cutoff=8)
        self.assertEqual([{v['Key'] for v in group[3]} for group in groups],
                         [{'pending'}, {'orphan'}, {'old', 'thumb', 'copy'}, {'voice'}])
        self.assertEqual(len(groups[2][0]), 2)

    def test_new_version_or_thumbnail_protects_whole_attachment(self):
        files = {'a': [{'LastModified': 1}], 'b': [{'LastModified': 1}, {'LastModified': 10}]}
        self.assertEqual(plan(files, [('a', 'b', {})], cutoff=9), [])

    def test_trim_stops_at_low_watermark_and_dry_run_matches(self):
        from rotate import HIGH, LOW, trim
        groups = [([], 1, key, [{'Key': key, 'Size': 850_000_000}]) for key in ('a', 'b', 'c')]
        removed = []
        result = trim(HIGH, groups, lambda _, versions: removed.append(versions[0]['Key']))
        self.assertEqual(result, LOW - 200_000_000)
        self.assertEqual(removed, ['a', 'b'])
        self.assertEqual(trim(HIGH, groups, None), result)
