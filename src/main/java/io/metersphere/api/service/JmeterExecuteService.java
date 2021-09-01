package io.metersphere.api.service;

import io.metersphere.api.controller.request.RunRequest;
import io.metersphere.api.jmeter.JMeterService;
import io.metersphere.api.jmeter.utils.FileUtils;
import io.metersphere.api.jmeter.utils.MSException;
import io.metersphere.api.service.utils.ZipSpider;
import io.metersphere.node.util.LogUtil;
import org.apache.commons.lang3.StringUtils;
import org.apache.jmeter.NewDriver;
import org.apache.jmeter.save.SaveService;
import org.apache.jorphan.collections.HashTree;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import javax.annotation.Resource;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class JmeterExecuteService {
    @Resource
    private JMeterService jMeterService;
    @Resource
    private KafkaTemplate<String, Object> kafkaTemplate;

    private static String url = null;
    // 记录所以执行中的请求/场景
    private Map<String, List<String>> runningTasks = new HashMap<>();

    private static InputStream getStrToStream(String sInputString) {
        if (StringUtils.isNotEmpty(sInputString)) {
            try {
                ByteArrayInputStream tInputStringStream = new ByteArrayInputStream(sInputString.getBytes());
                return tInputStringStream;
            } catch (Exception ex) {
                ex.printStackTrace();
                MSException.throwException("生成脚本异常");
            }
        }
        return null;
    }

    private void loadJar(String path) {
        try {
            NewDriver.addPath(path);
        } catch (MalformedURLException e) {
            LogUtil.error(e.getMessage(), e);
            MSException.throwException(e.getMessage());
        }
    }

    public String run(RunRequest request, MultipartFile[] bodyFiles, MultipartFile[] jarFiles) {
        if (request == null || request.getJmx() == null) {
            return "执行文件为空，无法执行！";
        }
        LogUtil.info(request.getJmx());
        // 生成附件/JAR文件
        FileUtils.createFiles(bodyFiles, FileUtils.BODY_FILE_DIR);
        FileUtils.createFiles(jarFiles, FileUtils.JAR_FILE_DIR);
        try {
            this.loadJar(FileUtils.JAR_FILE_DIR);
            // 生成执行脚本
            InputStream inputSource = getStrToStream(request.getJmx());
            Object scriptWrapper = SaveService.loadElement(inputSource);
            HashTree testPlan = JMeterService.getHashTree(scriptWrapper);
            // 开始执行
            jMeterService.run(request, testPlan);
        } catch (Exception e) {
            LogUtil.error(e.getMessage());
            return e.getMessage();
        }
        return "SUCCESS";
    }

    public String runStart(RunRequest runRequest) {
        try {
            // 生成附件/JAR文件
            URL urlObject = new URL(runRequest.getUrl());
            String jarUrl = urlObject.getProtocol() + "://" + urlObject.getHost() + (urlObject.getPort() > 0 ? ":" + urlObject.getPort() : "") + "/api/jmeter/download/jar";

            if (StringUtils.isEmpty(url)) {
                LogUtil.info("开始同步上传的第三方JAR：" + jarUrl);
                File file = ZipSpider.downloadFile(jarUrl, FileUtils.JAR_FILE_DIR);
                if (file != null) {
                    ZipSpider.unzip(file.getPath(), FileUtils.JAR_FILE_DIR);
                    this.loadJar(FileUtils.JAR_FILE_DIR);
                }
            }
            url = jarUrl;
            LogUtil.info("开始拉取脚本和脚本附件：" + runRequest.getUrl());

            File bodyFile = ZipSpider.downloadFile(runRequest.getUrl(), FileUtils.BODY_FILE_DIR);
            if (bodyFile != null) {
                ZipSpider.unzip(bodyFile.getPath(), FileUtils.BODY_FILE_DIR);
                File jmxFile = new File(FileUtils.BODY_FILE_DIR + "/" + runRequest.getTestId() + ".jmx");
                // 生成执行脚本
                HashTree testPlan = SaveService.loadTree(jmxFile);
                // 开始执行
                jMeterService.run(runRequest, testPlan);
                FileUtils.deleteFile(bodyFile.getPath());
            } else {
                MSException.throwException("未找到执行的JMX文件");
            }
        } catch (Exception e) {
            LogUtil.error(e.getMessage());
            return e.getMessage();
        }
        return "SUCCESS";
    }

    public void putRunningTasks(String key, String value) {
        List<String> list = new ArrayList<>();
        if (this.runningTasks.containsKey(key)) {
            list = this.runningTasks.get(key);
        }
        list.add(value);
        this.runningTasks.put(key, list);
    }

    public int getRunningTasks(String key) {
        if (this.runningTasks.containsKey(key)) {
            return this.runningTasks.get(key).size();
        }
        return 0;
    }

    public void remove(String key, String value) {
        if (this.runningTasks.containsKey(key)) {
            this.runningTasks.get(key).remove(value);
        }
    }

    @Scheduled(cron = "0 0/5 * * * ?")
    public void execute() {
        if (StringUtils.isNotEmpty(url)) {
            File file = ZipSpider.downloadFile(url, FileUtils.JAR_FILE_DIR);
            if (file != null) {
                ZipSpider.unzip(file.getPath(), FileUtils.JAR_FILE_DIR);
                this.loadJar(FileUtils.JAR_FILE_DIR);
                FileUtils.deleteFile(file.getPath());
            }
        }
    }
}
