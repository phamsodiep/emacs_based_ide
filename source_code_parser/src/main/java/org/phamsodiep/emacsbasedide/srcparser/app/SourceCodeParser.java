package org.phamsodiep.emacsbasedide.srcparser.app;


import java.lang.reflect.Method;

import java.io.File;
import java.util.Collection;
import java.util.List;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Map;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.logging.Logger;
import java.util.logging.Level;

import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.dom.FileASTRequestor;
import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.AbstractTypeDeclaration;
import org.eclipse.jdt.core.dom.SimpleName;
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.IBinding;

import com.mongodb.MongoClient;
import com.mongodb.BasicDBObject;
import com.mongodb.DBObject;
import com.mongodb.DB;
import com.mongodb.DBCollection;
import com.mongodb.DBCursor;
import com.mongodb.BasicDBList;


public class SourceCodeParser {
  private static final String WORKSPACE_BASE = System.getenv("SRC_PARSER_WS");
  private static final String MAVEN_SRC_LOCATION = "src/main/java";
  private static final String MAVEN_CLASS_LOCATION = "target/classes";
  private static final int MAVEN_SRC_LOCATION_STR_LEN =
    MAVEN_SRC_LOCATION.length();


  public static void main(String[] args) {
    DB database = openDatabase(args);
    if (database == null) {
      return;
    }
    String cmd = args[0];
    Map<String, String> result = processCommand(database, cmd);
  }


  private static DB openDatabase(String[] args) {
    // Parse input parameter
    String cmd = null;
    String databaseName = System.getProperty("user.name");
    String host = "localhost";
    Integer port = 27017;

    switch (args.length) {
      case 3:
        databaseName = args[2];

      case 2: {
        String[] hostport = args[1].split(":");
        if (hostport.length >= 2) {
          port = new Integer(hostport[1]);
        }
        host = hostport[0];
      }

      case 1:
        break;

      default:
        return null;
    }

    // Open client connection and database
    Logger logger = Logger.getLogger("org.mongodb.driver");
    logger.setLevel(Level.SEVERE);
    MongoClient mongoClient = new MongoClient(host, port);
    return mongoClient.getDB(databaseName);
  }


  private static Map<String, String> processCommand(
    DB database,
    String cmdStr
  ) {
    int cmdStrLength = cmdStr.length();
    if (cmdStrLength == 0) {
      return null;
    }

    char cmdOp = cmdStr.charAt(0);
    switch (cmdOp) {
      case 'G':
        if (cmdStrLength > 1) {
          return null;
        }
        return processGetWorkspaceInformationCommand(database);

      case 'S':
        return processSwitchWorkspaceCommand(
          database,
          cmdStrLength == 1 ? "" : cmdStr.substring(1, cmdStrLength)
        );

      case 'P':
        return processParseCommand(
          database,
          cmdStr.substring(1, cmdStrLength)
        );

      default:
        break;
    }

    return null;
  }


  private static Map<String, String> processGetWorkspaceInformationCommand(
    DB database
  ) {
    Map<String, String> result = new HashMap();
    result.put("error", "Y");
    result.put("opcode", "G");
    String path = getWorkspacePath(database);
    if (path != null) {
      result.put("error", "N");
      result.put("path", path);
    }
    return result;
  }


  private static Map<String, String> processSwitchWorkspaceCommand(
    DB database,
    String path
  ) {
    Map<String, String> result = new HashMap();
    result.put("opcode", "S");
    result.put("error", "Y");
    // Query object
    BasicDBObject query = new BasicDBObject();
    query.put("_id", 1);
    // New value object
    BasicDBObject workspace = new BasicDBObject();
    workspace.put("_id", 1);
    workspace.put("path", path);
    // Target update object
    BasicDBObject target = new BasicDBObject();
    target.put("$set", workspace);
    DBCollection wsCollection = database.getCollection("workspace");
    wsCollection.update(query, target, true, false);
    result.put("error", "N");
    return result;
  }


  private static Map<String, String> processParseCommand(
    DB database,
    String target
  ) {
    Map<String, String> result = new HashMap();
    result.put("opcode", "P");
    result.put("error", "Y");
    int targetLength = target.length();
    if (targetLength == 0) {
      processParseWorkspaceCommand(result, database);
    }
    else {
    }
    return result;
  }


