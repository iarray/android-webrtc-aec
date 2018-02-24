#ifndef WEBRTC_AEC_H_
#define WEBRTC_AEC_H_

#ifdef __cplusplus
extern "C" {
#endif

int ingenic_apm_init(int sample_rate);
int ingenic_apm_set_far_frame(short *buf,float far_v);
int ingenic_apm_set_near_frame(short *input,short *output,int delay,float near_v);
void ingenic_apm_destroy();

#ifdef __cplusplus
}
#endif
#endif

