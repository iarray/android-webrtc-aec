#include "com_android_aec_util_WebRtcUtil.h"
#include <stdint.h>
#include <time.h>
#include <pthread.h>
#include <android/log.h>
#include <string.h>
#include <audio_aec.h>

#define elog(...) __android_log_print(6, "WEBRTC", __VA_ARGS__);
#define jlog(...) __android_log_print(4, "WEBRTC", __VA_ARGS__);
void* handle = NULL;
static pthread_mutex_t pthread_mutex;

JNIEXPORT jint JNICALL Java_com_android_aec_util_WebRtcUtil_init(JNIEnv *env,
		jclass obj, jlong rate) {
	static int lockinit = 0;
	if (lockinit == 0) {
		pthread_mutex_init(&pthread_mutex, NULL);
		lockinit = 1;
	}
	handle = audio_process_aec_create(rate);
	if (handle) {
		return 0;
	}
	return -1;
}

JNIEXPORT void JNICALL Java_com_android_aec_util_WebRtcUtil_free(JNIEnv * env,
		jclass obj) {
	pthread_mutex_lock(&pthread_mutex);
	if (handle != NULL) {
		audio_process_aec_free(handle);
		handle = NULL;
	}
	pthread_mutex_unlock(&pthread_mutex);
}

JNIEXPORT jint JNICALL Java_com_android_aec_util_WebRtcUtil_bufferFarendAndProcess(
		JNIEnv *env, jclass obj, jbyteArray far, jbyteArray near,
		jbyteArray out, jint length, jint delay, jint skew, jfloat near_v,
		jfloat far_v) {
	pthread_mutex_lock(&pthread_mutex);
	char *farbuff = (char *) (*env)->GetByteArrayElements(env, far, NULL);
	char *nearbuff = (char *) (*env)->GetByteArrayElements(env, near, NULL);
	char *outbuff = (char *) (*env)->GetByteArrayElements(env, out, NULL);
	struct aec_process_param param;
	param.far = farbuff;
	param.near = nearbuff;
	param.filter = outbuff;
	param.size = length;
	param.delay_time = delay;
	if (handle != NULL) {
		int ret = audio_process_aec_process(handle, &param, near_v, far_v);
	}
	(*env)->ReleaseByteArrayElements(env, far, (jbyte *) farbuff, JNI_ABORT);
	(*env)->ReleaseByteArrayElements(env, near, (jbyte *) nearbuff, JNI_ABORT);
	(*env)->ReleaseByteArrayElements(env, out, (jbyte *) outbuff, 0);
	pthread_mutex_unlock(&pthread_mutex);

	return 0;
}
;

/*
 #ifdef __cplusplus
 }
 #endif
 */

