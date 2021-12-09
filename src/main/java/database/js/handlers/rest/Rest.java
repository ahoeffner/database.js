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

package database.js.handlers.rest;

import java.io.File;
import java.util.Date;
import java.util.Base64;
import java.util.HashMap;
import org.json.JSONArray;
import java.sql.Savepoint;
import org.json.JSONObject;
import java.util.ArrayList;
import java.io.FileInputStream;
import java.util.logging.Level;
import java.util.logging.Logger;
import database.js.config.Config;
import database.js.database.Pool;
import database.js.security.OAuth;
import database.js.servers.Server;
import database.js.custom.SQLRewriter;
import database.js.database.SQLParser;
import database.js.database.BindValue;
import database.js.custom.SQLValidator;
import database.js.database.AuthMethod;
import database.js.database.BindValueDef;
import database.js.database.NameValuePair;
import java.util.concurrent.ConcurrentHashMap;
import static database.js.handlers.rest.JSONFormatter.Type.*;


public class Rest
{
  private final String host;
  private final String repo;
  private final Server server;
  private final Config config;
  private final SessionState state;

  private final boolean compact;
  private final String dateform;

  private final boolean modify;
  private final boolean sppost;
  private final boolean sppatch;

  private boolean failed = false;

  private final SQLRewriter rewriter;
  private final SQLValidator validator;

  private final static Logger logger = Logger.getLogger("rest");
  private final HashMap<String,BindValueDef> bindvalues = new HashMap<String,BindValueDef>();
  private static final ConcurrentHashMap<String,String> sqlfiles = new ConcurrentHashMap<String,String>();


  public Rest(Server server, boolean modify, String host) throws Exception
  {
    this.host      = host;
    this.modify    = modify;

    this.server    = server;
    this.config    = server.config();
    this.state     = new SessionState(this);

    this.compact   = config.getDatabase().compact;
    this.rewriter  = config.getDatabase().rewriter;
    this.validator = config.getDatabase().validator;
    this.dateform  = config.getDatabase().dateformat;
    this.repo      = config.getDatabase().repository;
    this.sppost    = config.getDatabase().savepoint("sppost");
    this.sppatch   = config.getDatabase().savepoint("sppatch");
  }


  public String execute(String path, String payload)
  {
    try
    {
      Request request = new Request(this,path,payload);
      state.session(SessionManager.get(request.session));

      if (request.nvlfunc().equals("batch"))
        return(batch(request.payload));

      if (request.nvlfunc().equals("script"))
        return(script(request.payload));

      return(exec(request));
    }
    catch (Throwable e)
    {
      failed = true;
      return(error(e,false));
    }
  }


  private String batch(JSONObject payload)
  {
    try
    {
      JSONArray services = payload.getJSONArray("batch");

      String result = null;
      String response = "[\n";

      state.prepare(payload);

      for (int i = 0; i < services.length(); i++)
      {
        String cont = "\n";
        if (i < services.length() - 1) cont += ",\n";

        JSONObject spload = null;
        JSONObject service = services.getJSONObject(i);

        String path = service.getString("path");

        if (service.has("payload"))
          spload = service.getJSONObject("payload");

        Request request = new Request(this,path,spload);

        if (request.nvlfunc().equals("map"))
        {
          map(result,spload);
          continue;
        }

        result = exec(request);
        response += result + cont;

        if (failed) break;
      }

      response += "]";

      state.release();
      return(response);
    }
    catch (Throwable e)
    {
      failed = true;
      return(state.release(e));
    }
  }


  private String script(JSONObject payload)
  {
    try
    {
      JSONArray services = payload.getJSONArray("script");

      state.prepare(payload);

      String result = null;
      for (int i = 0; i < services.length(); i++)
      {
        String cont = "\n";
        if (i < services.length() - 1) cont += ",\n";

        JSONObject spload = null;
        JSONObject service = services.getJSONObject(i);

        String path = service.getString("path");

        if (service.has("payload"))
          spload = service.getJSONObject("payload");

        Request request = new Request(this,path,spload);

        if (request.nvlfunc().equals("map"))
        {
          map(result,spload);
          continue;
        }

        result = exec(request);
        if (failed) break;
      }

      state.release();
      return(result);
    }
    catch (Throwable e)
    {
      failed = true;
      return(state.release(e));
    }
  }


