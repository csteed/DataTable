package data;

import org.joda.time.DateTime;
import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FilenameFilter;
import java.util.ArrayList;
import java.util.StringTokenizer;

/**
 * Created by csg on 4/28/15.
 */
public class ORCALogCSVExtractor {
    private final static Logger log = LoggerFactory.getLogger(ORCALogCSVExtractor.class);

    private static int netflowFieldIndices[] = new int [] {1, 5, 16, 17, 33, 34, 35, 36, 37, 38, 42, 43, 44, 45, 46, 47};
    private static DateTimeFormatter logDTFormatter = DateTimeFormat.forPattern("MMM d y hh:mm:ss");

    public static void main (String args[]) throws Exception {
        // get list of csv (netflow) files from the directory
        File netflowFileDirectory = new File("/Users/csg/Desktop/netflow-casestudy-data/netflow");
        File netflowFiles[] = netflowFileDirectory.listFiles(new FilenameFilter() {
            @Override
            public boolean accept(File dir, String name) {
                if (name.endsWith(".csv")) {
                    return true;
                } else {
                    return false;
                }
            }
        });
        log.debug("Found " + netflowFiles.length + " netflow files");

        // The ORCA alert log file
//        File orcaLogFile = new File("/Users/csg/Desktop/netflow-casestudy-data/orca-alert-logs/LapRLSLearner.alert.log");
        File orcaLogFile = new File("/Users/csg/Desktop/netflow-casestudy-data/orca-alert-logs/TweakedLearner.alert.log");

        // Read ORCA alert log file lines
        ArrayList<ArrayList<String>> alertList = new ArrayList<ArrayList<String>>();
        BufferedReader reader = new BufferedReader(new FileReader(orcaLogFile));
        String line = reader.readLine();
        while (line != null) {
            ArrayList<String> alertFields = new ArrayList<String>();

            StringTokenizer st = new StringTokenizer(line);
            int tokenCounter = 0;
            while (st.hasMoreTokens()) {
                String token = st.nextToken(",").trim();
                if (tokenCounter == 0) {
                    DateTime dt = logDTFormatter.parseDateTime(token);
                    alertFields.add(Long.toString(dt.getMillis()));
                } else if (tokenCounter == 5 || tokenCounter == 6) {

                    alertFields.add(token.substring(token.indexOf("=") + 1));
                }

                tokenCounter++;
            }
            alertList.add(alertFields);

            line = reader.readLine();
        }
        reader.close();

        log.debug("Read " + alertList.size() + " ORCA log file lines");

        // read each netflow file and collect any records that match the orca log file parameters
        ArrayList<ArrayList<String>> flowRecordList = new ArrayList<ArrayList<String>>();
        for (File f : netflowFiles) {
            log.debug("Reading " + f.getName());
            BufferedReader csvReader = new BufferedReader(new FileReader(f));
            line = csvReader.readLine();
            line = csvReader.readLine();
            while (line != null) {
                // read fields for the line


                String tokens[] = line.split(",");
//                for (String token : tokens) {
//                    flowRecordFields.add(token.trim());
//                }

//                StringTokenizer st = new StringTokenizer(line);
//                int tokenCounter = 0;
//                while (st.hasMoreTokens()) {
//                    String token = st.nextToken(",");
//                    flowRecordFields.add(token.trim());
//                    tokenCounter++;
//                }

                // check src and dst ip from ORCA log records for a match
//                log.debug(tokens[16] + " -> " + tokens[17]);

//                if (tokens[1].contains("59:13")) {
//                    log.debug(tokens[1] + "  " + tokens[16] + " -> " + tokens[17]);
//                }

                for (ArrayList<String> alertFields : alertList) {
                    // if srcIP and dstIP fields match keep the record
                    if (alertFields.get(1).equals(tokens[16].trim()) ||
                            alertFields.get(1).equals(tokens[17].trim())) {
//                    if (alertFields.get(1).equals(tokens[16].trim()) &&
//                            alertFields.get(2).equals(tokens[17].trim())) {
                        ArrayList<String> flowRecordFields = new ArrayList<String>();
                        for (int index : netflowFieldIndices) {
                            flowRecordFields.add(tokens[index].trim());
                        }

                        flowRecordList.add(flowRecordFields);
                        log.debug("Found a match and stored the record");
                        break;
                    }
                }

                // read next line
                line = csvReader.readLine();
            }
            csvReader.close();

            log.debug("Found " + flowRecordList.size() + " matches.");
        }

    }
}
