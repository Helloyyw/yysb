package com.crk.bysj.controller;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.crk.bysj.config.ApiResultDto;
import com.crk.bysj.util.EncryptUtil;
import com.crk.bysj.util.HttpUtil;
import com.crk.bysj.util.JsonData;
import com.iflytek.msp.lfasr.LfasrClient;
import com.iflytek.msp.lfasr.model.Message;
import com.iflytek.msp.lfasr.util.SliceIdGenerator;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.security.SignatureException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static com.sun.xml.internal.bind.api.impl.NameConverter.standard;

/**
 * 语音转写功能实现:
 * 该功能需要到官网去申请免费的5个小时转写时间
 */
@Controller
public class YYZXController {

    public static final String LFASR_HOST = "http://raasr.xfyun.cn/api";
    public static final String APPID = "6018d7f6";
    public static final String SECRET_KEY = "943bf78e720ac131b17f54b91f11c89d";
    public static final String PREPARE = "/prepare";
    public static final String UPLOAD = "/upload";
    public static final String MERGE = "/merge";
    public static final String GET_RESULT = "/getResult";
    public static final String GET_PROGRESS = "/getProgress";

    /**
     * 文件分片大小,可根据实际情况调整
     */
    public static final int SLICE_SICE = 10485760;// 10M

