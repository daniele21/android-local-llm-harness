# Piano di integrazione `dev` e rilascio Harness 0.5.0

**Stato:** proposto
**Data:** 2026-08-05
**Ambito:** governance Git, ripristino CI, recovery model management, brand Android, UX/UI, validazione e rilascio interno

## 1. Obiettivo

Introdurre una linea di integrazione stabile chiamata `dev` nella quale confluiscono le pull request di prodotto e infrastruttura, mantenendo `main` protetto e utilizzabile solo per promozioni validate, hotfix eccezionali e release.

Il risultato atteso è una versione Harness 0.5.0 che includa:

- gestione modelli sicura recuperata dalla PR #53;
- brand kit integrato nell'app Android, non soltanto nei documenti;
- architettura UI Compose basata su Navigation Compose, ViewModel e flusso dati unidirezionale;
- completamento delle principali superfici Overview, Playground, Models, Diagnostics e Settings;
- test UI, screenshot, accessibilità e layout responsive;
- AAB firmato distribuito tramite Google Play Internal Testing;
- evidenza privacy-safe su hardware Android `arm64-v8a` rappresentativo.

La versione non deve essere descritta come production-ready finché il gate fisico GGUF non è completato.

## 2. Modello dei branch

```text
feature / fix / docs branch
            |
            | pull request
            v
           dev  ------------------------------+
            |                                 |
            | promotion pull request          | nuove PR
            v                                 |
           main                               |
            |                                 |
            | tag / signed artifact           |
            v                                 |
 Google Play Internal Testing                 |
                                              |
main hotfix -> main -> forward-port PR -> dev-+
```

### 2.1 `main`

`main` è la linea stabile, protetta e promuovibile.

Regole:

- nessun push diretto, incluso da GitHub Actions;
- accetta normalmente solo promotion PR da `dev`;
- accetta hotfix diretti solo quando `main` è rotto o un artefatto già distribuito richiede una correzione urgente;
- richiede il check stabile `Repository validation` sul commit corrente;
- richiede branch aggiornata, conversazioni risolte e almeno una review;
- vieta force-push ed eliminazione;
- produce tag e artefatti di release solo da commit validati;
- non riceve feature branch ordinarie.

### 2.2 `dev`

`dev` è la linea canonica di integrazione e test continuo.

Regole:

- viene creata dal primo `main` nuovamente verde;
- tutte le feature, fix non urgenti, aggiornamenti UX/UI e documentazione di prodotto partono da `dev` e aprono PR verso `dev`;
- nessun push diretto;
- richiede `Repository validation`, branch aggiornata e conversazioni risolte;
- vieta force-push ed eliminazione;
- rimane verde; se il push successivo a un merge fallisce, le integrazioni vengono sospese e si applica un fix-forward prioritario;
- non viene usata per tag o dichiarazioni di release.

### 2.3 Branch di lavoro

Ogni branch di lavoro:

- nasce dall'ultimo `dev`, salvo hotfix esplicitamente basato su `main`;
- contiene un solo deliverable coerente;
- usa il prefisso previsto dall'ambiente di sviluppo, preferibilmente `codex/` per nuovo lavoro automatizzato;
- viene eliminato dopo merge e audit;
- non viene riutilizzato dopo merge o chiusura come superseded.

### 2.4 Hotfix

Un hotfix urgente segue:

```text
latest main
  -> hotfix branch
  -> pull request verso main
  -> full validation
  -> merge
  -> pull request main -> dev
  -> verifica che dev contenga la stessa correzione
```

Non si applica la stessa modifica manualmente su entrambi i branch.

### 2.5 Metodo di merge

- Feature PR verso `dev`: squash merge, salvo una motivazione documentata per preservare più commit.
- Promotion PR `dev -> main`: merge commit, senza squash o rebase, per conservare l'identità esatta della linea validata.
- Dopo la promozione: sincronizzare il merge commit di `main` in `dev` prima del successivo ciclo di promozione.
- Hotfix verso `main`: squash merge, poi forward-port tramite PR `main -> dev`.

