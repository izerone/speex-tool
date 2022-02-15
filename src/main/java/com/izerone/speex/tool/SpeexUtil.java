package com.izerone.speex.tool;

import com.izerone.speex.tool.converter.SpeexConverter;

import java.io.File;
import java.io.IOException;

/**
 * Speex 音频格式工具类
 *
 * @author izerone
 * @date 2022-02-15
 */
public class SpeexUtil {

    private static final SpeexConverter SPEEX_CONVERTER = new SpeexConverter();

    /**
     * 转换 spx 文件为 wav 文件
     *
     * @param spxFilePath     spx 文件路径
     * @param destWavFilePath 目标 wav 文件路径
     */
    public static void spxToWav(String spxFilePath, String destWavFilePath) {
        try {
            SPEEX_CONVERTER.convert2wav(new File(spxFilePath), new File(destWavFilePath));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
