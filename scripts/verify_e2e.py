#!/usr/bin/env python3
from __future__ import annotations
import json, sys
from pathlib import Path

d=json.loads(Path('.engineering/e2e.json').read_text()); errors=[]
if d.get('schema_version')!=1: errors.append('schema_version must be 1')
if d.get('contract_version')!='0.2.1': errors.append('contract_version must be 0.2.1')
sp=d.get('stage_policy',{}); i=sp.get('integration',{}); r=sp.get('release',{})
for k in ('automated_e2e_before_shared_integration','real_environment_deferred_to_release'):
    if i.get(k) is not True: errors.append(f'integration.{k} must be true')
if i.get('real_environment_blocking') is not False: errors.append('integration.real_environment_blocking must be false')
if i.get('material_ui_journey_minimum_evidence_mode')!='full_media': errors.append('material UI integration must require full_media')
for k in ('full_validation_required','release_critical_e2e_required','required_real_environment_blocking'):
    if r.get(k) is not True: errors.append(f'release.{k} must be true')
modes=set(d.get('ui_evidence',{}).get('modes',[]))
if modes!={'assertions','screenshots','full_media'}: errors.append('invalid UI evidence modes')
targets={x.get('id') for x in d.get('target_environments',[]) if isinstance(x,dict)}; envs={x.get('id') for x in d.get('execution_environments',[]) if isinstance(x,dict)}
for j in d.get('critical_journeys',[]):
    if j.get('minimum_ui_evidence_mode') not in modes: errors.append(f"journey {j.get('id')} invalid UI mode")
    if not set(j.get('target_environment_refs',[])).issubset(targets): errors.append(f"journey {j.get('id')} unknown target env")
    if not set(j.get('automated_environment_refs',[])).issubset(envs): errors.append(f"journey {j.get('id')} unknown execution env")
print('E2E environment contract check')
for e in errors: print('FAIL:',e)
print('RESULT:', 'FAIL' if errors else 'PASS'); sys.exit(bool(errors))
