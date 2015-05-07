package gov.ornl.datatable;

/**
 * Created by csg on 11/25/14.
 */
public class ColumnSelectionRange {
    private ColumnSelection columnSelection;
    private float minValue;
    private float maxValue;

    public ColumnSelectionRange(ColumnSelection columnSelection, float minValue, float maxValue) {
        this.columnSelection = columnSelection;
        this.minValue = minValue;
        this.maxValue = maxValue;
    }

    public float getMaxValue() {
        return maxValue;
    }

    public ColumnSelection getColumnSelection () {
        return columnSelection;
    }

    public void setMaxValue(float maxValue) {
        this.maxValue = maxValue;
    }

    public float getMinValue() {
        return minValue;
    }

    public void setMinValue(float minValue) {
        this.minValue = minValue;
    }
}
