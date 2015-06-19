package gov.ornl.datatable;

import org.apache.commons.math3.stat.correlation.PearsonsCorrelation;
import org.apache.commons.math3.stat.descriptive.DescriptiveStatistics;
import org.apache.commons.math3.stat.regression.OLSMultipleLinearRegression;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Arrays;

public class DataModel {
	private static final int DEFAULT_NUM_HISTOGRAM_BINS = 50;
    private static final int MAX_NUM_HISTOGRAM_BINS = 100;
    private final static Logger log = LoggerFactory.getLogger(DataModel.class);

	protected ArrayList<Tuple> tuples = new ArrayList<Tuple>();
//	protected ArrayList<Tuple> queriedTuples = new ArrayList<Tuple>();
	protected ArrayList<Column> columns = new ArrayList<Column>();
	protected ArrayList<Column> disabledColumns = new ArrayList<Column>();
    protected ArrayList<Tuple> disabledColumnTuples = new ArrayList<Tuple>();
	protected OLSMultipleLinearRegression regression;

	private ArrayList<DataModelListener> listeners = new ArrayList<DataModelListener>();
	private Column highlightedColumn = null;
    private Column timeColumn = null;
    private Column xColumn = null;
    private Column yColumn = null;
	private Column regressionYColumn = null;
	private ArrayList<Query> savedQueryList = new ArrayList<Query>();
	private Query activeQuery = new Query("Q1");
	private int nextQueryNumber = 2;
    private int histogramBinSize = DEFAULT_NUM_HISTOGRAM_BINS;

	public DataModel() {

	}

    public Column getXColumn() {
        return xColumn;
    }

    public Column getYColumn() {
        return yColumn;
    }

    public void setYColumn (Column yColumn) {
        if (this.yColumn == yColumn) {
            return;
        }

        this.yColumn = yColumn;
        fireDataModelChanged();
    }

    public void clearYColumn() {
        if (yColumn == null) {
            return;
        }

        yColumn = null;
        fireDataModelChanged();
    }

    public void setXColumn (Column xColumn) {
        if (this.xColumn == xColumn) {
            return;
        }

        this.xColumn = xColumn;
        fireDataModelChanged();
    }

    public void clearXColumn() {
        if (xColumn == null) {
            return;
        }

        xColumn = null;
        fireDataModelChanged();
    }

	public boolean isEmpty() {
		return tuples.isEmpty();
	}

	public OLSMultipleLinearRegression getOLSMultipleLinearRegression() {
		return regression;
	}

	public Column getOLSMultipleLinearRegressionDependentColumn() {
		return regressionYColumn;
	}

	public Column getHighlightedColumn() {
		return highlightedColumn;
	}

	public void setHighlightedColumn(Column column) {
		if (columns.contains(column)) {
			highlightedColumn = column;
		}
		fireHighlightedColumnChanged();
	}

	public void clearHighlightedColumn() {
		if (highlightedColumn != null) {
			highlightedColumn = null;
		}
		fireHighlightedColumnChanged();
	}

    public void setTimeColumn(Column column) {
        if (columns.contains(column)) {
            timeColumn = column;
        }
        fireDataModelChanged();
    }

    public void clearTimeColumn() {
        if (timeColumn != null) {
            timeColumn = null;
            fireDataModelChanged();
        }
    }

    public Column getTimeColumn() {
        return timeColumn;
    }

	public int runMulticollinearityFilter(Column dependentColumn,
			boolean useQueryCorrelations, float significantCorrelationThreshold) {
		if (dependentColumn == null) {
			return -1;
		}

		int dependentColumnIdx = getColumnIndex(dependentColumn);
		if (dependentColumnIdx == -1) {
			return -1;
		}

		ArrayList<ColumnSortRecord> sortedColumnList = new ArrayList<ColumnSortRecord>();
		for (Column column : columns) {
			if (column == dependentColumn) {
				continue;
			}

			if (!column.isEnabled()) {
				continue;
			}

			float corrCoef;
			if (useQueryCorrelations) {
				corrCoef = activeQuery.getColumnQuerySummaryStats(column).getCorrelationCoefficients().get(dependentColumnIdx);
//				corrCoef = column.getQueryCorrelationCoefficients().get(dependentColumnIdx);
			} else {
				corrCoef = column.getSummaryStats().getCorrelationCoefficients().get(dependentColumnIdx);
//				corrCoef = column.getCorrelationCoefficients().get(dependentColumnIdx);
			}

			ColumnSortRecord rec = new ColumnSortRecord(column, (float) Math.abs(corrCoef));

			sortedColumnList.add(rec);
		}

		Object sortedRecords[] = sortedColumnList.toArray();
		Arrays.sort(sortedRecords);

		ArrayList<Column> removeColumnList = new ArrayList<Column>();

		log.debug("Sorted enabled columns by correlation coefficients with the dependent column");
		for (int i = 0; i < sortedRecords.length; i++) {
			ColumnSortRecord colRecord = (ColumnSortRecord) sortedRecords[i];
			log.debug(i + ": " + colRecord.column.getName() + " - " + colRecord.sortValue);

			if (removeColumnList.contains(colRecord.column)) {
				continue;
			}

			log.debug("Inspecting column '" + colRecord.column.getName());

			for (int j = 0; j < columns.size(); j++) {
				if (j == dependentColumnIdx) {
					continue;
				}
				Column column = columns.get(j);
				if (removeColumnList.contains(column)) {
					continue;
				}
				if (column == colRecord.column) {
					continue;
				}
				if (!column.isEnabled()) {
					continue;
				}

				float corrCoef;
				if (useQueryCorrelations) {
					corrCoef = (float)Math.abs(activeQuery.getColumnQuerySummaryStats(colRecord.column).getCorrelationCoefficients().get(j));
//					corrCoef = (float) Math.abs(colRecord.column.getQueryCorrelationCoefficients().get(j));
				} else {
					corrCoef = (float)Math.abs(colRecord.column.getSummaryStats().getCorrelationCoefficients().get(j));
//					corrCoef = (float) Math.abs(colRecord.column.getCorrelationCoefficients().get(j));
				}

				if (corrCoef > significantCorrelationThreshold) {
					log.debug("Removed column '" + column.getName() + "'" + "corrCoef=" + corrCoef);
					removeColumnList.add(column);
				}
			}
		}

		disableColumns(removeColumnList);
		return removeColumnList.size();
	}

