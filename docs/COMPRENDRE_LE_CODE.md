# Comprendre le code de WavHuff

Le projet utilise Kotlin pour l’écran Android, la lecture du WAV, les calculs et l’algorithme Huffman autonome. L’encodeur MP3 est la bibliothèque C LAME, appelée depuis Kotlin par JNI. Les commentaires ajoutés expliquent notre code ; les sources tierces de LAME conservent leurs commentaires et leur licence d’origine.

## Dans quel ordre lire les fichiers ?

| Ordre | Fichier | Ce qu’il faut comprendre |
| --- | --- | --- |
| 1 | `app/src/main/java/com/josias/wavhuff/MainActivity.kt` | Les boutons, les états de l’écran et l’enchaînement des opérations. |
| 2 | `app/src/main/java/com/josias/wavhuff/WavReader.kt` | La structure du WAV et les formules de compression. |
| 3 | `app/src/main/java/com/josias/wavhuff/Huffman.kt` | Compter les octets, construire l’arbre, coder et décoder. |
| 4 | `app/src/main/java/com/josias/wavhuff/Mp3Encoder.kt` | Lire les échantillons PCM et les envoyer à LAME par blocs. |
| 5 | `app/src/main/cpp/bridge.c` | La passerelle entre Kotlin et les fonctions C de LAME. |
| 6 | `app/src/test/java/com/josias/wavhuff/CoreTest.kt` | Les cas qui vérifient le lecteur WAV, les calculs et Huffman. |

## 1. Le parcours de l’utilisateur

`onCreate` construit l’écran. Le fichier est sélectionné avec `pickFile`. Android renvoie son URI à `onActivityResult`, qui lance `loadInput`. Après validation, le bouton appelle `runConversion` : MP3 d’abord, archive Huffman ensuite. `renderResults` présente les tailles finales. `createDocument` demande où exporter le fichier choisi.

Une URI Android n’est pas forcément un chemin de fichier classique : elle peut désigner un document provenant d’une autre application. `contentResolver.openInputStream(uri)` permet de le lire. Nous le copions dans le cache privé pour pouvoir parcourir ses octets avec `RandomAccessFile`.

Les codes de requête permettent de reconnaître la réponse d’Android :

| Code | Action |
| --- | --- |
| 10 | Choix du WAV source. |
| 20 | Enregistrement du MP3. |
| 21 | Enregistrement de l’archive `.whf`. |

Les variables `input`, `mp3` et `archive` contiennent les fichiers temporaires. `info` contient les propriétés audio. `busy` indique qu’une opération est en cours : on désactive alors les actions qui risqueraient de modifier ces fichiers.

Android possède un thread principal pour dessiner l’écran. Si on y compressait le son, l’interface semblerait bloquée. `worker.execute` lance donc les opérations longues dans un autre thread. `runOnUiThread` revient sur le thread principal pour changer un texte, une carte ou la progression.

## 2. Comment le WAV est lu

WAV est un conteneur RIFF composé de blocs. Le bloc `fmt ` décrit le son, le bloc `data` contient les échantillons. Des blocs supplémentaires peuvent exister : il ne faut donc pas supposer que le son commence toujours à l’octet 44.

`WavReader.read` lit les signatures, les tailles et les propriétés, vérifie leur cohérence, puis retourne `WavInfo` :

| Propriété | Signification |
| --- | --- |
| `sampleRate` | Nombre d’échantillons par seconde et par canal, par exemple 44 100 Hz. |
| `channels` | 1 pour mono, 2 pour stéréo. |
| `dataOffset` | Adresse du premier octet du son dans le fichier. |
| `dataSize` | Nombre d’octets audio à lire. |
| `frames` | Nombre de trames PCM. |
| `duration` | Durée en secondes. |

Dans cette version, un échantillon fait 16 bits, soit 2 octets. Une **trame PCM** contient un échantillon de chaque canal : 2 octets en mono, 4 en stéréo. Elle est différente d’une **trame MP3**, qui est une unité du fichier audio compressé.

Exemple : 176 400 octets de PCM stéréo à 44 100 Hz donnent `176400 / (2 × 2) = 44100` trames PCM, soit une seconde.

Les entiers d’un WAV sont en *little endian* : l’octet de poids faible arrive d’abord. Java lit les entiers de `RandomAccessFile` en sens inverse (*big endian*). `reverseBytes` corrige l’ordre des octets. Les masques `and 65535` et `and 0xffffffffL` retrouvent les valeurs non signées.

`require(condition)` interrompt l’opération si la condition est fausse. Cela permet de refuser un format non pris en charge, une taille incohérente ou un fichier tronqué.

## 3. Le Huffman écrit en Kotlin

Huffman attribue des codes binaires plus courts aux symboles fréquents. Ici, un symbole est un octet, donc une valeur entre 0 et 255. Tous les octets du WAV sont conservés, en-tête inclus : la décompression peut restituer le fichier exact.

L’algorithme suit ces étapes :

1. Lire le fichier et compter chaque valeur dans `freq`.
2. Créer une feuille par symbole présent.
3. Prendre les deux nœuds les moins fréquents dans une file de priorité.
4. Les réunir dans un parent dont le poids est leur somme.
5. Recommencer jusqu’à obtenir une racine.
6. Parcourir l’arbre : gauche ajoute `0`, droite ajoute `1`.
7. Relire le fichier et remplacer chaque octet par son code.
8. Regrouper les bits par huit pour les écrire dans l’archive.