## 3. Stato iniziale da correggere

Alla data del piano:

- `main` è a `6cb1871`;
- l'ultima validazione Android completa di `main` è rossa;
- `ThrowingTelemetryRepository` non implementa `benchmarkBaselineHistory()`;
- la CI scoped non ha compilato i test dei consumer diretti quando è cambiato `observability/contracts`;
- la PR #53 è draft, indietro rispetto a `main` e contiene workflow temporanei auto-modificanti;
- il workflow temporaneo di #53 fallisce anche in `PhoneModelDistributionControllerTest`;
- la PR #34 è legacy e non deve essere integrata;
- gli asset PNG del brand sono presenti sotto `docs/assets/brand`, ma non sono launcher asset o componenti Android;
- la UI corrente è connessa al runtime reale, ma conserva debito in `MainActivity`, navigazione, ViewModel/UDF e test UI;
- la protezione di `main` è ancora tracciata dall'issue #46.

## 4. Sequenza operativa

## Fase 0 — Freeze e ripristino di `main`

### OPS-01 — Ripristinare la validazione del repository

Creare un branch di bootstrap dall'ultimo `main`. Per l'esecuzione corrente il branch locale si chiama `dev`: parte dall'esatto commit di `main`, accetta soltanto OPS-01 e non è ancora la linea canonica di integrazione descritta nella Fase 1. La prima pubblicazione è l'eccezione di bootstrap; nessuna feature confluisce in `dev` finché OPS-01 non è promosso e le protezioni di OPS-03 non sono applicate.

Modifiche richieste:

- [x] aggiungere a `ThrowingTelemetryRepository` l'override di `benchmarkBaselineHistory(limit)` coerente con gli altri metodi del fake;
- [x] aggiungere o aggiornare il test di regressione pertinente;
- [x] modificare `scripts/detect_ci_scope.py` affinché un cambiamento ai contratti pubblici compili e testi i consumer diretti;
- [x] per `core/contracts` e `observability/contracts`, preferire la validazione Android completa se il grafo dei consumer non è mantenuto in modo esplicito e verificabile;
- [x] aggiungere test a `scripts/test_detect_ci_scope.py` per il fan-out dei contratti;
- [x] aggiornare il workflow del brand da `actions/checkout@v4` a `actions/checkout@v7`;
- [x] rimuovere dal workflow del brand il push diretto su `main`;
- [x] trasformare la generazione del brand in un controllo riproducibile con `git diff --exit-code`, oppure in un'automazione che apra una PR separata;
- [ ] eseguire il gate completo locale e CI;
- [x] aggiornare il ledger dello stato senza dichiarare completato il model management.

Validazione minima:

```bash
python3 scripts/verify-agent-navigation.py
python3 -m py_compile scripts/*.py
python3 scripts/test_detect_ci_scope.py
./gradlew spotlessCheck
./gradlew --no-configuration-cache detekt verifyNoModelArtifacts
./gradlew check
./gradlew lintDebug :apps:local-llm-console:lintInternal
```

Criterio di uscita:

- `Repository validation` verde sul commit head della hotfix PR;
- `main` verde dopo il merge;
- nessun workflow può modificare direttamente `main`.

### OPS-02 — Proteggere `main`

Applicare l'issue #46 tramite branch protection o repository ruleset.

- [ ] richiedere pull request;
- [ ] richiedere `Repository validation`;
- [ ] richiedere branch aggiornata;
- [ ] richiedere almeno una approvazione;
- [ ] richiedere conversazioni risolte;
- [ ] vietare force-push ed eliminazione;
- [ ] applicare le regole anche agli amministratori, salvo procedura break-glass documentata;
- [ ] impedire bypass ai workflow con `contents: write`;
- [ ] abilitare eliminazione automatica dei branch integrati quando non serve conservarli per audit.

Criterio di uscita:

- un push diretto di prova viene rifiutato;
- una PR con check rosso non può essere unita.

## Fase 1 — Creazione della linea `dev`

### OPS-03 — Creare e proteggere `dev`