  private String exec(Request request)
  {
    String response = null;

    switch(request.cmd)
    {
      case "ping" :
        response = ping(request.payload); break;

      case "status" :
        response = status(request.payload); break;

      case "connect" :
        response = connect(request.payload); break;

      case "commit" :
        response = commit(); break;

      case "rollback" :
        response = rollback(); break;

      case "disconnect" :
        response = disconnect(); break;

      case "exec" :
        {
          switch(request.func)
          {
            case "ddl" :
              response = ddl(request.payload); break;

            case "merge" :
              response = update(request.payload); break;

            case "insert" :
              response = update(request.payload); break;

            case "update" :
              response = update(request.payload); break;

            case "select" :
              response = select(request.payload); break;

            case "fetch" :
              response = fetch(request.payload); break;

            case "call" :
              response = call(request.payload); break;

            default : return(error("Unknown command "+request));
          }

          break;
        }

      default :
        failed = true;
        return(error("Unknown command "+request.cmd));
    }

    return(response);
  }


  private String ping(JSONObject payload)
  {
    try
    {
      boolean keepalive = false;

      if (payload.has("keepalive"))
        keepalive = payload.getBoolean("keepalive");

      if (keepalive && state.session() == null)
        return(error("keepalive cannot be used without a valid session"));

      if (keepalive)
        state.session().touch();
    }
    catch (Throwable e)
    {
      failed = true;
      return(error(e,false));
    }

    JSONFormatter json = new JSONFormatter();
    json.success(true);
    return(json.toString());
  }


  private String status(JSONObject payload)
  {
    ArrayList<String[]> stats = new ArrayList<String[]>();

    try
    {
      ;
    }
    catch (Throwable e)
    {
      failed = true;
      return(error(e,false));
    }

    JSONFormatter json = new JSONFormatter();
    json.success(true);

    for(String[] stat : stats)
      json.add(stat[0],stat[1]);

    return(json.toString());
  }


  private String connect(JSONObject payload)
  {
    Pool pool = null;
    String scope = null;
    String secret = null;
    String username = null;
    AuthMethod method = null;
    boolean privateses = true;

    try
    {
      if (payload.has("username"))
        username = payload.getString("username");

      if (payload.has("private"))
        privateses = payload.getBoolean("private");

      if (payload.has("scope"))
        scope = payload.getString("scope");

      if (payload.has("auth.secret"))
        secret = payload.getString("auth.secret");

      if (payload.has("auth.method"))
      {
        String meth = payload.getString("auth.method");

        switch(meth.toLowerCase())
        {
          case "oauth"    : method = AuthMethod.OAuth; break;
          case "database" : method = AuthMethod.Database; break;
          case "token"    : method = AuthMethod.PoolToken; break;

          default: return(error("Unknown authentication method "+meth));
        }

        if (method == AuthMethod.OAuth)
        {
          username = OAuth.getUserName(secret);

          if (username == null)
            return(error("OAuth authentication failed"));
        }

        boolean anonymous = username == null;
        if (method == AuthMethod.PoolToken || method == AuthMethod.OAuth)
        {
          if (!anonymous) pool = config.getDatabase().proxy;
          else            pool = config.getDatabase().anonymous;

          if (pool == null)
            return(error("Connection pool not configured"));
        }

        state.session(new Session(method,pool,scope,username,secret));
        state.session().connect(state.batch());
      }
    }
    catch (Throwable e)
    {
      failed = true;
      return(error(e,false));
    }

    JSONFormatter json = new JSONFormatter();
    String sesid = encode(privateses,state.session().guid(),host);

    json.success(true);
    json.add("session",sesid);

    return(json.toString());
  }


