#!/usr/bin/env python3
"""Zero-dependency validation for the E2E environment fidelity contract."""
from __future__ import annotations
import argparse, json, sys
from pathlib import Path

FIDELITY_ORDER=["host_or_fake","simulated_or_emulated","representative_virtual","representative_physical","target_environment"]
FIDELITY_RANK={name:i for i,name in enumerate(FIDELITY_ORDER)}
FIDELITY_CLASSES=set(FIDELITY_ORDER)
APPLICABILITY={"required","recommended","n/a"}; AUTOMATION={"automated","real_environment"}; REAL_CONFIRMATION={"required","conditional","not_required"}; REQUIRED_UI_MEDIA={"screenshots","video"}
PLACEHOLDER_MARKERS=("<REPLACE_WITH_","<PROJECT_")
REQUIRED_PRINCIPLES=("final_environment_should_confirm_not_discover","execution_capability_separate_from_environment_fidelity","lowest_sufficient_test_level","critical_journeys_only","built_artifact_when_material","residual_fidelity_gaps_explicit","ui_journey_screenshot_and_video_artifacts_required")

def parse_args():
 p=argparse.ArgumentParser(); p.add_argument("--root",default="."); p.add_argument("--template-mode",action="store_true"); return p.parse_args()
def non_empty(v): return isinstance(v,str) and bool(v.strip())
def string_list(v): return isinstance(v,list) and all(non_empty(x) for x in v)
def placeholder(v):
 if isinstance(v,str): return any(m in v for m in PLACEHOLDER_MARKERS)
 if isinstance(v,list): return any(placeholder(x) for x in v)
 if isinstance(v,dict): return any(placeholder(x) for x in v.values())
 return False
def load(path,label,errors):
 if not path.is_file(): errors.append(f"missing required file: {path}"); return {}
 try: v=json.loads(path.read_text(encoding="utf-8"))
 except (OSError,json.JSONDecodeError) as e: errors.append(f"invalid {label}: {e}"); return {}
 if not isinstance(v,dict): errors.append(f"{label} must contain an object"); return {}
 return v
def indexed(items,label,errors):
 if not isinstance(items,list): errors.append(f"{label} must be a list"); return {}
 out={}
 for i,item in enumerate(items):
  if not isinstance(item,dict): errors.append(f"{label}[{i}] must be an object"); continue
  ident=item.get("id")
  if not non_empty(ident): errors.append(f"{label}[{i}].id is required")
  elif ident in out: errors.append(f"duplicate {label} id: {ident}")
  else: out[ident]=item
 return out
def refs(v,known,label,errors,allow_empty=False):
 if not string_list(v): errors.append(f"{label} must be a list of non-empty ids"); return []
 if not v and not allow_empty: errors.append(f"{label} must not be empty")
 for ref in v:
  if ref not in known and not placeholder(ref): errors.append(f"{label} references unknown id: {ref}")
 return list(v)

