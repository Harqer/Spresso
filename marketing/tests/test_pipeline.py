import importlib.util
import json
import os
from pathlib import Path
import tempfile
import unittest
import datetime as dt
from zoneinfo import ZoneInfo

M = Path(__file__).resolve().parents[1] / 'pipeline.py'
spec = importlib.util.spec_from_file_location('pipeline', M)
p = importlib.util.module_from_spec(spec)
spec.loader.exec_module(p)

POST = {"slug":"example-post","title":"Text & proof","summary":"Price <test>","category":"Update","status":"approved","approved_by":"Editorial reviewer","publish_at":"2026-11-01T10:00:00-08:00","body":["A <b> safe & useful story."],"channels":{}}
NOW = dt.datetime(2026,11,11,10,tzinfo=ZoneInfo('America/Los_Angeles'))

class PipelineTests(unittest.TestCase):
    def test_approval_required(self):
        d = dict(POST, status='draft', approved_by='')
        p.validate_posts([d]); self.assertFalse(p.due(d, NOW))
        with self.assertRaises(ValueError): p.validate_posts([dict(POST, approved_by='')])
    def test_scheduled_future_does_not_publish(self):
        q = dict(POST, publish_at='2026-12-01T10:00:00-08:00')
        self.assertFalse(p.due(q,NOW))
    def test_duplicate_slug_rejected(self):
        with self.assertRaises(ValueError): p.validate_posts([POST, POST])
    def test_escape_and_preview(self):
        with tempfile.TemporaryDirectory() as tmp:
            out=Path(tmp)
            written=p.build_site([POST],out,'https://example.com/blog/',NOW)
            self.assertEqual(len(written),1)
            page=(out/'posts'/'example-post'/'index.html').read_text()
            self.assertIn('&lt;b&gt;',page)
            self.assertNotIn('<b>',page)
            self.assertIn('Your shopping, on autopilot.',page)
            self.assertTrue((out/'feed.xml').exists())
            self.assertTrue((out/'sitemap.xml').exists())
    def test_social_queue_is_drafts(self):
        with tempfile.TemporaryDirectory() as tmp:
            items=p.queue_posts([POST],Path(tmp),'https://example.com/blog/',NOW)
            self.assertEqual(len(items),6)
            self.assertTrue(all(x['status']=='ready_for_review' for x in items))
            self.assertLessEqual(len(next(x for x in items if x['platform']=='x')['body']),280)
    def test_unapproved_drafts_are_exported_but_not_published(self):
        d=dict(POST, status="draft", approved_by="")
        with tempfile.TemporaryDirectory() as tmp:
            drafts=p.queue_posts([d],Path(tmp),"https://example.com/blog/",NOW)
            self.assertEqual(len(drafts),6)
            self.assertTrue(all(x["status"]=="pending_editorial_approval" for x in drafts))
            pub=p.build_site([d],Path(tmp)/"site","https://example.com/blog/",NOW)
            self.assertEqual(pub, [])

    def test_facebook_is_opt_in(self):
        with tempfile.TemporaryDirectory() as tmp:
            with self.assertRaises(ValueError): p.publish_facebook(POST,Path(tmp)/'state.sqlite3',confirm=False,instant=NOW)
    def test_bad_slug_rejected(self):
        with self.assertRaises(ValueError): p.validate_posts([dict(POST, slug='../other')])

if __name__=='__main__': unittest.main()
