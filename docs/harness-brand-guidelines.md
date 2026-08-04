# Harness — Brand Guidelines

**Brand:** Harness
**Descriptor:** Local AI Console
**Tagline:** Run local. Measure everything.
**Version:** 1.0
**Status:** Brand direction approved for product design and Android implementation

---

## 1. Brand foundation

Harness è una console locale per l’esecuzione, il controllo e il monitoraggio di modelli AI su Android.

Il brand deve trasmettere:

- controllo;
- affidabilità;
- privacy;
- precisione tecnica;
- trasparenza sulle performance;
- modularità;
- maturità di prodotto.

Harness non deve apparire come un esperimento, un’app “hacker” o un’interfaccia generica basata su cliché dell’intelligenza artificiale.

### 1.1 Brand promise

> Eseguire modelli AI localmente, mantenendo controllo, privacy e visibilità sul comportamento del runtime.

### 1.2 Positioning statement

Harness è una Local AI Console per Android che permette di importare, eseguire, misurare e diagnosticare modelli GGUF direttamente sul dispositivo, senza dipendere dal cloud.

### 1.3 Brand attributes

| Attributo | Significato nel prodotto |
| --- | --- |
| Privacy-first | Prompt, output e modello restano sul dispositivo |
| Local | L’inferenza avviene direttamente sul device |
| Technical | Metriche, runtime e diagnostica sono visibili |
| Modular | UI, runtime, backend e integrazioni restano separati |
| Reliable | Stati, errori e limiti vengono rappresentati in modo esplicito |
| Measurable | TTFT, token/s, memoria, temperatura e benchmark sono osservabili |

---

## 2. Naming system

### 2.1 Nome principale

```text
Harness
```

Il nome deve essere sempre scritto con la H maiuscola e il resto in minuscolo.

### 2.2 Descriptor

```text
Local AI Console
```

Il descriptor chiarisce la funzione del prodotto e deve essere usato:

- sotto il wordmark;
- nelle schermate introduttive;
- nei materiali di presentazione;
- nella documentazione prodotto;
- nella scheda Google Play, quando utile.

### 2.3 Tagline

```text
Run local. Measure everything.
```

La tagline sintetizza i due pilastri del prodotto:

1. inferenza locale;
2. osservabilità completa.

Non deve essere usata come titolo di pagina o come call to action.

### 2.4 Nomi da evitare

Non utilizzare come nome prodotto principale:

- Local LLM Phone Test;
- Local LLM Console;
- Android LLM Test;
- AI Runtime Tester;
- Llama Console.

Questi termini possono restare come nomi tecnici interni, moduli, build o strumenti di validazione.

---

## 3. Logo system

## 3.1 Concept

Il logo è una H geometrica composta da due blocchi speculari collegati da un ponte centrale.

Il simbolo rappresenta:

- i due lati dell’harness;
- il runtime al centro;
- il flusso controllato dei token;
- la connessione tra applicazione e modello;
- il bilanciamento tra inferenza e osservabilità.

Il lato sinistro utilizza il viola, il lato destro il teal. Il collegamento centrale può includere punti o segmenti che evocano il passaggio dei token.

## 3.2 Varianti previste

Il sistema deve includere:

- logo completo con wordmark;
- simbolo standalone;
- app icon;
- variante monocromatica chiara;
- variante monocromatica scura;
- Android themed icon;
- lockup orizzontale;
- lockup verticale.

## 3.3 Lockup principale

```text
[simbolo] Harness
          Local AI Console
```

Il descriptor deve avere peso visivo inferiore rispetto al nome.

## 3.4 Area di rispetto

L’area libera attorno al simbolo deve essere almeno pari alla larghezza del ponte centrale della H.

Nessun testo, bordo o elemento grafico deve entrare in questa area.

## 3.5 Dimensioni minime

| Contesto | Dimensione minima consigliata |
| --- | ---: |
| Simbolo digitale | 20 px |
| Wordmark completo | 120 px di larghezza |
| App icon preview | 48 px |
| Icona in top app bar | 24 dp |

Sotto queste dimensioni utilizzare esclusivamente la variante semplificata del simbolo.

## 3.6 Usi non consentiti

Non:

- deformare il simbolo;
- modificare il rapporto tra i due blocchi;
- ruotarlo;
- applicare ombre pesanti;
- aggiungere gradienti non approvati;
- usare colori diversi dal sistema di brand;
- inserire il logo dentro forme non previste;
- usare un cervello, robot o scintilla come sostituto del marchio;
- animare il logo in modo decorativo o continuo.

---

## 4. Color system

Il prodotto è dark-first. Il tema scuro è la rappresentazione primaria del brand, mentre tema chiaro e tema di sistema devono mantenere gli stessi rapporti semantici.

## 4.1 Palette principale

| Token | Hex | Ruolo |
| --- | --- | --- |
| Background | `#0B0F14` | Sfondo principale |
| Surface | `#121821` | Card e pannelli |
| Surface Elevated | `#19212C` | Dialog, menu, sheet |
| Primary | `#7C5CFC` | CTA, selezione, focus |
| Primary Container | `#2A2057` | Stato selezionato, nav attiva |
| Secondary | `#25C2A0` | Locale, privacy, runtime attivo |
| Secondary Container | `#103D36` | Badge e superfici locali/private |
| Text Primary | `#F5F7FA` | Titoli e testo principale |
| Text Secondary | `#98A2B3` | Label e metadati |
| Outline | `#2B3543` | Bordi, separatori, input |
| Success | `#38C172` | Healthy, completed, verified |
| Warning | `#F4B740` | Warning, thermal pressure, warm |
| Error | `#EF5B5B` | Errori, failure, destructive actions |

## 4.2 Gerarchia cromatica

- **Viola:** identità, navigazione, azione primaria, selezione.
- **Teal:** inferenza locale, privacy, runtime connesso, metriche live.
- **Verde:** stato positivo conclusivo.
- **Arancio/giallo:** attenzione o stato intermedio.
- **Rosso:** errore, stop, eliminazione e rischio.

Teal e verde non sono intercambiabili:

- teal indica che qualcosa è locale, attivo o connesso;
- verde indica che un controllo o un’operazione ha avuto esito positivo.

## 4.3 Gradienti

Il gradiente principale può essere usato per:

- logo;
- app icon;
- hero visual;
- CTA primaria ad alta rilevanza.

Gradiente suggerito:

```text
#7C5CFC → #25C2A0
```

Non utilizzare gradienti su:

- testo lungo;
- metriche;
- stati di errore;
- grafici tecnici;
- superfici estese.

## 4.4 Contrasto

Tutti i testi e i componenti interattivi devono rispettare almeno WCAG AA.

Indicazioni:

- testo normale: contrasto minimo 4.5:1;
- testo grande: contrasto minimo 3:1;
- indicatori e bordi informativi: contrasto minimo 3:1;
- non affidarsi esclusivamente al colore per comunicare uno stato.

---

## 5. Typography

## 5.1 Font primario

```text
Inter
```

Usato per:

- titoli;
- navigazione;
- body copy;
- pulsanti;
- label;
- messaggi di stato.

Pesi raccomandati:

- Regular 400;
- Medium 500;
- SemiBold 600;
- Bold 700.

## 5.2 Font tecnico

```text
JetBrains Mono
```

Usato esclusivamente per:

- hash;
- token/s;
- TTFT;
- latenza;
- identificatori;
- log;
- versioni;
- valori tecnici;
- codice e configurazioni.

Non usare JetBrains Mono per paragrafi lunghi o descrizioni generali.

## 5.3 Type scale

| Stile | Font | Peso | Dimensione / line-height | Uso |
| --- | --- | ---: | --- | --- |
| Display | Inter | 700 | 36 / 44 | Hero e onboarding |
| H1 / Page title | Inter | 700 | 32 / 40 | Titolo schermata |
| H2 / Section title | Inter | 600 | 20 / 28 | Titolo sezione |
| H3 / Card title | Inter | 600 | 16 / 24 | Card e modali |
| Body large | Inter | 400 | 16 / 24 | Testo principale |
| Body | Inter | 400 | 14 / 20 | Testo secondario |
| Label | Inter | 500 | 13 / 18 | Label e pulsanti |
| Caption | Inter | 400 | 12 / 16 | Metadati |
| Metric | JetBrains Mono | 500 | 14 / 20 | Metriche tecniche |
| Log | JetBrains Mono | 400 | 12 / 18 | Log e identificatori |

## 5.4 Regole tipografiche