	public void setData(ArrayList<Tuple> tuples, ArrayList<Column> columns) {
		if (columns.isEmpty()) {
			return;
		}

        histogramBinSize = (int)Math.floor(Math.sqrt(tuples.size()));
        if (histogramBinSize > MAX_NUM_HISTOGRAM_BINS) {
            histogramBinSize = MAX_NUM_HISTOGRAM_BINS;
        }
//        log.debug("histogram bin size is " + histogramBinSize);
		highlightedColumn = null;
		this.tuples.clear();
		this.tuples.addAll(tuples);
        disabledColumnTuples.clear();
		this.columns.clear();
		this.columns.addAll(columns);
		this.disabledColumns.clear();
//		this.queriedTuples.clear();
//		this.queriedTuples.addAll(tuples);
		this.regression = null;
		this.regressionYColumn = null;
		this.highlightedColumn = null;

		calculateStatistics();
		fireDataModelChanged();
	}

	public void addTuples(ArrayList<Tuple> newTuples) {
		this.tuples.addAll(newTuples);
		calculateStatistics();
		fireTuplesAdded(newTuples);
	}

	public void clear() {
		tuples.clear();
        disabledColumnTuples.clear();
//		this.queriedTuples.clear();
		clearActiveQuery();
		this.columns.clear();
		this.disabledColumns.clear();
		this.regression = null;
		this.regressionYColumn = null;
		this.highlightedColumn = null;
		this.timeColumn = null;
		fireDataModelChanged();
	}

	public void setColumnName(Column column, String name) {
		if (columns.contains(column)) {
			column.setName(name);
			fireDataModelChanged();
		}
		// } else if (disabledColumns.contains(column)) {
		// column.setName(name);
		// fireDataModelChanged();
		// }
	}

