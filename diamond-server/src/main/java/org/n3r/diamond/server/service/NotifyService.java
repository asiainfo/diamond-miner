package org.n3r.diamond.server.service;

import com.google.common.net.HostAndPort;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.n3r.diamond.server.domain.DiamondStone;
import org.n3r.diamond.server.utils.Constants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Enumeration;
import java.util.List;
import java.util.Properties;

@Service
public class NotifyService {
    private Logger log = LoggerFactory.getLogger(NotifyService.class);

    private static final int TIMEOUT = 5000;
    private static final String URL_PREFIX = "/diamond-server/notify.do";
    private static final String PROTOCOL = "http://";

    private Properties nodeProps = new Properties();

    @Autowired
    private DiamondService diamondService;

    @PostConstruct
    public void loadNodes() {
        // 多行ip:port=[specialUrl]
        DiamondStone info = diamondService.findConfigInfo("nameservers", "admin");
        try {
            if (info != null) {
                List<String> lines = IOUtils.readLines(IOUtils.toInputStream(info.getContent()));
                for (String line : lines) {
                    int equalPos = line.indexOf('=');
                    if (equalPos < 0) {
                        nodeProps.put(line.trim(), "");
                    } else {
                        String key = line.substring(0, equalPos);
                        String value = equalPos < line.length() - 1 ? line.substring(equalPos + 1) : "";
                        nodeProps.put(key.trim(), value.trim());
                    }
                }
            }
        } catch (IOException e) {
            log.error("加载节点配置文件失败");
        }

        log.info("节点列表{}", nodeProps);
    }

    public void notifyConfigInfoChange(String dataId, String group) {
        Enumeration<?> enu = nodeProps.propertyNames();
        while (enu.hasMoreElements()) {
            String address = (String) enu.nextElement();
            // if (address.contains(LOCAL_IP)) continue;

            String urlString = generateNotifyConfigInfoPath(dataId, group, address);
            final String result = invokeURL(urlString);
            log.info("通知节点{}分组信息改变：{}", address, result);
        }
    }

    String generateNotifyConfigInfoPath(String dataId, String group, String address) {
        String specialUrl = nodeProps.getProperty(address);

        HostAndPort hostAndPort = HostAndPort.fromString(address);

        String urlString = PROTOCOL + hostAndPort.getHostText() + ":"
                + hostAndPort.getPortOrDefault(Constants.DEF_DIAMOND_NAMESERVER_PORT)
                + URL_PREFIX;
        // 如果有指定url，使用指定的url
        if (StringUtils.isNotBlank(specialUrl)) urlString = specialUrl;

        return urlString + "?method=notifyConfigInfo&dataId=" + dataId + "&group=" + group;
    }

    private String invokeURL(String urlString) {
        HttpURLConnection conn = null;
        try {
            URL url = new URL(urlString);
            conn = (HttpURLConnection) url.openConnection();
            conn.setConnectTimeout(TIMEOUT);
            conn.setReadTimeout(TIMEOUT);
            conn.setRequestMethod("GET");
            conn.connect();
            return IOUtils.toString(conn.getInputStream());
        } catch (Exception e) {
            log.error("http调用失败,url=" + urlString, e.toString());
        } finally {
            if (conn != null) conn.disconnect();
        }
        return "error";
    }

}