- utilizzare sentence case;
- evitare titoli interamente in maiuscolo;
- mantenere massimo tre livelli gerarchici visibili per schermata;
- usare numeri tabulari per metriche e confronti;
- non usare più di due famiglie tipografiche;
- evitare pesi troppo sottili nel dark mode.

---

## 6. Iconography

## 6.1 Stile

Le icone devono essere:

- lineari;
- geometriche;
- coerenti nello spessore;
- preferibilmente rounded;
- leggibili a 20–24 dp;
- prive di dettagli decorativi inutili.

Material Symbols Rounded è il riferimento consigliato.

## 6.2 Icone principali

| Funzione | Icona consigliata |
| --- | --- |
| Overview | `home` o `grid_view` |
| Playground | `code` o `terminal` |
| Models | `deployed_code` o `view_in_ar` |
| Diagnostics | `monitoring` |
| Settings | `settings` |
| Privacy | `lock` |
| Runtime | `memory` |
| Thermal | `device_thermostat` |
| Logs | `description` |
| Benchmark | `speed` |
| Health | `health_and_safety` |
| Import | `add` o `upload_file` |
| Delete | `delete` |
| Stop | `stop` |

## 6.3 Uso del colore nelle icone

- navigazione selezionata: Primary;
- navigazione non selezionata: Text Secondary;
- local/privacy: Secondary;
- successo: Success;
- warning: Warning;
- errore/destructive: Error.

Le icone decorative non devono essere colorate senza una funzione semantica.

---

## 7. Shape and spacing

## 7.1 Radius

| Componente | Radius |
| --- | ---: |
| Card | 16 dp |
| Input | 12 dp |
| Button | 12 dp |
| Chip | 999 dp |
| Dialog | 20 dp |
| Bottom sheet | 24 dp superiori |
| App icon | conforme ad adaptive icon Android |

## 7.2 Spacing scale

```text
4 / 8 / 12 / 16 / 24 / 32 / 40 / 48
```

Regole:

- padding orizzontale mobile: 16 dp;
- spazio tra sezioni principali: 24–32 dp;
- spazio tra card: 12–16 dp;
- spazio interno card: 16–20 dp;
- touch target minimo: 48 × 48 dp.

## 7.3 Elevation

Usare elevation in modo limitato.

Preferire:

- differenza tra surface;
- bordo sottile;
- contrasto cromatico;
- stato selezionato.

Evitare ombre marcate o glow eccessivo.

---

## 8. UI component guidelines

## 8.1 Top app bar

Deve includere:

- simbolo Harness;
- nome del prodotto o titolo pagina;
- eventuale stato locale/runtime;
- azioni contestuali;
- accesso alle impostazioni.

Il logo non deve competere con il titolo della pagina.

## 8.2 Navigation

### Compact phone

Usare bottom navigation con:

- Overview;
- Playground;
- Models;
- Diagnostics.

Settings resta accessibile dalla top app bar.

### Expanded / tablet

Usare navigation rail con:

- Overview;
- Playground;
- Models;
- Diagnostics;
- Settings.

La voce selezionata usa Primary Container e Primary.

## 8.3 Buttons

### Primary

- background Primary o gradiente approvato;
- testo Text Primary;
- una sola azione primaria evidente per area;
- esempi: `Run locally`, `Import model`, `Run all checks`.

### Secondary

- background trasparente o Surface Elevated;
- bordo Outline o Secondary;
- testo Secondary o Text Primary.

### Destructive

- bordo o background Error;
- testo e icona Error/Text Primary;
- conferma obbligatoria per azioni irreversibili.

### Stop / cancel

`Stop generation` deve usare semantica distruttiva ma distinta da `Delete`.

## 8.4 Cards

Le card devono avere:

- titolo chiaro;
- eventuale stato;
- informazioni essenziali;
- massimo una CTA primaria;
- azioni secondarie nel menu overflow.

Tipi principali:

- Runtime card;
- Model card;
- Metric card;
- Health card;
- Run card;
- Resource card;
- Empty-state card.

## 8.5 Status badges

| Badge | Colore | Significato |
| --- | --- | --- |
| Local | Secondary | Esecuzione o dato locale |
| Healthy | Success | Check superato |
| Warm | Warning | Runtime già caricato |
| Cold | Text Secondary / Outline | Primo caricamento |
| Warning | Warning | Stato degradato o attenzione |
| Failed | Error | Operazione fallita |
| Disconnected | Text Secondary | Capability non disponibile |

