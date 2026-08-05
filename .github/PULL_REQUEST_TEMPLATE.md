## Target branch

- [ ] Ordinary feature, fix, documentation, dependency, UX/UI or infrastructure work targets `dev`.
- [ ] A pull request targeting `main` is either a promotion from `dev` or an explicit emergency hotfix.

## Summary

Describe the coherent deliverable in this pull request.

## Why

Explain the problem, architectural motivation or operational need.

## Validation

List the checks executed and any evidence still pending.

- [ ] `Repository validation` is green on the current head.
- [ ] Relevant Android packaging and native checks are green.
- [ ] Tests cover the changed behavior.
- [ ] Documentation and repository state ledgers are updated when the functional boundary changed.

## Safety and privacy

- [ ] No GGUF/GGML model binary, credential, signing material, private path, URI, prompt or generated output is committed or exposed.
- [ ] Public contracts remain backend-neutral and implementation details do not leak across module boundaries.

## Promotion-only checklist

Complete this section only for `dev -> main`.

- [ ] The head is the current protected `dev` branch.
- [ ] Full, non-scoped repository validation passed.
- [ ] Android packaging passed for the exact candidate commit.
- [ ] Native host tests passed for the exact candidate commit.
- [ ] All conversations are resolved and the branch is up to date.
- [ ] The merge method will preserve the validated `dev` history with a merge commit.