    /**
     * 上传语音文件 然后翻译成中文字符
     * @param file
     * @return
     */
    @PostMapping("/yyzx")
    @ResponseBody
    public JsonData upload(@RequestParam("file") MultipartFile file) {

        StringBuilder text = new StringBuilder();
        String resultTest = "";
        if (file.isEmpty()) {
            return JsonData.fail("上传失败，请选择文件");
        }
        String fileName = file.getOriginalFilename();
        File file1 = new File("E:\\interesting\\sushe\\yysb\\src\\main\\resources\\iat\\" + fileName);
        try {
            if (!file1.isFile()) {
                file1.createNewFile();
            }
            file.transferTo(file1);
            Thread.sleep(200);
            resultTest =  gotoZX(file1.getAbsolutePath());
        } catch (IOException e) {
            e.printStackTrace();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        JSONArray jsonObject = JSON.parseArray(resultTest);
        if(jsonObject.size()>0){
            jsonObject.forEach(
                    ss->{
                        JSONObject o = (JSONObject) ss;
                        text.append( o.get("onebest").toString());
            });

        }
        return JsonData.success(text.toString());
    }

    private String gotoZX(String absolutePath) {
        File audio = new File(absolutePath);
        String result = "";
        try (FileInputStream fis = new FileInputStream(audio)) {
            // 预处理
            String taskId = prepare(audio);
            // 分片上传文件
            int len = 0;
            byte[] slice = new byte[SLICE_SICE];
            SliceIdGenerator generator = new SliceIdGenerator();
            while ((len =fis.read(slice)) > 0) {
                // 上传分片
                if (fis.available() == 0) {
                    slice = Arrays.copyOfRange(slice, 0, len);
                }
                uploadSlice(taskId, generator.getNextSliceId(), slice);
            }

            // 合并文件
            merge(taskId);

            // 轮询获取任务结果
            while (true) {
                try {
                    System.out.println("sleep a while Zzz" );
                    Thread.sleep(20000);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                ApiResultDto taskProgress = getProgress(taskId);
                if (taskProgress.getOk() == 0) {
                    if (taskProgress.getErr_no() != 0) {
                        System.out.println("任务失败：" + JSON.toJSONString(taskProgress));
                    }

                    String taskStatus = taskProgress.getData();
                    if (JSON.parseObject(taskStatus).getInteger("status") == 9) {
                        System.out.println("任务完成！");
                        break;
                    }
                    System.out.println("任务处理中：" + taskStatus);
                } else {
                    System.out.println("获取任务进度失败！");
                    JSONArray array = new JSONArray();
                    JSONObject jsonObject = new JSONObject();
                    jsonObject.put("onebest","本次语音视频失败，请检查音频格式或者音频文件是否损坏");
                    array.add(jsonObject);
                    return array.toJSONString();
                }
            }
             result = getResult(taskId);
            // 获取结果
            System.out.println("\r\n\r\n转写结果: " + result);
        } catch (SignatureException e) {
            e.printStackTrace();
        } catch (FileNotFoundException e1) {
            e1.printStackTrace();
        } catch (IOException e1) {
            e1.printStackTrace();
        }
        return result;
    }

    /**
     * 获取每个接口都必须的鉴权参数
     *
     * @return
     * @throws SignatureException
     */
    public static Map<String, String> getBaseAuthParam(String taskId) throws SignatureException {
        Map<String, String> baseParam = new HashMap<String, String>();
        String ts = String.valueOf(System.currentTimeMillis() / 1000L);
        baseParam.put("app_id", APPID);
        baseParam.put("ts", ts);
        baseParam.put("signa", EncryptUtil.HmacSHA1Encrypt(EncryptUtil.MD5(APPID + ts), SECRET_KEY));
        if (taskId != null) {
            baseParam.put("task_id", taskId);
        }

        return baseParam;
    }

    /**
     * 预处理
     *
     * @param audio     需要转写的音频
     * @return
     * @throws SignatureException
     */
    public static String prepare(File audio) throws SignatureException {
        Map<String, String> prepareParam = getBaseAuthParam(null);
        long fileLenth = audio.length();

        prepareParam.put("file_len", fileLenth + "");
        prepareParam.put("file_name", audio.getName());
        prepareParam.put("slice_num", (fileLenth/SLICE_SICE) + (fileLenth % SLICE_SICE == 0 ? 0 : 1) + "");

        /********************TODO 可配置参数********************/
        // 转写类型
//        prepareParam.put("lfasr_type", "0");
        // 开启分词
//        prepareParam.put("has_participle", "true");
        // 说话人分离
//        prepareParam.put("has_seperate", "true");
        // 设置多候选词个数
//        prepareParam.put("max_alternatives", "2");
        /****************************************************/

        String response = HttpUtil.post(LFASR_HOST + PREPARE, prepareParam);
        if (response == null) {
            throw new RuntimeException("预处理接口请求失败！");
        }
        ApiResultDto resultDto = JSON.parseObject(response, ApiResultDto.class);
        String taskId = resultDto.getData();
        if (resultDto.getOk() != 0 || taskId == null) {
            throw new RuntimeException("预处理失败！" + response);
        }

        System.out.println("预处理成功, taskid：" + taskId);
        return taskId;
    }

    /**
     * 分片上传
     *
     * @param taskId        任务id
     * @param slice         分片的byte数组
     * @throws SignatureException
     */
    public static void uploadSlice(String taskId, String sliceId, byte[] slice) throws SignatureException {
        Map<String, String> uploadParam = getBaseAuthParam(taskId);
        uploadParam.put("slice_id", sliceId);

        String response = HttpUtil.postMulti(LFASR_HOST + UPLOAD, uploadParam, slice);
        if (response == null) {
            throw new RuntimeException("分片上传接口请求失败！");
        }
        if (JSON.parseObject(response).getInteger("ok") == 0) {
            System.out.println("分片上传成功, sliceId: " + sliceId + ", sliceLen: " + slice.length);
            return;
        }

        System.out.println("params: " + JSON.toJSONString(uploadParam));
        throw new RuntimeException("分片上传失败！" + response + "|" + taskId);
    }

    /**
     * 文件合并
     *
     * @param taskId        任务id
     * @throws SignatureException
     */
    public static void merge(String taskId) throws SignatureException {
        String response = HttpUtil.post(LFASR_HOST + MERGE, getBaseAuthParam(taskId));
        if (response == null) {
            throw new RuntimeException("文件合并接口请求失败！");
        }
        if (JSON.parseObject(response).getInteger("ok") == 0) {
            System.out.println("文件合并成功, taskId: " + taskId);
            return;
        }

        throw new RuntimeException("文件合并失败！" + response);
    }

    /**
     * 获取任务进度
     *
     * @param taskId        任务id
     * @throws SignatureException
     */
    public static ApiResultDto getProgress(String taskId) throws SignatureException {
        String response = HttpUtil.post(LFASR_HOST + GET_PROGRESS, getBaseAuthParam(taskId));
        if (response == null) {
            throw new RuntimeException("获取任务进度接口请求失败！");
        }

        return JSON.parseObject(response, ApiResultDto.class);
    }

    /**
     * 获取转写结果
     *
     * @param taskId
     * @return
     * @throws SignatureException
     */
    public static String getResult(String taskId) throws SignatureException {
        String responseStr = HttpUtil.post(LFASR_HOST + GET_RESULT, getBaseAuthParam(taskId));
        if (responseStr == null) {
            throw new RuntimeException("获取结果接口请求失败！");
        }
        ApiResultDto response = JSON.parseObject(responseStr, ApiResultDto.class);
        if (response.getOk() != 0) {
            throw new RuntimeException("获取结果失败！" + responseStr);
        }

        return response.getData();
    }
}