Solo dopo OPS-01:

- [ ] creare `dev` dall'esatto commit verde di `main`;
- [ ] pubblicare `dev` senza commit aggiuntivi;
- [ ] applicare protezioni equivalenti a quelle di `main` per push, force-push ed eliminazione;
- [ ] richiedere `Repository validation` sulle PR verso `dev`;
- [ ] mantenere `main` come default branch del repository;
- [ ] aggiornare il template PR affinché una feature ordinaria selezioni `dev` come base;
- [ ] aggiungere un controllo che segnali una feature PR aperta per errore verso `main`.

### OPS-04 — Adattare la CI a `dev`

- [ ] eseguire `Validate` anche sui push a `dev`;
- [ ] mantenere il percorso rapido per PR esclusivamente documentali;
- [ ] eseguire tutti i moduli Android per modifiche a contratti pubblici;
- [ ] eseguire native host tests quando cambiano JNI, C++, CMake o il pin `llama.cpp`;
- [ ] eseguire packaging quando cambiano app, manifest, ABI, risorse launcher o configurazione Gradle;
- [ ] eseguire una validazione cumulativa dopo ogni merge su `dev`;
- [ ] aggiungere un workflow di promotion/release candidate per PR con base `main`;
- [ ] mantenere `Package Android Artifacts` su `main` e renderlo eseguibile esplicitamente sul candidato `dev` prima della promotion.

Matrice prevista:

| Evento | Validazione | Packaging | Native |
| --- | --- | --- | --- |
| PR documentale -> `dev` | guard, link e script | no | no |
| PR implementativa -> `dev` | Android completa o scope con consumer verificati | se rilevante | se rilevante |
| push merge su `dev` | cumulativa | se rilevante | se rilevante |
| promotion PR `dev -> main` | completa e non scoped | completa | completa |
| push su `main` | verifica commit promosso | pubblicazione artefatti | se rilevante |

### OPS-05 — Rendere canonica la nuova disciplina

Nella PR che introduce `dev`, aggiornare insieme:

- [ ] `BRANCHING.md`;
- [ ] `AGENTS.md`;
- [ ] `README.md`;
- [ ] `docs/current-state.md`;
- [ ] `docs/roadmap.md`;
- [ ] `docs/definition-of-done.md`;
- [ ] `docs/versioning.md`;
- [ ] un nuovo ADR sulla linea di integrazione `dev` e sulla promozione protetta verso `main`;
- [ ] eventuali template PR e regole CODEOWNERS.

Il nuovo ADR deve definire almeno:

- motivazione di `dev`;
- ruoli di `dev` e `main`;
- strategia di hotfix e forward-port;
- metodo di merge per le promotion PR;
- responsabilità dei gate CI;
- comportamento in caso di `dev` rosso;
- strategia di rollback e tagging.

Criterio di uscita della Fase 1:

- `main` e `dev` sono protetti e verdi;
- i documenti non indicano più `main` come base ordinaria delle feature;
- nessuna automazione esegue push diretto su entrambi.

## Fase 2 — Recovery model management

### REC-01 — Ripulire e retargettare la PR #53

Dopo la creazione di `dev`:

- [ ] cambiare la base della PR #53 da `main` a `dev`;
- [ ] aggiornare il branch della PR sull'ultimo `dev`;
- [ ] eliminare `.github/workflows/fix-model-management-compile.yml`;
- [ ] eliminare `.github/workflows/finalize-model-management-fix.yml`;
- [ ] applicare nel sorgente, non in un runner temporaneo, il fake stateful di `PhoneModelDistributionControllerTest`;
- [ ] garantire che la rimozione aggiorni store simulato e metadata;
- [ ] ereditare da `dev` il fix `benchmarkBaselineHistory()`;
- [ ] applicare Spotless e verificare il diff completo;
- [ ] verificare che il modello selezionato o caricato non possa essere rimosso;
- [ ] verificare conferma, annullamento, successo, modello assente, errore store e cleanup metadata;
- [ ] verificare che nessun path o URI venga mostrato, persistito o loggato;
- [ ] aggiornare `docs/current-state.md`, `docs/roadmap.md` e la documentazione model management;
- [ ] ottenere CI verde e review prima di rimuovere il draft;
- [ ] eseguire squash merge verso `dev`.

