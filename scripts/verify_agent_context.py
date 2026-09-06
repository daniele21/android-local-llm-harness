#!/usr/bin/env python3
from __future__ import annotations
import argparse, json, math, os, sys
from pathlib import Path
REQ={'docs','bug','contract','ui','integration','release','resume'}

def inside(root, rel):
    p=(root/rel).resolve()
    if Path(rel).is_absolute() or not p.is_relative_to(root): raise ValueError(f'invalid path {rel}')
    return p

def main():
    ap=argparse.ArgumentParser(); ap.add_argument('--root',default='.'); ap.add_argument('--route'); ap.add_argument('--path',action='append',default=[]); ap.add_argument('--workstream'); ap.add_argument('--format',choices=['text','json'],default='text'); ap.add_argument('--template-mode',action='store_true'); a=ap.parse_args()
    try:
        root=Path(a.root).resolve(); pol=json.loads((root/'.engineering/documentation-policy.json').read_text()); base=json.loads((root/'.engineering/baseline.json').read_text())
        if pol.get('schema_version')!=2: raise ValueError('documentation policy schema_version must be 2')
        routes=pol.get('context_routes',{}); missing=REQ-set(routes)
        if missing: raise ValueError(f'missing routes {sorted(missing)}')
        cpt=pol.get('estimated_token_characters',4); cache={}
        def cost(p):
            if p not in cache:
                if not p.is_file(): raise ValueError(f'missing context source: {p.relative_to(root)}')
                cache[p]=math.ceil(len(p.read_text())/cpt)
            return cache[p]
        excluded=set(pol.get('context_exclude_directories',[])); scoped=[]
        for d,dirs,files in os.walk(root):
            dirs[:]=[x for x in dirs if x not in excluded]
            if 'AGENTS.md' in files and Path(d)!=root: scoped.append((Path(d)/'AGENTS.md').resolve())
        affected=[inside(root,x) for x in a.path]
        # Without an affected path there is no truthful scoped-guide selection: measure the
        # route bootstrap only. With --path, include only guides whose directory owns/contains
        # an affected path. This keeps repository-health representative instead of summing the
        # entire monorepo instruction graph.
        scope={g for g in scoped if affected and any(g.parent==p or g.parent in p.parents for p in affected)}
        work=None
        if a.workstream:
            work=inside(root,a.workstream)
            if not work.is_relative_to(root/'docs/workstreams'): raise ValueError('workstream must be under docs/workstreams')
        profiles=set(base.get('profiles',[])); reports=[]; errors=[]
        selected=[a.route] if a.route else list(routes)
        for name in selected:
            r=routes.get(name)
            if not r: raise ValueError(f'unknown route {name}')
            reqp=r.get('requires_profile')
            if reqp and reqp not in profiles and not a.template_mode: continue
            src={inside(root,x) for x in r.get('files',[])}
            if r.get('include_scoped_guides'): src|=scope
            if r.get('include_workstream') and work: src.add(work)
            detail=[{'path':str(p.relative_to(root)),'estimated_tokens':cost(p)} for p in sorted(src)]; total=sum(x['estimated_tokens'] for x in detail); budget=r.get('max_estimated_tokens',0)
            if total>budget: errors.append(f'route {name} ~{total} exceeds {budget}')
            reports.append({'route':name,'estimated_tokens':total,'budget':budget,'files':detail})
        boot=cost(root/'AGENTS.md'); limit=pol.get('context_targets',{}).get('bootstrap_max_estimated_tokens',0)
        if boot>limit: errors.append(f'bootstrap ~{boot} exceeds {limit}')
        out={'measurement':'characters/policy-factor, not runtime tokens','bootstrap_estimated_tokens':boot,'routes':reports,'errors':errors,'result':'FAIL' if errors else 'PASS'}
    except Exception as exc: out={'errors':[str(exc)],'result':'FAIL'}
    if a.format=='json': print(json.dumps(out,indent=2))
    else:
        print('Agent context health');
        for r in out.get('routes',[]): print(f"{r['route']}: ~{r['estimated_tokens']} / {r['budget']}")
        for x in out.get('errors',[]): print('FAIL:',x)
        print('RESULT:',out['result'])
    return out['result']!='PASS'
if __name__=='__main__': sys.exit(main())