	private void calculateQueryStatistics() {
		double[][] data = new double[columns.size()][];

		for (int icolumn = 0; icolumn < columns.size(); icolumn++) {
			Column column = columns.get(icolumn);
			data[icolumn] = getColumnQueriedValues(icolumn);
			DescriptiveStatistics stats = new DescriptiveStatistics(data[icolumn]);

			SummaryStats columnSummaryStats = new SummaryStats();
			activeQuery.setColumnQuerySummaryStats(column, columnSummaryStats);

			columnSummaryStats.setMean((float) stats.getMean());
			columnSummaryStats.setMedian((float) stats.getPercentile(50));
			columnSummaryStats.setVariance((float) stats.getVariance());
			columnSummaryStats.setStandardDeviation((float)stats.getStandardDeviation());
			columnSummaryStats.setQuantile1((float)stats.getPercentile(25));
			columnSummaryStats.setQuantile3((float)stats.getPercentile(75));
			columnSummaryStats.setSkewness((float)stats.getSkewness());
			columnSummaryStats.setKurtosis((float)stats.getKurtosis());
			columnSummaryStats.setMax((float)stats.getMax());
			columnSummaryStats.setMin((float)stats.getMin());
//			column.setQueryMean((float) stats.getMean());
//			column.setQueryMedian((float) stats.getPercentile(50));
//			column.setQueryVariance((float) stats.getVariance());
//			column.setQueryStandardDeviation((float) stats
//					.getStandardDeviation());
//			column.setQueryQ1((float) stats.getPercentile(25));
//			column.setQueryQ3((float) stats.getPercentile(75));
//			column.setQuerySkewness((float) stats.getSkewness());
//			column.setQueryKurtosis((float) stats.getKurtosis());
//			column.setQueryMaxValue((float) stats.getMax());
//			column.setQueryMinValue((float) stats.getMin());

			// calculate whiskers for box plot 1.5 of IQR
			float iqr_range = 1.5f * columnSummaryStats.getIQR();
			float lowerFence = columnSummaryStats.getQuantile1() - iqr_range;
			float upperFence = columnSummaryStats.getQuantile3() + iqr_range;
//			float iqr_range = 1.5f * column.getQueryIQR();
//			float lowerFence = column.getQueryQ1() - iqr_range;
//			float upperFence = column.getQueryQ3() + iqr_range;
			double sorted_data[] = stats.getSortedValues();
//			double sorted_data[] = Arrays.copyOf(data[icolumn],
//					data[icolumn].length);
//			Arrays.sort(sorted_data);

			// find upper datum that is not greater than upper fence
//			if (upperFence >= column.getMaxQueryValue()) {
//				column.setQueryUpperWhisker(column.getMaxQueryValue());
			if (upperFence >= columnSummaryStats.getMax()) {
				columnSummaryStats.setUpperWhisker(columnSummaryStats.getMax());
			} else {
				// find largest datum not larger than upper fence value
				for (int i = sorted_data.length - 1; i >= 0; i--) {
					if (sorted_data[i] <= upperFence) {
						columnSummaryStats.setUpperWhisker((float)sorted_data[i]);
//						column.setQueryUpperWhisker((float) sorted_data[i]);
						break;
					}
				}
			}

//			if (lowerFence <= column.getMinQueryValue()) {
//				column.setQueryLowerWhisker(column.getMinQueryValue());
			if (lowerFence <= columnSummaryStats.getMin()) {
				columnSummaryStats.setLowerWhisker(columnSummaryStats.getMin());
			} else {
				// find smallest datum not less than lower fence value
				for (int i = 0; i < sorted_data.length; i++) {
					if (sorted_data[i] >= lowerFence) {
						columnSummaryStats.setLowerWhisker((float)sorted_data[i]);
//						column.setQueryLowerWhisker((float) sorted_data[i]);
						break;
					}
				}
			}

			// calculate frequency information for column
            Histogram histogram;
            if (column.isContinuous()) {
                histogram = new Histogram(column.getName(),
                        histogramBinSize, column.getSummaryStats().getMin(),
                        column.getSummaryStats().getMax());
            } else {
                int numBins = column.getSummaryStats().getHistogram().numberOfBins();
                histogram = new Histogram(column.getName(), numBins, column.getSummaryStats().getMin(),
                        column.getSummaryStats().getMax());
            }
            columnSummaryStats.setHistogram(histogram);

//			Histogram histogram = new Histogram(column.getName(),
//					DEFAULT_NUM_HISTOGRAM_BINS, column.getSummaryStats().getMin(),
//					column.getSummaryStats().getMax());
//			columnSummaryStats.setHistogram(histogram);
			for (double value : data[icolumn]) {
				histogram.fill(value);
			}
		}

		PearsonsCorrelation pCorr = new PearsonsCorrelation();

		for (int ix = 0; ix < columns.size(); ix++) {
			Column column = columns.get(ix);
			SummaryStats columnSummaryStats = activeQuery.getColumnQuerySummaryStats(column);

			ArrayList<Float> coefList = new ArrayList<Float>();

			for (int iy = 0; iy < columns.size(); iy++) {
				try {
					double coef = pCorr.correlation(data[ix], data[iy]);
					coefList.add((float) coef);
				} catch (Exception ex) {
					coefList.add(0.f);
				}
			}
			columnSummaryStats.setCorrelationCoefficients(coefList);
		}
	}

	public ArrayList<Column> getColumns() {
		return columns;
	}

	public void setColumns(ArrayList<Column> columns) {
		highlightedColumn = null;
		this.columns.clear();
		this.columns.addAll(columns);
		this.tuples.clear();
		fireDataModelChanged();
	}

	public ArrayList<Tuple> getTuples() {
		return tuples;
	}

	private void calculateStatistics() {
		double[][] data = new double[columns.size()][];

		for (int icolumn = 0; icolumn < columns.size(); icolumn++) {
			Column column = columns.get(icolumn);

			data[icolumn] = getColumnValues(icolumn);

			// calculate descriptive statistics
			DescriptiveStatistics stats = new DescriptiveStatistics(data[icolumn]);

			column.getSummaryStats().setMean((float) stats.getMean());
			column.getSummaryStats().setMedian((float) stats.getPercentile(50));
			column.getSummaryStats().setVariance((float) stats.getVariance());
			column.getSummaryStats().setStandardDeviation((float) stats.getStandardDeviation());
			column.getSummaryStats().setMax((float) stats.getMax());
			column.getSummaryStats().setMin((float) stats.getMin());
			column.getSummaryStats().setQuantile1((float) stats.getPercentile(25));
			column.getSummaryStats().setQuantile3((float) stats.getPercentile(75));
			column.getSummaryStats().setSkewness((float) stats.getSkewness());
			column.getSummaryStats().setKurtosis((float) stats.getKurtosis());

			// calculate whiskers for box plot 1.5 of IQR
			float iqr_range = 1.5f * column.getSummaryStats().getIQR();
			float lowerFence = column.getSummaryStats().getQuantile1() - iqr_range;
			float upperFence = column.getSummaryStats().getQuantile3() + iqr_range;
			double sorted_data[] = stats.getSortedValues();

			// find upper datum that is not greater than upper fence
			if (upperFence >= column.getSummaryStats().getMax()) {
				column.getSummaryStats().setUpperWhisker(column.getSummaryStats().getMax());
			} else {
				// find largest datum not larger than upper fence value
				for (int i = sorted_data.length - 1; i >= 0; i--) {
					if (sorted_data[i] <= upperFence) {
						column.getSummaryStats().setUpperWhisker((float) sorted_data[i]);
						break;
					}
				}
			}

			if (lowerFence <= column.getSummaryStats().getMin()) {
				column.getSummaryStats().setLowerWhisker(column.getSummaryStats().getMin());
			} else {
				// find smallest datum not less than lower fence value
				for (int i = 0; i < sorted_data.length; i++) {
					if (sorted_data[i] >= lowerFence) {
						column.getSummaryStats().setLowerWhisker((float) sorted_data[i]);
						break;
					}
				}
			}

			// calculate frequency information for column
            Histogram histogram;
            if (column.isContinuous()) {
                histogram = new Histogram(column.getName(),
                        histogramBinSize, column.getSummaryStats().getMin(),
                        column.getSummaryStats().getMax());
            } else {
                int numBins = ((int)column.getSummaryStats().getMax() - (int)column.getSummaryStats().getMin()) + 1;
                histogram = new Histogram(column.getName(), numBins, column.getSummaryStats().getMin(),
                        column.getSummaryStats().getMax());
            }
            column.getSummaryStats().setHistogram(histogram);

			for (double value : data[icolumn]) {
				histogram.fill(value);
			}
		}

		PearsonsCorrelation pCorr = new PearsonsCorrelation();

		for (int ix = 0; ix < columns.size(); ix++) {
			Column column = columns.get(ix);
			ArrayList<Float> coefList = new ArrayList<Float>();

			for (int iy = 0; iy < columns.size(); iy++) {
				double coef = pCorr.correlation(data[ix], data[iy]);
				coefList.add((float) coef);
			}
			column.getSummaryStats().setCorrelationCoefficients(coefList);
		}
	}

