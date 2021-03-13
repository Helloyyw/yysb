package com.crk.bysj.controller;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.crk.bysj.config.DebugLog;
import com.crk.bysj.util.JsonData;
import com.iflytek.cloud.speech.*;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseBody;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;

@Controller
public class YYHCNEWController {

    private static final String APPID = "6018d7f6";

    private static final String USER_WORDS = "{\"userword\":[{\"name\":\"计算机词汇\",\"words\":[\"随机存储器\",\"只读存储器\",\"扩充数据输出\",\"局部总线\",\"压缩光盘\",\"十七寸显示器\"]},{\"name\":\"我的词汇\",\"words\":[\"槐花树老街\",\"王小贰\",\"发炎\",\"公事\"]}]}";

    private static YYHCNEWController mObject;

    private static StringBuffer mResult = new StringBuffer();

    private boolean mIsLoop = true;

    @PostMapping("/yyhc")
    @ResponseBody
    public JsonData yyhc(@RequestBody String param){
        String synthesize ="";
        try {
            JSONObject jsonObject = JSON.parseObject(param);
           String text = jsonObject.get("param").toString();
           String victor = jsonObject.get("victor").toString();
            SpeechUtility.createUtility("appid=" + APPID);
            YYHCNEWController  mObject = new YYHCNEWController();
             synthesize = Synthesize(text, victor);
             Thread.sleep(200);
            //todo语音文件转
        } catch (Exception e) {
            e.printStackTrace();
        }
        return JsonData.success(synthesize);
    }
    /**
     * 合成
     */
    private String Synthesize(String text,String victor) {
        SpeechSynthesizer speechSynthesizer = SpeechSynthesizer
                .createSynthesizer();
        // 设置发音人
        if(victor==null || victor.length()==0){
            victor="xiaoyan";
        }  if(text==null || text.length()==0){
            text="语音合成测试程序";
        }
        speechSynthesizer.setParameter(SpeechConstant.VOICE_NAME, victor);

        //启用合成音频流事件，不需要时，不用设置此参数
        speechSynthesizer.setParameter( SpeechConstant.TTS_BUFFER_EVENT, "1" );
        // 存放音频的文件
        SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMddHHmmssSSS");
        String date = sdf.format(new Date());
        // 设置合成音频保存位置（可自定义保存位置），默认不保存
        speechSynthesizer.synthesizeToUri(text, "./target/classes/static/" + date + ".pcm",
                synthesizeToUriListener);
        return date + ".pcm";
    }
    /**
     * 合成监听器
     */
    SynthesizeToUriListener synthesizeToUriListener = new SynthesizeToUriListener() {

        public void onBufferProgress(int progress) {
            DebugLog.Log("*************合成进度*************" + progress);

        }

        public void onSynthesizeCompleted(String uri, SpeechError error) {
            if (error == null) {
                DebugLog.Log("*************合成成功*************");
                DebugLog.Log("合成音频生成路径：" + uri);
            } else
                DebugLog.Log("*************" + error.getErrorCode()
                        + "*************");

        }


        @Override
        public void onEvent(int eventType, int arg1, int arg2, int arg3, Object obj1, Object obj2) {
            if( SpeechEvent.EVENT_TTS_BUFFER == eventType ){
                DebugLog.Log( "onEvent: type="+eventType
                        +", arg1="+arg1
                        +", arg2="+arg2
                        +", arg3="+arg3
                        +", obj2="+(String)obj2 );
                ArrayList<?> bufs = null;
                if( obj1 instanceof ArrayList<?> ){
                    bufs = (ArrayList<?>) obj1;
                }else{
                    DebugLog.Log( "onEvent error obj1 is not ArrayList !" );
                }//end of if-else instance of ArrayList

                if( null != bufs ){
                    for( final Object obj : bufs ){
                        if( obj instanceof byte[] ){
                            final byte[] buf = (byte[]) obj;
                            DebugLog.Log( "onEvent buf length: "+buf.length );
                        }else{
                            DebugLog.Log( "onEvent error element is not byte[] !" );
                        }
                    }//end of for
                }//end of if bufs not null
            }//end of if tts buffer event
        }

    };


}
