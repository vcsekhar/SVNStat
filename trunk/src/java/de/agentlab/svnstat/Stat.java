/*
 * Copyright (C) 2006 Juergen Lind, jli@agentlab.de
 * 
 * This program is free software; you can redistribute it and/or modify it under the terms of the
 * GNU General Public License as published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without
 * even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License along with this program; if
 * not, write to the Free Software Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA
 * 02111-1307 USA
 * 
 */

package de.agentlab.svnstat;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.collections.map.MultiKeyMap;
import org.apache.commons.math.stat.StatUtils;


public class Stat {

    private String      dir                 = "./";
    private String      repository;

    private List        users               = new ArrayList();
    private List        dates               = new ArrayList();
    private List        records             = new ArrayList();
    private Map         recordByDateMap     = new HashMap();
    private List        moduleList          = new ArrayList();

    private MultiKeyMap datesFromTo         = new MultiKeyMap();
    private MultiKeyMap commitsDateUser     = new MultiKeyMap();
    private MultiKeyMap countUserModule     = new MultiKeyMap();
    private MultiKeyMap countUserModuleDate = new MultiKeyMap();

    private List        moduleMapping       = new ArrayList();

    public Stat() {
        for (Enumeration e = Config.getKeys(); e.hasMoreElements();) {
            String key = (String) e.nextElement();
            if (key.startsWith("Module")) {
                String moduleName = key.substring(key.indexOf(".") + 1);
                String pattern = Config.getProperty(key);

                moduleMapping.add(new String[][] { { pattern, moduleName } });
            }
        }
    }

    public void setDir(String dir) {
        this.dir = dir + "/";
        new File(dir).mkdir();
    }

    public String getDir() {
        return this.dir;
    }

    public void setRepository(String repository) {
        this.repository = repository;
    }

    public String getRepository() {
        return this.repository;
    }

    public List getDates() {
        return this.dates;
    }

    public List getUsers() {
        return this.users;
    }

    public void addRecord(SvnRecord record) {
        this.records.add(record);

        List records = (List) this.recordByDateMap.get(record.getDate());
        if (records == null) {
            records = new ArrayList();
        }
        records.add(record);
        this.recordByDateMap.put(record.getDate(), records);

        if (!users.contains(record.getUser())) {
            this.users.add(record.getUser());
        }
        if (!dates.contains(record.getDate())) {
            this.dates.add(record.getDate());
        }
    }

    public int countCommits(String date, String user) {
        Integer cachedResult = (Integer) this.commitsDateUser.get(date, user);
        if (cachedResult != null) {
            return cachedResult.intValue();
        }
        int result = 0;
        for (Iterator i = this.records.iterator(); i.hasNext();) {
            SvnRecord record = (SvnRecord) i.next();
            if (record.getDate().equals(date) && record.getUser().equals(user)) {
                result++;
            }
        }

        this.commitsDateUser.put(date, user, new Integer(result));
        return result;
    }

    public int getChanges(String date, String user, String type) {
        int result = 0;
        for (Iterator i = this.records.iterator(); i.hasNext();) {
            SvnRecord record = (SvnRecord) i.next();
            if (record.getDate().equals(date) && record.getUser().equals(user)) {
                if (type.equals("added")) {
                    result += record.getAdded();
                }
                if (type.equals("modified")) {
                    result += record.getModified();
                }
                if (type.equals("deleted")) {
                    result += record.getDeleted();
                }
            }
        }
        return result;
    }

