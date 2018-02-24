#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <time.h>
#include <sys/time.h>
#include <android/log.h>

#define android_log(...) __android_log_print(6, "WEBRTC", __VA_ARGS__);
//#include "profiles_management.h"
#include "audio_processing.h"
#include "audio_buffer.h"
#include "module_common_types.h"

#include "webrtc_aec.h"
#define CONFIG_FILE 0
#define PROFILE_NAME "jni/webrtc_profile.ini"

using webrtc::AudioFrame;
using webrtc::AudioBuffer;
using webrtc::AudioProcessing;
using webrtc::EchoCancellation;
using webrtc::EchoControlMobile;
using webrtc::GainControl;
using webrtc::HighPassFilter;
using webrtc::LevelEstimator;
using webrtc::NoiseSuppression;
//using webrtc::VoiceDetection;
using webrtc::ProcessingConfig;

#define AEC		/* acoustic echo cancellation */
#define AECM	/* acoustic echo control for mobile ;it is not recommended to use*/
//#define AGC		/* automatic gain control */
#define HP		/* High Pass Filter */
//#define LE		/* Level Estimator */
#define NS		/* noise suppression */
//#define VAD		/* voice activity detection */

AudioProcessing *apm = NULL;
AudioFrame *far_frame = NULL;
AudioFrame *near_frame = NULL;

static float frame_far_v = 1.0;
static float frame_near_v = 1.0;
static int agc_start_volumn = 85;

static int SAMPLE_RATE = 8000;
static int CHANNEL_NUM = 1;
static int SAMPLE_TIME = 10;

