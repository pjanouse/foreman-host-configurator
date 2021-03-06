package com.scoheb.foreman.cli;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.scoheb.foreman.cli.exception.ForemanApiException;
import com.scoheb.foreman.cli.model.Host;
import com.scoheb.foreman.cli.model.HostTypeAdapter;
import com.scoheb.foreman.cli.model.Hosts;
import com.scoheb.foreman.cli.model.Parameter;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang.text.StrSubstitutor;
import org.apache.log4j.Logger;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.List;
import java.util.Properties;

/**
 * Created by shebert on 17/01/17.
 */
public abstract class AbstractFileProcessor extends Command {

    @com.beust.jcommander.Parameter(description = "<Files to process>"
            , required = true)
    protected List<String> files;

    @com.beust.jcommander.Parameter(names = "--properties",
            description = "Properties file whose key/value pairs can be used as tokens")
    protected String properties;

    private static Logger LOGGER = Logger.getLogger(AbstractFileProcessor.class);
    private final Properties props = new Properties();

    public AbstractFileProcessor(List<String> files) {
        this.files = files;
    }

    protected AbstractFileProcessor() {
    }

    @Override
    public void run() throws ForemanApiException {
        if (files == null) {
            throw new RuntimeException("No files provided");
        }
        if (properties != null) {
            FileInputStream propFileStr = null;
            try {
                File p = new File(properties);
                propFileStr = new FileInputStream(p);
                props.load(propFileStr);
                LOGGER.info("Loading properties file: " + p.getAbsolutePath());
            } catch (IOException e) {
                LOGGER.warn("Could load properties from " + properties + ". " + e.getMessage());
            } finally {
                if (propFileStr != null) {
                    try {
                        propFileStr.close();
                    } catch (IOException e) {
                        //swallow
                    }
                }
            }
        }
        for (String path: files) {
            File f = new File(path);
            if (!f.exists()) {
                throw new RuntimeException("File " + f.getAbsolutePath() + " does not exist.");
            }
            LOGGER.info("Processing " + f.getAbsolutePath());
            String json;
            try {
                String rawString = FileUtils.readFileToString(f);
                LOGGER.debug("raw file: " + rawString);
                StrSubstitutor sub = new StrSubstitutor(props);
                json = sub.replace(rawString);
                LOGGER.debug("substituted file: " + json);
            } catch (IOException e) {
                throw new RuntimeException("Exception while trying to read File " + f.getAbsolutePath() + " - " + e.getMessage());
            }
            final GsonBuilder gsonBuilder = new GsonBuilder();
            gsonBuilder.registerTypeAdapter(Host.class, new HostTypeAdapter());
            gsonBuilder.setPrettyPrinting();

            final Gson gson = gsonBuilder.create();
            final Hosts hosts = gson.fromJson(json, Hosts.class);
            if (hosts == null || hosts.getHosts() == null || hosts.getHosts().size() == 0) {
                throw new RuntimeException("No Hosts loaded from " + f.getAbsolutePath());
            }
            if (hosts.getParameterValue("RESERVED") == null) {
                hosts.addDefaultParameter(new Parameter("RESERVED", "false"));
            }
            perform(hosts);
        }
    }

    public abstract void perform(Hosts hosts) throws ForemanApiException;

    protected void checkHostAttributes(Host host) {
        if (host.getName() == null || host.getName().equals("")) {
            throw new RuntimeException("host is missing its 'name' attribute");
        }
        if (host.parameters != null) {
            for (Parameter p: host.parameters) {
                if (p.getName() == null || p.getName().equals("")) {
                    throw new RuntimeException("host parameter is missing its 'name' attribute: " + p.toString());
                }
                if (p.getValue() == null || p.getValue().equals("")) {
                    throw new RuntimeException("host parameter is missing its 'value' attribute: " + p.toString());
                }
            }
        }
    }
}
