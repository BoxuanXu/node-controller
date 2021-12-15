package io.metersphere.api.jmeter;

import io.metersphere.api.jmeter.utils.JmeterProperties;
import io.metersphere.api.jmeter.utils.MSException;
import io.metersphere.dto.JmeterRunRequestDTO;
import io.metersphere.jmeter.JMeterBase;
import io.metersphere.jmeter.LocalRunner;
import io.metersphere.utils.LoggerUtil;
import org.apache.jmeter.util.JMeterUtils;
import org.apache.jorphan.collections.HashTree;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;
import java.io.File;

@Service
public class JMeterService {

    @Resource
    private JmeterProperties jmeterProperties;

    @PostConstruct
    public void init() {
        String JMETER_HOME = getJmeterHome();

        String JMETER_PROPERTIES = JMETER_HOME + "/bin/jmeter.properties";
        JMeterUtils.loadJMeterProperties(JMETER_PROPERTIES);
        JMeterUtils.setJMeterHome(JMETER_HOME);
        JMeterUtils.setLocale(LocaleContextHolder.getLocale());

        APISingleResultListener.setConsole();
    }

    public String getJmeterHome() {
        String home = getClass().getResource("/").getPath() + "jmeter";
        try {
            File file = new File(home);
            if (file.exists()) {
                return home;
            } else {
                return jmeterProperties.getHome();
            }
        } catch (Exception e) {
            return jmeterProperties.getHome();
        }
    }

    public void run(JmeterRunRequestDTO runRequest, HashTree testPlan) {
        try {
            init();
            runRequest.setHashTree(testPlan);
            JMeterBase.addSyncListener(runRequest,APISingleResultListener.class.getCanonicalName());
            LocalRunner runner = new LocalRunner(testPlan);
            runner.run(runRequest.getReportId());
        } catch (Exception e) {
            LoggerUtil.error(e.getMessage(), e);
            MSException.throwException("读取脚本失败");
        }
    }
}