#define AGC_STARTUP_MIN_VOLUMN 85
#define AGC_MAX_VOLUMN 255
#if 1
void dump()
{
	int ret;
	int sample_rate_hz_dump = apm->proc_sample_rate_hz();
	int num_input_channels_dump = apm->num_input_channels();
	int num_output_channels_dump = apm->num_output_channels();
	int num_reverse_channels_dump = apm->num_reverse_channels();
	int stream_delay_ms_dump = apm->stream_delay_ms();
	android_log("sample rate : %d\n", sample_rate_hz_dump);
	android_log("num_input_channels : %d\n", num_input_channels_dump);
	android_log("num_output_channels : %d\n", num_output_channels_dump);
	android_log("num_reverse_channels : %d\n", num_reverse_channels_dump);
	android_log("stream_delay_ms : %d\n", stream_delay_ms_dump);
/* AEC */
	ret = apm->echo_cancellation()->is_enabled();
	if(ret) {
		android_log("AEC enable !\n");
		ret = apm->echo_cancellation()->is_drift_compensation_enabled();
		if(ret) {
			android_log("\t\tenable_drift_compensation\n");

			//ret = apm->echo_cancellation()->device_sample_rate_hz();
			//android_log("\t\t\tdevice_sample_rate_hz : %d\n", ret);
			ret = apm->echo_cancellation()->stream_drift_samples();
			android_log("\t\t\tstream_drift_samples : %d\n", ret);
		}

		ret = apm->echo_cancellation()->suppression_level();
		android_log("\t\tsuppression_level : %d\n", ret);

		ret = apm->echo_cancellation()->are_metrics_enabled();
		if(ret) {
			android_log("\t\tenable_metrics\n");

			EchoCancellation::Metrics* metrics = NULL;
			apm->echo_cancellation()->GetMetrics(metrics);//dai yong
			if(metrics)
			  android_log("echo_return_loss: [%d, %d, %d, %d]\n",metrics->echo_return_loss.instant, metrics->echo_return_loss.average,metrics->echo_return_loss.maximum,metrics->echo_return_loss.minimum);
		}

		ret = apm->echo_cancellation()->is_delay_logging_enabled();
		if(ret) {
			android_log("\t\tenable_delay_logging\n");

		//???	apm->echo_cancellation()->GetDelayMetrics();//dai yong
		}

	}
#ifdef AECM
/* AECM */
	ret = apm->echo_control_mobile()->is_enabled();
	if(ret) {
		android_log("AECM enable !\n");

		ret = apm->echo_control_mobile()->routing_mode();
		android_log("\t\trouting_mode : %d\n", ret);
		android_log("\t\tps: 0: kQuietEarpieceOrHeadset 1: kEarpiece 2: kLoudEarpiece 3: kSpeakerphone  4: kLoudSpeakerphone\n");

		ret = apm->echo_control_mobile()->is_comfort_noise_enabled();
		if(ret)
			android_log("\t\tenable_comfort_noise\n");

	//???	apm->echo_control_mobile()->GetEchoPath();
	//???	apm->echo_control_mobile()->echo_path_size_bytes();
	}
#endif
#ifdef AGC
/* AGC */
	ret = apm->gain_control()->is_enabled();
	if(ret == 1) {
		android_log("AGC enabled !\n");

		ret = apm->gain_control()->mode();
		android_log("\t\tmode : %d\n", ret);
		android_log("\t\tps: 0: kAdaptiveAnalog 1: kAdaptiveDigital 2: kFixedDigital\n");

		ret = apm->gain_control()->target_level_dbfs();
		android_log("\t\ttarget_level_dbfs : %d\n", ret);

		ret = apm->gain_control()->compression_gain_db();
		android_log("\t\tcompression_gain_db : %d\n", ret);

		ret = apm->gain_control()->is_limiter_enabled();
		if(ret)
			android_log("\t\tlimiter_enabled\n");
	}
#endif
/* HP */
	ret = apm->high_pass_filter()->is_enabled();
	if(ret)
		android_log("HighPassFilter is enabled\n");

/* LE */
	ret = apm->level_estimator()->is_enabled();
	if(ret) {
		android_log("LevelEstimator is enable\n");
		/* not support */
		/*
		Metrics metrics, reverse_metrics;
		apm->level_estimator()->GetMetrics((Metrics*) &metrics, (Metrics*) &reverse_metrics);
		*/
	}

/* NS */
	ret = apm->noise_suppression()->is_enabled();
	if(ret) {
		android_log("NoiseSuppression is enabled !\n");
		ret = apm->noise_suppression()->level();
		android_log("\t\tNoiseSuppression : %d\n", ret);
	}
#ifdef VAD
/* VAD */
	ret = apm->voice_detection()->is_enabled();
	if(ret) {
		android_log("voice activity detection is enable !\n");

		ret = apm->voice_detection()->likelihood();
		android_log("\t\tlikelihood : %d\n", ret);

		ret = apm->voice_detection()->frame_size_ms();
		android_log("\t\tframe size per ms : %d\n", ret);
	}
#endif
}
#endif