    public void makeColumnDiscrete(Column column) {
        if (column.isContinuous()) {
            column.makeDiscrete();
            calculateStatistics();
            if (activeQuery.hasColumnSelections()) {
                calculateQueryStatistics();
            }
            fireDataModelChanged();
        }
    }

    public void makeColumnContinuous(Column column) {
        if (column.isDiscrete()) {
            column.makeContinuous();
            calculateStatistics();
            if (activeQuery.hasColumnSelections()) {
                calculateQueryStatistics();
            }
            fireDataModelChanged();
        }
    }

	public OLSMultipleLinearRegression calculateOLSMultipleLinearRegression(
			Column yColumn) {
		regression = new OLSMultipleLinearRegression();
		regressionYColumn = yColumn;

		int yItemIndex = getColumnIndex(highlightedColumn);

		double[] y = new double[getTupleCount()];
		double[][] x = new double[getTupleCount()][getColumnCount() - 1];

		for (int i = 0; i < tuples.size(); i++) {
			Tuple tuple = tuples.get(i);
			y[i] = tuple.getElement(yItemIndex);

			for (int j = 0, k = 0; j < getColumnCount(); j++) {
				if (j == yItemIndex) {
					continue;
				}
				x[i][k++] = tuple.getElement(j);
			}
		}

		regression.newSampleData(y, x);

		log.debug("Regression results:");
		log.debug("rSquared: " + regression.calculateRSquared()
				+ " rSquaredAdj: " + regression.calculateAdjustedRSquared());
		double[] beta = regression.estimateRegressionParameters();
		for (int i = 0; i < beta.length; i++) {
			log.debug("b[" + i + "]: " + beta[i]);
		}

		fireDataModelChanged();
		return regression;
	}

	public double[] getColumnValues(int columnIndex) {
		Column column = columns.get(columnIndex);

		double[] values = new double[tuples.size()];

		for (int ituple = 0; ituple < tuples.size(); ituple++) {
			Tuple tuple = tuples.get(ituple);
			values[ituple] = tuple.getElement(columnIndex);
		}

		return values;
	}

	public double[] getColumnQueriedValues(int columnIndex) {
		Column column = columns.get(columnIndex);

//		double[] values = new double[queriedTuples.size()];
		double[] values = new double[activeQuery.getTuples().size()];

		for (int ituple = 0; ituple < activeQuery.getTuples().size(); ituple++) {
			Tuple tuple = activeQuery.getTuples().get(ituple);
			values[ituple] = tuple.getElement(columnIndex);
		}

		return values;
	}

	public void addDataModelListener(DataModelListener listener) {
		if (!listeners.contains(listener)) {
			listeners.add(listener);
		}
	}

	public Tuple getTuple(int idx) {
		return tuples.get(idx);
	}

	public Column getColumn(int idx) {
		return columns.get(idx);
	}

	// public Column getDisabledColumn(int idx) {
	// return disabledColumns.get(idx);
	// }
	//
	public Column getColumn(String columnName) {
		for (Column column : columns) {
			if (column.getName().equals(columnName)) {
				return column;
			}
		}
		return null;
	}

	// public Column getDisabledColumn(String columnName) {
	// for (Column column : disabledColumns) {
	// if (column.getName().equals(columnName)) {
	// return column;
	// }
	// }
	// return null;
	// }
	//
	public int getColumnIndex(Column column) {
		return columns.indexOf(column);
	}

	// public int getDisabledColumnIndex(Column column) {
	// return disabledColumns.indexOf(column);
	// }
	//
	public int getTupleCount() {
		return tuples.size();
	}

	public int getColumnCount() {
		return columns.size();
	}

