#include <stdio.h>
#include <stdlib.h>
#include <unistd.h>
#include <string.h>
#include <dirent.h>
#include <pthread.h>
#include <sys/types.h>
#include <fcntl.h>
#include <errno.h>

#include "profiles_management.h"

static pthread_mutex_t mutex;

static void profile_lock()
{
	static int lock_init = 0;
	if(lock_init == 0) {
		pthread_mutex_init(&mutex, NULL);
		lock_init = 1;
	}
	pthread_mutex_lock(&mutex);
}

static void profile_unlock()
{
	pthread_mutex_unlock(&mutex);
}

int read_dir_file(char *DirPath,char (*NameBuf)[101])
{
	DIR *dir;
	char buf[100];
	char *p;
	struct dirent *dirbuf;
	int i = 0,j = 0;
	if((dir = opendir(DirPath)) != NULL) {
		while((dirbuf = readdir(dir)) != NULL && i < 10) {
			if(dirbuf->d_name[0] != '.') {
				strcpy(buf,dirbuf->d_name);
				p = strtok(buf,".");
				p = strtok(NULL,".");
				if(strcmp(p,"ini") == 0) {
					strcpy(*(NameBuf+j),dirbuf->d_name);
					j++;
				}
				i++;
			}
		}
		closedir(dir);
		return j;
	}
	return -1;
}

/**
 * file data type:
 *	[token1]
 *	parameter1=value1
 *	parameter2=value2
 *	[token2]
 *	parameter1=value1
 *	parameter2=value2
 *
 * input:
 *	@filename:profile name
 *	@token:profile token
 *	@parameter:parameter in token
 *	@value:return value, parameter value
 *
 * output:
 *	value length(char *)
 */
int read_profile(const char *filename, const char *token, const char *parameter, char *value)
{
	int ret = 0;
	int file_length =0;
	FILE *fd = NULL;

	char token_buffer[100] = "";
	char *file_buffer = NULL;
	char *temp_buffer = NULL;
	char *temp_start_p = NULL;
	char *temp_end_p = NULL;
	char *parameter_p = NULL;

	profile_lock();

	/* read file data to data buffer */
	if((fd = fopen(filename,"r")) == NULL)
	{
		printf("fopen error file %s: %s\n", filename,  strerror(errno));
		ret = -1;
		goto read_out;
	}

	if(fseek(fd, 0, SEEK_END)) {
		printf("ERROR:Calculated length of the %s failed\n", filename);
		fclose(fd);
		ret = -1;
		goto read_out;
	}
	file_length = ftell(fd);

	if(fseek(fd, 0, SEEK_SET)) {
		printf("ERROR:Calculated length of the %s failed\n", filename);
		fclose(fd);
		ret = -1;
		goto read_out;
	}

	file_buffer = (char *)malloc(file_length);
	if( file_buffer == NULL ) {
		printf("ERROR: file buffer malloc failed\n");
		fclose(fd);
		ret = -1;
		goto read_out;
	}
	memset(file_buffer, 0x00, file_length);
	fread(file_buffer, 1, file_length, fd);
	fclose(fd);

	/* find the token in file_buffer and copy to temp_buffer*/
	sprintf(token_buffer, "%s]", token);
	temp_start_p = strstr(file_buffer, token);
	if( temp_start_p == NULL ) {
		printf("ERROR: serch the %s token failed\n", token);
		ret = -1;
		goto file_read_out;
	}

	temp_end_p = strchr(temp_start_p, '[');
	if( temp_end_p == NULL ) {
		temp_buffer = (char *)malloc(strlen(temp_start_p)+1);
		if( temp_buffer == NULL ) {
			printf("ERROR: temp_buffer malloc failed %s, %d\n", __func__, __LINE__);
			ret = -1;
			goto file_read_out;
		}
		memset(temp_buffer, 0x00, strlen(temp_start_p)+1);
		strcpy(temp_buffer, temp_start_p);
	} else {
		temp_buffer = (char *)malloc(temp_end_p-temp_start_p+1);
		if( temp_buffer == NULL ) {
			printf("ERROR: temp_buffer malloc failed %s, %d\n", __func__, __LINE__);
			ret = -1;
			goto file_read_out;
		}
		memset(temp_buffer, 0x00, temp_end_p-temp_start_p+1);
		strncpy(temp_buffer, temp_start_p, temp_end_p-temp_start_p);
	}

	/* find the parameter in temp_buffer */
	while(1) {
		/* if find but don't parameter we need, continue search */
		if (parameter_p != NULL)
			parameter_p = strstr(parameter_p+strlen(parameter), parameter);
		else /* first serch */
			parameter_p = strstr(temp_buffer, parameter);

		/* not find */
		if( parameter_p == NULL ) {
			printf("ERROR: no %s this parameter in %s file\n", parameter, filename);
			ret = -1;
			goto temp_read_out;
		}

		/* find but don't we need */
		if( *(parameter_p + strlen(parameter)) != '=' )
			continue;
		else /* already find */
			break;
	}

	/* get the parameter value */
	parameter_p = strchr(parameter_p, '=');
	if( parameter_p == NULL ) {
		printf("ERROR: %s parameter no value\n", parameter);
		ret = -1;
		goto temp_read_out;
	}
	strtok(parameter_p, "\n");

	/* copy the parameter value to return (char *value) */
	strcpy(value, parameter_p+1);
	//printf("%s = %s\n", parameter, value);
	ret = strlen(value);
temp_read_out:
	free(temp_buffer);
file_read_out:
	free(file_buffer);
read_out:

	profile_unlock();
	return ret;
}