Comandi minimi prima del push finale:

```bash
./gradlew spotlessApply spotlessCheck
./gradlew --no-configuration-cache detekt verifyNoModelArtifacts
./gradlew :apps:local-llm-phone-test:compileDebugKotlin \
  :apps:local-llm-phone-test:testDebugUnitTest \
  :core:runtime-core:testDebugUnitTest
./gradlew check
./gradlew lintDebug :apps:local-llm-console:lintInternal
python3 scripts/verify-android-packaging.py
```

### REC-02 — Chiudere il ramo legacy

Solo dopo il merge di REC-01:

- [ ] chiudere la PR #34 come superseded dalla PR #53;
- [ ] aggiungere una nota con il mapping delle funzionalità recuperate;
- [ ] confermare che non restino commit unici necessari;
- [ ] eliminare il branch remoto legacy quando l'audit è completo;
- [ ] non riaprire o rebasare la vecchia console come percorso prodotto.

Criterio di uscita della Fase 2:

- model management integrato e validato su `dev`;
- nessuna implementazione concorrente aperta;
- `docs/current-state.md` indica il blocco come completato su `dev`, non ancora rilasciato su `main`.

## Fase 3 — Brand Android reale

### UX-01 — Asset e identità Android

Branch fresco da `dev`, senza modifiche al runtime.

- [ ] definire master vector repository-owned per simbolo, wordmark e lockup;
- [ ] convertire il simbolo in Android VectorDrawable;
- [ ] creare adaptive launcher icon foreground/background;
- [ ] creare monochrome/themed icon per Android 13+;
- [ ] aggiungere le risorse `mipmap-anydpi-v26` e fallback necessari;
- [ ] collegare `android:icon` e `android:roundIcon` nel manifest;
- [ ] validare safe zone e leggibilità alle dimensioni previste;
- [ ] mantenere i PNG documentali come reference, non come unica sorgente runtime;
- [ ] rendere la generazione riproducibile e verificata dalla CI;
- [ ] verificare che l'AAB contenga gli asset corretti.

### UX-02 — Design system e tema

- [ ] separare colori, tipografia, shape, spacing e componenti nei file previsti dal piano;
- [ ] rimuovere colori e stili locali duplicati dalle schermate;
- [ ] completare dark, light e system theme;
- [ ] decidere e documentare la policy font offline per Inter e JetBrains Mono;
- [ ] introdurre componenti condivisi per app bar, navigation, card, status, metriche, empty/error/loading state e dialoghi;
- [ ] aggiungere preview e test dei componenti;
- [ ] verificare WCAG AA e touch target di almeno 48 dp;
- [ ] aggiornare le brand guidelines se l'implementazione richiede una variazione approvata.

Criterio di uscita della Fase 3:

- il brand è visibile nel launcher e nell'app;
- i token sono centralizzati;
- nessun valore illustrativo viene presentato come dato reale;
- packaging, screenshot preliminari e accessibilità di base sono verdi.

## Fase 4 — Nuova architettura UX/UI

Le PR seguenti devono essere verticali e mantenere parità con il comportamento reale già connesso.

### UX-03 — Shell, Navigation Compose e back stack

- [ ] introdurre `HarnessApp` e `HarnessNavHost`;
- [ ] definire route top-level e detail route;
- [ ] mantenere bottom navigation su compact e navigation rail su medium/expanded;
- [ ] implementare back behavior e deep-link interni previsti;
- [ ] preservare la generazione attiva durante la navigazione senza persistere prompt/output;
- [ ] ridurre `MainActivity` a composition root, Activity Result e wiring di alto livello;
- [ ] mantenere Storage Access Framework tramite Activity Result API;
- [ ] aggiungere test di navigazione e state restoration non sensibile.

### UX-04 — Playground ViewModel/UDF

