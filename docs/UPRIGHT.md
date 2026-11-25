# Prospettiva automatica (Upright)

Nel pannello **Ritaglio** ci sono due pulsanti accanto a "Raddrizza automaticamente":

- **Verticali auto**: i palazzi fotografati dal basso, che "cadono all'indietro", tornano dritti.
  La foto viene anche livellata attorno alle verticali.
- **Prospettiva auto**: come sopra, e in più squadra le linee orizzontali della facciata principale,
  come se fosse stata ripresa di fronte.

Il risultato è una normale **prospettiva a 4 punti**: dopo la correzione si può toccare
"Modifica 4 punti" e trascinarli per rifinire, oppure "Togli 4 punti" per annullarla. Il ritaglio
si adatta da solo per non lasciare angoli vuoti, mantenendo il formato scelto.

## Come funziona

1. **Linee dritte** (`math/LineSegments.kt`): sull'anteprima ridotta a 1024 px si calcola il gradiente
   (Scharr); i pixel di bordo con direzione simile vengono uniti in regioni, e ogni regione lunga
   e sottile diventa un segmento. È una versione semplificata del metodo LSD (von Gioi et al.),
   scritta da zero dalla descrizione dell'algoritmo.
2. **Punti di fuga** (`model/Upright.kt`): tra i segmenti quasi verticali (entro 35°) ogni coppia
   dei 60 più lunghi propone un punto d'incontro; vince quello verso cui punta la maggior lunghezza
   di linee (entro 1,5°), poi un raffinamento ai minimi quadrati. Lo stesso per le orizzontali in
   modalità completa.
3. **Quattro punti**: i lati sinistro e destro del quadrilatero passano per il punto di fuga
   verticale; sopra e sotto sono perpendicolari alla direzione verticale (Verticali) oppure passano
   per il punto di fuga orizzontale (Completa). Squadrare quel quadrilatero, come fa già la
   prospettiva a 4 punti, rende parallele tutte le linee che convergevano.

Tutto avviene sul telefono, in meno di un secondo, senza modelli di IA né librerie esterne.

## Quando non corregge

- **Poche linee dritte** (paesaggi, ritratti, texture): messaggio "Non trovo abbastanza linee dritte".
- **Linee che convergono troppo** (punto di fuga a meno di 1,6 mezze diagonali dal centro, cioè
  quasi dentro la foto): la correzione stirerebbe troppo l'immagine e quasi sempre le linee sono
  diagonali volute. Messaggio dedicato; si può comunque usare la prospettiva a 4 punti.
- **Linee già dritte** (meno di 0,2° di scarto ai bordi): non cambia nulla.

## Limiti

- Le proporzioni dopo la correzione sono stimate dalla media dei lati, come nella prospettiva a
  4 punti: un palazzo molto scorciato può risultare un po' più basso o più alto del vero.
- Con due facciate d'angolo, "Prospettiva auto" squadra quella con più linee.
- Non c'è ancora la modalità "guidata" di Lightroom (disegnare a mano 2-4 linee): la prospettiva
  a 4 punti copre gran parte dello stesso uso.
