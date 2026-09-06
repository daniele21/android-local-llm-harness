#!/usr/bin/env python3
from __future__ import annotations
import json, sys
from pathlib import Path

c=json.loads(Path('.engineering/commands.json').read_text()); e=json.loads(Path('.engineering/e2e.json').read_text()); errors=[]
ci=c.get('development_velocity',{}).get('integration',{}); cr=c.get('development_velocity',{}).get('release',{})
ei=e.get('stage_policy',{}).get('integration',{}); er=e.get('stage_policy',{}).get('release',{})
checks=[(ci.get('automated_e2e_required_when_affected') is True,'commands integration automated E2E'),(ci.get('real_environment_blocking') is False,'commands integration physical non-blocking'),(ci.get('real_environment_deferred_to_release') is True,'commands integration physical deferred'),(cr.get('required_real_environment_blocking') is True,'commands release physical blocking'),(ei.get('automated_e2e_before_shared_integration') is True,'e2e integration automation'),(ei.get('real_environment_blocking') is False,'e2e integration physical non-blocking'),(ei.get('real_environment_deferred_to_release') is True,'e2e integration physical deferred'),(er.get('required_real_environment_blocking') is True,'e2e release physical blocking')]
for ok,label in checks:
    if not ok: errors.append(label)
print('Stage/environment policy check')
for x in errors: print('FAIL:',x)
print('RESULT:', 'FAIL' if errors else 'PASS'); sys.exit(bool(errors))