  private String disconnect()
  {
    if (state.session() == null)
    {
      failed = true;
      return(error("not connected"));
    }

    try
    {
      state.session().disconnect();
    }
    catch (Throwable e)
    {
      failed = true;
      return(error(e,true));
    }

    JSONFormatter json = new JSONFormatter();

    json.success(true);
    json.add("disconnected",true);

    return(json.toString());
  }


  private String ddl(JSONObject payload)
  {
    boolean success = false;

    if (state.session() == null)
    {
      failed = true;
      return(error("not connected"));
    }

    try
    {
      String sql = getStatement(payload);
      if (sql == null) return(error("Attribute \"sql\" is missing"));

      state.ensure();
      state.prepare(payload);

      state.lock();
      success = state.session().execute(sql);
      state.unlock();

      state.release();
    }
    catch (Throwable e)
    {
      return(state.release(e));
    }

    JSONFormatter json = new JSONFormatter();
    json.success(true);
    json.add("result",success);
    return(json.toString());
  }


  private String select(JSONObject payload)
  {
    if (state.session() == null)
    {
      failed = true;
      return(error("not connected"));
    }

    try
    {
      int rows = 0;
      int skip = 0;
      String curname = null;
      boolean compact = this.compact;
      String dateform = this.dateform;

      if (payload.has("bindvalues"))
        this.getBindValues(payload.getJSONArray("bindvalues"));

      if (payload.has("rows")) rows = payload.getInt("rows");
      if (payload.has("skip")) skip = payload.getInt("skip");

      if (payload.has("dateformat"))
      {
        if (payload.isNull("dateformat")) dateform = null;
        else   dateform = payload.getString("dateformat");
      }

      if (payload.has("compact")) compact = payload.getBoolean("compact");
      if (state.session().dedicated() && payload.has("cursor")) curname = payload.getString("cursor");

      String sql = getStatement(payload);
      if (sql == null) return(error("Attribute \"sql\" is missing"));

      SQLParser parser = new SQLParser(bindvalues,sql);

      sql = parser.sql();
      ArrayList<BindValue> bindvalues = parser.bindvalues();

      if (rewriter != null)
        sql = rewriter.rewrite(sql,bindvalues);

      if (validator != null)
        validator.validate(sql,bindvalues);

      state.ensure();
      state.session().closeCursor(curname);

      state.prepare(payload);

      state.lock();
      Cursor cursor = state.session().executeQuery(curname,sql,bindvalues);
      state.unlock();

      cursor.rows = rows;
      cursor.compact = compact;
      cursor.dateformat = dateform;

      String[] columns = state.session().getColumnNames(cursor);
      ArrayList<Object[]> table = state.session().fetch(cursor,skip);

      state.release();

      JSONFormatter json = new JSONFormatter();

      json.success(true);
      json.add("more",!cursor.closed);

      if (compact)
      {
        json.push("columns",SimpleArray);
        json.add(columns);
        json.pop();

        json.push("rows",Matrix);
        json.add(table);
        json.pop();
      }
      else
      {
        json.push("rows",ObjectArray);
        for(Object[] row : table) json.add(columns,row);
        json.pop();
      }

      if (cursor.name == null)
        state.session().closeCursor(cursor);

      return(json.toString());
    }
    catch (Throwable e)
    {
      failed = true;
      return(state.release(e));
    }
  }


  private String update(JSONObject payload)
  {
    if (state.session() == null)
    {
      failed = true;
      return(error("not connected"));
    }

    try
    {
      if (payload.has("bindvalues"))
        this.getBindValues(payload.getJSONArray("bindvalues"));

      String sql = getStatement(payload);
      if (sql == null) return(error("Attribute \"sql\" is missing"));

      SQLParser parser = new SQLParser(bindvalues,sql);

      sql = parser.sql();
      ArrayList<BindValue> bindvalues = parser.bindvalues();

      if (rewriter != null)
        sql = rewriter.rewrite(sql,bindvalues);

      if (validator != null)
        validator.validate(sql,bindvalues);

      state.ensure();
      state.prepare(payload);

      state.lock();
      int rows = state.session().executeUpdate(sql,bindvalues);
      state.unlock();

      state.release();

      JSONFormatter json = new JSONFormatter();

      json.success(true);
      json.add("rows",rows);

      return(json.toString());
    }
    catch (Throwable e)
    {
      failed = true;
      return(state.release(e));
    }
  }