	public void disableColumn(Column disabledColumn) {
		// int colIndex = columns.indexOf(column);
		//
		// for (int i = 0; i < tuples.size(); i++) {
		// Tuple tuple = tuples.get(i);
		// float elementValue = tuple.getElement(colIndex);
		// tuple.removeElement(colIndex);
		//
		// if (disabledColumnTuples.size() != tuples.size()) {
		// Tuple disabledTuple = new Tuple();
		// disabledTuple.addElement(elementValue);
		// disabledColumnTuples.add(disabledTuple);
		// } else {
		// Tuple disabledTuple = disabledColumnTuples.get(i);
		// disabledTuple.addElement(elementValue);
		// }
		// }
		//
		// column.setEnabled(false);
		// columns.remove(column);
		// disabledColumns.add(column);
		//
		// if (column == highlightedColumn) {
		// highlightedColumn = null;
		// }
		if (!disabledColumns.contains(disabledColumn)) {
            int disabledColumnIndex = columns.indexOf(disabledColumn);
            removeTupleElementsForColumn(disabledColumn);
			disabledColumn.setEnabled(false);
			if (disabledColumn == this.highlightedColumn) {
				highlightedColumn = null;
                fireHighlightedColumnChanged();
			}
			disabledColumns.add(disabledColumn);
            columns.remove(disabledColumn);
            clearActiveQueryColumnSelection(disabledColumn);
            for (Column column : columns) {
                column.getSummaryStats().getCorrelationCoefficients().remove(disabledColumnIndex);
            }
			fireColumnDisabled(disabledColumn);
		}
	}

    // get index of column and remove all tuple elements at this index
    // add the tuple elements to a list of disabledColumnTuples for later enabling
    private void removeTupleElementsForColumn(Column column) {
        int columnIndex = columns.indexOf(column);

        for (int iTuple = 0; iTuple < tuples.size(); iTuple++) {
            Tuple tuple = tuples.get(iTuple);
            float elementValue = tuple.getElement(columnIndex);
            tuple.removeElement(columnIndex);

            if (disabledColumnTuples.size() != tuples.size()) {
                Tuple disabledTuple = new Tuple();
                disabledTuple.addElement(elementValue);
                disabledColumnTuples.add(disabledTuple);
            } else {
                Tuple disabledTuple = disabledColumnTuples.get(iTuple);
                disabledTuple.addElement(elementValue);
            }
        }
    }

    public void addTupleElementsForDisabledColumn(Column column) {
        int columnIndex = disabledColumns.indexOf(column);
        if (columnIndex != -1) {
            for (int iTuple = 0; iTuple < disabledColumnTuples.size(); iTuple++) {
                Tuple disabledTuple = disabledColumnTuples.get(iTuple);
                float elementValue = disabledTuple.getElement(columnIndex);
                disabledTuple.removeElement(columnIndex);

                if (disabledColumnTuples.size() != tuples.size()) {
                    Tuple tuple = new Tuple();
                    tuple.addElement(elementValue);
                    tuples.add(tuple);
                } else {
                    Tuple tuple = tuples.get(iTuple);
                    tuple.addElement(elementValue);
                }
            }
        }
    }

	public void disableColumns(ArrayList<Column> columns) {
		for (Column column : columns) {
			if (!disabledColumns.contains(column)) {
                removeTupleElementsForColumn(column);
				column.setEnabled(false);
				if (column == this.highlightedColumn) {
					highlightedColumn = null;
                    fireHighlightedColumnChanged();
				}
				disabledColumns.add(column);
                columns.remove(column);
                clearActiveQueryColumnSelection(column);
			}
		}

		fireColumnsDisabled(columns);
	}

	public void enableColumn(Column column) {
		// int colIndex = disabledColumns.indexOf(column);
		// if (colIndex != -1) {
		// for (int i = 0; i < disabledColumnTuples.size(); i++) {
		// Tuple disabledTuple = disabledColumnTuples.get(i);
		// float elementValue = disabledTuple.getElement(colIndex);
		// disabledTuple.removeElement(colIndex);
		//
		// if (disabledColumnTuples.size() != tuples.size()) {
		// Tuple tuple = new Tuple();
		// tuple.addElement(elementValue);
		// tuples.add(tuple);
		// } else {
		// Tuple tuple = tuples.get(i);
		// tuple.addElement(elementValue);
		// }
		// }
		//
		// column.setEnabled(true);
		// disabledColumns.remove(column);
		// columns.add(column);
		// fireDataModelChanged();
		// }
		if (disabledColumns.contains(column)) {
            // move elements from disable column tuples to active tuples list
            addTupleElementsForDisabledColumn(column);
			disabledColumns.remove(column);
			column.setEnabled(true);
            columns.add(column);
            calculateStatistics();
            calculateQueryStatistics();
			// fireDataModelChanged();
			fireColumnEnabled(column);
		}
	}

	public int getDisabledColumnCount() {
		return disabledColumns.size();
	}

	public ArrayList<Column> getDisabledColumns() {
		return disabledColumns;
	}

	public void clearColumnSelectionRange (ColumnSelectionRange selectionRange) {
		ColumnSelection columnSelection = selectionRange.getColumnSelection();
		columnSelection.removeColumnSelectionRange(selectionRange);
		if (columnSelection.getColumnSelectionRangeCount() == 0) {
			activeQuery.clearColumnSelection(columnSelection.getColumn());
		}
		
		setQueriedTuples();
		this.fireColumnSelectionRemoved(selectionRange);
//		fireQueryChanged();
	}

