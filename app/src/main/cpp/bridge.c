/* Pont JNI : Kotlin appelle ces fonctions C pour utiliser LAME.
 * Le nom Java_com_josias_wavhuff_Mp3Encoder_X correspond au package, à l’objet et à sa méthode.
 * Les tableaux Java sont temporairement exposés en C, puis libérés après chaque appel.
 * Le moteur MP3/Huffman normalisé reste celui de LAME (dossier lame/libmp3lame).
 */
#include <jni.h>
#include <stdint.h>
#include <stdlib.h>
#include "lame.h"

/* Convertir l’entier Long transmis par Kotlin vers le pointeur opaque du contexte LAME. */
static lame_t ptr(jlong h) { return (lame_t)(intptr_t)h; }
/* Créer le contexte, définir fréquence/canaux/débit/qualité, puis initialiser LAME. */
JNIEXPORT jlong JNICALL Java_com_josias_wavhuff_Mp3Encoder_open(JNIEnv *env, jobject self, jint rate, jint channels, jint bitrate, jlong frames) {
    lame_t h = lame_init();
    if (!h) return 0;
    if (lame_set_in_samplerate(h,rate) < 0 || lame_set_num_channels(h,channels) < 0 ||
        lame_set_brate(h,bitrate) < 0 || lame_set_quality(h,2) < 0 ||
        lame_set_num_samples(h,(unsigned long)frames) < 0 ||
        lame_set_bWriteVbrTag(h,1) < 0 || lame_init_params(h) < 0) {
        lame_close(h); return 0;
    }
    return (jlong)(intptr_t)h;
}
/* Encoder un bloc PCM : stéréo entrelacée gauche/droite, ou un seul tableau en mono. */
JNIEXPORT jint JNICALL Java_com_josias_wavhuff_Mp3Encoder_encode(JNIEnv *env, jobject self, jlong handle, jshortArray pcm, jint frames, jbyteArray dest) {
    lame_t h = ptr(handle);
    if (!h || frames < 0 || (*env)->GetArrayLength(env,pcm) < frames * lame_get_num_channels(h)) return -1;
    jshort *samples = (*env)->GetShortArrayElements(env,pcm,NULL);
    if (!samples) return -2;
    jbyte *out = (*env)->GetByteArrayElements(env,dest,NULL);
    if (!out) { (*env)->ReleaseShortArrayElements(env,pcm,samples,JNI_ABORT); return -2; }
    int cap = (*env)->GetArrayLength(env,dest);
    int n = lame_get_num_channels(h)==2 ? lame_encode_buffer_interleaved(h,samples,frames,(unsigned char*)out,cap)
        : lame_encode_buffer(h,samples,samples,frames,(unsigned char*)out,cap);
    /* JNI_ABORT : les données PCM lues ne doivent pas être recopiées vers Java. */
    (*env)->ReleaseShortArrayElements(env,pcm,samples,JNI_ABORT);
    /* Mode 0 : recopier les octets MP3 produits dans le tableau Java puis libérer l’accès. */
    (*env)->ReleaseByteArrayElements(env,dest,out,0);
    return n;
}
/* Vider les trames encore en attente dans l’encodeur. */
JNIEXPORT jint JNICALL Java_com_josias_wavhuff_Mp3Encoder_finish(JNIEnv *env, jobject self, jlong h, jbyteArray dest) {
    jbyte *out = (*env)->GetByteArrayElements(env,dest,NULL);
    if (!out) return -2;
    int n = lame_encode_flush(ptr(h),(unsigned char*)out,(*env)->GetArrayLength(env,dest));
    (*env)->ReleaseByteArrayElements(env,dest,out,0); return n;
}
/* Récupérer la trame LAME finale qui remplacera celle réservée au début du MP3. */
JNIEXPORT jbyteArray JNICALL Java_com_josias_wavhuff_Mp3Encoder_tag(JNIEnv *env,jobject self,jlong h) {
    unsigned char data[16384]; size_t n = lame_get_lametag_frame(ptr(h),data,sizeof(data));
    if (n > sizeof(data)) n = 0;
    jbyteArray out = (*env)->NewByteArray(env,(jsize)n);
    if (out && n) (*env)->SetByteArrayRegion(env,out,0,(jsize)n,(jbyte*)data);
    return out;
}
/* Libérer le contexte natif, même si Kotlin a rencontré une erreur. */
JNIEXPORT void JNICALL Java_com_josias_wavhuff_Mp3Encoder_close(JNIEnv *env,jobject self,jlong h) { if (h) lame_close(ptr(h)); }
