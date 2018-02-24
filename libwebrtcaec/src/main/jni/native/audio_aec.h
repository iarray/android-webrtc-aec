#ifndef AUDIO_AEC_H
#define AUDIO_AEC_H
struct aec_process_param {
	char *far;
	char *near;
	char *filter;
	int size;
	int delay_time;
};
#ifdef __cplusplus
extern "C" {
#endif

void *audio_process_aec_create(int sample_rate);

int audio_process_aec_process(void *aec_handel, struct aec_process_param *param,float near_v,float far_v);

int audio_process_aec_free(void * aec_handel);

#ifdef __cplusplus
}

#endif
#endif

