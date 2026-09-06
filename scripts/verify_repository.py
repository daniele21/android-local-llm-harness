#!/usr/bin/env python3
from __future__ import annotations
import json, sys
from pathlib import Path

ROOT=Path('.').resolve(); errors=[]
required=['README.md','AGENTS.md','CONTRIBUTING.md','SECURITY.md','EXECUTION-CAPABILITY-CONTRACT.md','E2E-ENVIRONMENT-CONTRACT.md','.engineering/baseline.json','.engineering/documentation-policy.json','.engineering/commands.json','.engineering/e2e.json','.github/workflows/repository-health.yml','docs/README.md','docs/architecture.md','docs/current-state.md','scripts/verify_operations.py','scripts/verify_e2e.py','scripts/verify_stage_environment_policy.py','scripts/verify_agent_context.py']
for rel in required:
    if not (ROOT/rel).is_file(): errors.append(f'missing required file: {rel}')
try: b=json.loads((ROOT/'.engineering/baseline.json').read_text())
except Exception as e: errors.append(f'invalid baseline: {e}'); b={}
if b:
    s=b.get('standard',{})
    if b.get('schema_version')!=1: errors.append('baseline schema_version must be 1')
    if s.get('source')!='daniele21/repo-template-sw': errors.append('baseline source mismatch')
    if s.get('version')!='0.10.0': errors.append('baseline version must be 0.10.0')
    if b.get('target_level') not in {'L0','L1','L2'}: errors.append('invalid target_level')
    for name in ('plan-workstream','structured-change','design-product-experience','validate-change','preflight-change','remote-preflight','finalize-workstream','review-reference-quality'):
        e=b.get('skills',{}).get(name)
        if not isinstance(e,dict) or not e.get('source_version') or not isinstance(e.get('customized'),bool): errors.append(f'invalid skill metadata: {name}')
for rel in ('README.md','AGENTS.md','docs/architecture.md','SECURITY.md'):
    p=ROOT/rel
    if p.is_file() and any(x in p.read_text() for x in ('<PROJECT_NAME>','<REPLACE_WITH_','<DESCRIBE_','<LIST_')): errors.append(f'unresolved adopter placeholder: {rel}')
print('Repository baseline check')
for e in errors: print('FAIL:',e)
print('RESULT:', 'FAIL' if errors else 'PASS')
sys.exit(bool(errors))