def main():
 args=parse_args(); root=Path(args.root).resolve(); errors=[]; warnings=[]; data=load(root/".engineering/e2e.json",".engineering/e2e.json",errors)
 if data.get("schema_version")!=1: errors.append("schema_version must be 1")
 if data.get("contract_version")!="0.1.1": errors.append("contract_version must be 0.1.1")
 app=data.get("applicability") if isinstance(data.get("applicability"),dict) else {}; status=app.get("status")
 if status not in APPLICABILITY: errors.append(f"applicability.status must be one of {sorted(APPLICABILITY)}")
 if not non_empty(app.get("reason")): errors.append("applicability.reason is required")
 commands=load(root/".engineering/commands.json",".engineering/commands.json",errors); cmap=commands.get("commands") if isinstance(commands.get("commands"),dict) else {}; centry=cmap.get("e2e") if isinstance(cmap,dict) else None; cstatus=centry.get("status") if isinstance(centry,dict) else None
 if not isinstance(centry,dict): errors.append("commands.json must declare commands.e2e")
 if status=="n/a" and cstatus!="n/a": errors.append("E2E applicability n/a requires commands.e2e.status = n/a")
 elif status in {"required","recommended"} and cstatus=="n/a": errors.append("E2E-applicable repositories may not set commands.e2e.status = n/a")
 elif status=="required" and cstatus not in {None,"required"}: errors.append("E2E applicability required requires commands.e2e.status = required")
 principles=data.get("principles") if isinstance(data.get("principles"),dict) else {}
 for key in REQUIRED_PRINCIPLES:
  if principles.get(key) is not True: errors.append(f"principles.{key} must be true")
 if data.get("fidelity_order")!=FIDELITY_ORDER: errors.append("fidelity_order must match the canonical ordered fidelity classes")
 targets=indexed(data.get("target_environments"),"target_environments",errors); envs=indexed(data.get("execution_environments"),"execution_environments",errors); journeys=indexed(data.get("critical_journeys"),"critical_journeys",errors)
 if status in {"required","recommended"} and (not targets or not envs or not journeys): errors.append("E2E-applicable repositories require target environments, execution environments and critical journeys")
 if status=="n/a" and (targets or envs or journeys): errors.append("E2E marked n/a must not declare target/execution environments or critical journeys")
 for tid,t in targets.items():
  if not non_empty(t.get("platform")): errors.append(f"target_environments.{tid}.platform is required")
  if not non_empty(t.get("description")): errors.append(f"target_environments.{tid}.description is required")
  if not string_list(t.get("material_dimensions")) or not t.get("material_dimensions"): errors.append(f"target_environments.{tid}.material_dimensions must be a non-empty string list")
 automated=set()
 for eid,e in envs.items():
  fidelity=e.get("fidelity_class"); automation=e.get("automation")
  if fidelity not in FIDELITY_CLASSES: errors.append(f"execution_environments.{eid}.fidelity_class must be one of {FIDELITY_ORDER}")
  if automation not in AUTOMATION: errors.append(f"execution_environments.{eid}.automation must be one of {sorted(AUTOMATION)}")
  elif automation=="automated": automated.add(eid)
  if not non_empty(e.get("platform")) or not non_empty(e.get("artifact_surface")): errors.append(f"execution_environments.{eid} requires platform and artifact_surface")
  refs(e.get("target_environment_refs"),set(targets),f"execution_environments.{eid}.target_environment_refs",errors)
  if not string_list(e.get("known_gaps")): errors.append(f"execution_environments.{eid}.known_gaps must be a string list")
 for jid,j in journeys.items():
  if not non_empty(j.get("claim")): errors.append(f"critical_journeys.{jid}.claim is required")
  ui=j.get("ui_surface"); media=j.get("required_media_artifacts")
  if not isinstance(ui,bool): errors.append(f"critical_journeys.{jid}.ui_surface must be boolean")
  if not string_list(media): errors.append(f"critical_journeys.{jid}.required_media_artifacts must be a string list")
  elif ui is True and set(media)!=REQUIRED_UI_MEDIA: errors.append(f"critical_journeys.{jid}.required_media_artifacts must contain screenshots and video for UI journeys")
  elif ui is False and media: errors.append(f"critical_journeys.{jid}.required_media_artifacts must be empty when ui_surface is false")
  refs(j.get("target_environment_refs"),set(targets),f"critical_journeys.{jid}.target_environment_refs",errors); arefs=refs(j.get("automated_environment_refs"),set(envs),f"critical_journeys.{jid}.automated_environment_refs",errors,allow_empty=True); ranks=[]
  for ref in arefs:
   e=envs.get(ref)
   if e and e.get("automation")!="automated": errors.append(f"critical_journeys.{jid}.automated_environment_refs must reference automated environments: {ref}")
   if e and e.get("automation")=="automated" and e.get("fidelity_class") in FIDELITY_RANK: ranks.append(FIDELITY_RANK[e["fidelity_class"]])
  minimum=j.get("minimum_automated_fidelity")
  if minimum not in FIDELITY_CLASSES: errors.append(f"critical_journeys.{jid}.minimum_automated_fidelity must be one of {FIDELITY_ORDER}")
  elif arefs and ranks and max(ranks)<FIDELITY_RANK[minimum]: errors.append(f"critical_journeys.{jid} does not reach minimum_automated_fidelity {minimum}")
  confirmation=j.get("real_environment_confirmation")
  if confirmation not in REAL_CONFIRMATION: errors.append(f"critical_journeys.{jid}.real_environment_confirmation must be one of {sorted(REAL_CONFIRMATION)}")
  residual=j.get("residual_gaps")
  if not string_list(residual): errors.append(f"critical_journeys.{jid}.residual_gaps must be a string list")
  if not arefs and not non_empty(j.get("automation_gap_reason")): errors.append(f"critical_journeys.{jid} needs automated_environment_refs or an explicit automation_gap_reason")
  if arefs and not any(ref in automated for ref in arefs): errors.append(f"critical_journeys.{jid} has no valid automated execution environment")
  if confirmation=="not_required" and residual: warnings.append(f"critical_journeys.{jid} declares residual gaps but real_environment_confirmation is not_required")
 if not args.template_mode and placeholder(data): errors.append("unresolved adopter placeholder in .engineering/e2e.json")
 print("E2E environment fidelity contract check"); print(f"root: {root}"); print(f"applicability: {status}"); print(f"commands.e2e.status: {cstatus}")
 for warning in warnings: print(f"WARN: {warning}")
 for error in errors: print(f"FAIL: {error}")
 if errors: print(f"RESULT: FAIL ({len(errors)} error(s), {len(warnings)} warning(s))"); return 1
 print(f"RESULT: PASS ({len(warnings)} warning(s))"); return 0
if __name__=="__main__": raise SystemExit(main())
