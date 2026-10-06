package travelplanner;

/**
 * Distance + duration table returned by the OpenRouteService Matrix API.
 * km[i][j] and minutes[i][j] = travel from location i to location j.
 * A value of -1 means "no road route found" for that pair.
 */
public class MatrixResult {

    private double[][] km;
    private double[][] minutes;

    public MatrixResult(double[][] km, double[][] minutes) {
        this.km = km;
        this.minutes = minutes;
    }

    public double getKm(int from, int to) { return km[from][to]; }
    public double getMinutes(int from, int to) { return minutes[from][to]; }
    public int size() { return km.length; }
}
