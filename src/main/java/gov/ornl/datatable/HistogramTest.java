package gov.ornl.datatable;

import gov.ornl.edenx.Utilities;

import java.io.File;

/**
 * Created by csg on 2/27/15.
 */
public class HistogramTest {
    public static void main (String args[]) throws Exception {
        Histogram histogram = new Histogram("Test", 3, 1., 3.);

        DataModel dataModel = new DataModel();
        File dataFile = new File("data/csv/cars.csv");
        Utilities.readCSV(dataFile, dataModel);

        int originColumnIndex = dataModel.getColumnIndex(dataModel.getColumn("Origin"));
        for (Tuple tuple : dataModel.getTuples()) {
            float value = tuple.getElement(originColumnIndex);
            histogram.fill(value);
        }
    }
}