    public int removeUnselectedTuples() {
        int tuplesRemoved = 0;

        if (activeQuery.hasColumnSelections()) {
            tuplesRemoved = tuples.size() - activeQuery.getTuples().size();
            log.debug("clearing old tuples list");
            tuples.clear();
            log.debug("adding selected tuples to list");
            tuples.addAll(activeQuery.getTuples());
            log.debug("Reseting column selections");
            activeQuery.clearAllColumnSelections();
            log.debug("Recalculating statistics");
            calculateStatistics();
            log.debug("Notifing listeners of changed data model");
            fireDataModelChanged();
        }

        return tuplesRemoved;
//
//        ArrayList<Tuple> tuplesRemoved = new ArrayList<Tuple>();
//
//		log.debug("Getting list of unselected tuples");
//        for (Tuple tuple : tuples) {
//            if (!tuple.getQueryFlag()) {
//                tuplesRemoved.add(tuple);
//            }
//        }
//
//		log.debug("Will remove " + tuplesRemoved.size() + " tuples");
//        if (!tuplesRemoved.isEmpty()) {
//			log.debug("Removing tuples");
//            tuples.removeAll(tuplesRemoved);
//			log.debug("Reseting column selections");
//            activeQuery.clearAllColumnSelections();
//			log.debug("Recalculating statistics");
//            calculateStatistics();
//			log.debug("Notifing listeners of changed data model");
//            fireDataModelChanged();
//        }
//
//        return tuplesRemoved.size();
    }

	public int removeSelectedTuples() {
        int tuplesRemoved = 0;

		if (activeQuery.hasColumnSelections()) {
            tuplesRemoved = activeQuery.getTuples().size();
            tuples.removeAll(activeQuery.getTuples());
            activeQuery.clearAllColumnSelections();
            calculateStatistics();
            fireDataModelChanged();
        }

        return tuplesRemoved;
//		ArrayList<Tuple> tuplesRemoved = new ArrayList<Tuple>();
//
//		for (Tuple tuple : tuples) {
//			if (tuple.getQueryFlag()) {
//				tuplesRemoved.add(tuple);
//			}
//		}
//
//		if (!tuplesRemoved.isEmpty()) {
//			tuples.removeAll(tuplesRemoved);
////			clearAllColumnQueries();
//			calculateStatistics();
//			fireDataModelChanged();
//		}
//
//		return tuplesRemoved.size();
	}

	public void saveActiveQuery() {
		savedQueryList.add(activeQuery);
		activeQuery = new Query("Q"+(nextQueryNumber++));
	}

	public Query getActiveQuery() {
		return activeQuery;
	}

	public void setActiveQuery(String queryID) {
		for (Query query : savedQueryList) {
			if (query.getID().equals(queryID)) {
				activeQuery = query;
				savedQueryList.remove(query);
				return;
			}
		}
	}

	public void clearActiveQuery() {
		activeQuery = new Query("Q"+(nextQueryNumber++));
	}

	public void clearActiveQueryColumnSelection(Column column) {
		if (activeQuery != null) {
			activeQuery.clearColumnSelection(column);
		}
	}

	public ArrayList<Query> getSavedQueryList() {
		return savedQueryList;
	}

	public Query getQueryByID(String ID) {
		if (activeQuery.getID().equals(ID)) {
			return activeQuery;
		} else {
			for (Query query : savedQueryList) {
				if (query.getID().equals(ID)) {
					return query;
				}
			}
		}

		return null;
	}

	public ColumnSelectionRange addColumnSelectionRangeToActiveQuery(Column column, float minValue, float maxValue) {
		ColumnSelection columnSelection = activeQuery.getColumnSelection(column);
		if (columnSelection == null) {
			columnSelection = new ColumnSelection(activeQuery, column);
			activeQuery.addColumnSelection(columnSelection);
		}

		ColumnSelectionRange selectionRange = columnSelection.addColumnSelectionRange(minValue, maxValue);

		fireColumnSelectionAdded(selectionRange);
//		fireQueryChanged();
		return selectionRange;
	}

//	private void clearAllColumnQueries() {
//		// boolean fireQueryChangedFlag = false;
//		for (Column column : columns) {
//			// if (column.isQuerySet()) {
//			column.setQueryFlag(false);
//			// fireQueryChangedFlag = true;
//			// }
//		}
//
//		// if (fireQueryChangedFlag) {
//		// fireQueryChanged();
//		// }
//	}

	/*
	 * public void moveElements(int currentElementIndex, int newElementIndex) {
	 * // dataset.moveElements(currentElementIndex, newElementIndex); if
	 * (currentElementIndex == newElementIndex) { return; }
	 * 
	 * Column tmpColumn = columns.get(currentElementIndex); if
	 * (currentElementIndex < newElementIndex) { for (int i =
	 * currentElementIndex; i < newElementIndex; i++) { columns.set(i,
	 * columns.get(i+1)); } } else { for (int i = currentElementIndex; i >
	 * newElementIndex; i--) { columns.set(i, columns.get(i-1)); } }
	 * columns.set(newElementIndex, tmpColumn);
	 * 
	 * // swap all tuple elements for (Tuple tuple : tuples) {
	 * tuple.moveElement(currentElementIndex, newElementIndex); }
	 * 
	 * calculateStatistics();
	 * 
	 * fireDataModelChanged(); }
	 */


