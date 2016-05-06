#include <jni.h>
#include <com_example_imageeditor4_ImageBlendingTask.h>
#include <android/bitmap.h>
#include <android/log.h>
#include <stdio.h>
#include <stdlib.h>
#include <math.h>
#define LOG_TAG "NDKBitmapTest"
//#define LOGD(...) ((void)__android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, __VA_ARGS__))
#define LOGE(...) ((void)__android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__))
#define LOOP_MAX 100
#define EPS 2.2204e-016
#define NUM_NEIGHBOR 4
#define SHIFT 15

int quasi_poisson_solver(AndroidBitmapInfo* info, void* img_src, void* img_dst, void* img_mask, void* img_new, int channel, int iteration) {
	int i, j, loop;
	int count_neighbors = (4 << SHIFT) >> 9;
	int error, sum_f, sum_vpq, fp;
	int width = info->width;
	int height = info->height;
	int line = info->stride;
	int *tmp_img = (int*)malloc(sizeof(int)*width*height);
	int *tmp_src_img = (int *)malloc(sizeof(int)*width*height);

	for (i = 0; i < height; i++) {
		uint8_t* p_dst  = (uint8_t*)img_dst+i*line;
		uint8_t* p_mask = (uint8_t*)img_mask+i*line;
		uint8_t* p_src = (uint8_t*)img_src+i*line;
		for (j = 0; j < width; j++) {
			if (p_mask[channel] == 0) {
				tmp_img[i*width+j] = p_src[channel] << SHIFT;
			} else {
				tmp_img[i*width+j] = p_dst[channel] << SHIFT;
			}
			tmp_src_img[i*width+j] = p_src[channel] << SHIFT;
			p_src += 4;
			p_dst += 4;
			p_mask += 4;
		}
	}

	for (loop = 0; loop < iteration; loop++) {
		for (i = 1; i < height-1; i++) {
			uint8_t* p_src = (uint8_t*)img_src+i*line;
			uint8_t* p_dst  = (uint8_t*)img_dst+i*line;
			uint8_t* p_mask = (uint8_t*)img_mask+i*line;
			int* p_tmp_img = (int*)tmp_img+i*width;
			int* p_tmp_src_img = (int*)tmp_src_img + i * width;

			for (j = 1; j < width-1; j++) {
				p_src += 4;
				p_dst += 4;
				p_mask += 4;
				p_tmp_img ++;
				p_tmp_src_img++;
				if (p_mask[channel] == 0) {
					sum_f = 0;
					sum_vpq = 0;
					sum_f += (p_tmp_img[-1]) + (p_tmp_img[1]) + (p_tmp_img[-width]) + (p_tmp_img[width]);//自分の4近傍画素を加える
					sum_vpq += ((p_tmp_src_img[0])-(p_tmp_src_img[-1]))+((p_tmp_src_img[0])-(p_tmp_src_img[1]))
							 + ((p_tmp_src_img[0])-(p_tmp_src_img[-width]))+((p_tmp_src_img[0])-(p_tmp_src_img[width]));
					fp = ((sum_f + sum_vpq)<<6) / count_neighbors;
					p_tmp_img[0] = fp;
				}
			}
		}
	}

	for (i = 0; i < height; i++) {
		uint8_t* p_dst = (uint8_t*)img_dst+i*line;
		int* p_tmp_img = (int*)tmp_img+i*width;
		for (j = 0; j < width; j++) {
			if ((p_tmp_img[0]>>SHIFT) >= 255) {
				p_dst[channel] = 255;
			} else if ((p_tmp_img[0]>>SHIFT) <= 0) {
				p_dst[channel] = 0;
			} else {
				p_dst[channel] = (uint8_t)(p_tmp_img[0]>>SHIFT);
			}
			p_tmp_img++;
			p_dst += 4;
		}
	}
	free(tmp_img);
	free(tmp_src_img);
	return 1;
}

JNIEXPORT void JNICALL Java_com_example_imageeditor4_ImageBlendingTask_poissonBlending
(JNIEnv *env, jobject thisz, jobject source, jobject destination, jobject mask, jobject newImage, jint iteration)
{
	int ret;
	AndroidBitmapInfo info[4]; // destination,source,maskの順
	void* pixels[4];
	// ビットマップオブジェクトに関する情報を取得
	ret = AndroidBitmap_getInfo(env, source, &info[0]);
	ret = AndroidBitmap_getInfo(env, destination, &info[1]);
	ret = AndroidBitmap_getInfo(env, mask, &info[2]);
	ret = AndroidBitmap_getInfo(env, newImage, &info[3]);
	// ビットマップの画像データをロックして画像データを操作できるようにする
	ret = AndroidBitmap_lockPixels(env, source, &pixels[0]);
	ret = AndroidBitmap_lockPixels(env, destination, &pixels[1]);
	ret = AndroidBitmap_lockPixels(env, mask, &pixels[2]);
	ret = AndroidBitmap_lockPixels(env, newImage, &pixels[3]);
	// 各チャンネルについて、ポアソン合成のソルバーを走らせる
	for (int i = 0; i < 3; i++) {
		LOGE("poisson solver:%d", i);
		quasi_poisson_solver(info, pixels[0], pixels[1], pixels[2], pixels[3], i, iteration);
	}
	// ビットマップの画像データをアンロック
	AndroidBitmap_unlockPixels(env, source);
	AndroidBitmap_unlockPixels(env, destination);
	AndroidBitmap_unlockPixels(env, mask);
	AndroidBitmap_unlockPixels(env, newImage);
	return;
}