int ingenic_apm_init(int sample_rate) {
	SAMPLE_RATE = sample_rate;
	//int32_t sample_rate_hz = SAMPLE_RATE;
	//int num_capture_input_channels = CHANNEL_NUM;
	//int num_capture_output_channels = CHANNEL_NUM;
	//int num_render_channels = CHANNEL_NUM;

	int input_sample_rate_hz = SAMPLE_RATE;
	int output_sample_rate_hz = SAMPLE_RATE;
	int reverse_sample_rate_hz = SAMPLE_RATE;
	int input_channels = CHANNEL_NUM;
	int output_channels = CHANNEL_NUM;
	int reverse_channels = CHANNEL_NUM;

	//AudioProcessing::ChannelLayout input_layout = AudioProcessing::kMono;
	//AudioProcessing::ChannelLayout output_layout = AudioProcessing::kMono;
	//AudioProcessing::ChannelLayout reverse_layout = AudioProcessing::kMono;

	if(agc_start_volumn > AGC_MAX_VOLUMN) {
	  android_log("AGC start volume must not exceed %u", AGC_MAX_VOLUMN);
	  return -1;
	}
	webrtc::Config config;
	config.Set<webrtc::ExtendedFilter>(new webrtc::ExtendedFilter(true));
	config.Set<webrtc::DelayAgnostic>(new webrtc::DelayAgnostic(false));
	config.Set<webrtc::ExperimentalAgc>(new webrtc::ExperimentalAgc(false,agc_start_volumn));
	config.Set<webrtc::ExperimentalNs>(new webrtc::ExperimentalNs(false));
	//config.Set<webrtc::Beamforming>(new webrtc::Beamforming(false));
	config.Set<webrtc::Intelligibility>(new webrtc::Intelligibility(false));
/*
	if(config.Get<webrtc::DelayAgnostic>().enabled){
		android_log("Unknown Delay\n");
		return -1;
			}
	else
		android_log("Fixed Delay\n");
*/
	apm = AudioProcessing::Create(config);
	if(apm == NULL) {
		android_log("AudioProcessing::Create() error !\n");
		return -1;
	}

	webrtc::ProcessingConfig pconfig = {{
		webrtc::StreamConfig(input_sample_rate_hz, input_channels, false), /* input stream */
		webrtc::StreamConfig(output_sample_rate_hz, output_channels, false), /* output stream */
		webrtc::StreamConfig(reverse_sample_rate_hz, reverse_channels, false), /* reverse input stream */
		webrtc::StreamConfig(reverse_sample_rate_hz, reverse_channels, false), /* reverse output stream */
	}};
	if (apm->Initialize(pconfig) != webrtc::AudioProcessing::kNoError) {
		android_log("Error initialising audio processing module\n");
		return -1;
	}

	//apm->set_stream_key_pressed(true);
	//apm->set_delay_offset_ms(0);
/*HP*/
#ifdef HP
	apm->high_pass_filter()->Enable(true);
#endif

	apm->echo_cancellation()->Enable(true);
	apm->echo_cancellation()->enable_drift_compensation(false);
	apm->echo_cancellation()->set_suppression_level(EchoCancellation::kHighSuppression);
	//apm->echo_cancellation()->set_suppression_level(EchoCancellation::kModerateSuppression);
	//apm->echo_cancellation()->set_suppression_level(EchoCancellation::kHighSuppression);
	apm->echo_cancellation()->enable_metrics(true);
	apm->echo_cancellation()->enable_delay_logging(true);

#ifdef AECM
	if(!apm->echo_cancellation()->is_enabled()){
	    apm->echo_control_mobile()->Enable(true);

	    apm->echo_control_mobile()->set_routing_mode(EchoControlMobile::kSpeakerphone);

	    apm->echo_control_mobile()->enable_comfort_noise(true);
	}
#endif//AECM
#ifdef NS
/*ns*/
	apm->noise_suppression()->Enable(true);
	//apm->noise_suppression()->set_level(NoiseSuppression::kLow);
	//apm->noise_suppression()->set_level(NoiseSuppression::kModerate);
	//apm->noise_suppression()->set_level(NoiseSuppression::kHigh);
	apm->noise_suppression()->set_level(NoiseSuppression::kModerate);
#endif//NS
#ifdef AGC
/*AGC*/
#if CONFIG_FILE
	if(read_profile(PROFILE_NAME, "AGC","AGC_enable", profile_buf) < 0) {
		apm->gain_control()->Enable(true);
	} else if(!strcmp(profile_buf, "true")) {
		apm->gain_control()->Enable(true);
	} else {
		apm->gain_control()->Enable(false);
	}

	if(read_profile(PROFILE_NAME, "AGC","set_mode", profile_buf) < 0) {
		apm->gain_control()->set_mode(GainControl::kFixedDigital);

	} else if(!strcmp(profile_buf, "kFixedDigital")) {

		apm->gain_control()->set_mode(GainControl::kFixedDigital);
	} else if(!strcmp(profile_buf, "kAdaptiveAnalog")) {

		apm->gain_control()->set_mode(GainControl::kAdaptiveAnalog);
	}else {

		apm->gain_control()->set_mode(GainControl::kAdaptiveDigital);
	}




	if(read_profile(PROFILE_NAME, "AGC","set_target_level_dbfs", profile_buf) < 0) {
		apm->gain_control()->set_target_level_dbfs(3);			//Limited to [0, 31];
	} else {

		apm->gain_control()->set_target_level_dbfs(atoi(profile_buf));			//Limited to [0, 31];3, 20
	}

	if(read_profile(PROFILE_NAME, "AGC","set_compression_gain_db", profile_buf) < 0) {
		apm->gain_control()->set_compression_gain_db(1);		//Limited to [0, 90];
	} else {

		apm->gain_control()->set_compression_gain_db(atoi(profile_buf));		//Limited to [0, 90];9, 30
	}

	if(read_profile(PROFILE_NAME, "AGC","enable_limiter", profile_buf) < 0) {
		apm->gain_control()->enable_limiter(true);
	} else if(!strcmp(profile_buf, "true")) {
		apm->gain_control()->enable_limiter(true);
	} else {
		apm->gain_control()->enable_limiter(false);
	}
#else
	apm->gain_control()->Enable(true);
	apm->gain_control()->set_mode(GainControl::kFixedDigital);
	apm->gain_control()->set_target_level_dbfs(6);//[0, 31]or negative ???
	apm->gain_control()->set_compression_gain_db(30);//[0,90]
	apm->gain_control()->enable_limiter(false);//When an analog mode is set
	//apm->gain_control()->set_analog_level_limits(10);//[0,255]
	//apm->gain_control()->set_analog_level_limits(300,60000);//[0,65535]
#endif//CONFIG_FILE
#endif//AGC
#ifdef LE
/*LE*/
#if CONFIG_FILE
	if(read_profile(PROFILE_NAME, "LE","LE_enable", profile_buf) < 0) {
		apm->level_estimator()->Enable(true);
	} else if(!strcmp(profile_buf, "true")) {
		apm->level_estimator()->Enable(true);
	} else {
		apm->level_estimator()->Enable(false);
	}
#else
	apm->level_estimator()->Enable(true);
#endif//CONFIG_FILE
#endif//LE
#ifdef VAD
/*vad*/
#if CONFIG_FILE
	if(read_profile(PROFILE_NAME, "VAD","VAD_enable", profile_buf) < 0) {
		apm->voice_detection()->Enable(true);
	} else if(!strcmp(profile_buf, "true")) {
		apm->voice_detection()->Enable(true);
	} else {
		apm->voice_detection()->Enable(false);
	}

	if(read_profile(PROFILE_NAME, "VAD","set_likelihood", profile_buf) < 0) {
		apm->voice_detection()->set_likelihood(VoiceDetection::kLowLikelihood);		//1
	} else if(!strcmp(profile_buf, "kVeryLowLikelihood")) {
		apm->voice_detection()->set_likelihood(VoiceDetection::kVeryLowLikelihood);	//0
	} else if(!strcmp(profile_buf, "kLowLikelihood")) {
		apm->voice_detection()->set_likelihood(VoiceDetection::kLowLikelihood);		//1
	} else if(!strcmp(profile_buf, "kModerateLikelihood")) {
		apm->voice_detection()->set_likelihood(VoiceDetection::kModerateLikelihood);	//2
	} else if(!strcmp(profile_buf, "kHighLikelihood")) {
		apm->voice_detection()->set_likelihood(VoiceDetection::kHighLikelihood);	//3
	}

	if(read_profile(PROFILE_NAME, "VAD","set_frame_size_ms", profile_buf) < 0) {
		apm->voice_detection()->set_frame_size_ms(30);
	} else if(!strcmp(profile_buf, "10")) {
		apm->voice_detection()->set_frame_size_ms(10);
	} else if(!strcmp(profile_buf, "20")) {
		apm->voice_detection()->set_frame_size_ms(20);
	} else if(!strcmp(profile_buf, "30")) {
		apm->voice_detection()->set_frame_size_ms(30);
	}
#else
	apm->voice_detection()->Enable(true);
	//apm->voice_detection()->set_likelihood(VoiceDetection::kVeryLowLikelihood);
	//apm->voice_detection()->set_likelihood(VoiceDetection::kLowLikelihood);
	//apm->voice_detection()->set_likelihood(VoiceDetection::kModerateLikelihood);
	apm->voice_detection()->set_likelihood(VoiceDetection::kModerateLikelihood);
	apm->voice_detection()->set_frame_size_ms(10);
#endif//CONFIG_FILE
#endif//VAD

		frame_far_v = 1.0;
		frame_near_v = 1.0;

	//dump();
	// apm->Initialize(input_sample_rate_hz, output_sample_rate_hz, reverse_sample_rate_hz, input_layout, output_layout, reverse_layout);

	far_frame = new AudioFrame();
	if(far_frame == NULL) {
		android_log("new far AudioFrame error\n");
		return -1;
	}

	near_frame = new AudioFrame();
	if(near_frame == NULL) {
		android_log("new near AudioFrame error\n");
		return -1;
	}
	dump();
/*
	android_log("delay_time = %d\n",delay_time);
	android_log("far_Frame_V = %f\n",frame_far_v);
	android_log("near_Frame_V = %f\n",frame_near_v);
	dump();
*/
	return 0;
}


