# Piano di integrazione `dev` e rilascio Harness 0.5.0

**Stato:** in esecuzione — Fasi 0, 2 e 3 completate; Fase 1 completata nel repository con protezione amministrativa di `dev` ancora da applicare; prossimo blocco UX-03
**Data ultimo aggiornamento:** 2026-08-05
**Ambito:** governance Git, CI cumulativa, recovery model management, brand Android, UX/UI, validazione e rilascio interno

## 1. Obiettivo

Usare `dev` come linea canonica di integrazione e mantenere `main` come linea stabile e promuovibile. Harness 0.5.0 deve includere gestione modelli sicura, brand Android reale, design system condiviso, nuova architettura Compose, superfici prodotto complete, hardening UI e distribuzione interna validata.

La versione non deve essere descritta come production-ready finché il gate fisico con un vero GGUF su hardware Android `arm64-v8a` non è completato.

## 2. Modello dei branch

```text
feature / fix / docs branch
            |
            | pull request
            v
           dev
            |
            | promotion pull request
            v
           main
            |
            | tag / signed artifact
            v
 Google Play Internal Testing
```

Regole operative:

- feature, fix non urgenti e documentazione partono da `dev` e aprono PR verso `dev`;
- `main` accetta normalmente solo promotion PR da `dev`;
- hotfix urgenti partono da `main` e vengono poi forward-portati in `dev`;
- feature PR verso `dev`: squash merge salvo eccezioni documentate;
- promotion `dev -> main`: merge commit;
- nessun workflow deve eseguire push diretto su `main` o `dev`.

## 3. Stato di avanzamento

| Blocco | Stato | Evidenza principale |
| --- | --- | --- |
| OPS-01 Ripristino validazione | completato | PR #55, merge `2935743` |
| OPS-02 Protezione `main` | completato | ruleset verificata e `main` verde |
| OPS-03 Creazione disciplina `dev` | completato nel repository; azione amministrativa residua | branch `dev` attivo; issue #59 per protezione amministrativa |
| OPS-04 CI cumulativa e promotion gate | completato | PR #57, commit `77ab158` |
| OPS-05 Documentazione canonica | completato | PR #57, ADR 0008 e documenti aggiornati |
| REC-01 Model management sicuro | completato | PR #53, commit `9451314` |
| REC-02 Chiusura legacy | completato | PR #34 chiusa come superseded |
| UX-01 Brand Android reale | completato | PR #60, commit `c9d7a2c` |
| UX-02 Design system e tema | completato | PR #61, commit `22c4d9c` |
| UX-03 Shell e Navigation Compose | prossimo | da avviare su branch fresco da `dev` |

## 4. Fase 0 — Freeze e ripristino di `main`

### OPS-01 — Ripristinare la validazione del repository

- [x] completare `ThrowingTelemetryRepository` con `benchmarkBaselineHistory(limit)`;
- [x] aggiungere il test di regressione;
- [x] correggere il fan-out CI per i contratti pubblici;
- [x] aggiungere i test per `detect_ci_scope.py`;
- [x] aggiornare il workflow brand;
- [x] eliminare push diretti dal workflow brand;
- [x] rendere la generazione brand riproducibile;
- [x] ottenere il gate completo verde.

### OPS-02 — Proteggere `main`

- [x] richiedere pull request;
- [x] richiedere `Repository validation`;
- [x] richiedere branch aggiornata, review e conversazioni risolte;
- [x] vietare force-push ed eliminazione;
- [x] applicare la regola agli amministratori salvo break-glass;
- [x] impedire bypass dei workflow.

## 5. Fase 1 — Linea di integrazione `dev`

### OPS-03 — Creare e proteggere `dev`

- [x] creare `dev` dalla baseline verde;
- [x] mantenere `main` come default branch;
- [x] aggiornare template e branch policy delle PR;
- [x] aggiungere il controllo che blocca feature PR dirette a `main`;
- [ ] applicare la protezione amministrativa di `dev` equivalente a `main`;
- [ ] verificare con un tentativo non distruttivo che un push diretto venga rifiutato.

