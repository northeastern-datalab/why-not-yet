package wny.entities;

import java.util.ArrayList;

/** 
 * A box is a hyper-rectangle where each attribute is limited by a lower bound and an upper bound
 * @author Zixuan Chen
*/
public class Box {
    private double measure; // This can either be the perimeter or volume of the box
	private ArrayList<String> bounds;

    public Box(double measure) {
        this.measure = measure;
        bounds = new ArrayList<String>();
    }

    /** 
     * @return The measure of the box
    */
    public double getPerimeter() {
        double v = 0;
        for (int i = 0; i < bounds.size(); i += 2) {
            v += Double.parseDouble(bounds.get(i + 1)) - Double.parseDouble(bounds.get(i));
        }
        return v;
    }

    /** 
     * @return The volume of the box
    */
    public double getVolume() {
        double v = 1.0;
        for (int i = 0; i < bounds.size(); i += 2) {
            v *= Double.parseDouble(bounds.get(i + 1)) - Double.parseDouble(bounds.get(i));
        }
        return v;
    }

    /** 
     * Add a bound to a box
     * All bounds need to be added in a sequence of lower, upper of attribute 1, lower, upper of attribute 2, ...
     * @param s The bound to be added
    */
    public void addBound(String s) {
        bounds.add(s);
    }

    /** 
     * Check whether a bound is zero
     * If both lower bound and upper bound of a attribute are 0, we skip printing this attribute
     * @param s The bound to be added
     * @return An indicator of whether the bound is zero
    */
    private boolean isZero(String s) {
        if (Double.parseDouble(s) == 0) return true;
        else return false;
    }
    
    /** 
     * @return Whether this is a valid box
    */
    public boolean valid() {
        return (measure > 0);
    }

    /** 
     * Print all non-zero attributes of the box
    */
    public void print() {
        if (valid()) {
            System.out.println("The optimization goal of the box is: " + String.format("%.5f", measure));
            System.out.print("The box is ");
            for (int i = 0; i < bounds.size() / 2; i++) {
                if (!isZero(bounds.get(i * 2 + 1))) {
                    System.out.print("Attribute" + (i + 1) + " ");
                    System.out.print(bounds.get(i * 2) + " ");
                    System.out.print(bounds.get(i * 2 + 1) + " ");
                }
            }
            System.out.println();
        } else {
            System.out.println("No valid box returned by the optimizer");
        }
    } 
}