Exemple : pour `A A A A B B C D`, les fréquences sont A=4, B=2, C=1, D=1. Avec la règle de départage du code, les codes obtenus sont A=`0`, B=`10`, C=`110`, D=`111`. Le message occupe 14 bits avant remplissage, contre 64 bits si chaque lettre occupe un octet. Pour un fichier aussi petit, notre en-tête rend néanmoins l’archive plus grosse !

La file `PriorityQueue` trie d’abord par poids, puis par symbole. Ce second critère est indispensable : deux arbres différents pourraient autrement être construits quand plusieurs fréquences sont égales.

`bits shl 1` décale les bits déjà accumulés d’une position vers la gauche. `or` ajoute le nouveau bit. Quand `count` atteint 8, un octet est écrit, puis l’accumulateur repart à zéro. Le dernier octet incomplet est rempli de zéros.

Le format `.whf` est organisé ainsi :

| Zone | Taille | Rôle |
| --- | --- | --- |
| Signature `WHF1` | 4 octets | Identifier notre format. |
| 256 fréquences | 256 × 8 = 2048 octets | Reconstruire l’arbre et connaître la taille d’origine. |
| Codes binaires | Variable | Contenu compressé, avec remplissage final éventuel. |

L’en-tête fait donc 2052 octets. L’archive peut grandir si les octets se compressent mal ou si le fichier est très court. Les statistiques affichent alors un gain négatif.

`decompress` lit les fréquences, reconstruit l’arbre, puis descend dans cet arbre pour chaque bit jusqu’à une feuille. La somme des fréquences indique combien d’octets restituer ; les zéros de remplissage ne sont pas interprétés comme des données supplémentaires.

Le cas d’un seul symbole utilise le code `0`. Le cas vide ne possède aucun arbre ni donnée à décoder. Les tests vérifient ces deux situations.

## 4. Pourquoi un deuxième encodeur pour le MP3 ?

L’archive Huffman précédente **n’est pas un MP3**. Renommer son extension ne la rendrait pas lisible par un lecteur MP3.

Pour obtenir un MP3 valide, LAME réalise plusieurs opérations audio, notamment l’analyse psychoacoustique, la transformation, la quantification puis le codage Huffman défini pour MP3. Cette conversion est avec perte ; la compression `.whf` est sans perte.

`Mp3Encoder.convert` saute les en-têtes WAV, lit les échantillons par blocs de 8192 trames PCM, reconstitue chaque `Short` signé et appelle `encode`. Il écrit seulement le nombre d’octets MP3 indiqué par LAME.

`finish` vide les données que l’encodeur gardait en attente. Le tag LAME final remplace ensuite la trame réservée au début du fichier pour renseigner les lecteurs compatibles. La taille est mesurée après ces opérations.

Les fonctions Kotlin déclarées `external` sont implémentées dans `bridge.c`. Le `handle` est une référence au contexte C de LAME, représentée par un `Long` en Kotlin. `open` crée ce contexte, `close` le libère. Le bloc `finally` garantit la libération même si une erreur survient.

Dans `bridge.c`, les accès aux tableaux Java doivent également être libérés. `JNI_ABORT` signifie que les échantillons PCM n’ont pas à être recopiés vers Java. Le mode `0` recopie les octets MP3 produits, puis libère l’accès natif.

## 5. Les calculs affichés

`CompressionStats` reçoit les tailles réelles des fichiers en octets, en-têtes compris.

| Mesure | Formule |
| --- | --- |
| Gain / taux de réduction | `(1 − sortie / origine) × 100` |
| Taille restante en % | `(sortie / origine) × 100` |
| Ratio | `origine / sortie` |

Si un WAV fait 1 000 000 octets et son MP3 100 000 octets, le gain vaut 90 %, la taille restante 10 % et le ratio 10:1. `toDouble()` permet de conserver les décimales au lieu d’effectuer une division entière.

Une taille en Mio se calcule avec `octets / 1048576.0`. La barre graphique garde une largeur minimale pour rester visible ; ses dimensions ne servent pas au calcul des statistiques.

## 6. Où se trouve le logo ?

`AndroidManifest.xml` désigne `@mipmap/ic_launcher`. Les dossiers `mipmap-mdpi` à `mipmap-xxxhdpi` contiennent les tailles raster adaptées aux différentes densités.

À partir d’Android 8, `mipmap-anydpi-v26/ic_launcher.xml` fournit une icône adaptative : fond vert et image du logo avec une marge. Le lanceur du téléphone peut ainsi appliquer un masque rond ou carré arrondi sans couper l’onde sonore. Les petites icônes internes de l’écran restent dessinées dans `IconView`.

## 7. Les vérifications automatiques

`CoreTest.kt` teste l’aller-retour exact Huffman, les formats WAV valides et incorrects, et les formules. `tools/test_native.py` compile les sources LAME et vérifie 54 MP3 : neuf fréquences × deux nombres de canaux × trois débits. `ffprobe` contrôle les propriétés et `ffmpeg` vérifie la lecture.

Le workflow `.github/workflows/android.yml` installe les outils, exécute les tests, compile l’APK et lance Android Lint. L’APK est disponible dans l’artifact `WavHuff-APK`. Un essai sur téléphone complète ces contrôles pour le lanceur et les sélecteurs de documents.