L'azione amministrativa residua è tracciata nell'issue #59. La mancanza della ruleset non blocca lo sviluppo tecnico successivo, ma deve essere chiusa prima della promotion finale della 0.5.0.

### OPS-04 — Adattare la CI a `dev`

- [x] eseguire `Validate` sui push a `dev`;
- [x] mantenere il fast path documentale;
- [x] validare tutti i consumer quando cambiano contratti pubblici;
- [x] eseguire native host tests quando cambia il backend nativo;
- [x] eseguire packaging per app, manifest, ABI, launcher e Gradle;
- [x] eseguire validazione cumulativa dopo ogni merge;
- [x] aggiungere il gate completo per promotion verso `main`;
- [x] rendere il packaging eseguibile sul candidato `dev`.

### OPS-05 — Rendere canonica la disciplina

- [x] aggiornare `BRANCHING.md`;
- [x] aggiornare `AGENTS.md`;
- [x] aggiornare `README.md`;
- [x] aggiornare `docs/current-state.md`;
- [x] aggiornare `docs/roadmap.md`;
- [x] aggiornare `docs/definition-of-done.md`;
- [x] aggiornare `docs/versioning.md`;
- [x] aggiungere ADR 0008 sulla strategia `dev -> main`;
- [x] aggiornare template PR e policy repository.

## 6. Fase 2 — Recovery model management

### REC-01 — Recuperare la PR #53

- [x] retargettare la PR a `dev`;
- [x] riallinearla alla baseline corrente;
- [x] eliminare i workflow temporanei auto-modificanti;
- [x] rendere stateful il fake dei test;
- [x] aggiornare store simulato e metadata durante la rimozione;
- [x] proteggere modello selezionato o posseduto dal runtime;
- [x] coprire conferma, annullamento, successo ed errori;
- [x] evitare esposizione di path, URI e dati sensibili;
- [x] completare wiring Compose dello stato visibile;
- [x] ottenere Spotless, Detekt, test, Lint, compilazione e packaging verdi;
- [x] eseguire squash merge in `dev`.

### REC-02 — Chiudere il ramo legacy

- [x] auditare la PR #34;
- [x] confermare che la parte utile fosse recuperata nella PR #53;
- [x] chiudere la PR #34 come superseded;
- [x] non recuperare il vecchio import SAF e la console parallela;
- [x] non riaprire il percorso prodotto legacy.

## 7. Fase 3 — Brand Android e design system

### UX-01 — Asset e identità Android

- [x] definire master SVG repository-owned;
- [x] creare VectorDrawable e fallback;
- [x] creare adaptive launcher icon;
- [x] creare monochrome/themed icon Android 13+;
- [x] collegare icona e round icon nel manifest;
- [x] validare safe zone e leggibilità;
- [x] mantenere gli asset documentali come reference;
- [x] rendere la generazione riproducibile;
- [x] verificare risorse in APK e AAB.

### UX-02 — Design system e tema

- [x] separare palette, tipografia, shape, spacing e componenti;
- [x] introdurre light, dark e system theme;
- [x] documentare la policy font completamente offline;
- [x] introdurre app bar, navigation, card, metriche, badge e azioni condivise;
- [x] introdurre dialoghi e stati loading, empty ed error;
- [x] preservare le API già usate dall'app;
- [x] aggiungere preview Compose dark e light;
- [x] aggiungere test automatici WCAG AA;
- [x] applicare touch target minimo di 48 dp;
- [x] ottenere validazione cumulativa verde su `dev`.

## 8. Fase 4 — Nuova architettura UX/UI

Le PR devono essere verticali e preservare il comportamento reale già connesso.

### UX-03 — Shell, Navigation Compose e back stack

- [ ] introdurre `HarnessApp` e `HarnessNavHost`;
- [ ] definire route top-level e detail route;
- [ ] usare bottom navigation su compact e navigation rail su medium/expanded;
- [ ] implementare back behavior e deep-link interni;
- [ ] preservare la generazione attiva durante la navigazione;
- [ ] ridurre `MainActivity` a composition root, Activity Result e wiring di alto livello;
- [ ] mantenere Storage Access Framework tramite Activity Result API;
- [ ] aggiungere test di navigazione e state restoration non sensibile.

