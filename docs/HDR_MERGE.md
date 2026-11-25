# Unione HDR (HDR merge)

Dalla schermata iniziale, il pulsante **HDR** chiede 2-9 scatti della stessa scena fatti con
esposizioni diverse (bracketing, AEB) e li unisce in una sola foto con dettagli sia nelle ombre
sia nelle luci. Il risultato viene salvato come JPEG (qualità 97, con i metadati dello scatto
intermedio e senza GPS se così dicono le impostazioni) nella cartella delle esportazioni e si
apre subito nell'editor, dove si modifica come qualsiasi altra foto.

*From the home screen, the **HDR** button takes 2-9 bracketed shots of one scene and merges them
into one photo, saved as a JPEG next to the exports and opened in the editor.*

## Come funziona

Tutto è codice nostro in `app/src/main/cpp/hdr_merge.cpp` (C++ portabile, senza dipendenze
Android) con il collegamento JNI in `hdr_jni.cpp`. Nessun modello di IA, nessuna libreria
esterna, nessuna connessione.

1. **Riferimento**: lo scatto di luminosità media. Gli altri vengono confrontati con lui.
2. **Allineamento** (opzionale, attivo per default): bitmap a soglia mediana di
   G. Ward, *Fast, Robust Image Registration for Compositing High Dynamic Range Photographs from
   Handheld Exposures* (2003), su una piramide di 7 livelli: trova spostamenti fino a 127 pixel
   (meno sulle foto sotto i 2 MP). Se uno scatto è quasi tutto bruciato o nero, la soglia passa dalla mediana a una
   percentuale di pixel ben esposta in entrambi gli scatti, così si allinea lo stesso. Corregge solo
   gli spostamenti, non le rotazioni. I bordi non comuni a tutti gli scatti vengono ritagliati.
3. **Fusione delle esposizioni**: T. Mertens, J. Kautz, F. Van Reeth, *Exposure Fusion* (2007).
   Ogni pixel di ogni scatto riceve un peso da contrasto locale, saturazione e vicinanza al grigio
   medio; gli scatti vengono uniti in una piramide laplaciana, così le transizioni non lasciano
   aloni. I pesi sono normalizzati livello per livello, il che permette di elaborare uno scatto alla
   volta e tenere in memoria solo le somme.
4. **Rimozione fantasmi** (opzionale, attiva per default): per ogni scatto si stima, con
   l'equalizzazione degli istogrammi, come il riferimento dovrebbe apparire a quell'esposizione.
   Dove lo scatto si discosta troppo (qualcosa si è mosso) il suo peso scende verso zero e resta il
   riferimento. Vale solo dove il riferimento è ben esposto: se lì è bruciato non può sostituire
   nessuno, e un movimento in quella zona può lasciare un fantasma.

I file RAW vengono sviluppati senza luminosità automatica, altrimenti LibRaw schiarirebbe gli
scatti scuri e scurirebbe quelli chiari, annullando il bracketing. Gli scatti JPEG vengono letti
in Display P3 se uno di loro ha un profilo ad ampia gamma o se c'è un RAW, altrimenti in sRGB.

## Memoria e velocità

Il lavoro nativo richiede circa 44 byte per pixel del risultato, più gli scatti decodificati
(4 byte per pixel, 8 per i RAW). Tre JPEG da 12 MP chiedono circa 770 MB; prima di iniziare l'app
controlla la memoria libera e, se non basta, riduce la dimensione (mai sotto 1/4 del lato) e lo
dice. Su un PC con 4 core la fusione di tre scatti da 12 MP richiede circa 2 secondi; su un
telefono ci si aspetta qualche secondo in più.

## Prove

`tools/hdr/hdr_test.cpp` crea tre scatti sintetici di una scena troppo contrastata (ombre,
mezzitoni, luci), due spostati come a mano libera e uno con un oggetto in movimento, e verifica:
scelta del riferimento, spostamenti esatti, ritaglio, recupero dei dettagli in ombre e luci,
riduzione del fantasma, un solo scatto che resta identico, lettura dei RAW in half float e
annullamento. Gira sul PC e nella CI:

```sh
g++ -O2 -std=c++17 -pthread -Iapp/src/main/cpp tools/hdr/hdr_test.cpp app/src/main/cpp/hdr_merge.cpp -o hdr_test && ./hdr_test
```

`HdrPlanTest` verifica sulla JVM i controlli sugli scatti, la stima della memoria, la riduzione
e il nome del file.

## Limiti

- Niente rotazioni o prospettiva tra gli scatti: a mano libera conviene tenere il telefono fermo
  e usare la raffica di bracketing della fotocamera.
- Il risultato è una foto a 8 bit (JPEG), non un file HDR a 32 bit: è la fusione di esposizioni,
  pensata per essere poi rifinita nell'editor.
- Panorami: non ancora.
