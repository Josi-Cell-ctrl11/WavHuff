# WavHuff — Android / Kotlin

Sélectionner un WAV PCM 16 bits mono/stéréo, le convertir hors ligne en véritable MP3, afficher les tailles réelles, le gain de place, la taille restante et le ratio, puis enregistrer le résultat avec le sélecteur de fichiers Android. Android 8 minimum. Aucun compte, serveur ou droit d'accès général au stockage.

## Huffman et MP3

Huffman seul appliqué aux octets du WAV ne crée **pas** de MP3. L'application utilise deux chemins explicitement distincts :

1. **WAV → MP3 valide** : Kotlin lit les chunks RIFF et transmet le PCM à LAME 3.100 via JNI. LAME réalise l'analyse psychoacoustique, la transformation, la quantification et le codage Huffman normalisé du MP3. La sortie est avec perte. `app/src/main/cpp/lame/libmp3lame/bitstream.c` contient l'écriture Huffman et `takehiro.c` la sélection des tables.
2. **WAV → WHF1** : `Huffman.kt` implémente un arbre à file de priorité, les codes binaires, l'empaquetage des bits et la décompression sans perte. L'archive `.whf` est exportable séparément ; ce n'est pas un fichier audio MP3. Son en-tête stocke les 256 fréquences de symboles (Long big endian), après la signature `WHF1`. La table de fréquences détermine l'arbre de façon stable. Les tests vérifient la restitution exacte, y compris les fichiers vides et à symbole unique. Le décompresseur est disponible dans le code ; l'interface exporte seulement les archives.

Pour un devoir qui exige un **encodeur MP3 entièrement écrit soi-même en Kotlin**, cette version ne remplit pas cette exigence : l'interface, la lecture du WAV, les statistiques et le Huffman autonome sont en Kotlin, l'encodeur MP3 est en C. Le MP3 n'est jamais simulé en changeant l'extension du WAV ou de l'archive.

## Mesures

- Taille WAV : nombre réel d'octets du fichier d'origine, en-têtes compris.
- Taille MP3 : nombre réel d'octets après finalisation et écriture de la trame LAME.
- Gain / taux de réduction (%) = `(1 − taille_sortie / taille_WAV) × 100`.
- Taille restante (%) = `taille_sortie / taille_WAV × 100`.
- Ratio = `taille_WAV / taille_sortie`, affiché sous la forme `x:1`.

Un gain négatif est possible (notamment pour les petits WAV ou les archives Huffman) ; il est affiché tel quel. Débits cibles : 96/128/192 kbit/s. LAME peut rééchantillonner automatiquement les WAV à basse fréquence pour respecter le débit cible MP3. Les WAV float, extensibles, ADPCM, multicanaux et tronqués sont rejetés avec un message.

## Compiler l'APK sur GitHub

Le workflow `.github/workflows/android.yml` se lance à chaque push sur `main`, pull request ou lancement manuel. Ouvrir **Actions → Build Android APK → exécution verte → Artifacts → WavHuff-APK**, extraire le ZIP puis installer `WavHuff-1.0.0.apk`.

L'APK est une version de test signée avec la clé debug du runner. La signature n'est pas persistante entre les runners : pour publier et garantir les mises à jour sans désinstallation, configurer une clé de production privée dans les secrets et un build release. Ne jamais commiter une clé privée dans ce dépôt.

Dans Android Studio : importer ce dossier, sélectionner JDK 17, installer SDK 35 et NDK 27.2.12479018. Le workflow utilise Gradle 8.9 ; localement, installer Gradle 8.9 ou générer un wrapper avec `gradle wrapper --gradle-version 8.9`.

## Vérifications

Le workflow exécute les tests JUnit (lecture RIFF avec chunks supplémentaires et padding, rejet des formats incorrects, statistiques et aller-retour Huffman), compile les trois ABI `arm64-v8a`, `armeabi-v7a`, `x86_64`, et lance Android Lint. `python3 tools/test_native.py` compile les mêmes sources LAME sur Linux et vérifie 54 MP3 avec ffprobe et ffmpeg. Une installation sur un vrai téléphone reste nécessaire pour confirmer le comportement du sélecteur de fichiers et de l'interface.

## Sources et licences

LAME 3.100 : https://lame.sourceforge.io/ ; archive officielle https://downloads.sourceforge.net/project/lame/lame/3.100/lame-3.100.tar.gz . Sources d'encodage redistribuées dans `app/src/main/cpp/lame`, sans modification du moteur ; la configuration portable et le pont JNI sont séparés. LGPL 2 ou ultérieure, voir `app/src/main/cpp/lame/COPYING` et le bouton « À propos et licences ». LAME est lié en bibliothèque partagée distincte. Pour reconstruire/remplacer LAME, modifier ces sources, recompiler le projet puis signer son APK avec sa propre clé.

Interface Kotlin, Huffman, configuration et pont JNI : MIT, voir LICENSE.
