package org.phamsodiep.emacsbasedide.srcanalysis.controller;


import java.util.List;
import javax.validation.Valid;
import javax.servlet.ServletContext;
import java.net.Socket;
import java.io.InputStream;

import com.mongodb.MongoClient;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.beans.factory.annotation.Autowired;

import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Criteria;

import org.phamsodiep.emacsbasedide.srcanalysis.model.SimpleName;
import org.phamsodiep.emacsbasedide.srcanalysis.model.Source;


@RestController
@RequestMapping(value = "/sourcecodebrowser")
public class SourceCodeBrowserController {
  private static final boolean VERBOSE = false;
  private static final int[] EMACS_ACK_FORMAT;
  private static final String APP_CONFIG_VAR = "sourceCodeBrowserConfig";
  private static final String SRC_DB_VAR = "sourceCodeAnalysisDBServer";

  @Autowired
  private ServletContext servletContext;


  @PostMapping("/target")
  public ResponseEntity<String> postTarget(
    @Valid
    @RequestBody
    Target target
  ) {
    if (VERBOSE) {
      System.out.println("Emacs requests update browser target to:");
      System.out.println("\tFile: " + target.getFile());
      System.out.println("\tPosition: " + target.getPosition());
    }

    MongoTemplate dbTemplate = this.getSrcDBMongoTemplate();
    if (dbTemplate == null) {
      return new ResponseEntity<String>(HttpStatus.NOT_FOUND);
    }

    String fileFullPath = target.getFile();
    int fileFullPathLen = fileFullPath.length();
    int startOfMavenPrefix = fileFullPath.indexOf("src/main/java/");
    if (startOfMavenPrefix < 0) {
      return new ResponseEntity<String>(HttpStatus.NOT_FOUND);
    }
    int srcRelativePathStartIdx = startOfMavenPrefix + 14;
    String key = fileFullPath.substring(srcRelativePathStartIdx, fileFullPathLen - 5);
    String srcPathPrefix = fileFullPath.substring(0, srcRelativePathStartIdx);
    Integer targetPos = target.getPosition();

    Query query = Query.query(
      Criteria
        .where("_id").is(key)
        .and("simpleNames.start").lte(targetPos)
        .and("simpleNames.end").gte(targetPos)
    );
    query.fields().exclude("_id");
    query.fields().position("simpleNames", new Integer(1));

    Source src = dbTemplate.findOne(
      query,
      Source.class,
      "src"
    );

    if (src != null ) {
      List<SimpleName> simpleNames = src.getSimpleNames();
      if (simpleNames != null && simpleNames.size() == 1) {
        SimpleName simpleName = simpleNames.get(0);
        Integer line = simpleName.getDefLine();
        Integer col = simpleName.getDefCol();
        String defClassName = simpleName.getDefClassName();
        if (line == null || col == null) {
          return new ResponseEntity<String>(HttpStatus.NOT_FOUND);
        }
        String defFile =
          defClassName == null ?
          fileFullPath :
          srcPathPrefix + defClassName + ".java";
        boolean result = this.requestEmacsClientOpenFile(defFile, line, col + 1);
        if (VERBOSE) {
          System.out.print("Send request to client: ");
          System.out.println(result ? "successfully" : "unsuccessfully");
        }
        new ResponseEntity<String>(HttpStatus.OK);

      }
    }
    else {
      if (VERBOSE) {
        System.out.println("Null Source");
      }
    }

    return new ResponseEntity<String>(HttpStatus.NOT_FOUND);
  }

  @PostMapping("/config")
  public ResponseEntity<String> postConfig(
    @Valid
    @RequestBody
    Configuration config
  ) {
    Configuration previousCfg = this.getConfiguration();
    if (VERBOSE) {
      System.out.println("\nPrevious configuration:");
      System.out.println("\tHost: " + previousCfg.getHost());
      System.out.println("\tPort: " + previousCfg.getPort());
      System.out.println("\tKey:  " + previousCfg.getAuthorizationKey());
      System.out.println("\tDB:   " + previousCfg.getSrcDbConnStr());
    }

    this.setConfiguration(config);
    Configuration readBack = this.getConfiguration();
    if (VERBOSE) {
      System.out.println("Emacs requests configuration update:");
      System.out.println("\tHost: " + readBack.getHost());
      System.out.println("\tPort: " + readBack.getPort());
      System.out.println("\tKey:  " + readBack.getAuthorizationKey());
      System.out.println("\tDB:   " + readBack.getSrcDbConnStr());
    }
    return new ResponseEntity<String>(HttpStatus.OK);
  }

  private boolean checkVariableConfict(
    String variableName,
    Object currentValue,
    Class expectedClass
  ) {
    if (
      (expectedClass != null)
      && (currentValue != null)
      && !(expectedClass.isAssignableFrom(currentValue.getClass()))
    ) {
      StringBuffer sb = new StringBuffer(
        "\nApplication scope variable name '"
      );
      sb.append(variableName == null ? "null/unknown" : variableName);
      sb.append(
        "' conflict is detected. This variable expectation type is class of '"
      );
      sb.append(expectedClass.getName());
      sb.append("', but found '");
      sb.append(currentValue.getClass().getName());
      sb.append("'.");
      System.err.println(sb.toString());
      return true;
    }
    return false;
  }

  private static String[] parseDbConnStr(String dbConnStr) {
    String[] result = null;
    int endHostIdx = dbConnStr.indexOf(':');
    int endHostPortIdx = dbConnStr.indexOf('/');

    if (endHostIdx < 0 || endHostPortIdx < 0) {
      return result;
    }

    result = new String[3];
    result[0] = dbConnStr.substring(0, endHostIdx);
    result[1] = dbConnStr.substring(endHostIdx + 1, endHostPortIdx);
    result[2] = dbConnStr.substring(endHostPortIdx + 1);
    return result;
  }