    public void orderColumnsByCorrelation (Column compareColumn, boolean useQueryCorrelations) {
        int compareColumnIndex = getColumnIndex(compareColumn);

        ArrayList<Column> newColumnList = new ArrayList<Column>();
        ArrayList<ColumnSortRecord> positiveColumnList = new ArrayList<ColumnSortRecord>();
        ArrayList<ColumnSortRecord> negativeColumnList = new ArrayList<ColumnSortRecord>();
        ArrayList<ColumnSortRecord> nanColumnList = new ArrayList<ColumnSortRecord>();

        for (int i = 0; i < columns.size(); i++) {
            Column column = columns.get(i);
            if (column == compareColumn) {
                continue;
            }

            float corrCoef;
            if (useQueryCorrelations) {
                corrCoef = getActiveQuery().getColumnQuerySummaryStats(column).getCorrelationCoefficients().get(compareColumnIndex);
            } else {
                corrCoef = column.getSummaryStats().getCorrelationCoefficients().get(compareColumnIndex);
            }

            ColumnSortRecord columnSortRecord = new ColumnSortRecord(column, corrCoef);
            if (Float.isNaN(corrCoef)) {
                nanColumnList.add(columnSortRecord);
            } else if (corrCoef < 0.) {
                negativeColumnList.add(columnSortRecord);
            } else {
                positiveColumnList.add(columnSortRecord);
            }
        }

        // add negatively correlated axes
        if (!negativeColumnList.isEmpty()) {
            Object sortedRecords[] = negativeColumnList.toArray();
            Arrays.sort(sortedRecords);

            for (Object recordObject : sortedRecords) {
                ColumnSortRecord sortRecord = (ColumnSortRecord)recordObject;
                newColumnList.add(sortRecord.column);
            }
        }

        // compare axis goes between negative and positive correlated axes
        newColumnList.add(compareColumn);

        // add positively correlated axes
        if (!positiveColumnList.isEmpty()) {
            Object sortedRecords[] = positiveColumnList.toArray();
            Arrays.sort(sortedRecords);

            for (Object recordObject : sortedRecords) {
                ColumnSortRecord sortRecord = (ColumnSortRecord)recordObject;
                newColumnList.add(sortRecord.column);
            }
        }

        // add nan axes at bottom of the list
        if (!nanColumnList.isEmpty()) {
            for (ColumnSortRecord sortRecord : nanColumnList) {
                newColumnList.add(sortRecord.column);
            }
        }

        changeColumnOrder(newColumnList);
    }

    public void changeColumnOrder(ArrayList<Column> newColumnOrder) {
//        StringBuffer stringBuffer = new StringBuffer();
//        for (int i = 0; i < columns.size(); i++) {
//            float coef = highlightedColumn.getSummaryStats().getCorrelationCoefficients().get(i);
//            stringBuffer.append(columns.get(i).getName() + ": " + coef + "\n");
//        }
//        log.debug("before order change:\n" + stringBuffer.toString());

        // determine destination indices for new column order
        int dstColumnIndices[] = new int[newColumnOrder.size()];
        for (int i = 0; i < newColumnOrder.size(); i++) {
            // find index of column in new column order
            Column column = newColumnOrder.get(i);
            dstColumnIndices[i] = columns.indexOf(column);
        }

        // reset columns array
        columns = newColumnOrder;

        // rearrange column correlation coefficients
        for (int iColumn = 0; iColumn < columns.size(); iColumn++) {
            Column column = columns.get(iColumn);
            ArrayList<Float> corrCoef = column.getSummaryStats().getCorrelationCoefficients();
            ArrayList<Float> newCorrCoef = new ArrayList<Float>();
            for (int iCorrCoef = 0; iCorrCoef < corrCoef.size(); iCorrCoef++) {
                newCorrCoef.add(corrCoef.get(dstColumnIndices[iCorrCoef]));
            }
            column.getSummaryStats().setCorrelationCoefficients(newCorrCoef);
        }

        // move tuple elements to reflect new column order
//        ArrayList<Tuple> newTuples = new ArrayList<Tuple>();
        for (int iTuple = 0; iTuple < tuples.size(); iTuple++) {
            Tuple tuple = tuples.get(iTuple);
            Float elements[] = tuple.getElementsAsArray();
            tuple.removeAllElements();
//            Tuple newTuple = new Tuple();

            for (int iElement = 0; iElement < elements.length; iElement++) {
                tuple.addElement(elements[dstColumnIndices[iElement]]);
//                newTuple.addElement(tuple.getElement(dstColumnIndices[iElement]));
            }
//            newTuples.add(newTuple);
        }

        // reset tuples array
//        tuples = newTuples;

        // move query statistics to reflect new column order
        if (activeQuery.hasColumnSelections()) {
            for (int iColumn = 0; iColumn < columns.size(); iColumn++) {
                Column column = columns.get(iColumn);
                SummaryStats summaryStats = activeQuery.getColumnQuerySummaryStats(column);
                ArrayList<Float> corrCoef = summaryStats.getCorrelationCoefficients();
                ArrayList<Float> newCorrCoef = new ArrayList<Float>();
                for (int iCorrCoef = 0; iCorrCoef < corrCoef.size(); iCorrCoef++) {
                    newCorrCoef.add(corrCoef.get(dstColumnIndices[iCorrCoef]));
                }
                summaryStats.setCorrelationCoefficients(newCorrCoef);
            }
        }

//        stringBuffer = new StringBuffer();
//        for (int i = 0; i < getHighlightedColumn().getSummaryStats().getCorrelationCoefficients().size(); i++) {
//            float coef = getHighlightedColumn().getSummaryStats().getCorrelationCoefficients().get(i);
//            stringBuffer.append(getColumn(i).getName() + ": " + coef + "\n");
//        }
//        log.debug("after order change:\n" + stringBuffer.toString());

        fireDataModelChanged();
    }