  private static void processParseWorkspaceCommand(
    Map<String, String> result,
    DB database
  ) {
    final String fullPath = getWorkspaceFullPath(database);
    if (fullPath == null) {
      return;
    }

    // Retreive enviroment parameters
    String srcPath = fullPath + MAVEN_SRC_LOCATION;
    String classPath = fullPath + MAVEN_CLASS_LOCATION;
    if (!doesPathExist(srcPath) || !doesPathExist(classPath)) {
      return;
    }

    // List source files
    List<String> srcFilesList = new ArrayList<String>();
    listSourceCodeFiles(srcPath, srcFilesList, ".java");
    String srcFiles[] = srcFilesList.toArray(new String[srcFilesList.size()]);
    // Construct a parser
    ASTParser parser = ASTParser.newParser(AST.JLS8);
    parser.setKind(ASTParser.K_COMPILATION_UNIT);
    parser.setResolveBindings(true);
    parser.setStatementsRecovery(true);
    parser.setBindingsRecovery(true);
    Hashtable<String, String> compilerOptions = JavaCore.getDefaultOptions();
    compilerOptions.put(JavaCore.COMPILER_SOURCE, JavaCore.VERSION_1_8);
    parser.setCompilerOptions(compilerOptions);
    parser.setEnvironment(
      new String[] {classPath},
      new String[] {srcPath},
      new String[] { "UTF-8"},
      true
    );
    // Construct a visitor to process parser events
    ASTVisitorImpl astVisitor = new ASTVisitorImpl(fullPath);
    FileASTRequestor requestor = new FileASTRequestor() {
      public void acceptAST(String sourceFullPath, CompilationUnit cu) {
        astVisitor.setContext(sourceFullPath, cu);
        cu.accept(astVisitor);
      }
      public void acceptBinding(String bindingKey, IBinding binding) {
      }
    };
    // Do parse and delegate event processing to visitor
    parser.createASTs(srcFiles, null, new String[] {""}, requestor, null);

    // Request visitor do post process after parsing
    astVisitor.retrieveSimpleNamesDefinition();
    astVisitor.persit(database);

    // Successfully process return
    result.put("error", "N");
  }


  private static String getWorkspacePath(DB database) {
    DBCollection wsCollection = database.getCollection("workspace");
    DBObject workspace = wsCollection.findOne();
    if (workspace == null) {
      return null;
    }
    Object path = workspace.get("path");
    return path == null ? null : path.toString();
  }


  private static String getWorkspaceFullPath(DB database) {
    String pathPrefix = WORKSPACE_BASE == null ? "" : WORKSPACE_BASE + "/";
    String path = getWorkspacePath(database);
    return path == null ? null : pathPrefix + path;
  }


  private static void listSourceCodeFiles(
    String folderFullPath,
    List<String> files,
    String... sourceCodeFileExtensions
  ) {
    if (folderFullPath == null) {
      return;
    }
    File folder = new File(folderFullPath);

    // Get all files from a folder
    File[] srcFiles = folder.listFiles();
    int srcFileCount = srcFiles == null ? 0 : srcFiles.length;
    for(int i = 0; i < srcFileCount; i++) {
      File srcFile = srcFiles[i];
      if (srcFile.isFile()) {
        final String fileName = srcFile.getName();
        boolean isMatched = false;
        for (String sourceCodeFileExtension: sourceCodeFileExtensions) {
          if (fileName.endsWith(sourceCodeFileExtension)) {
            isMatched = true;
            break;
          }
        }
        if (isMatched) {
          files.add(srcFile.getAbsolutePath());
        }
      }
      else if (srcFile.isDirectory()) {
        String subFolderFullPath = srcFile.getAbsolutePath();
        listSourceCodeFiles(subFolderFullPath, files, sourceCodeFileExtensions);
      }
    }
  }


  private static boolean doesPathExist(String fullPath) {
    File folder = new File(fullPath);
    return folder.isDirectory();
  }

  private static String convertToClassName(String fileLocalName) {
    boolean isMavenPrj = fileLocalName.startsWith(MAVEN_SRC_LOCATION);
    int startPos = isMavenPrj ? MAVEN_SRC_LOCATION_STR_LEN + 1 : 0;
    boolean isJavaFile = fileLocalName.endsWith(".java");
    return isJavaFile ?
      fileLocalName.substring(startPos, fileLocalName.length() - 5) :
      fileLocalName.substring(startPos);
  }



  private static final class ASTVisitorImpl extends ASTVisitor {
    private static final String K_SRC_KEY      = "src";
    private static final String K_ID           = "_id";
    private static final String K_SIMPLE_NAMES = "simpleNames";

    private CompilationUnit cu;
    private String fullPath;
    private String workspaceFullPath;
    private boolean isLegalFile = false;
    private String className = "";
    private Map<String, CompilationUnit> cuMap;
    private Map<String, DBObject> srcDocs;