### UX-04 — Playground ViewModel/UDF

- [ ] introdurre stato immutabile, azioni ed effetti;
- [ ] mantenere prompt e output soltanto in memoria;
- [ ] conservare streaming, cancellazione, cleanup e metriche reali;
- [ ] aggiungere generation settings sheet;
- [ ] implementare smart auto-scroll;
- [ ] coalescere gli update troppo frequenti;
- [ ] testare complete, failed, cancelled e cleanup failed;
- [ ] verificare warm reuse e richieste concorrenti.

### UX-05 — Models ViewModel/UDF e catalogo multi-modello

- [ ] integrare verifica e rimozione recuperate;
- [ ] implementare lista, dettaglio e conferme;
- [ ] completare catalog/store reconciliation;
- [ ] mostrare orphaned, unavailable e failed verification;
- [ ] rendere deterministica la selezione attiva;
- [ ] preservare deduplicazione per digest;
- [ ] bloccare la rimozione durante ownership runtime;
- [ ] non mostrare path, URI o URL firmati;
- [ ] testare più modelli dopo restart.

### UX-06 — Overview

- [ ] usare soltanto dati reali o `Unavailable`;
- [ ] mostrare modello selezionato/caricato e stato runtime;
- [ ] aggiungere risorse, attività recente e quick action;
- [ ] coprire no-model, loading, ready, busy, thermal warning, low-memory ed error.

### UX-07 — Diagnostics

- [ ] completare Health, Runs, timeline e route dettaglio;
- [ ] aggiungere resource charts con gap per valori null;
- [ ] completare benchmark, readiness, confronto e history;
- [ ] mantenere filtri, copy e request correlation dei log;
- [ ] usare query bounded;
- [ ] separare osservazione, health e azioni distruttive.

### UX-08 — Settings e developer tools

- [ ] tema System/Dark/Light;
- [ ] privacy disclosure coerente con download e permesso Internet;
- [ ] storage summary e cleanup con conferma;
- [ ] mostrare versione, commit SHA, ABI e backend revision;
- [ ] collegare health, logs, resources e physical validation;
- [ ] permettere copy/share soltanto di dati privacy-safe.

Criterio di uscita della Fase 4:

- nessuna schermata chiama direttamente orchestrator, store, repository o executor;
- `MainActivity` non possiede stato di dominio;
- tutte le funzioni esistenti restano raggiungibili;
- la navigazione non introduce lavoro runtime implicito.

## 9. Fase 5 — Hardening e release candidate

### UX-09 — Test e qualità

- [ ] Compose UI test dei flussi principali;
- [ ] screenshot/golden dark e light;
- [ ] compact, medium, tablet e landscape;
- [ ] font scale 1.0, 1.5 e controllo manuale 2.0;
- [ ] empty/loading/populated/warning/failure;
- [ ] TalkBack e semantic traversal;
- [ ] touch-target e contrast audit;
- [ ] Macrobenchmark per startup e navigazione;
- [ ] profiling streaming e memoria UI bounded;
- [ ] verificare che il first frame non inizializzi `llama.cpp`.

### REL-01 — Preparare Harness 0.5.0

- [ ] congelare nuove feature su `dev`;
- [ ] aggiornare changelog e versioni;
- [ ] usare `0.5.0-rc.N` durante internal testing;
- [ ] eseguire il gate completo da checkout pulito;
- [ ] produrre APK, AAB, AAR e checksum;
- [ ] verificare AAB, ABI, icone e assenza di GGUF/GGML;
- [ ] creare promotion PR `dev -> main`;
- [ ] firmare l'AAB con upload key esterna;
- [ ] pubblicare su Google Play Internal Testing;
- [ ] conservare il precedente artefatto noto come buono.

### REL-02 — Evidenza fisica

- [ ] installare tramite Google Play su hardware rappresentativo;
- [ ] importare o scaricare un GGUF supportato;
- [ ] verificare download, installazione, selezione e integrità;
- [ ] eseguire generazione, streaming e cancellazione;
- [ ] eseguire cicli load/generate/unload;
- [ ] registrare PSS, TTFT, throughput e thermal state;
- [ ] verificare tutte le superfici sul device;
- [ ] completare TalkBack, font scaling, portrait e landscape;
- [ ] allegare evidenza privacy-safe al release record;
- [ ] creare il tag finale solo a Definition of Done soddisfatta.

