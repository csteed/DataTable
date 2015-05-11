package gov.ornl.datatable;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.util.ArrayList;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SyntheticDataGenerator {
	private final static Logger log = LoggerFactory.getLogger(SyntheticDataGenerator.class);
			
	public static int numRows = 1000;
	public static int numCols = 10;
	public static float maxValue = 10.f;
	public static float minValue = -10.f;
	
	public static void main(String[] args) throws Exception {
		ArrayList<ArrayList<Float>> rows = new ArrayList<ArrayList<Float>>();
		
		for (int irow = 0; irow < numRows; irow++) {
			ArrayList<Float> row = new ArrayList<Float>();
			
			for (int icol = 0; icol < numCols; icol++) {
				float value = 0.f;
				if (icol == 0) {
					value = (((float)irow/numRows) * (maxValue - minValue)) + minValue;
				} else if (icol == 1) {
					value = maxValue - (((float)irow/numRows) * (maxValue - minValue));
				} else if (icol == 2) {
					value = maxValue - (((float)irow/numRows) * (maxValue - minValue));
				} else if (icol == 3) {
					value = (((float)irow/numRows) * (maxValue - minValue)) + minValue;
				} else if (icol == 4) {
					value = (float) Math.exp(row.get(1));
				} else if (icol == 5) {
					value = (float) Math.pow(row.get(1), 2);
				} else if (icol == 6) {
					value = (float) Math.cos(row.get(1));
				} else if (icol == 7) {
					value = (float) Math.sin(row.get(1));
				} else if (icol == 8) {
					value = (float) Math.cosh(row.get(1));
				} else if (icol == 9) {
					value = (float) Math.pow(row.get(1), 4);
				}
				row.add(value);
			}
			rows.add(row);
		}
		
		BufferedWriter writer = new BufferedWriter(new FileWriter("test.csv"));
		
		StringBuffer header = new StringBuffer();
		for (int icol = 0; icol < numCols; icol++) {
			header.append(icol+1);
			if (icol + 1 < numCols) {
				header.append(", ");
			}
		}
		writer.write(header.toString() + "\n");
		
		for (int irow = 0; irow < numRows; irow++) {
			ArrayList<Float> row = rows.get(irow);
			for (int icol = 0; icol < numCols; icol++) {
				writer.write(String.valueOf(row.get(icol)));
				if (icol + 1 < numCols) {
					writer.write(", ");
				} else {
					writer.write("\n");
				}
			}
		}
		writer.close();
	}

}
