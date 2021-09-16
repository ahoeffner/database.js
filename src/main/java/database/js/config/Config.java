/*
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 3 only, as
 * published by the Free Software Foundation.

 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 */

package database.js.config;

import ipc.Broker;
import java.io.File;
import org.json.JSONObject;
import org.json.JSONTokener;
import java.io.FileInputStream;
import database.js.security.PKIContext;


public class Config
{
  private final String loge; 
  private final String httpe; 
  private final String securitye; 
  private final String topologye; 
  private final String databasee;
  
  private Java java = null;
  private HTTP http = null;
  private Logger logger = null;
  private PKIContext pkictx = null;
  private Security security = null;
  private Topology topology = null;
  private Database database = null;
  private ipc.config.Config config = null;
  
  private static final String CONFDEF = "conf.json";
  private static final String JAVADEF = "java.json";
  private static final String HTTPDEF = "http.json";
  private static final String LOGGERDEF = "logger.json";
  private static final String SECURITYDEF = "security.json";

  private static final String TOPOLOGY = "topology";
  private static final String DATABASE = "database";
  
  
  public static boolean windows()
  {
    String os = System.getProperty("os.name");
    return(os.toLowerCase().startsWith("win"));    
  }


  public Config() throws Exception
  {
    FileInputStream in = new FileInputStream(confpath());
    
    JSONTokener tokener = new JSONTokener(in);
    JSONObject  config  = new JSONObject(tokener);
    
    this.topologye = config.getString("topology");
    this.databasee = config.getString("database");

    if (!config.has("log")) loge = "logger"; 
    else  loge = config.getString("logger");
    
    if (!config.has("http")) httpe = "http"; 
    else   httpe = config.getString("http");
    
    if (!config.has("security")) securitye = "security"; 
    else       securitye = config.getString("security");
  }
  
    
  public synchronized ipc.config.Config getIPConfig() throws Exception
  {
    if (config != null) return(config);
    
    Topology topology = this.getTopology();

    int extnds = topology.extnds();
    String extsize = topology.extsize();
    
    short processes = 4;
    short statesize = 16;
    
    Broker.logger(this.getLogger().logger);
    this.config = IPC.getConfig(Paths.ipcdir,processes,extnds,extsize,statesize);
    
    return(config);
  }
  
    
  public synchronized PKIContext getPKIContext() throws Exception
  {
    if (pkictx != null) return(pkictx);
    Security security = this.getSecurity();    
    pkictx = new PKIContext(security.getIdentity(),security.getTrusted());
    return(pkictx);
  }
  
    
  public synchronized Java getJava() throws Exception
  {
    if (java != null) return(java);
    FileInputStream in = new FileInputStream(javapath());
    
    JSONTokener tokener = new JSONTokener(in);
    JSONObject  config  = new JSONObject(tokener);
    
    java = new Java(config);
    return(java);
  }
  
  
  public synchronized HTTP getHTTP() throws Exception
  {
    if (http != null) return(http);
    FileInputStream in = new FileInputStream(httppath());
    
    JSONTokener tokener = new JSONTokener(in);
    JSONObject  config  = new JSONObject(tokener);
    
    http = new HTTP(config);
    return(http);
  }
  
  
  public synchronized Logger getLogger() throws Exception
  {
    if (logger != null) return(logger);
    FileInputStream in = new FileInputStream(loggerpath());
    
    JSONTokener tokener = new JSONTokener(in);
    JSONObject  config  = new JSONObject(tokener);
    
    logger = new Logger(config);
    return(logger);
  }
  
  
  public synchronized Database getDatabase() throws Exception
  {
    if (database != null) return(database);
    FileInputStream in = new FileInputStream(javapath());
    
    JSONTokener tokener = new JSONTokener(in);
    JSONObject  config  = new JSONObject(tokener);
    
    database = new Database(config);
    return(database);
  }
  
  
  public synchronized Security getSecurity() throws Exception
  {
    if (security != null) return(security);
    FileInputStream in = new FileInputStream(javapath());
    
    JSONTokener tokener = new JSONTokener(in);
    JSONObject  config  = new JSONObject(tokener);
    
    security = new Security(config);
    return(security);
  }
  
  
  public synchronized Topology getTopology() throws Exception
  {
    if (topology != null) return(topology);
    FileInputStream in = new FileInputStream(toppath());
    
    JSONTokener tokener = new JSONTokener(in);
    JSONObject  config  = new JSONObject(tokener);
    
    topology = new Topology(config);
    return(topology);
  }
  
  
  private String path()
  {
    return(Paths.confdir + File.separator);
  }
  
  
  private String javapath()
  {
    return(path() + JAVADEF);
  }
  
  
  private String httppath()
  {
    return(path() + HTTPDEF);
  }
  
  
  private String loggerpath()
  {
    return(path() + LOGGERDEF);
  }
  
  
  private String securitypath()
  {
    return(path() + SECURITYDEF);
  }
  
  
  private String confpath()
  {
    return(path() + CONFDEF);
  }
  
  
  private String dbpath()
  {
    return(path() + DATABASE + File.separator + databasee + ".json");
  }
  
  
  private String toppath()
  {
    return(path() + TOPOLOGY + File.separator + topologye + ".json");
  }
  
  
  public static enum Type
  {
    http,
    rest
  }
}