# Piano di integrazione `dev` e rilascio Harness 0.5.0

**Stato:** in esecuzione — Fasi 0, 2 e 3 completate; Fase 1 operativa con protezione amministrativa di `dev` ancora aperta (#59); Fase 4 parzialmente implementata e in attesa di integrazione/validazione remota
**Data:** 2026-08-06
**Ultimo avanzamento registrato:** `origin/dev` a `2850d03`; il candidato locale ribasato aggiunge shell Navigation Compose, visual matching delle cinque superfici e tooling riproducibile per build release/emulatore
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
- [x] eseguire il gate completo locale e CI;
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

- [x] richiedere pull request;
- [x] richiedere `Repository validation`;
- [x] richiedere branch aggiornata;
- [x] richiedere almeno una approvazione;
- [x] richiedere conversazioni risolte;
- [x] vietare force-push ed eliminazione;
- [x] applicare le regole anche agli amministratori, salvo procedura break-glass documentata;
- [x] impedire bypass ai workflow con `contents: write`;
- [x] conservare `dev` tra le promotion e rimuovere gli altri branch integrati dopo l'audit, senza attivare la cancellazione automatica globale.

Criterio di uscita:

- un push diretto di prova viene rifiutato;
- una PR con check rosso non può essere unita.

Evidenza della Fase 0: la PR #55 è stata unita con merge commit `2935743`; `Repository validation`, native host tests, brand reproducibility e packaging Android sono verdi sul commit di `main`. La configurazione riletta tramite API applica le regole anche agli amministratori e non consente force-push o eliminazione. Non è stato eseguito un push distruttivo di prova su `main`.

## Fase 1 — Creazione della linea `dev`

### OPS-03 — Creare e proteggere `dev`

Solo dopo OPS-01:

- [x] creare `dev` dall'esatto commit verde di `main`;
- [x] pubblicare `dev` senza commit aggiuntivi;
- [ ] applicare protezioni equivalenti a quelle di `main` per push, force-push ed eliminazione (issue amministrativa #59);
- [ ] richiedere `Repository validation` come regola GitHub bloccante sulle PR verso `dev` (issue amministrativa #59);
- [x] mantenere `main` come default branch del repository;
- [x] aggiornare il template PR affinché una feature ordinaria selezioni `dev` come base;
- [x] aggiungere un controllo che segnali una feature PR aperta per errore verso `main`.

### OPS-04 — Adattare la CI a `dev`

- [x] eseguire `Validate` anche sui push a `dev`;
- [x] mantenere il percorso rapido per PR esclusivamente documentali;
- [x] eseguire tutti i moduli Android per modifiche a contratti pubblici;
- [x] eseguire native host tests quando cambiano JNI, C++, CMake o il pin `llama.cpp`;
- [x] eseguire packaging quando cambiano app, manifest, ABI, risorse launcher o configurazione Gradle;
- [x] eseguire una validazione cumulativa dopo ogni merge su `dev`;
- [x] aggiungere un workflow di promotion/release candidate per PR con base `main`;
- [x] mantenere `Package Android Artifacts` su `main` e renderlo eseguibile esplicitamente sul candidato `dev` prima della promotion.

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

- [x] `BRANCHING.md`;
- [x] `AGENTS.md`;
- [x] `README.md`;
- [x] `docs/current-state.md`;
- [x] `docs/roadmap.md`;
- [x] `docs/definition-of-done.md`;
- [x] `docs/versioning.md`;
- [x] un nuovo ADR sulla linea di integrazione `dev` e sulla promozione protetta verso `main`;
- [x] eventuali template PR e regole CODEOWNERS.

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

Evidenza della Fase 1: la PR #57 è stata unita in `dev` con commit `77ab158`; CI, policy di base delle PR, packaging del candidato, ADR 0008 e documentazione canonica sono integrati. La fase è operativa nel repository, ma il criterio amministrativo di protezione del branch non è ancora chiuso: l'issue #59 resta aperta per applicare e verificare il ruleset GitHub su `dev`.

## Fase 2 — Recovery model management

### REC-01 — Ripulire e retargettare la PR #53

Dopo la creazione di `dev`:

- [x] cambiare la base della PR #53 da `main` a `dev`;
- [x] aggiornare il branch della PR sull'ultimo `dev`;
- [x] eliminare `.github/workflows/fix-model-management-compile.yml`;
- [x] eliminare `.github/workflows/finalize-model-management-fix.yml`;
- [x] applicare nel sorgente, non in un runner temporaneo, il fake stateful di `PhoneModelDistributionControllerTest`;
- [x] garantire che la rimozione aggiorni store simulato e metadata;
- [x] ereditare da `dev` il fix `benchmarkBaselineHistory()`;
- [x] applicare Spotless e verificare il diff completo;
- [x] verificare che il modello selezionato o caricato non possa essere rimosso;
- [x] verificare conferma, annullamento, successo, modello assente, errore store e cleanup metadata;
- [x] verificare che nessun path o URI venga mostrato, persistito o loggato;
- [x] aggiornare `docs/current-state.md`, `docs/roadmap.md` e la documentazione model management;
- [x] ottenere CI verde e review prima di rimuovere il draft;
- [x] eseguire squash merge verso `dev`.

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

- [x] chiudere la PR #34 come superseded dalla PR #53;
- [x] aggiungere una nota con il mapping delle funzionalità recuperate;
- [x] confermare che non restino commit unici necessari;
- [ ] eliminare il branch remoto legacy quando l'audit è completo;
- [x] non riaprire o rebasare la vecchia console come percorso prodotto.

Criterio di uscita della Fase 2:

- model management integrato e validato su `dev`;
- nessuna implementazione concorrente aperta;
- `docs/current-state.md` indica il blocco come completato su `dev`, non ancora rilasciato su `main`.

Evidenza della Fase 2: la PR #53 è stata unita in `dev` con commit `9451314`; verifica, rimozione protetta, conferma, cleanup metadata e risultati privacy-safe sono integrati. La PR legacy #34 è stata chiusa come superseded. Resta soltanto la cancellazione del branch remoto legacy dopo l'audit amministrativo.

## Fase 3 — Brand Android reale

### UX-01 — Asset e identità Android

Branch fresco da `dev`, senza modifiche al runtime.

- [x] definire master vector repository-owned per simbolo, wordmark e lockup;
- [x] convertire il simbolo in Android VectorDrawable;
- [x] creare adaptive launcher icon foreground/background;
- [x] creare monochrome/themed icon per Android 13+;
- [x] aggiungere le risorse `mipmap-anydpi-v26` e fallback necessari;
- [x] collegare `android:icon` e `android:roundIcon` nel manifest;
- [x] validare safe zone e leggibilità alle dimensioni previste;
- [x] mantenere i PNG documentali come reference, non come unica sorgente runtime;
- [x] rendere la generazione riproducibile e verificata dalla CI;
- [x] verificare che l'AAB contenga gli asset corretti.

### UX-02 — Design system e tema

- [x] separare colori, tipografia, shape, spacing e componenti nei file previsti dal piano;
- [x] rimuovere colori e stili locali duplicati dalle schermate;
- [x] completare dark, light e system theme;
- [x] decidere e documentare la policy font offline per Inter e JetBrains Mono;
- [x] introdurre componenti condivisi per app bar, navigation, card, status, metriche, empty/error/loading state e dialoghi;
- [x] aggiungere preview e test dei componenti;
- [x] verificare WCAG AA e touch target di almeno 48 dp;
- [x] aggiornare le brand guidelines se l'implementazione richiede una variazione approvata.

Criterio di uscita della Fase 3:

- il brand è visibile nel launcher e nell'app;
- i token sono centralizzati;
- nessun valore illustrativo viene presentato come dato reale;
- packaging, screenshot preliminari e accessibilità di base sono verdi.

Evidenza della Fase 3: UX-01 è stata unita con PR #60 e commit `c9d7a2c`, includendo master SVG, launcher adaptive/monochrome, manifest e verifica riproducibile degli asset nel packaging. UX-02 è stata unita con PR #61 e commit `22c4d9c`, includendo tema light/dark/system, token centralizzati, componenti Compose condivisi, policy font offline, preview e test WCAG/touch target. La validazione cumulativa post-merge su `dev` è verde.

## Fase 4 — Nuova architettura UX/UI

Le PR seguenti devono essere verticali e mantenere parità con il comportamento reale già connesso.

### INT-01 — Integrare il candidato UI e tooling già implementato

Stato: **IMPLEMENTATO LOCALMENTE / VALIDAZIONE REMOTA PENDENTE**.

Il candidato locale è stato ribasato sull'attuale `origin/dev` e conserva sia UX-02 sia il
visual matching approvato. Questo stato non equivale ancora a completamento della Fase 4:
l'integrazione deve passare dalla normale review verso `dev` e dalla CI cumulativa.

- [x] ribasare i commit locali sopra `origin/dev` senza merge commit o perdita di storia;
- [x] risolvere l'overlap con UX-02 mantenendo separati token, tema, componenti e test del design system;
- [x] integrare top-level Navigation Compose, cinque superfici e shell compact/expanded;
- [x] mantenere touch target di almeno 48 dp e palette con contrasto verificabile;
- [x] aggiungere il runner emulator e una sorgente di versione esplicita per il phone-test;
- [x] creare una feature branch pubblicabile dal candidato locale;
- [ ] pubblicare la feature branch e aprire una PR verso `dev`;
- [x] eseguire review del diff completo dopo il rebase;
- [x] passare localmente Spotless, Detekt, test, Lint, APK/AAB assembly e packaging verification;
- [ ] ottenere `Repository validation` verde sul commit corrente della PR;
- [x] verificare packaging release, ABI `arm64-v8a`, launcher assets e assenza di GGUF/GGML;
- [ ] rieseguire gli smoke test strumentali compact; nessun emulatore era collegato durante il gate post-rebase;
- [ ] eseguire la CI cumulativa sul commit risultante in `dev`;
- [ ] aggiornare i riferimenti di commit e lo stato dei ledger dopo il merge remoto.

Criterio di uscita: il candidato è integrato tramite PR, `dev` remoto è verde e il working tree
locale non contiene commit di prodotto non pubblicati direttamente sulla branch protetta.

### UX-03 — Shell, Navigation Compose e back stack

Stato: **PARZIALE**. Il candidato contiene `HarnessApp`, un `NavHost` top-level, bottom
navigation compact, navigation rail expanded e le cinque destinazioni primarie. Restano il
confine architetturale del NavHost, le route di dettaglio e la rimozione dello stato di dominio
dall'Activity.

- [x] introdurre `HarnessApp` e un `NavHost` per le route top-level;
- [x] definire Overview, Playground, Models, Diagnostics e Settings come destinazioni top-level;
- [x] mantenere bottom navigation su compact e navigation rail su medium/expanded;
- [x] mantenere Storage Access Framework tramite Activity Result API;
- [x] conservare prompt/output soltanto in memoria di processo durante la navigazione;
- [ ] estrarre un `HarnessNavHost` testabile fuori da `MainActivity`;
- [ ] aggiungere route di dettaglio per modello, run/timeline, build info, storage e validazione fisica;
- [ ] definire back behavior, ritorno da dettaglio e ripristino della destinazione senza salvare dati sensibili;
- [ ] implementare i deep-link interni previsti soltanto verso route prive di side effect;
- [ ] ridurre `MainActivity` a composition root, Activity Result e wiring di alto livello;
- [ ] aggiungere test per top-level navigation, detail route, back stack, rotazione e process recreation;
- [ ] dimostrare che cambiare schermata non avvia load, health, benchmark, resource capture o download.

Criterio di uscita: navigazione e stato UI non dipendono da campi mutabili dell'Activity; ogni
route ha comportamento back deterministico e nessuna transizione esegue lavoro runtime implicito.

### UX-04 — Playground ViewModel/UDF

Stato: **PARZIALE**. Inferenza reale, streaming, cancellazione, cleanup, metriche e warm reuse
sono connessi; lo stato resta però posseduto dall'Activity e dai controller callback-based.

- [ ] introdurre `PlaygroundUiState` immutabile, azioni utente ed effetti one-shot;
- [ ] spostare ownership e coordinamento in `PlaygroundViewModel` con coroutine strutturate;
- [ ] adattare `PhonePlaygroundController` senza duplicare policy di runtime o lifecycle;
- [ ] mantenere prompt/output esclusivamente in memoria di processo e fuori da SavedState, Room e telemetry;
- [ ] aggiungere generation settings sheet riusando i limiti di `PlaygroundRequestOptions`;
- [ ] implementare smart auto-scroll che non sottragga il controllo quando l'utente legge output precedente;
- [ ] coalescere i delta ad alta frequenza mantenendo streaming percepito e output bounded;
- [ ] rappresentare prepare, queued, prefill, decode, cancelling e stato terminale senza gare tra callback;
- [ ] testare complete, failed, cancelled, cleanup failed, doppio tap e richiesta concorrente;
- [ ] verificare warm reuse, model switch sicuro e rilascio dopo cambio schermata/background;
- [ ] aggiungere Compose UI test con fake deterministici, senza richiedere un GGUF nei test repository.

Criterio di uscita: nessuna mutazione Playground risiede nell'Activity, tutte le transizioni
terminali sono deterministiche e privacy/lifecycle restano equivalenti al controller attuale.

### UX-05 — Models ViewModel/UDF e catalogo multi-modello

Stato: **PARZIALE**. Download, installazione, metadata per digest, selezione, verifica e rimozione
protetta esistono. Manca una rappresentazione unica e durevole dell'inventario multi-modello e
dei suoi stati degradati.

- [x] incorporare verifica, conferma e rimozione recuperate dalla PR #53;
- [x] preservare deduplicazione e identità immutabile per SHA-256;
- [x] bloccare la rimozione durante ownership runtime quando l'identità è disponibile;
- [x] evitare path, document URI, download URL e signed URL nella presentazione e nei report;
- [ ] introdurre `ModelsUiState` e `ModelsViewModel` sopra i contratti esistenti;
- [ ] unificare release di catalogo, metadata installati, snapshot del `ModelStore` e selezione attiva;
- [ ] implementare lista e dettaglio con stati downloading, verified, installing, installed, selected e loaded;
- [ ] rendere visibili e recuperabili gli stati orphaned, unavailable, incompatible e failed verification;
- [ ] definire selezione deterministica e `lastUsedAt` senza attivazione runtime implicita;
- [ ] completare reconciliation al bootstrap senza cancellazioni automatiche distruttive;
- [ ] aggiungere storage summary e cleanup mirato con conferma e active-model protection;
- [ ] testare più modelli dopo restart, metadata corrotti, artifact mancante e digest duplicato;
- [ ] validare download, installazione, selezione, verifica e rimozione su hardware fisico.

Criterio di uscita: più modelli sopravvivono al restart, una sola selezione è deterministica e
ogni discrepanza catalogo/store è esplicita senza esporre backing path o applicare side effect.

### UX-06 — Overview

Stato: **PARZIALE**. La composizione visuale e le quick action esistono e non inventano i valori
illustrativi dei mockup; serve completare il modello di stato e la copertura.

- [x] usare soltanto dati reali o `Unavailable`;
- [x] mostrare modello selezionato e stato runtime disponibile;
- [x] collegare quick action come navigazione esplicita;
- [ ] introdurre `OverviewUiState` derivato da sorgenti osservabili e bounded;
- [ ] distinguere modello selected/loaded e runtime cold/warm/busy;
- [ ] mostrare operazione attiva, ultima run reale e timestamp privacy-safe;
- [ ] aggiungere memoria disponibile, PSS, low-memory e thermal pressure senza capture implicita;
- [ ] coprire no-model, loading, ready, busy, thermal warning, low-memory, stale ed error state;
- [ ] verificare che l'apertura di Overview non carichi modelli e non esegua refresh mutanti.

Criterio di uscita: ogni valore è attribuibile a una sorgente reale o marcato indisponibile e la
dashboard rimane completamente osservazionale.

### UX-07 — Diagnostics

Stato: **PARZIALE**. Health, Runs, Resources, Benchmarks, Logs e Validation sono connessi a dati
reali o unavailable; filtri log, timeline e history benchmark sono presenti. Mancano route di
dettaglio, grafici, capability complete e la decisione sulla persistenza della telemetry.

- [x] mantenere filtri, copy privacy-safe e request correlation dei log;
- [x] usare query bounded e timeline cronologiche deterministiche;
- [x] separare refresh osservazionale da health, benchmark e resource capture espliciti;
- [x] presentare history benchmark senza modificare la baseline attiva;
- [ ] introdurre un `DiagnosticsViewModel` o ViewModel per sezione con stato immutabile;
- [ ] completare Health con run-all, azioni mirate, capability state e risultato persistito;
- [ ] spostare Runs e request timeline su route di dettaglio con back behavior;
- [ ] aggiungere resource charts con gap per valori null e riepilogo testuale accessibile;
- [ ] completare benchmark key selection, readiness, confronto, history e stati non azionabili;
- [ ] collegare cache health/repair soltanto quando la capability runtime è realmente disponibile;
- [ ] decidere e documentare in ADR l'uso di Room oppure il limite process-only della telemetry;
- [ ] testare empty, loading, populated, warning, failure, source unavailable e dataset al limite;
- [ ] verificare che navigazione e refresh non avviino health, capture, repair o baseline mutation.

Criterio di uscita: tutte le sezioni sono source-backed, bounded, privacy-safe e testabili senza
Activity; ogni operazione costosa o mutante richiede un'azione utente esplicita.

### UX-08 — Settings e developer tools

Stato: **PARZIALE**. Tema, privacy, storage sintetico, versione app e accesso ai developer tools
sono presenti, ma alcuni valori sono soltanto session-scoped o incompleti.

- [x] rendere selezionabile il tema System/Dark/Light durante la sessione;
- [x] mostrare privacy disclosure per prompt/output on-device;
- [x] fornire accesso a health, logs, resources e physical validation;
- [x] mantenere copy/share limitato a report privacy-safe;
- [x] spostare la validazione fisica fuori dal flusso primario;
- [ ] persistere la preferenza tema senza salvare stato sensibile;
- [ ] allineare la privacy disclosure al permesso Internet e al download catalogo;
- [ ] calcolare storage summary sull'intero store e non solo sul modello selezionato;
- [ ] aggiungere cleanup selettivo/totale con conferma, protezione del modello attivo e risultato esplicito;
- [ ] mostrare app version, version code, commit SHA, ABI, Android e revisione backend;
- [ ] creare route dedicate per build info, storage e developer tools;
- [ ] distinguere controlli di sviluppo dalle funzioni sicure disponibili in release;
- [ ] testare preferenze, conferme distruttive, capability mancanti e report redaction.

Criterio di uscita: le impostazioni descrivono il comportamento effettivo, le azioni distruttive
sono protette e nessuna informazione privata entra in UI condivisibile o telemetry.

Criterio di uscita della Fase 4:

- nessuna schermata chiama direttamente `RuntimeOrchestrator`, `ModelStore`, repository o executor;
- `MainActivity` non possiede stato di dominio delle schermate;
- tutte le funzioni esistenti restano raggiungibili;
- le schermate non introducono lavoro runtime implicito.

## Fase 5 — Hardening UX e release candidate

### UX-09 — Test e qualità

Stato: **PARZIALE**. Sono presenti smoke test strumentali per altezza shell e raggiungibilità
delle destinazioni su emulatore compact; non esiste ancora la matrice di regressione richiesta.

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

Matrice minima da conservare come evidenza:

| Dimensione | Varianti obbligatorie | Evidenza |
| --- | --- | --- |
| Stato | empty, loading, populated, warning, failure, unavailable | Compose test e screenshot |
| Tema | dark, light, system | golden e contrast check |
| Finestra | 360x800, 411x891, medium/tablet, landscape | screenshot e navigation test |
| Testo | font scale 1.0, 1.5, controllo manuale 2.0 | screenshot, clipping audit e TalkBack |
| Interazione | keyboard/IME, scroll, back, rotazione, cancel | instrumentation test |
| Performance | cold start, navigazione, streaming lungo | Macrobenchmark e profiler |
| Privacy | prompt/output/path assenti da telemetry, log e report | unit/instrumentation assertion |

Criterio di uscita: la matrice è automatizzata dove deterministica, i passaggi manuali sono
registrati con device/configurazione e non esistono regressioni bloccanti di accessibilità,
layout o memoria UI.

### REL-01 — Preparare Harness 0.5.0

Stato: **PENDENTE**. Il repository dispone del workflow di firma esterna e del nuovo runner di
build, ma la versione locale `0.4.1` non è ancora una release candidate 0.5.0 e nessun artefatto
del candidato corrente è stato promosso su Play.

- [ ] congelare nuove feature su `dev`;
- [ ] aggiornare `CHANGELOG.md`;
- [ ] impostare `versionName` a `0.5.0-rc.N` durante internal testing e a `0.5.0` soltanto per la promotion finale;
- [ ] assegnare un `versionCode` strettamente superiore a ogni upload Play e registrarlo nel release record;
- [ ] rendere l'incremento ripetibile e sicuro anche quando Gradle o la firma falliscono;
- [ ] verificare che sorgente di versione, output Gradle, release name Play e note concordino;
- [ ] aggiornare note di compatibilità, limitazioni e rollback;
- [ ] eseguire il gate completo da checkout pulito;
- [ ] produrre APK, AAB, AAR e checksum;
- [ ] verificare contenuto AAB, ABI `arm64-v8a`, icone e assenza di GGUF/GGML;
- [ ] creare promotion PR `dev -> main`;
- [ ] rieseguire validazione completa e packaging sul commit promosso;
- [ ] firmare l'AAB con upload key esterna;
- [ ] pubblicare su Google Play Internal Testing;
- [ ] conservare il precedente artefatto noto come buono.

Artefatti obbligatori del candidato:

- AAB firmato con upload key esterna e certificato atteso;
- APK debug per riproduzione locale e AAR dei moduli distribuibili previsti;
- checksum SHA-256 e inventario con commit, versione, ABI e backend revision;
- report di packaging che conferma `arm64-v8a`, ELF AArch64, icone e assenza di modelli;
- changelog, release notes, limitazioni device/model e istruzioni di rollback.

Criterio di uscita: il medesimo commit pulito supera il gate completo, produce artefatti
identificabili e viene caricato sul track Internal Testing senza riutilizzare un `versionCode`.

### REL-02 — Evidenza fisica

Stato: **BLOCCO DI PRODUCTION READINESS**. Emulator e host test sono preflight, non evidenza
fisica. Il gate deve usare l'AAB installato da Google Play e un GGUF supportato reale.

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

Ogni esecuzione deve registrare almeno commit, app version/code, device, Android, ABI, RAM,
model digest, architettura, quantizzazione, esito JNI, TTFT, throughput, PSS e thermal state.
Prompt, output, URI documento, signed URL e backing path restano esclusi. Un singolo device può
chiudere il gate interno iniziale ma non autorizza claim di compatibilità generale.

Criterio di uscita: il lifecycle completo e la cancellazione prefill/decode passano, i cicli
ripetuti non mostrano crescita memoria non bounded e l'archivio privacy-safe è collegato al
release record del commit esatto.

## 5. Ordine delle pull request

| Ordine | PR logica | Base | Scopo | Blocco successivo |
| ---: | --- | --- | --- | --- |
| 1 | OPS-01 Restore repository validation | `main` | hotfix CI e fake telemetry | OPS-02 |
| 2 | OPS-03/04 Introduce protected dev integration | `main` | branch policy, CI, ADR e documenti | REC-01 |
| 3 | REC-01 Recover safe phone model management | `dev` | pulizia e completamento PR #53 | REC-02 |
| 4 | UX-01 Android brand assets | `dev` | launcher e vector asset | UX-02 |
| 5 | UX-02 Complete design system | `dev` | token, tema e componenti | UX-03 |
| 6 | INT-01 Integrate local UI/tooling candidate | `dev` | rebase, design-system reconciliation, CI | UX-03B |
| 7 | UX-03B Navigation details and Activity slimming | `dev` | detail route, back stack, composition root | UX-04 |
| 8 | UX-04 Playground UDF | `dev` | inferenza UI e lifecycle | UX-05 |
| 9 | UX-05 Models UDF | `dev` | multi-model e management | UX-06 |
| 10 | UX-06 Overview | `dev` | dashboard reale | UX-07 |
| 11 | UX-07 Diagnostics | `dev` | health/runs/resources/benchmarks/logs | UX-08 |
| 12 | UX-08 Settings | `dev` | amministrazione e developer tools | UX-09 |
| 13 | UX-09 Hardening | `dev` | UI, screenshot, a11y, performance | REL-01 |
| 14 | REL-01 Promotion Harness 0.5.0 | `main` da `dev` | versione e release candidate | REL-02 |

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
- [x] le feature PR puntano a `dev`;
- [x] le promotion PR sono l'unico percorso ordinario verso `main`;
- [ ] gli hotfix vengono forward-portati;
- [x] PR #53 è integrata e PR #34 è chiusa come superseded;
- [x] nessun workflow auto-modificante o push diretto resta attivo;
- [x] brand launcher e design system sono integrati nell'app;
- [ ] Navigation Compose e ViewModel/UDF sostituiscono lo stato di dominio in `MainActivity`;
- [ ] le principali funzionalità del piano UX/UI sono collegate a dati reali;
- [ ] UI test, screenshot, accessibilità e responsive gate passano;
- [ ] CI e packaging completi passano da checkout pulito;
- [ ] Harness 0.5.0 è promossa da `dev` a `main`;
- [ ] l'AAB firmato è installato tramite Google Play Internal Testing;
- [ ] l'evidenza fisica privacy-safe è registrata;
- [ ] documentazione, roadmap, changelog e release record concordano con il comportamento effettivo.

## 11. Prima azione

Completare **INT-01**: pubblicare il candidato ribasato su una feature branch, aprire la PR verso
`dev`, eseguire il gate completo proporzionato e ottenere la CI cumulativa verde. La PR deve
includere l'allineamento dei ledger e non deve dichiarare conclusa UX-03: top-level navigation e
visual matching sono implementati, mentre detail route, back stack completo, ViewModel/UDF e
Activity slimming restano il successivo blocco UX-03B.

In parallelo, completare l'issue amministrativa #59 applicando il ruleset di protezione a `dev`.
Il residuo non blocca la review di INT-01, ma blocca la promotion Harness 0.5.0 verso `main`.
