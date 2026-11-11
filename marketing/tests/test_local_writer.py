import importlib.util
import json
import sys
import tempfile
from pathlib import Path
import unittest

HERE = Path(__file__).resolve().parents[1]
sys.path.insert(0,str(HERE))
import local_writer as w

class DummyResponse:
    def __init__(self,data): self.data=data
    def __enter__(self): return self
    def __exit__(self,*args): return False
    def read(self): return json.dumps(self.data).encode('utf-8')

class LocalWriterTests(unittest.TestCase):
    def test_reject_remote_server(self):
        with self.assertRaises(ValueError):w.local_endpoint('https://api.example.com')
    def test_parse_model_json_and_draft_only(self):
        article={"title":"How we are designing order tracking","summary":"An update on the state of order-tracking development.","category":"Builder's notes","body":["We tested order status scenarios with our current local prototype today.","The merchant-checking workflow still needs a supported production integration."]}
        result=w.draft_from_notes('Local prototype test on Nov 10: order status scenarios were tested, carrier cron not launched.', 'gemma3:4b', sender=lambda request,timeout:DummyResponse({'response':json.dumps(article)}))
        with tempfile.TemporaryDirectory() as tmp:
            p=w.save_draft(result,Path('source_notes.md'),'local-test-post','gemma3:4b',dest=Path(tmp))
            obj=json.loads(p.read_text())
            self.assertEqual(obj['status'],'draft')
            self.assertEqual(obj['approved_by'],'')
            with self.assertRaises(FileExistsError):w.save_draft(result,Path('source_notes.md'),'local-test-post','gemma3:4b',dest=Path(tmp))

if __name__=='__main__':unittest.main()