- [ ] introdurre stato immutabile, azioni ed effetti;
- [ ] mantenere prompt/output esclusivamente in memoria di processo;
- [ ] conservare streaming, cancellazione, cleanup e metriche reali;
- [ ] aggiungere generation settings sheet;
- [ ] implementare smart auto-scroll;
- [ ] coalescere update troppo frequenti;
- [ ] testare complete, failed, cancelled e cleanup failed;
- [ ] verificare warm reuse e blocco di richieste concorrenti.

### UX-05 — Models ViewModel/UDF e catalogo multi-modello

- [ ] incorporare verifica e rimozione recuperate dalla PR #53;
- [ ] implementare lista, dettaglio e conferme;
- [ ] completare catalog/store reconciliation;
- [ ] mostrare stati orphaned, unavailable e failed verification;
- [ ] rendere deterministica la selezione attiva;
- [ ] preservare deduplicazione per digest;
- [ ] bloccare rimozione durante ownership runtime;
- [ ] non mostrare path, URI o URL firmati;
- [ ] testare persistenza di più modelli dopo restart.

### UX-06 — Overview

- [ ] usare soltanto dati reali o `Unavailable`;
- [ ] mostrare modello selezionato/caricato, runtime cold/warm/busy e operazione attiva;
- [ ] aggiungere risorse e attività recente;
- [ ] collegare quick action senza side effect di navigazione;
- [ ] coprire no-model, loading, ready, busy, thermal warning, low-memory ed error state.

### UX-07 — Diagnostics

- [ ] completare Health con azioni mirate e capability state;
- [ ] completare Runs con route dettaglio e timeline;
- [ ] aggiungere resource charts con gap per valori null;
- [ ] completare benchmark key selection, readiness, confronto e history;
- [ ] mantenere filtri, copy e request correlation dei log;
- [ ] usare query bounded;
- [ ] separare osservazione, health e azioni distruttive;
- [ ] non avviare operazioni durante refresh o navigazione.

### UX-08 — Settings e developer tools

- [ ] tema System/Dark/Light;
- [ ] privacy disclosure coerente con il permesso Internet e il download catalogo;
- [ ] storage summary e cleanup con conferma;
- [ ] app version, commit SHA, ABI e revisione backend;
- [ ] accesso a health, logs, resources e physical validation;
- [ ] copy/share soltanto di dati privacy-safe;
- [ ] spostare la validazione fisica fuori dal flusso primario.

Criterio di uscita della Fase 4:

- nessuna schermata chiama direttamente `RuntimeOrchestrator`, `ModelStore`, repository o executor;
- `MainActivity` non possiede stato di dominio delle schermate;
- tutte le funzioni esistenti restano raggiungibili;
- le schermate non introducono lavoro runtime implicito.

## Fase 5 — Hardening UX e release candidate

### UX-09 — Test e qualità

- [ ] Compose UI test per i flussi principali;
- [ ] screenshot/golden test dark e light;
- [ ] compact 360x800 e 411x891 equivalenti;
- [ ] medium/tablet e landscape;
- [ ] font scale 1.0, 1.5 e controllo manuale 2.0;
- [ ] empty/loading/populated/warning/failure state;
- [ ] TalkBack e semantic traversal;
- [ ] touch-target audit;
- [ ] verifica contrasto e uso non esclusivo del colore;
- [ ] Macrobenchmark per startup e navigazione;
- [ ] profiling streaming e memoria UI bounded;
- [ ] verifica che il first frame non inizializzi `llama.cpp`.

### REL-01 — Preparare Harness 0.5.0

