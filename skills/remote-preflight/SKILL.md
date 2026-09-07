---
name: remote-preflight
description: Reuse trusted equivalent Harnex validation evidence first, then execute only missing deterministic integration/release gates.
---

# Remote Preflight

Use after `preflight-change` identifies `REMOTE_AUTOMATED` gates.

Resolve exact PR head/tree, live base, risks, gates/profile and material E2E identity. Search trusted exact-head evidence first; if sufficient, report it without another heavy run. Otherwise dispatch `/preflight auto` or justified stronger profile once. On failure classify the owning cause and apply the diagnostic protocol in `validate-change`; rerun only invalidated/missing proof.

Never ask the user to execute automatable Gradle/native/build gates and never downgrade contract/lifecycle/native/package evidence for speed.

After content-preserving integration, tree-equivalent reuse is allowed only when final Git tree, validated target base, required gates/profile and material E2E identity remain equivalent and evidence is trusted/current. A moved base, changed tree, broader gates, direct push without proof or RELEASE validates normally. Preserve trusted requester, same-repository, least-privilege and secret-free execution.

Report source identity, required gates/reasons/statuses, exact-head or tree-equivalent evidence, new runs, failures and remaining `REAL_ENVIRONMENT` release obligations. The summary never turns deferred physical evidence into PASS.
