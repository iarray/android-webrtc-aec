#ifndef __PROFILES_MANAGEMENT__
#define __PROFILES_MANAGEMENT__

#ifdef __cplusplus
extern "C" {
#endif

int read_dir_file(char *DirPath, char (*NameBuf)[101]);
int read_profile(const char *filename, const char *token, const char *parameter, char *value);
int write_profile(char *filename, char *token, char *parameter, char *value);


#ifdef __cplusplus
}
#endif


#endif //__PROFILES_MANAGEMENT__
