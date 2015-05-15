package gov.ornl.datatable;

import gov.ornl.datatable.Column;
import gov.ornl.datatable.DataModel;
import gov.ornl.datatable.Tuple;

import java.awt.Color;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Random;
import java.util.StringTokenizer;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

public class IOUtilities {
	private static final Logger log = LoggerFactory.getLogger(IOUtilities.class);

	public static void readCSVSample(File f, DataModel dataModel,
			double sampleFactor) throws IOException {
		BufferedReader reader = new BufferedReader(new FileReader(f));
		int totalLineCount = 0;
		String line = null;
		while ((line = reader.readLine()) != null) {
			totalLineCount++;
		}
		totalLineCount -= 1; // remove header line
		reader.close();

		log.debug("totalLineCount is " + totalLineCount);

		int sampleSize = (int) (sampleFactor * totalLineCount);
		log.debug("sample size is " + sampleSize);

		int sampleIndices[] = new int[sampleSize];
		boolean sampleSelected[] = new boolean[totalLineCount];
		Arrays.fill(sampleSelected, false);
		Random rand = new Random();
		for (int i = 0; i < sampleIndices.length; i++) {
			int index = rand.nextInt(totalLineCount);
			while (sampleSelected[index]) {
				log.debug("got a duplicate");
				index = rand.nextInt(totalLineCount);
			}
			sampleSelected[index] = true;
			sampleIndices[i] = index;
		}

		ArrayList<Tuple> tuples = new ArrayList<Tuple>();
		ArrayList<Column> columns = new ArrayList<Column>();
		reader = new BufferedReader(new FileReader(f));

		// Read the header line
		line = reader.readLine();
		int tokenCounter = 0;
		StringTokenizer st = new StringTokenizer(line);
		while (st.hasMoreTokens()) {
			String token = st.nextToken(",");
			Column column = new Column(token.trim());
//			column.setName(token.trim());
			columns.add(column);
			tokenCounter++;
		}

		// Read the data tuples
		int lineCounter = 0;
		boolean skipLine = false;
		while ((line = reader.readLine()) != null) {
			// is the current line selected to be read
			if (sampleSelected[lineCounter]) {
				// read the line as a tuple
				Tuple tuple = new Tuple();
				st = new StringTokenizer(line);
				tokenCounter = 0;

				skipLine = false;
				while (st.hasMoreTokens()) {
					String token = st.nextToken(",");
					try {
						float value = Float.parseFloat(token);

						// data attribute
						tuple.addElement(value);

						tokenCounter++;
					} catch (NumberFormatException ex) {
						log.debug("NumberFormatException caught so skipping record. "
								+ ex.fillInStackTrace());
						skipLine = true;
						break;
					}
				}

				if (!skipLine) {
					// log.debug("added tuple at index " + lineCounter);
					tuples.add(tuple);
				}

				// line = reader.readLine();
			}

			lineCounter++;
		}

		reader.close();
		dataModel.setData(tuples, columns);
	}

	public static void readCSV(File f, DataModel dataModel) throws IOException {
		BufferedReader reader = new BufferedReader(new FileReader(f));

		ArrayList<Tuple> tuples = new ArrayList<Tuple>();
		ArrayList<Column> columns = new ArrayList<Column>();

		String line = reader.readLine();
		int line_counter = 0;

		boolean skip_line = false;
		while (line != null) {
			if (line_counter == 0) {
				// The first line contains the column headers.

				int token_counter = 0;
				StringTokenizer st = new StringTokenizer(line);
				while (st.hasMoreTokens()) {
					String token = st.nextToken(",");
					Column column = new Column(token.trim());
//					column.setName(token.trim());
					columns.add(column);
					token_counter++;
				}

				line_counter++;
				line = reader.readLine();
				continue;
			}

			Tuple tuple = new Tuple();
			StringTokenizer st = new StringTokenizer(line);
			int token_counter = 0;

			skip_line = false;
			while (st.hasMoreTokens()) {
				String token = st.nextToken(",");

				try {
					float value = Float.parseFloat(token);

					// data attribute
					tuple.addElement(value);
					token_counter++;
				} catch (NumberFormatException ex) {
					System.out
							.println("DataSet.readCSV(): NumberFormatException caught so skipping record. "
									+ ex.fillInStackTrace());
					skip_line = true;
					break;
				}
			}

			if (tuple.getElementCount() != columns.size()) {
				log.debug("Row ignored because it has "
						+ (columns.size() - tuple.getElementCount())
						+ " column values missing.");
				skip_line = true;
			}

			if (!skip_line) {
				tuples.add(tuple);
			}

			line_counter++;
			line = reader.readLine();
		}

		reader.close();

		dataModel.setData(tuples, columns);

		// dataset.setData(data);
		// dataset.setAxisNames(axisNames);
		// dataset.setDataMax(maxData);
		// dataset.setDataMin(minData);
		// dataset.setNumberOfDimensions(data.get(0).getElementCount());

		// return dataset;
	}
}