## 10. Ordine aggiornato delle pull request

| Ordine | Blocco | Stato | Base |
| ---: | --- | --- | --- |
| 1 | OPS-01 Restore repository validation | completato | `main` |
| 2 | OPS-03/04/05 Introduce `dev` integration | completato nel repository | `main` / `dev` |
| 3 | REC-01 Safe model management | completato | `dev` |
| 4 | REC-02 Close legacy recovery | completato | `dev` |
| 5 | UX-01 Android brand assets | completato | `dev` |
| 6 | UX-02 Shared design system | completato | `dev` |
| 7 | UX-03 Navigation shell | prossimo | `dev` |
| 8 | UX-04 Playground UDF | pianificato | `dev` |
| 9 | UX-05 Models UDF | pianificato | `dev` |
| 10 | UX-06 Overview | pianificato | `dev` |
| 11 | UX-07 Diagnostics | pianificato | `dev` |
| 12 | UX-08 Settings | pianificato | `dev` |
| 13 | UX-09 Hardening | pianificato | `dev` |
| 14 | REL-01 Promotion 0.5.0 | pianificato | `main` da `dev` |
| 15 | REL-02 Physical evidence | pianificato | release candidate |

## 11. Regole di validazione

Prima del merge in `dev`:

- diff completo revisionato;
- nessun file temporaneo o modifica estranea;
- formatter, static analysis e test verdi;
- consumer diretti compilati quando cambia un contratto;
- documentazione aggiornata nella stessa PR;
- nessun GGUF, chiave, credenziale, URI privata o path sensibile;
- `Repository validation` verde per modifiche implementative;
- per PR esclusivamente documentali è sufficiente il percorso documentale rapido e non serve attendere build Android o native non pertinenti.

Prima della promotion in `main`:

- `dev` verde e congelata;
- ruleset di `dev` applicata e verificata;
- gate completo non scoped;
- packaging completo;
- changelog, versioni, rollback e checksum pronti;
- nessun blocco release aperto.

## 12. Rollback

- Su `dev`: revert della PR o fix-forward isolato; mai reset o force-push.
- Su `main`: revert tramite PR prima della distribuzione oppure nuova build con `versionCode` superiore.
- Ogni hotfix di `main` deve essere forward-portato in `dev`.
- Il rollback UI non deve rimuovere automaticamente modelli o metadata validi.

## 13. Ambito escluso da Harness 0.5.0

- Binder/shared runtime;
- diagnostics bridge cross-app definitivo;
- plugin Capacitor produttivo;
- SDK Android pubblico completo;
- GPU/Vulkan come default;
- parallel decode;
- sincronizzazione amministrativa remota completa del catalogo;
- claim di compatibilità oltre la matrice fisicamente testata.

## 14. Definition of Done del piano

Il piano è completato quando:

- [ ] `main` e `dev` sono protetti e verdi;
- [x] le feature PR puntano a `dev`;
- [x] le promotion PR sono il percorso ordinario verso `main`;
- [x] PR #53 è integrata e PR #34 è chiusa;
- [x] nessun workflow auto-modificante resta attivo;
- [x] brand launcher e design system sono integrati;
- [ ] Navigation Compose e ViewModel/UDF sostituiscono lo stato di dominio in `MainActivity`;
- [ ] le superfici UX/UI principali usano dati reali;
- [ ] UI test, screenshot, accessibilità e responsive gate passano;
- [ ] CI e packaging completi passano da checkout pulito;
- [ ] Harness 0.5.0 è promossa da `dev` a `main`;
- [ ] l'AAB firmato è installato tramite Internal Testing;
- [ ] l'evidenza fisica privacy-safe è registrata;
- [ ] documentazione, roadmap, changelog e release record concordano.

## 15. Prossima azione

Avviare **UX-03 — Shell, Navigation Compose e back stack** su un branch fresco dall'ultimo `dev` verde. In parallelo, completare l'azione amministrativa dell'issue #59 prima della promotion finale della release.