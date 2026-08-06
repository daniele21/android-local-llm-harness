# Harness ViewModel and UDF foundation

## Status

Implementation foundation added on top of `dev` as the first isolated block of the Activity-slimming and ViewModel/UDF migration.

This block deliberately does not move runtime ownership or Android lifecycle resources out of `MainActivity` yet. It introduces the typed state boundary and deterministic transition model required to perform that migration incrementally without changing the already connected model, Playground, diagnostics, or validation behavior in one high-risk rewrite.

## Implemented in this block

### Typed application state

`HarnessUiState` provides one immutable representation for the state currently spread across Activity-owned `mutableStateOf` properties:

- selected model and model-distribution operation;
- validation report and operation status;
- controller and diagnostic busy states;
- Playground state and editable generation inputs;
- diagnostics, benchmark, log-filter and request-timeline state;
- diagnostics section selection;
- removal confirmation;
- theme preference.

Derived properties centralize the current busy and keep-screen-on policy instead of duplicating Boolean expressions across UI callbacks.

### Unidirectional events and reducer

`HarnessUiEvent` represents state changes as typed events. `HarnessUiReducer` is a pure reducer with no Android, runtime, storage, or telemetry side effects.

The reducer preserves existing behavior that is easy to regress during migration, including:

- model selection clears stale removal confirmation;
- model-distribution messages become the visible operation status;
- validation completion uses the canonical status message;
- concurrent diagnostic actions are tracked independently;
- leaving the Logs section clears the selected request timeline;
- Playground and model-distribution activity participate in the screen-on policy.

### ViewModel state owner

`HarnessViewModel` exposes the immutable state as `StateFlow<HarnessUiState>` and accepts only typed events. StateFlow updates are atomic, allowing existing callback controllers to be bridged safely while their executor implementation is migrated later.

### Tests

The JVM test suite covers:

- model-selection cleanup;
- model-distribution busy and screen-on state;
- independent diagnostic-action transitions;
- active Playground behavior;
- request-timeline cleanup when leaving Logs;
- validation-report status updates.

## Intended integration sequence

The next block should migrate one vertical slice at a time, starting with Playground as required by the canonical UX/UI plan:

1. instantiate `HarnessViewModel` from the Activity composition root;
2. collect `uiState` with lifecycle awareness;
3. dispatch existing Playground callbacks as `HarnessUiEvent` values;
4. make `PlaygroundScreen` render only from `HarnessUiState` and emit user intents;
5. introduce the effectful `HarnessController` boundary for start, cancel, runtime release, and model lifecycle operations;
6. add `FakeHarnessController` tests for idle, generating, completed, failed, and cancelled states;
7. repeat the migration for Models, Diagnostics, Overview, Settings, and physical validation;
8. remove the corresponding Activity-owned mutable properties only after each vertical slice passes JVM, Compose, emulator, and device gates.

## Architectural constraints

- `HarnessUiReducer` must remain pure and Android-independent.
- Runtime, model-store, telemetry, clipboard, document-picker, and window operations remain effects behind controllers or the Activity composition root.
- Prompt and generated output must not be written to saved state, logs, telemetry, or diagnostics exports.
- No callback controller may mutate Compose state directly after its slice has moved to the ViewModel.
- The Activity must become a composition and lifecycle root, not a second state owner.
- A migration slice is complete only when its old Activity state has been removed and its state transitions are covered by tests.

## Validation still required

- repository Spotless and Detekt;
- phone-test JVM tests;
- Android Lint and compilation;
- debug APK assembly;
- compact and expanded emulator smoke tests;
- physical-device GGUF validation after the effectful Playground controller is connected.
