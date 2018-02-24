#include <stdlib.h>
#include <stdio.h>
#include <errno.h>
#include <string.h>
#include "webrtc_aec.h"
#include "audio_aec.h"
#include <android/log.h>
#define and_log(...) __android_log_print(4, "WEBRTC", __VA_ARGS__);

#define AP_AEC_MAGIC (('a'<<24)|('e'<<16)|('c'<<8)|('0'))

struct aec_handle {
	unsigned int magic;
	int sample_rate;
};

void *audio_process_aec_create(int sample_rate)
{
	struct aec_handle *ah;
	ah = (struct aec_handle *)malloc(sizeof(struct aec_handle));
	if (NULL == ah) {
		printf("err: %s,%d-%s\n", __func__, __LINE__, strerror(errno));
		return NULL;
	}
	ingenic_apm_init(sample_rate);
	ah->sample_rate = sample_rate;
	ah->magic = AP_AEC_MAGIC;
	return ah;
}

int audio_process_aec_process(void *aec_handle, struct aec_process_param *param,float near_v,float far_v)
{
	int i = 0;
	short *far_frame = NULL;
	short *near_frame = NULL;
	short *filter_frame = NULL;
	struct aec_handle *ah = (struct aec_handle *)aec_handle;
	if (AP_AEC_MAGIC != ah->magic) {
		printf("err aec handle error %p\n", ah);
		return -1;
	}
	/* aec process 10ms data once */
	int dsize = param->size;
	int aecsize = ah->sample_rate*sizeof(short)/100;
	for (i = 0; i < dsize/aecsize; i++) {
		far_frame = (short *)(param->far+aecsize*i);
		near_frame = (short *)(param->near+aecsize*i);
		filter_frame = (short *)(param->filter+aecsize*i);
		ingenic_apm_set_far_frame(far_frame,far_v);
		ingenic_apm_set_near_frame(near_frame, filter_frame,param->delay_time,near_v);

	}
	return 0;
}

int audio_process_aec_free(void * aec_handle)
{
	struct aec_handle *ah = (struct aec_handle *)aec_handle;
	if (AP_AEC_MAGIC != ah->magic) {
		printf("err aec handle free error %p\n", ah);
		return -1;
	}
	ingenic_apm_destroy();
	free(ah);
	return 0;
}