  private MongoTemplate getSrcDBMongoTemplate() {
    MongoTemplate dBTemplate = null;
    Object obj = servletContext.getAttribute(SRC_DB_VAR);

    if (obj != null) {
      if (!(this.checkVariableConfict(SRC_DB_VAR, obj, MongoTemplate.class))) {
        dBTemplate = (MongoTemplate) obj;
      }
    }
    return dBTemplate;
  }

  private Configuration getConfiguration() {
    Configuration cfg = null;
    Object obj = servletContext.getAttribute(APP_CONFIG_VAR);

    if (obj == null) {
      cfg = new Configuration();
      servletContext.setAttribute(APP_CONFIG_VAR, cfg);
      return cfg;
    }
    if (this.checkVariableConfict(APP_CONFIG_VAR, obj, Configuration.class)) {
      return new Configuration();
    }
    cfg = (Configuration) obj;
    return cfg;
  }

  private void setConfiguration(Configuration config) {
    // Set configuration information
    Object obj = servletContext.getAttribute(APP_CONFIG_VAR);
    if (this.checkVariableConfict(APP_CONFIG_VAR, obj, Configuration.class)) {
      return;
    }
    servletContext.setAttribute(APP_CONFIG_VAR, config);

    // Prepare MongoTemplate for next operation
    String[] params = parseDbConnStr(config.getSrcDbConnStr());
    if (params != null) {
      MongoClient client = new MongoClient(params[0], new Integer(params[1]));
      MongoTemplate dBTemplate = new MongoTemplate(client, params[2]);
      obj = servletContext.getAttribute(SRC_DB_VAR);
      if (this.checkVariableConfict(SRC_DB_VAR, obj, MongoTemplate.class)) {
        return;
      }
      servletContext.setAttribute(SRC_DB_VAR, dBTemplate);
    }
  }

  private boolean requestEmacsClientOpenFile(
    String fileName,
    int line,
    int column
  ) {
    // Information sent to Emacs is as below format:
    // "-auth $authorizationKey -dir $workingDirectory -nowait -nowait
    //  -current-frame -tty /dev/pts/$clientTTY xterm -position +$line:$column
    //  -file $fileName\n"
    //
    // The format could be reduced as below:
    // "-auth $authorizationKey -nowait -nowait -current-frame -position
    //  +$line:$column -file $fileName\n"
    //
    // The reduced version is applied, because some information is not available
    // for server (E.g. TTY dev of server, working directory of server...). It
    // is not available because of security or server OS environment.
    //
    // It is noted that: 
    //   . The ending token "\n" of the format is important to notify Emacs 
    //     executes that response.

    boolean result = true;
    Configuration cfg = this.getConfiguration();

    if (cfg == null || cfg.getHost().length() == 0) {
      return false;
    }
    try {
      Socket sock = new Socket(cfg.getHost(), new Integer(cfg.getPort()));
      StringBuffer sb = new StringBuffer("-auth ");
      sb.append(cfg.getAuthorizationKey());
      sb.append(" -nowait -current-frame");
      sb.append(" -position +");
      sb.append(line);
      sb.append(':');
      sb.append(column);
      sb.append(" -file ");
      sb.append(fileName);
      sb.append("\n");
      sock.getOutputStream().write(sb.toString().getBytes());
      //System.out.println(sb.toString());
      // Read ACK and verify it.
      InputStream is = sock.getInputStream();
      boolean confirm = true;
      // Verify that ACK begins with EMACS_ACK_FORMAT
      for (int expectedCh : EMACS_ACK_FORMAT) {
        if (is.read() != expectedCh) {
          confirm = false;
          break;
        }
      }
      if (confirm) {
        // Verify that ACK ends with a pid (a sequence of digits)
        // and followed by a "\n" or "\r\n" (optional)
        while(is.available() > 0) {
          int ch = is.read();
          if (ch < '0' || ch > '9') {
            result = (ch == '\r' || ch == '\n');
            break;
          }
        }
      }
      else {
        result = false;
      }
      sock.close();
    }
    catch(Exception e) {
      result = false;
    }
    return result;
  }


  private static class Target {
    private String file;
    private Integer position;

    public void setFile(String file) {
      this.file = file;
    }

    public void setPosition(Integer position) {
      this.position = position;
    }

    String getFile() {
      return this.file;
    }

    Integer getPosition() {
      return this.position;
    }
  }


  private static class Configuration {
    private String host;
    private String port;
    private String authorizationKey;
    private String srcDbConnStr;


    Configuration() {
      this.host = "";
      this.port = "8888";
      this.authorizationKey = "";
      this.srcDbConnStr = "";
    }

    String getHost() {
      return this.host;
    }

    String getPort() {
      return this.port;
    }

    String getAuthorizationKey() {
      return this.authorizationKey;
    }

    String getSrcDbConnStr() {
      return this.srcDbConnStr;
    }

    public void setHost(String host) {
      this.host = host;
    }

    public void setPort(String port) {
      this.port = port;
    }

    public void setAuthorizationKey(String authorizationKey) {
      this.authorizationKey = authorizationKey;
    }

    public void setSrcDbConnStr(String srcDbConnStr) {
      this.srcDbConnStr = srcDbConnStr;
    }
  }


  static {
    EMACS_ACK_FORMAT = "-emacs-pid ".chars().toArray();
  }
}