    public void commitsPerUser(String from, String to, String user) {
        List selectedDates = this.filterDates(from, to);

        String[] xAxisLabels = new String[selectedDates.size()];

        double[][] data;
        data = new double[1][selectedDates.size()];

        this.printCsv("Datum, ");

        this.printCsv(user);
        this.printCsv("\n");

        int index = 0;
        for (Iterator i = this.filterDates(from, to).iterator(); i.hasNext();) {
            String date = (String) i.next();

            xAxisLabels[index] = date;
            this.printCsv(date + ", ");

            int commits = this.countCommits(date, user);
            data[0][index] = commits;
            this.printCsv(commits);
            index++;
            this.printCsv("\n");
        }

        try {
            String filename = null;
            String[] legendLabels;
            legendLabels = new String[] { user };
            filename = user + "_commits.jpg";

            new Graph().stackedBarChart(Config.getIntProperty("CommitsPerUser.width", 850), Config
                .getIntProperty("CommitsPerUser.height", 360), Config
                .getProperty("CommitsPerUser.xLabel"), Config.getProperty("CommitsPerUser.yLabel"),
                xAxisLabels, Config.getProperty("CommitsPerUser.title") + this.repository, legendLabels,
                data, this.dir + filename);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void commitsAllUsers(String from, String to) {
        List selectedDates = this.filterDates(from, to);

        String[] xAxisLabels = new String[selectedDates.size()];

        double[][] data;
        data = new double[this.getUsers().size()][selectedDates.size()];

        this.printCsv("Datum, ");

        for (Iterator i = this.getUsers().iterator(); i.hasNext();) {
            this.printCsv((String) i.next());
            if (i.hasNext()) {
                this.printCsv(", ");
            }
        }
        this.printCsv(", Total");
        this.printCsv("\n");

        int index = 0;
        for (Iterator i = this.filterDates(from, to).iterator(); i.hasNext();) {
            String date = (String) i.next();

            xAxisLabels[index] = date;
            this.printCsv(date + ", ");

            int sum = 0;
            int jindex = 0;
            for (Iterator j = this.getUsers().iterator(); j.hasNext(); jindex++) {
                int commits = this.countCommits(date, (String) j.next());
                this.printCsv(commits);
                this.printCsv(", ");
                data[jindex][index] = commits;
                sum += commits;
            }
            this.printCsv(sum);
            index++;
            this.printCsv("\n");
        }

        try {
            String filename = null;
            String[] legendLabels;
            legendLabels = (String[]) this.getUsers().toArray(new String[data.length]);
            filename = "AllUsers_commits.jpg";
            new Graph().stackedBarChart(Config.getIntProperty("CommitsAllUsers.width", 850), Config
                .getIntProperty("CommitsAllUsers.height", 360), Config
                .getProperty("CommitsAllUsers.xLabel"), Config.getProperty("CommitsAllUsers.yLabel"),
                xAxisLabels, Config.getProperty("CommitsAllUsers.title") + this.repository, legendLabels,
                data, this.dir + filename);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void commitsTotal(String from, String to) {
        List selectedDates = this.filterDates(from, to);

        String[] xAxisLabels = new String[selectedDates.size()];

        double[][] data = new double[1][selectedDates.size()];

        this.printCsv("Datum, Total\n");

        int index = 0;
        for (Iterator i = this.filterDates(from, to).iterator(); i.hasNext();) {
            String date = (String) i.next();

            xAxisLabels[index] = date;
            this.printCsv(date + ", ");

            int sum = 0;
            for (Iterator j = this.getUsers().iterator(); j.hasNext();) {
                int commits = this.countCommits(date, (String) j.next());
                sum += commits;
                data[0][index] = sum;
            }
            this.printCsv(sum);

            index++;
            this.printCsv("\n");
        }

        try {
            String[] legendLabels = new String[] { Config.getProperty("CommitsTotal.yLabel") };

            new Graph().lineChart(Config.getIntProperty("CommitsTotal.width", 850), Config
                .getIntProperty("CommitsTotal.height", 360), Config.getProperty("CommitsTotal.xLabel"),
                Config.getProperty("CommitsTotal.yLabel"), xAxisLabels, Config
                    .getProperty("CommitsTotal.title")
                    + this.repository, legendLabels, data, -1, -1, this.dir + "Total_commits.jpg");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void changesPerUser(String from, String to, String user) {
        List selectedDates = this.filterDates(from, to);

        String[] xAxisLabels = new String[selectedDates.size()];

        double[][] data = new double[3][selectedDates.size()];

        int index = 0;
        for (Iterator i = this.filterDates(from, to).iterator(); i.hasNext();) {
            String date = (String) i.next();

            xAxisLabels[index] = date;

            data[0][index] = this.getChanges(date, user, "added");
            data[1][index] = this.getChanges(date, user, "modified");
            data[2][index] = this.getChanges(date, user, "deleted");
            index++;
        }

        try {
            String filename = null;
            String[] legendLabels;
            legendLabels = new String[] { "added", "modified", "deleted" };
            filename = user + "_changes.jpg";
            new Graph().stackedBarChart(Config.getIntProperty("ChangesPerUser.width", 850), Config
                .getIntProperty("ChangesPerUser.height", 360), Config
                .getProperty("ChangesPerUser.xlabel"), Config.getProperty("ChangesPerUser.ylabel"),
                xAxisLabels, Config.getProperty("ChangesPerUser.title") + this.repository, legendLabels,
                data, this.dir + filename);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void commitsPercentage(String from, String to) {
        String[] labels = new String[this.getUsers().size()];
        double[] data = new double[this.getUsers().size()];

        int index = 0;
        int sum[] = new int[this.getUsers().size() + 1];
        for (Iterator i = this.filterDates(from, to).iterator(); i.hasNext();) {
            String date = (String) i.next();

            int jindex = 1;
            for (Iterator j = this.getUsers().iterator(); j.hasNext(); jindex++) {
                int commits = this.countCommits(date, (String) j.next());
                sum[0] += commits;
                sum[jindex] += commits;
            }

            index++;
        }

        for (int i = 1; i < sum.length; i++) {
            labels[i - 1] = (String) this.getUsers().get(i - 1);
            data[i - 1] = (((double) sum[i]) / ((double) sum[0])) * 100.0;
        }
        try {
            new Graph().pieChart(Config.getIntProperty("CommitsPercentage.width", 850), Config
                .getIntProperty("CommitsPercentage.height", 360), labels, Config
                .getProperty("CommitsPercentage.title")
                + this.repository, data, this.dir + "Commit_Percentage.jpg");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void commitTimesPerUser(String from, String to, String user) {
        int offset = Config.getIntProperty("Server.timeoffset", 0);

        String[] xAxisLabels = new String[24];

        double[][] data = new double[1][24];

        for (int j = 0; j < 24; j++) {
            int k = Math.abs((j + (24 + offset)) % 24);
            String hour;

            if (k < 10) {
                hour = "0" + k;
            } else {
                hour = "" + k;
            }
            xAxisLabels[j] = "" + j + ":00";

            data[0][j] = this.getCommitsPerHour(user, from, to, "" + hour);
        }

        try {
            String filename = null;
            String[] legendLabels;
            legendLabels = new String[] { Config.getProperty("CommitTimesPerUser.yLabel") };
            filename = user + "_commitTimes.jpg";
            new Graph().stackedBarChart(Config.getIntProperty("CommitTimesPerUser.width", 850), Config
                .getIntProperty("CommitTimesPerUser.height", 360), Config
                .getProperty("CommitTimesPerUser.xLabel"), Config
                .getProperty("CommitTimesPerUser.yLabel"), xAxisLabels, Config
                .getProperty("CommitTimesPerUser.title")
                + this.repository, legendLabels, data, this.dir + filename);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void fileCount(String from, String to) {
    //public void fileCount(String from, String to) {
        List selectedDates = this.filterDates(from, to);

        String[] xAxisLabels = new String[selectedDates.size()];

        double[][] data = new double[1][selectedDates.size()];

        int base = 0;

        if (from != null) {
            // Compute number of files up to selected start date
            for (Iterator i = this.filterDates("0000-00-00", from).iterator(); i.hasNext();) {
                String date = (String) i.next();

                if (i.hasNext()) {
                    // omit last date
                    base += getFileCount(date);
                }
            }
        }
        int sum = base;
        int index = 0;
        for (Iterator i = this.filterDates(from, to).iterator(); i.hasNext();) { // TBD
            //for (Iterator i = this.filterDates(from, to).iterator(); i.hasNext();) {
            String date = (String) i.next();

            sum += getFileCount(date);
            xAxisLabels[index] = date;
            data[0][index] = sum;
            index++;
        }

        long minValue = Math.round(StatUtils.min(data[0]));
        long maxValue = Math.round(StatUtils.max(data[0]));

        long diff = maxValue-minValue;

        try {
            String[] legendLabels = new String[] { Config.getProperty("FileCount.yLabel") };

            new Graph().lineChart(Config.getIntProperty("FileCount.width", 850), Config.getIntProperty(
                "FileCount.height", 360), Config.getProperty("FileCount.xLabel"), Config
                .getProperty("FileCount.yLabel"), xAxisLabels, Config.getProperty("FileCount.title")
                + this.repository, legendLabels, data, minValue, diff / 4, this.dir + "File_Count.jpg"); // TBD: preempting an exception when diff == 0
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void modulesPerUser(String from, String to, String user) {
        if (this.moduleMapping.size() == 0) {
            return;
        }

        Map modulesPerDay = new HashMap();

        for (Iterator i = this.filterDates(from, to).iterator(); i.hasNext();) {
            String date = (String) i.next();
            List modules = new ArrayList();

            List records = this.getRecordsByDate(date);
            for (Iterator l = records.iterator(); l.hasNext();) {
                SvnRecord record = (SvnRecord) l.next();

                if (!record.getUser().equals(user)) {
                    continue;
                }

                List files = record.getFiles();
                for (Iterator j = files.iterator(); j.hasNext();) {
                    String filename = (String) j.next();
                    for (Iterator k = this.moduleMapping.iterator(); k.hasNext();) {
                        String[][] mapping = (String[][]) k.next();
                        String patternString = mapping[0][0];
                        String moduleName = mapping[0][1];
                        Pattern pattern = Pattern.compile(patternString);

                        Matcher mref = pattern.matcher(filename);

                        if (mref.find()) {
                            if (!modules.contains(moduleName)) {
                                modules.add(moduleName);
                            }
                            if (!moduleList.contains(moduleName)) {
                                moduleList.add(moduleName);
                            }
                            Integer count = (Integer) this.countUserModuleDate.get(user, moduleName, date);
                            if (count == null) {
                                count = new Integer(0);
                            }
                            this.countUserModuleDate.put(user, moduleName, date,
                            new Integer(count.intValue() + 1));
                        }
                    }
                }
            }
            if (modules.size() > 0) {
                modulesPerDay.put(date, modules);
                for (Iterator j = modules.iterator(); j.hasNext();) {
                    String module = (String) j.next();
                    this.addCommitCount(user, module);
                }
            }
        }
        try {
            PrintWriter pw = new PrintWriter(new FileOutputStream(this.dir + user + "_modules.txt"));
                for (Iterator i = this.filterDates(from, to).iterator(); i.hasNext();) {
                    String date = (String) i.next();
                    List modules = (List) modulesPerDay.get(date);
                    if (modules != null) {
                        pw.println(date + ": " + modules);
                    }
                }
            pw.close();
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }
    }

    public void moduleActivityPerUser() {
        try {
            PrintWriter pw = new PrintWriter(new FileOutputStream(this.dir + "moduleActivity.csv"));
            for (Iterator i = this.users.iterator(); i.hasNext();) {
                String user = (String) i.next();
                pw.println(user + ", ,");
                for (Iterator j = this.moduleList.iterator(); j.hasNext();) {
                    String module = (String) j.next();
                    Integer count = (Integer) this.countUserModule.get(user, module);
                    if (count != null) {
                        pw.println(", " + module + ", " + count);
                    }
                }
            }
            pw.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void moduleActivityPerUserPerDate() {
        try {
            for (Iterator i = this.users.iterator(); i.hasNext();) {
                String user = (String) i.next();
                PrintWriter pw = new PrintWriter(new FileOutputStream(this.dir + "/" + user + "_moduleActivityPerDate.csv"));

                for (Iterator j = this.dates.iterator(); j.hasNext();) {
                    String date = (String) j.next();
                    pw.print(date + ": ");
                    boolean first = true;
                    for (Iterator k = this.moduleList.iterator(); k.hasNext();) {
                        String module = (String) k.next();
                        Integer count = (Integer) this.countUserModuleDate.get(user, module, date);
                        if (count != null) {
                            if (!first) {
                                pw.print(", ");
                            }
                            first = false;
                            pw.print(module + "(" + count + ")");
                        }
                    }
                    pw.println();
                }
                pw.close();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void addCommitCount(String user, String module) {
        Integer curVal = (Integer) this.countUserModule.get(user, module);
        if (curVal == null) {
            curVal = new Integer(0);
        }
        this.countUserModule.put(user, module, new Integer(curVal.intValue() + 1));
    }

    private List getRecordsByDate(String date) {
        List records = (List) this.recordByDateMap.get(date);
        return records;
    }

    private int getFileCount(String date) {
        int sum = 0;
        for (Iterator j = this.users.iterator(); j.hasNext();) {
            String user = (String) j.next();
            int added = this.getChanges(date, user, "added");
            int deleted = this.getChanges(date, user, "deleted");

            int delta = added - deleted;
            sum += delta;
        }
        return sum;
    }

    private double getCommitsPerHour(String user, String from, String to, String hour) {
        List records = new ArrayList();

        List selectedDates = this.filterDates(from, to);
        for (Iterator i = selectedDates.iterator(); i.hasNext();) {
            String date = (String) i.next();
            records.addAll(this.getRecordsByDate(date));
        }
        int result = 0;

        for (Iterator i = records.iterator(); i.hasNext();) {
            SvnRecord record = (SvnRecord) i.next();
            if (record.getUser().equals(user)) {
                String commitHour = record.getTime().substring(0, 2);
                if (commitHour.equals(hour)) {
                    result++;
                }
            }
        }
        return result;
    }

    private List filterDates(String from, String to) {
        List cachedResult = (List) this.datesFromTo.get(from, to);
        if (cachedResult != null) {
            return cachedResult;
        }

        List result = new ArrayList();
        for (Iterator i = this.dates.iterator(); i.hasNext();) {
            String date = (String) i.next();

            if (from != null && to != null) {
                if (date.compareTo(from) >= 0 && date.compareTo(to) <= 0) {
                    result.add(date);
                }
            } else if (from != null) {
                if (date.compareTo(from) >= 0) {
                    result.add(date);
                }
            } else if (to != null) {
                if (date.compareTo(to) <= 0) {
                    result.add(date);
                }
            } else {
                result.add(date);
            }
        }
        this.datesFromTo.put(from, to, result);
        return result;
    }

    private void printCsv(int sum) {
        // TODO Auto-generated method stub
    }

    public void printCsv(String string) {
        // TODO Auto-generated method stub
    }

}