  private String call(JSONObject payload)
  {
    if (state.session() == null)
    {
      failed = true;
      return(error("not connected"));
    }

    try
    {
      String dateconv = null;

      if (payload.has("dateconversion"))
        dateconv = payload.getString("dateconversion");

      if (payload.has("bindvalues"))
        this.getBindValues(payload.getJSONArray("bindvalues"));

      String sql = getStatement(payload);
      if (sql == null) return(error("Attribute \"sql\" is missing"));

      SQLParser parser = new SQLParser(bindvalues,sql,true);

      sql = parser.sql();
      ArrayList<BindValue> bindvalues = parser.bindvalues();

      if (rewriter != null)
        sql = rewriter.rewrite(sql,bindvalues);

      if (validator != null)
        validator.validate(sql,bindvalues);

      state.ensure();
      state.prepare(payload);

      state.lock();
      System.out.println("sql: <"+sql+">");
      ArrayList<NameValuePair<Object>> values = state.session().executeCall(sql,bindvalues,dateconv);
      state.unlock();

      state.release();

      JSONFormatter json = new JSONFormatter();

      json.success(true);

      for(NameValuePair<Object> nvp : values)
        json.add(nvp.getName(),nvp.getValue());

      return(json.toString());
    }
    catch (Throwable e)
    {
      failed = true;
      return(state.release(e));
    }
  }


  private String fetch(JSONObject payload)
  {
    if (state.session() == null)
    {
      failed = true;
      return(error("not connected"));
    }

    try
    {
      boolean close = false;
      JSONFormatter json = new JSONFormatter();
      String name = payload.getString("cursor");

      Cursor cursor = state.session().getCursor(name);
      if (payload.has("close")) close = payload.getBoolean("close");

      if (cursor == null)
        return(error("Cursor \'"+name+"\' does not exist"));

      if (close)
      {
        state.session().closeCursor(name);

        json.success(true);
        json.add("closed",true);
        return(json.toString());
      }

      state.ensure();
      state.prepare(payload);

      String[] columns = state.session().getColumnNames(cursor);
      ArrayList<Object[]> table = state.session().fetch(cursor,0);

      state.release();

      json.success(true);
      json.add("more",!cursor.closed);

      if (cursor.compact)
      {
        json.push("columns",SimpleArray);
        json.add(columns);
        json.pop();

        json.push("rows",Matrix);
        json.add(table);
        json.pop();
      }
      else
      {
        json.push("rows",ObjectArray);
        for(Object[] row : table) json.add(columns,row);
        json.pop();
      }

      return(json.toString());
    }
    catch (Throwable e)
    {
      failed = true;
      return(state.release(e));
    }
  }


  private String commit()
  {
    if (state.session() == null)
    {
      failed = true;
      return(error("not connected"));
    }

    try
    {
      state.session().commit();
    }
    catch (Exception e)
    {
      failed = true;
      return(state.release(e));
    }

    JSONFormatter json = new JSONFormatter();
    json.success(true);
    return(json.toString());
  }


  private String rollback()
  {
    if (state.session() == null)
    {
      failed = true;
      return(error("not connected"));
    }

    try
    {
      state.session().rollback();
    }
    catch (Exception e)
    {
      failed = true;
      return(state.release(e));
    }

    JSONFormatter json = new JSONFormatter();
    json.success(true);
    return(json.toString());
  }


