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

import java.io.File;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.ArrayList;
import java.net.InetAddress;
import java.util.concurrent.ConcurrentHashMap;


public class HTTP
{
  public final String host;
  public final String path;
  public final Ports ports;
  public final int timeout;
  public final int bufsize;
  public final String tmppath;
  public final String virtendp;
  public final int graceperiod;
  public final Handlers handlers;
  public final ArrayList<FilePattern> cache;
  public final ArrayList<String> corsdomains;
  public final ArrayList<FilePattern> compression;
  public final ConcurrentHashMap<String,String> mimetypes;


  HTTP(Handlers handlers, JSONObject config) throws Exception
  {
    this.timeout = Config.<Integer>get(config,"KeepAlive") * 1000;
    this.host = Config.get(config,"Host",InetAddress.getLocalHost().getHostName());

    JSONObject buffers = Config.getSection(config,"buffers");
    this.bufsize = Config.get(buffers,"network",4096);

    JSONObject deploy = config.getJSONObject("deployment");
    graceperiod = Config.get(deploy,"grace.period");

    String apppath = Config.get(deploy,"path");
    this.path = Config.getPath(apppath,Paths.apphome);

    File tmp = new File(Paths.tmpdir);
    this.tmppath = tmp.getCanonicalPath();

    this.ports = new Ports(Config.getSection(config,"ports"));


    JSONObject security = Config.getSection(config,"security");

    this.corsdomains = new ArrayList<String>();
    String corsdomains = Config.get(security,"Cors-Allow-Domains");

    if (corsdomains != null)
    {
      String[] domains = corsdomains.split("[ ,]+");

      for (int i = 0; i < domains.length; i++)
      {
        domains[i] = domains[i].trim();
        if (domains[i].length() > 0) this.corsdomains.add(domains[i]);
      }
    }

    JSONObject virtp = Config.getSection(config,"virtual-path");
    this.virtendp = Config.get(virtp,"endpoint");

    this.handlers = handlers;

    if (handlers != null)
    {
      JSONArray hconfig = Config.getArray(config,"handlers");

      for (int i = 0; i < hconfig.length(); i++)
      {
        JSONObject entry = hconfig.getJSONObject(i);

        this.handlers.add(
          Config.get(entry,"url"),
          Config.get(entry,"methods"),
          Config.get(entry,"class"));
      }

      this.handlers.finish();
    }


    this.cache = new ArrayList<FilePattern>();
    JSONArray cache = Config.getArray(config,"cache");

    for (int i = 0; i < cache.length(); i++)
    {
      JSONObject entry = cache.getJSONObject(i);

      int size = Config.get(entry,"maxsize");
      String pattern = Config.get(entry,"pattern");

      this.cache.add(new FilePattern(pattern,size));
    }


    this.compression = new ArrayList<FilePattern>();
    JSONArray compression = Config.getArray(config,"compression");

    for (int i = 0; i < compression.length(); i++)
    {
      JSONObject entry = compression.getJSONObject(i);

      int size = Config.get(entry,"minsize");
      String pattern = Config.get(entry,"pattern");

      this.compression.add(new FilePattern(pattern,size));
    }

    JSONArray mtypes = Config.getArray(config,"mimetypes");
    this.mimetypes = new ConcurrentHashMap<String,String>();

    for (int i = 0; i < mtypes.length(); i++)
    {
      JSONObject entry = mtypes.getJSONObject(i);
      mimetypes.put(Config.get(entry,"ext"),Config.get(entry,"type"));
    }
  }

  public String getAppPath()
  {
    return(path);
  }

  public String getTmpPath()
  {
    return(tmppath);
  }

  public String getVirtualEndpoint()
  {
    return(virtendp);
  }


  public static class FilePattern
  {
    public final int size;
    public final String pattern;

    FilePattern(String pattern, int size)
    {
      this.size = size;

      pattern = pattern.replace(".","\\.");
      pattern = pattern.replace("*",".*");

      this.pattern = pattern;
    }
  }
}