- [ ] congelare nuove feature su `dev`;
- [ ] aggiornare `CHANGELOG.md`;
- [ ] riservare `versionName` 0.5.0 e incrementare `versionCode` per ogni upload Play;
- [ ] usare versioni `0.5.0-rc.N` durante internal testing;
- [ ] aggiornare note di compatibilità, limitazioni e rollback;
- [ ] eseguire il gate completo da checkout pulito;
- [ ] produrre APK, AAB, AAR e checksum;
- [ ] verificare contenuto AAB, ABI `arm64-v8a`, icone e assenza di GGUF/GGML;
- [ ] creare promotion PR `dev -> main`;
- [ ] rieseguire validazione completa e packaging sul commit promosso;
- [ ] firmare l'AAB con upload key esterna;
- [ ] pubblicare su Google Play Internal Testing;
- [ ] conservare il precedente artefatto noto come buono.

### REL-02 — Evidenza fisica

- [ ] installare tramite Google Play su hardware fisico rappresentativo;
- [ ] importare o scaricare un GGUF supportato;
- [ ] verificare download, installazione, selezione e integrità;
- [ ] eseguire generazione e streaming;
- [ ] verificare cancellazione durante prefill e decode;
- [ ] eseguire cicli ripetuti load/generate/unload;
- [ ] registrare PSS, TTFT, throughput e thermal state;
- [ ] verificare Overview, Models, Playground, Diagnostics e Settings sul device;
- [ ] completare TalkBack, font scaling, portrait e landscape;
- [ ] allegare evidenza privacy-safe al release record;
- [ ] creare il tag finale soltanto quando la Definition of Done applicabile è soddisfatta.

## 5. Ordine delle pull request

| Ordine | PR logica | Base | Scopo | Blocco successivo |
| ---: | --- | --- | --- | --- |
| 1 | OPS-01 Restore repository validation | `main` | hotfix CI e fake telemetry | OPS-02 |
| 2 | OPS-03/04 Introduce protected dev integration | `main` | branch policy, CI, ADR e documenti | REC-01 |
| 3 | REC-01 Recover safe phone model management | `dev` | pulizia e completamento PR #53 | REC-02 |
| 4 | UX-01 Android brand assets | `dev` | launcher e vector asset | UX-02 |
| 5 | UX-02 Complete design system | `dev` | token, tema e componenti | UX-03 |
| 6 | UX-03 Navigation shell | `dev` | NavHost e Activity slim | UX-04 |
| 7 | UX-04 Playground UDF | `dev` | inferenza UI | UX-05 |
| 8 | UX-05 Models UDF | `dev` | multi-model e management | UX-06 |
| 9 | UX-06 Overview | `dev` | dashboard reale | UX-07 |
| 10 | UX-07 Diagnostics | `dev` | health/runs/resources/benchmarks/logs | UX-08 |
| 11 | UX-08 Settings | `dev` | amministrazione e developer tools | UX-09 |
| 12 | UX-09 Hardening | `dev` | UI, screenshot, a11y, performance | REL-01 |
| 13 | REL-01 Promotion Harness 0.5.0 | `main` da `dev` | versione e release candidate | REL-02 |

Le PR possono essere ulteriormente divise quando il diff supera un confine di responsabilità. Non devono essere accorpate per compensare ritardi di pianificazione.

## 6. Regole di validazione per ogni PR

Prima del push:

- [ ] diff completo revisionato;
- [ ] nessuna modifica estranea o generata temporaneamente;
- [ ] formatter e static analysis verdi;
- [ ] produzione e test dei moduli interessati compilati;
- [ ] consumer diretti compilati quando cambia un contratto;
- [ ] test success, failure, cancellation e cleanup dove applicabili;
- [ ] documentazione aggiornata nella stessa PR;
- [ ] nessun GGUF, chiave, credenziale, URI privata o path sensibile nel diff.

Prima del merge in `dev`:

- [ ] branch aggiornata con `dev`;
- [ ] `Repository validation` verde;
- [ ] review completata;
- [ ] conversazioni risolte;
- [ ] nessuna PR concorrente sulla stessa ownership;
- [ ] deferimenti fisici dichiarati senza claim di production readiness.

Prima della promotion in `main`:

- [ ] `dev` verde e congelata;
- [ ] gate completo non scoped;
- [ ] packaging completo;
- [ ] changelog e versioni corretti;
- [ ] piano di rollback verificato;
- [ ] artifact inventory e checksum disponibili;
- [ ] limitazioni di device/model esplicite;
- [ ] nessun blocco release aperto.