  private void map(String latest, JSONObject payload) throws Exception
  {
    try
    {
      JSONArray rows = null;
      ArrayList<String> cols = null;
      JSONObject last = Request.parse(latest);

      if (last.has("rows"))
        rows = last.getJSONArray("rows");

      if (last.has("columns"))
      {
        cols = new ArrayList<String>();
        JSONArray columns = last.getJSONArray("columns");
        for (int i = 0; i < columns.length(); i++)
          cols.add((String) columns.get(i));
      }

      String[] bindvalues = JSONObject.getNames(payload);

      if (rows == null)
      {
        // Previous was procedure
        for (int i = 0; i < bindvalues.length; i++)
        {
          String bindv = bindvalues[i];
          String pointer = payload.getString(bindv).trim();
          this.bindvalues.put(bindv,new BindValueDef(bindv,last.get(pointer)));
        }
      }
      else
      {
        // Previous was select
        for (int i = 0; i < bindvalues.length; i++)
        {
          int row = 0;
          Object value = null;
          String bindv = bindvalues[i];
          String pointer = payload.getString(bindv).trim();

          if (pointer.endsWith("]"))
          {
            int pos = pointer.lastIndexOf('[');

            if (pos > 0)
            {
              row = Integer.parseInt(pointer.substring(pos+1,pointer.length()-1));
              pointer = pointer.substring(0,pos);
            }
          }

          if (cols == null)
          {
            JSONObject record = (JSONObject) rows.get(row);
            value = record.get(pointer);
          }
          else
          {
            int col = -1;

            for (int j = 0; j < cols.size(); j++)
            {
              if (cols.get(j).equals(pointer))
              {
                col = j;
                break;
              }
            }

            JSONArray record = (JSONArray) rows.get(row);
            value = record.get(col);
          }

          this.bindvalues.put(bindv,new BindValueDef(bindv,value));
        }
      }
    }
    catch (Throwable e)
    {
      failed = true;
      error(e,false);
    }
  }


  String getStatement(JSONObject payload) throws Exception
  {
    if (!payload.has("sql"))
      return(null);

    String file = "@";
    String sql = payload.getString("sql");

    if (sql.startsWith(file))
    {
      String fname = sql.substring(file.length());
      if (!fname.startsWith(File.separator)) fname = File.separator + fname;

      fname = repo + fname;
      sql = sqlfiles.get(fname);
      if (sql != null) return(sql);

      File f = new File(fname);

      String path = f.getCanonicalPath();

      if (!path.startsWith(repo+File.separator))
        throw new Exception("Illegal path '"+path+"'. File must be located in repository");

      byte[] content = new byte[(int) f.length()];
      FileInputStream in = new FileInputStream(f);
      int read = in.read(content);
      in.close();

      if (read != content.length)
        throw new Exception("Could not read '"+f.getCanonicalPath()+"'");

      sql = new String(content);
      sqlfiles.put(fname,sql);
    }

    return(sql);
  }


  private void getBindValues(JSONArray values)
  {
    for (int i = 0; i < values.length(); i++)
    {
      JSONObject bvalue = values.getJSONObject(i);

      Object value = null;
      boolean outval = false;

      String name = bvalue.getString("name");
      String type = bvalue.getString("type");

      if (!bvalue.has("value")) outval = true;
      else value = bvalue.get("value");

      BindValueDef bindvalue = new BindValueDef(name,type,outval,value);

      if (value != null && bindvalue.isDate())
      {
        if (value instanceof Long)
          value = new Date((Long) value);
      }

      this.bindvalues.put(name,bindvalue);
    }
  }


  private boolean getSavepoint(JSONObject payload)
  {
    boolean defaults = modify ? sppatch : sppost;

    try
    {
      boolean savepoint = defaults;

      if (payload != null && payload.has("savepoint"))
        savepoint = payload.getBoolean("savepoint");

      return(savepoint);
    }
    catch (Throwable e)
    {
      error(e,false);
      return(defaults);
    }
  }


  String encode(boolean priv, String data)
  {
    return(encode(priv,data,host));
  }


  String decode(String data)
  {
    return(decode(data,host));
  }


