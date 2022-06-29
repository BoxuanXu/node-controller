package io.metersphere.api.jmeter;

import io.metersphere.api.jmeter.queue.BlockingQueueUtil;
import io.metersphere.api.jmeter.queue.PoolExecBlockingQueueUtil;
import io.metersphere.api.jmeter.utils.CommonBeanFactory;
import io.metersphere.api.jmeter.utils.FileUtils;
import io.metersphere.api.jmeter.utils.FixedCapacityUtils;
import io.metersphere.api.service.JvmService;
import io.metersphere.api.service.ProducerService;
import io.metersphere.cache.JMeterEngineCache;
import io.metersphere.dto.ResultDTO;
import io.metersphere.jmeter.JMeterBase;
import io.metersphere.jmeter.MsExecListener;
import io.metersphere.utils.LoggerUtil;
import io.metersphere.utils.RetryResultUtil;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.jmeter.samplers.SampleResult;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

public class MsApiBackendListener implements MsExecListener {

    private List<SampleResult> queues;

    @Override
    public void setupTest() {
        queues = new LinkedList<>();
    }

    @Override
    public void handleTeardownTest(List<SampleResult> results, ResultDTO dto, Map<String, Object> kafkaConfig) {
        LoggerUtil.info("开始处理单条执行结果报告【" + dto.getReportId() + " 】,资源【 " + dto.getTestId() + " 】");
        if (CollectionUtils.isNotEmpty(results)) {
            RetryResultUtil.clearLoops(results);
            queues.addAll(results);
        }
    }

    @Override
    public void testEnded(ResultDTO dto, Map<String, Object> kafkaConfig) {
        try {
            PoolExecBlockingQueueUtil.offer(dto.getReportId());

            if (dto.isRetryEnable()) {
                LoggerUtil.info("重试结果处理【" + dto.getReportId() + " 】开始");
                RetryResultUtil.mergeRetryResults(queues);
                LoggerUtil.info("重试结果处理【" + dto.getReportId() + " 】结束");
            }
            // 整理执行结果
            JMeterBase.resultFormatting(queues, dto);
            queues.clear();

            LoggerUtil.info("报告【" + dto.getReportId() + " 】执行完成");
            if (StringUtils.isNotEmpty(dto.getReportId())) {
                BlockingQueueUtil.remove(dto.getReportId());
            }
            dto.setConsole(FixedCapacityUtils.getJmeterLogger(dto.getReportId(), dto.getTestId()));
            if (dto.getArbitraryData() == null || dto.getArbitraryData().isEmpty()) {
                dto.setArbitraryData(new HashMap<String, Object>() {{
                    this.put("TEST_END", true);
                }});
            } else {
                dto.getArbitraryData().put("TEST_END", true);
            }
            FileUtils.deleteFile(FileUtils.BODY_FILE_DIR + "/" + dto.getReportId() + "_" + dto.getTestId() + ".jmx");
            // 存储结果
            CommonBeanFactory.getBean(ProducerService.class).send(dto, kafkaConfig);
            LoggerUtil.info(JvmService.jvmInfo().toString());
        } catch (Exception e) {
            LoggerUtil.error(e);
        } finally {
            PoolExecBlockingQueueUtil.offer(dto.getReportId());
            if (JMeterEngineCache.runningEngine.containsKey(dto.getReportId())) {
                JMeterEngineCache.runningEngine.remove(dto.getReportId());
            }
            queues.clear();
        }
    }
}