/**
 *           temp_start_p                       temp_end_p
 *                |                                  |
 *  -------------------------------------------------------------- file_buffer
 *                |   parameter_p    parameter_end_p |
 *                |       |                  |       |
 *                ------------------------------------             temp_buffer
 *                        |------------------|					   parameter_buffer
 */
int write_profile(char *filename, char *token, char *parameter, char *value)
{
	int ret = 0;
	int file_length =0;
	FILE *fd = NULL;

	char token_buffer[100] = "";
	char *file_buffer = NULL;
	char *temp_buffer = NULL;
	char *temp_start_p = NULL;
	char *temp_end_p = NULL;
	char *parameter_p = NULL;
	char *parameter_end_p = NULL;
	char *parameter_buffer = (char *)malloc(256);

	profile_lock();
	/* check parameter_buffer malloc is successful and memset */
	if( parameter_buffer == NULL ) {
		printf("malloc parameter_buffer error \n");
		ret = -1;
		goto write_out;
	}
	memset(parameter_buffer, 0x00, 256);

	/* read file data to file buffer */
	if((fd = fopen(filename,"r+")) == NULL) {
		printf("fopen error file:%s\n", filename);
		/* if local not this file, create it */
		goto create_new;
	}

	if(fseek(fd, 0, SEEK_END)) {
		printf("ERROR:Calculated length of the %s failed\n", filename);
		fclose(fd);
		ret = -1;
		goto parameter_write_out;
	}
	file_length = ftell(fd);

	if(fseek(fd, 0, SEEK_SET)) {
		printf("ERROR:Calculated length of the %s failed\n", filename);
		fclose(fd);
		ret = -1;
		goto parameter_write_out;
	}

	file_buffer = (char *)malloc(file_length + 1);
	if( file_buffer == NULL ) {
		printf("ERROR: file buffer malloc failed\n");
		fclose(fd);
		ret = -1;
		goto parameter_write_out;
	}
	memset(file_buffer, 0x00, file_length + 1);
	fread(file_buffer, 1, file_length, fd);
	fclose(fd);

	/* find the token in file_buffer, if don't find, create it */
	sprintf(token_buffer, "%s]", token);
	if( file_length < strlen(token_buffer) )
		temp_start_p = NULL;
	else
		temp_start_p = strstr(file_buffer, token_buffer);
	if( temp_start_p == NULL ) {
create_new:
		if((fd = fopen(filename,"a+")) == NULL) {
			printf("fwopen %s error\n", filename);
			ret = -1;
			goto file_write_out;
		}
		sprintf(parameter_buffer, "[%s]\n%s=%s\n", token, parameter, value);
		fwrite(parameter_buffer,1,strlen(parameter_buffer),fd);
		fclose(fd);
		goto file_write_out;
	}

	/* if token already created, copy token data to temp buffer */
	temp_end_p = strchr(temp_start_p, '[');
	if( temp_end_p == NULL ) {
		temp_end_p = file_buffer + strlen(file_buffer);
		temp_buffer = (char *)malloc(strlen(temp_start_p)+1);
		if( temp_buffer == NULL ) {
			printf("ERROR: temp_buffer malloc failed %s, %d\n", __func__, __LINE__);
			ret = -1;
			goto file_write_out;
		}
		memset(temp_buffer, 0x00, strlen(temp_start_p)+1);
		strcpy(temp_buffer, temp_start_p);
	} else {
		temp_buffer = (char *)malloc(temp_end_p-temp_start_p+1);
		if( temp_buffer == NULL ) {
			printf("ERROR: temp_buffer malloc failed %s, %d\n", __func__, __LINE__);
			ret = -1;
			goto file_write_out;
		}
		memset(temp_buffer, 0x00, temp_end_p-temp_start_p+1);
		strncpy(temp_buffer, temp_start_p, temp_end_p-temp_start_p);
	}

	/* find the parameter in temp_buffer */
	while(1) {
		/* if find but don't parameter we need, continue search */
		if (parameter_p != NULL)
			parameter_p = strstr(parameter_p+strlen(parameter), parameter);
		else /* first serch */
			parameter_p = strstr(temp_buffer, parameter);
		/* already find */
		if( (parameter_p != NULL) && (*(parameter_p+strlen(parameter)) == '=')) {
			parameter_end_p = strstr(parameter_p, "\n");
			if( parameter_end_p == NULL ) {
				printf("parameter_end_p is NULL\n");
			}
			if((fd = fopen(filename,"w+")) == NULL) {
				printf("fwopen %s error\n", filename);
				ret = -1;
				goto temp_write_out;
			}
			fwrite(file_buffer,1,(temp_start_p+(parameter_p-temp_buffer)-file_buffer),fd);
			sprintf(parameter_buffer, "%s=%s", parameter, value);
			fwrite(parameter_buffer,1,strlen(parameter_buffer),fd);
			fwrite((char *)(temp_start_p+(parameter_end_p-temp_buffer)),1,
					strlen((char *)(temp_start_p+(parameter_end_p-temp_buffer))),fd);
			fclose(fd);
			break;
		} else if(parameter_p != NULL){ /* find but not we need */
			continue;
		}else { /* not find */
			if((fd = fopen(filename,"w+")) == NULL) {
				printf("fwopen %s error\n", filename);
				ret = -1;
				goto temp_write_out;
			}
			fwrite(file_buffer,1,temp_end_p-file_buffer,fd);
			sprintf(parameter_buffer, "%s=%s\n", parameter, value);
			fwrite(parameter_buffer,1,strlen(parameter_buffer),fd);
			fwrite(temp_end_p,1,strlen(temp_end_p),fd);
			fclose(fd);
			break;
		}
	}

temp_write_out:
	free(temp_buffer);
file_write_out:
	free(file_buffer);
parameter_write_out:
	free(parameter_buffer);
write_out:

	profile_unlock();
	return ret;
}

