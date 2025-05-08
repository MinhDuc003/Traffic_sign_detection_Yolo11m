package come.example.yolo11;

public class BoundingBox {
    private final float x1;
    private final float y1;
    private final float x2;
    private final float y2;
    private final float cnf;
    private final int cls;
    private final String clsName;

    // Constructor
    public BoundingBox(float x1, float y1, float x2, float y2, float cnf, int cls, String clsName) {
        this.x1 = x1;
        this.y1 = y1;
        this.x2 = x2;
        this.y2 = y2;
        this.cnf = cnf;
        this.cls = cls;
        this.clsName = clsName;
    }

    // Getters
    public float getX1() { return x1; }
    public float getY1() { return y1; }
    public float getX2() { return x2; }
    public float getY2() { return y2; }
    public float getCnf() { return cnf; }
    public int getCls() { return cls; }
    public String getClsName() { return clsName; }

    // toString method
    @Override
    public String toString() {
        return "BoundingBox{" +
                "x1=" + x1 +
                ", y1=" + y1 +
                ", x2=" + x2 +
                ", y2=" + y2 +
                ", cnf=" + cnf +
                ", cls=" + cls +
                ", clsName='" + clsName + '\'' +
                '}';
    }

    // equals method
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        BoundingBox that = (BoundingBox) o;
        return Float.compare(that.x1, x1) == 0 &&
                Float.compare(that.y1, y1) == 0 &&
                Float.compare(that.x2, x2) == 0 &&
                Float.compare(that.y2, y2) == 0 &&
                Float.compare(that.cnf, cnf) == 0 &&
                cls == that.cls &&
                clsName.equals(that.clsName);
    }

    // hashCode method
    @Override
    public int hashCode() {
        int result = (x1 != +0.0f ? Float.floatToIntBits(x1) : 0);
        result = 31 * result + (y1 != +0.0f ? Float.floatToIntBits(y1) : 0);
        result = 31 * result + (x2 != +0.0f ? Float.floatToIntBits(x2) : 0);
        result = 31 * result + (y2 != +0.0f ? Float.floatToIntBits(y2) : 0);
        result = 31 * result + (cnf != +0.0f ? Float.floatToIntBits(cnf) : 0);
        result = 31 * result + cls;
        result = 31 * result + (clsName != null ? clsName.hashCode() : 0);
        return result;
    }
}