Ogni badge deve includere testo e, quando utile, icona o punto di stato.

## 8.6 Telemetry chips

Usare chip compatti per:

- TTFT;
- token/s;
- memoria;
- temperatura;
- cache;
- output tokens;
- load state.

Le metriche devono usare JetBrains Mono e valori reali.

Non mostrare valori di esempio in produzione.

## 8.7 Charts

- teal per memoria e dati locali;
- viola per performance o throughput;
- warning per thermal pressure;
- error per soglie superate;
- assi e label sempre leggibili;
- nessun gradiente decorativo che riduca la precisione;
- gap reali nei dati devono restare gap, non essere interpolati senza indicazione.

---

## 9. Motion

Le animazioni devono comunicare stato, non decorare.

Durate consigliate:

| Tipo | Durata |
| --- | ---: |
| Micro interaction | 100–150 ms |
| Cambio stato | 150–250 ms |
| Navigation transition | 200–300 ms |
| Bottom sheet | 250–350 ms |

Principi:

- rispettare `Remove animations` e preferenze di riduzione movimento;
- non animare continuamente metriche o logo;
- lo streaming può usare un cursore discreto;
- il caricamento modello può usare progress indicator determinato o indeterminato;
- nessun effetto “typing” artificiale oltre allo streaming reale dei token.

---

## 10. Tone of voice

## 10.1 Personalità verbale

La voce di Harness deve essere:

- chiara;
- tecnica;
- calma;
- trasparente;
- diretta;
- non promozionale durante l’uso operativo.

## 10.2 Regole di scrittura

Preferire:

```text
Model ready
Generating locally
Integrity verified
Runtime unavailable
Generation cancelled
```

Evitare:

```text
Amazing! Your AI is ready!
Something went wrong :(
Super-fast local intelligence
Magic is happening
```

## 10.3 Error messages

Un errore deve indicare:

1. cosa è successo;
2. cosa può fare l’utente;
3. eventuale codice tecnico separato.

Esempio:

```text
Model verification failed
The imported file no longer matches its recorded SHA-256 digest.
Import the model again or remove the invalid copy.

Code: MODEL_INTEGRITY_FAILED
```

Non mostrare:

- percorsi privati;
- URI sensibili;
- prompt;
- output;
- stack trace;
- messaggi raw del backend.

## 10.4 CTA language

Usare verbi espliciti:

- Run locally;
- Stop generation;
- Import model;
- Verify integrity;
- Run health checks;
- Copy report;
- Remove model.

Evitare CTA generiche come:

- Continue;
- Proceed;
- Confirm;
- Go;
- Start.

---

## 11. Privacy language

La privacy deve essere visibile ma non invasiva.

Frasi approvate:

```text
Runs entirely on this device
Prompts are not persisted
The GGUF stays in private app storage
No internet connection is required
```

Non usare claim assoluti non verificati come:

```text
100% secure
Impossible to access
Completely anonymous
Zero risk
```

La UI deve distinguere tra:

- locale;
- privato;
- non persistito;
- non inviato in rete.

Questi concetti sono collegati ma non equivalenti.

---

## 12. Accessibility

Ogni componente deve rispettare:

- touch target minimo 48 dp;
- supporto TalkBack;
- content description per icone non decorative;
- ordine di focus coerente;
- Dynamic Type / font scaling;
- landscape e tablet;
- contrasto WCAG AA;
- nessuna informazione affidata solo al colore;
- supporto a riduzione movimento;
- feedback testuale per loading, completion ed errori.

Le metriche devono essere lette in forma comprensibile:

```text
Time to first token: 412 milliseconds
Decode speed: 36.5 tokens per second
```

---

## 13. Screen-specific application

## 13.1 Overview

Deve comunicare immediatamente:

- runtime status;
- modello attivo;
- load state;
- memoria;
- temperatura;
- ultima inferenza;
- azioni rapide.

Il colore dominante resta neutro; viola e teal evidenziano solo azioni e stati.

## 13.2 Playground

È la superficie principale del prodotto.

Deve includere:

- modello selezionato;
- badge local/privacy;
- prompt composer;
- generation settings;
- CTA `Run locally`;
- `Stop generation` durante l’esecuzione;
- output streaming;
- metriche terminali.

Prompt e output devono avere una gerarchia più forte rispetto ai parametri tecnici.

