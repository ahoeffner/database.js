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

import org.json.JSONObject;


public class Rest
{
  public final int dump;
  public final int timeout;


  public Rest(JSONObject config)
  {
    this.dump = Config.get(config,"dump-stats");
    this.timeout = Config.get(config,"timeout");
  }
}