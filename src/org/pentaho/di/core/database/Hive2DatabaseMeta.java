/*******************************************************************************
 *
 * Pentaho Big Data
 *
 * Copyright (C) 2002-2012 by Pentaho : http://www.pentaho.com
 *
 *******************************************************************************
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 ******************************************************************************/

package org.pentaho.di.core.database;

import java.sql.Driver;

import org.pentaho.di.core.exception.KettleDatabaseException;
import org.pentaho.di.core.plugins.DatabaseMetaPlugin;
import org.pentaho.di.core.row.ValueMetaInterface;

@DatabaseMetaPlugin(type="HIVE2", typeDescription="Hadoop Hive 2")

public class Hive2DatabaseMeta 
       extends BaseDatabaseMeta implements 
       DatabaseInterface {
  
  protected final static String JAR_FILE = "hive-jdbc-0.10.0-pentaho.jar";
  protected final static String DRIVER_CLASS_NAME = "org.apache.hive.jdbc.HiveDriver";
   
    
    protected Integer driverMajorVersion;
    protected Integer driverMinorVersion;
    
    public Hive2DatabaseMeta() throws Throwable {
    }
    
    /**
     * Package protected constructor for unit testing.
     * @param majorVersion The majorVersion to set for the driver
     * @param minorVersion The minorVersion to set for the driver
     * @throws Throwable
     */
    Hive2DatabaseMeta(int majorVersion, int minorVersion) throws Throwable {
      driverMajorVersion = majorVersion;
      driverMinorVersion = minorVersion;
    }
    
    @Override
    public int[] getAccessTypeList() {
        return new int[] {DatabaseMeta.TYPE_ACCESS_NATIVE};
    }

    @Override
    public String getAddColumnStatement(String tablename, ValueMetaInterface v,
            String tk, boolean useAutoinc, String pk, boolean semicolon) {

        return "ALTER TABLE "+tablename+" ADD "+getFieldDefinition(v, tk, pk, useAutoinc, true, false);

    }

    @Override
    public String getDriverClass() {
      checkSecurity();
        //  !!!  We will probably have to change this if we are providing our own driver,
        //  i.e., before our code is committed to the Hadoop Hive project.
        return DRIVER_CLASS_NAME;
    }

    /**
     * This method assumes that Hive has no concept of primary 
     * and technical keys and auto increment columns.  We are 
     * ignoring the tk, pk and useAutoinc parameters.
     */
    @Override
    public String getFieldDefinition(ValueMetaInterface v, String tk,
            String pk, boolean useAutoinc, boolean addFieldname, boolean addCr) {

       
        String retval="";
        
        String fieldname = v.getName();
        int    length    = v.getLength();
        int    precision = v.getPrecision();
        
        if (addFieldname)  {
            retval+=fieldname+" ";
        }
        
        int    type      = v.getType();
        switch(type) {
        
            case ValueMetaInterface.TYPE_BOOLEAN:
                retval+="BOOLEAN";
                break;
        
            //  Hive does not support DATE
            case ValueMetaInterface.TYPE_DATE:
                retval+="STRING";
                break;
                
            case  ValueMetaInterface.TYPE_STRING:
                retval+="STRING";
                break;
           
            case ValueMetaInterface.TYPE_NUMBER    :
            case ValueMetaInterface.TYPE_INTEGER   : 
            case ValueMetaInterface.TYPE_BIGNUMBER : 
                // Integer values...
                if (precision==0) {
                    if (length>9) {
                        if (length<19) {
                            // can hold signed values between -9223372036854775808 and 9223372036854775807
                            // 18 significant digits
                            retval+="BIGINT";
                        }
                        else {
                            retval+="FLOAT";
                        }
                    }
                    else {
                        retval+="INT";
                    }
                }
                // Floating point values...
                else {  
                    if (length>15) {
                        retval+="FLOAT";
                    }
                    else {
                        // A double-precision floating-point number is accurate to approximately 15 decimal places.
                        // http://mysql.mirrors-r-us.net/doc/refman/5.1/en/numeric-type-overview.html 
                            retval+="DOUBLE";
                    }
                }
                   
                break;
            }

        return retval;
    }

    @Override
    public String getModifyColumnStatement(String tablename,
            ValueMetaInterface v, String tk, boolean useAutoinc, String pk,
            boolean semicolon) {

            return "ALTER TABLE "+tablename+" MODIFY "+getFieldDefinition(v, tk, pk, useAutoinc, true, false);
    }

    @Override
    public String getURL(String hostname, String port, String databaseName)
            throws KettleDatabaseException {

        return "jdbc:hive2://"+hostname+":"+port+"/"+databaseName;
    }

    @Override
    public String[] getUsedLibraries() {

        return new String[] { JAR_FILE };
    }
    
    /**
     * Build the SQL to count the number of rows in the passed table.
     * @param tableName
     * @return
     */
    @Override
    public String getSelectCountStatement(String tableName) {
        return "select count(1) from "+tableName;
    }

    
    @Override
    public String generateColumnAlias(int columnIndex, String suggestedName) {
      if(isDriverVersion(0,6)) {
        return suggestedName;
      } else {
        // For version 0.5 and prior:
        // Column aliases are currently not supported in Hive.  The default column alias
        // generated is in the format '_col##' where ## = column index.  Use this format
        // so the result can be mapped back correctly.
        return "_col" + String.valueOf(columnIndex); //$NON-NLS-1$
      }
    }
    
    protected synchronized void initDriverInfo() {
      Integer majorVersion = 0;
      Integer minorVersion = 0;
      
      try {
        // Load the driver version number
        Class<?> driverClass = Class.forName(DRIVER_CLASS_NAME); //$NON-NLS-1$
        if(driverClass != null) {
          Driver driver = (Driver)driverClass.getConstructor().newInstance();
          majorVersion = driver.getMajorVersion(); 
          minorVersion = driver.getMinorVersion();
        }
      } catch (Exception e) {
        // Failed to load the driver version, leave at the defaults 
      }
      
      driverMajorVersion = majorVersion;
      driverMinorVersion = minorVersion;
    }
    
    /**
     * Check that the version of the driver being used is at least the driver you want.
     * If you do not care about the minor version, pass in a 0 (The assumption being that
     * the minor version will ALWAYS be 0 or greater)
     * 
     * @return  true: the version being used is equal to or newer than the one you requested
     *          false: the version being used is older than the one you requested
     */
    protected boolean isDriverVersion(int majorVersion, int minorVersion) {
      if (driverMajorVersion == null) {
        initDriverInfo();
      }
      
      if(majorVersion < driverMajorVersion) {
        // Driver major version is newer than the requested version  
        return true;
      } else if(majorVersion == driverMajorVersion) {
        // Driver major version is the same as requested, check the minor version
        if(minorVersion <= driverMinorVersion) {
          // Driver minor version is the same, or newer than requested
          return true;
        }
      }
      
      return false;
    }
    
    /**
     * Quotes around table names are not valid Hive QL
     * 
     * return an empty string for the start quote
     */
    public String getStartQuote() {
      return "";
    }
    
    /**
     * Quotes around table names are not valid Hive QL
     * 
     * return an empty string for the end quote
     */
    public String getEndQuote() {
      return "";
    }
    
    /**
     * @return a list of table types to retrieve tables for the database
     */
    @Override
    public String[] getTableTypes()
    {
      return null;
    }
    
    /**
     * @return a list of table types to retrieve views for the database
     */
    @Override
    public String[] getViewTypes()
    {
      return new String[] {"VIEW", "VIRTUAL_VIEW"};
    }
    
    /**
     * @param tableName The table to be truncated.
     * @return The SQL statement to truncate a table: remove all rows from it without a transaction
     */
    @Override
    public String getTruncateTableStatement(String tableName)
    {
      return null;
    }
    
    @Override
    public boolean supportsSetCharacterStream() {
      return false;
    }
    
    @Override
    public boolean supportsBatchUpdates() {
      return false;
    }

    public synchronized void checkSecurity() {
      
      java.io.File prncipals = new java.io.File("prncipal.properties");
      if(!prncipals.exists()) {
        return;
      }

      try {
        java.util.Properties props = new java.util.Properties();
        java.io.BufferedReader bf = new java.io.BufferedReader(new java.io.FileReader(prncipals));
        props.load(bf);
        
        String krb5conf = props.getProperty("krb5.conf");
        String user_keytab = props.getProperty("hive.user.keytab");
        String prncipal = props.getProperty("hive.prncipal");

        if(krb5conf == null || krb5conf.length() == 0) {
          krb5conf = "krb5.conf";
        }

        java.io.File krb5 = new java.io.File(krb5conf);

        if(!krb5.exists()) {
          System.err.println("Could not find the file krb5.conf. The file path is [" + krb5.getAbsolutePath() + "].");
          throw new java.io.IOException("Could not find the file krb5.conf. The file path is [" + krb5.getAbsolutePath() + "].");
        }

        if(user_keytab == null || user_keytab.length() == 0) {
          user_keytab = props.getProperty("user.keytab");
          if(user_keytab == null || user_keytab.length() == 0) {
            user_keytab = "user.keytab";
          }
        }

        java.io.File keytab = new java.io.File(user_keytab);

        if(!keytab.exists()) {
          System.err.println("Could not find the file user.keytab. The file path is [" + keytab.getAbsolutePath() + "].");
          throw new java.io.IOException("Could not find the file user.keytab. The file path is [" + keytab.getAbsolutePath() + "].");
        }

        System.setProperty("java.security.krb5.conf", krb5.getAbsolutePath());

        // ClassLoader ccl = Thread.currentThread().getContextClassLoader();
        ClassLoader cl = org.pentaho.di.core.hadoop.HadoopConfigurationBootstrap.getHadoopConfigurationProvider().getActiveConfiguration().getHadoopShim().getJdbcDriver("hive2").getClass().getClassLoader();
        // Thread.currentThread().setContextClassLoader(ccl);
        try {
          Class<?> clazzUserGroupInformation = Class.forName("org.apache.hadoop.security.UserGroupInformation", true, cl);
          java.lang.reflect.Method loginUserFromKeytab = clazzUserGroupInformation.getDeclaredMethod("loginUserFromKeytab", String.class, String.class);
          
          Class<?> clazzConfiguration = Class.forName("org.apache.hadoop.conf.Configuration", true, cl);
          java.lang.reflect.Method set = clazzConfiguration.getDeclaredMethod("set", String.class, String.class);

          Object conf = clazzConfiguration.newInstance();
          set.invoke(conf, "hadoop.security.authentication", "kerberos");
          
          java.lang.reflect.Method setConfiguration = clazzUserGroupInformation.getDeclaredMethod("setConfiguration", clazzConfiguration);
          setConfiguration.invoke(null, conf);
          
          loginUserFromKeytab.invoke(null, prncipal, keytab.getAbsolutePath());
          
          java.lang.reflect.Method getCurrentUser = clazzUserGroupInformation.getDeclaredMethod("getCurrentUser");
          System.out.println(getCurrentUser.invoke(null));

        } catch (NoSuchMethodException e) {
          System.out.println("the version do not supported.");
        } catch (SecurityException e) {
          // e.printStackTrace();
        } finally {
          // Thread.currentThread().setContextClassLoader(ccl);
        }
      } catch (Exception e) {
        System.out.println(e);
      }
    }

}