  static String encode(boolean priv, String data, String salt)
  {
    byte[] bdata = data.getBytes();
    byte[] bsalt = salt.getBytes();

    byte indicator;
    long ran = System.currentTimeMillis() % 25;

    if (priv) indicator = (byte) ('a' + ran);
    else      indicator = (byte) ('A' + ran);

    byte[] token = new byte[bdata.length+1];

    token[0] = indicator;
    System.arraycopy(bdata,0,token,1,bdata.length);

    if (priv)
    {
      for (int i = 1; i < token.length; i++)
      {
        byte s = bsalt[i % bsalt.length];
        token[i] = (byte) (token[i] ^ s);
      }
    }

    token = Base64.getEncoder().encode(token);

    int len = token.length;
    while(token[len-1] == '=') len--;

    return(new String(token,0,len));
  }


  static String decode(String data, String salt)
  {
    byte[] bsalt = salt.getBytes();
    while(data.length() % 4 != 0) data += "=";

    byte[] bdata = Base64.getDecoder().decode(data);

    byte indicator = bdata[0];
    boolean priv = (indicator >= 'a' && indicator <= 'z');

    byte[] token = new byte[bdata.length-1];
    System.arraycopy(bdata,1,token,0,token.length);

    if (priv)
    {
      for (int i = 0; i < token.length; i++)
      {
        byte s = bsalt[(i+1) % bsalt.length];
        token[i] = (byte) (token[i] ^ s);
      }
    }

    return(new String(token));
  }


  private String error(Throwable err, boolean fatal)
  {
    JSONFormatter json = new JSONFormatter();
    logger.log(Level.WARNING,err.getMessage(),err);

    json.set(err);
    json.success(false);
    json.fatal(fatal,"disconnected");

    return(json.toString());
  }


  private String error(String message)
  {
    JSONFormatter json = new JSONFormatter();

    json.success(false);
    json.add("message",message);

    return(json.toString());
  }


  private static class SessionState
  {
    Rest rest;
    int dept = 0;
    int shared = 0;
    Session session;
    boolean modify = false;
    boolean exclusive = false;
    Savepoint savepoint = null;


    SessionState(Rest rest)
    {
      this.rest = rest;
      this.modify = rest.modify;
    }


    Session session()
    {
      return(this.session);
    }


    void session(Session session)
    {
      this.session = session;
    }


    boolean batch()
    {
      return(dept > 0);
    }


    void ensure() throws Exception
    {
      session.ensure();
    }


    void prepare(JSONObject payload) throws Exception
    {
      System.out.println("prepare dept="+dept);

      if (dept == 0)
      {
        boolean savepoint = rest.getSavepoint(payload);
        if (payload.has("savepoint")) savepoint = payload.getBoolean("savepoint");

        if (savepoint)
        {
          lock(true);
          this.savepoint = session.setSavePoint();
        }
      }

      dept++;
    }


    void release() throws Exception
    {
      if (--dept > 0)
        return;

      if (savepoint != null)
      {
        if (!session.releaseSavePoint(savepoint))
          throw new Exception("Could not release savepoint");

        unlock(true);
      }

      session.release(false);
    }


    String release(Throwable err)
    {
      boolean fatal = false;

      if (session != null)
      {
        session.releaseSavePoint(savepoint,true);

        releaseAll();
        fatal = session.release(true);
      }

      return(rest.error(err,fatal));
    }


    void lock()
    {
      lock(false);
    }


    private void lock(boolean exclusive)
    {
      try
      {
        session.lock().lock(exclusive);
        if (!exclusive) shared++;
        else this.exclusive = true;
      }
      catch (Throwable e)
      {
        rest.error(e,false);
      }
    }


    void unlock()
    {
      unlock(false);
    }


    private void unlock(boolean exclusive)
    {
      try
      {
        if (!exclusive && shared < 1)
          throw new Throwable("Cannot release shared lock not obtained");

        session.lock().release(exclusive);

        if (!exclusive) shared--;
        else this.exclusive = false;
      }
      catch (Throwable e)
      {
        rest.error(e,false);
      }
    }


    private void releaseAll()
    {
      try
      {
        if (exclusive || shared > 0)
          session.lock().release(exclusive,shared);

        shared = 0;
        exclusive = false;
      }
      catch (Throwable e)
      {
        rest.error(e,false);
      }
    }
  }
}