## 7. Rollback e recovery

### 7.1 `dev`

- Non usare reset o force-push.
- Revertire la PR responsabile o applicare un fix-forward isolato.
- Sospendere merge ulteriori finché il branch non torna verde.
- Non promuovere un `dev` che ha avuto un gate rosso non risolto sul commit corrente.

### 7.2 `main`

- Revertire la promotion PR con una nuova PR se il problema viene scoperto prima della distribuzione.
- Se un AAB è già stato caricato su Play, non riutilizzare né diminuire il `versionCode`.
- Pubblicare una build correttiva con `versionCode` superiore, costruita da un commit noto e verificato.
- Forward-portare ogni hotfix da `main` a `dev` prima di riprendere le feature.

### 7.3 Dati e modelli

- Non introdurre rollback distruttivi della Room database.
- Ogni nuova migrazione deve essere non distruttiva e testata dalla versione precedente.
- Conservare l'identità SHA-256 e non riscrivere artifact installati durante rollback UI.
- Il rollback dell'app non deve rimuovere automaticamente modelli o metadata validi.

## 8. Monitoraggio del rilascio interno

Durante internal testing osservare e raccogliere in forma privacy-safe:

- crash o errori JNI;
- fallimenti download, verifica e installazione;
- fallimenti di selezione o rimozione modelli;
- richieste complete, fallite e cancellate;
- TTFT, durata totale e decode throughput;
- PSS, heap nativo, heap Java e thermal state;
- crescita memoria nei cicli ripetuti;
- errori di navigazione, contenuto tagliato e problemi TalkBack;
- differenze tra cold, warm e loaded runtime;
- commit SHA, app version, device, Android version, ABI e model digest.

Prompt e output non devono entrare nel report condiviso.

## 9. Ambito escluso da Harness 0.5.0

Salvo nuova decisione esplicita, non fanno parte della release:

- Binder/shared runtime;
- diagnostics bridge cross-app definitivo;
- Capacitor plugin produttivo;
- SDK Android pubblico completo;
- GPU/Vulkan come default;
- parallel decode;
- WorkManager/foreground service per tutte le operazioni lunghe;
- sincronizzazione remota amministrativa completa del catalogo;
- claim di compatibilità ampia oltre la matrice fisicamente testata.

Questi elementi restano nel piano generale e devono partire da `dev` dopo la stabilizzazione della 0.5.0.

## 10. Definition of Done del piano

Il piano è completato quando:

- [ ] `main` e `dev` sono protetti e verdi;
- [ ] le feature PR puntano a `dev`;
- [ ] le promotion PR sono l'unico percorso ordinario verso `main`;
- [ ] gli hotfix vengono forward-portati;
- [ ] PR #53 è integrata e PR #34 è chiusa come superseded;
- [ ] nessun workflow auto-modificante o push diretto resta attivo;
- [ ] brand launcher e design system sono integrati nell'app;
- [ ] Navigation Compose e ViewModel/UDF sostituiscono lo stato di dominio in `MainActivity`;
- [ ] le principali funzionalità del piano UX/UI sono collegate a dati reali;
- [ ] UI test, screenshot, accessibilità e responsive gate passano;
- [ ] CI e packaging completi passano da checkout pulito;
- [ ] Harness 0.5.0 è promossa da `dev` a `main`;
- [ ] l'AAB firmato è installato tramite Google Play Internal Testing;
- [ ] l'evidenza fisica privacy-safe è registrata;
- [ ] documentazione, roadmap, changelog e release record concordano con il comportamento effettivo.

## 11. Prima azione

Eseguire OPS-01 sul branch locale `dev`, creato dall'esatto commit dell'attuale `main`, e usarlo esclusivamente come bootstrap. Aprire la promotion PR verso `main`, ottenere il gate completo verde e sincronizzare il merge commit di `main` in `dev`; soltanto allora applicare OPS-03 e iniziare a usarlo come linea di integrazione per le feature.