	private void fireColumnDisabled(Column column) {
		for (DataModelListener listener : listeners) {
			listener.columnDisabled(this, column);
		}
	}

	private void fireColumnsDisabled(ArrayList<Column> disabledColumns) {
		for (DataModelListener listener : listeners) {
			listener.columnsDisabled(this, disabledColumns);
		}
	}

	private void fireColumnEnabled(Column column) {
		for (DataModelListener listener : listeners) {
			listener.columnEnabled(this, column);
		}
	}

	private void fireDataModelChanged() {
		for (DataModelListener listener : listeners) {
			listener.dataModelChanged(this);
		}
	}

	private void fireTuplesAdded(ArrayList<Tuple> newTuples) {
		for (DataModelListener listener : listeners) {
			listener.tuplesAdded(this, newTuples);
		}
	}

	public void fireHighlightedColumnChanged() {
		for (DataModelListener listener : listeners) {
			listener.highlightedColumnChanged(this);
		}
	}

	public void fireColumnSelectionAdded(ColumnSelectionRange columnSelectionRange) {
		for (DataModelListener listener : listeners) {
			listener.dataModelColumnSelectionAdded(this, columnSelectionRange);
		}
	}
	
	public void fireColumnSelectionRemoved(ColumnSelectionRange columnSelectionRange) {
		for (DataModelListener listener : listeners) {
			listener.dataModelColumnSelectionRemoved(this, columnSelectionRange);
		}
	}
	
	public void fireQueryChanged() {
		for (DataModelListener listener : listeners) {
			listener.queryChanged(this);
		}
	}

	public int getQueriedTupleCount() {
		return activeQuery.getTuples().size();
//		return queriedTuples.size();
	}

	public ArrayList<Tuple> getQueriedTuples() {
		return activeQuery.getTuples();
//		return queriedTuples;
	}

//	public boolean isColumnQuerySet() {
//		for (Column column : columns) {
//			if (column.isEnabled() && column.isQuerySet()) {
//				return true;
//			}
//		}
//		return false;
//	}

	public void setQueriedTuples() {
		log.debug("setting queried tuples");
//		queriedTuples.clear();
		activeQuery.clearTuples();

		if (getTupleCount() == 0) {
			return;
		}

		if (!activeQuery.getColumnSelections().isEmpty()) {
			for (int ituple = 0; ituple < getTupleCount(); ituple++) {
				Tuple currentTuple = getTuple(ituple);
				currentTuple.setQueryFlag(true);

				for (int icolumn = 0; icolumn < columns.size(); icolumn++) {
					Column column = columns.get(icolumn);
					ColumnSelection columnSelection = activeQuery.getColumnSelection(column);
					if (columnSelection != null && !columnSelection.getColumnSelectionRanges().isEmpty()) {
//						int selectionRangeIntersections = 0;
						boolean insideSelection = false;
//
						for (ColumnSelectionRange selectionRange : columnSelection.getColumnSelectionRanges()) {
							if ((currentTuple.getElement(icolumn) <= selectionRange.getMaxValue()) &&
									(currentTuple.getElement(icolumn) >= selectionRange.getMinValue())) {
//								selectionRangeIntersections++;
								insideSelection = true;
								break;
////							} else if (columnSelection.getColumnSelectionRanges().size() > 1) {
////								log.debug("Checking other selection");
							}
						}

						if (!insideSelection) {
							currentTuple.setQueryFlag(false);
							break;
						}
//
//						if (selectionRangeIntersections > 1) {
//							log.debug("selectionRangeIntersections: " + selectionRangeIntersections);
//						}
//						if (selectionRangeIntersections == 0) {
//							currentTuple.setQueryFlag(false);
//							break;
//						}
////						if (selectionRangeIntersections == 0) {
////							currentTuple.setQueryFlag(false);
////							break;
////						}
					}
				}

				if (currentTuple.getQueryFlag()) {
//					queriedTuples.add(currentTuple);
					activeQuery.addTuple(currentTuple);
				}
			}

			calculateQueryStatistics();
			fireQueryChanged();
		} else {
			for (Tuple tuple : tuples) {
				tuple.setQueryFlag(true);
			}
		}
//		if (querySet) {
//			for (int ituple = 0; ituple < getTupleCount(); ituple++) {
//				Tuple currentTuple = getTuple(ituple);
//				currentTuple.setQueryFlag(true);
//
//				for (int icolumn = 0; icolumn < columns.size(); icolumn++) {
//					Column column = columns.get(icolumn);
//					if (column.isQuerySet()) {
//						if ((currentTuple.getElement(icolumn) > column
//								.getMaxQueryValue())
//								|| (currentTuple.getElement(icolumn) < column
//										.getMinQueryValue())) {
//							currentTuple.setQueryFlag(false);
//							break;
//						}
//					}
//				}
//
//				if (currentTuple.getQueryFlag()) {
//					queriedTuples.add(currentTuple);
//				}
//			}
//			calculateQueryStatistics();
//			fireQueryChanged();
//		}

		log.debug("Finished setting queried tuples");
		log.debug("ActiveQuery has " + activeQuery.getTuples().size() + " tuples");
		log.debug("ActiveQuery has column Selections " + activeQuery.hasColumnSelections());
	}
}
