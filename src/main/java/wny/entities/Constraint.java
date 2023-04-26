package wny.entities;

/** 
 * A flexible constraint defined by users
 * @author Zixuan Chen
*/

public class Constraint {
    public int attribute;
    public String type;
    public Double value;

    /** 
     * @param attribute The attribute to add this constraint to
     * @param type The type of the constraint can be min, max, space,
     * limiting the minimum of the lower bound, maximum of the upper bound and the length between two bounds respectively
     * @param value The value of min, max or space
    */
    public Constraint(int attribute, String type, Double value) {
        this.attribute = attribute;
        this.type = type;
        this.value = value;
    }
}