## 13.3 Models

Le card dei modelli devono mostrare:

- nome;
- quantizzazione;
- dimensione;
- verifica;
- ultimo utilizzo;
- stato attivo;
- azione principale.

Hash e dettagli tecnici restano nella detail page.

## 13.4 Diagnostics

Diagnostics usa il linguaggio più tecnico del prodotto.

Sottosezioni:

- Health;
- Runs;
- Resources;
- Benchmarks;
- Logs.

Usare grafici, timeline, badge e metriche senza semplificazioni fuorvianti.

## 13.5 Settings

Le impostazioni devono includere:

- appearance;
- privacy;
- storage;
- build information;
- developer tools;
- physical-device validation.

L’identità di brand può essere mostrata in una card About, senza trasformare Settings in una pagina promozionale.

---

## 14. Brand implementation tokens

Esempio di struttura token Compose:

```kotlin
object HarnessColors {
    val Background = Color(0xFF0B0F14)
    val Surface = Color(0xFF121821)
    val SurfaceElevated = Color(0xFF19212C)
    val Primary = Color(0xFF7C5CFC)
    val PrimaryContainer = Color(0xFF2A2057)
    val Secondary = Color(0xFF25C2A0)
    val SecondaryContainer = Color(0xFF103D36)
    val TextPrimary = Color(0xFFF5F7FA)
    val TextSecondary = Color(0xFF98A2B3)
    val Outline = Color(0xFF2B3543)
    val Success = Color(0xFF38C172)
    val Warning = Color(0xFFF4B740)
    val Error = Color(0xFFEF5B5B)
}
```

Naming consigliato per i componenti:

```text
HarnessTheme
HarnessTopAppBar
HarnessNavigationBar
HarnessNavigationRail
HarnessCard
HarnessMetricCard
HarnessRuntimeCard
HarnessModelCard
HarnessStatusBadge
HarnessTelemetryChip
HarnessPromptComposer
HarnessGenerationSettingsSheet
HarnessHealthRow
HarnessEmptyState
HarnessErrorState
```

---

## 15. Governance

## 15.1 Source of truth

Le fonti ufficiali del brand devono essere:

```text
docs/ux-ui/brand-guidelines.md
docs/ux-ui/design-system.md
docs/ux-ui/assets/
```

I colori, i componenti e la tipografia devono essere implementati tramite token centralizzati e non duplicati nelle singole schermate.

## 15.2 Modifiche al brand

Ogni modifica significativa deve includere:

- motivazione;
- screenshot o mockup;
- aggiornamento dei token;
- verifica accessibilità;
- aggiornamento della documentazione;
- controllo di coerenza su mobile e tablet.

## 15.3 Definition of done

Una schermata è coerente con il brand quando:

- usa i token ufficiali;
- rispetta la gerarchia tipografica;
- usa componenti condivisi;
- comunica stati con colore e testo;
- non usa claim privacy non verificati;
- funziona in dark, light e system mode;
- è accessibile con TalkBack e font scaling;
- non introduce icone, colori o stili locali non documentati;
- mantiene priorità funzionale rispetto alla decorazione.

---

## 16. Brand checklist

Prima del merge verificare:

```text
[ ] Nome Harness scritto correttamente
[ ] Descriptor coerente
[ ] Colori derivati dai token
[ ] Tipografia Inter / JetBrains Mono
[ ] Icone coerenti e accessibili
[ ] Touch target minimo rispettato
[ ] Stati non comunicati solo dal colore
[ ] Privacy claim verificabili
[ ] Nessun valore mock mostrato come reale
[ ] Dark, light e system mode verificati
[ ] Layout mobile e tablet verificati
[ ] Componenti condivisi, non duplicati
[ ] Documentazione aggiornata
```

---

## 17. Reference brand kit

Il brand kit visuale di riferimento include:

- logo e app icon;
- wordmark;
- palette;
- tipografia;
- iconografia;
- UI components;
- esempio mobile;
- esempio tablet/navigation rail.

Immagine di riferimento prevista nel repository:

```text
docs/assets/ux-ui/harness-brand-kit.png
```

Il brand kit è una direzione visuale. Le regole contenute in questo documento hanno priorità quando il mockup presenta dati illustrativi, limitazioni di leggibilità o componenti non coerenti con il comportamento reale dell’app.
