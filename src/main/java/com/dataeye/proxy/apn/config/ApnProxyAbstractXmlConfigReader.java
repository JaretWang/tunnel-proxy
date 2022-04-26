package com.dataeye.proxy.apn.config;


import com.dataeye.proxy.utils.MyLogbackRollingFileUtil;
import nu.xom.Builder;
import nu.xom.Document;
import nu.xom.Element;
import nu.xom.ParsingException;
import org.slf4j.Logger;

import java.io.*;

/**
 * @author jaret
 * @date 2022/4/14 10:42
 */
public abstract class ApnProxyAbstractXmlConfigReader {

    private static final Logger logger = MyLogbackRollingFileUtil.getLogger("ApnProxyServer");

    public final void read(InputStream xmlConfigFileInputStream) {
        Document doc = null;
        try {
            Builder parser = new Builder();
            doc = parser.build(xmlConfigFileInputStream);
        } catch (ParsingException ex) {
            logger.error(ex.getMessage(), ex);
        } catch (IOException ex) {
            logger.error(ex.getMessage(), ex);
        }
        if (doc == null) {
            return;
        }
        Element rootElement = doc.getRootElement();

        realReadProcess(rootElement);
    }

    protected abstract void realReadProcess(Element rootElement);

    public final void read(File xmlConfigFile) throws FileNotFoundException {
        if (xmlConfigFile.exists() && xmlConfigFile.isFile()) {
            read(new FileInputStream(xmlConfigFile));
        }
    }
}
