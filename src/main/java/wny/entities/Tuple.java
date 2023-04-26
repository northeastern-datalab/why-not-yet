package wny.entities;

import java.util.Arrays;
import java.util.List;

/** 
 * A database tuple
 * The attribute list of the tuple must agree with the schema of the relation it belongs to
 * @author Nikolaos Tziavelis 
 * Modified by Zixuan Chen 
 * The original code of Nikolaos is from the any-k repository, https://github.com/northeastern-datalab/anyk-code.
 * 
*/
public class Tuple implements Comparable<Tuple>
{
    public String[] values;
    public Relation relation;

    /** 
     * Constructs a tuple based on a given list of values for the attributes<br>
     * CAUTION: The object will contain a reference to the passed list of values
     * No deep copy is made
     * @param values_list A list of values for the attributes of the relation
     * @param rel The relation the tuple belongs to
    */
    public Tuple(String[] values_list, Relation rel)
    {
        this.values = values_list;
        /*
        // Make a deep copy of the values inside this object
        int length = values_list.size();
        this.values = ArrayList<Integer>();
        for (int i = 0; i < length; i++) this.values.add(values_list.get(i);
        */
        this.relation = rel;
    }

    public Tuple(String[] values_list)
    {
        this.values = values_list;
        /*
        // Make a deep copy of the values inside this object
        int length = values_list.size();
        this.values = ArrayList<Integer>();
        for (int i = 0; i < length; i++) this.values.add(values_list.get(i);
        */
    }

    /** 
     * Constructs a new tuple by concatenating a list of other tuples
     * @param list_of_tuples A list of tuples that will be merged to create a new one
     * @param rel The relation the tuple belongs to
    */
    public Tuple(List<Tuple> list_of_tuples, Relation rel)
    {
        // First, find the size of the new tuple
        int size = 0;
        for (Tuple t : list_of_tuples)
            size += t.values.length;
        // Now allocate an array of that size
        String[] vals = new String[size];
        // Iterate through the list and add values and costs
        int i = 0;
        for (Tuple t : list_of_tuples)
        {
            for (String val : t.values)
            {
                vals[i] = val;
                i++;
            }            
        }
        this.values = vals;
        this.relation = rel;
    }

    
    /** 
     * @return String A string representation of the values of the tuple
     */
    public String valuesToString()
    {
        return Arrays.toString(values).replaceAll("\\[|\\]|,", "");
    }
    
    /** 
     * A tuple is compared with another according to their default score
     * @param other the other tuple
     * @return An integer showing whether the tuple is has larger, same or smaller score.
     */
    @Override
    public int compareTo(Tuple other)
    {
        double score1 = 0;
        double score2 = 0;
        for (int i = 1; i < values.length; i++) {
            score1 += Double.valueOf(this.values[i]);
            score2 += Double.valueOf(other.values[i]);
        }
        if (score1 < score2) return -1;
        else if (score1 > score2) return 1;
        else return 0;
    }

    /** 
     * A tuple is compared with another to get the relationship of the domination
     * @param other the other tuple
     * @return An integer showing whether the tuple dominates, is domianted by or has no domination relationship with the other tuple.
     */
    public int isDominating(Tuple other)
    {
        int count = 0;
        for (int i = 1; i < values.length; i++) {
            if (Double.valueOf(this.values[i]) >= Double.valueOf(other.values[i])) {
                count++;
            } else if (Double.valueOf(this.values[i]) < Double.valueOf(other.values[i])) {
                count--;
            }
        }
        if (count == values.length - 1) return 1;
        else if (count == -(values.length - 1)) return -1;
        else return 0;
    }
    
    /** 
     * @return String A string representation of the values of the tuple together with its cost
     */
    public String flat_format()
    {
        return Arrays.toString(values).replaceAll("\\[|\\]|,", "");
    }

    /** 
     * @return int
     */
    @Override
    public int hashCode() 
    {
        return Arrays.hashCode(this.values);
    }
    
    /** 
     * A Tuple is equal to another when they agree on the values
     * (but not necessarily the cost)
     * @param o
     * @return boolean
     */
    @Override
    public boolean equals(Object o) 
    {
        if (o == this) return true;
        if (!(o instanceof Tuple)) return false;
        Tuple other_tuple = (Tuple) o;
        return Arrays.equals(this.values, other_tuple.values);
    }
    
    /** 
     * @return String A string representation of the values of the tuple 
     * together with the id of the relation it belongs to
     */
    @Override
    public String toString()
    {
        return relation.relation_id + ":" + Arrays.toString(values); 
        // return Arrays.toString(values).replaceAll("\\[|\\]|,", "") + " " + cost;
    }
}
