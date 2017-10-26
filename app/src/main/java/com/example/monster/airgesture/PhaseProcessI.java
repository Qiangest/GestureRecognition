package com.example.monster.airgesture;

/**
 * Created by bostinshi on 2017/7/27.
 */

public class PhaseProcessI {
    //保存c++类的地址
    public long nativePerson;
    public long nativeSignalProcess;
    //构造函数
    public PhaseProcessI() {
        nativeSignalProcess = createNativeSignalProcess();
    }


    static {
        System.loadLibrary("PhaseProcess");
    }

    public native String getJniString();

    public native long createNativeRangeFinder(int inMaxFramesPerSlice, int inNumFreq, float inStartFreq, float inFreqInterv);

    public native float getDistanceChange(long thizptr, short[] recordData, int size);

    public native float[] getBaseBand(long thizptr, int inNumFreq);

    //public native float[] doProcess(long thizptr, int inNumFreq);
    public native long createNativeSignalProcess();

    public native float  doActionRecognition(long thizptr, float[] recordData, int iLen);
    public native float[]  doActionRecognitionV2(long thizptr, float[] recordData, int iLen, String sPath, String sName);
    public native float doActionRecognitionV3(long thizptr, short[] recordData, int iLen, String sPath, String sName);
    public native float  doInit(long thizptr, String sPath);
}
