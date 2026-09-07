#!/usr/bin/env python3
from __future__ import annotations
import json, sys
from pathlib import Path

p=Path('.engineering/commands.json'); errors=[]
try: d=json.loads(p.read_text())
except Exception as e: print('FAIL:',e); raise SystemExit(1)
if d.get('schema_version')!=1: errors.append('schema_version must be 1')
if d.get('contract_version')!='0.7.0': errors.append('contract_version must be 0.7.0')
for name in ('setup','doctor','dev','check','test','e2e','build','smoke','package','stop','clean'):
    e=d.get('commands',{}).get(name)
    if not isinstance(e,dict) or e.get('status') not in {'required','recommended','optional','n/a'}: errors.append(f'invalid command {name}')
for name in ('setup','check','test','build','clean'):
    if d.get('commands',{}).get(name,{}).get('status')=='n/a': errors.append(f'{name} may not be n/a')
v=d.get('development_velocity',{}); i=v.get('integration',{}); r=v.get('release',{})
if v.get('stages')!=['iteration','integration','release']: errors.append('invalid delivery stages')
for k in ('exact_head_required','full_diff_review_required','durable_documentation_current_required','automated_e2e_required_when_affected','real_environment_deferred_to_release'):
    if i.get(k) is not True: errors.append(f'integration.{k} must be true')
if i.get('real_environment_blocking') is not False: errors.append('integration.real_environment_blocking must be false')
if r.get('required_real_environment_blocking') is not True: errors.append('release.required_real_environment_blocking must be true')
ve=d.get('validation_execution',{})
if ve.get('no_human_runner_for_automatable_gates') is not True or ve.get('remote_automation_required_when_agent_local_unavailable') is not True: errors.append('invalid validation execution policy')
vp=d.get('validation_profiles',{})
if not vp.get('selector') or vp.get('selector_output')!='risk_dimensions_and_required_gates': errors.append('invalid native selector contract')
rem=d.get('remote_preflight',{})
for k in ('exact_head_required','reuse_successful_equivalent_evidence','rerun_only_when_missing_stale_or_insufficient'):
    if rem.get(k) is not True: errors.append(f'remote_preflight.{k} must be true')
rep=d.get('agent_reporting',{}); req={'stage','source_identity','risks','profile','required_gates','evidence','remaining_gaps','next_action'}
if rep.get('schema_version')!=1 or rep.get('format')!='summary_with_evidence_references': errors.append('invalid agent_reporting contract')
if not req.issubset(set(rep.get('required_summary_fields',[]))): errors.append('agent_reporting missing summary fields')
for k in ('bounded_output','full_report_on_demand','preserve_failed_pending_gates','summary_is_not_evidence_verification'):
    if rep.get(k) is not True: errors.append(f'agent_reporting.{k} must be true')
print('Project operating contract check')
for e in errors: print('FAIL:',e)
print('RESULT:', 'FAIL' if errors else 'PASS'); sys.exit(bool(errors))