int ingenic_apm_set_far_frame(short *buf,float far_v) {
	int i, ret;
	far_frame->num_channels_ = CHANNEL_NUM;
	far_frame->sample_rate_hz_ = SAMPLE_RATE;
	far_frame->samples_per_channel_ = far_frame->sample_rate_hz_ * SAMPLE_TIME / 1000;

	for(i=0;i<(int)far_frame->samples_per_channel_;i++) {
		far_frame->data_[i]=(short)((float)buf[i] * far_v);

	}
#ifdef VAD
	//ret = apm->voice_detection()->stream_has_voice();
	//android_log("voice_detection..: %d\n",ret);
#endif
	ret = apm->ProcessReverseStream(far_frame);
	if(ret < 0) {
		android_log("ProcessReverseStream error : %d---\n", ret);
	}

	return 0;
}

int ingenic_apm_set_near_frame(short *input, short *output,int delay,float near_v){
  //android_log("ingenic_apm_set_near_frame..\n");
	int i, ret;
	near_frame->num_channels_ = CHANNEL_NUM;
	near_frame->sample_rate_hz_ = SAMPLE_RATE;
	near_frame->samples_per_channel_ = near_frame->sample_rate_hz_ * SAMPLE_TIME / 1000;

	for(i=0;i<(int)near_frame->samples_per_channel_;i++){
		near_frame->data_[i]=(short)((float)input[i] * near_v);
	}
	//struct timeval currentTime;
	//gettimeofday(&currentTime, NULL);
	//android_log("process time: %lld\n",currentTime.tv_sec*1000 + currentTime.tv_usec/1000);
	apm->set_stream_delay_ms(delay);
	//apm->set_stream_delay_ms(delaytime);

#ifdef VAD
	//apm->voice_detection()->set_stream_has_voice();
	//ret = apm->voice_detection()->stream_has_voice();
	//android_log("voice_detection..: %d\n",ret);
#endif
	ret = apm->ProcessStream(near_frame);
	if(ret < 0) {
		android_log("ProcessStream() error : %d\n", ret);
	}

#if 0//def VAD
	ret = apm->voice_detection()->stream_has_voice();
	//android_log("voice_detection: %d\n",ret);
	if(ret == 0)
	  {
	    vadCount++;
	    //if(vadCount>5)
	      {
		/* there is not human voice */
		for(i = 0; i < (int)near_frame->samples_per_channel_; i++){
		  //near_frame->data_[i] = near_frame->data_[i] / 5;
		  near_frame->data_[i] = near_frame->data_[i];
		  //near_frame->data_[i] = 0;
	      }
	    }
	  }
	else
	  vadCount = 0;
#endif
	memcpy(output, near_frame->data_, near_frame->samples_per_channel_*sizeof(short));
//	for(int i=0;i<10;i++){
//		android_log("webrtc data =%d",*(output+i));
//	}

	return 0;
}
void ingenic_apm_destroy() {
	//dump();
	if(apm != NULL){
		delete apm;//remove this method
		apm = NULL;
	}

	if (far_frame != NULL) {
		delete far_frame;
		far_frame = NULL;
	}

	if (near_frame != NULL) {
		delete near_frame;
		near_frame = NULL;
	}
}