    public ASTVisitorImpl(String workspaceFullPath) {
      this.workspaceFullPath = workspaceFullPath;
      this.cuMap = new HashMap<String, CompilationUnit>();
      this.srcDocs = new HashMap<String, DBObject>();
    }

    public boolean visit(SimpleName node) {
      // Retreive node information
      IBinding binding = node.resolveBinding();
      if (binding == null) {
        return true;
      }
      String key = binding.getKey();
      Integer startPos = node.getStartPosition();
      Integer endPos = startPos + node.getLength() - 1;
      SimpleName defNode = getDeclarationSimpleName(this.cu, key);
      // Skip simple name that also acts as definition node
      if (defNode != null) {
        if (defNode.getStartPosition() == node.getStartPosition()) {
          return true;
        }
      }

      DBObject srcDoc = this.getSrcDocument(this.className);
      DBObject simpleName = new BasicDBObject();
      List<DBObject> simpleNames = (List<DBObject>) srcDoc.get(K_SIMPLE_NAMES);
      simpleNames.add(simpleName);

      simpleName.put("start", startPos);
      simpleName.put("end", endPos);
      simpleName.put("key", key);
      return true;
    }

    private DBObject getSrcDocument(String srcKey) {
      DBObject result = this.srcDocs.get(srcKey);
      if (result == null) {
        result = new BasicDBObject();
        result.put(K_ID, srcKey);
        result.put(K_SIMPLE_NAMES, new BasicDBList());
        this.srcDocs.put(srcKey, result);
        result = this.srcDocs.get(srcKey);
      }
      return result;
    }

    public void setContext(String fullPath, CompilationUnit cu) {
      this.cu = cu;
      this.fullPath = fullPath;
      this.isLegalFile = this.fullPath.startsWith(this.workspaceFullPath);
      this.className =
        isLegalFile ?
        convertToClassName(
          this.fullPath.substring(this.workspaceFullPath.length())
        ) :
        "";
      this.cuMap.put(this.className, cu);
    }

    public boolean retrieveSimpleNamesDefinition() {
      Collection<String> keys = this.srcDocs.keySet();
      for (String srcClassName : keys) {
        DBObject srcDoc = this.srcDocs.get(srcClassName);
        List<DBObject> simpleNames =
          (List<DBObject>) srcDoc.get(K_SIMPLE_NAMES);
        for (DBObject simpleName : simpleNames) {
          String key = simpleName.get("key").toString();
          String defClassName = convertKeyToClassName(key);
          CompilationUnit defCu = this.findDefinitionCompileUnit(defClassName);
          if (defCu != null) {
            SimpleName defSimpleName = getDeclarationSimpleName(defCu, key);
            if (defSimpleName != null) {
              int defPos = defSimpleName.getStartPosition();
              simpleName.put("defPos", new Integer(defPos));
              simpleName.put("defLine", defCu.getLineNumber(defPos));
              simpleName.put("defCol", defCu.getColumnNumber(defPos)); // + 1
              if (srcClassName.compareTo(defClassName) != 0) {
                simpleName.put("defClassName", defClassName);
              }
            }
          }
        }
      }
      return true;
    }

    public void persit(DB database) {
      DBCollection srcCollection = database.getCollection(K_SRC_KEY);
      Collection<String> keys = this.srcDocs.keySet();
      for (String key : keys) {
        DBObject srcDoc = this.srcDocs.get(key);
        // Refresh this source file
        srcCollection.remove(new BasicDBObject(K_ID, key));
        srcCollection.insert(srcDoc);
      }
    }

    private static String convertKeyToClassName(String key) {
      String result = null;
      if(key.length() > 0 && key.charAt(0) == 'L') {
        int end = key.indexOf(";");
        if (end > 1 && key.charAt(end - 1) == '>') {
          end = key.indexOf("<");
        }
        result = key.substring(1, end);
      }
      return result;
    }

    private CompilationUnit findDefinitionCompileUnit(String defClassName) {
      CompilationUnit result = null;
      if (defClassName != null) {
        result = this.cuMap.get(defClassName);
      }
      return result;
    }

    private static SimpleName getDeclarationSimpleName(
      CompilationUnit defCu,
      String key
    ) {
      SimpleName result = null;
      Object node = defCu.findDeclaringNode(key);
      if (node != null) {
        try {
          Method getNameMethod = node.getClass().getMethod("getName");
          Object defSimpleName = getNameMethod.invoke(node);
          if (defSimpleName instanceof SimpleName) {
            result = (SimpleName) defSimpleName;
          }
        }
        catch(Exception e) {
        }
      }
      return result;
    }
  }
}


